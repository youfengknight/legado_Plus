/**
 * 正则表达式测试工具界面 - Jetpack Compose实现
 * 
 * 功能说明：
 * 测试正则表达式匹配效果，支持：
 * - 输入正则表达式模式
 * - 输入待匹配文本
 * - 设置匹配选项（忽略大小写、多行模式、点匹配换行）
 * - 实时预览：输入时自动执行匹配
 * - 替换预览：显示替换后的文本效果
 * - 显示匹配结果（完整匹配、分组信息、位置详情）
 * - 高亮显示匹配内容
 * - 状态提示（成功/错误）
 * 
 * 界面结构：
 * - 正则表达式输入区（含选项复选框）
 * - 替换文本输入区
 * - 待匹配文本输入区
 * - 操作按钮（测试、清空）
 * - 状态提示区
 * - 匹配结果显示区
 * - 高亮显示区
 * - 替换预览区
 * 
 * 架构说明：
 * - 数据类 MatchResultData：封装单个匹配结果
 * - 数据类 TestResult：封装完整测试结果
 * - 函数 findMatches：执行匹配逻辑
 * - Composable RegexTestScreen：主界面
 * - Composable StatusCard：状态提示卡片
 * - Composable MatchInfoCard：匹配详情卡片
 * - Composable HighlightCard：高亮显示卡片
 * - Composable ReplacePreviewCard：替换预览卡片
 */
package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import java.util.regex.PatternSyntaxException

/**
 * 匹配结果数据类
 * 
 * 封装单个匹配项的信息，用于统一处理正则匹配和普通文本匹配的结果
 * 
 * @property start 匹配起始位置（包含）
 * @property end 匹配结束位置（不包含）
 * @property value 匹配的文本内容
 * @property groups 正则分组列表，普通文本匹配时为空
 */
private data class MatchResultData(
    val start: Int,
    val end: Int,
    val value: String,
    val groups: List<String> = emptyList()
)

/**
 * 测试结果数据类
 * 
 * 封装完整的测试结果，包含状态信息、匹配数据、高亮文本等
 * 
 * @property success 是否匹配成功
 * @property message 状态消息（成功/错误描述）
 * @property matchCount 匹配数量
 * @property matches 所有匹配项列表
 * @property highlightedText 带高亮样式的文本，用于显示匹配位置
 * @property replacedText 替换后的文本，仅当有替换文本时存在
 * @property matchInfo 匹配详情文本，包含位置索引信息
 */
private data class TestResult(
    val success: Boolean,
    val message: String,
    val matchCount: Int = 0,
    val matches: List<MatchResultData> = emptyList(),
    val highlightedText: AnnotatedString? = null,
    val replacedText: String? = null,
    val matchInfo: String? = null
)

