package io.legado.app.ui.book.read.page.entities

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.text.TextPaint
import androidx.annotation.Keep
import io.legado.app.help.PaintPool
import io.legado.app.help.book.isImage
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextPage.Companion.emptyTextPage
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.canvasrecorder.recordIfNeededThenDraw
import io.legado.app.utils.dpToPx

/**
 * 行信息
 */
@Keep
@Suppress("unused", "MemberVisibilityCanBePrivate")
data class TextLine(
    var text: String = "",
    private val textColumns: ArrayList<BaseColumn> = arrayListOf(),
    var lineTop: Float = 0f,
    var lineBase: Float = 0f,
    var lineBottom: Float = 0f,
    var indentWidth: Float = 0f,
    var paragraphNum: Int = 0,
    var chapterPosition: Int = 0,
    var pagePosition: Int = 0,
    val isTitle: Boolean = false,
    var isParagraphEnd: Boolean = false,
    var isImage: Boolean = false,
    var isHtml: Boolean = false,
    var startX: Float = 0f,
    var indentSize: Int = 0,
    var extraLetterSpacing: Float = 0f,
    var extraLetterSpacingOffsetX: Float = 0f,
    var wordSpacing: Float = 0f,
    var exceed: Boolean = false,
    var onlyTextColumn: Boolean = true,
) {

    val columns: List<BaseColumn> get() = textColumns
    val charSize: Int get() = text.length
    val lineStart: Float get() = textColumns.firstOrNull()?.start ?: 0f
    val lineEnd: Float get() = textColumns.lastOrNull()?.end ?: 0f
    val chapterIndices: IntRange get() = chapterPosition..chapterPosition + charSize
    val height: Float inline get() = lineBottom - lineTop
    val canvasRecorder = CanvasRecorderFactory.create()
    var searchResultColumnCount = 0
    var isReadAloud: Boolean = false
        set(value) {
            if (field != value) {
                invalidate()
            }
            if (value) {
                textPage.hasReadAloudSpan = true
            }
            field = value
        }
    var textPage: TextPage = emptyTextPage
    var isLeftLine = true

    /**
     * 向行中添加文本列
     */
    fun addColumn(column: BaseColumn) {
        if (column !is TextColumn) {
            onlyTextColumn = false
        }
        column.textLine = this
        textColumns.add(column)
    }

    /**
     * 向行中批量添加文本列
     */
    fun addColumns(columns: Collection<BaseColumn>) {
        onlyTextColumn = false
        columns.forEach { column ->
            column.textLine = this
        }
        textColumns.addAll(columns)
    }

    /**
     * 获取指定位置的文本列，越界时返回最后一个
     */
    fun getColumn(index: Int): BaseColumn {
        return textColumns.getOrElse(index) {
            textColumns.last()
        }
    }

    /**
     * 从后向前获取指定位置的文本列
     */
    fun getColumnReverseAt(index: Int, offset: Int = 0): BaseColumn {
        return textColumns[textColumns.lastIndex - offset - index]
    }

    /**
     * 获取行内文本列数量
     */
    fun getColumnsCount(): Int {
        return textColumns.size
    }

    /**
     * 更新行的顶部、底部和基线位置
     */
    fun upTopBottom(durY: Float, textHeight: Float, fontMetrics: Paint.FontMetrics) {
        lineTop = ChapterProvider.paddingTop + durY
        lineBottom = lineTop + textHeight
        lineBase = lineBottom - fontMetrics.descent
    }

    /**
     * 判断触摸坐标是否在当前行范围内
     */
    fun isTouch(x: Float, y: Float, relativeOffset: Float): Boolean {
        return y > lineTop + relativeOffset
                && y < lineBottom + relativeOffset
                && x >= lineStart
                && x <= lineEnd + 20.dpToPx()
    }

    /**
     * 判断触摸Y坐标是否在当前行范围内
     */
    fun isTouchY(y: Float, relativeOffset: Float): Boolean {
        return y > lineTop + relativeOffset
                && y < lineBottom + relativeOffset
    }

    /**
     * 判断行是否在可视区域内
     */
    fun isVisible(relativeOffset: Float): Boolean {
        val top = lineTop + relativeOffset
        val bottom = lineBottom + relativeOffset
        val width = bottom - top
        val visibleTop = ChapterProvider.paddingTop
        val visibleBottom = ChapterProvider.visibleBottom
        val visible = when {
            // 完全可视
            top >= visibleTop && bottom <= visibleBottom -> true
            top <= visibleTop && bottom >= visibleBottom -> true
            // 上方第一行部分可视
            top < visibleTop && bottom > visibleTop && bottom < visibleBottom -> {
                if (isImage) {
                    true
                } else {
                    val visibleRate = (bottom - visibleTop) / width
                    visibleRate > 0.6
                }
            }
            // 下方第一行部分可视
            top > visibleTop && top < visibleBottom && bottom > visibleBottom -> {
                if (isImage) {
                    true
                } else {
                    val visibleRate = (visibleBottom - top) / width
                    visibleRate > 0.6
                }
            }
            // 不可视
            else -> false
        }
        return visible
    }

    /**
     * 绘制整行内容，包含优化渲染和普通渲染两种模式
     */
    fun draw(view: ContentTextView, canvas: Canvas) {
        if (AppConfig.optimizeRender) {
            canvasRecorder.recordIfNeededThenDraw(canvas, view.width, height.toInt()) {
                drawTextLine(view, this)
            }
        } else {
            drawTextLine(view, canvas)
        }
    }

    /**
     * 绘制行内文本和列内容，包含搜索高亮、墨水屏下划线、自定义下划线等
     */
    private fun drawTextLine(view: ContentTextView, canvas: Canvas) {
        drawCurrentSearchResultBackgrounds(canvas)
        if (checkFastDraw()) {
            fastDrawTextLine(view, canvas)
        } else {
            for (i in columns.indices) {
                columns[i].draw(view, canvas)
            }
        }

        // 墨水屏模式下的朗读和搜索下划线
        if (AppConfig.isEInkMode && (isReadAloud || searchResultColumnCount > 0)) {
            val underlinePaint = PaintPool.obtain()
            underlinePaint.set(ChapterProvider.contentPaint)
            underlinePaint.strokeWidth = 1.dpToPx().toFloat()
            val lineY = height - 1.dpToPx()
            canvas.drawLine(lineStart + indentWidth, lineY, lineEnd, lineY, underlinePaint)
            PaintPool.recycle(underlinePaint)
        }

        drawStyledUnderlines(canvas)

        val underlineMode = ReadBookConfig.underlineMode
        if (underlineMode == 0) return
        if (!isImage && !isHtml && ReadBook.book?.isImage != true) {
            drawUnderline(canvas, underlineMode)
        }
    }

    /**
     * 快速绘制纯文本行，适用于优化渲染模式
     */
    @SuppressLint("NewApi")
    private fun fastDrawTextLine(view: ContentTextView, canvas: Canvas) {
        val textPaint = if (isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = if (isReadAloud) {
            ReadBookConfig.textAccentColor
        } else {
            ReadBookConfig.textColor
        }
        if (textPaint.color != textColor) {
            textPaint.color = textColor
        }
        val paint = PaintPool.obtain()
        paint.set(textPaint)
        val letterSpacing = paint.letterSpacing * paint.textSize
        val letterSpacingHalf = letterSpacing * 0.5f
        if (extraLetterSpacing != 0f) {
            paint.letterSpacing += extraLetterSpacing
        }
        if (wordSpacing != 0f) {
            paint.wordSpacing = wordSpacing
        }
        val offsetX = if (atLeastApi35) letterSpacingHalf else extraLetterSpacingOffsetX
        canvas.drawText(text, indentSize, text.length, startX + offsetX, lineBase - lineTop, paint)
        PaintPool.recycle(paint)
        for (i in columns.indices) {
            val column = columns[i] as TextColumn
            if (column.selected && !column.isSearchResult) {
                canvas.drawRect(column.start, 0f, column.end, height, view.selectedPaint)
            }
        }
    }

    /**
     * 绘制全局下划线（朗读标记除外），支持实线/虚线/波浪线/点线
     */
    private fun drawUnderline(canvas: Canvas, underlineMode: Int) {
        val underlineWidth = ReadBookConfig.durConfig.underlineWidth
        val paint = TextPaint(ChapterProvider.contentPaint).apply {
            strokeWidth = underlineWidth.dpToPx().toFloat()
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = true
        }
        val distance = (ChapterProvider.lineSpacingExtra * 10 - 11).coerceIn(-1f, 10f)
        val lineY = height + distance.dpToPx()
        val startX = lineStart + indentWidth
        val endX = lineEnd
        when (underlineMode) {
            1 -> canvas.drawLine(startX, lineY, endX, lineY, paint)
            2 -> drawDashedLine(canvas, paint, startX, lineY, endX, underlineWidth)
            3 -> drawWavyLine(canvas, paint, startX, lineY, endX, underlineWidth)
            4 -> drawDottedLine(canvas, paint, startX, lineY, endX, underlineWidth)
        }
    }

    /**
     * 绘制虚线下划线，每段8dp线段+5dp间隔
     */
    private fun drawDashedLine(canvas: Canvas, paint: Paint, startX: Float, y: Float, endX: Float, underlineWidth: Int) {
        paint.strokeWidth = underlineWidth.dpToPx().toFloat()
        val dashLen = 8.dpToPx().toFloat()
        val gapLen = 5.dpToPx().toFloat()
        var x = startX
        while (x < endX) {
            val x2 = (x + dashLen).coerceAtMost(endX)
            canvas.drawLine(x, y, x2, y, paint)
            x += dashLen + gapLen
        }
    }

    /**
     * 绘制点线下划线，2dp圆点+4dp间隔
     */
    private fun drawDottedLine(canvas: Canvas, paint: Paint, startX: Float, y: Float, endX: Float, underlineWidth: Int) {
        paint.strokeWidth = underlineWidth.dpToPx().toFloat()
        val dotSize = 2.dpToPx().toFloat()
        val gapLen = 4.dpToPx().toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        var x = startX
        while (x < endX) {
            val x2 = (x + dotSize).coerceAtMost(endX)
            canvas.drawLine(x, y, x2, y, paint)
            x += dotSize + gapLen
        }
    }

    /**
     * 绘制波浪线下划线，使用贝塞尔曲线实现
     */
    private fun drawWavyLine(canvas: Canvas, paint: Paint, startX: Float, y: Float, endX: Float, underlineWidth: Int) {
        paint.strokeWidth = underlineWidth.dpToPx().toFloat()
        val path = Path()
        val waveAmplitude = 3.dpToPx().toFloat()
        val waveLength = 12.dpToPx().toFloat()
        path.moveTo(startX, y)
        var currentX = startX
        while (currentX < endX) {
            val nextX = (currentX + waveLength).coerceAtMost(endX)
            val midX = (currentX + nextX) / 2
            path.quadTo(midX, y - waveAmplitude, nextX, y)
            currentX = nextX
            if (currentX < endX) {
                val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                val midX2 = (currentX + nextX2) / 2
                path.quadTo(midX2, y + waveAmplitude, nextX2, y)
                currentX = nextX2
            }
        }
        canvas.drawPath(path, paint)
    }

    /**
     * 判断是否满足快速绘制条件
     */
    fun checkFastDraw(): Boolean {
        if (!AppConfig.optimizeRender || exceed || !onlyTextColumn || textPage.isMsgPage) {
            return false
        }
        if (wordSpacing != 0f && (!atLeastApi26 || !wordSpacingWorking)) {
            return false
        }
        if (searchResultColumnCount != 0) {
            return false
        }
        return columns.none {
            it is TextBaseColumn && (it.textColor != null || it.underlineMode != 0)
        }
    }

    /**
     * 绘制高亮规则匹配文本的下划线段
     */
    private fun drawStyledUnderlines(canvas: Canvas) {
        if (isImage || columns.isEmpty()) return
        var rangeStart = 0f
        var rangeEnd = 0f
        var mode = 0
        var color = 0
        var svgPath = ""
        var active = false
        columns.forEachIndexed { index, column ->
            val textColumn = column as? TextBaseColumn
            val currentMode = textColumn?.underlineMode ?: 0
            val currentColor = textColumn?.underlineColor
                ?: textColumn?.textColor
                ?: ReadBookConfig.textColor
            val currentSvgPath = textColumn?.underlineSvgPath ?: ""
            val shouldContinue = active && currentMode == mode && currentColor == color && currentSvgPath == svgPath
            when {
                currentMode == 0 && active -> {
                    drawUnderlineSegment(canvas, rangeStart, rangeEnd, mode, color, svgPath)
                    active = false
                }
                currentMode != 0 && !active -> {
                    rangeStart = textColumn!!.start
                    rangeEnd = textColumn.end
                    mode = currentMode
                    color = currentColor
                    svgPath = currentSvgPath
                    active = true
                }
                currentMode != 0 && shouldContinue -> {
                    rangeEnd = textColumn!!.end
                }
                currentMode != 0 -> {
                    drawUnderlineSegment(canvas, rangeStart, rangeEnd, mode, color, svgPath)
                    rangeStart = textColumn!!.start
                    rangeEnd = textColumn.end
                    mode = currentMode
                    color = currentColor
                    svgPath = currentSvgPath
                }
            }
            if (active && index == columns.lastIndex) {
                drawUnderlineSegment(canvas, rangeStart, rangeEnd, mode, color, svgPath)
            }
        }
    }

    /**
     * 绘制当前搜索结果匹配区域的高亮背景
     */
    private fun drawCurrentSearchResultBackgrounds(canvas: Canvas) {
        if (columns.isEmpty()) return
        var startX = 0f
        var endX = 0f
        var active = false
        columns.forEachIndexed { index, column ->
            val textColumn = column as? TextBaseColumn
            val current = textColumn?.isCurrentSearchResult == true
            when {
                current && !active -> {
                    startX = textColumn.start
                    endX = textColumn.end
                    active = true
                }
                current -> {
                    endX = textColumn.end
                }
                active -> {
                    drawCurrentSearchRange(canvas, startX, endX)
                    active = false
                }
            }
            if (active && index == columns.lastIndex) {
                drawCurrentSearchRange(canvas, startX, endX)
            }
        }
    }

    /**
     * 绘制搜索结果匹配范围的圆角背景
     */
    private fun drawCurrentSearchRange(canvas: Canvas, startX: Float, endX: Float) {
        val paint = PaintPool.obtain()
        paint.set(ChapterProvider.contentPaint)
        paint.color = (0x33 shl 24) or (ReadBookConfig.textAccentColor and 0x00FFFFFF)
        paint.style = android.graphics.Paint.Style.FILL
        val radius = 5.dpToPx().toFloat()
        canvas.drawRoundRect(
            startX,
            1.dpToPx().toFloat(),
            endX,
            height - 1.dpToPx().toFloat(),
            radius,
            radius,
            paint
        )
        PaintPool.recycle(paint)
    }

    /**
     * 绘制单段下划线，用于高亮规则匹配区域，支持实线/虚线/波浪线/标题强调条
     */
    private fun drawUnderlineSegment(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        underlineMode: Int,
        underlineColor: Int,
        svgPathStr: String = "",
    ) {
        val underlineWidth = ReadBookConfig.durConfig.underlineWidth
        val paint = TextPaint(ChapterProvider.contentPaint).apply {
            color = underlineColor
            strokeWidth = underlineWidth.dpToPx().toFloat()
            style = android.graphics.Paint.Style.STROKE
        }
        val distance = (ChapterProvider.lineSpacingExtra * 10 - 11).coerceIn(-1f, 10f)
        val lineY = height + distance.dpToPx()
        when (underlineMode) {
            1 -> canvas.drawLine(startX, lineY, endX, lineY, paint)
            2 -> drawDashedLine(canvas, paint, startX, lineY, endX, underlineWidth)
            3 -> drawWavyLine(canvas, paint, startX, lineY, endX, underlineWidth)
            4 -> {
                val lineGap = 3.dpToPx().toFloat()
                val line2Y = lineY + lineGap + 2.dpToPx()
                canvas.drawLine(startX, lineY, endX, lineY, paint)
                canvas.drawLine(startX, line2Y, endX, line2Y, paint)
            }
            5 -> {
                if (svgPathStr.isNotBlank()) {
                    drawSvgPath(canvas, startX, endX, lineY, svgPathStr, paint)
                }
            }
        }
    }
    
    private fun drawSvgPath(
        canvas: Canvas,
        startX: Float,
        endX: Float,
        lineY: Float,
        svgPathStr: String,
        paint: TextPaint
    ) {
        val baseWidth = 100f
        val baseY = 50f
        val path = io.legado.app.ui.book.read.config.SvgPathParser.parse(svgPathStr) ?: return
        
        val width = endX - startX
        val scaleX = width / baseWidth
        val scaleY = 1f
        val translateX = startX
        val translateY = lineY - baseY
        
        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleX, scaleY)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    /**
     * 触发行重绘，同时刷新页面缓存
     */
    fun invalidate() {
        invalidateSelf()
        textPage.invalidate()
    }

    /**
     * 仅触发行自身缓存失效
     */
    fun invalidateSelf() {
        canvasRecorder.invalidate()
    }

    /**
     * 释放 Canvas 录制器资源
     */
    fun recycleRecorder() {
        canvasRecorder.recycle()
    }

    /**
     * 静态常量和兼容性检测
     */
    @SuppressLint("NewApi")
    companion object {
        val emptyTextLine = TextLine()
        private val atLeastApi26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val atLeastApi28 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        private val atLeastApi35 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        private val wordSpacingWorking by lazy {
            // issue 3785 3846
            val paint = PaintPool.obtain()
            val text = "一二 三"
            val width1 = paint.measureText(text)
            try {
                paint.wordSpacing = 10f
                val width2 = paint.measureText(text)
                width2 - width1 == 10f
            } catch (e: NoSuchMethodError) {
                false
            } finally {
                PaintPool.recycle(paint)
            }
        }
    }

}
