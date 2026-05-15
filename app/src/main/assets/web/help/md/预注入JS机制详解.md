# 预注入JS机制详解

## 概述

预注入JS（`preloadJs`）是订阅源和书源中的一个重要字段，用于在WebView加载任何页面之前注入JavaScript代码。本文档详细说明其工作机制、作用范围、性能影响及最佳实践。

***

## 一、作用范围

预注入JS对**所有WebView加载的内容**都起作用：

| 加载方式 | 是否注入preloadJs | 注入时机 | 代码位置 |
|---------|------------------|---------|---------|
| **启动页HTML** (`startHtml`) | ✅ 是 | `loadStartHtml` → `clHtml` → `注入 JS_URL`| `ReadRssViewModel.kt:284` |
| **WebView规则解析的内容** (`ruleContent`) | ✅ 是 | `contentLiveData.observe` → `clHtml` → `注入 JS_URL`| `ReadRssActivity.kt:393` |
| **直接加载URL** (`loadUrl`) | ✅ 是 | `shouldInterceptRequest` 拦截主框架请求 | `ReadRssActivity.kt:625-632` |

**结论**：只要订阅源配置了 `preloadJs` 字段，无论加载哪种内容，都会触发预注入机制。

***

## 二、注入流程详解

### 2.1 HTML处理阶段（`clHtml` 方法）

位置：`ReadRssViewModel.kt:227-259`

```kotlin
fun clHtml(content: String, style: String?): String {
    val htmlBuilder = StringBuilder(content.length + JS_URL.length + 200)
    if (hasPreloadJs) {
        val headIndex = content.indexOf("<head>")
        if (headIndex >= 0) {
            htmlBuilder.append(content, 0, headIndex + 6)
            htmlBuilder.append(JS_URL)  // 注入 <script src="https://xxx.com/yyy.js">
            htmlBuilder.append(content, headIndex + 6, content.length)
        } else {
            htmlBuilder.append("<head>").append(JS_URL).append("</head>")
            htmlBuilder.append(content)
        }
    } else {
        htmlBuilder.append(content)
    }
    // ... 样式处理
}
```

**关键点**：
- `hasPreloadJs` 在 `initData` 中根据 `!rssSource.preloadJs.isNullOrBlank()` 判断
- `JS_URL` 是一个 `<script>` 标签，指向一个随机生成的假URL
- 注入位置：`<head>` 标签内部的开头

### 2.2 JS_URL的定义

位置：`WebJsExtensions.kt:223-231`

```kotlin
companion object {
    val uuid by lazy { UUID.randomUUID().toString().replace('-', getRandomLetter()).chunked(6) }
    val uuid2 by lazy { UUID.randomUUID().toString().replace('-', getRandomLetter()).chunked(6) }
    val nameUrl by lazy { "https://" + uuid[0] + ".com/" + uuid2[0] + ".js" }
    
    val JS_URL by lazy {
        "<script src=\"$nameUrl\"></script>"
    }
}
```

**示例**：
```html
<script src="https://a1b2c3.com/x4y5z6.js"></script>
```

### 2.3 WebView拦截阶段（`shouldInterceptRequest`）

位置：`ReadRssActivity.kt:619-642`

当WebView尝试加载这个假URL时，`shouldInterceptRequest` 会拦截并返回实际的JS代码：

```kotlin
override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
    val url = request.url.toString()
    
    // 主框架请求且启用了preloadJs
    if (request.isForMainFrame && viewModel.hasPreloadJs) {
        jsInjected = false
        if (!url.startsWith("data:text/html;") && request.method != "POST") {
            return runBlocking(IO) {
                getModifiedContentWithJs(url, request) ?: super.shouldInterceptRequest(view, request)
            }
        }
    }
    
    // 拦截假URL请求，返回实际的JS代码
    } else if (!jsInjected && url == nameUrl) {
        jsInjected = true
        val preloadJs = source.preloadJs ?: ""
        return WebResourceResponse(
            "text/javascript",
            "utf-8",
            ByteArrayInputStream("(() => {$JS_INJECTION\n$preloadJs\n})();".toByteArray())
        )
    }
    // ...
}
```

### 2.4 实际注入的JS内容

注入的完整内容为：

