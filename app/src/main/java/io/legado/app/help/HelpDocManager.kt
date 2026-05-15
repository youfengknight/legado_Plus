package io.legado.app.help

import android.content.res.AssetManager

object HelpDocManager {
    // 帮助文档切换列表（显示在文档切换器中）
    val allHelpDocs = listOf(
        HelpDoc("ruleHelp", "书源制作教程"),
        HelpDoc("jsHelp", "js变量和函数"),
        HelpDoc("rssRuleHelp", "订阅源规则帮助"),
        HelpDoc("xpathHelp", "xpath语法教程"),
        HelpDoc("regexHelp", "正则表达式教程"),
        HelpDoc("txtTocRuleHelp", "txt目录正则说明"),
        HelpDoc("dictRuleHelp", "字典规则说明"),
        HelpDoc("httpTTSHelp", "在线朗读规则"),
        HelpDoc("debugHelp", "书源调试说明"),
        HelpDoc("书源字段规则类型", "书源字段规则类型"),
        HelpDoc("订阅源字段规则类型", "订阅源字段规则类型"),
        HelpDoc("订阅源机制详解", "订阅源机制详解"),
        HelpDoc("预注入JS机制详解", "订阅源预注入JS机制"),
        HelpDoc("jsPackagesHelp", "JS Packages使用指南"),
        HelpDoc("替换规则流程与机制", "替换规则流程与机制"),
        HelpDoc("下拉刷新流程与机制", "下拉刷新流程与机制"),
        HelpDoc("部分功能需要安卓特定版本", "需要安卓特定版本的功能"),
        HelpDoc("ExtensionContentType", "扩展内容类型"),
        HelpDoc("jsVariableHelp", "JS 变量存储机制详解"),
        HelpDoc("图片加载机制", "图片加载机制"),
        HelpDoc("网络请求机制", "网络请求机制"),
        HelpDoc("错误处理机制", "错误处理机制"),
        HelpDoc("懒加载与缓存机制分析", "懒加载与缓存机制"),
        HelpDoc("书源登录信息与运行变量备份机制", "书源登录信息与运行变量备份机制")
    )
    
    // 隐藏的帮助文档（可以在某些界面加载查看，但不会出现在切换列表中）
    private val hiddenHelpDocs = listOf(
        HelpDoc("SourceMBookHelp", "书源管理界面帮助"),
        HelpDoc("SourceMRssHelp", "订阅源管理界面帮助"),
        HelpDoc("replaceRuleHelp", "替换规则说明"),
        HelpDoc("readMenuHelp", "阅读界面帮助文档"),
        HelpDoc("webDavBookHelp", "WebDav书籍简明使用教程"),
        HelpDoc("webDavHelp", "WebDav备份教程"),
        HelpDoc("updateLog", "更新日志")
    )
    
    // 所有帮助文档（切换列表 + 隐藏文档）
    val allDocs: List<HelpDoc>
        get() = allHelpDocs + hiddenHelpDocs
    
    // 加载帮助文档
    fun loadDoc(assets: AssetManager, fileName: String): String {
        return String(assets.open("web/help/md/${fileName}.md").readBytes())
    }
    
    // 获取帮助文档在切换列表中的索引
    fun getDocIndex(fileName: String): Int {
        return allHelpDocs.indexOfFirst { it.fileName == fileName }
    }
    
    // 根据文件名获取文档（优先从切换列表查找，找不到再从隐藏文档查找）
    fun getDocByFileName(fileName: String): HelpDoc? {
        return allHelpDocs.find { it.fileName == fileName }
            ?: hiddenHelpDocs.find { it.fileName == fileName }
    }
    
    // 判断文档是否为隐藏文档
    fun isHiddenDoc(fileName: String): Boolean {
        return hiddenHelpDocs.any { it.fileName == fileName }
    }
}
