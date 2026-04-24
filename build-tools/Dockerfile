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
ARG NDK_SHA256=1a81810f8e45fd91d7c4c2aa17aa1e8efa6c89b2571e3f0625c9cad8a14bdcdc

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
# golang:1.22.12-bookworm — pinned patch version for reproducibility.
# Using bookworm (Debian 12) for glibc compatibility with NDK toolchain.
# ---------------------------------------------------------------------------
FROM golang:1.22.12-bookworm AS builder

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
# gomobile (pinned для reproducibility — F-Droid требование)
# Обновлять одновременно с upstream golang.org/x/mobile releases.
# ---------------------------------------------------------------------------
ARG GOMOBILE_VERSION=v0.0.0-20240806205939-81131f6468ab
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

# The build script is mounted via -v "$PWD:/workspace" at runtime.
# ENTRYPOINT runs it directly so `docker run ... ozero-xray-builder` works
# without specifying the script path.
ENTRYPOINT ["/workspace/build-tools/build_xray.sh"]

# ---------------------------------------------------------------------------
# Labels
# ---------------------------------------------------------------------------
LABEL org.opencontainers.image.title="ozero-xray-builder" \
      org.opencontainers.image.description="Reproducible build env: Xray-core -> Android AAR via gomobile" \
      org.opencontainers.image.source="https://github.com/thesmithmode/Ozero" \
      org.opencontainers.image.base.name="golang:1.22.12-bookworm" \
      com.ozero.ndk.version="r27c" \
      com.ozero.go.version="1.22.12"
