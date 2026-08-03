package io.github.hmqyhm.focuspenpro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hmqyhm.focuspenpro.BuildConfig
import io.github.hmqyhm.focuspenpro.config.ConfigContract
import io.github.hmqyhm.focuspenpro.config.ConfigStore
import io.github.hmqyhm.focuspenpro.config.ModuleConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

private const val UI_PREFS = "focus_pen_ui"
private const val CURRENT_PROJECT_URL = "https://github.com/HMQYHM/FocusPenProX"
private const val KEY_LANGUAGE = "language"
private const val KEY_LAUNCH_COUNT = "launch_count"
private const val KEY_STAR_COMPLETED = "star_completed"
private const val TIP_COUNT = 16

private enum class UiLanguage(val code: String) {
    SIMPLIFIED("zh-CN"), TRADITIONAL("zh-TW"), ENGLISH("en");

    companion object {
        fun from(code: String?): UiLanguage = entries.firstOrNull { it.code == code } ?: SIMPLIFIED
    }
}

private enum class XPage { HOME, SCOPE, GESTURES, OTHER, WHITELIST, BLACKLIST, APP_PICKER }

private enum class AppPickerTarget { FOUR_TAP, FOUR_HOLD }

private data class XAppEntry(val label: String, val packageName: String)

private data class XRuntime(
    val active: Boolean = false,
    val compatible: Boolean = false,
    val message: String = "",
    val laser: Boolean = false,
    val foreground: String = "",
    val events: List<String> = emptyList(),
)

private fun l(language: UiLanguage, simplified: String, traditional: String, english: String): String =
    when (language) {
        UiLanguage.SIMPLIFIED -> simplified
        UiLanguage.TRADITIONAL -> traditional
        UiLanguage.ENGLISH -> english
    }