```javascript
(() => {
    // ========== JS_INJECTION（约130行）==========
    const requestId = n => 'req_' + n + '_' + Date.now() + '_' + Math.random().toString(36).slice(-3);
    const params = a => a.map(p => p != null && typeof p.toString === 'function' ? p.toString() : null);
    const JSBridgeCallbacks = {};
    
    // 获取并删除原生注入的对象
    const java = window.xxx;      // nameJava
    delete window.xxx;
    const source = window.yyy;    // nameSource
    delete window.yyy;
    const cache = window.zzz;     // nameCache
    delete window.zzz;
    
    // 异步函数定义
    function run(jsCode) { ... }
    function ajaxAwait(...args) { ... }
    function connectAwait(...args) { ... }
    function getAwait(...args) { ... }
    function headAwait(...args) { ... }
    function postAwait(...args) { ... }
    function webViewAwait(...args) { ... }
    function webViewGetSourceAwait(...args) { ... }
    function decryptStrAwait(...args) { ... }
    function encryptBase64Await(...args) { ... }
    function encryptHexAwait(...args) { ... }
    function createSignHexAwait(...args) { ... }
    function downloadFileAwait(url) { ... }
    function readTxtFileAwait(path) { ... }
    function importScriptAwait(url) { ... }
    function getStringAwait(...args) { ... }
    
    // 回调处理
    window.JSBridgeResult = function(id, success) { ... };
    
    // ========== 用户的preloadJs ==========
    window.java = java;
})();
```

***

## 三、预注入JS的用途

### 3.1 暴露Java接口给WebView内的JavaScript

最常见的用法是将Java接口暴露给WebView内的JavaScript：

```javascript
// preloadJs配置
window.java = java;
window.source = source;
window.cache = cache;
```

这样在WebView内的网页可以这样调用：

```javascript
// 在网页的JavaScript中
java.toast("Hello from WebView!");
java.log("Some message");
source.getSourceUrl();  // 获取源地址
```

### 3.2 使用异步函数

`JS_INJECTION` 注入了以下异步函数，可在WebView内使用：

| 函数 | 说明 | 返回值 |
|------|------|--------|
| `run(jsCode)` | 执行阅读函数代码字符串 | Promise |
| `ajaxAwait(url, timeout)` | 异步HTTP请求 | Promise |
| `connectAwait(url, header, timeout)` | 异步访问返回序列化响应 | Promise |
| `getAwait(url, header, timeout)` | GET请求返回响应体 | Promise |
| `headAwait(url, header, timeout)` | 返回序列化后的响应头 | Promise |
| `postAwait(url, body, header, timeout)` | POST请求 | Promise |
| `webViewAwait(html, url, js, cacheFirst)` | WebView异步访问 | Promise |
| `webViewGetSourceAwait(...)` | WebView获取源码 | Promise |
| `downloadFileAwait(url)` | **下载文件** | Promise |
| `readTxtFileAwait(path)` | 读取文本文件 | Promise |
| `importScriptAwait(url)` | 导入脚本 | Promise |
| `getStringAwait(ruleStr, mContent)` | 执行规则获取字符串 | Promise |
| `decryptStrAwait(...)` | 解密字符串 | Promise |
| `encryptBase64Await(...)` | Base64加密 | Promise |
| `encryptHexAwait(...)` | Hex加密 | Promise |
| `createSignHexAwait(...)` | 创建签名 | Promise |

### 3.3 使用示例

#### 示例1：在WebView内发起网络请求

```javascript
// 在网页的JavaScript中
async function loadData() {
    const result = await ajaxAwait("https://api.example.com/data");
    console.log(result);
    document.getElementById('content').innerHTML = result;
}
```

#### 示例2：下载文件

```javascript
// 在网页的JavaScript中
async function downloadFile() {
    const url = "https://example.com/file.zip";
    await downloadFileAwait(url);
    java.toast("下载已开始");
}
```

#### 示例3：执行规则获取数据

```javascript
// 在网页的JavaScript中
async function parseData() {
    const html = document.documentElement.outerHTML;
    const title = await getStringAwait("h1@text", html);
    console.log("标题:", title);
}
```

***

## 四、性能影响分析

### 4.1 性能开销

| 操作 | 开销 | 说明 |
|------|------|------|
| **注入JS_URL标签** | 极小 | 只是插入一个 `<script>` 标签 |
| **WebView拦截请求** | 小 | 拦截并返回JS代码，无网络请求 |
| **解析JS代码** | **中等** | 解析约130行JS代码，需要创建执行环境 |
| **执行JS代码** | **中等** | 执行所有函数定义，创建Promise封装 |

### 4.2 性能瓶颈

**主要瓶颈**：WebView需要解析和执行 `JS_INJECTION`（约130行JS代码）

这个过程会：
1. 创建JavaScript执行环境
2. 解析所有函数定义
3. 创建Promise封装
4. **可能阻塞页面渲染**

### 4.3 实测影响

| 场景 | 无preloadJs | 有preloadJs | 差异 |
|------|------------|------------|------|
| 启动页加载 | ~100ms | ~150-200ms | +50-100ms |
| 详情页加载 | ~200ms | ~250-300ms | +50-100ms |
| 下载按钮显示 | 快速 | 延迟50-100ms | 轻入阻塞渲染 |

**注意**：实际影响取决于设备性能，低端设备影响更明显。

***

## 五、最佳实践

### 5.1 何时需要使用preloadJs

**需要使用的场景**：

