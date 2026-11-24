import base64
import concurrent.futures
import ipaddress
import json
import re
import subprocess
import tempfile
import time
from pathlib import Path

SOURCE = Path("engine-warp/src/main/java/ru/ozero/enginewarp/ProxyWarpAutoConfig.kt")
CANDIDATES = (
    "https://warply3.vercel.app/api/generate",
    "https://getwarp3.netlify.app/api/generate",
    "https://warp-gen-vip.vercel.app/api/warp",
    "https://portalwg.vercel.app/api/warp",
    "https://warp-gen.cyb-portal.org/api/warp",
)
MAX_BODY = 1024 * 1024


def kotlin_constant(source, name):
    match = re.search(r"const val " + name + r"\s*=\s*((?:\"(?:\\.|[^\"\\])*\"\s*(?:\+\s*)?)+)", source)
    if not match:
        raise ValueError(name)
    return "".join(json.loads(s) for s in re.findall(r'\"(?:\\.|[^\"\\])*\"', match[1]))


def ini(value):
    if isinstance(value, str) and "[Interface]" in value:
        return value[value.index("[Interface]"):]
    return None


def extract(body):
    try:
        obj = json.loads(body)
    except ValueError:
        return ini(body)
    if not isinstance(obj, dict) or obj.get("success") is False:
        return None
    content = obj.get("content")
    if isinstance(content, dict):
        for key in ("amQuick", "wgQuick"):
            if result := ini(content.get(key)):
                return result
        if content.get("configBase64"):
            try:
                decoded = base64.b64decode(content["configBase64"]).decode()
                return ini(decoded) or decoded
            except (ValueError, TypeError):
                return None
    for key in ("data", "config", "wireguard", "conf"):
        if result := ini(obj.get(key)):
            return result
    for key in ("data", "configs", "config"):
        nested = obj.get(key)
        if isinstance(nested, dict):
            for value in nested.values():
                if result := ini(value):
                    return result
    return None


def valid_config(body, peer_key, peer_host):
    conf = extract(body)
    if not conf:
        return False
    sections = {"interface": {}, "peer": {}}
    section = None
    for raw in conf.splitlines():
        line = raw.partition("#")[0].strip()
        if line.lower() in ("[interface]", "[peer]"):
            section = line[1:-1].lower()
        elif section and "=" in line:
            key, _, value = line.partition("=")
            sections[section][key.strip().lower()] = value.strip()
    interface, peer = sections["interface"], sections["peer"]
    try:
        if len(base64.b64decode(interface["privatekey"], validate=True)) != 32:
            return False
        addresses = [ipaddress.ip_interface(a.strip()) for a in interface["address"].split(",")]
        host, port = peer["endpoint"].rsplit(":", 1)
        host = host.strip().lower()
        if not 0 < int(port) < 65536 or peer["publickey"] != peer_key:
            return False
        if host != peer_host:
            ip = ipaddress.ip_address(host)
            if not any(ip in ipaddress.ip_network(n) for n in ("162.159.192.0/22", "188.114.96.0/22")):
                return False
        routes = {r.strip() for r in peer.get("allowedips", "0.0.0.0/0,::/0").split(",") if r.strip()}
        return bool(addresses) and "0.0.0.0/0" in routes and (not any(a.version == 6 for a in addresses) or "::/0" in routes)
    except (KeyError, ValueError, TypeError):
        return False


def probe(url, source):
    payload = kotlin_constant(source, "GENERATOR_REQUEST_BODY" if url.endswith("/api/generate") else "REQUEST_BODY")
    agent = kotlin_constant(source, "DEFAULT_USER_AGENT")
    start = time.monotonic()
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory) / "response"
        try:
            proc = subprocess.run(
                ["curl", "--silent", "--show-error", "--proto", "=https",
                 "--connect-timeout", "5", "--max-time", "12", "--max-filesize", str(MAX_BODY),
                 "--user-agent", agent, "--header", "Content-Type: application/json",
                 "--data-binary", payload, "--output", str(output),
                 "--write-out", "%{http_code}", url],
                capture_output=True, text=True, timeout=15, check=False,
            )
        except (subprocess.TimeoutExpired, OSError):
            return "transport_error", 0, round((time.monotonic() - start) * 1000)
        status = int(proc.stdout) if proc.stdout.isdigit() else 0
        if proc.returncode:
            label = {6: "dns_error", 7: "connect_error", 28: "timeout", 60: "tls_error"}.get(proc.returncode, "transport_error")
        elif status in (404, 410):
            label = "endpoint_missing"
        elif 200 <= status < 300:
            body = output.read_bytes()[:MAX_BODY].decode("utf-8", errors="replace")
            label = "valid_config" if valid_config(body, kotlin_constant(source, "CLOUDFLARE_WARP_PEER_PUBKEY"), kotlin_constant(source, "CLOUDFLARE_WARP_PEER_HOST")) else "invalid_config"
        else:
            label = "http_error"
        return label, status, round((time.monotonic() - start) * 1000)


def main():
    source = SOURCE.read_text(encoding="utf-8")
    block = source.split("val DEFAULT_MIRRORS:", 1)[1].split("listOf(", 1)[1].split(")", 1)[0]
    existing = re.findall(r'"(https://[^"]+)"', block)
    if not existing or len(set(existing)) != len(existing):
        raise ValueError("Empty or duplicate mirror list")
    candidates = [url for url in CANDIDATES if url not in existing]
    urls = existing + candidates
    print(f"Existing: {len(existing)}; candidates: {len(candidates)}", flush=True)
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        for url, result in zip(urls, pool.map(lambda u: probe(u, source), urls)):
            label, code, elapsed = result
            row = {"url": url, "existing": url in existing, "status": label, "http": code, "ms": elapsed}
            results.append(row)
            print(json.dumps(row), flush=True)
        missing = [row for row in results if row["status"] == "endpoint_missing"]
        for row, result in zip(missing, pool.map(lambda r: probe(r["url"], source), missing)):
            label, code, elapsed = result
            row["recheck"] = {"status": label, "http": code, "ms": elapsed}
            print("RECHECK " + json.dumps(row), flush=True)
    Path("warp-mirror-audit.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
