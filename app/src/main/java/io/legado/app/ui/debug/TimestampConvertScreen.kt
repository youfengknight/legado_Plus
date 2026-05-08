/**
 * 时间戳转换工具界面 - Jetpack Compose实现
 * 
 * 功能说明：
 * 时间戳与日期字符串互相转换，支持：
 * - 时间戳转日期（支持10位秒级和13位毫秒级）
 * - 日期转时间戳
 * - 8种日期格式选择
 * - 获取当前时间戳
 * 
 * 界面结构：
 * - 时间戳输入区（含复制按钮）
 * - 日期格式选择器
 * - 日期输入区（含复制按钮）
 * - 当前时间按钮
 * - 结果显示区
 */
package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 支持的日期格式列表
 * 用于日期转时间戳时解析日期字符串
 */
private val formats = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd",
    "yyyy/MM/dd HH:mm:ss",
    "yyyy/MM/dd",
    "MM-dd HH:mm:ss",
    "HH:mm:ss",
    "yyyyMMddHHmmss",
    "yyyyMMdd"
)

/**
 * 时间戳转换界面
 * 
 * @param onBackClick 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimestampConvertScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    var timestamp by remember { mutableStateOf("") }
    // 日期字符串输入
    var dateStr by remember { mutableStateOf("") }
    // 转换结果
    var result by remember { mutableStateOf("") }
    // 当前选中的日期格式索引
    var currentFormatIndex by remember { mutableStateOf(0) }
    // 格式下拉菜单是否展开
    var formatExpanded by remember { mutableStateOf(false) }

    // 初始化：显示当前时间戳
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        timestamp = now.toString()
        result = formatTimestamp(now)
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
                        text = stringResource(R.string.debug_timestamp),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 时间戳输入卡片
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.debug_timestamp_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        // 复制按钮
                        IconButton(
                            onClick = { context.sendToClip(timestamp) }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 时间戳输入框
                    OutlinedTextField(
                        value = timestamp,
                        onValueChange = { timestamp = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_timestamp_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 时间戳转日期按钮
                    Button(
                        onClick = {
                            if (timestamp.isEmpty()) {
                                context.toastOnUi(R.string.input_is_empty)
                                return@Button
                            }
                            
                            try {
                                var ts = timestamp.trim().toLong()
                                // 10位时间戳是秒级，需要转换为毫秒
                                if (timestamp.trim().length == 10) {
                                    ts *= 1000
                                }
                                result = formatTimestamp(ts)
                            } catch (e: Exception) {
                                result = "错误: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.debug_timestamp_to_date))
                    }
                }
            }

            // 日期格式选择卡片
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "日期格式",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 日期格式下拉选择框
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = !formatExpanded }
                    ) {
                        OutlinedTextField(
                            value = formats[currentFormatIndex],
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded)
                            }
                        )
                        
                        ExposedDropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false }
                        ) {
                            formats.forEachIndexed { index, format ->
                                DropdownMenuItem(
                                    text = { Text(format) },
                                    onClick = {
                                        currentFormatIndex = index
                                        formatExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 日期输入卡片
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.debug_date_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        // 复制按钮
                        IconButton(
                            onClick = { context.sendToClip(dateStr) }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 日期输入框
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_date_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 日期转时间戳按钮
                    Button(
                        onClick = {
                            if (dateStr.isEmpty()) {
                                context.toastOnUi(R.string.input_is_empty)
                                return@Button
                            }
                            
                            try {
                                // 使用选中的格式解析日期
                                val format = SimpleDateFormat(formats[currentFormatIndex], Locale.getDefault())
                                format.timeZone = TimeZone.getDefault()
                                val date = format.parse(dateStr.trim())
                                date?.let {
                                    val ts = it.time
                                    timestamp = ts.toString()
                                    result = formatTimestamp(ts)
                                }
                            } catch (e: Exception) {
                                result = "错误: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.debug_date_to_timestamp))
                    }
                }
            }

            // 获取当前时间按钮
            OutlinedButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    timestamp = now.toString()
                    result = formatTimestamp(now)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.debug_now))
            }

            // 结果显示卡片
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.debug_result),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        // 复制按钮
                        IconButton(
                            onClick = { context.sendToClip(result) }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = result.ifEmpty { "暂无结果" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (result.isEmpty()) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 格式化时间戳为日期字符串
 * 
 * @param timestamp 毫秒级时间戳
 * @return 格式化后的日期字符串 (yyyy-MM-dd HH:mm:ss)
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(date)
}
