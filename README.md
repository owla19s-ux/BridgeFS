# BridgeFS

**最新 APK：** [下载 BridgeFS.apk](https://github.com/owla19s-ux/BridgeFS/releases/latest/download/BridgeFS.apk)

**BridgeFS** 是一个 Android 本地文件执行桥：把 AI 对话中的结构化文件指令交给手机上的 BridgeFS 执行，并返回真实执行结果。

本仓库当前版本已经完成这一轮功能测试，**短期冻结，不再继续扩展 BridgeFS 本体功能**。后续如需新能力，应以新的施工轮次或新版本计划处理。

## 当前状态

| 项目 | 状态 |
|---|---|
| 主页面目录浏览 | 已实现 |
| 文件夹工作区勾选 | 已实现 |
| 设置 / 操作提示 / 指令帮助 | 已实现 |
| 首次使用引导 | 已实现 |
| 悬浮机器人与面板 | 已实现并完成本轮测试 |
| AI 指令执行 | 已实现并验证 |
| 文件读写回执 | 已实现并验证 |
| 基础异常提示 | 已实现 |
| Service 弹窗崩溃处理 | 已修复 |
| Java / Kotlin JVM 目标 | 已统一为 Java 17 / Kotlin 17 |
| 云端 APK 构建 | 已验证 |
| GitHub Release 最新 APK | 已验证 |
| GitHub Pages 下载页 | 已验证 |
| 短期版本状态 | **冻结** |

## 这是什么

BridgeFS 的定位不是文件管理器，而是一个很小的“本地执行桥”。

基本流程：

1. 在 BridgeFS 主页面选择可管理的工作区文件夹。
2. 开启悬浮窗权限，并按手机系统要求允许后台运行。
3. 在 AI 对话中生成 BridgeFS 指令。
4. 复制指令到悬浮面板。
5. BridgeFS 在当前工作区内执行。
6. 将执行结果显示为回执，可复制回 AI 对话。

BridgeFS 不要求 AI 直接获得手机文件系统权限；AI 只负责产生结构化指令，BridgeFS 负责本地执行。

## 支持的指令

路径均相对于当前 BridgeFS 工作区。

```text
[list]
```

列出工作区内容。

```text
[read: 相对路径]
```

读取文件。

```text
[write: 相对路径]
文件内容
[/write]
```

创建或覆盖文件。

```text
[edit: 相对路径]
旧内容
====
新内容
[/edit]
```

按旧内容替换为新内容。

```text
[search: *.json]
```

按文件名搜索。

```text
[grep: 关键词]
```

按文件内容搜索。

```text
[path: 相对路径]
```

查看文件或目录的完整路径。

```text
[copy-path: 相对路径]
```

复制完整路径。

```text
[mkdir: 文件夹名]
```

创建文件夹。

一次输入可以包含多条指令，BridgeFS 会按指令出现的顺序执行。

## 当前安全边界

当前版本刻意保持能力很小：

- 文件操作以当前工作区为边界。
- 系统受保护目录不会作为工作区。
- 没有删除文件指令。
- 没有 Shell / 终端执行能力。
- 没有无障碍自动操作能力。
- 不会主动读取系统剪贴板。
- 点击“粘贴”时才通过临时 Activity 读取剪贴板内容。
- 点击“复制”回执或路径时才写入剪贴板。
- `write` 指令缺少 `[/write]` 结束标记时不会写入文件。

## Android 与工程

- Kotlin 原生 Android
- minSdk 30
- targetSdk 35
- compileSdk 35
- Java 17
- Kotlin JVM target 17
- Android 前台 Service
- `TYPE_APPLICATION_OVERLAY` 悬浮层
- RecyclerView 目录浏览
- GitHub Actions 云端构建
- GitHub Release 自动发布最新版 APK

Android 工程位于：

```text
BridgeFS-0.1/
```

应用模块：

```text
BridgeFS-0.1/app/
```

## 权限

BridgeFS 当前需要：

- 悬浮窗权限
- 所有文件访问权限
- 前台服务相关权限

OPPO / ColorOS 等系统可能还需要允许后台运行、自启动，并在最近任务中锁定应用，具体行为由系统版本决定。

## 构建

本地或云端 Android 环境可使用：

```bash
cd BridgeFS-0.1
gradle :app:assembleDebug
```

GitHub Actions：

- `.github/workflows/build.yml`：构建 APK，并自动发布到 GitHub Release 的 `latest`
- `.github/workflows/pages.yml`：构建并发布 GitHub Pages 下载页

当前对外下载的 APK 文件固定为：

```text
BridgeFS.apk
```

下载地址固定为：

```text
https://github.com/owla19s-ux/BridgeFS/releases/latest/download/BridgeFS.apk
```

## 下载

项目的 GitHub Pages 下载页和 README 顶部均提供最新版 APK 下载入口。

下载链接始终指向 GitHub Release 的 `latest`，不会因为新版本构建而需要修改 README 或主页中的链接。

## 项目结构

```text
BridgeFS/
├─ BridgeFS-0.1/              # Android 工程
├─ docs/                      # GitHub Pages 下载页
├─ .github/workflows/         # 云端构建、Release 发布与 Pages
├─ BRIDGEFS_CODE_REVIEW_2026-09-25.md
└─ README.md                  # 仓库总说明
```

APK 不再作为需要手动维护的仓库文件发布；每次成功构建后由 GitHub Actions 自动更新 GitHub Release 的 `latest` 资产。

## 当前冻结说明

这一轮的目标是把 BridgeFS 做成一个**能安装、能运行、能让 AI 通过指令可靠操作本地工作区的最小工具**。

目前已经完成：

- 主页面目录浏览与工作区选择
- 设置页面统一
- 首次使用引导
- 指令帮助
- 悬浮机器人 / 面板交互
- 文件读写与回执
- 错误提示
- 云端构建
- GitHub Release 最新 APK
- APK 下载页

**短期不继续升级 BridgeFS 本体。**

如果未来重新施工，应先重新定义施工目标，再修改代码；不要把冻结版本当成持续开发分支随意堆功能。
