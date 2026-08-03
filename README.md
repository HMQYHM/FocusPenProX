# FocusPen Pro X

小米焦点触控笔 Pro 手势增强模块
Gesture enhancement module for Xiaomi Focus Pen Pro

[简体中文](#简体中文) · [English](#english)

> [!WARNING]
> 本模块会通过 LSPosed 在 `system_server` 中运行。请仅在已验证设备上使用，并在升级后先用少量白名单应用测试。
> This module runs inside `system_server` through LSPosed. Use it only on a verified device, and test with a small allowlist after system updates.

## 简体中文

FocusPen Pro X 是面向已 Root 小米平板和小米焦点触控笔 Pro 的 LSPosed 模块。它把小米虚拟激光能力扩展为可配置的手写笔鼠标和手势动作。

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

- 设备：小米 Pad 8 Pro
- 型号标识：`25091RP04C`
- Android：16 / API 36
- 架构：`arm64-v8a`
- 触控笔：小米焦点触控笔 Pro（VID/PID `0022:5081`）

预计兼容绝大部分支持小米焦点触控笔 Pro 的 HyperOS 3 设备。

### 安装

1. 从 [Releases](https://github.com/HMQYHM/FocusPenProX/releases) 下载 APK。
2. 安装 APK，并在 LSPosed 中启用模块。
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

### 相关项目

[HyperOSKeyboardFix](https://github.com/HMQYHM/HyperOSKeyboardFix) — 改善 HyperOS 平板白名单应用中的实体键盘快捷键兼容性。

### 许可证

Copyright © 2026 HMQYHM。本项目仅以 [GNU General Public License v3.0](LICENSE) 授权。

## English

FocusPen Pro X is an LSPosed module for rooted Xiaomi tablets and Xiaomi Focus Pen Pro. It extends Xiaomi's virtual-laser pipeline into a configurable stylus mouse and gesture-action system.

### Features

- Material 3 configuration app with landscape and portrait layouts, predictive back, and nonlinear animations.
- Simplified Chinese, Traditional Chinese, and English.
- Ordinary gestures and stylus-mouse enhancements in allowlisted apps.
- A highest-priority blacklist that bypasses all hooks and preserves Xiaomi's original pen and laser-brush behavior.
- Stylus-mouse left click, right click, tap, hold, and actions at the current pointer position.
- Up and down swipes can send real volume keys, compatible with most reading apps that use volume keys for page turning.
- Mouse-wheel input for documents, PowerPoint, short-video apps, and other apps that support scrolling.
- Back, Home, Recents, consume-only, and pass-through actions.
- Global four-pinch and four-pinch-hold gestures can enable the virtual laser or stylus mouse, launch an app, or run another action.
- In ordinary allowlist mode, double-pinch and double-pinch-hold gestures can enable the virtual laser or stylus mouse.
- Mutual-exclusion confirmation for lists, selected apps pinned to the top, conflicting disabled apps placed at the bottom, and explicit saving.

### Verified environment

- Device: Xiaomi Pad 8 Pro
- Model identifier: `25091RP04C`
- Android: 16 / API 36
- Architecture: `arm64-v8a`
- Stylus: Xiaomi Focus Pen Pro (VID/PID `0022:5081`)

Expected to be compatible with most HyperOS 3 devices that support Xiaomi Focus Pen Pro.

### Installation

1. Download the APK from [Releases](https://github.com/HMQYHM/FocusPenProX/releases).
2. Install it and enable the module in LSPosed.
3. Reboot the device.
4. Open FocusPen Pro X, add an ordinary app to the allowlist, and then enable the master switch for testing.
5. Add note-taking, drawing, and game apps to the blacklist to preserve their original input behavior.

### Safety and recovery

- Does not modify or replace files in `system`, `product`, `vendor`, `odm`, or other system partitions.
- Does not disable SELinux and does not require Accessibility, device-administrator privileges, or a persistent shell.
- Does not connect to the internet or collect or upload personal data.
- High-frequency input paths do not read from disk or the network and only access immutable configuration snapshots.
- Simulated press actions include safe release and timeout protection.
- Hook callbacks catch exceptions; repeated failures trigger circuit breaking and pass original events through.
- Turning off the master switch immediately stops taking over new events.
- Removing the LSPosed scope or uninstalling the module, followed by a reboot, removes all in-memory hooks without a recovery script.

### Build

Requirements:

- JDK 21
- Android SDK Platform 36.1
- Android Build Tools 36.x

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

For a release build, place an untracked `keystore.properties` file in the project root:

```properties
storeFile=/absolute/path/to/release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Then run `.\gradlew.bat :app:assembleRelease`. Keep an offline backup of the signing key; without it, future updates cannot replace an installed release.

### Issue reports

Please include:

- Tablet model and full Android and HyperOS versions;
- LSPosed version and root solution version;
- The affected gesture, foreground app, and laser state;
- LSPosed logs with account names, paths, and other personal information removed.

Do not upload complete system APK/JAR files, account information, or unredacted logs.

### Related project

[HyperOSKeyboardFix](https://github.com/HMQYHM/HyperOSKeyboardFix) — improves physical-keyboard shortcut compatibility in allowlisted apps on HyperOS tablets.

### License

Copyright © 2026 HMQYHM. Licensed under the [GNU General Public License v3.0 only](LICENSE).
