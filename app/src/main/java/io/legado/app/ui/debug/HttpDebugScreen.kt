/**
 * HTTP请求调试工具界面 - Jetpack Compose实现
 * 
 * 功能说明：
 * 发送HTTP请求并查看响应，支持：
 * - GET/POST请求方法
 * - 自定义URL
 * - 自定义请求头（每行一个，格式: Key: Value）
 * - 自定义请求体（POST方法时显示）
 * - 预设User-Agent选择（默认/Chrome PC/Chrome Mobile/Safari iOS/Firefox/自定义）
 * - 查看响应头和响应体
 * - 查看请求源码和响应源码
 * 
 * 界面结构：
 * - URL输入区（含请求方法选择）
 * - User-Agent选择区
 * - 请求头输入区
 * - 请求体输入区（仅POST方法显示）
 * - 发送按钮
 * - 响应头显示区
 * - 响应体显示区
 */
package io.legado.app.ui.debug

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.StrResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

/** HTTP请求方法列表 */
private val methods = listOf("GET", "POST")

/** User-Agent选项显示名称的资源ID列表 */
private val uaNames = listOf(
    R.string.debug_ua_default,
    R.string.debug_ua_chrome_pc,
    R.string.debug_ua_chrome_mobile,
    R.string.debug_ua_safari_ios,
    R.string.debug_ua_firefox,
    R.string.debug_ua_custom
)

/**
 * 获取User-Agent值列表
 * 索引0为空字符串，表示使用AppConfig.userAgent全局配置
 * 索引5为空字符串，表示使用自定义UA
 */
private fun getUaValues(): List<String> = listOf(
    "",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${BuildConfig.Cronet_Main_Version} Safari/537.36",
    "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${BuildConfig.Cronet_Main_Version} Mobile Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    ""
)

