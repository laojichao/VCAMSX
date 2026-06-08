# VCAMSX - 安卓虚拟摄像头

## 项目概述
基于 Xposed 的虚拟摄像头模块，支持视频替换和 RTMP 直播替换。

## 技术栈
- **开发语言**: Java/Kotlin
- **Hook 框架**: Xposed API
- **最低 API**: 82

## 环境要求
- Android 7.0+
- 已安装 LSPosed 框架

## 使用方法
1. 在 LSPosed 中勾选自己想要的播放平台
2. 在软件中选择自己想要播放的视频
3. 打开视频开关
4. 然后选择平台播放

## 支持替换
1. 支持视频替换
2. 支持 RTMP 直播替换（不稳定）

## 注意事项
1. 视频播放需要与平台播放的格式相同，基本支持 9:16 的视频
2. 画面黑屏，相机启动失败，因为视频解码有问题，请多次点击翻转摄像头
3. 不同软件对于硬解码和软解码的要求不同

## 构建命令
```bash
./gradlew assembleRelease
```

## 致谢
提供 hook 代码：https://github.com/Xposed-Modules-Repo/com.example.vcam
