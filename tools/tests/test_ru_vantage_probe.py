"""
Тесты для ru-vantage-probe.py
Запуск: pytest tools/tests/test_ru_vantage_probe.py -v
"""
import json
import socket
import sys
import threading
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

# Добавляем tools/ в путь для импорта
sys.path.insert(0, str(Path(__file__).parent.parent))

from ru_vantage_probe import (
    ProbeResult,
    parse_server_host_port,
    probe_server,
    tcp_tls_probe,
)


class TestParseServerHostPort:
    """Тесты парсинга host:port из URI."""

    def test_vless_uri(self):
        uri = "vless://uuid@proxy.example.com:443?type=tcp#test"
        result = parse_server_host_port(uri)
        assert result == ("proxy.example.com", 443)

    def test_https_uri(self):
        uri = "https://example.com:8443/path"
        result = parse_server_host_port(uri)
        assert result == ("example.com", 8443)

    def test_ss_uri(self):
        uri = "ss://base64@192.168.1.1:8388"
        result = parse_server_host_port(uri)
        assert result == ("192.168.1.1", 8388)

    def test_invalid_uri_returns_none(self):
        assert parse_server_host_port("not-a-uri") is None

    def test_uri_without_port_returns_none(self):
        # scheme без порта — не можем определить
        assert parse_server_host_port("vless://uuid@example.com") is None

    def test_placeholder_uri(self):
        uri = "vless://00000000-0000-0000-0000-000000000000@example.invalid:443?encryption=none"
        result = parse_server_host_port(uri)
        assert result == ("example.invalid", 443)


class TestTcpTlsProbe:
    """Тесты TCP/TLS probe с мокированным сокетом."""

    def test_successful_tcp_probe(self):
        with patch("socket.create_connection") as mock_conn:
            mock_sock = MagicMock()
            mock_conn.return_value = mock_sock
            status, latency, err = tcp_tls_probe("example.com", 80, 5, use_tls=False)
        assert status == "ok"
        assert latency >= 0
        assert err is None

    def test_timeout_probe(self):
        with patch("socket.create_connection", side_effect=socket.timeout("timed out")):
            status, latency, err = tcp_tls_probe("example.com", 80, 1, use_tls=False)
        assert status == "timeout"
        assert "timed out" in err

    def test_connection_refused(self):
        with patch(
            "socket.create_connection",
            side_effect=OSError("Connection refused"),
        ):
            status, latency, err = tcp_tls_probe("example.com", 80, 5, use_tls=False)
        assert status == "error"
        assert err is not None


class TestProbeServer:
    """Тесты probe_server с мокированными зависимостями."""

    def test_probe_invalid_uri(self):
        result = probe_server("not-a-uri", [], timeout=5)
        assert result.status == "error"
        assert "распарсить" in result.error_msg

    def test_direct_probe_ok(self):
        with patch("ru_vantage_probe.tcp_tls_probe", return_value=("ok", 42.0, None)):
            result = probe_server("vless://uuid@example.com:443", [], timeout=5)
        assert result.status == "ok"
        assert result.latency_ms == 42.0
        assert result.proxy_used == "(direct)"

    def test_direct_probe_timeout(self):
        with patch(
            "ru_vantage_probe.tcp_tls_probe",
            return_value=("timeout", 5000.0, "connection timed out"),
        ):
            result = probe_server("vless://uuid@example.com:443", [], timeout=5)
        assert result.status == "timeout"
        assert result.error_msg == "connection timed out"

    def test_probe_via_proxy_fallback(self):
        """При неудаче первых прокси должен вернуть последний результат."""
        call_count = 0

        def mock_probe_socks5(host, port, proxy_str, timeout):
            nonlocal call_count
            call_count += 1
            return "error", 100.0, f"proxy {call_count} failed"

        with patch("ru_vantage_probe.probe_via_socks5", side_effect=mock_probe_socks5):
            result = probe_server(
                "vless://uuid@example.com:443",
                proxies=["proxy1:1080", "proxy2:1080", "proxy3:1080"],
                timeout=5,
            )
        assert result.status == "error"
        assert call_count == 3  # попробовало все 3 прокси


class TestProbeResultSerialization:
    """Тесты сериализации ProbeResult."""

    def test_asdict_contains_all_fields(self):
        from dataclasses import asdict
        result = ProbeResult(
            server_uri="vless://test@host:443",
            proxy_used="proxy:1080",
            status="ok",
            latency_ms=12.5,
        )
        d = asdict(result)
        assert "server_uri" in d
        assert "proxy_used" in d
        assert "status" in d
        assert "latency_ms" in d
        assert "probed_at" in d

    def test_json_serializable(self):
        from dataclasses import asdict
        result = ProbeResult(
            server_uri="vless://test@host:443",
            proxy_used="proxy:1080",
            status="timeout",
            latency_ms=9999.0,
            error_msg="timed out",
        )
        # Не должно бросать исключение
        json_str = json.dumps(asdict(result))
        parsed = json.loads(json_str)
        assert parsed["status"] == "timeout"
