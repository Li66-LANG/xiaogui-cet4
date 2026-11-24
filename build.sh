#!/bin/sh
# 统一构建脚本：固定 SDK / Gradle 路径，避免每次手写环境变量
export ANDROID_HOME="D:/workbuddy学习区/四级知识库/安卓软件/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="D:/workbuddy学习区/四级知识库/安卓软件/gradle-home"
export GRADLE_OPTS="-Dfile.encoding=UTF-8 -Dorg.gradle.jvmargs=-Xmx2g"
cd "D:/workbuddy学习区/四级知识库/安卓软件/项目" || exit 1
exec "D:/workbuddy学习区/四级知识库/安卓软件/gradle-8.7/bin/gradle" --no-daemon "$@"
