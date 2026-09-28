# BridgeFS 0.1

BridgeFS 是一个 Android 本地文件执行桥：AI 或其他输入源生成结构化指令，BridgeFS 在用户选择的本地工作区内执行并返回真实结果。

## 当前状态

本轮功能测试已完成，当前版本进入短期冻结状态。后续新功能不直接堆叠到冻结版本。

## 使用流程

1. 首次启动完成操作提示。
2. 在主页面浏览手机存储并勾选需要管理的文件夹。
3. 在设置中确认悬浮窗权限。
4. 按 OPPO / ColorOS 等系统要求允许后台运行、自启动或锁定最近任务。
5. 从 AI 对话复制 BridgeFS 指令到悬浮面板。
6. 执行后查看回执。

## 指令

支持：

- `[list]`
- `[read: 相对路径]`
- `[write: 相对路径] ... [/write]`
- `[edit: 相对路径] ... ==== ... [/edit]`
- `[search: *.json]`
- `[grep: 关键词]`
- `[path: 相对路径]`
- `[copy-path: 相对路径]`
- `[mkdir: 文件夹名]`

路径均相对于当前工作区。

## 安全边界

当前版本没有删除文件、Shell / 终端、无障碍自动操作等能力，也不会主动读取剪贴板。只有用户点击“粘贴”时才读取剪贴板；复制回执或路径时才写入剪贴板。

`write` 指令缺少 `[/write]` 时会被拒绝，不会写入文件。

## 工程

- minSdk 30
- targetSdk 35
- compileSdk 35
- Java 17
- Kotlin JVM target 17
- Android 原生 Kotlin
- 工程目录：`BridgeFS-0.1`

构建：

```bash
cd BridgeFS-0.1
gradle :app:assembleDebug
```

GitHub Actions 会执行同样的 Android Debug 构建，并上传 APK artifact。

## 相关文件

- 根目录 `README.md`：仓库总说明
- `.github/workflows/build.yml`：APK 构建
- `.github/workflows/pages.yml`：下载页发布
- `release/BridgeFS.apk`：固定正式版 APK
- `docs/index.html`：下载页
- `BRIDGEFS_CODE_REVIEW_2026-09-25.md`：代码检查记录

**本工程当前作为冻结版本保存。**
