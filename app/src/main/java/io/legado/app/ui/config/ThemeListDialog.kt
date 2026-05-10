package io.legado.app.ui.config

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ThemeListDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { Adapter(requireContext()) }
    private var isMultiSelectMode = false
    private val selectedPositions = mutableSetOf<Int>()

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.theme_list)
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        recyclerView.adapter = adapter
    }

    private fun initMenu() = binding.run {
        toolBar.setOnMenuItemClickListener(this@ThemeListDialog)
        toolBar.inflateMenu(R.menu.theme_list)
        toolBar.menu.applyTint(requireContext())
    }

    // 初始化多选菜单
    private fun initMultiSelectMenu() = binding.run {
        toolBar.menu.clear()
        toolBar.inflateMenu(R.menu.theme_list_multi)
        toolBar.menu.applyTint(requireContext())
        toolBar.setTitle(getString(R.string.selected, selectedPositions.size))
    }

    fun initData() {
        adapter.setItems(ThemeConfig.configList)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_import -> {
                requireContext().getClipText()?.let { clipText ->
                    val count = ThemeConfig.addConfig(clipText)
                    if (count > 0) {
                        initData()
                        toastOnUi("成功导入 $count 个主题")
                    } else {
                        toastOnUi("格式不对,添加失败")
                    }
                } ?: toastOnUi("剪贴板为空")
            }
            R.id.menu_select_all -> {
                if (selectedPositions.size == adapter.itemCount) {
                    selectedPositions.clear()
                } else {
                    selectedPositions.clear()
                    for (i in 0 until adapter.itemCount) {
                        selectedPositions.add(i)
                    }
                }
                adapter.notifyDataSetChanged()
                binding.toolBar.setTitle(getString(R.string.selected, selectedPositions.size))
            }
            R.id.menu_to_top -> {
                if (selectedPositions.isEmpty()) {
                    toastOnUi("请先选择主题")
                    return true
                }
                toTopSelected()
            }
            R.id.menu_export -> {
                if (selectedPositions.isEmpty()) {
                    toastOnUi("请先选择主题")
                    return true
                }
                exportSelected()
            }
            R.id.menu_delete -> {
                if (selectedPositions.isEmpty()) {
                    toastOnUi("请先选择主题")
                    return true
                }
                deleteSelected()
            }
        }
        return true
    }

    // 进入多选模式
    private fun enterMultiSelectMode(position: Int) {
        isMultiSelectMode = true
        selectedPositions.clear()
        selectedPositions.add(position)
        initMultiSelectMenu()
        adapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedPositions.clear()
        binding.toolBar.menu.clear()
        initMenu()
        binding.toolBar.setTitle(R.string.theme_list)
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
            if (selectedPositions.isEmpty()) {
                exitMultiSelectMode()
                return
            }
        } else {
            selectedPositions.add(position)
        }
        adapter.notifyItemChanged(position)
        binding.toolBar.setTitle(getString(R.string.selected, selectedPositions.size))
    }

    private fun exportSelected() {
        val configs = selectedPositions.sorted().map { index ->
            ThemeConfig.configList[index]
        }
        val json = GSON.toJson(configs)
        requireContext().share(json, "主题分享")
        exitMultiSelectMode()
    }

    private fun deleteSelected() {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                val positions = selectedPositions.sortedDescending()
                positions.forEach { position ->
                    ThemeConfig.delConfig(position)
                }
                exitMultiSelectMode()
                initData()
            }
            noButton()
        }
    }

    // 移动选中主题到顶部
    private fun toTopSelected() {
        val positions = selectedPositions.sorted()
        ThemeConfig.toTopConfigs(positions)
        exitMultiSelectMode()
        initData()
    }

    fun delete(index: Int) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                ThemeConfig.delConfig(index)
                initData()
            }
            noButton()
        }
    }

    fun share(index: Int) {
        val json = GSON.toJson(ThemeConfig.configList[index])
        requireContext().share(json, "主题分享")
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<ThemeConfig.Config, ItemThemeConfigBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding {
            return ItemThemeConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: ThemeConfig.Config,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                tvName.text = item.themeName
                if (isMultiSelectMode) {
                    cbSelect.visibility = View.VISIBLE
                    cbSelect.isChecked = selectedPositions.contains(holder.layoutPosition)
                    ivShare.visibility = View.GONE
                    ivDelete.visibility = View.GONE
                } else {
                    cbSelect.visibility = View.GONE
                    ivShare.visibility = View.VISIBLE
                    ivDelete.visibility = View.VISIBLE
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.apply {
                root.setOnClickListener {
                    val position = holder.layoutPosition
                    if (isMultiSelectMode) {
                        toggleSelection(position)
                    } else {
                        ThemeConfig.applyConfig(context, ThemeConfig.configList[position])
                    }
                }
                root.setOnLongClickListener {
                    if (!isMultiSelectMode) {
                        enterMultiSelectMode(holder.layoutPosition)
                    }
                    true
                }
                ivShare.setOnClickListener {
                    if (!isMultiSelectMode) {
                        share(holder.layoutPosition)
                    }
                }
                ivDelete.setOnClickListener {
                    if (!isMultiSelectMode) {
                        delete(holder.layoutPosition)
                    }
                }
            }
        }

    }
}
