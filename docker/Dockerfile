# Build image for the i30 PD dashboard APKs: everything scripts/build.py and the signing step need, so a build is
# one `docker run` with the checkout mounted at /src (docker/build.ps1 does that). Nothing from the repo is baked in
# except the entry script, so the image is reused across commits and branches.
#
#   docker build -t i30pd-build docker
#   docker run --rm -v "<checkout>:/src" i30pd-build                # tests, both APKs, signed -> build/apk/
#   docker run --rm -v "<checkout>:/src" i30pd-build dashboard --no-tests
#   docker run --rm -it -v "<checkout>:/src" i30pd-build shell      # a shell with the tools on PATH
#
# JDK 17: the sources compile at Java 7 level (-source 7 -target 7), which JDK 20 and later refuse.
FROM eclipse-temurin:17-jdk-jammy

ARG CMDLINE_TOOLS=11076708
ARG BUILD_TOOLS=34.0.0
ARG PLATFORM=android-17
# The public AOSP platform test key the released APKs are signed with (i30pd_port/app/README.md): the head unit
# refuses testkey, and an update must carry the installed APK's certificate.
ARG KEY_TAG=android-4.2.2_r1
ARG KEY_SHA1=27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA

ENV ANDROID_SDK=/opt/android-sdk \
    KEY_DIR=/opt/keys \
    BUILD_TOOLS=${BUILD_TOOLS} \
    PLATFORM=${PLATFORM} \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUTF8=1 \
    LANG=C.UTF-8 \
    GIT_OPTIONAL_LOCKS=0

RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 git unzip curl ca-certificates openssl \
 && rm -rf /var/lib/apt/lists/* \
 && git config --system --add safe.directory '*' \
 && git config --system core.fileMode false

# Android SDK: the API 17 platform (android.jar) and the build tools (aapt2, d8, zipalign, apksigner). `sdkmanager
# --licenses` accepts the Android SDK licence (https://developer.android.com/studio/terms): build this image only if
# you accept it. sdkmanager itself is not kept (the build does not use it).
RUN curl -fsSL -o /tmp/clt.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" \
 && mkdir -p /tmp/clt && unzip -q /tmp/clt.zip -d /tmp/clt \
 && (yes | /tmp/clt/cmdline-tools/bin/sdkmanager --sdk_root="$ANDROID_SDK" --licenses > /dev/null) \
 && /tmp/clt/cmdline-tools/bin/sdkmanager --sdk_root="$ANDROID_SDK" "platforms;${PLATFORM}" "build-tools;${BUILD_TOOLS}" \
 && rm -rf /tmp/clt /tmp/clt.zip "$ANDROID_SDK/.temp" /root/.android \
 && test -f "$ANDROID_SDK/platforms/${PLATFORM}/android.jar" \
 && test -f "$ANDROID_SDK/build-tools/${BUILD_TOOLS}/lib/d8.jar" \
 && "$ANDROID_SDK/build-tools/${BUILD_TOOLS}/aapt2" version

# The signing key, checked against the certificate fingerprint the releases carry.
RUN mkdir -p "$KEY_DIR" \
 && for f in platform.pk8 platform.x509.pem; do \
      curl -fsSL "https://android.googlesource.com/platform/build/+/refs/tags/${KEY_TAG}/target/product/security/$f?format=TEXT" \
        | base64 -d > "$KEY_DIR/$f" || exit 1; \
    done \
 && openssl x509 -in "$KEY_DIR/platform.x509.pem" -noout -fingerprint -sha1 | tee /dev/stderr | grep -qi "${KEY_SHA1}"

COPY i30pd-build /usr/local/bin/i30pd-build
RUN sed -i 's/\r$//' /usr/local/bin/i30pd-build && chmod +x /usr/local/bin/i30pd-build

WORKDIR /src
ENTRYPOINT ["i30pd-build"]
CMD ["all"]
