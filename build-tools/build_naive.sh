#!/usr/bin/env bash
# build_naive.sh — сборка klzgrad/naiveproxy для Android (CLI-бинарь)
#
# Использование:
#   export ANDROID_NDK_ROOT=/opt/android-ndk
#   ./build-tools/build_naive.sh
#
# NaiveProxy — форк Chromium net stack (C++), не Go-проект, поэтому AAR не делаем —
# собираем native binary `naive` для каждой ABI и кладём в engine-naive/libs/.
# Apk-paste этих файлов в `nativeLibraryDir` делает их исполняемыми на device.
#
# Переменные окружения:
#   NAIVE_VERSION  — тег upstream (default: v131.0.6778.85-1)
#   OUTPUT_DIR     — куда положить бинари (default: engine-naive/libs)
#   GO_MODULE      — git URL (default: https://github.com/klzgrad/naiveproxy)
#   ABIS           — целевые ABI (default: arm64-v8a)

set -euo pipefail

log() { echo "[build_naive] $*" >&2; }
die() { log "ERROR: $*"; exit 1; }

NAIVE_VERSION="${NAIVE_VERSION:-v131.0.6778.85-1}"
OUTPUT_DIR="${OUTPUT_DIR:-engine-naive/libs}"
GIT_URL="${GIT_URL:-https://github.com/klzgrad/naiveproxy.git}"
ABIS="${ABIS:-arm64-v8a}"

log "=== NaiveProxy native build ==="
log "Version  : ${NAIVE_VERSION}"
log "ABIs     : ${ABIS}"
log "Output   : ${OUTPUT_DIR}"

if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    die "ANDROID_NDK_ROOT is not set"
fi
if [[ ! -d "${ANDROID_NDK_ROOT}" ]]; then
    die "ANDROID_NDK_ROOT='${ANDROID_NDK_ROOT}' does not exist"
fi
log "NDK root : ${ANDROID_NDK_ROOT}"

# naiveproxy сборка через `src/build.sh` форк-скрипт upstream.
for tool in python3 git curl; do
    command -v "${tool}" &>/dev/null || die "${tool} required"
done

mkdir -p "${OUTPUT_DIR}"

TMP_DIR="$(mktemp -d)"
trap 'log "Cleaning up ${TMP_DIR}..."; rm -rf "${TMP_DIR}"' EXIT
log "Work dir : ${TMP_DIR}"

REPO_DIR="${TMP_DIR}/naiveproxy"
log "Cloning ${GIT_URL} @ ${NAIVE_VERSION}..."
git clone \
    --depth 1 \
    --branch "${NAIVE_VERSION}" \
    "${GIT_URL}" \
    "${REPO_DIR}"
UPSTREAM_SHA="$(git -C "${REPO_DIR}" rev-parse HEAD)"
log "Upstream commit: ${UPSTREAM_SHA}"

# naiveproxy/src/build.sh умеет билдить под ANDROID если задан target_os=android в args.gn.
# Полный билд занимает ~30 мин и тянет ~30 ГБ Chromium-исходников. Skip полная сборка
# в обычном CI: обычно используют release artifact с GitHub Releases.
#
# Стратегия: качаем precompiled tarball из GitHub Releases вместо локальной сборки.
ARCH_TAR_NAME="naiveproxy-${NAIVE_VERSION}-android-${ABIS}.tar.xz"
RELEASE_URL="https://github.com/klzgrad/naiveproxy/releases/download/${NAIVE_VERSION}/${ARCH_TAR_NAME}"

log "Downloading ${RELEASE_URL}..."
curl -fsSL --retry 3 -o "${TMP_DIR}/${ARCH_TAR_NAME}" "${RELEASE_URL}" || \
    die "не удалось скачать ${RELEASE_URL}; локальная сборка не реализована — используйте release artifact"

log "Extracting..."
tar -xJf "${TMP_DIR}/${ARCH_TAR_NAME}" -C "${TMP_DIR}"
NAIVE_BIN="$(find "${TMP_DIR}" -type f -name "naive" | head -1)"
[[ -z "${NAIVE_BIN}" ]] && die "naive binary не найден в распакованном архиве"

DEST="${OUTPUT_DIR}/${ABIS}/libnaive.so"
mkdir -p "$(dirname "${DEST}")"
cp "${NAIVE_BIN}" "${DEST}"
chmod +x "${DEST}"
log "Binary placed: ${DEST}"

OUTPUT_SHA256="${OUTPUT_DIR}/libnaive-${ABIS}.sha256"
if command -v sha256sum &>/dev/null; then
    sha256sum "${DEST}" > "${OUTPUT_SHA256}"
elif command -v shasum &>/dev/null; then
    shasum -a 256 "${DEST}" > "${OUTPUT_SHA256}"
else
    die "Neither sha256sum nor shasum found"
fi
cat "${OUTPUT_SHA256}"

OUTPUT_MANIFEST="${OUTPUT_DIR}/naive.manifest.txt"
{
    echo "naive_version=${NAIVE_VERSION}"
    echo "upstream_commit=${UPSTREAM_SHA}"
    echo "abis=${ABIS}"
    echo "build_timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "${OUTPUT_MANIFEST}"
log "Manifest written to ${OUTPUT_MANIFEST}"

log "=== Build complete ==="
