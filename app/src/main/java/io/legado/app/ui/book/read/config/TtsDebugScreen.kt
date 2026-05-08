package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.setPadding
import androidx.core.widget.addTextChangedListener
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.databinding.ItemSelectorSingleBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.util.Date

data class TestHistory(
    val timestamp: Long,
    val text: String,
    val success: Boolean,
    val duration: Long,
    val errorMessage: String? = null
)

data class TtsTestResult(
    val success: Boolean,
    val audioUrl: String? = null,
    val duration: Long = 0,
    val errorMessage: String? = null,
    val debugLogs: List<String> = emptyList(),
    val jsLibCode: String? = null,
    val resultData: Map<String, Any>? = null
)

data class DebugLog(
    val level: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

class TtsDebugActivity : AppCompatActivity() {

    private var bgDrawable: Drawable? = null
    private var ttsId: Long = 0
    private var mediaPlayer: android.media.MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        
        ttsId = intent.getLongExtra("ttsId", 0)
        
        setContent {
            TtsDebugContent(
                bgDrawable = bgDrawable,
                ttsId = ttsId,
                onBackClick = { finish() },
                mediaPlayer = mediaPlayer,
                onMediaPlayerCreated = { mediaPlayer = it }
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    @Suppress("DEPRECATION")
    private fun loadBackgroundImage() {
        try {
            val metrics = android.util.DisplayMetrics()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            bgDrawable = ThemeConfig.getBgImage(this, metrics)
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
fun TtsDebugContent(
    bgDrawable: Drawable?,
    ttsId: Long,
    onBackClick: () -> Unit,
    mediaPlayer: android.media.MediaPlayer?,
    onMediaPlayerCreated: (android.media.MediaPlayer?) -> Unit
) {
    val context = LocalContext.current

    val primaryColorValue = remember { ThemeStore.primaryColor(context) }
    val accentColor = remember { ThemeStore.accentColor(context) }
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val textPrimaryColor = remember { ThemeStore.textColorPrimary(context) }
    val textSecondaryColor = remember { ThemeStore.textColorSecondary(context) }

    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    val primary = remember(primaryColorValue) { Color(primaryColorValue) }
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
                onPrimary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
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
                onPrimary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
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
        TtsDebugBoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            TtsDebugScreen(
                ttsId = ttsId,
                onBackClick = onBackClick,
                mediaPlayer = mediaPlayer,
                onMediaPlayerCreated = onMediaPlayerCreated
            )
        }
    }
}

@Composable
fun TtsDebugBoxWithBackground(
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
                contentScale = ContentScale.Crop
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsDebugScreen(
    ttsId: Long,
    onBackClick: () -> Unit,
    mediaPlayer: android.media.MediaPlayer?,
    onMediaPlayerCreated: (android.media.MediaPlayer?) -> Unit
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    val coroutineScope = rememberCoroutineScope()
    
    var httpTTS by remember { mutableStateOf<HttpTTS?>(null) }
    var testText by remember { mutableStateOf("这是一段测试文本") }
    var speed by remember { mutableStateOf(5) }
    var selectedSpeaker by remember { mutableStateOf("") }
    var pitch by remember { mutableStateOf(0) }
    
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TtsTestResult?>(null) }
    var testHistory by remember { mutableStateOf<List<TestHistory>>(emptyList()) }
    var debugLogs by remember { mutableStateOf<List<DebugLog>>(emptyList()) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var currentMediaPlayer by remember { mutableStateOf(mediaPlayer) }
    
    val speakers = remember { mutableStateListOf<String>() }
    val loginInfo = remember { mutableStateMapOf<String, String>() }
    val rowUis = remember { mutableStateListOf<RowUi>() }
    
    var showLogDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(ttsId) {
        httpTTS = withContext(Dispatchers.IO) {
            appDb.httpTTSDao.get(ttsId)
        }
        
        httpTTS?.let { tts ->
            loginInfo.putAll(tts.getLoginInfoMap())
            
            tts.jsLib?.let { jsLib ->
                parseSpeakersFromJsLib(jsLib)?.let { speakerList ->
                    speakers.clear()
                    speakers.addAll(speakerList)
                    if (speakers.isNotEmpty() && selectedSpeaker.isEmpty()) {
                        selectedSpeaker = speakers[0]
                    }
                }
            }
            
            tts.loginUi?.let { loginUiStr ->
                val codeStr = loginUiStr.let {
                    when {
                        it.startsWith("@js:") -> it.substring(4)
                        it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                        else -> null
                    }
                }
                
                if (codeStr != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val loginUiJson = evalLoginUiJs(tts, codeStr, loginInfo.toMap())
                            val rows = GSON.fromJsonArray<RowUi>(loginUiJson).getOrNull()
                            rows?.let {
                                rowUis.clear()
                                rowUis.addAll(it)
                            }
                        } catch (e: Exception) {
                            AppLog.put("解析loginUi失败: ${e.message}", e)
                        }
                    }
                } else {
                    val rows = GSON.fromJsonArray<RowUi>(loginUiStr).getOrNull()
                    rows?.let {
                        rowUis.clear()
                        rowUis.addAll(it)
                    }
                }
            }
        }
    }
    
    if (showLogDialog && debugLogs.isNotEmpty()) {
        Dialog(onDismissRequest = { showLogDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "调试日志",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showLogDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(debugLogs) { log ->
                            Surface(
                                color = when (log.level) {
                                    "ERROR" -> MaterialTheme.colorScheme.errorContainer
                                    "WARN" -> Color(0xFFFFF3E0)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "[${log.level}] ${formatTimestamp(log.timestamp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (log.level) {
                                            "ERROR" -> MaterialTheme.colorScheme.error
                                            "WARN" -> Color(0xFFFF6F00)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    Text(
                                        text = log.message,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                context.sendToClip(debugLogs.joinToString("\n") { 
                                    "[${it.level}] ${formatTimestamp(it.timestamp)}: ${it.message}" 
                                })
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("复制日志")
                        }
                        Button(
                            onClick = { debugLogs = emptyList() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("清空日志")
                        }
                    }
                }
            }
        }
    }
    
    if (showSourceDialog && testResult != null) {
        Dialog(onDismissRequest = { showSourceDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "源码查看",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showSourceDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        testResult?.jsLibCode?.let { code ->
                            item {
                                Column {
                                    Text(
                                        text = "jsLib代码",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = code,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        testResult?.resultData?.let { data ->
                            item {
                                Column {
                                    Text(
                                        text = "result参数",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = GSON.toJson(data),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        testResult?.debugLogs?.let { logs ->
                            item {
                                Column {
                                    Text(
                                        text = "执行日志",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = logs.joinToString("\n"),
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            val sourceCode = buildString {
                                testResult?.jsLibCode?.let { append("=== jsLib代码 ===\n$it\n\n") }
                                testResult?.resultData?.let { append("=== result参数 ===\n${GSON.toJson(it)}\n\n") }
                                testResult?.debugLogs?.let { append("=== 执行日志 ===\n${it.joinToString("\n")}") }
                            }
                            context.startActivity<CodeEditActivity> {
                                putExtra("text", sourceCode)
                                putExtra("title", "TTS调试源码")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("在编辑器中查看")
                    }
                }
            }
        }
    }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Text(
                        text = "TTS调试工具",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showLogDialog = true }) {
                        Icon(Icons.Default.Description, contentDescription = "查看日志")
                    }
                    IconButton(onClick = { showSourceDialog = true }) {
                        Icon(Icons.Default.Code, contentDescription = "查看源码")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            httpTTS?.let { tts ->
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "引擎信息",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "名称: ${tts.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "ID: ${tts.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (rowUis.isNotEmpty()) {
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "登录配置",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowUis.forEachIndexed { index, rowUi ->
                                    when (rowUi.type) {
                                        RowUi.Type.text, RowUi.Type.password -> {
                                            OutlinedTextField(
                                                value = loginInfo[rowUi.name] ?: rowUi.default ?: "",
                                                onValueChange = { loginInfo[rowUi.name] = it },
                                                label = { Text(rowUi.viewName ?: rowUi.name) },
                                                singleLine = true,
                                                visualTransformation = if (rowUi.type == RowUi.Type.password) 
                                                    androidx.compose.ui.text.input.PasswordVisualTransformation() 
                                                else 
                                                    androidx.compose.ui.text.input.VisualTransformation.None,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        
                                        RowUi.Type.select -> {
                                            val chars = rowUi.chars?.filterNotNull() ?: listOf()
                                            var expanded by remember { mutableStateOf(false) }
                                            val currentValue = loginInfo[rowUi.name] ?: rowUi.default ?: ""
                                            
                                            ExposedDropdownMenuBox(
                                                expanded = expanded,
                                                onExpandedChange = { expanded = !expanded }
                                            ) {
                                                OutlinedTextField(
                                                    value = currentValue,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text(rowUi.viewName ?: rowUi.name) },
                                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                )
                                                
                                                ExposedDropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false }
                                                ) {
                                                    chars.forEach { char ->
                                                        DropdownMenuItem(
                                                            text = { Text(char) },
                                                            onClick = {
                                                                loginInfo[rowUi.name] = char
                                                                expanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        
                                        RowUi.Type.button -> {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        executeLoginAction(tts, rowUi.action, loginInfo.toMap())
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(rowUi.viewName ?: rowUi.name)
                                            }
                                        }
                                        
                                        RowUi.Type.toggle -> {
                                            val chars = rowUi.chars?.filterNotNull() ?: listOf()
                                            var currentIndex by remember { 
                                                mutableStateOf(chars.indexOf(loginInfo[rowUi.name] ?: rowUi.default).let { if (it < 0) 0 else it })
                                            }
                                            
                                            Surface(
                                                onClick = {
                                                    currentIndex = (currentIndex + 1) % chars.size
                                                    loginInfo[rowUi.name] = chars[currentIndex]
                                                    coroutineScope.launch {
                                                        executeLoginAction(tts, rowUi.action, loginInfo.toMap())
                                                    }
                                                },
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(rowUi.viewName ?: rowUi.name)
                                                    Text(
                                                        text = chars.getOrNull(currentIndex) ?: "",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "测试文本",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${testText.length} 字",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 200.dp),
                            placeholder = { Text("输入要测试的文本") }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { testText = "这是一段测试文本" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("默认文本")
                            }
                            Button(
                                onClick = { testText = "床前明月光，疑是地上霜。举头望明月，低头思故乡。" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("诗词")
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "参数配置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "语速: $speed",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = speed.toFloat(),
                            onValueChange = { speed = it.toInt() },
                            valueRange = -10f..10f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "音调: $pitch",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = pitch.toFloat(),
                            onValueChange = { pitch = it.toInt() },
                            valueRange = -100f..100f,
                            steps = 200,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (speakers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var speakerExpanded by remember { mutableStateOf(false) }
                            
                            Text(
                                text = "音色",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            ExposedDropdownMenuBox(
                                expanded = speakerExpanded,
                                onExpandedChange = { speakerExpanded = !speakerExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedSpeaker,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = speakerExpanded)
                                    }
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = speakerExpanded,
                                    onDismissRequest = { speakerExpanded = false }
                                ) {
                                    speakers.forEach { speaker ->
                                        DropdownMenuItem(
                                            text = { Text(speaker) },
                                            onClick = {
                                                selectedSpeaker = speaker
                                                speakerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                val startTime = System.currentTimeMillis()
                                debugLogs = emptyList()
                                
                                try {
                                    addDebugLog(debugLogs, "INFO", "开始测试TTS引擎")
                                    addDebugLog(debugLogs, "INFO", "引擎名称: ${tts.name}")
                                    addDebugLog(debugLogs, "INFO", "测试文本: $testText")
                                    addDebugLog(debugLogs, "INFO", "语速: $speed, 音调: $pitch, 音色: $selectedSpeaker")
                                    
                                    tts.putLoginInfo(GSON.toJson(loginInfo))
                                    addDebugLog(debugLogs, "INFO", "已保存登录信息: ${GSON.toJson(loginInfo)}")
                                    
                                    val result = withContext(Dispatchers.IO) {
                                        testTtsEngine(
                                            tts = tts,
                                            text = testText,
                                            speed = speed,
                                            speaker = selectedSpeaker,
                                            pitch = pitch,
                                            loginInfo = loginInfo.toMap(),
                                            onLog = { level, msg ->
                                                debugLogs = debugLogs + DebugLog(level, msg)
                                            }
                                        )
                                    }
                                    
                                    testResult = result
                                    val duration = System.currentTimeMillis() - startTime
                                    
                                    testHistory = testHistory + TestHistory(
                                        timestamp = System.currentTimeMillis(),
                                        text = testText,
                                        success = result.success,
                                        duration = duration,
                                        errorMessage = result.errorMessage
                                    )
                                    
                                    if (result.success) {
                                        addDebugLog(debugLogs, "INFO", "测试成功！耗时: ${duration}ms")
                                        addDebugLog(debugLogs, "INFO", "音频URL: ${result.audioUrl}")
                                        context.toastOnUi("测试成功！耗时 ${duration}ms")
                                    } else {
                                        addDebugLog(debugLogs, "ERROR", "测试失败: ${result.errorMessage}")
                                        context.toastOnUi("测试失败: ${result.errorMessage}")
                                    }
                                } catch (e: Exception) {
                                    val duration = System.currentTimeMillis() - startTime
                                    addDebugLog(debugLogs, "ERROR", "异常: ${e.message}")
                                    addDebugLog(debugLogs, "ERROR", e.stackTraceToString())
                                    
                                    testResult = TtsTestResult(
                                        success = false,
                                        errorMessage = e.message ?: "未知错误",
                                        debugLogs = debugLogs.map { "[${it.level}] ${it.message}" }
                                    )
                                    testHistory = testHistory + TestHistory(
                                        timestamp = System.currentTimeMillis(),
                                        text = testText,
                                        success = false,
                                        duration = duration,
                                        errorMessage = e.message
                                    )
                                    context.toastOnUi("测试失败: ${e.message}")
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting && testText.isNotEmpty()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTesting) "测试中..." else "开始测试")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            speed = 5
                            pitch = 0
                            if (speakers.isNotEmpty()) {
                                selectedSpeaker = speakers[0]
                            }
                            testResult = null
                            debugLogs = emptyList()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重置")
                    }
                }
                
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (result.success && result.audioUrl != null) {
                        Surface(
                            color = containerColor,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "试听",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            if (isPlaying) {
                                                currentMediaPlayer?.stop()
                                                currentMediaPlayer?.release()
                                                currentMediaPlayer = null
                                                onMediaPlayerCreated(null)
                                                isPlaying = false
                                            } else {
                                                try {
                                                    val mp = android.media.MediaPlayer()
                                                    mp.setDataSource(result.audioUrl)
                                                    mp.prepareAsync()
                                                    mp.setOnPreparedListener {
                                                        mp.start()
                                                        isPlaying = true
                                                    }
                                                    mp.setOnCompletionListener {
                                                        mp.release()
                                                        currentMediaPlayer = null
                                                        onMediaPlayerCreated(null)
                                                        isPlaying = false
                                                    }
                                                    mp.setOnErrorListener { _, what, extra ->
                                                        context.toastOnUi("播放失败: $what, $extra")
                                                        mp.release()
                                                        currentMediaPlayer = null
                                                        onMediaPlayerCreated(null)
                                                        isPlaying = false
                                                        true
                                                    }
                                                    currentMediaPlayer = mp
                                                    onMediaPlayerCreated(mp)
                                                } catch (e: Exception) {
                                                    context.toastOnUi("播放失败: ${e.message}")
                                                    isPlaying = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isPlaying) "停止" else "播放")
                                    }
                                    
                                    OutlinedButton(
                                        onClick = {
                                            context.openUrl(result.audioUrl!!)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("浏览器打开")
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "测试结果",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    result.audioUrl?.let { url ->
                                        IconButton(onClick = { context.sendToClip(url) }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "复制URL")
                                        }
                                    }
                                    IconButton(onClick = { showSourceDialog = true }) {
                                        Icon(Icons.Default.Code, contentDescription = "查看源码")
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = if (result.success) "成功" else "失败",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "耗时: ${result.duration}ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
                            result.audioUrl?.let { url ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "音频URL:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            
                            result.errorMessage?.let { error ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "错误: $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            
                            if (result.debugLogs.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "执行日志 (${result.debugLogs.size}条):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = result.debugLogs.take(3).joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

fun addDebugLog(logs: List<DebugLog>, level: String, message: String): List<DebugLog> {
    return logs + DebugLog(level, message)
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

suspend fun evalLoginUiJs(tts: HttpTTS, jsStr: String, loginInfo: Map<String, String>): String? {
    return try {
        runScriptWithContext {
            tts.evalJS(jsStr) {
                put("result", loginInfo.toMutableMap())
            }.toString()
        }
    } catch (e: Exception) {
        AppLog.put("evalLoginUiJs error: ${e.message}", e)
        null
    }
}

suspend fun executeLoginAction(tts: HttpTTS, action: String?, loginInfo: Map<String, String>) {
    if (action.isAbsUrl()) {
        appCtx.openUrl(action!!)
    } else if (action != null) {
        try {
            runScriptWithContext {
                tts.evalJS(action) {
                    put("result", loginInfo.toMutableMap())
                }
            }
        } catch (e: Exception) {
            AppLog.put("executeLoginAction error: ${e.message}", e)
        }
    }
}

fun parseSpeakersFromJsLib(jsLib: String): List<String>? {
    return try {
        val speakerMapRegex = Regex("""var\s+speakerMap\s*=\s*\{([^}]+)\}""")
        val match = speakerMapRegex.find(jsLib) ?: return null
        
        val mapContent = match.groupValues[1]
        val speakerRegex = Regex("""'([^']+)'\s*:""")
        speakerRegex.findAll(mapContent).map { it.groupValues[1] }.toList()
    } catch (e: Exception) {
        null
    }
}

suspend fun testTtsEngine(
    tts: HttpTTS,
    text: String,
    speed: Int,
    speaker: String,
    pitch: Int,
    loginInfo: Map<String, String>,
    onLog: (String, String) -> Unit
): TtsTestResult {
    val debugLogs = mutableListOf<String>()
    
    return try {
        onLog("INFO", "准备测试参数")
        
        val result = mutableMapOf<String, Any>()
        result.putAll(loginInfo)
        result["音色"] = speaker
        result["音调"] = pitch
        
        onLog("INFO", "登录信息: ${GSON.toJson(result)}")
        onLog("INFO", "测试文本: $text")
        onLog("INFO", "语速: $speed")
        
        onLog("INFO", "保存登录信息到TTS配置")
        tts.putLoginInfo(GSON.toJson(result))
        
        onLog("INFO", "创建AnalyzeUrl实例")
        val analyzeUrl = io.legado.app.model.analyzeRule.AnalyzeUrl(
            tts.url,
            speakText = text,
            speakSpeed = speed,
            source = tts,
            readTimeout = 30 * 1000L
        )
        
        onLog("INFO", "构建请求URL")
        val url = analyzeUrl.url
        onLog("INFO", "请求URL: $url")
        
        onLog("INFO", "发送HTTP请求")
        val response = analyzeUrl.getResponseAwait()
        onLog("INFO", "响应状态码: ${response.code}")
        onLog("INFO", "响应Content-Type: ${response.headers["Content-Type"]}")
        
        val contentType = response.headers["Content-Type"]?.substringBefore(";")
        
        when {
            contentType?.startsWith("audio/") == true -> {
                onLog("INFO", "直接返回音频流")
                val audioUrl = url.toString()
                response.body.close()
                
                TtsTestResult(
                    success = true,
                    audioUrl = audioUrl,
                    debugLogs = debugLogs,
                    jsLibCode = tts.jsLib,
                    resultData = result
                )
            }
            
            contentType == "application/json" || contentType?.startsWith("text/") == true -> {
                onLog("INFO", "返回JSON数据")
                val responseBody = response.body.string()
                onLog("INFO", "响应内容: $responseBody")
                
                val json = io.legado.app.utils.jsonPath.parse(responseBody)
                val audioUrl = json.read<String>("$.data.audio_url") 
                    ?: json.read<String>("$.audio_url")
                    ?: json.read<String>("$.url")
                
                if (!audioUrl.isNullOrBlank()) {
                    onLog("INFO", "从JSON中提取音频URL: $audioUrl")
                    TtsTestResult(
                        success = true,
                        audioUrl = audioUrl,
                        debugLogs = debugLogs,
                        jsLibCode = tts.jsLib,
                        resultData = result
                    )
                } else {
                    onLog("ERROR", "JSON中未找到音频URL")
                    TtsTestResult(
                        success = false,
                        errorMessage = "JSON中未找到音频URL\n响应: $responseBody",
                        debugLogs = debugLogs,
                        jsLibCode = tts.jsLib,
                        resultData = result
                    )
                }
            }
            
            else -> {
                val responseBody = response.body.string()
                onLog("ERROR", "未知的Content-Type: $contentType")
                onLog("ERROR", "响应内容: $responseBody")
                
                TtsTestResult(
                    success = false,
                    errorMessage = "未知的Content-Type: $contentType\n响应: $responseBody",
                    debugLogs = debugLogs,
                    jsLibCode = tts.jsLib,
                    resultData = result
                )
            }
        }
    } catch (e: Exception) {
        onLog("ERROR", "异常: ${e.message}")
        onLog("ERROR", e.stackTraceToString())
        TtsTestResult(
            success = false,
            errorMessage = e.message ?: "测试失败",
            debugLogs = debugLogs,
            jsLibCode = tts.jsLib
        )
    }
}

@Composable
fun debugToolsCardContainerColor(): Color {
    val context = LocalContext.current
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    return remember(background, isLight) {
        lerp(background, if (isLight) Color.White else Color.Black, if (isLight) 0.08f else 0.12f)
    }
}

@Composable
fun debugToolsTopBarContainerColor(): Color {
    val context = LocalContext.current
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    return remember(background, isLight) {
        lerp(background, if (isLight) Color.White else Color.Black, if (isLight) 0.04f else 0.08f)
    }
}
