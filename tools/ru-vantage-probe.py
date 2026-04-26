#!/usr/bin/env python3
"""
tools/ru-vantage-probe.py
Читает bootstrap-servers.json, для каждого URI делает TCP+TLS probe
через RU SOCKS5 прокси. Источник прокси: proxyscrape RU SOCKS5.

Использование:
    python3 tools/ru-vantage-probe.py [--bootstrap-file PATH] [--output PATH] [--timeout 10] [--max-proxies 20]

Выходной файл: ru-probe-results.json
"""

import argparse
import json
import socket
import ssl
import time
import urllib.request
import urllib.error
import concurrent.futures
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional
from urllib.parse import urlparse

PROXYSCRAPE_URL = (
    "https://api.proxyscrape.com/v3/free-proxy-list/get"
    "?request=displayproxies&country=ru&proxy_format=protocolipport"
    "&format=text&protocol=socks5"
)

DEFAULT_BOOTSTRAP = Path(__file__).parent.parent / "app/src/main/assets/bootstrap-servers.json"


@dataclass
class ProbeResult:
    server_uri: str
    proxy_used: str
    status: str            # "ok" | "timeout" | "error" | "tls_error"
    latency_ms: Optional[float] = None
    error_msg: Optional[str] = None
    probed_at: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())


def fetch_ru_proxies(max_proxies: int, timeout: int) -> list[str]:
    """Скачивает список RU SOCKS5 прокси с proxyscrape."""
    print(f"[probe] Скачиваю RU SOCKS5 прокси с proxyscrape...", flush=True)
    try:
        req = urllib.request.Request(
            PROXYSCRAPE_URL,
            headers={"User-Agent": "ozero-ru-probe/1.0"},
        )
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            # 10 МБ лимит против OOM при malicious response
            body = resp.read(10 * 1024 * 1024 + 1)
            if len(body) > 10 * 1024 * 1024:
                print("[probe] Ответ > 10 МБ — отклонено", flush=True)
                return []
            text = body.decode("utf-8", errors="replace")
        proxies = [
            line.strip()
            for line in text.splitlines()
            if line.strip() and not line.startswith("#")
        ]
        print(f"[probe] Получено {len(proxies)} прокси, берём первые {max_proxies}", flush=True)
        return proxies[:max_proxies]
    except Exception as e:
        print(f"[probe] Ошибка получения прокси: {e}", flush=True)
        return []


def tcp_tls_probe(host: str, port: int, timeout: int, use_tls: bool) -> tuple[str, float, Optional[str]]:
    """Прямой TCP (опционально TLS) probe. Возвращает (status, latency_ms, error_msg)."""
    start = time.monotonic()
    try:
        sock = socket.create_connection((host, port), timeout=timeout)
        if use_tls:
            ctx = ssl.create_default_context()
            ctx.check_hostname = True
            ctx.verify_mode = ssl.CERT_REQUIRED
            with ctx.wrap_socket(sock, server_hostname=host) as ssock:
                ssock.do_handshake()
        else:
            sock.close()
        latency = (time.monotonic() - start) * 1000
        return "ok", latency, None
    except socket.timeout:
        return "timeout", (time.monotonic() - start) * 1000, "connection timed out"
    except ssl.SSLError as e:
        return "tls_error", (time.monotonic() - start) * 1000, str(e)
    except OSError as e:
        return "error", (time.monotonic() - start) * 1000, str(e)


def probe_via_socks5(host: str, port: int, proxy_str: str, timeout: int) -> tuple[str, float, Optional[str]]:
    """
    Пытается TCP probe через SOCKS5 прокси используя встроенный socket.
    Реализует минимальный SOCKS5 handshake (RFC 1928).
    """
    start = time.monotonic()
    try:
        # Парсим прокси
        if "://" in proxy_str:
            parsed = urlparse(proxy_str)
            proxy_host, proxy_port = parsed.hostname, parsed.port or 1080
        else:
            parts = proxy_str.split(":")
            proxy_host = parts[0]
            proxy_port = int(parts[1]) if len(parts) > 1 else 1080

        sock = socket.create_connection((proxy_host, proxy_port), timeout=timeout)

        # SOCKS5 greeting: версия=5, nmethods=1, method=0 (no auth)
        sock.sendall(b"\x05\x01\x00")
        resp = sock.recv(2)
        if len(resp) < 2 or resp[0] != 0x05 or resp[1] == 0xFF:
            sock.close()
            return "error", (time.monotonic() - start) * 1000, "SOCKS5 auth negotiation failed"

        # SOCKS5 CONNECT request
        host_bytes = host.encode("ascii")
        request = (
            b"\x05\x01\x00\x03"            # VER=5, CMD=CONNECT, RSV=0, ATYP=domain
            + bytes([len(host_bytes)])      # domain length
            + host_bytes                    # domain
            + port.to_bytes(2, "big")       # port
        )
        sock.sendall(request)
        resp = sock.recv(10)

        if len(resp) < 2 or resp[0] != 0x05 or resp[1] != 0x00:
            code = resp[1] if len(resp) > 1 else -1
            sock.close()
            return "error", (time.monotonic() - start) * 1000, f"SOCKS5 CONNECT failed, code={code}"

        sock.close()
        latency = (time.monotonic() - start) * 1000
        return "ok", latency, None

    except socket.timeout:
        return "timeout", (time.monotonic() - start) * 1000, "proxy timeout"
    except OSError as e:
        return "error", (time.monotonic() - start) * 1000, str(e)


