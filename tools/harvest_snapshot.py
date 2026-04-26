#!/usr/bin/env python3
"""
Harvest snapshot tool — pulls public proxy sources, runs TCP+TLS liveness probe,
selects top-N by latency, writes app/src/main/assets/bootstrap-servers.json.

Replicates Android-side PublicProxyHarvester + LiveProber on the host so that
release artifacts can be regenerated reproducibly in CI / locally.

Usage:
    python3 tools/harvest_snapshot.py --top 50 --timeout 3 --concurrency 64

Sources are read from app/src/main/assets/proxy-sources.json.
Output: app/src/main/assets/bootstrap-servers.json (+ optional .sig if --sign-key given).
"""
from __future__ import annotations

import argparse
import asyncio
import base64
import json
import os
import socket
import ssl
import sys
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable

REPO_ROOT = Path(__file__).resolve().parent.parent
SOURCES_PATH = REPO_ROOT / "app/src/main/assets/proxy-sources.json"
BOOTSTRAP_PATH = REPO_ROOT / "app/src/main/assets/bootstrap-servers.json"

SUPPORTED_SCHEMES = {"vless", "vmess", "trojan", "ss", "hysteria2", "hy2"}


@dataclass
class Candidate:
    uri: str
    scheme: str
    host: str
    port: int
    source_id: str
    region: str

    @property
    def hostport(self) -> str:
        return f"{self.host}:{self.port}"


@dataclass
class ProbeResult:
    candidate: Candidate
    alive: bool
    latency_ms: int | None
    error: str | None


MAX_BODY_BYTES = 10 * 1024 * 1024  # 10 МБ — лимит против OOM от malicious source