/**
 * 正则表达式测试界面
 * 
 * 主界面 Composable，负责：
 * - 状态管理和用户交互
 * - 实时预览逻辑
 * - 布局渲染和组件组合
 * 
 * @param onBackClick 返回按钮点击回调
 * @param initialPattern 初始正则表达式，从外部传入时预填充
 * @param initialReplacement 初始替换文本，从外部传入时预填充
 * @param initialIsRegex 初始是否使用正则模式，从外部传入时预设
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexTestScreen(
    onBackClick: () -> Unit,
    initialPattern: String = "",
    initialReplacement: String = "",
    initialIsRegex: Boolean = true
) {
    val context = LocalContext.current
    
    var pattern by remember { mutableStateOf(initialPattern) }         // 正则表达式
    var input by remember { mutableStateOf("") }                       // 待匹配文本
    var replacement by remember { mutableStateOf(initialReplacement) } // 替换文本
    
    // 正则选项状态
    var ignoreCase by remember { mutableStateOf(false) }  // 忽略大小写
    var multiline by remember { mutableStateOf(false) }   // 多行模式
    var dotAll by remember { mutableStateOf(false) }      // 点匹配换行
    var useRegex by remember { mutableStateOf(initialIsRegex) }  // 是否使用正则
    var realtimePreview by remember { mutableStateOf(true) }     // 实时预览开关
    
    // 测试结果状态
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    
    /**
     * 执行匹配测试
     * 
     * 核心业务逻辑，处理流程：
     * 1. 验证输入是否为空
     * 2. 验证正则语法是否正确
     * 3. 执行匹配并收集结果
     * 4. 构建高亮文本和替换预览
     * 5. 生成匹配详情信息
     * 
     * @return 测试结果 TestResult
     */
    fun performTest(): TestResult {
        // 检查输入文本是否为空
        if (input.isEmpty()) {
            return TestResult(
                success = false,
                message = context.getString(R.string.debug_input_hint)
            )
        }
        
        // 检查匹配模式是否为空
        if (pattern.isEmpty()) {
            return TestResult(
                success = false,
                message = context.getString(R.string.pattern_empty)
            )
        }
        
        // 正则模式下验证语法
        if (useRegex) {
            try {
                // 构建正则选项
                val regexOptions = mutableSetOf<RegexOption>()
                if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
                if (multiline) regexOptions.add(RegexOption.MULTILINE)
                if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
                
                // 尝试创建正则表达式，验证语法
                kotlin.text.Regex(pattern, regexOptions)
            } catch (e: PatternSyntaxException) {
                return TestResult(
                    success = false,
                    message = context.getString(R.string.regex_syntax_error, e.localizedMessage)
                )
            }
        }
        
        try {
            // 执行匹配
            val matches = findMatches(input, pattern, useRegex, ignoreCase, multiline, dotAll)
            
            // 无匹配结果
            if (matches.isEmpty()) {
                return TestResult(
                    success = false,
                    message = context.getString(R.string.no_match_found),
                    matchCount = 0
                )
            }
            
            // 构建高亮显示文本
            val highlightColor = Color(0x40FFEB3B)  // 半透明黄色背景
            val highlightedText = buildAnnotatedString {
                var lastIndex = 0
                val sortedMatches = matches.sortedBy { it.start }
                
                for (match in sortedMatches) {
                    // 添加匹配前的普通文本
                    if (match.start > lastIndex) {
                        append(input.substring(lastIndex, match.start))
                    }
                    // 添加高亮的匹配文本
                    withStyle(style = SpanStyle(background = highlightColor)) {
                        append(match.value)
                    }
                    lastIndex = match.end
                }
                
                // 添加最后的普通文本
                if (lastIndex < input.length) {
                    append(input.substring(lastIndex))
                }
            }
            
            // 计算替换后的文本
            val replacedText = if (replacement.isNotEmpty()) {
                if (useRegex) {
                    // 正则替换
                    val regexOptions = mutableSetOf<RegexOption>()
                    if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
                    if (multiline) regexOptions.add(RegexOption.MULTILINE)
                    if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
                    input.replace(kotlin.text.Regex(pattern, regexOptions), replacement)
                } else {
                    // 普通文本替换
                    input.replace(pattern, replacement)
                }
            } else null
            
            // 构建匹配详情信息
            val infoBuilder = StringBuilder()
            infoBuilder.append(context.getString(R.string.match_times, matches.size))
            // 最多显示10个匹配位置
            matches.take(10).forEachIndexed { index, match ->
                infoBuilder.append("\n").append(
                    context.getString(R.string.match_position, index + 1, match.start, match.end)
                )
            }
            // 超过10个时显示省略提示
            if (matches.size > 10) {
                infoBuilder.append("\n...").append(
                    context.getString(R.string.more_matches, matches.size - 10)
                )
            }
            
            return TestResult(
                success = true,
                message = context.getString(R.string.regex_valid_match_success),
                matchCount = matches.size,
                matches = matches,
                highlightedText = highlightedText,
                replacedText = replacedText,
                matchInfo = infoBuilder.toString()
            )
            
        } catch (e: Exception) {
            return TestResult(
                success = false,
                message = e.message ?: context.getString(R.string.no_match_found)
            )
        }
    }
    
    // ========== 实时预览逻辑 ==========
    // 监听所有相关状态变化，自动触发测试
    LaunchedEffect(pattern, input, replacement, ignoreCase, multiline, dotAll, useRegex, realtimePreview) {
        if (realtimePreview && pattern.isNotEmpty() && input.isNotEmpty()) {
            testResult = performTest()
        }
    }
    
    // ========== 界面布局 ==========
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
                        text = stringResource(R.string.debug_regex_test),
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
        // 主内容区域：可滚动列布局
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== 正则表达式输入卡片 ==========
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题行：包含实时预览开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.debug_regex_pattern),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        // 实时预览开关
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.realtime_preview),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = realtimePreview,
                                onCheckedChange = { realtimePreview = it },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 正则表达式输入框
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_regex_pattern_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 使用正则表达式选项
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useRegex,
                                onCheckedChange = { useRegex = it }
                            )
                            Text(
                                text = stringResource(R.string.use_regex),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    
                    // 正则选项（仅在使用正则时显示）
                    if (useRegex) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 忽略大小写
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ignoreCase,
                                    onCheckedChange = { ignoreCase = it }
                                )
                                Text(
                                    text = stringResource(R.string.debug_regex_ignore_case),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            // 多行模式
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = multiline,
                                    onCheckedChange = { multiline = it }
                                )
                                Text(
                                    text = stringResource(R.string.debug_regex_multiline),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            
                            // 点匹配换行
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = dotAll,
                                    onCheckedChange = { dotAll = it }
                                )
                                Text(
                                    text = stringResource(R.string.debug_regex_dot_all),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // ========== 替换文本输入卡片 ==========
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.replace_to),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.replace_to)) },
                        singleLine = true
                    )
                }
            }

            // ========== 待匹配文本输入卡片 ==========
            Card(
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_input),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        placeholder = { Text(stringResource(R.string.debug_input_hint)) }
                    )
                }
            }

            // ========== 操作按钮（仅非实时预览模式显示） ==========
            if (!realtimePreview) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 测试按钮
                    Button(
                        onClick = {
                            if (pattern.isEmpty()) {
                                context.toastOnUi(R.string.debug_pattern_empty)
                                return@Button
                            }
                            if (input.isEmpty()) {
                                context.toastOnUi(R.string.input_is_empty)
                                return@Button
                            }
                            testResult = performTest()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.debug_test))
                    }
                    
                    // 清空按钮
                    OutlinedButton(
                        onClick = {
                            pattern = ""
                            input = ""
                            replacement = ""
                            testResult = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.clear))
                    }
                }
            }

            // ========== 结果显示区域 ==========
            testResult?.let { result ->
                // 状态提示卡片
                StatusCard(
                    success = result.success,
                    message = result.message,
                    matchCount = result.matchCount
                )
                
                // 匹配详情卡片（仅成功时显示）
                if (result.success && result.matchInfo != null) {
                    MatchInfoCard(
                        matchInfo = result.matchInfo,
                        onCopy = { context.sendToClip(result.matchInfo) }
                    )
                }
                
                // 高亮显示卡片
                result.highlightedText?.let { highlighted ->
                    HighlightCard(
                        highlightedText = highlighted
                    )
                }
                
                // 替换预览卡片（仅当有替换文本时显示）
                result.replacedText?.let { replaced ->
                    ReplacePreviewCard(
                        replacedText = replaced,
                        onCopy = { context.sendToClip(replaced) }
                    )
                }
            }
        }
    }
}

