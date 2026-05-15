package io.legado.app.model

import android.content.Context
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ConcurrentException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.CacheBookService
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import io.legado.app.utils.startService
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

object CacheBook {

    val cacheBookMap = ConcurrentHashMap<String, CacheBookModel>()

    private val workingState = MutableStateFlow(true)
    private val mutex = Mutex()

    private val downloadingBySource = ConcurrentHashMap<String, String>()

    /**
     * 根据书籍URL获取或创建缓存模型
     * @param bookUrl 书籍URL
     * @return 缓存书籍模型，如果书籍或书源不存在则返回null
     */
    @Synchronized
    fun getOrCreate(bookUrl: String): CacheBookModel? {
        val book = appDb.bookDao.getBook(bookUrl) ?: return null
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin) ?: return null
        updateBookSource(bookSource)
        var cacheBook = cacheBookMap[bookUrl]
        if (cacheBook != null) {
            //存在时更新,书源可能会变化,必须更新
            cacheBook.bookSource = bookSource
            cacheBook.book = book
            return cacheBook
        }
        cacheBook = CacheBookModel(bookSource, book)
        cacheBookMap[bookUrl] = cacheBook
        return cacheBook
    }

    /**
     * 根据书源和书籍获取或创建缓存模型
     * @param bookSource 书源
     * @param book 书籍
     * @return 缓存书籍模型
     */
    @Synchronized
    fun getOrCreate(bookSource: BookSource, book: Book): CacheBookModel {
        updateBookSource(bookSource)
        var cacheBook = cacheBookMap[book.bookUrl]
        if (cacheBook != null) {
            //存在时更新,书源可能会变化,必须更新
            cacheBook.bookSource = bookSource
            cacheBook.book = book
            return cacheBook
        }
        cacheBook = CacheBookModel(bookSource, book)
        cacheBookMap[book.bookUrl] = cacheBook
        return cacheBook
    }

    /**
     * 更新书源
     * @param newBookSource 新的书源
     */
    private fun updateBookSource(newBookSource: BookSource) {
        cacheBookMap.forEach {
            val model = it.value
            if (model.bookSource.bookSourceUrl == newBookSource.bookSourceUrl) {
                model.bookSource = newBookSource
            }
        }
    }

    /**
     * 启动缓存书籍服务
     * @param context 上下文
     * @param book 书籍
     * @param start 起始章节索引
     * @param end 结束章节索引
     */
    fun start(context: Context, book: Book, start: Int, end: Int) {
        if (!book.isLocal) {
            context.startService<CacheBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putExtra("start", start)
                putExtra("end", end)
            }
        }
    }

    /**
     * 移除缓存书籍
     * @param context 上下文
     * @param bookUrl 书籍URL
     */
    fun remove(context: Context, bookUrl: String) {
        context.startService<CacheBookService> {
            action = IntentAction.remove
            putExtra("bookUrl", bookUrl)
        }
    }

    /**
     * 停止缓存书籍服务
     * @param context 上下文
     */
    fun stop(context: Context) {
        if (CacheBookService.isRun) {
            context.startService<CacheBookService> {
                action = IntentAction.stop
            }
        }
    }

    /**
     * 关闭所有缓存任务并清理资源
     */
    fun close() {
        cacheBookMap.forEach { it.value.stop() }
        cacheBookMap.clear()
        downloadingBySource.clear()
        successDownloadSet.clear()
        errorDownloadMap.clear()
    }

    /**
     * 设置工作状态
     * @param value 工作状态值
     */
    fun setWorkingState(value: Boolean) {
        workingState.value = value
    }

    /**
     * 启动处理任务
     * @param context 协程上下文
     */
    suspend fun startProcessJob(context: CoroutineContext) = mutex.withLock {
        setWorkingState(true)
        flow {
            while (currentCoroutineContext().isActive && cacheBookMap.isNotEmpty()) {
                var emitted = false

                cacheBookMap.forEach { (_, model) ->
                    if (!model.isLoading()) {
                        val sourceUrl = model.bookSource.bookSourceUrl
                        val currentDownloading = downloadingBySource[sourceUrl]
                        if (currentDownloading == null || currentDownloading == model.book.bookUrl) {
                            downloadingBySource[sourceUrl] = model.book.bookUrl
                            emit(model)
                            emitted = true
                        }
                    }
                    workingState.first { it }
                }

                if (!emitted) {
                    delay(1000)
                }
            }
        }.onStart {
            postEvent(EventBus.UP_DOWNLOAD_STATE, "")
        }.onEachParallel(AppConfig.threadCount) {
            coroutineScope {
                it.download(this, context)
            }
        }.onCompletion {
            postEvent(EventBus.UP_DOWNLOAD_STATE, "")
        }.collect()
    }


    val downloadSummary: String
        get() {
            return "正在下载:${onDownloadCount}|等待中:${waitCount}|失败:${errorDownloadMap.count()}|成功:${successDownloadSet.size}"
        }

    val isRun: Boolean
        get() {
            cacheBookMap.forEach {
                if (it.value.isRun()) {
                    return true
                }
            }
            return false
        }

    private val waitCount: Int
        get() {
            var count = 0
            cacheBookMap.forEach {
                count += it.value.waitCount
            }
            return count
        }

    val onDownloadCount: Int
        get() {
            var count = 0
            cacheBookMap.forEach {
                count += it.value.onDownloadCount
            }
            return count
        }

    val successDownloadSet = linkedSetOf<String>()
    val errorDownloadMap = hashMapOf<String, Int>()

    class CacheBookModel(var bookSource: BookSource, var book: Book) {

        private val waitDownloadSet = linkedSetOf<Int>()
        private val onDownloadSet = linkedSetOf<Int>()
        private val tasks = CompositeCoroutine()
        private var isStopped = false
        private var waitingRetry = false
        private var isLoading = false

        val waitCount get() = waitDownloadSet.size
        val onDownloadCount get() = onDownloadSet.size

        init {
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        /**
         * 判断是否正在运行
         * @return 是否正在运行
         */
        @Synchronized
        fun isRun(): Boolean {
            return waitDownloadSet.isNotEmpty() || onDownloadSet.isNotEmpty() || isLoading
        }

        /**
         * 判断是否已停止
         * @return 是否已停止
         */
        @Synchronized
        fun isStop(): Boolean {
            return isStopped || (!isRun() && !waitingRetry)
        }

        /**
         * 判断是否正在加载
         * @return 是否正在加载
         */
        @Synchronized
        fun isLoading(): Boolean {
            return isLoading
        }

        /**
         * 设置加载状态为true
         */
        @Synchronized
        fun setLoading() {
            isLoading = true
        }

        /**
         * 停止下载任务
         */
        @Synchronized
        fun stop() {
            waitDownloadSet.clear()
            tasks.clear()
            isStopped = true
            isLoading = false
            downloadingBySource.remove(bookSource.bookSourceUrl)
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        /**
         * 添加下载任务
         * @param start 起始章节索引
         * @param end 结束章节索引
         */
        @Synchronized
        fun addDownload(start: Int, end: Int) {
            isStopped = false
            for (i in start..end) {
                if (!onDownloadSet.contains(i)) {
                    waitDownloadSet.add(i)
                }
            }
            cacheBookMap[book.bookUrl] = this
            isLoading = false
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        /**
         * 下载成功回调
         * @param chapter 书籍章节
         */
        @Synchronized
        private fun onSuccess(chapter: BookChapter) {
            onDownloadSet.remove(chapter.index)
            successDownloadSet.add(chapter.primaryStr())
            errorDownloadMap.remove(chapter.primaryStr())
            AppLog.put("✅《${book.name}》章节《${chapter.title}》缓存成功")
        }

        /**
         * 下载错误预处理
         * @param chapter 书籍章节
         * @param error 错误信息
         */
        @Synchronized
        private fun onPreError(chapter: BookChapter, error: Throwable) {
            waitingRetry = true
            if (error !is ConcurrentException) {
                errorDownloadMap[chapter.primaryStr()] =
                    (errorDownloadMap[chapter.primaryStr()] ?: 0) + 1
            }
            onDownloadSet.remove(chapter.index)
        }

        /**
         * 下载错误后处理
         * @param chapter 书籍章节
         * @param error 错误信息
         */
        @Synchronized
        private fun onPostError(chapter: BookChapter, error: Throwable) {
            //重试3次
            if ((errorDownloadMap[chapter.primaryStr()] ?: 0) < 3 && !isStopped) {
                waitDownloadSet.add(chapter.index)
                AppLog.put("《${book.name}》章节《${chapter.title}》缓存失败，准备重试\n${error.localizedMessage}")
            } else {
                AppLog.put(
                    "❌《${book.name}》章节《${chapter.title}》缓存失败\n${error.localizedMessage}",
                    error
                )
            }
            waitingRetry = false
        }

        /**
         * 下载错误回调
         * @param chapter 书籍章节
         * @param error 错误信息
         */
        @Synchronized
        private fun onError(chapter: BookChapter, error: Throwable) {
            onPreError(chapter, error)
            onPostError(chapter, error)
        }

        /**
         * 取消下载回调
         * @param index 章节索引
         */
        @Synchronized
        private fun onCancel(index: Int) {
            onDownloadSet.remove(index)
            if (!isStopped) waitDownloadSet.add(index)
        }

        /**
         * 最终处理回调
         */
        @Synchronized
        private fun onFinally() {
            if (waitDownloadSet.isEmpty() && onDownloadSet.isEmpty()) {
                cacheBookMap.remove(book.bookUrl)
                downloadingBySource.remove(bookSource.bookSourceUrl)
            }
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        /**
         * 从待下载列表内取第一条下载
         */
        @Synchronized
        fun download(scope: CoroutineScope, context: CoroutineContext) {
            val chapterIndex = waitDownloadSet.firstOrNull()
            if (chapterIndex == null) {
                if (!isLoading && onDownloadSet.isEmpty()) {
                    cacheBookMap.remove(book.bookUrl)
                    downloadingBySource.remove(bookSource.bookSourceUrl)
                }
                return
            }
            if (onDownloadSet.contains(chapterIndex)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: let {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (chapter.isVolume) {
                /** 修正下载计数 */
                postEvent(EventBus.SAVE_CONTENT, Pair(book, chapter))
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (bookSource.nextPageLazyLoad) {
                AppLog.put("书源「${bookSource.bookSourceName}」已开启下一页懒加载，只允许在线阅读\n源URL: ${bookSource.bookSourceUrl}\n书名: ${book.name}")
                appCtx.toastOnUi("该书源已开启下一页懒加载，只允许在线阅读")
                stop()
                return
            }
            if (BookHelp.hasImageContent(book, chapter)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            waitDownloadSet.remove(chapterIndex)
            onDownloadSet.add(chapterIndex)
            if (BookHelp.hasContent(book, chapter)) {
                Coroutine.async(scope, context, executeContext = context) {
                    BookHelp.getContent(book, chapter)?.let {
                        BookHelp.saveImages(bookSource, book, chapter, it, 1)
                    }
                }.onSuccess {
                    onSuccess(chapter)
                }.onError {
                    onPreError(chapter, it)
                    //出现错误等待一秒后重新加入待下载列表
                    delay(1000)
                    onPostError(chapter, it)
                }.onCancel {
                    onCancel(chapterIndex)
                }.onFinally {
                    onFinally()
                }.let {
                    tasks.add(it)
                }
                return
            }
            WebBook.getContent(
                scope,
                bookSource,
                book,
                chapter,
                context = context,
                start = CoroutineStart.LAZY,
                executeContext = context
            ).onSuccess { content ->
                onSuccess(chapter)
                downloadFinish(chapter, content)
            }.onError {
                onPreError(chapter, it)
                //出现错误等待一秒后重新加入待下载列表
                delay(1000)
                onPostError(chapter, it)
                downloadFinish(chapter, "获取正文失败\n${it.localizedMessage}")
            }.onCancel {
                onCancel(chapterIndex)
            }.onFinally {
                onFinally()
            }.apply {
                tasks.add(this)
            }.start()
        }

        /**
         * 下载章节内容
         * @param scope 协程作用域
         * @param chapter 书籍章节
         * @return 章节内容
         */
        suspend fun downloadAwait(chapter: BookChapter): String {
            synchronized(this) {
                onDownloadSet.add(chapter.index)
                waitDownloadSet.remove(chapter.index)
            }
            try {
                val content = WebBook.getContentAwait(bookSource, book, chapter)
                onSuccess(chapter)
                ReadBook.downloadedChapters.add(chapter.index)
                ReadBook.downloadFailChapters.remove(chapter.index)
                return content
            } catch (e: Exception) {
                if (e is CancellationException) {
                    onCancel(chapter.index)
                }
                onError(chapter, e)
                ReadBook.downloadFailChapters[chapter.index] =
                    (ReadBook.downloadFailChapters[chapter.index] ?: 0) + 1
                return "获取正文失败\n${e.localizedMessage}"
            } finally {
                postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
            }
        }

        /**
         * 下载章节内容
         * @param scope 协程作用域
         * @param chapter 书籍章节
         * @param semaphore 信号量
         * @param resetPageOffset 是否重置页面偏移
         */
        @Synchronized
        fun download(
            scope: CoroutineScope,
            chapter: BookChapter,
            semaphore: Semaphore?,
            resetPageOffset: Boolean = false
        ) {
            if (onDownloadSet.contains(chapter.index)) {
                return
            }
            onDownloadSet.add(chapter.index)
            waitDownloadSet.remove(chapter.index)
            WebBook.getContent(
                scope,
                bookSource,
                book,
                chapter,
                start = CoroutineStart.LAZY,
                executeContext = IO,
                semaphore = semaphore
            ).onSuccess { content ->
                onSuccess(chapter)
                ReadBook.downloadedChapters.add(chapter.index)
                ReadBook.downloadFailChapters.remove(chapter.index)
                downloadFinish(chapter, content, resetPageOffset)
            }.onError {
                onError(chapter, it)
                ReadBook.downloadFailChapters[chapter.index] =
                    (ReadBook.downloadFailChapters[chapter.index] ?: 0) + 1
                downloadFinish(chapter, "获取正文失败\n${it.localizedMessage}", resetPageOffset)
            }.onCancel {
                onCancel(chapter.index)
                downloadFinish(chapter, "download canceled", resetPageOffset, true)
            }.onFinally {
                postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
            }.start()
        }

        /**
         * 下载完成处理
         * @param chapter 书籍章节
         * @param content 章节内容
         * @param resetPageOffset 是否重置页面偏移
         * @param canceled 是否被取消
         */
        private fun downloadFinish(
            chapter: BookChapter,
            content: String,
            resetPageOffset: Boolean = false,
            canceled: Boolean = false
        ) {
            if (ReadBook.book?.bookUrl == book.bookUrl) {
                ReadBook.contentLoadFinish(
                    book, chapter, content,
                    resetPageOffset = resetPageOffset,
                    canceled = canceled
                )
            }
        }

    }

}