# Changelog

## 0.10.1 - 2026-10-20

### 中文

- 增加 HyperOS 3 / HyperOS 4 / 自动三种适配选择，默认依据系统版本自动识别。
- HyperOS 4 增加 `InputManagerGlobal` 输入注入候选路径，并保留兼容回退；所有入口不可用时放行原始事件。
- 在“其他”页的界面语言下方增加系统版本适配选择，并显示当前检测到的 Android / HyperOS 信息。
- 已在小米 Pad 8 Pro / HyperOS 4 上确认轻捏仍发送 `F19`；HyperOS 4 注入路径尚待用户实测，不宣称已验证兼容。

### English

- Added Auto, HyperOS 3, and HyperOS 4 adapter selection; Auto is the default.
- Added an `InputManagerGlobal` injection candidate for HyperOS 4 with a compatibility fallback. Original events remain unconsumed if no injection endpoint is available.
- Added the system profile selector below Interface language in Other settings, along with detected Android / HyperOS information.
- Confirmed that Xiaomi Pad 8 Pro on HyperOS 4 still emits `F19` for a pinch. The HyperOS 4 injection path remains pending on-device validation and is not claimed as verified compatibility.

## 0.8.1 - 2026-08-04

- 全新 FocusPen Pro X Material 3 配置界面，支持横屏侧栏与竖屏底部导航。
- 支持简体中文、繁體中文和 English，并加入本土化洗牌 Tips。
- 支持白名单手势增强和最高优先级黑名单完全绕过。
- 支持手写笔鼠标、左右键点击与长按、真实音量键、返回、桌面、最近任务和鼠标滚轮。
- 支持全局轻捏四次与轻捏四次并按住，可开启虚拟激光或手写笔鼠标、启动应用或执行导航动作。
- 白名单普通模式的轻捏两次与轻捏两次并按住可开启虚拟激光或手写笔鼠标。
- 增加应用选择器、名单互斥确认、保存按钮、恢复默认手势和预测返回动画。
- 保留异常熔断、按键超时释放、配置快照和失效开放安全机制。

## 0.8.1 - English

- New adaptive Material 3 configuration UI with landscape and portrait navigation.
- Simplified Chinese, Traditional Chinese, and English localization.
- Allowlisted gesture enhancement with a highest-priority, complete-hook-bypass blacklist.
- Stylus mouse, click/hold, real volume keys, navigation actions, and mouse-wheel scrolling.
- Global four-pinch actions and app launching.
- Fail-open hooks, circuit breaking, immutable configuration snapshots, and input-release safeguards.