@Composable
internal fun FocusPenProXApp(activity: MainActivity) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors) {
        FocusPenProXContent(activity)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FocusPenProXContent(activity: MainActivity) {
    val store = remember { ConfigStore(activity) }
    val preferences = remember {
        activity.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
    }
    var language by remember {
        mutableStateOf(UiLanguage.from(preferences.getString(KEY_LANGUAGE, null)))
    }
    var config by remember { mutableStateOf(store.read()) }
    var runtime by remember { mutableStateOf(XRuntime()) }
    var apps by remember { mutableStateOf(emptyList<XAppEntry>()) }
    var currentPage by rememberSaveable { mutableStateOf(XPage.HOME) }
    var backStack by rememberSaveable { mutableStateOf(listOf<XPage>()) }
    var navigatingForward by remember { mutableStateOf(true) }
    val predictiveProgress = remember { Animatable(0f) }
    var predictiveCommit by remember { mutableStateOf(false) }
    var pickerTarget by rememberSaveable { mutableStateOf(AppPickerTarget.FOUR_TAP) }
    var showStarPrompt by rememberSaveable { mutableStateOf(false) }
    var starSeconds by remember { mutableIntStateOf(6) }
    var railExpanded by rememberSaveable { mutableStateOf(true) }
    var tipOrder by rememberSaveable { mutableStateOf((0 until TIP_COUNT).shuffled()) }
    var tipPosition by rememberSaveable { mutableIntStateOf(0) }
    val tipIndex = tipOrder.getOrElse(tipPosition) { 0 }

    fun updateConfig(transform: (ModuleConfig) -> ModuleConfig) {
        config = store.update(transform)
    }

    fun navigate(page: XPage) {
        if (page == currentPage) return
        navigatingForward = true
        backStack = backStack + currentPage
        currentPage = page
    }

    fun navigateTopLevel(page: XPage) {
        if (page == currentPage) return
        navigatingForward = page.ordinal >= currentPage.ordinal
        backStack = if (currentPage == XPage.HOME) emptyList() else backStack
        currentPage = page
    }

    fun popPage() {
        val target = backStack.lastOrNull() ?: XPage.HOME
        navigatingForward = false
        backStack = if (backStack.isEmpty()) emptyList() else backStack.dropLast(1)
        currentPage = target
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadXApps(activity) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            runtime = readXRuntime(activity)
            delay(1_000L)
        }
    }
    LaunchedEffect(Unit) {
        val launches = preferences.getInt(KEY_LAUNCH_COUNT, 0) + 1
        preferences.edit().putInt(KEY_LAUNCH_COUNT, launches).apply()
        if (launches > 3 && !preferences.getBoolean(KEY_STAR_COMPLETED, false)) {
            delay(650L)
            showStarPrompt = true
        }
    }
    LaunchedEffect(showStarPrompt) {
        if (!showStarPrompt) return@LaunchedEffect
        for (remaining in 6 downTo 1) {
            starSeconds = remaining
            delay(1_000L)
        }
        showStarPrompt = false
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(9_000L)
            if (tipPosition < tipOrder.lastIndex) {
                tipPosition += 1
            } else {
                val previous = tipOrder.lastOrNull()
                var nextOrder = (0 until TIP_COUNT).shuffled()
                if (nextOrder.firstOrNull() == previous && nextOrder.size > 1) {
                    nextOrder = nextOrder.drop(1) + nextOrder.first()
                }
                tipOrder = nextOrder
                tipPosition = 0
            }
        }
    }

    PredictiveBackHandler(enabled = backStack.isNotEmpty() || currentPage != XPage.HOME) { events ->
        try {
            events.collect { event -> predictiveProgress.snapTo(event.progress) }
            predictiveCommit = true
            popPage()
            predictiveProgress.animateTo(
                0f,
                tween(durationMillis = 150),
            )
            predictiveCommit = false
        } catch (_: CancellationException) {
            predictiveProgress.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f),
            )
        } finally {
            predictiveCommit = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        val backTranslation = with(LocalDensity.current) {
            (if (wide) 72.dp else 54.dp).toPx()
        } * predictiveProgress.value
        Scaffold(
            bottomBar = {
                if (!wide) {
                    CompactNavigation(currentPage, language, ::navigateTopLevel)
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) {
                    SideNavigation(
                        page = currentPage,
                        language = language,
                        expanded = railExpanded,
                        onExpandedChange = { railExpanded = it },
                        navigate = ::navigateTopLevel,
                    )
                }
                    AnimatedContent(
                        targetState = currentPage,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                translationX = if (wide) backTranslation else 0f
                                translationY = if (wide) 0f else backTranslation
                                val backScale = if (wide) 0.018f else 0.028f
                                scaleX = 1f - predictiveProgress.value * backScale
                                scaleY = 1f - predictiveProgress.value * backScale
                                alpha = 1f - predictiveProgress.value * 0.14f
                            },
                        transitionSpec = {
                            val direction = if (navigatingForward) 1 else -1
                            if (predictiveCommit) {
                                (fadeIn(tween(130)) + scaleIn(initialScale = 0.99f)) togetherWith
                                    fadeOut(tween(100)) using SizeTransform(clip = false)
                            } else (
                                slideInHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = 0.84f,
                                        stiffness = 420f,
                                    ),
                                ) { direction * it / 10 } +
                                    fadeIn(tween(170)) +
                                    scaleIn(
                                        initialScale = 0.985f,
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = 430f,
                                        ),
                                    )
                                ) togetherWith (
                                slideOutHorizontally(tween(180)) { -direction * it / 14 } +
                                    fadeOut(tween(150)) + scaleOut(targetScale = 0.992f)
                                ) using SizeTransform(clip = false)
                        },
                        label = "FocusPenPage",
                    ) { page ->
                        when (page) {
                            XPage.HOME -> PageWithTips(language, tipIndex) {
                                HomePage(
                                    language, config, runtime, updateConfig = ::updateConfig,
                                    onScope = { navigate(XPage.SCOPE) },
                                    onGestures = { navigate(XPage.GESTURES) },
                                    onOther = { navigate(XPage.OTHER) },
                                )
                            }
                            XPage.SCOPE -> PageWithTips(language, tipIndex) {
                                ScopePage(
                                    language, config,
                                    onWhitelist = { navigate(XPage.WHITELIST) },
                                    onBlacklist = { navigate(XPage.BLACKLIST) },
                                )
                            }
                            XPage.GESTURES -> PageWithTips(language, tipIndex) {
                                GesturesPage(language, config, apps, ::updateConfig) { target ->
                                    pickerTarget = target
                                    navigate(XPage.APP_PICKER)
                                }
                            }
                            XPage.OTHER -> PageWithTips(language, tipIndex) {
                                OtherPage(
                                    language = language,
                                    onLanguage = { selected ->
                                        language = selected
                                        preferences.edit().putString(KEY_LANGUAGE, selected.code).apply()
                                    },
                                    onCurrentProject = { openUrl(activity, CURRENT_PROJECT_URL) },
                                    onKeyboardProject = {
                                        openUrl(activity, "https://github.com/HMQYHM/HyperOSKeyboardFix")
                                    },
                                    onResetGestures = {
                                        updateConfig { current ->
                                            current.copy(
                                                globalActionsEnabled = false,
                                                tripleAction = ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
                                                triplePackage = "",
                                                fourHoldAction = ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
                                                fourHoldPackage = "",
                                                gestureActions = ConfigContract.DEFAULT_GESTURE_ACTIONS,
                                                scrollAmount = ConfigContract.SCROLL_MEDIUM,
                                            )
                                        }
                                    },
                                )
                            }
                            XPage.WHITELIST -> AppSelectionPage(
                                language, true, apps, config, ::updateConfig, ::popPage,
                            )
                            XPage.BLACKLIST -> AppSelectionPage(
                                language, false, apps, config, ::updateConfig, ::popPage,
                            )
                            XPage.APP_PICKER -> AppPickerPage(
                                language = language,
                                apps = apps,
                                selectedPackage = if (pickerTarget == AppPickerTarget.FOUR_TAP) {
                                    config.triplePackage
                                } else {
                                    config.fourHoldPackage
                                },
                                onSave = { packageName ->
                                    updateConfig { current ->
                                        if (pickerTarget == AppPickerTarget.FOUR_TAP) {
                                            current.copy(triplePackage = packageName)
                                        } else {
                                            current.copy(fourHoldPackage = packageName)
                                        }
                                    }
                                    popPage()
                                },
                            )
                        }
                    }
            }
        }
    }

    if (showStarPrompt) {
        AlertDialog(
            onDismissRequest = { showStarPrompt = false },
            icon = { Text("🥺✨", style = MaterialTheme.typography.headlineMedium) },
            title = {
                Text(l(language, "喜欢这个项目吗？", "喜歡這個專案嗎？", "Enjoying this project?"))
            },
            text = {
                Text(
                    l(
                        language,
                        "喜欢的话，麻烦去 GitHub 项目地址点个 Star，感谢支持！",
                        "喜歡的話，麻煩到 GitHub 專案頁點個 Star，感謝支持！",
                        "If you like it, please give the project a Star on GitHub. Thank you!",
                    ),
                )
            },
            dismissButton = {
                TextButton(onClick = { showStarPrompt = false }) {
                    Text(
                        l(
                            language,
                            "下次一定 · ${starSeconds}s",
                            "下次一定 · ${starSeconds}s",
                            "Maybe next time · ${starSeconds}s",
                        ),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    preferences.edit().putBoolean(KEY_STAR_COMPLETED, true).apply()
                    showStarPrompt = false
                    openUrl(activity, CURRENT_PROJECT_URL)
                }) {
                    Text(l(language, "现在就去", "現在就去", "Go now"))
                }
            },
        )
    }
}

@Composable
private fun SideNavigation(
    page: XPage,
    language: UiLanguage,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    navigate: (XPage) -> Unit,
) {
    val railWidth by animateDpAsState(
        targetValue = if (expanded) 180.dp else 76.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 430f),
        label = "NavigationRailWidth",
    )
    val itemSpacing by animateDpAsState(
        targetValue = if (expanded) 5.dp else 11.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 360f),
        label = "NavigationItemSpacing",
    )
    Surface(modifier = Modifier.width(railWidth).fillMaxHeight(), tonalElevation = 1.dp) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.padding(vertical = 12.dp).size(42.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ChevronGlyph(pointsLeft = expanded)
            }
        }
        topPages(language).forEach { (target, symbol, title) ->
            val selected = page == target ||
                (target == XPage.SCOPE && page in listOf(XPage.WHITELIST, XPage.BLACKLIST)) ||
                (target == XPage.GESTURES && page == XPage.APP_PICKER)
            SideNavigationItem(
                symbol = symbol,
                title = title,
                selected = selected,
                expanded = expanded,
                onClick = { navigate(target) },
            )
        }
        }
    }
}