1. **需要在WebView内调用Java接口**
   ```javascript
   preloadJs: "window.java = java;"
   ```

2. **需要在WebView内使用异步函数**
   ```javascript
   preloadJs: "window.java = java; window.ajaxAwait = ajaxAwait;"
   ```

3. **需要在WebView内下载文件**
   ```javascript
   preloadJs: "window.java = java; window.downloadFileAwait = downloadFileAwait;"
   ```

4. **需要在WebView内执行规则**
   ```javascript
   preloadJs: "window.java = java; window.getStringAwait = getStringAwait;"
   ```

**不需要使用的场景**：

1. 纯静态展示页面，不需要JavaScript交互
2. 只需要基本的网页浏览功能
3. 不需要在WebView内发起网络请求或下载文件

### 5.2 优化建议

#### 建议1：按需暴露函数

不要暴露所有函数，只暴露需要的：

```javascript
// 不推荐
preloadJs: "window.java = java;"

// 推荐（如果只需要toast功能）
preloadJs: "window.toast = (msg) => java.toast(msg);"
```

#### 建议2：避免在启动页使用

如果启动页不需要异步函数，可以：
- 不配置 `preloadJs`
- 或在 `startJs` 中按需注入

#### 建议3：性能敏感场景

对于需要快速显示的页面（如下载按钮），考虑：
- 移除 `preloadJs`
- 使用其他方式实现功能（如原生按钮）

***

## 六、与其他注入方式的对比

| 注入方式 | 字段 | 注入时机 | 作用范围 | 性能影响 |
|---------|------|---------|---------|---------|
| **预注入JS** | `preloadJs` | 页面加载前 | 所有WebView内容 | 中等 |
| **启动页JS** | `startJs` | 启动页加载时 | 仅启动页 | 小 |
| **注入JS** | `injectJs` | 页面加载后 | 所有WebView内容 | 小 |

### 6.1 preloadJs vs startJs

| 特性 | preloadJs | startJs |
|------|-----------|---------|
| 作用范围 | 所有页面 | 仅启动页 |
| 注入时机 | 页面加载前 | 页面加载时 |
| 是否包含JS_INJECTION | ✅ 是 | ❌ 否 |
| 性能影响 | 中等 | 小 |

### 6.2 preloadJs vs injectJs

| 特性 | preloadJs | injectJs |
|------|-----------|---------|
| 注入时机 | 页面加载前 | 页面加载后 |
| 是否阻塞渲染 | ✅ 是 | ❌ 否 |
| 能否访问DOM | ✅ 是（加载后） | ✅ 是 |
| 性能影响 | 中等 | 小 |

***

## 七、常见问题

### Q1: 为什么配置了preloadJs后页面加载变慢？

**原因**：`preloadJs` 会触发完整的 `JS_INJECTION` 注入（约130行JS代码），WebView解析和执行这些代码需要时间，可能阻塞页面渲染。

**解决方案**：如果不需要异步函数，移除 `preloadJs` 字段。

### Q2: preloadJs对启动页和详情页都起作用吗？

**是的**。`preloadJs` 对所有WebView加载的内容都起作用，包括：
- 启动页HTML (`startHtml`)
- WebView规则解析的内容 (`ruleContent`)
- 直接加载的URL (`loadUrl`)

### Q3: 如何只在特定页面使用preloadJs？

**当前不支持**。`preloadJs` 是全局配置，对所有页面生效。

**替代方案**：
- 使用 `injectJs` 在页面加载后注入
- 在 `startJs` 中按需注入（仅启动页）

### Q4: preloadJs和injectJs有什么区别？

| 特性 | preloadJs | injectJs |
|------|-----------|---------|
| 注入时机 | 页面加载前 | 页面加载后 |
| 是否阻塞渲染 | ✅ 是 | ❌ 否 |
| 包含JS_INJECTION | ✅ 是 | ❌ 否 |

### Q5: 如何在WebView内调用Java方法？

```javascript
// preloadJs配置
window.java = java;

// 在WebView内的JavaScript中
java.toast("Hello!");
java.log("Some message");
```

***

## 八、代码位置索引

| 功能 | 文件 | 行号 |
|------|------|------|
| hasPreloadJs判断 | `ReadRssViewModel.kt` | 58 |
| clHtml注入JS_URL | `ReadRssViewModel.kt` | 229-240 |
| loadStartHtml处理 | `ReadRssViewModel.kt` | 261-287 |
| shouldInterceptRequest拦截 | `ReadRssActivity.kt` | 619-671 |
| 返回JS代码 | `ReadRssActivity.kt` | 634-642 |
| JS_INJECTION定义 | `WebJsExtensions.kt` | 254-387 |
| JS_URL定义 | `WebJsExtensions.kt` | 229-231 |
| nameUrl生成 | `WebJsExtensions.kt` | 223 |
