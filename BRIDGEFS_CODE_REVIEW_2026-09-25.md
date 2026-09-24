# BridgeFS 当前代码库检查记录

> 检查时间：2026-09-25
> 检查基准：main / `2126469b637f42b7fcd97a8e7c306cc9839aa1e5`
> 性质：代码巡检记录，不代表这些问题已经在实机复现。

## 当前状态

### 已确认
- 指令修复已经进入 main。
- `CommandParser.kt` 已增加裸 `write/edit`、`path`、`copy-path` 解析。
- `CommandExecutor.kt` 已增加 `Path` / `CopyPath`，并使用 `PathSecurity.safe()`。
- `copy-path` 使用 Android `ClipboardManager`。
- `grep` 已加入 500 文件、5 秒、200 结果限制，并记录读取异常。
- Service 的指令执行链已传入 Context。
- 输入框已切换到独立 Overlay Dialog 方案。

### 待实机验证
1. **独立输入 Dialog**
   - `TYPE_APPLICATION_OVERLAY` + `Dialog` 在当前 OPPO/ColorOS 环境是否稳定。
   - 点击粘贴框后是否稳定获得焦点并拉起输入法。
   - 确定/取消及面板收起时是否正确回收。
   - 是否出现新的 `BadTokenException` 或窗口生命周期问题。

2. **裸 write/edit**
   - 验证用户要求的裸格式与代码块格式都能解析。
   - 特别确认多条指令混合输入时解析顺序正确。

3. **path/copy-path**
   - `[path: A3]` 是否返回预期绝对路径。
   - `[copy-path: A3]` 是否真的进入系统剪贴板，并可在其他 App 粘贴。

4. **grep**
   - 500 文件限制。
   - 5 秒限制。
   - 200 条结果限制。
   - 二进制跳过。
   - 读取异常是否出现在回执中。
   - 大目录下是否仍会造成可感知卡顿。

## 值得后续关注的问题

### A. grep 仍然是同步执行
本轮按施工单保留同步版，因此即使有 5 秒上限，Service 调用线程仍可能被阻塞最多约 5 秒。
如果实机仍出现明显卡顿，下一轮优先改为 `Dispatchers.IO`，不要继续堆同步限制。

### B. 二进制判断的异常处理
二进制探测失败目前按“不是二进制”继续读取。若以后遇到特殊权限文件或特殊文件类型，可进一步处理，但暂不属于本轮必修项。

### C. 输入 Dialog 的窗口生命周期
这是本轮最值得实机重点观察的地方。Service Overlay 中使用 Dialog 属于 Android 窗口生命周期敏感区域；当前代码应先实机验证，不预判为必然错误。

## 本轮原则

- 不因为上述“待关注项”提前修改。
- 先构建、安装、按验收表实测。
- 只有出现实际问题，再针对问题施工。
