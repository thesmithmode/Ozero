#!/usr/bin/env bash
# tools/iperf3-runner.sh
# Поднимает локальный iperf3 server в Docker, прогоняет throughput тест и
# выводит mean Mbps + p50/p95 в stdout. Результаты сохраняются в
# throughput-results.json в текущей директории.
#
# Использование:
#   ./tools/iperf3-runner.sh [duration_seconds] [output_file]
#   OZERO_BENCH=1 ./tools/iperf3-runner.sh
#
# Зависимости: docker, iperf3, python3 (для парсинга JSON)

set -euo pipefail

DURATION="${1:-10}"
OUTPUT_FILE="${2:-throughput-results.json}"
IPERF3_SERVER_PORT=5201
IPERF3_CONTAINER="ozero-iperf3-server"
IPERF3_IMAGE="networkstatic/iperf3:latest"

log() { echo "[iperf3-runner] $*" >&2; }
die() { echo "[iperf3-runner] ERROR: $*" >&2; exit 1; }

cleanup() {
    log "Останавливаю iperf3 сервер..."
    docker rm -f "$IPERF3_CONTAINER" >/dev/null 2>&1 || true
}
trap cleanup EXIT

# Проверяем зависимости
command -v docker >/dev/null 2>&1 || die "docker не найден"
command -v python3 >/dev/null 2>&1 || die "python3 не найден"

log "Запускаю iperf3 сервер в Docker (порт $IPERF3_SERVER_PORT)..."
docker rm -f "$IPERF3_CONTAINER" >/dev/null 2>&1 || true
docker run -d \
    --name "$IPERF3_CONTAINER" \
    -p "${IPERF3_SERVER_PORT}:${IPERF3_SERVER_PORT}" \
    "$IPERF3_IMAGE" \
    -s \
    >/dev/null

# Ждем поднятия сервера
sleep 2

log "Прогреваю соединение (1 сек)..."
iperf3 -c 127.0.0.1 -p "$IPERF3_SERVER_PORT" -t 1 --json >/dev/null 2>&1 || true

log "Запускаю throughput тест (${DURATION}s, 5 параллельных потоков)..."
RAW_JSON=$(iperf3 -c 127.0.0.1 \
    -p "$IPERF3_SERVER_PORT" \
    -t "$DURATION" \
    -P 5 \
    --json 2>&1) || die "iperf3 тест завершился с ошибкой: $RAW_JSON"

# Парсим результаты через Python
RESULT=$(python3 - <<'PYEOF'
import sys
import json
import statistics

raw = sys.stdin.read()
data = json.loads(raw)

intervals = data.get("intervals", [])
throughputs = []
for interval in intervals:
    streams = interval.get("streams", [])
    # суммируем bits_per_second по всем потокам для каждого интервала
    total_bps = sum(s.get("bits_per_second", 0) for s in streams)
    throughputs.append(total_bps / 1_000_000)  # в Mbps

if not throughputs:
    # fallback: берём итоговую сумму
    end = data.get("end", {})
    sum_received = end.get("sum_received", {})
    mbps = sum_received.get("bits_per_second", 0) / 1_000_000
    throughputs = [mbps]

mean_mbps = statistics.mean(throughputs)
sorted_tp = sorted(throughputs)
n = len(sorted_tp)
p50 = sorted_tp[int(n * 0.5)]
p95 = sorted_tp[min(int(n * 0.95), n - 1)]

result = {
    "mean_mbps": round(mean_mbps, 2),
    "p50_mbps": round(p50, 2),
    "p95_mbps": round(p95, 2),
    "samples": len(throughputs),
    "duration_seconds": data.get("start", {}).get("test_start", {}).get("duration", 0),
    "raw_intervals_mbps": [round(x, 2) for x in throughputs],
}
print(json.dumps(result, indent=2))
PYEOF
) <<< "$RAW_JSON"

echo "$RESULT" > "$OUTPUT_FILE"

log "Результаты сохранены в $OUTPUT_FILE"
echo ""
echo "=== Throughput Results ==="
python3 -c "
import json, sys
d = json.loads(sys.stdin.read())
print(f\"Mean:  {d['mean_mbps']:.2f} Mbps\")
print(f\"p50:   {d['p50_mbps']:.2f} Mbps\")
print(f\"p95:   {d['p95_mbps']:.2f} Mbps\")
print(f\"Samples: {d['samples']}\")
" <<< "$RESULT"