@Composable
private fun ChevronGlyph(pointsLeft: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(22.dp)) {
        val pointX = if (pointsLeft) size.width * 0.34f else size.width * 0.66f
        val tailX = if (pointsLeft) size.width * 0.66f else size.width * 0.34f
        val centerY = size.height * 0.5f
        val arm = size.height * 0.27f
        val stroke = 2.2.dp.toPx()
        drawLine(color, Offset(tailX, centerY - arm), Offset(pointX, centerY), stroke, StrokeCap.Round)
        drawLine(color, Offset(pointX, centerY), Offset(tailX, centerY + arm), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SideNavigationItem(
    symbol: String,
    title: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val iconSize by animateDpAsState(
        targetValue = if (expanded) 40.dp else 42.dp,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 390f),
        label = "SideIconSize",
    )
    val iconStart by animateDpAsState(
        targetValue = if (expanded) 8.dp else 10.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 390f),
        label = "SideIconPosition",
    )
    val itemCorner by animateDpAsState(
        targetValue = if (expanded) 18.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 390f),
        label = "SideItemCorner",
    )
    val itemColor by animateColorAsState(
        targetValue = if (selected && expanded) MaterialTheme.colorScheme.primaryContainer
        else Color.Transparent,
        animationSpec = tween(180),
        label = "SideItemColor",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected && !expanded) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "SideIconColor",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(itemCorner),
        color = itemColor,
    ) {
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = iconStart).size(iconSize),
                shape = CircleShape,
                color = iconColor,
            ) {
                Box(
                    modifier = if (symbol == "⌁") {
                        Modifier.graphicsLayer { translationY = -(28 * 0.05f).dp.toPx() }
                    } else Modifier,
                    contentAlignment = Alignment.Center,
                ) { NavSymbol(symbol, 28) }
            }
            AnimatedVisibility(
                visible = expanded,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 62.dp, end = 8.dp),
                enter = fadeIn(tween(190, delayMillis = 70)) + slideInVertically(tween(230)) { it / 2 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(170)) { it },
            ) {
                Text(
                    title,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CompactNavigation(page: XPage, language: UiLanguage, navigate: (XPage) -> Unit) {
    NavigationBar(tonalElevation = 3.dp) {
        topPages(language).forEach { (target, symbol, title) ->
            NavigationBarItem(
                selected = page == target ||
                    (target == XPage.SCOPE && page in listOf(XPage.WHITELIST, XPage.BLACKLIST)) ||
                    (target == XPage.GESTURES && page == XPage.APP_PICKER),
                onClick = { navigate(target) },
                icon = { NavSymbol(symbol, 27) },
                label = { Text(title, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun NavSymbol(symbol: String, size: Int) {
    Box(Modifier.size((size + 8).dp), contentAlignment = Alignment.Center) {
        if (symbol == "⋯") {
            val color = LocalContentColor.current
            Canvas(Modifier.size(size.dp)) {
                val radius = this.size.minDimension * 0.085f
                val centerY = this.size.height * 0.5f
                listOf(0.27f, 0.5f, 0.73f).forEach { fraction ->
                    drawCircle(color, radius, Offset(this.size.width * fraction, centerY))
                }
            }
        } else {
            Text(
                symbol,
                modifier = if (symbol == "⌂") {
                    Modifier.graphicsLayer { translationY = -(size * 0.05f).dp.toPx() }
                } else Modifier,
                fontSize = size.sp,
                lineHeight = size.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PageWithTips(language: UiLanguage, tipIndex: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        TipsTicker(language, tipIndex)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TipsTicker(language: UiLanguage, tipIndex: Int) {
    val localizedTips = remember(language) { tips(language) }
    val message = localizedTips[tipIndex % localizedTips.size]
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(
            targetState = message,
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 3 }) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 3 })
            },
            label = "TipsRotation",
        ) { currentTip ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "${l(language, "小提示", "小提示", "TIP")}  ·  $currentTip",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            repeatDelayMillis = 2_400,
                            initialDelayMillis = 1_600,
                            velocity = 18.dp,
                        ),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

private fun tips(language: UiLanguage): List<String> = listOf(
    l(language, "真实音量键动作也能支持多数小说应用的音量键翻页。", "真實音量鍵動作也能支援多數小說應用程式的音量鍵翻頁。", "Real volume-key actions also support page turning in most reading apps."),
    l(language, "鼠标滚轮动作可用于支持滚轮翻页的 PPT／演示文稿应用。", "滑鼠滾輪動作可用於支援滾輪翻頁的 PowerPoint／簡報應用程式。", "Mouse-wheel actions work with PowerPoint and presentation apps that support wheel navigation."),
    l(language, "鼠标滚轮动作也能用来上下浏览抖音等支持滚轮操作的短视频应用。", "滑鼠滾輪動作也能用來上下瀏覽 TikTok 等支援滾輪操作的短影音應用程式。", "Mouse-wheel actions can also scroll through TikTok and other short-video apps that support wheel input."),
    l(language, "开启全局动作后，激光画笔清屏结束时，可直接触发设为“开启虚拟激光或手写笔鼠标”的手势，再次进入激光画笔。", "啟用全域動作後，雷射畫筆清除畫面結束時，可直接觸發設為「開啟虛擬雷射或手寫筆滑鼠」的手勢，再次進入雷射畫筆。", "With global actions enabled, after clearing the laser canvas, trigger a gesture assigned to “Enable virtual laser or stylus mouse” to enter the laser pen again."),
    l(language, "轻触两次触控笔的识别区域，可快速关闭当前的虚拟激光或手写笔鼠标。", "輕觸兩次觸控筆的感應區域，可快速關閉目前的虛擬雷射或手寫筆滑鼠。", "Double-tap the pen’s sensing area to quickly close the active virtual laser or stylus mouse."),
    l(language, "触摸屏幕即可直接关闭当前的虚拟激光或手写笔鼠标。", "輕觸螢幕即可直接關閉目前的虛擬雷射或手寫筆滑鼠。", "Touch the screen to immediately close the active virtual laser or stylus mouse."),
    l(language, "建议将笔记、绘画和游戏应用加入黑名单，避免增强手势影响原版书写、绘画或游戏操作哦 😁", "建議將筆記、繪圖與遊戲應用程式加入黑名單，避免增強手勢影響原版書寫、繪圖或遊戲操作喔 😁", "Add note-taking, drawing, and game apps to the blacklist so enhanced gestures don’t interfere with their original controls. 😁"),
    l(language, "发现 Bug 或有功能建议？欢迎前往项目网页提交反馈哦。", "發現 Bug 或有功能建議？歡迎前往專案網頁提交意見喔。", "Found a bug or have a suggestion? You’re welcome to submit it on the project page."),
    l(language, "黑名单拥有最高优先级：名单内完全不 Hook，也不会触发任何增强操作。", "黑名單擁有最高優先級：名單內完全不 Hook，也不會觸發任何增強操作。", "The blacklist has the highest priority: blacklisted apps are never hooked and trigger no enhanced actions."),
    l(language, "FocusPen Pro X 不联网，也不会上传你的任何数据。", "FocusPen Pro X 不連網，也不會上傳你的任何資料。", "FocusPen Pro X never connects to the internet or uploads any of your data."),
    l(language, "作者的碎碎念：明明触控笔已经注册成蓝牙鼠标，为什么不开放使用呢？", "作者的碎碎念：明明觸控筆已經註冊成藍牙滑鼠，為什麼不開放使用呢？", "A note from the author: the pen already registers as a Bluetooth mouse—why not let us use it?"),
    l(language, "偷偷问一句：可以帮这个项目点个 Star 吗？🥺", "偷偷問一句：可以幫這個專案點個 Star 嗎？🥺", "A tiny request: could you give this project a Star? 🥺"),
    l(language, "修改白名单或黑名单后，记得点击右下角绿色对勾保存。", "修改白名單或黑名單後，記得點擊右下角綠色勾號儲存。", "After editing either app list, tap the green check button to save."),
    l(language, "全局四次轻捏不受白名单限制，但始终不会影响黑名单应用。", "全域四次輕捏不受白名單限制，但始終不會影響黑名單應用程式。", "Global four-pinches work beyond the whitelist but never affect blacklisted apps."),
    l(language, "关闭总开关、取消 LSPosed 作用域或卸载模块后，系统会恢复原始触控笔行为。", "關閉總開關、取消 LSPosed 作用域或解除安裝模組後，系統會恢復原始觸控筆行為。", "Turning off the master switch, removing the LSPosed scope, or uninstalling restores original pen behavior."),
    l(language, "若出现按键未释放或操作异常，请先关闭总开关，模块会执行安全释放。", "若出現按鍵未釋放或操作異常，請先關閉總開關，模組會執行安全釋放。", "If an input appears stuck, turn off the master switch first so the module can release it safely."),
)

private fun topPages(language: UiLanguage) = listOf(
    Triple(XPage.HOME, "⌂", l(language, "首页", "首頁", "Home")),
    Triple(XPage.SCOPE, "▦", l(language, "应用范围", "應用範圍", "Apps")),
    Triple(XPage.GESTURES, "⌁", l(language, "手势设置", "手勢設定", "Gestures")),
    Triple(XPage.OTHER, "⋯", l(language, "其他", "其他", "Other")),
)

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomePage(
    language: UiLanguage,
    config: ModuleConfig,
    runtime: XRuntime,
    updateConfig: ((ModuleConfig) -> ModuleConfig) -> Unit,
    onScope: () -> Unit,
    onGestures: () -> Unit,
    onOther: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            PageTitle(
                l(language, "FocusPen Pro X", "FocusPen Pro X", "FocusPen Pro X"),
                l(
                    language,
                    "小米焦点触控笔 Pro 手势增强模块",
                    "小米焦點觸控筆 Pro 手勢增強模組",
                    "Gesture enhancement module for Xiaomi Focus Pen Pro",
                ),
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            l(language, "增强功能", "增強功能", "Enhancement"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            l(
                                language,
                                "关闭后立即恢复小米原始触控笔行为",
                                "關閉後立即恢復小米原始觸控筆行為",
                                "Turn off to immediately restore Xiaomi's original behavior",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Switch(checked = config.enabled, onCheckedChange = { enabled ->
                        updateConfig { it.copy(enabled = enabled) }
                    })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard(
                    Modifier.weight(1f), "✓",
                    l(language, "应用范围", "應用範圍", "Apps"), onScope,
                )
                QuickCard(
                    Modifier.weight(1f), "⌁",
                    l(language, "手势设置", "手勢設定", "Gestures"), onGestures,
                )
                QuickCard(
                    Modifier.weight(1f), "•••",
                    l(language, "其他", "其他", "Other"), onOther,
                )
            }
        }
        item {
            StatusPanel(language, runtime)
        }
        item {
            Text(
                l(language, "最近事件", "最近事件", "Recent events"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                Text(
                    if (runtime.events.isEmpty()) {
                        l(language, "暂无事件记录", "暫無事件記錄", "No events yet")
                    } else runtime.events.takeLast(10).reversed().joinToString("\n"),
                    Modifier.fillMaxWidth().padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QuickCard(modifier: Modifier, symbol: String, title: String, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(15.dp)) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.CenterStart) {
                if (symbol == "•••") {
                    val color = MaterialTheme.colorScheme.primary
                    Canvas(Modifier.size(25.dp)) {
                        val radius = size.minDimension * 0.07f
                        val centerY = size.height * 0.5f
                        listOf(0.28f, 0.5f, 0.72f).forEach { fraction ->
                            drawCircle(color, radius, Offset(size.width * fraction, centerY))
                        }
                    }
                } else {
                    val symbolSize = when (symbol) {
                        "⌁" -> 35
                        "✓" -> 23
                        else -> 27
                    }
                    Text(
                        symbol,
                        modifier = if (symbol == "⌁") {
                            Modifier.graphicsLayer { translationY = -(symbolSize * 0.05f).dp.toPx() }
                        } else Modifier,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = symbolSize.sp,
                        lineHeight = symbolSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StatusPanel(language: UiLanguage, runtime: XRuntime) {
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            XStatusLine(
                l(language, "模块状态", "模組狀態", "Module status"),
                if (runtime.active) l(language, "已激活", "已啟用", "Active")
                else l(language, "未激活", "未啟用", "Inactive"),
            )
            XStatusLine(
                l(language, "虚拟激光", "虛擬雷射", "Virtual laser"),
                if (runtime.laser) l(language, "已开启", "已開啟", "On")
                else l(language, "未开启", "未開啟", "Off"),
            )
            XStatusLine(
                l(language, "当前应用", "目前應用程式", "Foreground app"),
                runtime.foreground.ifBlank { l(language, "未知", "未知", "Unknown") },
            )
            XStatusLine(
                l(language, "系统版本", "系統版本", "System"),
                "Android ${Build.VERSION.RELEASE} · ${Build.DISPLAY}",
            )
        }
    }
}

@Composable
private fun XStatusLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScopePage(
    language: UiLanguage,
    config: ModuleConfig,
    onWhitelist: () -> Unit,
    onBlacklist: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageTitle(
                l(language, "应用范围", "應用範圍", "App scope"),
                l(
                    language,
                    "区分增强环境和完全原版环境",
                    "區分增強環境與完全原版環境",
                    "Separate enhanced apps from completely original behavior",
                ),
            )
        }
        item {
            ScopeCard(
                symbol = "✓",
                title = l(language, "白名单", "白名單", "Whitelist"),
                detail = l(
                    language,
                    "启用普通手势与手写笔鼠标增强 · ${config.whitelist.size} 个应用",
                    "啟用普通手勢與手寫筆滑鼠增強 · ${config.whitelist.size} 個應用程式",
                    "Enable gestures and stylus mouse · ${config.whitelist.size} apps",
                ),
                onClick = onWhitelist,
            )
        }
        item {
            ScopeCard(
                symbol = "◇",
                title = l(language, "黑名单", "黑名單", "Blacklist"),
                detail = l(
                    language,
                    "完全不 Hook，保留小米原版激光画笔 · ${config.blacklist.size} 个应用",
                    "完全不 Hook，保留小米原版雷射畫筆 · ${config.blacklist.size} 個應用程式",
                    "No hooks; preserve Xiaomi laser drawing · ${config.blacklist.size} apps",
                ),
                onClick = onBlacklist,
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    l(
                        language,
                        "黑名单优先级最高。即使开启全局动作，黑名单应用的小米原版激光功能也不受影响。",
                        "黑名單優先級最高。即使開啟全域動作，黑名單應用程式的小米原版雷射功能也不受影響。",
                        "Blacklist has the highest priority. Global actions never affect blacklisted apps.",
                    ),
                    Modifier.padding(15.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ScopeCard(symbol: String, title: String, detail: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(symbol, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun AppSelectionPage(
    language: UiLanguage,
    whitelist: Boolean,
    apps: List<XAppEntry>,
    config: ModuleConfig,
    updateConfig: ((ModuleConfig) -> ModuleConfig) -> Unit,
    onSaved: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selected by remember(whitelist, config.whitelist, config.blacklist) {
        mutableStateOf(if (whitelist) config.whitelist else config.blacklist)
    }
    var opposite by remember(whitelist, config.whitelist, config.blacklist) {
        mutableStateOf(if (whitelist) config.blacklist else config.whitelist)
    }
    var pendingTransfer by remember { mutableStateOf<XAppEntry?>(null) }
    val filtered = remember(apps, search, selected, opposite) {
        val matches = if (search.isBlank()) apps else apps.filter {
            it.label.contains(search, true) || it.packageName.contains(search, true)
        }
        matches.sortedWith(
            compareBy<XAppEntry> {
                when (it.packageName) {
                    in selected -> 0
                    in opposite -> 2
                    else -> 1
                }
            }.thenBy { it.label.lowercase() },
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                PageTitle(
                    if (whitelist) l(language, "选择白名单", "選擇白名單", "Select whitelist")
                    else l(language, "选择黑名单", "選擇黑名單", "Select blacklist"),
                    if (whitelist) {
                        l(language, "已选择 ${selected.size} 个应用", "已選取 ${selected.size} 個應用程式", "${selected.size} apps selected")
                    } else {
                        l(language, "已选择 ${selected.size} 个应用使用原版逻辑", "已選取 ${selected.size} 個應用程式使用原版邏輯", "${selected.size} apps keep original behavior")
                    },
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    label = { Text(l(language, "搜索应用或包名", "搜尋應用程式或套件名稱", "Search apps or package")) },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
            }
            items(filtered, key = XAppEntry::packageName) { app ->
                val isSelected = app.packageName in selected
                val isOpposite = app.packageName in opposite
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = tween(180),
                            placementSpec = spring(dampingRatio = 0.82f, stiffness = 390f),
                            fadeOutSpec = tween(120),
                        )
                        .alpha(if (isOpposite) 0.48f else 1f)
                        .clickable {
                            when {
                                isOpposite -> pendingTransfer = app
                                isSelected -> selected = selected - app.packageName
                                else -> selected = selected + app.packageName
                            }
                        },
                    shape = RoundedCornerShape(18.dp),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        isOpposite -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    },
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                app.label.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(checked = isSelected, onCheckedChange = null, enabled = !isOpposite)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                updateConfig { current ->
                    if (whitelist) {
                        current.copy(whitelist = selected, blacklist = opposite)
                    } else {
                        current.copy(blacklist = selected, whitelist = opposite)
                    }
                }
                onSaved()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            shape = CircleShape,
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White,
        ) {
            Text("✓", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }

    pendingTransfer?.let { app ->
        AlertDialog(
            onDismissRequest = { pendingTransfer = null },
            title = {
                Text(
                    if (whitelist) l(language, "移入白名单？", "移入白名單？", "Move to whitelist?")
                    else l(language, "移入黑名单？", "移入黑名單？", "Move to blacklist?"),
                )
            },
            text = {
                Text(
                    if (whitelist) {
                        l(language, "是否将 ${app.label} 移出黑名单并加入白名单？", "是否將 ${app.label} 移出黑名單並加入白名單？", "Remove ${app.label} from the blacklist and add it to the whitelist?")
                    } else {
                        l(language, "是否将 ${app.label} 移出白名单并加入黑名单？", "是否將 ${app.label} 移出白名單並加入黑名單？", "Remove ${app.label} from the whitelist and add it to the blacklist?")
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    selected = selected + app.packageName
                    opposite = opposite - app.packageName
                    pendingTransfer = null
                }) { Text(l(language, "是", "是", "Yes")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingTransfer = null }) {
                    Text(l(language, "否", "否", "No"))
                }
            },
        )
    }
}

@Composable
private fun AppPickerPage(
    language: UiLanguage,
    apps: List<XAppEntry>,
    selectedPackage: String,
    onSave: (String) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var selected by remember(selectedPackage) { mutableStateOf(selectedPackage) }
    val filtered = remember(apps, search, selected) {
        val matches = if (search.isBlank()) apps else apps.filter {
            it.label.contains(search, true) || it.packageName.contains(search, true)
        }
        matches.sortedWith(
            compareByDescending<XAppEntry> { it.packageName == selected }
                .thenBy { it.label.lowercase() },
        )
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                PageTitle(
                    l(language, "选择启动应用", "選擇啟動應用程式", "Select app to launch"),
                    l(language, "选择一个应用，点击右下角对勾保存", "選擇一個應用程式，點擊右下角勾號儲存", "Choose one app, then tap the check button to save"),
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    label = { Text(l(language, "搜索应用或包名", "搜尋應用程式或套件名稱", "Search apps or package")) },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
            }
            items(filtered, key = XAppEntry::packageName) { app ->
                val checked = app.packageName == selected
                Surface(
                    modifier = Modifier.fillMaxWidth().animateItem().clickable {
                        selected = app.packageName
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (checked) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(
                                if (checked) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondaryContainer,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                app.label.firstOrNull()?.uppercase() ?: "?",
                                color = if (checked) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { if (selected.isNotBlank()) onSave(selected) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
                .alpha(if (selected.isBlank()) 0.45f else 1f),
            shape = CircleShape,
            containerColor = Color(0xFF2E7D32),
            contentColor = Color.White,
        ) {
            Text("✓", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GesturesPage(
    language: UiLanguage,
    config: ModuleConfig,
    apps: List<XAppEntry>,
    updateConfig: ((ModuleConfig) -> ModuleConfig) -> Unit,
    onSelectApplication: (AppPickerTarget) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageTitle(
                l(language, "手势设置", "手勢設定", "Gesture settings"),
                l(language, "全局、普通模式与手写笔鼠标", "全域、普通模式與手寫筆滑鼠", "Global, normal mode, and stylus mouse"),
            )
        }
        item {
            SettingSurface {
                SwitchRow(
                    title = l(language, "启用全局动作", "啟用全域動作", "Enable global actions"),
                    detail = l(
                        language,
                        "控制轻捏四次与轻捏四次并按住",
                        "控制輕捏四次與輕捏四次並按住",
                        "Controls four pinches and four-pinches-and-hold",
                    ),
                    checked = config.globalActionsEnabled,
                    onChecked = { enabled -> updateConfig { it.copy(globalActionsEnabled = enabled) } },
                )
                Text(
                    if (config.globalActionsEnabled) {
                        l(
                            language,
                            "已开启：会接管全局轻捏序列，只保证一次性激光画笔。黑名单内的小米原版激光功能不受影响。",
                            "已開啟：會接管全域輕捏序列，只保證一次性雷射畫筆。黑名單內的小米原版雷射功能不受影響。",
                            "Enabled: intercepts global pinch sequences and guarantees one-shot laser drawing only. Blacklisted apps are unaffected.",
                        )
                    } else {
                        l(
                            language,
                            "已关闭：停止全局四捏识别，白名单其他设置仍然有效。",
                            "已關閉：停止全域四捏辨識，白名單其他設定仍然有效。",
                            "Disabled: global four-pinch recognition stops; other whitelist settings remain active.",
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (config.globalActionsEnabled) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SettingSurface {
                Text(l(language, "鼠标滚轮幅度", "滑鼠滾輪幅度", "Mouse wheel distance"), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ConfigContract.SCROLL_SHORT to l(language, "短", "短", "Short"),
                        ConfigContract.SCROLL_MEDIUM to l(language, "中", "中", "Medium"),
                        ConfigContract.SCROLL_LONG to l(language, "长", "長", "Long"),
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = config.scrollAmount == value,
                            onClick = { updateConfig { it.copy(scrollAmount = value) } },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        item {
            AnimatedVisibility(
                visible = config.globalActionsEnabled,
                enter = expandVertically(spring(dampingRatio = 0.82f, stiffness = 420f)) + fadeIn(),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(150)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle(l(language, "全局动作", "全域動作", "Global actions"))
                    SettingSurface {
                        GlobalActionEditor(
                            language, l(language, "轻捏四次", "輕捏四次", "Four pinches"),
                            config.tripleAction,
                            apps.firstOrNull { it.packageName == config.triplePackage }?.label,
                            onAction = { action -> updateConfig { it.copy(tripleAction = action) } },
                            onChooseApplication = { onSelectApplication(AppPickerTarget.FOUR_TAP) },
                        )
                        HorizontalDivider()
                        GlobalActionEditor(
                            language, l(language, "轻捏四次并按住", "輕捏四次並按住", "Four pinches and hold"),
                            config.fourHoldAction,
                            apps.firstOrNull { it.packageName == config.fourHoldPackage }?.label,
                            onAction = { action -> updateConfig { it.copy(fourHoldAction = action) } },
                            onChooseApplication = { onSelectApplication(AppPickerTarget.FOUR_HOLD) },
                        )
                    }
                }
            }
        }
        item { SectionTitle(l(language, "白名单 · 普通模式", "白名單 · 普通模式", "Whitelist · Normal mode")) }
        item {
            GestureGroup(
                language = language,
                rows = normalGestureIds(language),
                allowedActions = normalActionIds,
                config = config,
                updateConfig = updateConfig,
            )
        }
        item { SectionTitle(l(language, "白名单 · 手写笔鼠标", "白名單 · 手寫筆滑鼠", "Whitelist · Stylus mouse")) }
        item {
            GestureGroup(
                language = language,
                rows = laserGestureIds(language),
                allowedActions = laserActionIds,
                config = config,
                updateConfig = updateConfig,
            )
        }
        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun SettingSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun GlobalActionEditor(
    language: UiLanguage,
    title: String,
    action: String,
    selectedApplication: String?,
    onAction: (String) -> Unit,
    onChooseApplication: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Medium)
        DropdownSelector(
            currentLabel = actionLabel(language, action),
            values = globalActionIds,
            label = { actionLabel(language, it) },
            onSelect = onAction,
        )
        if (action == ConfigContract.TRIPLE_ACTION_LAUNCH_APP) {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseApplication),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedApplication != null) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                selectedApplication.firstOrNull()?.uppercase() ?: "?",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(selectedApplication, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(l(language, "更改软件", "更改應用程式", "Change app"), color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            l(language, "选择应用", "選擇應用程式", "Select app"),
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureGroup(
    language: UiLanguage,
    rows: List<Pair<String, String>>,
    allowedActions: List<String>,
    config: ModuleConfig,
    updateConfig: ((ModuleConfig) -> ModuleConfig) -> Unit,
) {
    SettingSurface {
        rows.forEachIndexed { index, (gesture, title) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                DropdownSelector(
                    currentLabel = actionLabel(
                        language,
                        config.gestureActions[gesture]
                            ?: ConfigContract.DEFAULT_GESTURE_ACTIONS.getValue(gesture),
                    ),
                    values = allowedActionsForGesture(gesture, allowedActions),
                    label = { actionLabel(language, it) },
                    onSelect = { action ->
                        updateConfig { current ->
                            var actions = current.gestureActions + (gesture to action)
                            if (gesture == ConfigContract.GESTURE_PINCH) {
                                actions = when (action) {
                                    ConfigContract.ACTION_MOUSE_LEFT_CLICK -> actions +
                                        (ConfigContract.GESTURE_PINCH_HOLD to ConfigContract.ACTION_MOUSE_LEFT_HOLD)
                                    ConfigContract.ACTION_MOUSE_RIGHT_CLICK -> actions +
                                        (ConfigContract.GESTURE_PINCH_HOLD to ConfigContract.ACTION_MOUSE_RIGHT_HOLD)
                                    else -> actions
                                }
                            }
                            if (gesture == ConfigContract.GESTURE_DOUBLE_TAP) {
                                actions = when (action) {
                                    ConfigContract.ACTION_MOUSE_LEFT_CLICK -> actions +
                                        (ConfigContract.GESTURE_DOUBLE_HOLD to ConfigContract.ACTION_MOUSE_LEFT_HOLD)
                                    ConfigContract.ACTION_MOUSE_RIGHT_CLICK -> actions +
                                        (ConfigContract.GESTURE_DOUBLE_HOLD to ConfigContract.ACTION_MOUSE_RIGHT_HOLD)
                                    else -> actions
                                }
                            }
                            current.copy(gestureActions = actions)
                        }
                    },
                )
            }
            if (index != rows.lastIndex) HorizontalDivider()
        }
    }
}

private fun allowedActionsForGesture(gesture: String, normal: List<String>): List<String> =
    when (gesture) {
        ConfigContract.GESTURE_PINCH_HOLD,
        ConfigContract.GESTURE_DOUBLE_HOLD,
        -> laserHoldActionIds
        ConfigContract.GESTURE_OFF_DOUBLE_TAP,
        ConfigContract.GESTURE_OFF_DOUBLE_HOLD,
        -> (normal + ConfigContract.TRIPLE_ACTION_ENABLE_LASER).distinct()
        else -> normal
    }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun <T> DropdownSelector(
    currentLabel: String,
    values: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.clickable { expanded = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                currentLabel,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.size(28.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) { DownChevronGlyph() }
            }
        }
    }
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    currentLabel,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(values) { value ->
                        val optionLabel = label(value)
                        val checked = optionLabel == currentLabel
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                expanded = false
                                onSelect(value)
                            },
                            shape = RoundedCornerShape(17.dp),
                            color = if (checked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(optionLabel, Modifier.weight(1f), fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                                if (checked) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownChevronGlyph() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(18.dp)) {
        val stroke = 2.2.dp.toPx()
        val centerX = size.width * 0.5f
        val pointY = size.height * 0.63f
        val armY = size.height * 0.36f
        val armOffset = size.width * 0.27f
        drawLine(
            color,
            Offset(centerX - armOffset, armY),
            Offset(centerX, pointY),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(centerX, pointY),
            Offset(centerX + armOffset, armY),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun OtherPage(
    language: UiLanguage,
    onLanguage: (UiLanguage) -> Unit,
    onCurrentProject: () -> Unit,
    onKeyboardProject: () -> Unit,
    onResetGestures: () -> Unit,
) {
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            PageTitle(
                l(language, "其他", "其他", "Other"),
                l(language, "语言、项目地址和相关作品", "語言、專案地址和相關作品", "Language, projects, and related work"),
            )
        }
        item {
            SettingSurface {
                Text(l(language, "界面语言", "介面語言", "Interface language"), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UiLanguage.entries.forEach { item ->
                        FilterChip(
                            selected = language == item,
                            onClick = { onLanguage(item) },
                            label = {
                                Text(
                                    when (item) {
                                        UiLanguage.SIMPLIFIED -> "简体中文"
                                        UiLanguage.TRADITIONAL -> "繁體中文"
                                        UiLanguage.ENGLISH -> "English"
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        item { SectionTitle(l(language, "项目", "專案", "Projects")) }
        item {
            ProjectCard(
                symbol = "X",
                title = "FocusPen Pro X",
                detail = l(
                    language,
                    "小米焦点触控笔 Pro 手势增强模块",
                    "小米焦點觸控筆 Pro 手勢增強模組",
                    "Gesture enhancement module for Xiaomi Focus Pen Pro",
                ),
                action = l(language, "现在就去", "現在就去", "Go now"),
                onClick = onCurrentProject,
            )
        }
        item {
            ProjectCard(
                symbol = "⌨",
                title = "HyperOSKeyboardFix",
                detail = l(
                    language,
                    "为 HyperOS 平板外接键盘补充快捷键兼容并修复按键行为的 LSPosed 项目。",
                    "為 HyperOS 平板外接鍵盤補充快速鍵相容性並修復按鍵行為的 LSPosed 專案。",
                    "An LSPosed project that improves shortcut compatibility and key behavior for external keyboards on HyperOS tablets.",
                ),
                action = l(language, "查看项目", "查看專案", "View project"),
                onClick = onKeyboardProject,
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { showResetConfirmation = true },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    Modifier.padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "↺",
                                modifier = Modifier.graphicsLayer { translationY = -3.5.dp.toPx() },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l(language, "恢复默认手势", "恢復預設手勢", "Restore default gestures"), fontWeight = FontWeight.SemiBold)
                        Text(
                            l(language, "不会清空黑白名单；全局四次轻捏默认关闭", "不會清空黑白名單；全域四次輕捏預設關閉", "Keeps app lists; global four-pinches will be disabled"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item {
            Text(
                "FocusPen Pro X ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(l(language, "恢复默认手势？", "恢復預設手勢？", "Restore default gestures?")) },
            text = {
                Text(l(language, "所有动作映射和滚轮幅度将恢复默认，全局四次轻捏将关闭。", "所有動作映射與滾輪幅度將恢復預設，全域四次輕捏將關閉。", "All gesture mappings and scroll distance will be reset. Global four-pinches will be disabled."))
            },
            confirmButton = {
                Button(onClick = {
                    showResetConfirmation = false
                    onResetGestures()
                }) { Text(l(language, "恢复", "恢復", "Restore")) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(l(language, "取消", "取消", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    symbol: String,
    title: String,
    detail: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(symbol, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

private val normalActionIds = listOf(
    ConfigContract.ACTION_SYSTEM,
    ConfigContract.ACTION_CONSUME,
    ConfigContract.ACTION_VOLUME_UP,
    ConfigContract.ACTION_VOLUME_DOWN,
    ConfigContract.ACTION_BACK,
    ConfigContract.ACTION_HOME,
    ConfigContract.ACTION_RECENTS,
    ConfigContract.ACTION_SCROLL_UP,
    ConfigContract.ACTION_SCROLL_DOWN,
)

private val laserActionIds = listOf(
    ConfigContract.ACTION_CONSUME,
    ConfigContract.ACTION_MOUSE_LEFT_CLICK,
    ConfigContract.ACTION_MOUSE_RIGHT_CLICK,
    ConfigContract.ACTION_VOLUME_UP,
    ConfigContract.ACTION_VOLUME_DOWN,
    ConfigContract.ACTION_BACK,
    ConfigContract.ACTION_HOME,
    ConfigContract.ACTION_RECENTS,
    ConfigContract.ACTION_SCROLL_UP,
    ConfigContract.ACTION_SCROLL_DOWN,
)

private val laserHoldActionIds = listOf(
    ConfigContract.ACTION_CONSUME,
    ConfigContract.ACTION_MOUSE_LEFT_HOLD,
    ConfigContract.ACTION_MOUSE_RIGHT_HOLD,
) + laserActionIds

private val globalActionIds = listOf(
    ConfigContract.TRIPLE_ACTION_NONE,
    ConfigContract.ACTION_CONSUME,
    ConfigContract.TRIPLE_ACTION_ENABLE_LASER,
    ConfigContract.TRIPLE_ACTION_LAUNCH_APP,
    ConfigContract.ACTION_VOLUME_UP,
    ConfigContract.ACTION_VOLUME_DOWN,
    ConfigContract.ACTION_BACK,
    ConfigContract.ACTION_HOME,
    ConfigContract.ACTION_RECENTS,
)

private fun normalGestureIds(language: UiLanguage) = listOf(
    ConfigContract.GESTURE_OFF_DOUBLE_TAP to l(language, "轻捏两次", "輕捏兩次", "Two pinches"),
    ConfigContract.GESTURE_OFF_DOUBLE_HOLD to l(language, "轻捏两次并按住", "輕捏兩次並按住", "Two pinches and hold"),
    ConfigContract.GESTURE_OFF_SWIPE_UP to l(language, "从笔尖向上滑", "從筆尖向上滑", "Slide up from tip"),
    ConfigContract.GESTURE_OFF_SWIPE_DOWN to l(language, "从上向笔尖滑", "從上向筆尖滑", "Slide down toward tip"),
)

private fun laserGestureIds(language: UiLanguage) = listOf(
    ConfigContract.GESTURE_PINCH to l(language, "轻捏一次", "輕捏一次", "One pinch"),
    ConfigContract.GESTURE_PINCH_HOLD to l(language, "轻捏并按住", "輕捏並按住", "Pinch and hold"),
    ConfigContract.GESTURE_DOUBLE_TAP to l(language, "轻捏两次", "輕捏兩次", "Two pinches"),
    ConfigContract.GESTURE_DOUBLE_HOLD to l(language, "轻捏两次并按住", "輕捏兩次並按住", "Two pinches and hold"),
    ConfigContract.GESTURE_SWIPE_UP to l(language, "从笔尖向上滑", "從筆尖向上滑", "Slide up from tip"),
    ConfigContract.GESTURE_SWIPE_DOWN to l(language, "从上向笔尖滑", "從上向筆尖滑", "Slide down toward tip"),
)

private fun actionLabel(language: UiLanguage, action: String): String = when (action) {
    ConfigContract.ACTION_SYSTEM -> l(language, "不处理，交给系统", "不處理，交給系統", "Do not handle")
    ConfigContract.ACTION_CONSUME -> l(language, "仅消费", "僅攔截", "Consume only")
    ConfigContract.ACTION_MOUSE_LEFT_CLICK -> l(language, "鼠标左键", "滑鼠左鍵", "Left click")
    ConfigContract.ACTION_MOUSE_LEFT_HOLD -> l(language, "左键长按", "左鍵長按", "Left hold")
    ConfigContract.ACTION_MOUSE_RIGHT_CLICK -> l(language, "鼠标右键", "滑鼠右鍵", "Right click")
    ConfigContract.ACTION_MOUSE_RIGHT_HOLD -> l(language, "右键长按", "右鍵長按", "Right hold")
    ConfigContract.ACTION_VOLUME_UP -> l(language, "音量增加", "提高音量", "Volume up")
    ConfigContract.ACTION_VOLUME_DOWN -> l(language, "音量降低", "降低音量", "Volume down")
    ConfigContract.ACTION_BACK -> l(language, "返回", "返回", "Back")
    ConfigContract.ACTION_HOME -> l(language, "回到桌面", "回到桌面", "Home")
    ConfigContract.ACTION_RECENTS -> l(language, "最近任务", "最近任務", "Recents")
    ConfigContract.ACTION_SCROLL_UP -> l(language, "滚轮向上", "滾輪向上", "Scroll up")
    ConfigContract.ACTION_SCROLL_DOWN -> l(language, "滚轮向下", "滾輪向下", "Scroll down")
    ConfigContract.TRIPLE_ACTION_ENABLE_LASER -> l(language, "开启虚拟激光或手写笔鼠标", "開啟虛擬雷射或手寫筆滑鼠", "Enable virtual laser or stylus mouse")
    ConfigContract.TRIPLE_ACTION_LAUNCH_APP -> l(language, "启动应用", "啟動應用程式", "Launch app")
    ConfigContract.TRIPLE_ACTION_NONE -> l(language, "不启用", "不啟用", "Disabled")
    else -> action
}

private fun loadXApps(activity: MainActivity): List<XAppEntry> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return activity.packageManager.queryIntentActivities(launcherIntent, 0)
        .map { info ->
            XAppEntry(
                label = info.loadLabel(activity.packageManager).toString(),
                packageName = info.activityInfo.packageName,
            )
        }
        .distinctBy(XAppEntry::packageName)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, XAppEntry::label))
}

private fun readXRuntime(activity: MainActivity): XRuntime = runCatching {
    val bundle = activity.contentResolver.call(
        ConfigContract.URI,
        ConfigContract.METHOD_GET_RUNTIME,
        null,
        null,
    ) ?: return XRuntime()
    XRuntime(
        active = bundle.getBoolean(ConfigContract.KEY_RUNTIME_ACTIVE),
        compatible = bundle.getBoolean(ConfigContract.KEY_RUNTIME_COMPATIBLE),
        message = bundle.getString(ConfigContract.KEY_RUNTIME_MESSAGE).orEmpty(),
        laser = bundle.getBoolean(ConfigContract.KEY_RUNTIME_LASER),
        foreground = bundle.getString(ConfigContract.KEY_RUNTIME_FOREGROUND).orEmpty(),
        events = bundle.getStringArrayList(ConfigContract.KEY_RUNTIME_EVENTS).orEmpty(),
    )
}.getOrDefault(XRuntime())

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
