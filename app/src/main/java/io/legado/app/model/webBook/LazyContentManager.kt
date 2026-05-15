package io.legado.app.model.webBook

import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isVideo
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.HtmlFormatter
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.text.StringEscapeUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class PageContent(
    val content: String,
    val nextUrl: String?
)

interface LazyContentCallback {
    fun onPageLoading(pageIndex: Int) {}
    fun onPageLoaded(pageIndex: Int, content: String)
}

class LazyContentManager(
    private val scope: CoroutineScope,
    private val bookSource: BookSource,
    private val book: Book,
    private val bookChapter: BookChapter,
    private val baseUrl: String,
    private val redirectUrl: String,
    private val initialBody: String,
    private val nextChapterUrl: String?,
    private val nextContentUrlRule: String,
    private val contentRule: String,
    private val webJs: String?,
    private val callback: LazyContentCallback? = null
) {
    val pages = ConcurrentHashMap<Int, PageContent>()
    private val loadingPages = ConcurrentHashMap<Int, AtomicBoolean>()
    
    val totalPages: AtomicInteger = AtomicInteger(-1)
    
    private var prefetchJob: Job? = null
    
    val contentChannel = Channel<PageContent>(Channel.UNLIMITED)
    
    val isCompleted: AtomicBoolean = AtomicBoolean(false)
    
    private val lock = Any()
    
    fun getPage(index: Int): PageContent? {
        return pages[index]
    }
    
    fun isPageLoaded(index: Int): Boolean {
        return pages.containsKey(index)
    }
    
    fun isPageLoading(index: Int): Boolean {
        return loadingPages[index]?.get() == true
    }
    
    fun getAllLoadedContent(): String {
        val sortedPages = pages.keys.sorted()
        return sortedPages.mapNotNull { pages[it]?.content }.joinToString("\n")
    }
    
    fun getNextPageToLoad(): Int {
        synchronized(lock) {
            if (isCompleted.get()) return -1
            val maxLoadedIndex = if (pages.isEmpty()) -1 else pages.keys.max()
            val nextIdx = maxLoadedIndex + 1
            if (isPageLoaded(nextIdx) || isPageLoading(nextIdx)) return -1
            return nextIdx
        }
    }
    
    suspend fun loadInitialPage(): PageContent {
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(initialBody, baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        
        val content = analyzeRule.getString(contentRule, unescape = false)
        val nextUrl = if (nextContentUrlRule.isNotBlank()) {
            analyzeRule.getStringList(nextContentUrlRule, isUrl = true)?.firstOrNull()
        } else null
        
        val pageContent = PageContent(content, nextUrl)
        pages[0] = pageContent
        contentChannel.trySend(pageContent)
        
        return pageContent
    }
    
    fun prefetchNextPage() {
        val nextIdx: Int
        synchronized(lock) {
            nextIdx = getNextPageToLoad()
            if (nextIdx < 0) return
            loadingPages[nextIdx] = AtomicBoolean(true)
        }
        
        AppLog.put("懒加载: 开始预加载第${nextIdx + 1}页")
        callback?.onPageLoading(nextIdx)
        
        prefetchJob?.cancel()
        prefetchJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val currentIdx = nextIdx - 1
                val currentPage = pages[currentIdx] ?: return@launch
                val nextUrl = currentPage.nextUrl
                
                if (nextUrl.isNullOrBlank()) {
                    AppLog.put("懒加载: 无下一页URL，标记完成")
                    isCompleted.set(true)
                    totalPages.set(currentIdx + 1)
                    return@launch
                }
                
                if (!nextChapterUrl.isNullOrEmpty() &&
                    NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl) ==
                    NetworkUtils.getAbsoluteURL(redirectUrl, nextChapterUrl)
                ) {
                    AppLog.put("懒加载: 下一页URL等于下一章URL，标记完成")
                    isCompleted.set(true)
                    totalPages.set(currentIdx + 1)
                    return@launch
                }
                
                ensureActive()
                
                AppLog.put("懒加载: 请求第${nextIdx + 1}页 URL: $nextUrl")
                val analyzeUrl = AnalyzeUrl(
                    mUrl = nextUrl,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = kotlin.coroutines.coroutineContext
                )
                val res = analyzeUrl.getStrResponseAwait(jsStr = webJs)
                
                res.body?.let { body ->
                    val analyzeRule = AnalyzeRule(book, bookSource)
                    analyzeRule.setContent(body, nextUrl)
                    val rUrl = analyzeRule.setRedirectUrl(res.url)
                    
                    var content = analyzeRule.getString(contentRule, unescape = false)
                    
                    if (!book.isAudio && !book.isVideo) {
                        val useHtmlMap = mutableMapOf<String, String>()
                        if (AppConfig.adaptSpecialStyle) {
                            content = AppPattern.useHtmlRegex.replace(content) { matchResult ->
                                val placeholder = "{usehtml_${useHtmlMap.size}}"
                                useHtmlMap[placeholder] = matchResult.value
                                placeholder
                            }
                        }
                        content = HtmlFormatter.formatKeepImg(content, rUrl)
                        if (content.indexOf('&') > -1) {
                            content = StringEscapeUtils.unescapeHtml4(content)
                        }
                        useHtmlMap.forEach { (placeholder, originalContent) ->
                            content = content.replace(placeholder, originalContent)
                        }
                    }
                    
                    val nextNextUrl = if (nextContentUrlRule.isNotBlank()) {
                        analyzeRule.getStringList(nextContentUrlRule, isUrl = true)?.firstOrNull()
                    } else null
                    
                    val pageContent = PageContent(content, nextNextUrl)
                    pages[nextIdx] = pageContent
                    contentChannel.trySend(pageContent)
                    
                    AppLog.put("懒加载: 第${nextIdx + 1}页加载成功，内容长度=${content.length}")
                    
                    callback?.onPageLoaded(nextIdx, content)
                }
            } catch (e: Exception) {
                AppLog.put("懒加载: 预加载失败: ${e.localizedMessage}", e)
            } finally {
                loadingPages[nextIdx]?.set(false)
            }
        }
    }
    
    suspend fun loadPage(index: Int): PageContent? {
        if (index < 0) return null
        
        pages[index]?.let { return it }
        
        while (isPageLoading(index)) {
            kotlinx.coroutines.delay(50)
        }
        
        pages[index]?.let { return it }
        
        if (index == 0) {
            return loadInitialPage()
        }
        
        loadingPages[index] = AtomicBoolean(true)
        
        return try {
            var prevPage = pages[index - 1]
            if (prevPage == null) {
                prevPage = loadPage(index - 1) ?: return null
            }
            
            val nextUrl = prevPage.nextUrl
            if (nextUrl.isNullOrBlank()) {
                isCompleted.set(true)
                totalPages.set(index)
                return null
            }
            
            if (!nextChapterUrl.isNullOrEmpty() &&
                NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl) ==
                NetworkUtils.getAbsoluteURL(redirectUrl, nextChapterUrl)
            ) {
                isCompleted.set(true)
                totalPages.set(index)
                return null
            }
            
            val analyzeUrl = AnalyzeUrl(
                mUrl = nextUrl,
                source = bookSource,
                ruleData = book,
                coroutineContext = kotlin.coroutines.coroutineContext
            )
            val res = analyzeUrl.getStrResponseAwait(jsStr = webJs)
            
            res.body?.let { body ->
                val analyzeRule = AnalyzeRule(book, bookSource)
                analyzeRule.setContent(body, nextUrl)
                analyzeRule.setRedirectUrl(res.url)
                
                val content = analyzeRule.getString(contentRule, unescape = false)
                val nextNextUrl = if (nextContentUrlRule.isNotBlank()) {
                    analyzeRule.getStringList(nextContentUrlRule, isUrl = true)?.firstOrNull()
                } else null
                
                val pageContent = PageContent(content, nextNextUrl)
                pages[index] = pageContent
                contentChannel.trySend(pageContent)
                pageContent
            }
        } catch (e: Exception) {
            AppLog.put("加载第${index}页失败: ${e.localizedMessage}", e)
            null
        } finally {
            loadingPages[index]?.set(false)
        }
    }
    
    fun cancel() {
        prefetchJob?.cancel()
        contentChannel.close()
    }
    
    fun hasMorePages(): Boolean {
        if (isCompleted.get()) return false
        val maxLoadedIndex = if (pages.isEmpty()) -1 else pages.keys.max()
        val currentPage = pages[maxLoadedIndex] ?: return false
        return !currentPage.nextUrl.isNullOrBlank()
    }
}
