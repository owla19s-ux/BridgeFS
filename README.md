# BridgeFS

> **AI → Android 本地执行桥**

BridgeFS 是一个 Android 原生本地执行工具：把 AI 生成的结构化指令交给 Android，在用户授权的本地目录中执行文件操作，并返回可记录的执行回执。

**项目状态：实验性原型 / 持续开发中**

[下载 APK](https://owla19s-ux.github.io/BridgeFS/) · [项目主页](https://owla19s-ux.github.io/BridgeFS/) · [GitHub](https://github.com/owla19s-ux/BridgeFS)

---

## 为什么做 BridgeFS

AI 很擅长理解需求、生成内容和规划任务，但它通常无法直接操作手机上的真实文件。

BridgeFS 尝试建立一条清晰的本地执行链：

**AI → 结构化指令 → BridgeFS → Android 本地执行 → Receipt 回执**

它不是让 AI 获得无限制的手机控制权，而是把“AI 能说什么”和“手机实际允许做什么”分开。

---

## 当前版本

### BridgeFS 0.1 / v0.1.2

当前版本主要验证：

- Android 原生运行
- 悬浮机器人 / 工作面板
- 本地项目目录选择
- 目录读取
- 文件写入
- 结构化命令解析
- 执行结果回执
- 路径边界限制
- GitHub Actions 云端构建 APK

### 已验证

目前已经在 Android 实机验证过本地目录读写：

`[list]` / `[write: 文件名]` 等结构化指令可以作用于绑定的项目目录，并返回执行结果。

---

## 安全边界

BridgeFS 目前刻意保持能力边界：

- 文件操作限制在用户选择的项目根目录
- 当前版本没有删除文件能力
- 当前版本没有 Shell 执行能力
- 当前版本没有无障碍自动操作能力
- 不主动读取系统剪贴板
- 只有用户点击执行时读取输入内容
- 只有用户点击复制回执时写入剪贴板
- 无法安全映射的目录路径会拒绝保存，而不是猜测路径

> BridgeFS 的目标不是“给 AI 一台远程手机”，而是提供一个**受边界约束的本地执行端**。

---

## 技术栈

- Kotlin
- Android SDK
- AndroidX
- Gradle
- GitHub Actions
- Android 原生悬浮窗 `WindowManager`

当前构建环境：

- compileSdk 35
- targetSdk 35
- minSdk 30
- Java / Kotlin JVM target 17

---

## 项目结构

```
BridgeFS/
├── .github/
│   └── workflows/
│       ├── build.yml
│       └── pages.yml
├── BridgeFS-0.1/
│   ├── app/
│   ├── build.gradle
│   ├── gradle.properties
│   └── settings.gradle
├── docs/
│   └── index.html
└── BRIDGEFS_CODE_REVIEW_2026-09-25.md
```

---

## 云端构建

项目使用 GitHub Actions 构建 Debug APK。

构建流程：

1. 准备 Java 17 与 Android SDK
2. 从 GitHub Actions Secret 恢复固定 Debug Keystore
3. 执行 Gradle 构建
4. 上传 APK Artifact
5. 项目主页提供可下载 APK

**签名密钥不会提交到仓库。**

---

## 后续方向

BridgeFS 正在从单纯的“文件操作桥”继续发展为 AI 本地执行工作台。

计划逐步探索：

- 更完整的 AI 指令协议
- Receipt / 执行回执标准化
- 本地文件浏览与编辑
- 浏览器与项目记录
- AI API 接入
- 多角色 AI 协作
- 权限与能力映射
- 与新的 A-BridgeFS / AI+ 架构衔接

这些内容中，部分仍属于设计或探索阶段，不代表已经实现。

---

## 开发状态

| 模块 | 状态 |
|---|---|
| Android 基础工程 | 已实现 |
| 本地目录绑定 | 已实现 |
| 文件读取 / 写入 | 已验证 |
| 结构化指令执行 | 已验证 |
| 执行回执 | 已实现 |
| 悬浮机器人 / 面板 | 开发中 |
| AI API 接入 | 已设计未实现 |
| 多角色协作 | 已设计未实现 |
| A-BridgeFS | 规划中 |

---

## License

当前项目处于早期实验阶段。许可证策略将在项目进入稳定公开阶段后进一步确定。

---

**BridgeFS · 给 AI 一双可以被约束的手。**
