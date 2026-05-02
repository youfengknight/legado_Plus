package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.widget.text.StrokeTextView
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

/**
 * 字重切换控件
 * 
 * 支持两种模式：
 * - 粗略模式：三个固定选项（正常/粗体/细体）
 * - 精细模式：SeekBar 进度条，支持 100~900 的字重值
 * 
 * 精细模式需要 Android 9 (API 28) 以上版本支持
 */
class TextFontWeightConverter(context: Context, attrs: AttributeSet?) :
    StrokeTextView(context, attrs) {

    // 显示的文字内容，用于高亮当前选中的字重
    private val spannableString = SpannableString(context.getString(R.string.font_weight_text))
    
    // 高亮颜色，使用主题强调色
    private var enabledSpan: ForegroundColorSpan = ForegroundColorSpan(context.accentColor)
    
    // 字重变化回调，用于通知外部刷新页面
    private var onChanged: (() -> Unit)? = null

    init {
        text = spannableString
        if (!isInEditMode) {
            upUi(ReadBookConfig.textBold)
        }
        setOnClickListener {
            showFontWeightDialog()
        }
    }

    /**
     * 更新 UI 显示
     * 在粗略模式下，高亮显示当前选中的字重选项
     * 
     * @param type 当前字重值（粗略模式：0=正常, 1=粗体, 2=细体；精细模式：100~900）
     */
    fun upUi(type: Int) {
        spannableString.removeSpan(enabledSpan)
        // 只有粗略模式才显示高亮
        if (AppConfig.textBoldMode == 0) {
            when (type) {
                0 -> spannableString.setSpan(enabledSpan, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                1 -> spannableString.setSpan(enabledSpan, 2, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                2 -> spannableString.setSpan(enabledSpan, 4, 5, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }
        text = spannableString
    }

    /**
     * 显示字重选择对话框
     * 根据当前模式显示不同的对话框
     */
    private fun showFontWeightDialog() {
        if (AppConfig.textBoldMode == 0) {
            showCoarseModeDialog()
        } else {
            showFineModeDialog()
        }
    }

    /**
     * 显示粗略模式对话框
     * 提供三个固定选项：正常/粗体/细体
     * 标题栏右侧有"精细调整"按钮可切换到精细模式
     */
    private fun showCoarseModeDialog() {
        val items = context.resources.getStringArray(R.array.text_font_weight).toList()
        context.alert(titleResource = R.string.text_font_weight_converter) {
            // 使用自定义标题栏，包含标题和右上角的切换按钮
            customTitle {
                createTitleBar(
                    context.getString(R.string.text_font_weight_converter),
                    context.getString(R.string.text_bold_fine_mode)
                ) {
                    switchToFineMode()
                }
            }
            // 三个选项列表
            items(items) { _, i ->
                ReadBookConfig.textBold = i
                upUi(i)
                onChanged?.invoke()
            }
        }
    }

    /**
     * 显示精细模式对话框
     * 提供 SeekBar 进度条，支持 100~900 的字重值
     * 标题栏右侧有"粗略调整"按钮可切换回粗略模式
     */
    private fun showFineModeDialog() {
        val currentValue = ReadBookConfig.textBold.coerceIn(100, 900)
        var tempValue = currentValue
        
        context.alert {
            // 使用自定义标题栏，包含标题和右上角的切换按钮
            customTitle {
                createTitleBar(
                    context.getString(R.string.text_font_weight_converter),
                    context.getString(R.string.text_bold_coarse_mode)
                ) {
                    switchToCoarseMode()
                }
            }
            
            // 自定义视图：包含 SeekBar 和当前值显示
            customView {
                createFineModeView(tempValue) { newValue ->
                    tempValue = newValue
                }
            }
            
            // 确认按钮，保存选择的字重值
            positiveButton(android.R.string.ok) {
                ReadBookConfig.textBold = tempValue
                upUi(tempValue)
                onChanged?.invoke()
            }
            
            // 取消按钮
            negativeButton(android.R.string.cancel) {}
        }
    }

    /**
     * 创建标题栏视图
     * 包含左侧的标题文字和右侧的切换按钮
     * 
     * @param title 标题文字
     * @param buttonText 右侧按钮文字
     * @param onButtonClick 按钮点击回调
     * @return 标题栏 LinearLayout
     */
    private fun createTitleBar(title: String, buttonText: String, onButtonClick: () -> Unit): View {
        val bg = context.bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = context.getPrimaryTextColor(isLight)
        val accentColor = context.accentColor
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            
            // 左侧标题
            val titleTextView = TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            // 右侧切换按钮
            val switchButton = TextView(context).apply {
                text = buttonText
                textSize = 14f
                setTextColor(accentColor)
                setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                setOnClickListener {
                    onButtonClick()
                }
            }
            
            addView(titleTextView)
            addView(switchButton)
        }
    }

    /**
     * 创建精细模式的自定义视图
     * 
     * @param currentValue 当前字重值
     * @param onValueChanged 值变化回调
     * @return 包含 SeekBar 和标签的 LinearLayout
     */
    private fun createFineModeView(currentValue: Int, onValueChanged: (Int) -> Unit): View {
        val bg = context.bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = context.getPrimaryTextColor(isLight)
        
        // 主容器：垂直布局
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        
        // 当前值显示：显示具体数值（如 400）
        val valueTextView = TextView(context).apply {
            text = currentValue.toString()
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }
        
        // 字重名称显示：显示对应的名称（如"正常"）
        val fontWeightNames = context.resources.getStringArray(R.array.text_font_weight_fine)
        val fontWeightNameTextView = TextView(context).apply {
            text = getFontWeightName(currentValue, fontWeightNames)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }
        
        // SeekBar 进度条：范围 100~900
        val seekBar = SeekBar(context).apply {
            max = 800  // 900 - 100 = 800
            progress = currentValue - 100
            setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 100  // 转换为实际字重值
                    valueTextView.text = value.toString()
                    fontWeightNameTextView.text = getFontWeightName(value, fontWeightNames)
                    onValueChanged(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        
        // 底部标签容器：显示"细"和"粗"
        val labelsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val thinLabel = TextView(context).apply {
            text = context.getString(R.string.text_bold_thin)
            textSize = 12f
            setTextColor(textColor)
        }
        
        val boldLabel = TextView(context).apply {
            text = context.getString(R.string.text_bold_bold)
            textSize = 12f
            setTextColor(textColor)
        }
        
        labelsContainer.apply {
            addView(thinLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(boldLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.END
            })
        }
        
        // 组装视图
        container.apply {
            addView(valueTextView)
            addView(fontWeightNameTextView)
            addView(seekBar)
            addView(labelsContainer)
        }
        
        return container
    }

    /**
     * 根据字重值获取对应的名称
     * 
     * 字重值对照表：
     * - 100: 极细 (Thin)
     * - 200: 超细 (Extra Light)
     * - 300: 细体 (Light)
     * - 400: 正常 (Normal)
     * - 500: 中等 (Medium)
     * - 600: 半粗 (Semi Bold)
     * - 700: 粗体 (Bold)
     * - 800-900: 超粗 (Extra Bold)
     * 
     * @param value 字重值（100~900）
     * @param names 名称数组
     * @return 对应的名称
     */
    private fun getFontWeightName(value: Int, names: Array<String>): String {
        return when {
            value <= 150 -> names.getOrElse(0) { "" }
            value <= 250 -> names.getOrElse(1) { "" }
            value <= 350 -> names.getOrElse(2) { "" }
            value <= 450 -> names.getOrElse(3) { "" }
            value <= 550 -> names.getOrElse(4) { "" }
            value <= 650 -> names.getOrElse(5) { "" }
            value <= 750 -> names.getOrElse(6) { "" }
            else -> names.getOrElse(7) { "" }
        }
    }

    /**
     * 切换到精细模式
     * 会检查系统版本和首次提示
     */
    private fun switchToFineMode() {
        // 检查系统版本，Android 9 以下不支持
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            showNotSupportedDialog()
            return
        }
        
        // 检查是否首次切换，首次需要显示提示
        if (!AppConfig.textBoldFineTipShown) {
            showFirstTimeTipDialog {
                AppConfig.textBoldFineTipShown = true
                doSwitchToFineMode()
            }
        } else {
            doSwitchToFineMode()
        }
    }

    /**
     * 显示不支持对话框
     * 当系统版本低于 Android 9 时显示
     */
    private fun showNotSupportedDialog() {
        context.alert(
            titleResource = R.string.text_bold_not_supported_title,
            messageResource = R.string.text_bold_not_supported_message
        ) {
            okButton()
        }
    }

    /**
     * 显示首次提示对话框
     * 告知用户精细模式的使用说明
     * 
     * @param onConfirmed 用户点击确认后的回调
     */
    private fun showFirstTimeTipDialog(onConfirmed: () -> Unit) {
        context.alert(
            titleResource = R.string.text_bold_fine_tip_title,
            messageResource = R.string.text_bold_fine_tip_message
        ) {
            okButton {
                onConfirmed()
            }
        }
    }

    /**
     * 执行切换到精细模式
     * 设置模式为精细模式，并初始化字重值为 400（正常）
     */
    private fun doSwitchToFineMode() {
        AppConfig.textBoldMode = 1
        ReadBookConfig.textBold = 400  // 默认正常字重
        upUi(400)
        onChanged?.invoke()
        showFineModeDialog()
    }

    /**
     * 切换到粗略模式
     * 设置模式为粗略模式，并重置字重值为 0（正常）
     */
    private fun switchToCoarseMode() {
        AppConfig.textBoldMode = 0
        ReadBookConfig.textBold = 0  // 默认正常
        upUi(0)
        onChanged?.invoke()
        showCoarseModeDialog()
    }

    /**
     * 设置字重变化回调
     * 
     * @param unit 回调函数
     */
    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}