/**
 * 查找所有匹配项
 * 
 * 根据匹配模式（正则或普通文本）查找输入文本中的所有匹配项
 * 
 * @param input 输入文本
 * @param pattern 匹配模式
 * @param useRegex 是否使用正则模式
 * @param ignoreCase 忽略大小写（仅正则模式有效）
 * @param multiline 多行模式（仅正则模式有效）
 * @param dotAll 点匹配换行（仅正则模式有效）
 * @return 匹配结果列表
 */
private fun findMatches(
    input: String,
    pattern: String,
    useRegex: Boolean,
    ignoreCase: Boolean,
    multiline: Boolean,
    dotAll: Boolean
): List<MatchResultData> {
    val results = mutableListOf<MatchResultData>()
    
    if (useRegex) {
        // 正则模式：使用 Kotlin Regex API
        val regexOptions = mutableSetOf<RegexOption>()
        if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
        if (multiline) regexOptions.add(RegexOption.MULTILINE)
        if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
        
        val regex = kotlin.text.Regex(pattern, regexOptions)
        regex.findAll(input).forEach { matchResult ->
            results.add(
                MatchResultData(
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1,
                    value = matchResult.value,
                    groups = matchResult.groupValues.drop(1)  // 第一个元素是完整匹配，跳过
                )
            )
        }
    } else {
        // 普通文本模式：使用 String.indexOf 循环查找
        var startIndex = 0
        while (true) {
            val index = input.indexOf(pattern, startIndex)
            if (index == -1) break
            results.add(MatchResultData(index, index + pattern.length, pattern))
            startIndex = index + pattern.length
        }
    }
    
    return results
}

