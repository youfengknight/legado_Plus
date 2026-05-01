package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.source.manage.BookSourceSort
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 发现界面
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreAdapter(requireContext(), this) }
    private val linearLayoutManager by lazy { LinearLayoutManager(context) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    // 列表项差异比较回调
    private val diffItemCallBack = ExploreDiffItemCallBack()
    // 书源分组集合
    private val groups = linkedSetOf<String>()
    // 发现数据流任务
    private var exploreFlowJob: Job? = null
    // 分组菜单
    private var groupsMenu: SubMenu? = null
    // 排序方式
    private var sort = BookSourceSort.Default
    // 是否升序排序
    private var sortAscending = true

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        initRecyclerView()
        initGroupData()
        upExploreData()
    }

    /**
     * 创建选项菜单
     * 初始化菜单布局，设置排序菜单项状态，并更新分组菜单
     */
    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu
        sortSubMenu?.findItem(R.id.menu_sort_desc)?.isChecked = !sortAscending
        sortSubMenu?.setGroupCheckable(R.id.menu_group_sort, true, true)
        upGroupsMenu()
    }

    /**
     * 准备选项菜单
     * 更新排序菜单项的选中状态
     */
    override fun onPrepareOptionsMenu(menu: Menu) {
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu!!
        sortSubMenu.findItem(R.id.menu_sort_desc).isChecked = !sortAscending
        sortSubMenu.setGroupCheckable(R.id.menu_group_sort, true, true)
        super.onPrepareOptionsMenu(menu)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    private fun initRecyclerView() {
        binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = linearLayoutManager
        binding.rvFind.adapter = adapter
        (binding.rvFind.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.rvFind.setItemViewCacheSize(8)
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.rvFind.scrollToPosition(0)
                }
            }
        })
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                    delay(500)
                }
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    appDb.bookSourceDao.flowExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupExplore(key)
                }

                else -> {
                    appDb.bookSourceDao.flowExplore(searchKey)
                }
            }.map { data ->
                // 根据排序方式和排序方向对数据进行排序
                if (sortAscending) {
                    // 升序排序
                    when (sort) {
                        // 按书源名称排序
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o1.bookSourceName.cnCompare(o2.bookSourceName)
                        }

                        // 按书源URL排序
                        BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }
                        // 按更新时间排序（最新的在前）
                        BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                        // 按响应时间排序
                        BookSourceSort.Respond -> data.sortedBy { it.respondTime }
                        else -> data
                    }
                } else {
                    // 降序排序
                    when (sort) {
                        // 按书源名称排序
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o2.bookSourceName.cnCompare(o1.bookSourceName)
                        }

                        // 按书源URL排序
                        BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }
                        // 按更新时间排序（最旧的在前）
                        BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                        // 按响应时间排序
                        BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }
                        else -> data.reversed()
                    }
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                binding.tvEmptyMsg.isGone = it.isNotEmpty() || searchView.query.isNotEmpty()
                // 不能用 adapter 当前列表和新列表直接判等；BookSourcePart.equals 只比较 URL，
                // 改名称这类“同一源内容变化”会被误判成相同，导致发现页不刷新。
                adapter.setItems(it, diffItemCallBack)
                binding.rvFind.post {
                    binding.rvFind.refreshSystemScrollBar()
                }
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.upResumed(true)
    }

    override fun onPause() {
        adapter.upResumed(false)
        searchView.clearFocus()
        super.onPause()
    }

    override fun onDestroyView() {
        adapter.onDestroy()
        super.onDestroyView()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach {
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }

    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_sort_desc -> {
                sortAscending = !sortAscending
                item.isChecked = !sortAscending
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_manual -> {
                item.isChecked = true
                sort = BookSourceSort.Default
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_name -> {
                item.isChecked = true
                sort = BookSourceSort.Name
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_url -> {
                item.isChecked = true
                sort = BookSourceSort.Url
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_time -> {
                item.isChecked = true
                sort = BookSourceSort.Update
                upExploreData(searchView.query?.toString())
            }

            R.id.menu_sort_respondTime -> {
                item.isChecked = true
                sort = BookSourceSort.Respond
                upExploreData(searchView.query?.toString())
            }
        }
        if (item.groupId == R.id.menu_group_text) {
            searchView.setQuery("group:${item.title}", true)
        }
    }

    override fun scrollTo(pos: Int) {
        (binding.rvFind.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        adapter.clearPendingScrollToSource()
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
        }
    }

    override fun toTop(source: BookSourcePart) {
        viewModel.topSource(source)
    }

    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton {
                viewModel.deleteSource(source)
            }
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(requireContext(), bookSource)
    }

    /**
     * 显示查询对话框
     */
    override fun showKindQueryDialog(source: BookSourcePart) {
        showDialogFragment(ExploreKindQueryDialog(source.bookSourceUrl, source.bookSourceName))
    }
    
    /**
     * 压缩目录
     */
    fun compressExplore() {
        if (!adapter.compressExplore()) {
            if (AppConfig.isEInkMode) {
                binding.rvFind.scrollToPosition(0)
            } else {
                binding.rvFind.smoothScrollToPosition(0)
            }
        }
    }

}
