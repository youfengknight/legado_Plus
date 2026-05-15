package io.legado.app.model.webBook

import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.repository.debug.FlowLogRecorder
import io.legado.app.exception.ContentEmptyException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.model.Debug
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setNextChapterUrl
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.HtmlFormatter
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.mapAsync
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import org.apache.commons.text.StringEscapeUtils
import splitties.init.appCtx
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.book.isVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext

/**
 * 获取正文
 * 正文解析器
 *
 * 负责解析书籍的正文内容，支持：
 * - 单页/多页正文解析
 * - 多页正文串行/并发获取
 * - 副内容（歌词/弹幕）提取
 * - 全文替换规则
 * - 章节标题规则
 * - HTML格式化与净化
 * - 封面图片提取
 *
 * @see WebBook.getContent 网络请求入口
 * @see ContentRule 正文规则定义
 */
object BookContent {

    suspend fun analyzeContentLazy(
        scope: CoroutineScope,
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        nextChapterUrl: String?,
        callback: LazyContentCallback? = null
    ): Pair<String, LazyContentManager?> {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 40)
        
        val mNextChapterUrl = if (nextChapterUrl.isNullOrEmpty()) {
            appDb.bookChapterDao.getChapter(book.bookUrl, bookChapter.index + 1)?.url
                ?: appDb.bookChapterDao.getChapter(book.bookUrl, 0)?.url
        } else {
            nextChapterUrl
        }
        
        val contentRule = bookSource.getContentRule()
        val nextContentUrlRule = contentRule.nextContentUrl ?: ""
        
        AppLog.put("懒加载: analyzeContentLazy nextContentUrlRule=$nextContentUrlRule")
        
