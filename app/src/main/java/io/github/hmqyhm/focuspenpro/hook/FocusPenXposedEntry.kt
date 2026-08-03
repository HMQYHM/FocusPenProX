package io.github.hmqyhm.focuspenpro.hook

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicReference

class FocusPenXposedEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "android" || lpparam.processName != "android") return

        if (!CompatProfile.deviceMatches()) {
            XposedBridge.log(
                "FocusPenPro: unsupported device ${CompatProfile.description()}; no hooks installed",
            )
            return
        }

        runCatching { install(lpparam.classLoader) }
            .onFailure {
                XposedBridge.log(
                    "FocusPenPro: installation failed; all events remain original\n" +
                        it.stackTraceToString(),
                )
            }
    }

    private fun install(classLoader: ClassLoader) {
        val touchFilmClass = XposedHelpers.findClass(
            CompatProfile.TOUCH_FILM_CLASS,
            classLoader,
        )
        val laserClass = XposedHelpers.findClass(
            CompatProfile.LASER_CONTROLLER_CLASS,
            classLoader,
        )
        val laserViewClass = XposedHelpers.findClass(
            CompatProfile.LASER_VIEW_CLASS,
            classLoader,
        )
        val shortcutClass = XposedHelpers.findClass(
            CompatProfile.SHORTCUT_MANAGER_CLASS,
            classLoader,
        )
        val policyClass = XposedHelpers.findClass(
            CompatProfile.POLICY_CLASS,
            classLoader,
        )
        verifyCapabilities(
            touchFilmClass,
            shortcutClass,
            laserClass,
            laserViewClass,
            policyClass,
        )

        val runtime = AtomicReference<FocusPenRuntime?>()
        val pendingLaserController = AtomicReference<Any?>()
        val initLock = Any()

        val ensureRuntime: (Any) -> FocusPenRuntime? = fun(source: Any): FocusPenRuntime? {
            runtime.get()?.let { return it }
            synchronized(initLock) {
                runtime.get()?.let { return it }
                val context = findContext(source) ?: run {
                    XposedBridge.log(
                        "FocusPenPro: Context unavailable from ${source.javaClass.name}; fail-open",
                    )
                    return null
                }
                return runCatching {
                    FocusPenRuntime(
                        context,
                        source.javaClass.classLoader ?: classLoader,
                    ).also { created ->
                        runtime.set(created)
                        created.start()
                        recoverLaserController(shortcutClass)
                            ?.also(pendingLaserController::set)
                        pendingLaserController.get()?.let(created::attachLaserController)
                        recoverTouchFilmManager(touchFilmClass)
                            ?.let(created::attachTouchFilmManager)
                        recoverCurrentFocus(touchFilmClass)?.let(created::updateForeground)
                        XposedBridge.log(
                            "FocusPenPro: runtime initialized from ${source.javaClass.name}",
                        )
                    }
                }.getOrElse {
                    runtime.set(null)
                    XposedBridge.log(
                        "FocusPenPro: runtime init failed; fail-open\n${it.stackTraceToString()}",
                    )
                    null
                }
            }
        }

        XposedBridge.hookAllConstructors(
            touchFilmClass,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching {
                        ensureRuntime(param.thisObject)
                            ?.attachTouchFilmManager(param.thisObject)
                    }
                }
            },
        )

        XposedBridge.hookAllConstructors(
            laserClass,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    pendingLaserController.set(param.thisObject)
                    runCatching {
                        ensureRuntime(param.thisObject)?.attachLaserController(param.thisObject)
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            laserClass,
            "turnoffvirtuallasershow",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // This private vendor method is also reached while Xiaomi is
                    // managing its original drawing presentation.  Capture whether
                    // the view owned by this exact controller is currently carrying
                    // our mouse overlay; a cursor left on another/stale LaserView must
                    // never turn an original brush clear into a complete laser exit.
                    val wasMousePresentation = runCatching {
                        val laserView = XposedHelpers.getObjectField(
                            param.thisObject,
                            "mLaserView",
                        ) ?: return@runCatching false
                        val laserState = XposedHelpers.getObjectField(
                            param.thisObject,
                            "mLaserState",
                        ) ?: return@runCatching false
                        val controllerMode = XposedHelpers.getIntField(
                            laserState,
                            "mCurrentMode",
                        )
                        // Verified ROM contract: 0 = pointer, 1 = original brush.
                        // The brush cleanup path also reaches this private method.
                        if (controllerMode != 0) return@runCatching false
                        LaserCursorRenderer.isActive(laserView) &&
                            LaserCursorRenderer.isLaserMode(laserView)
                    }.getOrDefault(false)
                    param.setObjectExtra(
                        PRESENTATION_WAS_MOUSE_EXTRA,
                        wasMousePresentation,
                    )
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable != null) return
                    val wasMousePresentation =
                        param.getObjectExtra(PRESENTATION_WAS_MOUSE_EXTRA) as? Boolean
                            ?: false
                    runCatching {
                        runtime.get()?.onLaserPresentationHidden(
                            "turnoffvirtuallasershow",
                            wasMousePresentation,
                        )
                    }.onFailure {
                        XposedBridge.log(
                            "FocusPenPro: presentation-exit callback failed; fail-open\n" +
                                it.stackTraceToString(),
                        )
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            laserViewClass,
            "setPosition",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val currentRuntime = runtime.get() ?: return
                    val x = param.args.getOrNull(0) as? Float ?: return
                    val y = param.args.getOrNull(1) as? Float ?: return
                    if (currentRuntime.shouldReplaceLaserPresentation() &&
                        LaserCursorRenderer.isLaserMode(param.thisObject)
                    ) {
                        val installed = runCatching {
                            LaserCursorRenderer.install(param.thisObject, x, y)
                        }.getOrElse {
                            XposedBridge.log(
                                "FocusPenPro: laser drawable install failed; original retained\n" +
                                    it.stackTraceToString(),
                            )
                            false
                        }
                        if (installed) {
                            currentRuntime.updateLaser(true, "LaserView.setPosition")
                            currentRuntime.onLaserPositionChanged(x, y)
                        }
                    } else {
                        LaserCursorRenderer.restore(param.thisObject)
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            laserViewClass,
            "setKeepPath",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val keepPath = param.args.firstOrNull() as? Boolean ?: return
                    if (keepPath &&
                        runtime.get()?.shouldSuppressVendorDrawing() == true &&
                        LaserCursorRenderer.isActive(param.thisObject)
                    ) {
                        param.result = null
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            laserViewClass,
            "setMode",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val mode = param.args.firstOrNull() as? Int ?: return
                    if (mode == 1 &&
                        runtime.get()?.shouldSuppressVendorDrawing() == true &&
                        LaserCursorRenderer.isActive(param.thisObject)
                    ) {
                        param.result = null
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            policyClass,
            "interceptKeyBeforeQueueing",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args.firstOrNull() as? KeyEvent ?: return
                    if (!isFocusPenEvent(event)) return
                    if (ensureRuntime(param.thisObject)?.intercept(event) == true) {
                        param.result = 0
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            touchFilmClass,
            "onDefaultDisplayFocusChangedLw",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    runCatching {
                        ensureRuntime(param.thisObject)
                            ?.updateForeground(param.args.firstOrNull())
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            touchFilmClass,
            "interceptKeyBeforeQueueing",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args.firstOrNull() as? KeyEvent ?: return
                    if (!isFocusPenEvent(event)) return
                    runCatching {
                        if (ensureRuntime(param.thisObject)
                                ?.shouldConsumeVendorOffLaserPinch(
                                    event,
                                    param.args.getOrNull(1) as? Boolean ?: false,
                                ) == true
                        ) {
                            // This ROM's boolean contract is "handled/consumed".
                            param.result = true
                        }
                    }.onFailure {
                        XposedBridge.log(
                            "FocusPenPro: off-laser vendor suppression failed; fail-open\n" +
                                it.stackTraceToString(),
                        )
                    }
                }
            },
        )

        XposedBridge.hookAllMethods(
            touchFilmClass,
            "onPointerEvent",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args.firstOrNull() as? MotionEvent ?: return
                    runCatching {
                        ensureRuntime(param.thisObject)?.onPointerEvent(event)
                    }.onFailure {
                        XposedBridge.log(
                            "FocusPenPro: finger-touch exit callback failed; original retained\n" +
                                it.stackTraceToString(),
                        )
                    }
                }
            },
        )

        hookLaserState(touchFilmClass, ensureRuntime, pendingLaserController, false)
        hookLaserState(laserClass, ensureRuntime, pendingLaserController, true)

        runCatching {
            XposedHelpers.getStaticObjectField(touchFilmClass, "sInstance")
        }.getOrNull()?.let { manager ->
            ensureRuntime(manager)?.attachTouchFilmManager(manager)
        }

        XposedBridge.log("FocusPenPro: verified hooks installed for ${CompatProfile.description()}")
    }

    private fun hookLaserState(
        clazz: Class<*>,
        ensureRuntime: (Any) -> FocusPenRuntime?,
        pendingLaserController: AtomicReference<Any?>,
        sourceIsController: Boolean,
    ) {
        listOf("enableVirtualLaser", "turnonvirtuallaser").forEach { method ->
            XposedBridge.hookAllMethods(
                clazz,
                method,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.throwable == null) {
                            runCatching {
                                if (sourceIsController) {
                                    pendingLaserController.set(param.thisObject)
                                }
                                ensureRuntime(param.thisObject)?.apply {
                                    if (sourceIsController) {
                                        attachLaserController(param.thisObject)
                                    }
                                    updateLaser(true, method)
                                }
                            }
                        }
                    }
                },
            )
        }
        XposedBridge.hookAllMethods(
            clazz,
            "turnoffvirtuallaser",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.throwable == null) {
                        runCatching {
                            if (sourceIsController) {
                                pendingLaserController.set(param.thisObject)
                            }
                            ensureRuntime(param.thisObject)?.apply {
                                if (sourceIsController) {
                                    attachLaserController(param.thisObject)
                                }
                                updateLaser(false, "turnoffvirtuallaser")
                            }
                        }
                    }
                }
            },
        )
    }

    private fun verifyCapabilities(
        touchFilmClass: Class<*>,
        shortcutClass: Class<*>,
        laserClass: Class<*>,
        laserViewClass: Class<*>,
        policyClass: Class<*>,
    ) {
        check(
            policyClass.declaredMethods.any {
                it.name == "interceptKeyBeforeQueueing" &&
                    it.returnType == Int::class.javaPrimitiveType &&
                    it.parameterTypes.contentEquals(
                        arrayOf(KeyEvent::class.java, Int::class.javaPrimitiveType),
                    )
            },
        ) { "Verified MiuiPhoneWindowManager input signature is absent" }
        check(
            touchFilmClass.declaredMethods.any {
                it.name == "enableVirtualLaser" &&
                    it.returnType == Void.TYPE &&
                    it.parameterCount == 0
            },
        ) { "Verified MiuiStylusTouchFilmManager.enableVirtualLaser() is absent" }
        check(
            touchFilmClass.declaredMethods.any {
                it.name == "onPointerEvent" &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(arrayOf(MotionEvent::class.java))
            },
        ) { "Verified MiuiStylusTouchFilmManager.onPointerEvent(MotionEvent) is absent" }
        check(
            touchFilmClass.declaredMethods.any {
                it.name == "interceptKeyBeforeQueueing" &&
                    it.returnType == Boolean::class.javaPrimitiveType &&
                    it.parameterTypes.contentEquals(
                        arrayOf(KeyEvent::class.java, Boolean::class.javaPrimitiveType),
                    )
            },
        ) { "Verified touch-film key interception signature is absent" }
        check(
            laserClass.declaredMethods.any {
                it.name == "getPosition" &&
                    it.parameterTypes.contentEquals(arrayOf(FloatArray::class.java))
            },
        ) { "Verified LaserPointerController.getPosition(float[]) is absent" }
        check(
            laserClass.declaredMethods.any {
                it.name == "turnonvirtuallaser" &&
                    it.returnType == Void.TYPE &&
                    it.parameterCount == 0
            },
        ) { "Verified LaserPointerController.turnonvirtuallaser() is absent" }
        check(
            laserClass.declaredMethods.any {
                it.name == "turnoffvirtuallaser" &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            },
        ) { "Verified LaserPointerController.turnoffvirtuallaser(int) is absent" }
        check(
            laserClass.declaredMethods.any {
                it.name == "turnoffvirtuallasershow" &&
                    it.returnType == Void.TYPE &&
                    it.parameterCount == 0
            },
        ) { "Verified laser presentation shutdown method is absent" }
        check(
            laserViewClass.declaredMethods.any {
                it.name == "setPosition" &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.contentEquals(
                        arrayOf(
                            Float::class.javaPrimitiveType,
                            Float::class.javaPrimitiveType,
                        ),
                    )
            },
        ) { "Verified LaserView.setPosition(float,float) signature is absent" }
        check(
            touchFilmClass.declaredFields.any {
                it.name == "sInstance" && it.type == touchFilmClass
            },
        ) { "Verified MiuiStylusTouchFilmManager.sInstance is absent" }
        check(
            shortcutClass.declaredFields.any {
                it.name == "sInstance" && it.type == shortcutClass
            } && shortcutClass.declaredFields.any {
                it.name == "mLaserPointerController" && it.type == laserClass
            },
        ) { "Verified shortcut manager laser fields are absent" }
    }

    private fun recoverLaserController(shortcutClass: Class<*>): Any? =
        runCatching {
            val shortcut = XposedHelpers.getStaticObjectField(shortcutClass, "sInstance")
                ?: return null
            XposedHelpers.getObjectField(shortcut, "mLaserPointerController")
        }.getOrNull()

    private fun recoverCurrentFocus(touchFilmClass: Class<*>): Any? =
        runCatching {
            val manager = XposedHelpers.getStaticObjectField(touchFilmClass, "sInstance")
                ?: return null
            XposedHelpers.getObjectField(manager, "mCurrentFocusWindow")
        }.getOrNull()

    private fun recoverTouchFilmManager(touchFilmClass: Class<*>): Any? =
        runCatching {
            XposedHelpers.getStaticObjectField(touchFilmClass, "sInstance")
        }.getOrNull()

    private fun findContext(instance: Any): Context? {
        var clazz: Class<*>? = instance.javaClass
        while (clazz != null) {
            clazz.declaredFields.forEach { field ->
                if (!Modifier.isStatic(field.modifiers) &&
                    Context::class.java.isAssignableFrom(field.type)
                ) {
                    val value = runCatching {
                        field.isAccessible = true
                        field.get(instance) as? Context
                    }.getOrNull()
                    if (value != null) return value
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun isFocusPenEvent(event: KeyEvent): Boolean {
        val supportedGesture =
            event.scanCode == CompatProfile.SCAN_PINCH_F19 ||
                event.keyCode == CompatProfile.KEY_SLIDE_UP ||
                event.keyCode == CompatProfile.KEY_SLIDE_DOWN
        if (!supportedGesture) return false
        val device = event.device ?: return false
        return device.vendorId == 0x0022 &&
            device.productId == 0x5081 &&
            device.name.startsWith("Xiaomi Focus Pen Pro")
    }

    private companion object {
        const val PRESENTATION_WAS_MOUSE_EXTRA =
            "focus_pen_presentation_was_mouse"
    }
}