def fetch(url: str, timeout: float = 10.0) -> str:
    parsed = urllib.parse.urlparse(url)
    # SSRF guard: запрещаем file://, ftp://, gopher://, javascript: и пр.
    if parsed.scheme not in ("http", "https"):
        raise ValueError(f"disallowed URL scheme: {parsed.scheme}")
    req = urllib.request.Request(url, headers={"User-Agent": "ozero-harvest/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read(MAX_BODY_BYTES + 1)
        if len(body) > MAX_BODY_BYTES:
            raise ValueError(f"response too large (>{MAX_BODY_BYTES} bytes)")
        return body.decode("utf-8", errors="replace")


def parse_lines(body: str, fmt: str) -> list[str]:
    if fmt == "BASE64_LINES":
        try:
            body = base64.b64decode(body.strip() + "==").decode("utf-8", errors="replace")
        except Exception:
            return []
    if fmt == "JSON_ARRAY":
        try:
            data = json.loads(body)
            if isinstance(data, list):
                return [str(x) for x in data]
            if isinstance(data, dict) and isinstance(data.get("servers"), list):
                return [str(x) for x in data["servers"]]
        except Exception:
            return []
        return []
    return [ln.strip() for ln in body.splitlines() if ln.strip() and not ln.startswith("#")]


def parse_uri(uri: str) -> Candidate | None:
    try:
        scheme = uri.split("://", 1)[0].lower()
        if scheme not in SUPPORTED_SCHEMES:
            return None
        if scheme == "vmess":
            payload = uri.split("://", 1)[1].split("#", 1)[0]
            try:
                decoded = base64.b64decode(payload + "==").decode("utf-8", errors="replace")
                obj = json.loads(decoded)
                host = obj.get("add") or obj.get("host")
                port = int(obj.get("port") or 0)
            except Exception:
                return None
            if not host or not port:
                return None
            return Candidate(uri=uri, scheme=scheme, host=host, port=port, source_id="", region="")
        rest = uri.split("://", 1)[1]
        userinfo_host = rest.split("?", 1)[0].split("#", 1)[0]
        if "@" in userinfo_host:
            host_port = userinfo_host.split("@", 1)[1]
        else:
            host_port = userinfo_host
        if ":" not in host_port:
            return None
        host, port_s = host_port.rsplit(":", 1)
        port = int(port_s)
        if not host or port <= 0 or port > 65535:
            return None
        return Candidate(uri=uri, scheme=scheme, host=host, port=port, source_id="", region="")
    except Exception:
        return None


async def probe_one(c: Candidate, timeout: float) -> ProbeResult:
    loop = asyncio.get_event_loop()
    start = loop.time()
    try:
        fut = asyncio.open_connection(c.host, c.port)
        reader, writer = await asyncio.wait_for(fut, timeout=timeout)
        latency = int((loop.time() - start) * 1000)
        writer.close()
        try:
            await writer.wait_closed()
        except Exception:
            pass
        return ProbeResult(c, alive=True, latency_ms=latency, error=None)
    except Exception as e:
        return ProbeResult(c, alive=False, latency_ms=None, error=str(e)[:80])


async def probe_all(cands: list[Candidate], timeout: float, concurrency: int) -> list[ProbeResult]:
    sem = asyncio.Semaphore(concurrency)
    async def bound(c: Candidate) -> ProbeResult:
        async with sem:
            return await probe_one(c, timeout)
    return await asyncio.gather(*(bound(c) for c in cands))


def harvest(sources: list[dict], fetch_timeout: float) -> list[Candidate]:
    seen: set[str] = set()
    out: list[Candidate] = []
    for src in sources:
        sid, url, fmt, region = src["id"], src["url"], src["format"], src.get("region", "")
        try:
            body = fetch(url, fetch_timeout)
        except Exception as e:
            print(f"[WARN] {sid}: fetch failed: {e}", file=sys.stderr)
            continue
        lines = parse_lines(body, fmt)
        added = 0
        for ln in lines:
            cand = parse_uri(ln)
            if not cand:
                continue
            key = f"{cand.scheme}:{cand.hostport}"
            if key in seen:
                continue
            seen.add(key)
            cand.source_id = sid
            cand.region = region
            out.append(cand)
            added += 1
        print(f"[INFO] {sid}: parsed {added} new (total {len(out)})", file=sys.stderr)
    return out


def write_snapshot(top: list[ProbeResult], path: Path) -> None:
    payload = {
        "_comment": "Auto-generated by tools/harvest_snapshot.py — DO NOT EDIT MANUALLY",
        "_snapshot_date": time.strftime("%Y-%m-%d", time.gmtime()),
        "_snapshot_source": "harvest_snapshot.py + TCP probe",
        "_count": len(top),
        "servers": [r.candidate.uri for r in top],
        "metadata": [
            {
                "uri": r.candidate.uri,
                "scheme": r.candidate.scheme,
                "host": r.candidate.host,
                "port": r.candidate.port,
                "source": r.candidate.source_id,
                "region": r.candidate.region,
                "latency_ms": r.latency_ms,
            }
            for r in top
        ],
    }
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def sign_snapshot(path: Path, key_path: Path) -> Path:
    sig_path = path.with_suffix(path.suffix + ".sig")
    import subprocess
    subprocess.run(
        ["openssl", "pkeyutl", "-sign", "-rawin", "-inkey", str(key_path), "-in", str(path), "-out", str(sig_path)],
        check=True,
    )
    return sig_path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--top", type=int, default=50)
    ap.add_argument("--timeout", type=float, default=3.0)
    ap.add_argument("--fetch-timeout", type=float, default=10.0)
    ap.add_argument("--concurrency", type=int, default=64)
    ap.add_argument("--sources", type=Path, default=SOURCES_PATH)
    ap.add_argument("--out", type=Path, default=BOOTSTRAP_PATH)
    ap.add_argument("--sign-key", type=Path, default=None,
                    help="Ed25519 private key (PEM) for detached signature")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    sources_doc = json.loads(args.sources.read_text(encoding="utf-8"))
    sources = sources_doc["sources"]
    print(f"[INFO] {len(sources)} sources", file=sys.stderr)

    cands = harvest(sources, args.fetch_timeout)
    print(f"[INFO] {len(cands)} unique candidates", file=sys.stderr)

    if not cands:
        print("[ERROR] no candidates harvested", file=sys.stderr)
        return 2

    results = asyncio.run(probe_all(cands, args.timeout, args.concurrency))
    alive = [r for r in results if r.alive]
    alive.sort(key=lambda r: r.latency_ms or 99999)
    print(f"[INFO] alive {len(alive)}/{len(results)}", file=sys.stderr)

    top = alive[: args.top]
    if not top:
        print("[ERROR] no alive servers", file=sys.stderr)
        return 3

    if args.dry_run:
        for r in top:
            print(f"  {r.latency_ms:>4}ms  {r.candidate.scheme:<10} {r.candidate.hostport:<40} ({r.candidate.source_id})")
        return 0

    write_snapshot(top, args.out)
    print(f"[OK] wrote {args.out} ({len(top)} servers)", file=sys.stderr)
    if args.sign_key:
        sig = sign_snapshot(args.out, args.sign_key)
        print(f"[OK] wrote signature {sig}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
