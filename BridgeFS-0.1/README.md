# FileBridge 0.1

Android 11+ 原生 Kotlin 悬浮文件操作桥。

## 云端编译

GitHub Actions 工作流位于 `.github/workflows/build.yml`，也可以在带 Android SDK 的云环境执行：

```bash
gradle :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

## 权限与目录

首次启动按顺序开启悬浮窗权限、所有文件访问权限，再选择项目根目录。所有文件操作都限制在该根目录内。

## 安全边界

本版本没有删除、Shell、无障碍自动操作等能力；不会主动读取剪贴板。只有点击执行时读取输入框，点击复制回执时才写入剪贴板。

## 实现说明

目录选择器返回的 `content://` URI 会在常见 primary/存储卷场景映射为真实路径；无法安全映射时拒绝保存，而不是猜测路径。
