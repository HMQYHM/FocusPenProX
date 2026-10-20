# FocusPen Pro X

**小米焦点触控笔 Pro 手势增强模块** · **Gesture enhancements for Xiaomi Focus Pen Pro**

[简体中文](#简体中文) · [English](#english)

FocusPen Pro X extends Xiaomi's virtual-laser workflow with configurable pen gestures, a stylus mouse, and laser-brush customization. It is designed for rooted Xiaomi tablets running **HyperOS 3–4** and uses LSPosed runtime hooks; it does not patch system partitions.

FocusPen Pro X 为小米焦点触控笔 Pro 增加可配置笔势、手写笔鼠标和激光画笔个性化能力，面向运行 **HyperOS 3–4** 的 Root 小米平板。模块通过 LSPosed 运行时 Hook 工作，不修改系统分区。

> [!WARNING]
> The module runs hooks in system processes. Compatibility has been verified on the device/builds listed below; other models and ROM builds may behave differently. After a system update, test with a small allowlist first.
>
> 本模块会在系统进程中运行 Hook。目前仅下列设备和系统版本有明确验证记录；其他型号或 ROM 版本可能存在差异。系统升级后请先使用少量白名单应用测试。

## 简体中文

### HyperOS 版本支持

- 支持 HyperOS 3 和 HyperOS 4 两套适配路径，可自动识别，也可在“其他设置”中手动选择。
- 已验证设备：小米 Pad 8 Pro（型号 `25091RP04C`），小米焦点触控笔 Pro，`arm64-v8a`。
- HyperOS 3 验证版本：`OS3.0.307.0.WOCCNXM`（Android 16 / API 36）。
- HyperOS 4 验证版本：`OS4.0.4.0.XPYCNXM`（Android 17 / API 37）；用户已确认 0.10.1 功能可用。
- 其他支持该触控笔的平板及系统构建尚未逐一验证，不能据此保证所有设备完全兼容。

### 功能

- 白名单应用手势增强；黑名单优先级最高，名单内应用完全绕过模块 Hook，保留小米原始行为。
- 独立选择手写笔鼠标生效应用；支持鼠标指针位置的左键、右键、单击和按住操作。
- 轻捏两次、轻捏四次及按住手势可配置动作，包括开启虚拟激光或手写笔鼠标、启动应用和系统导航操作。
- 笔身上下滑可发送真实音量键，适用于使用音量键翻页的阅读应用。
- 支持鼠标滚轮操作，可用于文档、演示文稿、短视频及其他支持滚轮输入的应用。
- 可自定义激光画笔颜色，支持纯色、彩虹、空间渐变和闪烁效果。
- 配置应用支持简体中文、繁體中文和 English，并适配横屏、竖屏及预测性返回。

### 安全与恢复

- 不修改或替换 `system`、`product`、`vendor`、`odm` 等系统分区文件，不关闭 SELinux。
- 不要求辅助功能、设备管理权限或常驻 Shell，也不联网、不收集或上传个人数据。
- 高频输入路径使用配置快照；模拟按压带有释放保护和超时机制。
- Hook 异常会被捕获；连续异常时熔断并尽量放行原始事件。
- 关闭总开关会停止新事件接管；取消 LSPosed 作用域或卸载后，重启即可移除内存中的 Hook，无需恢复脚本。

### 安装

1. 从 [GitHub Releases](https://github.com/HMQYHM/FocusPenProX/releases) 下载 APK。
2. 安装应用，在 LSPosed 中启用模块并选择需要的作用域。
3. 重启设备后打开应用，先将普通应用加入白名单，再开启总开关进行测试。
4. 建议将笔记、绘画和游戏应用加入黑名单，以保留原版输入行为。

### 构建

需要 JDK 21、Android SDK Platform 36.1 和 Android Build Tools 36.x。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

正式签名构建：在项目根目录放置不纳入 Git 的 `keystore.properties`，然后执行 ``.\gradlew.bat :app:assembleRelease`。请妥善离线备份签名密钥。

### 问题反馈

提交 Issue 时请附上平板型号、Android / HyperOS 完整版本、LSPosed 与 Root 方案版本、触发问题的手势和前台应用，以及已脱敏的相关日志。请勿上传账号信息或未经脱敏的系统日志。

## English

### HyperOS compatibility

- Includes separate HyperOS 3 and HyperOS 4 adapters, with automatic detection and a manual selector under Other settings.
- Verified device: Xiaomi Pad 8 Pro (model `25091RP04C`) with Xiaomi Focus Pen Pro, `arm64-v8a`.
- HyperOS 3 verified build: `OS3.0.307.0.WOCCNXM` (Android 16 / API 36).
- HyperOS 4 verified build: `OS4.0.4.0.XPYCNXM` (Android 17 / API 37); the user confirmed version 0.10.1 working.
- Other compatible tablets and ROM builds have not all been tested; compatibility on every device is not guaranteed.

### Features

- Gesture enhancement in allowlisted apps. The blacklist has highest priority and completely bypasses module hooks to preserve Xiaomi's original behavior.
- A separately configurable stylus-mouse app scope, with pointer-position left/right clicks and press-and-hold actions.
- Configurable double-pinch, four-pinch, and hold gestures for actions such as enabling the virtual laser or stylus mouse, launching an app, or system navigation.
- Up/down pen gestures can send real volume keys for reading apps that use volume keys to turn pages.
- Mouse-wheel actions for documents, presentations, short-video apps, and other apps that support scrolling.
- Laser-brush customization with solid colors, rainbow, spatial gradients, and flashing effects.
- Simplified Chinese, Traditional Chinese, and English UI, with landscape, portrait, and predictive-back support.

### Safety and recovery

- Does not modify or replace files in `system`, `product`, `vendor`, `odm`, or other system partitions; does not disable SELinux.
- Requires no Accessibility service, device-administrator privilege, persistent shell, or network access. It does not collect or upload personal data.
- High-frequency input handling uses configuration snapshots. Simulated presses include release safeguards and timeouts.
- Hook exceptions are contained; repeated failures trigger a circuit breaker and pass input through where possible.
- Turning off the master switch stops taking over new events. Removing the LSPosed scope or uninstalling the module and rebooting removes in-memory hooks without a recovery script.

### Installation

1. Download the APK from [GitHub Releases](https://github.com/HMQYHM/FocusPenProX/releases).
2. Install the app, enable the module in LSPosed, and select the required scope.
3. Reboot, add an ordinary app to the allowlist, and test with the master switch enabled.
4. Consider adding note-taking, drawing, and game apps to the blacklist to preserve their original input behavior.

### Build

Requirements: JDK 21, Android SDK Platform 36.1, and Android Build Tools 36.x.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

For a signed release, provide an untracked `keystore.properties` file in the project root and run `.\gradlew.bat :app:assembleRelease`. Keep an offline backup of the signing key.

### Issue reports

Please include the tablet model, full Android and HyperOS versions, LSPosed and root solution versions, the affected gesture and foreground app, and relevant logs with personal information removed. Do not upload account details or unredacted system logs.

## License

Copyright © 2026 HMQYHM. Licensed under the [GNU General Public License v3.0 only](LICENSE).
