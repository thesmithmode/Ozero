# Dockerfile — reproducible build environment for Ozero native engines
#
# Build:
#   docker build -t ozero-xray-builder -f build-tools/Dockerfile .
# Run:
#   docker run --rm -v "$PWD:/workspace" ozero-xray-builder
#
# To override Xray version or output dir:
#   docker run --rm -v "$PWD:/workspace" \
#     -e XRAY_VERSION=v25.11.0 \
#     -e OUTPUT_DIR=engine-xray/libs \
#     ozero-xray-builder

# ---------------------------------------------------------------------------
# Stage 1: NDK downloader
# Separate stage so NDK download is cached independently from Go toolchain.
# ---------------------------------------------------------------------------
FROM debian:bookworm-slim AS ndk-downloader

# Android NDK r27c — latest stable in the r27 train as of 2025-04
# SHA256 pinned to official Google release (linux x86_64)
#   https://developer.android.com/ndk/downloads
ARG NDK_VERSION=r27c
ARG NDK_ZIP=android-ndk-${NDK_VERSION}-linux.zip
ARG NDK_URL=https://dl.google.com/android/repository/${NDK_ZIP}
# SHA256 of android-ndk-r27c-linux.zip (official Google distribution)
ARG NDK_SHA256=59c2f6dc96743b5daf5d1626684640b20a6bd2b1d85b13156b90333741bad5cc

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt

RUN curl -fsSL -o "${NDK_ZIP}" "${NDK_URL}" \
    && echo "${NDK_SHA256}  ${NDK_ZIP}" | sha256sum --check - \
    && unzip -q "${NDK_ZIP}" \
    && mv "android-ndk-${NDK_VERSION}" android-ndk \
    && rm "${NDK_ZIP}"

# ---------------------------------------------------------------------------
# Stage 2: Go toolchain base
# golang:1.25-bookworm — required for `gomobile init`. gomobile-init unconditionally
# runs `go install gobind@latest`, and the upstream golang.org/x/mobile HEAD now
# requires Go ≥ 1.25 (toolchain directive). Pinning to an older patch fails with:
#   "golang.org/x/mobile@... requires go >= 1.25.0 (running go 1.22.12)".
# Tag `1.25-bookworm` resolves to the current 1.25.x patch — accepted drift in
# exchange for a working build. See manifest.txt for the exact go_version per build.
# ---------------------------------------------------------------------------
FROM golang:1.25-bookworm AS builder

# ---------------------------------------------------------------------------
# System dependencies
# ---------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
        git \
        unzip \
        curl \
        ca-certificates \
        openjdk-17-jdk-headless \
        python3 \
        cmake \
        make \
    && rm -rf /var/lib/apt/lists/*

# Verify JDK
RUN java -version

# ---------------------------------------------------------------------------
# Android NDK (from stage 1)
# ---------------------------------------------------------------------------
COPY --from=ndk-downloader /opt/android-ndk /opt/android-ndk

ENV ANDROID_NDK_ROOT=/opt/android-ndk
ENV ANDROID_NDK_HOME=/opt/android-ndk

# ---------------------------------------------------------------------------
# Android SDK (требуется gomobile bind для AAR упаковки + classes.jar)
#
# gomobile проверяет ANDROID_HOME / ANDROID_SDK_ROOT, fallback на ~/Android/Sdk.
# Без SDK падает с "Android SDK was not found at /root/Android/Sdk".
# Минимальный набор: cmdline-tools + platforms;android-24 + build-tools.
# ---------------------------------------------------------------------------
ARG ANDROID_PLATFORM=android-24
ARG ANDROID_BUILD_TOOLS=34.0.0
ARG ANDROID_CMDLINE_TOOLS=11076708
# SHA-256 для commandlinetools-linux-11076708_latest.zip (Google official).
# Источник: https://developer.android.com/studio#command-tools
# Override через --build-arg ANDROID_CMDLINE_TOOLS_SHA256=<hex64>.
ARG ANDROID_CMDLINE_TOOLS_SHA256=2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258

RUN test -n "${ANDROID_CMDLINE_TOOLS_SHA256}" \
    && mkdir -p /opt/android-sdk/cmdline-tools \
    && cd /opt/android-sdk/cmdline-tools \
    && curl -fsSL -o tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS}_latest.zip" \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  tools.zip" | sha256sum -c - \
    && unzip -q tools.zip \
    && mv cmdline-tools latest \
    && rm tools.zip

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=/opt/android-sdk/cmdline-tools/latest/bin:${PATH}

RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${ANDROID_BUILD_TOOLS}"

# ---------------------------------------------------------------------------
# gomobile (pinned для reproducibility — F-Droid требование)
#
# v0.0.0-20240806... зависит от golang.org/x/tools@v0.24.0, который не
# компилируется на Go 1.25 ("invalid array length -delta * delta"). Поднят
# до HEAD сборки апреля 2026 — синхронно с bump base image до Go 1.25.
# Обновлять одновременно с upstream golang.org/x/mobile releases.
# ---------------------------------------------------------------------------
ARG GOMOBILE_VERSION=v0.0.0-20260410095206-2cfb76559b7b
RUN go install golang.org/x/mobile/cmd/gomobile@${GOMOBILE_VERSION} \
    && go install golang.org/x/mobile/cmd/gobind@${GOMOBILE_VERSION}

# gomobile init must run in an environment with NDK already set
RUN gomobile init

# Verify gomobile is operational
RUN gomobile version

# ---------------------------------------------------------------------------
# Workspace
# ---------------------------------------------------------------------------
WORKDIR /workspace

# Build script mounts via -v "$PWD:/workspace" + invoked explicitly by caller
# (docker run … ozero-xray bash build-tools/build_xray.sh). No ENTRYPOINT —
# binaries.yml workflow uses a single image for multiple commands.

# ---------------------------------------------------------------------------
# Labels
# ---------------------------------------------------------------------------
LABEL org.opencontainers.image.title="ozero-xray-builder" \
      org.opencontainers.image.description="Reproducible build env: Xray-core -> Android AAR via gomobile" \
      org.opencontainers.image.source="https://github.com/thesmithmode/Ozero" \
      org.opencontainers.image.base.name="golang:1.25-bookworm" \
      com.ozero.ndk.version="r27c" \
      com.ozero.go.version="1.25"
