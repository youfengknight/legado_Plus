package io.legado.app.ui.rss.read

import android.os.SystemClock
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.RssSource

/**
 * 订阅源 WebView 性能追踪器
 * 用于测量各个阶段的耗时，帮助分析性能瓶颈
 */
class RssWebViewPerfTracker(private val source: RssSource) {
    
    private val sourceTag = source.getTag()
    
    var startTime = 0L
        private set
    private var htmlDownloadStart = 0L
    private var htmlDownloadEnd = 0L
    private var htmlParseStart = 0L
    private var htmlParseEnd = 0L
    private var jsInjectStart = 0L
    private var jsInjectEnd = 0L
    private var jsExecStart = 0L
    private var jsExecEnd = 0L
    private var domRenderStart = 0L
    private var domRenderEnd = 0L
    
    private var jsInjectionSize = 0
    
    fun start() {
        startTime = SystemClock.uptimeMillis()
        domRenderStart = startTime
    }
    
    fun htmlDownloadStart() {
        htmlDownloadStart = SystemClock.uptimeMillis()
    }
    
    fun htmlDownloadEnd() {
        htmlDownloadEnd = SystemClock.uptimeMillis()
    }
    
    fun htmlParseStart() {
        htmlParseStart = SystemClock.uptimeMillis()
    }
    
    fun htmlParseEnd() {
        htmlParseEnd = SystemClock.uptimeMillis()
    }
    
    fun jsInjectStart() {
        jsInjectStart = SystemClock.uptimeMillis()
    }
    
    fun jsInjectEnd(injectionSize: Int = 0) {
        jsInjectEnd = SystemClock.uptimeMillis()
        jsInjectionSize = injectionSize
    }
    
    fun jsExecStart() {
        jsExecStart = SystemClock.uptimeMillis()
    }
    
    fun jsExecEnd() {
        jsExecEnd = SystemClock.uptimeMillis()
    }
    
    fun domRenderEnd() {
        domRenderEnd = SystemClock.uptimeMillis()
    }
    
    fun report() {
        if (startTime == 0L) return
        
        val total = domRenderEnd - startTime
        val htmlDownload = if (htmlDownloadEnd > 0 && htmlDownloadStart > 0) htmlDownloadEnd - htmlDownloadStart else 0
        val htmlParse = if (htmlParseEnd > 0 && htmlParseStart > 0) htmlParseEnd - htmlParseStart else 0
        val jsInject = if (jsInjectEnd > 0 && jsInjectStart > 0) jsInjectEnd - jsInjectStart else 0
        val jsExec = if (jsExecEnd > 0 && jsExecStart > 0) jsExecEnd - jsExecStart else 0
        val domRender = if (domRenderEnd > 0 && domRenderStart > 0) domRenderEnd - domRenderStart else total
        
        val sb = StringBuilder()
        sb.appendLine("┌─────────────────────────────────────────")
        sb.appendLine("│ [订阅源性能分析] 总耗时: ${total}ms")
        sb.appendLine("├─────────────────────────────────────────")
        sb.appendLine("│ 阶段耗时分解:")
        
        // 1. HTML下载
        if (htmlDownloadStart > 0) {
            sb.appendLine("│ ├─ 1. HTML下载: ${htmlDownload}ms")
        } else {
            sb.appendLine("│ ├─ 1. HTML下载: -- (未触发)")
        }
        
        // 2. HTML解析
        if (htmlParseStart > 0) {
            sb.appendLine("│ ├─ 2. HTML解析: ${htmlParse}ms")
        } else {
            sb.appendLine("│ ├─ 2. HTML解析: -- (未触发)")
        }
        
        // 3. JS注入拦截
        if (jsInjectStart > 0) {
            sb.appendLine("│ ├─ 3. JS注入拦截: ${jsInject}ms (代码量: ${jsInjectionSize}字节)")
        } else {
            sb.appendLine("│ ├─ 3. JS注入拦截: -- (无preloadJs)")
        }
        
        // 4. JS解析执行
        if (jsExecStart > 0) {
            sb.appendLine("│ ├─ 4. JS解析执行: ${jsExec}ms")
        } else {
            sb.appendLine("│ ├─ 4. JS解析执行: -- (未触发)")
        }
        
        // 5. DOM渲染
        sb.appendLine("│ └─ 5. DOM渲染: ${domRender}ms")
        
        sb.appendLine("│")
        sb.appendLine("│ 性能瓶颈分析:")
        
        // 只统计已触发的阶段
        val stages = mutableListOf<Pair<String, Long>>()
        if (htmlDownloadStart > 0 && htmlDownload > 0) stages.add("HTML下载" to htmlDownload)
        if (htmlParseStart > 0 && htmlParse > 0) stages.add("HTML解析" to htmlParse)
        if (jsInjectStart > 0 && jsInject > 0) stages.add("JS注入拦截" to jsInject)
        if (jsExecStart > 0 && jsExec > 0) stages.add("JS解析执行" to jsExec)
        if (domRender > 0) stages.add("DOM渲染" to domRender)
        
        if (stages.isEmpty()) {
            sb.appendLine("│ ℹ️ 无有效性能数据")
        } else {
            val maxStage = stages.maxByOrNull { it.second }
            if (maxStage != null && maxStage.second > 0) {
                sb.appendLine("│ ⚠️ ${maxStage.first}是主要瓶颈 (${maxStage.second}ms)")
            } else {
                sb.appendLine("│ ✅ 各阶段耗时均衡")
            }
        }
        
        if (jsInjectionSize > 5000) {
            sb.appendLine("│ 💡 建议: JS注入代码量较大(${jsInjectionSize}字节)，可考虑优化")
        }
        
        sb.appendLine("└─────────────────────────────────────────")
        
        AppLog.put("$sourceTag: ${sb.toString()}")
    }
    
    fun reset() {
        startTime = 0L
        htmlDownloadStart = 0L
        htmlDownloadEnd = 0L
        htmlParseStart = 0L
        htmlParseEnd = 0L
        jsInjectStart = 0L
        jsInjectEnd = 0L
        jsExecStart = 0L
        jsExecEnd = 0L
        domRenderStart = 0L
        domRenderEnd = 0L
        jsInjectionSize = 0
    }
}