def parse_server_host_port(server_uri: str) -> Optional[tuple[str, int]]:
    """Извлекает host:port из URI (vless://, ss://, https://, и т.д.)."""
    try:
        parsed = urlparse(server_uri)
        host = parsed.hostname
        port = parsed.port
        if host and port:
            return host, port
    except Exception:
        pass
    return None


def probe_server(server_uri: str, proxies: list[str], timeout: int) -> ProbeResult:
    """Пробирует сервер через список прокси, возвращает первый успешный или последний результат."""
    hp = parse_server_host_port(server_uri)
    if hp is None:
        return ProbeResult(
            server_uri=server_uri,
            proxy_used="(none)",
            status="error",
            error_msg="Не удалось распарсить host:port из URI",
        )
    host, port = hp

    if not proxies:
        # Нет прокси — прямой probe
        status, latency, err = tcp_tls_probe(host, port, timeout, use_tls=(port in (443, 8443)))
        return ProbeResult(
            server_uri=server_uri,
            proxy_used="(direct)",
            status=status,
            latency_ms=round(latency, 2),
            error_msg=err,
        )

    last_result = None
    for proxy in proxies[:5]:  # пробуем максимум 5 прокси
        status, latency, err = probe_via_socks5(host, port, proxy, timeout)
        result = ProbeResult(
            server_uri=server_uri,
            proxy_used=proxy,
            status=status,
            latency_ms=round(latency, 2),
            error_msg=err,
        )
        last_result = result
        if status == "ok":
            return result

    return last_result  # type: ignore[return-value]


def main():
    parser = argparse.ArgumentParser(description="RU vantage proxy probe для bootstrap-servers.json")
    parser.add_argument("--bootstrap-file", default=str(DEFAULT_BOOTSTRAP), help="Путь к bootstrap-servers.json")
    parser.add_argument("--output", default="ru-probe-results.json", help="Выходной файл")
    parser.add_argument("--timeout", type=int, default=10, help="Таймаут TCP/SOCKS5 (секунды)")
    parser.add_argument("--max-proxies", type=int, default=20, help="Максимум прокси для скачивания")
    parser.add_argument("--workers", type=int, default=4, help="Параллельных workers")
    parser.add_argument("--direct", action="store_true", help="Прямой probe без SOCKS5 (debug)")
    args = parser.parse_args()

    # Загружаем bootstrap-servers.json
    bootstrap_path = Path(args.bootstrap_file)
    if not bootstrap_path.exists():
        print(f"[probe] Файл не найден: {bootstrap_path}", flush=True)
        raise SystemExit(1)

    with bootstrap_path.open() as f:
        bootstrap_data = json.load(f)

    servers = bootstrap_data.get("servers", [])
    print(f"[probe] Загружено {len(servers)} серверов из {bootstrap_path}", flush=True)

    # Получаем RU прокси
    proxies: list[str] = [] if args.direct else fetch_ru_proxies(args.max_proxies, args.timeout)

    # Параллельный probe
    results: list[ProbeResult] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = {
            executor.submit(probe_server, uri, proxies, args.timeout): uri
            for uri in servers
        }
        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            results.append(result)
            symbol = "✓" if result.status == "ok" else "✗"
            latency_str = f"{result.latency_ms:.0f}ms" if result.latency_ms is not None else "N/A"
            print(
                f"[probe] {symbol} {result.server_uri[:60]:60s} | {result.status:10s} | {latency_str:8s} | via {result.proxy_used[:40]}",
                flush=True,
            )

    ok_count = sum(1 for r in results if r.status == "ok")
    print(f"\n[probe] Итого: {ok_count}/{len(results)} серверов достижимы через RU прокси", flush=True)

    output = {
        "probed_at": datetime.now(timezone.utc).isoformat(),
        "bootstrap_file": str(bootstrap_path),
        "proxies_used": len(proxies),
        "servers_total": len(results),
        "servers_ok": ok_count,
        "results": [asdict(r) for r in results],
    }

    with open(args.output, "w") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"[probe] Результаты сохранены в {args.output}", flush=True)


if __name__ == "__main__":
    main()
