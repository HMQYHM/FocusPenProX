# FocusPen Pro X

小米焦点触控笔 Pro 手势增强模块
Gesture enhancement module for Xiaomi Focus Pen Pro

[简体中文](#简体中文) · [English](#english)

> [!WARNING]
> 本模块会通过 LSPosed 在 `system_server` 中运行。请仅在已验证设备上使用，并在升级后先用少量白名单应用测试。

## 简体中文

FocusPen Pro X 是面向已 Root 小米平板和小米焦点触控笔 Pro 的 LSPosed 模块。它把小米虚拟激光能力扩展为可配置的手写笔鼠标和手势动作，同时坚持“不修改系统文件、禁用或卸载即恢复”的设计。

### 功能

- Material 3 配置应用，支持横屏、竖屏、预测返回和非线性动画。
- 简体中文、繁體中文和 English。
- 白名单应用启用普通手势与手写笔鼠标增强。
- 黑名单拥有最高优先级：完全不 Hook，保留小米原版触控笔和激光画笔逻辑。
- 手写笔鼠标支持左键、右键、点击、长按和当前指针位置操作。
- 上下滑可发送真实音量键，兼容多数使用音量键翻页的阅读应用。
- 支持鼠标滚轮，可用于文档、PowerPoint、短视频及其他支持滚轮输入的应用。
- 支持返回、桌面、最近任务、仅消费和交还系统等动作。
- 全局轻捏四次和轻捏四次并按住可开启虚拟激光或手写笔鼠标、启动应用或执行其他动作。
- 白名单普通模式的轻捏两次和轻捏两次并按住可开启虚拟激光或手写笔鼠标。
- 名单互斥确认、已选应用置顶、灰色冲突项置底及显式保存。

### 已验证环境

- 设备代号：`piano`
- 型号标识：`25091RP04C`
- Android：16 / API 36
- HyperOS：`OS3.0.307.0.WPYCNXM`
- 架构：`arm64-v8a`
- 触控笔：小米焦点触控笔 Pro（VID/PID `0022:5081`）
- LSPosed 作用域：`android`（系统框架）

其他设备、ROM 或 HyperOS 大版本尚未验证。能力签名不匹配时，模块会拒绝安装高风险输入 Hook 并保持系统原行为。

### 安装

1. 从 [Releases](https://github.com/HMQYHM/FocusPenProX/releases) 下载 APK。
2. 安装 APK，在 LSPosed 中启用模块并保留推荐的 `android` 作用域。
3. 重启设备。
4. 打开 FocusPen Pro X，先加入一个普通应用到白名单，再开启总开关测试。
5. 建议把笔记、绘画和游戏应用加入黑名单，完整保留原版输入行为。

### 安全与恢复

- 不修改或替换 `system`、`product`、`vendor`、`odm` 等系统文件。
- 不关闭 SELinux，不要求辅助功能、设备管理权限或常驻 Shell。
- 应用不联网，不收集或上传个人数据。
- 高频输入路径不读取磁盘或网络，只读取不可变配置快照。
- 模拟按下动作具有安全释放和超时保护。
- Hook 回调捕获异常；连续异常会触发熔断并放行原始事件。
- 关闭总开关会立即停止新事件接管。
- 取消 LSPosed 作用域或卸载后，重启设备即可从内存中完全移除 Hook，不需要恢复脚本。

### 构建

要求：

- JDK 21
- Android SDK Platform 36.1
- Android Build Tools 36.x

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

正式签名可在项目根目录放置不纳入 Git 的 `keystore.properties`：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

随后执行 `.\gradlew.bat :app:assembleRelease`。请离线备份签名密钥；丢失后无法提供可覆盖安装的更新。

### 问题反馈

提交 Issue 时请附上：

- 平板型号、Android、HyperOS 完整版本号；
- LSPosed 和 Root 方案版本；
- 出现问题的手势、前台应用和激光状态；
- 已移除账号、路径等个人信息的 LSPosed 日志。

请勿上传完整系统 APK/JAR、账号信息或未经脱敏的日志。

## English

FocusPen Pro X is an LSPosed module for rooted Xiaomi tablets and Xiaomi Focus Pen Pro. It extends Xiaomi's virtual-laser pipeline into a configurable stylus mouse and gesture-action system without modifying system partitions.

### Highlights

- Adaptive Material 3 UI with landscape and portrait navigation.
- Simplified Chinese, Traditional Chinese, and English.
- Allowlisted gesture and stylus-mouse enhancement.
- Highest-priority blacklist that bypasses every module hook.
- Left/right click, hold, real volume keys, navigation actions, and mouse-wheel scrolling.
- Global four-pinch actions and app launching.
- Fail-open hooks, circuit breaking, configuration snapshots, and stuck-input release safeguards.
- No network permission, analytics, or data upload.

### Compatibility

Currently verified only on `piano` / `25091RP04C`, Android 16, HyperOS `OS3.0.307.0.WPYCNXM`, arm64-v8a, and Xiaomi Focus Pen Pro. Other devices and ROM versions are untested.

### Installation

1. Download the APK from [Releases](https://github.com/HMQYHM/FocusPenProX/releases).
2. Install it, enable the module in LSPosed, and keep the recommended `android` scope.
3. Reboot the device.
4. Add one low-risk test app to the allowlist before enabling the master switch.

## Related project

[HyperOSKeyboardFix](https://github.com/HMQYHM/HyperOSKeyboardFix) — improves physical-keyboard shortcut compatibility in allowlisted apps on HyperOS tablets.

## License

Copyright © 2026 HMQYHM. Licensed under the [GNU General Public License v3.0 only](LICENSE).