/**
 * 状态提示卡片
 * 
 * 显示匹配状态（成功/失败），包含图标和消息
 * 
 * @param success 是否成功
 * @param message 状态消息
 * @param matchCount 匹配数量
 * @param containerColor 容器背景色
 */
@Composable
private fun StatusCard(
    success: Boolean,
    message: String,
    matchCount: Int
) {
    // 根据状态选择颜色和图标
    val backgroundColor = if (success) {
        Color(0xFF4CAF50).copy(alpha = 0.15f)  // 绿色背景
    } else {
        Color(0xFFF44336).copy(alpha = 0.15f)  // 红色背景
    }
    val iconColor = if (success) Color(0xFF4CAF50) else Color(0xFFF44336)
    val icon: ImageVector = if (success) Icons.Default.Check else Icons.Default.Error
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            // 状态消息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = iconColor,
                    fontWeight = FontWeight.Medium
                )
                // 成功时显示匹配数量
                if (success && matchCount > 0) {
                    Text(
                        text = stringResource(R.string.match_count_format, matchCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = iconColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * 匹配详情卡片
 * 
 * 显示匹配次数和每个匹配的位置索引
 * 
 * @param matchInfo 匹配详情文本
 * @param containerColor 容器背景色
 * @param onCopy 复制按钮点击回调
 */
@Composable
private fun MatchInfoCard(
    matchInfo: String,
    onCopy: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行：包含复制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "匹配详情",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 匹配详情文本
            Text(
                text = matchInfo,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 高亮显示卡片
 * 
 * 显示带高亮标记的匹配文本，匹配部分使用黄色背景
 * 
 * @param highlightedText 带高亮样式的文本
 * @param containerColor 容器背景色
 */
@Composable
private fun HighlightCard(
    highlightedText: AnnotatedString
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.debug_highlight),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // SelectionContainer 使文本可选择
            SelectionContainer {
                Text(
                    text = highlightedText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * 替换预览卡片
 * 
 * 显示替换后的文本结果
 * 
 * @param replacedText 替换后的文本
 * @param containerColor 容器背景色
 * @param onCopy 复制按钮点击回调
 */
@Composable
private fun ReplacePreviewCard(
    replacedText: String,
    onCopy: () -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行：包含复制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.replace_preview),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 替换后的文本（可选择）
            SelectionContainer {
                Text(
                    text = replacedText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
