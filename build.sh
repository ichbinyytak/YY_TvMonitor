#!/bin/bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="YY_TvMonitor"
VERSION="v0.1"
APK_FILE="${APP_NAME}_${VERSION}.apk"

# 加载本地环境配置（支持覆盖构建机与电视IP，保护隐私，避免泄露至公开仓库）
if [ -f "$DIR/.env" ]; then
    # shellcheck disable=SC1091
    source "$DIR/.env"
fi

BUILD_HOST="${BUILD_HOST:-build_server}"
TV_DEVICE="${TV_DEVICE:-192.168.1.100:5555}"

echo "=== 正在将源码同步到编译机 ($BUILD_HOST) ==="
ssh "$BUILD_HOST" "mkdir -p /tmp/cpu_app/src/com/tv/cpu /tmp/cpu_app/bin /tmp/cpu_app/res/values"
scp "$DIR/AndroidManifest.xml" "$BUILD_HOST:/tmp/cpu_app/"
scp -r "$DIR/res" "$BUILD_HOST:/tmp/cpu_app/"
scp -r "$DIR/src" "$BUILD_HOST:/tmp/cpu_app/"

echo "=== 正在 $BUILD_HOST 上编译打包 ${APK_FILE} ==="
ssh "$BUILD_HOST" "cd /tmp/cpu_app && \
  rm -rf bin/* && \
  mkdir -p bin && \
  javac -encoding UTF-8 -source 8 -target 8 -cp /opt/android-sdk/platforms/android-28/android.jar -d bin src/com/tv/cpu/*.java && \
  /opt/android-sdk/build-tools/30.0.3/d8 bin/com/tv/cpu/*.class --output . && \
  /opt/android-sdk/build-tools/30.0.3/aapt package -f -M AndroidManifest.xml -S res -I /opt/android-sdk/platforms/android-28/android.jar -F cpu.apk && \
  zip -u -j cpu.apk classes.dex && \
  /opt/android-sdk/build-tools/30.0.3/zipalign -f -p 4 cpu.apk cpu-aligned.apk && \
  /opt/android-sdk/build-tools/30.0.3/apksigner sign --ks /tmp/gemini_tv/debug.keystore --ks-pass pass:android --key-pass pass:android --out cpu-signed.apk cpu-aligned.apk"

echo "=== 正在拉取生成的 APK 到本地 ==="
scp "$BUILD_HOST:/tmp/cpu_app/cpu-signed.apk" "$DIR/$APK_FILE"
cp -f "$DIR/$APK_FILE" "$DIR/${APP_NAME}.apk"
cp -f "$DIR/$APK_FILE" "$DIR/cpu.apk"

echo "=== 编译完成: $DIR/$APK_FILE ==="
echo "=== (标准主程序已同步: $DIR/${APP_NAME}.apk) ==="

if [ "$1" == "--install" ] || [ "$1" == "-i" ]; then
    echo "=== 正在安装到电视 ($TV_DEVICE) ==="
    adb -s "$TV_DEVICE" install -r "$DIR/$APK_FILE"
    adb -s "$TV_DEVICE" shell appops set com.tv.cpu SYSTEM_ALERT_WINDOW allow
    adb -s "$TV_DEVICE" shell pm grant com.tv.cpu android.permission.DUMP || true
    adb -s "$TV_DEVICE" shell dumpsys deviceidle whitelist +com.tv.cpu
    echo "=== 正在启动 HUD 服务 ==="
    adb -s "$TV_DEVICE" shell am start-foreground-service com.tv.cpu/.HudService
    echo "=== YY电视性能监控 已成功在电视屏幕右上角运行！ ==="
fi
