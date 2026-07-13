# Build-only image for the PP68 Sales Tracking Android app (frontend).
# Produces app-debug.apk — this image does not run the app (Android needs a device/emulator).
#
# Usage (ARG defaults below already match backend/.env — override only if it drifts):
#   docker build -t pp68-app-build .
#   docker create --name extract pp68-app-build && \
#     docker cp extract:/app/app/build/outputs/apk/debug/app-debug.apk . && \
#     docker rm extract
#
# app/google-services.json must exist on disk before building (Firebase config, gitignored).

FROM eclipse-temurin:17-jdk-jammy AS build

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends unzip curl && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    curl -sSL https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d ${ANDROID_SDK_ROOT}/cmdline-tools && \
    mv ${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

RUN yes | sdkmanager --licenses --sdk_root=${ANDROID_SDK_ROOT} >/dev/null && \
    sdkmanager --sdk_root=${ANDROID_SDK_ROOT} "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

WORKDIR /app

# Cache Gradle wrapper/dependency resolution before copying full source
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY app/build.gradle.kts app/build.gradle.kts
RUN chmod +x gradlew && ./gradlew --version --no-daemon

COPY . .

ARG MAPS_API_KEY="AIzaSyDKmJSLtEmvghV_sT-flPYMjtkfkCOZG8Y"
# ARG POSTGREST_URL="https://postgrest-279493695905.asia-southeast1.run.app/"
# ARG BASE_AUTH_URL="http://192.168.15.177:8080"
ARG POSTGREST_URL=postgresql://postgres:your_password@192.168.15.182:5432/postgres
ARG BASE_AUTH_URL=http://192.168.15.177:8080
ARG UPLOAD_URL="https://upload-visit-photo-279493695905.asia-southeast1.run.app/"
ARG JWT_SECRET="sales-app-super-secret-key-2026-practical-project"
RUN printf "MAPS_API_KEY=%s\nPOSTGREST_URL=%s\nBASE_AUTH_URL=%s\nUPLOAD_URL=%s\nJWT_SECRET=%s\nsdk.dir=%s\n" \
      "$MAPS_API_KEY" "$POSTGREST_URL" "$BASE_AUTH_URL" "$UPLOAD_URL" "$JWT_SECRET" "$ANDROID_SDK_ROOT" \
      > local.properties

RUN ./gradlew :app:assembleDebug --no-daemon