/**
 * HTTP请求调试界面
 * 
 * @param onBackClick 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpDebugScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // OkHttp客户端
    val client = remember { OkHttpClient.Builder().build() }
    val uaValues = remember { getUaValues() }
    
    // 请求参数状态
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var methodIndex by remember { mutableStateOf(0) }
    var uaIndex by remember { mutableStateOf(0) }
    var customUa by remember { mutableStateOf("") }
    
    // 响应状态
    var responseHeaders by remember { mutableStateOf("") }
    var responseBody by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    // 保存最后一次请求/响应用于查看源码
    var lastResponse by remember { mutableStateOf<StrResponse?>(null) }
    var lastRequestSrc by remember { mutableStateOf<String?>(null) }
    
    // 对话框显示状态
    var showMenu by remember { mutableStateOf(false) }
    var showUaDialog by remember { mutableStateOf(false) }
    var showRequestSrcDialog by remember { mutableStateOf(false) }

    // 自定义UA输入对话框
    if (showUaDialog) {
        var tempUa by remember { mutableStateOf(customUa.ifEmpty { AppConfig.userAgent }) }
        AlertDialog(
            onDismissRequest = { 
                showUaDialog = false
                uaIndex = 0
            },
            title = { Text(stringResource(R.string.debug_ua_custom)) },
            text = {
                OutlinedTextField(
                    value = tempUa,
                    onValueChange = { tempUa = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.debug_user_agent)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customUa = tempUa.trim()
                    showUaDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUaDialog = false
                    uaIndex = 0
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // 请求源码查看对话框
    if (showRequestSrcDialog && lastRequestSrc != null) {
        Dialog(onDismissRequest = { showRequestSrcDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_request_src),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = lastRequestSrc!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { showRequestSrcDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }

    // 页面骨架
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    scrolledContainerColor = MaterialTheme.colorScheme.secondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                title = {
                    Text(
                        text = stringResource(R.string.debug_http_request),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // 右侧菜单
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // 查看响应源码
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.debug_response_src)) },
                                onClick = {
                                    showMenu = false
                                    // 直接跳转到代码编辑界面
                                    lastResponse?.let { response ->
                                        val sb = StringBuilder()
                                        sb.append("=== 响应行 ===\n")
                                        sb.append("HTTP/1.1 ${response.code()} ${response.message()}\n\n")
                                        sb.append("=== 响应头 ===\n")
                                        response.raw.headers.forEach { (name, value) ->
                                            sb.append("$name: $value\n")
                                        }
                                        sb.append("\n=== 响应体 ===\n")
                                        sb.append(response.body)
                                        
                                        val intent = android.content.Intent(context, io.legado.app.ui.code.CodeEditActivity::class.java).apply {
                                            putExtra("text", sb.toString())
                                            putExtra("title", context.getString(R.string.debug_response_src))
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                enabled = lastResponse != null,
                                leadingIcon = {
                                    Icon(Icons.Default.Http, contentDescription = null)
                                }
                            )
                            // 查看请求源码
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.debug_request_src)) },
                                onClick = {
                                    showMenu = false
                                    showRequestSrcDialog = true
                                },
                                enabled = lastRequestSrc != null,
                                leadingIcon = {
                                    Icon(Icons.Default.Upload, contentDescription = null)
                                }
                            )
                            HorizontalDivider()
                            // 清空
                            DropdownMenuItem(
                                text = { Text("清空") },
                                onClick = {
                                    showMenu = false
                                    url = ""
                                    headers = ""
                                    body = ""
                                    responseHeaders = ""
                                    responseBody = ""
                                    lastResponse = null
                                    lastRequestSrc = null
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        // 主内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // URL输入卡片（含请求方法选择）
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var methodExpanded by remember { mutableStateOf(false) }
                        
                        // 请求方法选择器
                        ExposedDropdownMenuBox(
                            expanded = methodExpanded,
                            onExpandedChange = { methodExpanded = !methodExpanded },
                            modifier = Modifier.width(100.dp)
                        ) {
                            OutlinedTextField(
                                value = methods[methodIndex],
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded)
                                }
                            )
                            
                            ExposedDropdownMenu(
                                expanded = methodExpanded,
                                onDismissRequest = { methodExpanded = false }
                            ) {
                                methods.forEachIndexed { index, method ->
                                    DropdownMenuItem(
                                        text = { Text(method) },
                                        onClick = {
                                            methodIndex = index
                                            methodExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        // URL输入框
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.debug_url_hint)) },
                            singleLine = true
                        )
                    }
                }
            }

            // User-Agent选择卡片
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var uaExpanded by remember { mutableStateOf(false) }
                    
                    Text(
                        text = stringResource(R.string.debug_user_agent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = uaExpanded,
                        onExpandedChange = { uaExpanded = !uaExpanded }
                    ) {
                        OutlinedTextField(
                            value = stringResource(uaNames[uaIndex]),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = uaExpanded)
                            }
                        )
                        
                        ExposedDropdownMenu(
                            expanded = uaExpanded,
                            onDismissRequest = { uaExpanded = false }
                        ) {
                            uaNames.forEachIndexed { index, nameRes ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(nameRes)) },
                                    onClick = {
                                        uaIndex = index
                                        uaExpanded = false
                                        // 选择"自定义"时弹出输入对话框
                                        if (index == uaNames.size - 1) {
                                            showUaDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 请求头输入卡片
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_headers),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = headers,
                        onValueChange = { headers = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        placeholder = { Text(stringResource(R.string.debug_headers_hint)) }
                    )
                }
            }

            // 请求体输入卡片（仅POST方法显示）
            if (methodIndex == 1) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.debug_body),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                        )
                    }
                }
            }

            // 发送按钮
            Button(
                onClick = {
                    if (url.isEmpty()) {
                        context.toastOnUi(R.string.debug_url_empty)
                        return@Button
                    }
                    
                    isLoading = true
                    responseBody = context.getString(R.string.debug_loading)
                    responseHeaders = ""
                    
                    // 使用协程发送异步请求
                    coroutineScope.launch {
                        try {
                            // 在IO线程执行网络请求
                            val response = withContext(Dispatchers.IO) {
                                doHttpRequest(
                                    client = client,
                                    url = url,
                                    methodIndex = methodIndex,
                                    headersText = headers,
                                    bodyText = body,
                                    uaIndex = uaIndex,
                                    uaValues = uaValues,
                                    customUa = customUa
                                )
                            }
                            
                            // 保存响应用于查看源码
                            lastResponse = response
                            lastRequestSrc = buildRequestSrc(
                                url = url,
                                methodIndex = methodIndex,
                                headersText = headers,
                                bodyText = body,
                                userAgent = getSelectedUa(uaIndex, uaValues, customUa)
                            )
                            
                            // 显示响应头信息
                            val sb = StringBuilder()
                            sb.append("状态码: ${response.code()}\n")
                            sb.append("消息: ${response.message()}\n")
                            sb.append("耗时: ${response.raw.receivedResponseAtMillis - response.raw.sentRequestAtMillis}ms")
                            responseHeaders = sb.toString()
                            responseBody = response.body ?: ""
                            
                        } catch (e: Exception) {
                            responseBody = "错误: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                // 加载中显示进度条
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.debug_send))
            }

            // 响应头显示卡片
            if (responseHeaders.isNotEmpty()) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debug_response_headers),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            // 复制按钮
                            IconButton(
                                onClick = { context.sendToClip(responseHeaders) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = responseHeaders,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 响应体显示卡片
            if (responseBody.isNotEmpty()) {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.debug_response_body),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            // 复制按钮
                            IconButton(
                                onClick = { context.sendToClip(responseBody) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = responseBody,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 获取当前选中的User-Agent字符串
 * 
 * @param uaIndex 选中的UA索引
 * @param uaValues UA值列表
 * @param customUa 自定义UA字符串
 * @return 选中的UA值
 */
