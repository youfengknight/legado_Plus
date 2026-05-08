/**
 * 书源检测界面Activity
 * 
 * 使用Jetpack Compose构建的书源检测界面
 * 提供实时检测进度、结果统计、结果列表等功能
 */
package io.legado.app.ui.book.source.check

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.fullScreen
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.ThemeStore
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.model.CheckSourceResultEvent
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity

/**
 * 书源检测Activity
 * 
 * 继承AppCompatActivity，使用Compose构建UI
 */
class CheckSourceActivity : AppCompatActivity() {

    private var backgroundDrawable: Drawable? = null
    private var currentViewModel: CheckSourceViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        observeEvents()
        
        setContent {
            CheckSourceContent(
                backgroundDrawable = backgroundDrawable,
                onBackClick = { finish() },
                onOpenConfig = { showDialogFragment<CheckSourceConfig>() },
                onEditSource = { sourceUrl ->
                    startActivity<BookSourceEditActivity> {
                        putExtra("sourceUrl", sourceUrl)
                    }
                },
                onDebugSource = { sourceUrl ->
                    startActivity<BookSourceDebugActivity> {
                        putExtra("key", sourceUrl)
                    }
                },
                onViewModelCreated = { viewModel ->
                    currentViewModel = viewModel
                }
            )
        }
    }

    private fun observeEvents() {
        observeEvent<String>(EventBus.CHECK_SOURCE) { message ->
            currentViewModel?.updateCheckMessage(message)
        }

        observeEvent<CheckSourceResultEvent>(EventBus.CHECK_SOURCE_RESULT) { result ->
            currentViewModel?.onCheckResult(result)
        }
        
        observeEvent<Int>(EventBus.CHECK_SOURCE_DONE) {
            currentViewModel?.onCheckComplete()
        }
    }

    private fun loadBackgroundImage() {
        try {
            backgroundDrawable = ThemeConfig.getBgImage(this, windowManager.defaultDisplay.run {
                android.util.DisplayMetrics().apply { getMetrics(this) }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initTheme() {
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(R.style.AppTheme_Dark)
            }
            io.legado.app.constant.Theme.Light -> {
                setTheme(R.style.AppTheme_Light)
            }
            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(R.style.AppTheme_Light)
                } else {
                    setTheme(R.style.AppTheme_Dark)
                }
            }
        }
    }

    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

/**
 * 书源检测界面的Compose内容
 */
@Composable
fun CheckSourceContent(
    backgroundDrawable: Drawable?,
    onBackClick: () -> Unit,
    onOpenConfig: () -> Unit,
    onEditSource: (String) -> Unit,
    onDebugSource: (String) -> Unit,
    onViewModelCreated: (CheckSourceViewModel) -> Unit
) {
    val context = LocalContext.current

    val primaryColor = remember { ThemeStore.primaryColor(context) }
    val accentColor = remember { ThemeStore.accentColor(context) }
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val textPrimaryColor = remember { ThemeStore.textColorPrimary(context) }
    val textSecondaryColor = remember { ThemeStore.textColorSecondary(context) }

    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    val primary = remember(primaryColor) { Color(primaryColor) }
    val secondary = remember(accentColor) { Color(accentColor) }
    val onBackground = remember(textPrimaryColor) { Color(textPrimaryColor) }
    val onBackgroundVariant = remember(textSecondaryColor) { Color(textSecondaryColor) }
    
    val surface = remember(background, isLight) {
        lerp(background, Color.White, if (isLight) 0.04f else 0.10f)
    }
    
    val surfaceVariant = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    }
    
    val outline = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    }
    
    val pagePrimary = remember(primary, isLight) {
        if (isLight) primary else lerp(primary, Color.White, 0.20f)
    }
    
    val pageOnBackgroundVariant = remember(onBackgroundVariant, onBackground, isLight) {
        if (isLight) onBackgroundVariant else lerp(onBackgroundVariant, onBackground, 0.32f)
    }
    
    val pageSurfaceVariant = remember(surfaceVariant, onBackground, isLight) {
        if (isLight) surfaceVariant else lerp(surfaceVariant, onBackground, 0.08f)
    }

    val colorScheme = remember(
        isLight,
        pagePrimary,
        secondary,
        background,
        onBackground,
        pageOnBackgroundVariant,
        surface,
        pageSurfaceVariant,
        outline
    ) {
        if (isLight) {
            lightColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.75f),
                onPrimary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFE53935),
                onError = Color.White
            )
        } else {
            darkColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.8f),
                onPrimary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFFF5252),
                onError = Color.Black
            )
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        BoxWithBackground(
            backgroundDrawable = backgroundDrawable,
            backgroundColor = background
        ) {
            val viewModel: CheckSourceViewModel = viewModel()
            
            onViewModelCreated(viewModel)
            
            CheckSourceScreen(
                viewModel = viewModel,
                onBackClick = onBackClick,
                onOpenConfig = onOpenConfig,
                onEditSource = onEditSource,
                onDebugSource = onDebugSource
            )
        }
    }
}

/**
 * 带背景的Box容器
 */
@Composable
fun BoxWithBackground(
    backgroundDrawable: Drawable?,
    backgroundColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundDrawable != null) {
            val overlayAlpha = if (backgroundColor.luminance() > 0.5f) 0.22f else 0.40f
            
            Image(
                bitmap = backgroundDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor.copy(alpha = overlayAlpha))
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(backgroundColor)
            )
        }

        content()
    }
}
