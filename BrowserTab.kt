package com.maxdev.aisearchbrowser

import android.webkit.WebView
import org.json.JSONObject

/**
 * One open "browser tab". Each tab keeps its own three WebViews (one per
 * sub-tab: Ответ / Ссылки / Изображения) alive at the same time, the same
 * way Chrome keeps a tab's page alive when you switch away from it.
 */
class BrowserTab(val id: Int, val answerWebView: WebView, val linksWebView: WebView, val imagesWebView: WebView) {

    var title: String = "Новая вкладка"
    var searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO
    var aiService: AiService = AiService.CLAUDE
    var currentSubTab: SubTab = SubTab.ANSWER
    var lastQuery: String = ""

    fun webViewFor(tab: SubTab): WebView = when (tab) {
        SubTab.ANSWER -> answerWebView
        SubTab.LINKS -> linksWebView
        SubTab.IMAGES -> imagesWebView
    }

    /** Loads the correct URL into the correct webview for the current sub-tab / engine / AI choice. */
    fun runSearch(query: String) {
        lastQuery = query
        if (query.isNotBlank()) title = query
        when (currentSubTab) {
            SubTab.ANSWER -> answerWebView.loadUrl(aiService.queryUrl(query))
            SubTab.LINKS -> linksWebView.loadUrl(searchEngine.searchUrl(query))
            SubTab.IMAGES -> imagesWebView.loadUrl(searchEngine.imagesUrl(query))
        }
    }

    /** Called when the user switches sub-tab or AI/engine while a query already exists, or on first open. */
    fun refreshCurrentSubTab() {
        val wv = webViewFor(currentSubTab)
        if (wv.url == null) {
            val url = when (currentSubTab) {
                SubTab.ANSWER -> if (lastQuery.isNotBlank()) aiService.queryUrl(lastQuery) else aiService.homeUrl()
                SubTab.LINKS -> if (lastQuery.isNotBlank()) searchEngine.searchUrl(lastQuery) else searchEngine.homeUrl()
                SubTab.IMAGES -> if (lastQuery.isNotBlank()) searchEngine.imagesUrl(lastQuery) else searchEngine.homeUrl()
            }
            wv.loadUrl(url)
        }
    }

    /**
     * Only the lightweight state is saved (not the WebView itself, which can't be
     * serialized). On restart, [refreshCurrentSubTab] lazily reloads the right
     * page for whichever sub-tab was active, exactly like a freshly created tab.
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("searchEngine", searchEngine.name)
        put("aiService", aiService.name)
        put("currentSubTab", currentSubTab.name)
        put("lastQuery", lastQuery)
    }

    companion object {
        fun fromJson(json: JSONObject, answerWebView: WebView, linksWebView: WebView, imagesWebView: WebView): BrowserTab {
            val tab = BrowserTab(json.getInt("id"), answerWebView, linksWebView, imagesWebView)
            tab.title = json.optString("title", tab.title)
            tab.searchEngine = try {
                SearchEngine.valueOf(json.optString("searchEngine"))
            } catch (e: IllegalArgumentException) {
                SearchEngine.DUCKDUCKGO
            }
            tab.aiService = try {
                AiService.valueOf(json.optString("aiService"))
            } catch (e: IllegalArgumentException) {
                AiService.CLAUDE
            }
            tab.currentSubTab = try {
                SubTab.valueOf(json.optString("currentSubTab"))
            } catch (e: IllegalArgumentException) {
                SubTab.ANSWER
            }
            tab.lastQuery = json.optString("lastQuery", "")
            return tab
        }
    }
}