        if (nextContentUrlRule.isBlank()) {
            AppLog.put("懒加载: nextContentUrlRule 为空，走传统模式")
            val contentData = analyzeContent(
                book, baseUrl, redirectUrl, body, contentRule, bookChapter, bookSource, mNextChapterUrl
            )
            return Pair(contentData.first, null)
        }
        
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body, baseUrl)
        val rUrl = analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setChapter(bookChapter)
        analyzeRule.setNextChapterUrl(mNextChapterUrl)
        
        var content = analyzeRule.getString(contentRule.content, unescape = false)
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
        
        val nextUrl = analyzeRule.getStringList(nextContentUrlRule, isUrl = true)?.firstOrNull()
        
        Debug.log(bookSource.bookSourceUrl, "┌获取正文下一页链接")
        Debug.log(bookSource.bookSourceUrl, "└下一页懒加载已开启，将在阅读时按需加载")
        
        val lazyManager = LazyContentManager(
            scope = scope,
            bookSource = bookSource,
            book = book,
            bookChapter = bookChapter,
            baseUrl = baseUrl,
            redirectUrl = redirectUrl,
            initialBody = body,
            nextChapterUrl = mNextChapterUrl,
            nextContentUrlRule = nextContentUrlRule,
            contentRule = contentRule.content ?: "",
            webJs = contentRule.webJs,
            callback = callback
        )
        
        lazyManager.pages[0] = PageContent(content, nextUrl)
        
        AppLog.put("懒加载: 创建 LazyContentManager 成功，第一页内容长度=${content.length}, nextUrl=$nextUrl")
        
        return Pair(content, lazyManager)
    }

    /**
     * 解析章节正文
     *
     * @param bookSource 书源
     * @param book 书籍对象
     * @param bookChapter 章节对象
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param nextChapterUrl 下一章URL（用于多页正文判断终止）
     * @param needSave 是否保存到本地缓存
     * @return 正文内容字符串
     * @throws NoStackTraceException 当内容为空时抛出
     * @throws ContentEmptyException 当正文为空时抛出
     */
    @Throws(Exception::class)
    suspend fun analyzeContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        baseUrl: String,
        redirectUrl: String,
        body: String?,
        nextChapterUrl: String?,
        needSave: Boolean = true
    ): String {
        body ?: throw NoStackTraceException(
            appCtx.getString(R.string.error_get_web_content, baseUrl)
        )
        Debug.log(bookSource.bookSourceUrl, "≡获取成功:${baseUrl}")
        Debug.log(bookSource.bookSourceUrl, body, state = 40)
        val mNextChapterUrl = if (nextChapterUrl.isNullOrEmpty()) {
            appDb.bookChapterDao.getChapter(book.bookUrl, bookChapter.index + 1)?.url
                ?: appDb.bookChapterDao.getChapter(book.bookUrl, 0)?.url
        } else {
            nextChapterUrl
        }
        val contentList = arrayListOf<String>()
        val nextUrlList = arrayListOf(redirectUrl)
        val contentRule = bookSource.getContentRule()
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body, baseUrl)
        analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setChapter(bookChapter)
        analyzeRule.setNextChapterUrl(mNextChapterUrl)
        
        FlowLogRecorder.logExtract(
            source = bookSource,
            message = "开始提取正文内容",
            rule = contentRule.content
        )
        
        currentCoroutineContext().ensureActive()
        var contentData = analyzeContent(
            book, baseUrl, redirectUrl, body, contentRule, bookChapter, bookSource, mNextChapterUrl
        )
        
        FlowLogRecorder.logExtract(
            source = bookSource,
            message = "正文内容提取完成",
            rule = contentRule.content,
            result = contentData.first.take(100),
            originalValue = body?.take(100)
        )
        
        contentList.add(contentData.first)
        if (contentData.second.size == 1) {
            val webJs = contentRule.webJs
            var nextUrl = contentData.second[0]
            while (nextUrl.isNotEmpty() && !nextUrlList.contains(nextUrl)) {
                if (!mNextChapterUrl.isNullOrEmpty()
                    && NetworkUtils.getAbsoluteURL(redirectUrl, nextUrl)
                    == NetworkUtils.getAbsoluteURL(redirectUrl, mNextChapterUrl)
                ) break
                nextUrlList.add(nextUrl)
                currentCoroutineContext().ensureActive()
                val analyzeUrl = AnalyzeUrl(
                    mUrl = nextUrl,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = currentCoroutineContext()
                )
                val res = analyzeUrl.getStrResponseAwait(jsStr = webJs) //控制并发访问
                res.body?.let { nextBody ->
                    contentData = analyzeContent(
                        book, nextUrl, res.url, nextBody, contentRule,
                        bookChapter, bookSource, mNextChapterUrl,
                        printLog = false
                    )
                    nextUrl =
                        if (contentData.second.isNotEmpty()) contentData.second[0] else ""
                    contentList.add(contentData.first)
                    Debug.log(bookSource.bookSourceUrl, "第${contentList.size}页完成")
                }
            }
            Debug.log(bookSource.bookSourceUrl, "◇本章总页数:${nextUrlList.size}")
        } else if (contentData.second.size > 1) {
            Debug.log(bookSource.bookSourceUrl, "◇并发解析正文,总页数:${contentData.second.size}")
            flow {
                for (urlStr in contentData.second) {
                    emit(urlStr)
                }
            }.mapAsync(AppConfig.threadCount) { urlStr ->
                val analyzeUrl = AnalyzeUrl(
                    mUrl = urlStr,
                    source = bookSource,
                    ruleData = book,
                    coroutineContext = currentCoroutineContext()
                )
                val res = analyzeUrl.getStrResponseAwait() //控制并发访问
                analyzeContent(
                    book, urlStr, res.url, res.body!!, contentRule,
                    bookChapter, bookSource, mNextChapterUrl,
                    getNextPageUrl = false,
                    printLog = false
                ).first
            }.collect {
                currentCoroutineContext().ensureActive()
                contentList.add(it)
            }
        }
        val subContentRule = contentRule.subContent
        if (!subContentRule.isNullOrBlank()) { //副内容
            analyzeRule.getString(subContentRule).let { rawContent ->
                runCatching {
                    if (book.isOnLineTxt) {
                        contentList.add(rawContent)
                        return@let
                    }
                    val subContent = rawContent.trim().let {
                        if (it.startsWith("http", true)) {
                            AnalyzeUrl(
                                mUrl = it,
                                source = bookSource,
                                ruleData = book,
                                coroutineContext = currentCoroutineContext()
                            ).getStrResponseAwait().body
                        } else {
                            it
                        }
                    }
                    when {
                        book.isAudio -> {
                            bookChapter.putLyric(subContent)
                            Debug.log(bookSource.bookSourceUrl, "┌获取副文歌词")
                            Debug.log(bookSource.bookSourceUrl, "└\n$subContent")
                        }

                        book.isVideo -> {
                            bookChapter.putDanmaku(subContent)
                            Debug.log(bookSource.bookSourceUrl, "┌获取副文弹幕")
                            Debug.log(bookSource.bookSourceUrl, "└\n$subContent")
                        }
                    }
                }.onFailure { e ->
                    Debug.log(bookSource.bookSourceUrl, "获取副文出错, ${e.localizedMessage}")
                }
            }
        }
        var contentStr = contentList.joinToString("\n")
        //全文替换
        val replaceRegex = contentRule.replaceRegex
        if (!replaceRegex.isNullOrEmpty()) {
            val originalContent = contentStr.take(100)  // 保存原始数据
            FlowLogRecorder.logReplace(
                source = bookSource,
                message = "开始正文全文替换",
                rule = replaceRegex
            )
            contentStr = contentStr.split(AppPattern.LFRegex).joinToString("\n") { it.trim() }
            contentStr = analyzeRule.getString(replaceRegex, contentStr)
            if (book.isOnLineTxt) {
                contentStr = contentStr.split(AppPattern.LFRegex).joinToString("\n") { "　　$it" }
            }
            FlowLogRecorder.logReplace(
                source = bookSource,
                message = "正文全文替换完成",
                rule = replaceRegex,
                result = contentStr.take(100),
                originalValue = originalContent
            )
        }
        val titleRule = contentRule.title //先正文再章节名称
        if (!titleRule.isNullOrBlank()) {
            var title = analyzeRule.runCatching {
                getString(titleRule)
            }.onFailure {
                Debug.log(bookSource.bookSourceUrl, "获取标题出错, ${it.localizedMessage}")
            }.getOrNull()
            if (!title.isNullOrBlank()) {
                val matchResult = AppPattern.imgRegex.find(title)
                if (matchResult != null) {
                    matchResult.groupValues[1]
                    val (group1,group2) = matchResult.destructured
                    title = if (group1 != "") {
                        group1
                    } else {
                        bookChapter.title
                    }
                    bookChapter.imgUrl = group2
                }
                bookChapter.title = title
                bookChapter.titleMD5 = null
                bookChapter.update()
            }
        }
        Debug.log(bookSource.bookSourceUrl, "┌获取章节名称")
        Debug.log(bookSource.bookSourceUrl, "└${bookChapter.title}")
        Debug.log(bookSource.bookSourceUrl, "┌获取正文内容")
        Debug.log(bookSource.bookSourceUrl, "└\n$contentStr")
        if (!bookChapter.isVolume && contentStr.isBlank()) {
            throw ContentEmptyException("内容为空")
        }
        if (needSave) {
            BookHelp.saveContent(bookSource, book, bookChapter, contentStr)
        }
        return contentStr
    }

    /**
     * 解析单页正文内容
     *
     * @param book 书籍对象
     * @param baseUrl 基础URL
     * @param redirectUrl 重定向后的URL
     * @param body 页面内容
     * @param contentRule 正文规则
     * @param chapter 章节对象
     * @param bookSource 书源
     * @param nextChapterUrl 下一章URL
     * @param getNextPageUrl 是否获取下一页URL
     * @param printLog 是否输出调试日志
     * @return 正文内容和下一页URL列表的Pair
     */
    @Throws(Exception::class)
    private suspend fun analyzeContent(
        book: Book,
        baseUrl: String,
        redirectUrl: String,
        body: String,
        contentRule: ContentRule,
        chapter: BookChapter,
        bookSource: BookSource,
        nextChapterUrl: String?,
        getNextPageUrl: Boolean = true,
        printLog: Boolean = true
    ): Pair<String, List<String>> {
        val analyzeRule = AnalyzeRule(book, bookSource)
        analyzeRule.setContent(body, baseUrl)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        val rUrl = analyzeRule.setRedirectUrl(redirectUrl)
        analyzeRule.setNextChapterUrl(nextChapterUrl)
        val nextUrlList = arrayListOf<String>()
        analyzeRule.setChapter(chapter)
        //获取正文
        var content = analyzeRule.getString(contentRule.content, unescape = false)
        if (!book.isAudio && !book.isVideo) { //音频和视频获取的是链接，不需要html格式化
            val useHtmlMap = mutableMapOf<String, String>()
            if (AppConfig.adaptSpecialStyle) {
                content = AppPattern.useHtmlRegex.replace(content) { matchResult ->
                    val placeholder = "{usehtml_${useHtmlMap.size}}"
                    useHtmlMap[placeholder] = matchResult.value
                    placeholder
                }
            }
            content = HtmlFormatter.formatKeepImg(content, rUrl) //内置净化格式化
            if (content.indexOf('&') > -1) {
                content = StringEscapeUtils.unescapeHtml4(content)
            }
            useHtmlMap.forEach { (placeholder, originalContent) ->
                content = content.replace(placeholder, originalContent)
            }
        }
        //获取下一页链接
        if (getNextPageUrl) {
            if (bookSource.nextPageLazyLoad) {
                Debug.log(bookSource.bookSourceUrl, "┌获取正文下一页链接", printLog)
                Debug.log(bookSource.bookSourceUrl, "└下一页懒加载已开启，请在阅读界面查看效果", printLog)
            } else {
                val nextUrlRule = contentRule.nextContentUrl
                if (!nextUrlRule.isNullOrEmpty()) {
                    Debug.log(bookSource.bookSourceUrl, "┌获取正文下一页链接", printLog)
                    analyzeRule.getStringList(nextUrlRule, isUrl = true)?.let {
                        nextUrlList.addAll(it)
                    }
                    Debug.log(bookSource.bookSourceUrl, "└" + nextUrlList.joinToString("，"), printLog)
                }
            }
        }
        return Pair(content, nextUrlList)
    }
}