private fun getSelectedUa(uaIndex: Int, uaValues: List<String>, customUa: String): String {
    return when {
        uaIndex == 0 -> AppConfig.userAgent // 默认使用全局配置
        uaIndex == uaValues.size - 1 -> customUa.ifEmpty { AppConfig.userAgent } // 自定义
        else -> uaValues[uaIndex] // 预设UA
    }
}

/**
 * 执行HTTP请求
 * 
 * @param client OkHttp客户端
 * @param url 请求URL
 * @param methodIndex 请求方法索引（0=GET, 1=POST）
 * @param headersText 请求头文本
 * @param bodyText 请求体内容
 * @param uaIndex UA索引
 * @param uaValues UA值列表
 * @param customUa 自定义UA
 * @return 响应对象
 */
private suspend fun doHttpRequest(
    client: OkHttpClient,
    url: String,
    methodIndex: Int,
    headersText: String,
    bodyText: String,
    uaIndex: Int,
    uaValues: List<String>,
    customUa: String
): StrResponse {
    val userAgent = getSelectedUa(uaIndex, uaValues, customUa)
    
    return client.newCallStrResponse {
        url(url)
        // 根据方法类型设置请求
        when (methodIndex) {
            0 -> get()
            1 -> {
                if (bodyText.isNotEmpty()) {
                    val requestBody = bodyText.toRequestBody("application/json; charset=UTF-8".toMediaType())
                    post(requestBody)
                }
            }
        }
        // 添加User-Agent头
        addHeader("User-Agent", userAgent)
        // 解析并添加自定义请求头
        if (headersText.isNotEmpty()) {
            parseHeaders(headersText).forEach { (key, value) ->
                // 跳过User-Agent，使用上面设置的值
                if (!key.equals("User-Agent", ignoreCase = true)) {
                    addHeader(key, value)
                }
            }
        }
    }
}

/**
 * 解析请求头文本
 * 
 * @param headersText 请求头文本，每行一个，格式: Key: Value
 * @return 解析后的请求头Map
 */
private fun parseHeaders(headersText: String): Map<String, String> {
    val headers = mutableMapOf<String, String>()
    headersText.lines().forEach { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2) {
            headers[parts[0].trim()] = parts[1].trim()
        }
    }
    return headers
}

/**
 * 构建请求源码字符串
 * 用于显示请求详情
 * 
 * @param url 请求URL
 * @param methodIndex 请求方法索引
 * @param headersText 请求头文本
 * @param bodyText 请求体内容
 * @param userAgent User-Agent字符串
 * @return 格式化的请求源码字符串
 */
private fun buildRequestSrc(
    url: String,
    methodIndex: Int,
    headersText: String,
    bodyText: String,
    userAgent: String
): String {
    val sb = StringBuilder()
    sb.append("=== 请求行 ===\n")
    sb.append("${if (methodIndex == 0) "GET" else "POST"} $url\n\n")
    sb.append("=== 请求头 ===\n")
    sb.append("User-Agent: $userAgent\n")
    if (headersText.isNotEmpty()) {
        headersText.lines().forEach { line ->
            // 跳过User-Agent行，使用上面设置的值
            if (line.split(":").firstOrNull()?.trim()?.equals("User-Agent", ignoreCase = true) != true) {
                sb.append("$line\n")
            }
        }
    }
    // POST请求添加请求体
    if (methodIndex == 1 && bodyText.isNotEmpty()) {
        sb.append("Content-Type: application/json; charset=UTF-8\n")
        sb.append("\n=== 请求体 ===\n")
        sb.append(bodyText)
    }
    return sb.toString()
}
