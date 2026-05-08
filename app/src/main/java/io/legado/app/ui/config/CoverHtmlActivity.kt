package io.legado.app.ui.config

import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.CoverHtmlTemplateConfig
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

class CoverHtmlActivity : AppCompatActivity() {

    private var bgDrawable: Drawable? = null

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TEMPLATE_ID = "templateId"
        private const val EXTRA_IS_NEW = "isNew"
        
        const val MODE_TEMPLATE_LIST = 0
        const val MODE_EDIT_TEMPLATE = 1
        
        fun startTemplateList(context: Context) {
            val intent = Intent(context, CoverHtmlActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_TEMPLATE_LIST)
            }
            context.startActivity(intent)
        }
        
        fun startEditTemplate(context: Context, templateId: String? = null, isNew: Boolean = false) {
            val intent = Intent(context, CoverHtmlActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_EDIT_TEMPLATE)
                putExtra(EXTRA_TEMPLATE_ID, templateId)
                putExtra(EXTRA_IS_NEW, isNew)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        
        val mode = intent.getIntExtra(EXTRA_MODE, MODE_TEMPLATE_LIST)
        val templateId = intent.getStringExtra(EXTRA_TEMPLATE_ID)
        val isNew = intent.getBooleanExtra(EXTRA_IS_NEW, false)
        
        setContent {
            CoverHtmlContent(
                bgDrawable = bgDrawable,
                mode = mode,
                templateId = templateId,
                isNew = isNew,
                onBackClick = { finish() }
            )
        }
    }

    private fun loadBackgroundImage() {
        try {
            bgDrawable = ThemeConfig.getBgImage(this, windowManager.defaultDisplay.run {
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
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }
            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }
            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
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

@Composable
fun CoverHtmlContent(
    bgDrawable: Drawable?,
    mode: Int,
    templateId: String?,
    isNew: Boolean,
    onBackClick: () -> Unit
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
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            var currentMode by remember { mutableStateOf(mode) }
            var currentTemplateId by remember { mutableStateOf(templateId) }
            var currentIsNew by remember { mutableStateOf(isNew) }
            
            when (currentMode) {
                CoverHtmlActivity.MODE_TEMPLATE_LIST -> {
                    CoverHtmlTemplateListScreen(
                        onBackClick = onBackClick,
                        onEditTemplate = { template ->
                            if (template == null) {
                                currentMode = CoverHtmlActivity.MODE_EDIT_TEMPLATE
                                currentIsNew = true
                                currentTemplateId = null
                            } else {
                                currentMode = CoverHtmlActivity.MODE_EDIT_TEMPLATE
                                currentIsNew = false
                                currentTemplateId = template.id
                            }
                        }
                    )
                }
                CoverHtmlActivity.MODE_EDIT_TEMPLATE -> {
                    val template = currentTemplateId?.let { 
                        CoverHtmlTemplateConfig.getTemplateById(it) 
                    }
                    CoverHtmlCodeScreen(
                        template = template,
                        isNewTemplate = currentIsNew,
                        onBackClick = { 
                            currentMode = CoverHtmlActivity.MODE_TEMPLATE_LIST 
                        },
                        onShowTemplateList = {
                            currentMode = CoverHtmlActivity.MODE_TEMPLATE_LIST
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun BoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            val overlayAlpha = if (bgColor.luminance() > 0.5f) 0.22f else 0.40f
            
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = overlayAlpha))
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }

        content()
    }
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
