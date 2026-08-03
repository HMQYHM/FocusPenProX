package io.github.hmqyhm.focuspenpro.hook

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import io.github.hmqyhm.focuspenpro.config.ConfigContract
import io.github.hmqyhm.focuspenpro.gesture.PinchGestureMachine
import io.github.hmqyhm.focuspenpro.gesture.HierarchicalTapHoldDetector
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class FocusPenRuntime(
    private val context: Context,
    systemServerClassLoader: ClassLoader,
) {
    private val workerThread = HandlerThread("FocusPenPro").apply { start() }
    private val worker = Handler(workerThread.looper)
    private val foreground = AtomicReference<String?>(null)
    private val laserEnabled = AtomicBoolean(false)
    private val explicitLaserOff = AtomicBoolean(false)
    private val touchFilmManager = AtomicReference<Any?>(null)
    private val laserController = AtomicReference<Any?>(null)
    private val systemCursorApplied = AtomicBoolean(false)
    private val circuitOpen = AtomicBoolean(false)
    private val mouseHoldActive = AtomicBoolean(false)
    private val completeExitPending = AtomicBoolean(false)
    private val globalHoldAwaitingRelease = AtomicBoolean(false)
    private val lastModuleLaserEnableUptime = AtomicLong(Long.MIN_VALUE)
    private val suppressLaserEnableUntil = AtomicLong(0L)
    private val startupExitGuardActive = AtomicBoolean(false)
    private val replayingVendorPinch = AtomicBoolean(false)
    private val mouseHoldWatchdog = AtomicReference<Runnable?>(null)
    private val pendingUnknownForeground = AtomicReference<Runnable?>(null)
    private val vendorReplayLock = Any()
    private var vendorReplayDown: KeyEvent? = null
    private var vendorReplayUp: KeyEvent? = null
    private var vendorReplayInteractive = false
    private var vendorReplayTask: Runnable? = null
    private val reporter = RuntimeReporter(context.contentResolver, worker)
    private val injector = InputInjector(
        context,
        laserController,
        systemServerClassLoader,
    )
    private lateinit var config: SystemConfigClient
    private lateinit var gesture: PinchGestureMachine
    private lateinit var offLaserTapDetector: HierarchicalTapHoldDetector

    private var errorWindowStart = 0L
    private var errorCount = 0

    init {
        config = SystemConfigClient(context.contentResolver, worker) {
            offLaserTapDetector.reset()
            globalHoldAwaitingRelease.set(false)
            discardPendingVendorPinch()
            cancelActiveGesture("配置变化")
            synchronizeCursorMode()
            reportStatus("配置已刷新")
        }
        gesture = PinchGestureMachine(
            scheduler = PinchGestureMachine.Scheduler { delay, block ->
                val runnable = Runnable(block)
                worker.postDelayed(runnable, delay)
                object : PinchGestureMachine.Cancellable {
                    override fun cancel() {
                        worker.removeCallbacks(runnable)
                    }
                }
            },
            intervalMs = { config.snapshot.get().multiClickMs },
            onGesture = ::onGesture,
        )
        offLaserTapDetector = HierarchicalTapHoldDetector(
            maxTaps = 4,
            tapMilestones = setOf(2, 4),
            holdMilestones = setOf(2, 4),
            scheduler = HierarchicalTapHoldDetector.Scheduler { delay, block ->
                val runnable = Runnable(block)
                worker.postDelayed(runnable, delay)
                HierarchicalTapHoldDetector.Cancellable {
                    worker.removeCallbacks(runnable)
                }
            },
            intervalMs = { config.snapshot.get().multiClickMs },
            holdMs = { FOUR_TAP_HOLD_MS },
            onGesture = { tapCount, hold ->
                if (tapCount == 4) {
                    val snapshot = config.snapshot.get()
                    if (snapshot.enabled &&
                        snapshot.globalActionsEnabled &&
                        !snapshot.blocksAllHooks(foreground.get())
                    ) {
                        discardPendingVendorPinch()
                        if (hold) globalHoldAwaitingRelease.set(true)
                        worker.post { performGlobalMultiTapAction(hold) }
                    }
                } else if (tapCount == 2) {
                    val snapshot = config.snapshot.get()
                    val gestureId = if (hold) {
                        ConfigContract.GESTURE_OFF_DOUBLE_HOLD
                    } else {
                        ConfigContract.GESTURE_OFF_DOUBLE_TAP
                    }
                    val action = snapshot.gestureActions[gestureId]
                        ?: ConfigContract.ACTION_SYSTEM
                    val capabilityReady = when {
                        actionRequiresMouse(action) -> injector.canInjectMouse(true)
                        actionRequiresKeyInjection(action) -> injector.canInjectKey()
                        else -> true
                    }
                    if (snapshot.appliesTo(foreground.get()) &&
                        action != ConfigContract.ACTION_SYSTEM &&
                        capabilityReady
                    ) {
                        discardPendingVendorPinch()
                        if (hold) globalHoldAwaitingRelease.set(true)
                        worker.post {
                            val success = executeConfiguredAction(action, allowHold = false)
                            reporter.event(
                                "普通模式${if (hold) "双击长按" else "双击"} → " +
                                    "${actionLabel(action)} ${if (success) "成功" else "失败"}",
                            )
                        }
                    }
                }
            },
            onIncomplete = {},
        )
    }

    fun start() {
        XposedBridge.log("FocusPenPro: ${injector.capabilitySummary()}")
        config.start()
        registerCancellationReceiver()
        reporter.event("运行时初始化；激光=${laserEnabled.get()}")
        reportStatus("Hook 已安装，等待配置")
    }

    fun attachLaserController(controller: Any) {
        laserController.set(controller)
        reporter.event("已取得 LaserPointerController")
        val controllerActive = runCatching {
            XposedHelpers.getBooleanField(controller, "mvirtuallaser")
        }.getOrDefault(false)
        if (controllerActive) {
            updateLaser(true, "LaserPointerController.mvirtuallaser")
        }
        synchronizeCursorMode(force = true)
    }

    fun attachTouchFilmManager(manager: Any) {
        if (touchFilmManager.getAndSet(manager) !== manager) {
            reporter.event("已取得 MiuiStylusTouchFilmManager")
        }
    }

    fun updateLaser(enabled: Boolean, source: String) {
        if (enabled && source == "LaserView.setPosition" && explicitLaserOff.get()) {
            // Xiaomi may deliver a final queued position after its close callback. It is
            // not a new activation and must not resurrect mouse mappings.
            return
        }
        if (enabled && isLaserEnableSuppressed()) {
            // A fast close can finish before Xiaomi's asynchronous enable pipeline.
            // Ignore its late state callbacks until the bounded exit guard expires.
            return
        }
        if (enabled) explicitLaserOff.set(false) else explicitLaserOff.set(true)
        if (laserEnabled.getAndSet(enabled) != enabled) {
            cancelActiveGesture("激光状态变化")
            reporter.event("虚拟激光=${if (enabled) "开启" else "关闭"} ($source)")
            synchronizeCursorMode()
            reportStatus("虚拟激光状态已更新")
        }
    }

    fun updateForeground(windowState: Any?) {
        val next = extractPackage(windowState)
        if (next == null && foreground.get() != null) {
            val delayed = object : Runnable {
                override fun run() {
                    if (pendingUnknownForeground.compareAndSet(this, null)) {
                        applyForeground(null)
                    }
                }
            }
            pendingUnknownForeground.getAndSet(delayed)?.let(worker::removeCallbacks)
            worker.postDelayed(delayed, UNKNOWN_FOREGROUND_GRACE_MS)
            return
        }
        pendingUnknownForeground.getAndSet(null)?.let(worker::removeCallbacks)
        applyForeground(next)
    }

    private fun applyForeground(next: String?) {
        if (foreground.getAndSet(next) != next) {
            offLaserTapDetector.reset()
            globalHoldAwaitingRelease.set(false)
            discardPendingVendorPinch()
            cancelActiveGesture("前台应用切换")
            reporter.event("前台应用=${next ?: "未知"}")
            synchronizeCursorMode()
            reportStatus("前台窗口已更新")
        }
    }

    fun shouldUseSystemCursor(): Boolean =
        !circuitOpen.get() &&
            !isLaserEnableSuppressed() &&
            laserEnabled.get() &&
            config.snapshot.get().appliesTo(foreground.get())

    /**
     * LaserView.onDraw is itself the authoritative signal that Xiaomi is presenting
     * the virtual laser. The ROM setting may already be 0 while that view is active.
     */
    fun shouldReplaceLaserPresentation(): Boolean =
        !circuitOpen.get() &&
            !isLaserEnableSuppressed() &&
            config.snapshot.get().appliesTo(foreground.get())

    /**
     * Xiaomi can request drawing mode while a mapped mouse-button hold is active.
     * Suppress that transition only for the lifetime of the injected hold.  A
     * cursor overlay by itself is not sufficient evidence: the same LaserView is
     * reused when the user switches to Xiaomi's original drawing mode.
     */
    fun shouldSuppressVendorDrawing(): Boolean =
        shouldReplaceLaserPresentation() &&
            laserEnabled.get() &&
            mouseHoldActive.get()

    fun intercept(event: KeyEvent): Boolean {
        if (circuitOpen.get()) return false
        return try {
            interceptSafely(event)
        } catch (throwable: Throwable) {
            recordError(throwable)
            false
        }
    }

    /**
     * A global four-tap sequence cannot coexist with Xiaomi handling each constituent tap:
     * after a full exit the vendor reacts to the first tap and re-enters Bluetooth laser
     * mode before our fourth tap arrives. While either global multi-tap action is enabled,
     * consume only the verified F19 pinch stream in the vendor touch-film manager.
     */
    fun shouldConsumeVendorOffLaserPinch(event: KeyEvent, interactive: Boolean): Boolean {
        if (replayingVendorPinch.get() ||
            circuitOpen.get() ||
            laserEnabled.get() ||
            event.scanCode != CompatProfile.SCAN_PINCH_F19
        ) {
            return false
        }
        val snapshot = config.snapshot.get()
        if (snapshot.blocksAllHooks(foreground.get())) return false
        val normalGestureEnabled = snapshot.appliesTo(foreground.get()) &&
            (snapshot.gestureActions[ConfigContract.GESTURE_OFF_DOUBLE_TAP] !=
                ConfigContract.ACTION_SYSTEM ||
                snapshot.gestureActions[ConfigContract.GESTURE_OFF_DOUBLE_HOLD] !=
                ConfigContract.ACTION_SYSTEM)
        val globalGestureEnabled = snapshot.globalActionsEnabled &&
            (snapshot.tripleAction != ConfigContract.TRIPLE_ACTION_NONE ||
                snapshot.fourHoldAction != ConfigContract.TRIPLE_ACTION_NONE)
        val consume = snapshot.enabled && (globalGestureEnabled || normalGestureEnabled)
        if (consume) bufferVendorPinchForFallback(event, interactive)
        return consume
    }

    /**
     * Xiaomi handles the first off-laser tap immediately, while the module must wait for a
     * possible fourth tap. Preserve the first verified key pair and replay it after the
     * multi-tap window only when no four-tap gesture was completed.
     */
    private fun bufferVendorPinchForFallback(event: KeyEvent, interactive: Boolean) {
        synchronized(vendorReplayLock) {
            vendorReplayTask?.let(worker::removeCallbacks)
            vendorReplayTask = null
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                if (vendorReplayDown == null) {
                    vendorReplayDown = KeyEvent(event)
                    vendorReplayInteractive = interactive
                }
                return
            }
            if (event.action == KeyEvent.ACTION_UP && vendorReplayDown != null) {
                if (vendorReplayUp == null) vendorReplayUp = KeyEvent(event)
                val task = Runnable { replayPendingVendorPinch() }
                vendorReplayTask = task
                worker.postDelayed(
                    task,
                    config.snapshot.get().multiClickMs.coerceIn(220L, 600L) +
                        VENDOR_REPLAY_GRACE_MS,
                )
            }
        }
    }

    private fun replayPendingVendorPinch() {
        val pending = synchronized(vendorReplayLock) {
            val down = vendorReplayDown
            val up = vendorReplayUp
            val interactive = vendorReplayInteractive
            vendorReplayDown = null
            vendorReplayUp = null
            vendorReplayTask = null
            if (down == null || up == null) null else Triple(down, up, interactive)
        } ?: return
        offLaserTapDetector.reset()
        val manager = touchFilmManager.get() ?: return
        replayingVendorPinch.set(true)
        try {
            val downResult = runCatching {
                XposedHelpers.callMethod(
                    manager,
                    "interceptKeyBeforeQueueing",
                    pending.first,
                    pending.third,
                )
            }
            val upResult = runCatching {
                XposedHelpers.callMethod(
                    manager,
                    "interceptKeyBeforeQueueing",
                    pending.second,
                    pending.third,
                )
            }
            if (downResult.isSuccess && upResult.isSuccess) {
                reporter.event("单击未形成四击 → 已恢复小米快捷环事件")
            } else {
                downResult.exceptionOrNull()?.let(::recordError)
                upResult.exceptionOrNull()?.let(::recordError)
            }
        } finally {
            replayingVendorPinch.set(false)
        }
    }

    private fun discardPendingVendorPinch() {
        synchronized(vendorReplayLock) {
            vendorReplayTask?.let(worker::removeCallbacks)
            vendorReplayTask = null
            vendorReplayDown = null
            vendorReplayUp = null
        }
    }

    fun onLaserPositionChanged(x: Float, y: Float) {
        if (!mouseHoldActive.get()) return
        runCatching {
            if (injector.injectMouseMoveDuringHold(x, y)) {
                refreshMouseHoldWatchdog()
            }
        }.onFailure(::recordError)
    }

    /**
     * Xiaomi stops presenting the laser cursor after a finger touches the screen, but its
     * virtual-laser input mode can remain active. Mirror the shortcut manager's verified
     * full-exit path so hidden mouse mappings cannot continue accepting pen button events.
     */
    fun onPointerEvent(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN ||
            !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) ||
            event.pointerCount < 1 ||
            event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER ||
            circuitOpen.get() ||
            !laserEnabled.get() ||
            !config.snapshot.get().appliesTo(foreground.get())
        ) {
            return
        }
        armStartupExitGuardIfNeeded("手指触屏")
        requestCompleteLaserExit("手指触屏")
    }

    fun onLaserPresentationHidden(source: String, wasMousePresentation: Boolean) {
        val snapshot = config.snapshot.get()
        if (circuitOpen.get() ||
            !snapshot.appliesTo(foreground.get()) ||
            !wasMousePresentation
        ) return
        val reason = "鼠标显示消失 ($source)"
        explicitLaserOff.set(true)
        laserEnabled.set(false)
        offLaserTapDetector.reset()
        globalHoldAwaitingRelease.set(false)
        discardPendingVendorPinch()
        cancelActiveGesture(reason)
        LaserCursorRenderer.restoreAll()
        armStartupExitGuardIfNeeded(reason)
        requestCompleteLaserExit(reason)
    }

    private fun armStartupExitGuardIfNeeded(reason: String) {
        val now = android.os.SystemClock.uptimeMillis()
        val requestedAt = lastModuleLaserEnableUptime.get()
        if (requestedAt == Long.MIN_VALUE ||
            now < requestedAt ||
            now - requestedAt > LASER_STARTUP_RACE_WINDOW_MS ||
            !startupExitGuardActive.compareAndSet(false, true)
        ) {
            return
        }

        val guardUntil = now + LASER_STARTUP_EXIT_GUARD_MS
        suppressLaserEnableUntil.set(guardUntil)
        laserEnabled.set(false)
        reporter.event("$reason：命中四击启动竞态保护")

        STARTUP_EXIT_RETRY_DELAYS_MS.forEach { delay ->
            worker.postDelayed({ forceVendorLaserOffDuringStartupExit() }, delay)
        }
        worker.postDelayed({
            if (suppressLaserEnableUntil.compareAndSet(guardUntil, 0L)) {
                startupExitGuardActive.set(false)
                lastModuleLaserEnableUptime.set(Long.MIN_VALUE)
                reporter.event("四击启动竞态保护已解除")
                reportStatus("手写笔鼠标已完整退出")
            }
        }, LASER_STARTUP_EXIT_GUARD_MS)
    }

    private fun forceVendorLaserOffDuringStartupExit() {
        if (!startupExitGuardActive.get() || !isLaserEnableSuppressed()) return
        laserEnabled.set(false)
        cancelActiveGesture("四击启动竞态清理")
        LaserCursorRenderer.restoreAll()
        laserController.get()?.let { controller ->
            runCatching {
                XposedHelpers.callMethod(controller, "turnoffvirtuallaser", 0)
            }.onFailure(::recordError)
        }
    }

    private fun isLaserEnableSuppressed(): Boolean =
        android.os.SystemClock.uptimeMillis() < suppressLaserEnableUntil.get()

    private fun requestCompleteLaserExit(reason: String) {
        // Stop interception before leaving the current system callback. Cleanup and the
        // vendor call run on our worker to avoid re-entering pointer/window dispatch.
        explicitLaserOff.set(true)
        laserEnabled.set(false)
        if (!completeExitPending.compareAndSet(false, true)) return
        worker.post {
            try {
                cancelActiveGesture(reason)
                LaserCursorRenderer.restoreAll()
                val controller = laserController.get()
                val success = controller != null && runCatching {
                    XposedHelpers.callMethod(controller, "turnoffvirtuallaser", 0)
                }.isSuccess
                reporter.event("$reason → 完整退出手写笔鼠标${if (success) "成功" else "失败"}")
                reportStatus(
                    if (success) "$reason，已退出手写笔鼠标"
                    else "$reason，模块映射已停止；厂商退出调用失败",
                )
            } catch (throwable: Throwable) {
                recordError(throwable)
            } finally {
                completeExitPending.set(false)
            }
        }
    }

    private fun interceptSafely(event: KeyEvent): Boolean {
        val snapshot = config.snapshot.get()
        val target = foreground.get()
        if (snapshot.blocksAllHooks(target)) {
            offLaserTapDetector.reset()
            globalHoldAwaitingRelease.set(false)
            discardPendingVendorPinch()
            cancelActiveGesture("黑名单应用")
            return false
        }
        if (event.scanCode == CompatProfile.SCAN_PINCH_F19 &&
            globalHoldAwaitingRelease.get()
        ) {
            if (event.action == KeyEvent.ACTION_UP) {
                globalHoldAwaitingRelease.set(false)
            }
            // The fourth DOWN was consumed while laser was off. Do not expose its
            // repeats/final UP to Xiaomi after the hold action changes laser state.
            return true
        }
        val actualLaser = laserEnabled.get()
        if (event.scanCode == CompatProfile.SCAN_PINCH_F19 && !actualLaser) {
            val globalGestureEnabled = snapshot.globalActionsEnabled &&
                (snapshot.tripleAction != ConfigContract.TRIPLE_ACTION_NONE ||
                    snapshot.fourHoldAction != ConfigContract.TRIPLE_ACTION_NONE)
            val normalGestureEnabled = snapshot.appliesTo(target) &&
                (snapshot.gestureActions[ConfigContract.GESTURE_OFF_DOUBLE_TAP] !=
                    ConfigContract.ACTION_SYSTEM ||
                    snapshot.gestureActions[ConfigContract.GESTURE_OFF_DOUBLE_HOLD] !=
                    ConfigContract.ACTION_SYSTEM)
            if (!snapshot.enabled || (!globalGestureEnabled && !normalGestureEnabled)) {
                offLaserTapDetector.reset()
                return false
            }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) offLaserTapDetector.onDown(event.eventTime)
                }
                KeyEvent.ACTION_UP -> {
                    val heldMs = (event.eventTime - event.downTime).coerceAtLeast(0L)
                    offLaserTapDetector.onUp(event.eventTime, heldMs)
                }
            }
            // Observe globally without consuming the original off-laser behavior.
            return false
        }
        if (!actualLaser &&
            (event.keyCode == CompatProfile.KEY_SLIDE_UP ||
                event.keyCode == CompatProfile.KEY_SLIDE_DOWN)
        ) {
            if (!snapshot.appliesTo(target)) return false
            val up = event.keyCode == CompatProfile.KEY_SLIDE_UP
            val gestureId = if (up) {
                ConfigContract.GESTURE_OFF_SWIPE_UP
            } else {
                ConfigContract.GESTURE_OFF_SWIPE_DOWN
            }
            val action = snapshot.gestureActions[gestureId] ?: ConfigContract.ACTION_SYSTEM
            if (action == ConfigContract.ACTION_SYSTEM) return false
            if (actionRequiresKeyInjection(action) && !injector.canInjectKey()) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val success = executeConfiguredAction(action, allowHold = false)
                reporter.event(
                    "普通模式${if (up) "上滑" else "下滑"} → ${actionLabel(action)} " +
                        if (success) "成功" else "失败",
                )
                if (!success) return false
            }
            return event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
        }
        offLaserTapDetector.reset()
        if (!snapshot.appliesTo(target) || !actualLaser) {
            cancelActiveGesture("规则不再匹配")
            return false
        }

        return when {
            event.scanCode == CompatProfile.SCAN_PINCH_F19 -> {
                val useSystemCursor = false
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val pinchActions = listOf(
                        snapshot.gestureActions[ConfigContract.GESTURE_PINCH],
                        snapshot.gestureActions[ConfigContract.GESTURE_PINCH_HOLD],
                        snapshot.gestureActions[ConfigContract.GESTURE_DOUBLE_TAP],
                    ).filterNotNull()
                    if (pinchActions.any(::actionRequiresMouse) &&
                        !injector.canInjectMouse(useSystemCursor)
                    ) return false
                    if (pinchActions.any(::actionRequiresKeyInjection) &&
                        !injector.canInjectKey()
                    ) return false
                }
                val accepted = when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount > 0) {
                            if (mouseHoldActive.get()) refreshMouseHoldWatchdog()
                            true
                        } else {
                            gesture.onDown()
                        }
                    }
                    KeyEvent.ACTION_UP -> gesture.onUp()
                    else -> false
                }
                if (accepted) {
                    reporter.event(
                        "原始事件 PINCH " +
                            if (event.action == KeyEvent.ACTION_DOWN) "DOWN 已接管"
                            else "UP 已接管",
                    )
                }
                accepted
            }
            event.keyCode == CompatProfile.KEY_SLIDE_UP ||
                event.keyCode == CompatProfile.KEY_SLIDE_DOWN -> {
                val up = event.keyCode == CompatProfile.KEY_SLIDE_UP
                val gestureId = if (up) {
                    ConfigContract.GESTURE_SWIPE_UP
                } else {
                    ConfigContract.GESTURE_SWIPE_DOWN
                }
                val action = snapshot.gestureActions[gestureId]
                    ?: ConfigContract.DEFAULT_GESTURE_ACTIONS.getValue(gestureId)
                if (actionRequiresMouse(action) && !injector.canInjectMouse(false)) return false
                if (actionRequiresKeyInjection(action) && !injector.canInjectKey()) return false
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    val success = executeConfiguredAction(action, allowHold = false)
                    reporter.event(
                        "${if (up) "上滑" else "下滑"} → ${actionLabel(action)} " +
                            if (success) "成功" else "失败",
                    )
                    if (!success) return false
                }
                event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP
            }
            else -> false
        }
    }

    private fun onGesture(gesture: PinchGestureMachine.Gesture) {
        try {
            when (gesture) {
                PinchGestureMachine.Gesture.LONG_PINCH_END -> {
                    releaseMouseHold("轻捏长按松开")
                    return
                }
                PinchGestureMachine.Gesture.LONG_PINCH_CANCEL -> {
                    releaseMouseHold("轻捏长按取消")
                    return
                }
                PinchGestureMachine.Gesture.DOUBLE_TAP_HOLD_END -> {
                    releaseMouseHold("双击长按松开")
                    return
                }
                PinchGestureMachine.Gesture.DOUBLE_TAP_HOLD_CANCEL -> {
                    releaseMouseHold("双击长按取消")
                    return
                }
                else -> Unit
            }
            if (circuitOpen.get() ||
                !config.snapshot.get().appliesTo(foreground.get()) ||
                !laserEnabled.get()
            ) {
                return
            }
            if (gesture == PinchGestureMachine.Gesture.LONG_PINCH_START ||
                gesture == PinchGestureMachine.Gesture.DOUBLE_TAP_HOLD_START
            ) {
                val gestureId = if (gesture == PinchGestureMachine.Gesture.LONG_PINCH_START) {
                    ConfigContract.GESTURE_PINCH_HOLD
                } else {
                    ConfigContract.GESTURE_DOUBLE_HOLD
                }
                val action = config.snapshot.get().gestureActions[gestureId]
                    ?: ConfigContract.DEFAULT_GESTURE_ACTIONS.getValue(gestureId)
                val success = executeConfiguredAction(action, allowHold = true)
                reporter.event(
                    "${if (gestureId == ConfigContract.GESTURE_PINCH_HOLD) "单击长按" else "双击长按"}" +
                        " → ${actionLabel(action)} ${if (success) "成功" else "失败"}",
                )
                return
            }
            val gestureId = when (gesture) {
                PinchGestureMachine.Gesture.SINGLE_PINCH -> ConfigContract.GESTURE_PINCH
                PinchGestureMachine.Gesture.DOUBLE_TAP -> ConfigContract.GESTURE_DOUBLE_TAP
                else -> return
            }
            val action = config.snapshot.get().gestureActions[gestureId]
                ?: ConfigContract.DEFAULT_GESTURE_ACTIONS.getValue(gestureId)
            val success = executeConfiguredAction(action, allowHold = false)
            reporter.event(
                "${gesture.name} → ${actionLabel(action)} " +
                    if (success) "成功" else "失败",
            )
        } catch (throwable: Throwable) {
            recordError(throwable)
        }
    }

    private fun performGlobalMultiTapAction(hold: Boolean) {
        val snapshot = config.snapshot.get()
        if (!snapshot.enabled ||
            !snapshot.globalActionsEnabled ||
            snapshot.blocksAllHooks(foreground.get()) ||
            (!hold && laserEnabled.get())
        ) return
        val action = if (hold) snapshot.fourHoldAction else snapshot.tripleAction
        val packageName = if (hold) snapshot.fourHoldPackage else snapshot.triplePackage
        val gestureName = if (hold) "全局四击长按" else "全局四击"
        when (action) {
            ConfigContract.TRIPLE_ACTION_ENABLE_LASER -> {
                val success = enableVirtualLaser()
                reporter.event("$gestureName → 开启虚拟激光${if (success) "成功" else "失败"}")
                if (!success) reportStatus("$gestureName 开启激光失败，已保持系统原行为")
            }
            ConfigContract.TRIPLE_ACTION_LAUNCH_APP -> {
                val targetPackage = packageName.trim()
                val success = targetPackage.isNotEmpty() && runCatching {
                    val intent = context.packageManager
                        .getLaunchIntentForPackage(targetPackage)
                        ?: error("No launcher activity for $targetPackage")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }.isSuccess
                reporter.event(
                    "$gestureName → 启动${targetPackage.ifBlank { "未指定应用" }}" +
                        if (success) "成功" else "失败",
                )
                if (!success) reportStatus("$gestureName 启动应用失败，请重新选择应用")
            }
            ConfigContract.TRIPLE_ACTION_NONE -> Unit
            else -> {
                val success = executeConfiguredAction(action, allowHold = false)
                reporter.event(
                    "$gestureName → ${actionLabel(action)} ${if (success) "成功" else "失败"}",
                )
            }
        }
    }

    private fun extractPackage(windowState: Any?): String? {
        if (windowState == null) return null
        return runCatching {
            XposedHelpers.callMethod(windowState, "getOwningPackage") as? String
        }.getOrNull()?.takeIf(String::isNotBlank)
            ?: runCatching {
                val attrs = XposedHelpers.callMethod(windowState, "getAttrs")
                XposedHelpers.getObjectField(attrs, "packageName") as? String
            }.getOrNull()?.takeIf(String::isNotBlank)
    }

    private fun registerCancellationReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    offLaserTapDetector.reset()
                    globalHoldAwaitingRelease.set(false)
                    discardPendingVendorPinch()
                    cancelActiveGesture(intent?.action ?: "状态广播")
                    reporter.event("状态机取消：${intent?.action ?: "未知广播"}")
                }
            },
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun recordError(throwable: Throwable) {
        XposedBridge.log("FocusPenPro: callback error: ${throwable.stackTraceToString()}")
        val now = android.os.SystemClock.uptimeMillis()
        synchronized(this) {
            if (now - errorWindowStart > 60_000L) {
                errorWindowStart = now
                errorCount = 0
            }
            errorCount += 1
            if (errorCount >= 5) {
                circuitOpen.set(true)
                cancelActiveGesture("异常熔断")
                reporter.event("熔断器已打开：60 秒内发生 5 次异常，后续全部放行")
                reportStatus("异常熔断：已停止拦截")
            }
        }
    }

    private fun reportStatus(message: String) {
        reporter.status(
            active = !circuitOpen.get(),
            compatible = true,
            message = message,
            laser = laserEnabled.get(),
            foreground = foreground.get(),
        )
    }

    private fun executeConfiguredAction(action: String, allowHold: Boolean): Boolean =
        when (action) {
            ConfigContract.ACTION_CONSUME -> true
            ConfigContract.TRIPLE_ACTION_ENABLE_LASER -> enableVirtualLaser()
            ConfigContract.ACTION_MOUSE_LEFT_CLICK ->
                injector.injectMouseClick(MotionEvent.BUTTON_PRIMARY, false)
            ConfigContract.ACTION_MOUSE_RIGHT_CLICK ->
                injector.injectMouseClick(MotionEvent.BUTTON_SECONDARY, false)
            ConfigContract.ACTION_MOUSE_LEFT_HOLD -> if (allowHold) {
                startMouseHold(MotionEvent.BUTTON_PRIMARY)
            } else {
                injector.injectMouseClick(MotionEvent.BUTTON_PRIMARY, false)
            }
            ConfigContract.ACTION_MOUSE_RIGHT_HOLD -> if (allowHold) {
                startMouseHold(MotionEvent.BUTTON_SECONDARY)
            } else {
                injector.injectMouseClick(MotionEvent.BUTTON_SECONDARY, false)
            }
            ConfigContract.ACTION_VOLUME_UP -> injector.injectKeyClick(KeyEvent.KEYCODE_VOLUME_UP)
            ConfigContract.ACTION_VOLUME_DOWN ->
                injector.injectKeyClick(KeyEvent.KEYCODE_VOLUME_DOWN)
            ConfigContract.ACTION_BACK -> injector.injectKeyClick(KeyEvent.KEYCODE_BACK)
            ConfigContract.ACTION_HOME -> injector.injectKeyClick(KeyEvent.KEYCODE_HOME)
            ConfigContract.ACTION_RECENTS -> injector.injectKeyClick(KeyEvent.KEYCODE_APP_SWITCH)
            ConfigContract.ACTION_SCROLL_UP -> injector.injectMouseScroll(
                scrollMagnitude(),
                preferSystemCursor = !laserEnabled.get(),
            )
            ConfigContract.ACTION_SCROLL_DOWN -> injector.injectMouseScroll(
                -scrollMagnitude(),
                preferSystemCursor = !laserEnabled.get(),
            )
            else -> false
        }

    private fun enableVirtualLaser(): Boolean {
        val manager = touchFilmManager.get()
        lastModuleLaserEnableUptime.set(android.os.SystemClock.uptimeMillis())
        val success = manager != null && runCatching {
            // Same high-level entry used by Xiaomi's shortcut ring, initializing
            // both the laser layer and the pen movement pipeline.
            XposedHelpers.callMethod(manager, "enableVirtualLaser")
        }.isSuccess
        if (!success) lastModuleLaserEnableUptime.set(Long.MIN_VALUE)
        return success
    }

    private fun scrollMagnitude(): Float = when (config.snapshot.get().scrollAmount) {
        ConfigContract.SCROLL_SHORT -> 1f
        ConfigContract.SCROLL_LONG -> 6f
        else -> 3f
    }

    private fun actionRequiresMouse(action: String): Boolean =
        action == ConfigContract.ACTION_MOUSE_LEFT_CLICK ||
            action == ConfigContract.ACTION_MOUSE_LEFT_HOLD ||
            action == ConfigContract.ACTION_MOUSE_RIGHT_CLICK ||
            action == ConfigContract.ACTION_MOUSE_RIGHT_HOLD ||
            action == ConfigContract.ACTION_SCROLL_UP ||
            action == ConfigContract.ACTION_SCROLL_DOWN

    private fun actionRequiresKeyInjection(action: String): Boolean =
        action != ConfigContract.ACTION_SYSTEM &&
            action != ConfigContract.ACTION_CONSUME &&
            action != ConfigContract.TRIPLE_ACTION_ENABLE_LASER &&
            !actionRequiresMouse(action)

    private fun actionLabel(action: String): String = when (action) {
        ConfigContract.ACTION_SYSTEM -> "交给系统"
        ConfigContract.ACTION_CONSUME -> "仅消费"
        ConfigContract.ACTION_MOUSE_LEFT_CLICK -> "鼠标左键"
        ConfigContract.ACTION_MOUSE_LEFT_HOLD -> "鼠标左键保持"
        ConfigContract.ACTION_MOUSE_RIGHT_CLICK -> "鼠标右键"
        ConfigContract.ACTION_MOUSE_RIGHT_HOLD -> "鼠标右键保持"
        ConfigContract.ACTION_VOLUME_UP -> "音量增加"
        ConfigContract.ACTION_VOLUME_DOWN -> "音量降低"
        ConfigContract.ACTION_BACK -> "返回"
        ConfigContract.ACTION_HOME -> "桌面"
        ConfigContract.ACTION_RECENTS -> "最近任务"
        ConfigContract.ACTION_SCROLL_UP -> "鼠标滚轮向上"
        ConfigContract.ACTION_SCROLL_DOWN -> "鼠标滚轮向下"
        ConfigContract.TRIPLE_ACTION_ENABLE_LASER -> "开启虚拟激光或手写笔鼠标"
        else -> "未知动作"
    }

    private fun startMouseHold(button: Int): Boolean {
        val success = injector.injectMouseButtonDown(
            button,
            false,
        )
        if (!success) {
            injector.releaseMouseButton()
            reporter.event("轻捏长按 → 鼠标键按下失败，已安全释放")
            return false
        }
        mouseHoldActive.set(true)
        val watchdog = Runnable {
            if (mouseHoldActive.getAndSet(false)) {
                injector.releaseMouseButton()
                reporter.event("轻捏长按连续 60 秒无移动，已强制释放鼠标键")
            }
            mouseHoldWatchdog.set(null)
        }
        mouseHoldWatchdog.getAndSet(watchdog)?.let(worker::removeCallbacks)
        worker.postDelayed(watchdog, MOUSE_HOLD_IDLE_TIMEOUT_MS)
        reporter.event("轻捏长按 → ${if (button == MotionEvent.BUTTON_PRIMARY) "左键" else "右键"}保持")
        return true
    }

    private fun refreshMouseHoldWatchdog() {
        val watchdog = mouseHoldWatchdog.get() ?: return
        worker.removeCallbacks(watchdog)
        worker.postDelayed(watchdog, MOUSE_HOLD_IDLE_TIMEOUT_MS)
    }

    private fun releaseMouseHold(reason: String) {
        mouseHoldWatchdog.getAndSet(null)?.let(worker::removeCallbacks)
        if (mouseHoldActive.getAndSet(false)) {
            val success = injector.releaseMouseButton()
            reporter.event("$reason → 鼠标键${if (success) "已释放" else "释放失败"}")
        } else {
            injector.releaseMouseButton()
        }
    }

    private fun cancelActiveGesture(reason: String) {
        gesture.cancel()
        releaseMouseHold(reason)
    }

    private fun synchronizeCursorMode(force: Boolean = false) {
        systemCursorApplied.set(false)
        if (!shouldReplaceLaserPresentation() || !laserEnabled.get()) {
            LaserCursorRenderer.restoreAll()
        }
    }

    private companion object {
        const val VENDOR_REPLAY_GRACE_MS = 30L
        const val MOUSE_HOLD_IDLE_TIMEOUT_MS = 60_000L
        const val UNKNOWN_FOREGROUND_GRACE_MS = 250L
        const val FOUR_TAP_HOLD_MS = 650L
        const val LASER_STARTUP_RACE_WINDOW_MS = 2_000L
        const val LASER_STARTUP_EXIT_GUARD_MS = 1_600L
        val STARTUP_EXIT_RETRY_DELAYS_MS = longArrayOf(120L, 480L, 1_100L)
    }
}
