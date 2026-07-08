package com.maxdev.aisearchbrowser

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

class MainActivity : AppCompatActivity() {

    private val tabs = mutableListOf<BrowserTab>()
    private var currentTabIndex = -1
    private var nextTabId = 1

    // Views that get rebuilt/updated as state changes
    private lateinit var root: LinearLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var engineLabel: TextView
    private lateinit var aiRow: LinearLayout
    private lateinit var subTabRow: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var contentContainer: android.widget.FrameLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        addNewTab(switchToIt = true)
    }

    // region ---------- UI construction ----------

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    @SuppressLint("SetTextI18n")
    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(root)

        // ---- Tab strip (browser tabs, Chrome-style) ----
        val tabStripScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8))
        }
        tabStripScroll.addView(tabStrip)
        root.addView(tabStripScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        addDivider()

        // ---- Search engine row ----
        val engineRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val engineCaption = TextView(this).apply {
            text = "Поисковая система:"
            setTextColor(Color.parseColor("#5F6368"))
        }
        engineLabel = pillButton(currentTab()?.searchEngine?.label ?: "DuckDuckGo") {
            showEnginePopup(it)
        }
        val switchEngineLink = TextView(this).apply {
            text = "сменить поиск"
            setTextColor(Color.parseColor("#1A73E8"))
            setPadding(dp(12), 0, 0, 0)
            setOnClickListener { cycleEngine() }
        }
        engineRow.addView(engineCaption)
        engineRow.addView(engineLabel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        engineRow.addView(switchEngineLink)
        root.addView(engineRow)

        // ---- Search box row ----
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        searchInput = EditText(this).apply {
            hint = "Задайте вопрос или введите запрос"
            background = pillDrawable(Color.WHITE, Color.parseColor("#DADCE0"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    performSearch(); true
                } else false
            }
        }
        val searchButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            background = pillDrawable(Color.parseColor("#1A73E8"), Color.parseColor("#1A73E8"))
            setColorFilter(Color.WHITE)
            setOnClickListener { performSearch() }
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        root.addView(searchRow)

        // ---- Sub-tabs row: Ответ / Ссылки / Изображения ----
        subTabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        root.addView(subTabRow)
        addDivider()

        // ---- AI row (only shown for the "Ответ" sub-tab) ----
        aiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(aiRow)

        // ---- Progress bar ----
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        // ---- Content container that holds the currently visible WebView ----
        contentContainer = android.widget.FrameLayout(this)
        root.addView(contentContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun addDivider() {
        val divider = View(this).apply { setBackgroundColor(Color.parseColor("#E0E0E0")) }
        root.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun pillDrawable(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(24).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun pillButton(text: String, onClick: (View) -> Unit): TextView = TextView(this).apply {
        this.text = text
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = pillDrawable(Color.parseColor("#F1F3F4"), Color.parseColor("#DADCE0"))
        setTextColor(Color.parseColor("#202124"))
        setOnClickListener { onClick(it) }
    }

    // endregion

    // region ---------- WebView creation ----------

    @SuppressLint("SetJavaScriptEnabled")
    fun createWebView(): WebView {
        val wv = WebView(this)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // Strip the "; wv" WebView marker some sites use to detect (and block) embedded webviews.
            userAgentString = userAgentString.replace("; wv", "")
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (view === currentVisibleWebView()) {
                    progressBar.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view === currentVisibleWebView()) {
                    progressBar.visibility = View.GONE
                }
                view?.let { updateTabTitleFromWebView(it) }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (view === currentVisibleWebView()) {
                    progressBar.progress = newProgress
                }
            }

            // Handles target="_blank" links (e.g. clicking a result) by opening them in a new tab.
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val newTab = addNewTab(switchToIt = true)
                newTab.currentSubTab = SubTab.LINKS
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newTab.linksWebView
                resultMsg?.sendToTarget()
                renderTabStrip()
                renderSubTabsAndContent()
                return true
            }
        }
        return wv
    }

    private fun currentVisibleWebView(): WebView? = currentTab()?.webViewFor(currentTab()!!.currentSubTab)

    private fun updateTabTitleFromWebView(view: WebView) {
        val tab = tabs.find { it.answerWebView === view || it.linksWebView === view || it.imagesWebView === view } ?: return
        if (tab.lastQuery.isBlank() && !view.title.isNullOrBlank()) {
            tab.title = view.title!!.take(24)
            renderTabStrip()
        }
    }

    // endregion

    // region ---------- Tab management ----------

    private fun currentTab(): BrowserTab? = tabs.getOrNull(currentTabIndex)

    private fun addNewTab(switchToIt: Boolean): BrowserTab {
        val tab = BrowserTab(nextTabId++, createWebView(), createWebView(), createWebView())
        tabs.add(tab)
        if (switchToIt) {
            currentTabIndex = tabs.size - 1
        }
        renderTabStrip()
        if (switchToIt) renderSubTabsAndContent()
        return tab
    }

    private fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            addNewTab(switchToIt = true)
            return
        }
        currentTabIndex = currentTabIndex.coerceIn(0, tabs.size - 1)
        renderTabStrip()
        renderSubTabsAndContent()
    }

    private fun switchToTab(index: Int) {
        if (index == currentTabIndex) return
        currentTabIndex = index
        renderTabStrip()
        renderSubTabsAndContent()
    }

    @SuppressLint("SetTextI18n")
    private fun renderTabStrip() {
        tabStrip.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = pillDrawable(
                    if (index == currentTabIndex) Color.parseColor("#D2E3FC") else Color.parseColor("#F1F3F4"),
                    Color.parseColor("#DADCE0")
                )
                setPadding(dp(12), dp(6), dp(6), dp(6))
                setOnClickListener { switchToTab(index) }
            }
            val title = TextView(this).apply {
                text = tab.title
                maxLines = 1
                setTextColor(Color.parseColor("#202124"))
            }
            val close = TextView(this).apply {
                text = " ✕ "
                setTextColor(Color.parseColor("#5F6368"))
                setOnClickListener { closeTab(index) }
            }
            chip.addView(title)
            chip.addView(close)
            tabStrip.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        val addButton = TextView(this).apply {
            text = " + "
            textSize = 18f
            background = pillDrawable(Color.WHITE, Color.parseColor("#DADCE0"))
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener { addNewTab(switchToIt = true) }
        }
        tabStrip.addView(addButton)
    }

    // endregion

    // region ---------- Rendering the search-engine / AI / sub-tab controls + content ----------

    @SuppressLint("SetTextI18n")
    private fun renderSubTabsAndContent() {
        val tab = currentTab() ?: return

        // engine label
        engineLabel.text = tab.searchEngine.label
        searchInput.setText(tab.lastQuery)

        // sub-tabs
        subTabRow.removeAllViews()
        SubTab.values().forEach { st ->
            val selected = st == tab.currentSubTab
            val tv = TextView(this).apply {
                text = st.label
                setTextColor(if (selected) Color.parseColor("#1A73E8") else Color.parseColor("#5F6368"))
                setTypeface(typeface, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                setOnClickListener {
                    tab.currentSubTab = st
                    tab.refreshCurrentSubTab()
                    renderSubTabsAndContent()
                }
            }
            subTabRow.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        // AI row only for the "Ответ" tab
        aiRow.removeAllViews()
        aiRow.visibility = if (tab.currentSubTab == SubTab.ANSWER) View.VISIBLE else View.GONE
        if (tab.currentSubTab == SubTab.ANSWER) {
            val switchAiLink = TextView(this).apply {
                text = "сменить ИИ"
                setTextColor(Color.parseColor("#1A73E8"))
                setOnClickListener {
                    tab.aiService = AiService.next(tab.aiService)
                    tab.answerWebView.loadUrl(if (tab.lastQuery.isNotBlank()) tab.aiService.queryUrl(tab.lastQuery) else tab.aiService.homeUrl())
                    renderSubTabsAndContent()
                }
            }
            aiRow.addView(switchAiLink)
            AiService.values().forEach { ai ->
                val chip = TextView(this).apply {
                    text = ai.label
                    val selected = ai == tab.aiService
                    background = pillDrawable(
                        if (selected) Color.parseColor("#1A73E8") else Color.WHITE,
                        Color.parseColor("#DADCE0")
                    )
                    setTextColor(if (selected) Color.WHITE else Color.parseColor("#202124"))
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    setOnClickListener {
                        tab.aiService = ai
                        tab.answerWebView.loadUrl(if (tab.lastQuery.isNotBlank()) ai.queryUrl(tab.lastQuery) else ai.homeUrl())
                        renderSubTabsAndContent()
                    }
                }
                aiRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
            }
        }

        // content: show the correct webview, detaching it from any previous parent first
        val wv = tab.webViewFor(tab.currentSubTab)
        contentContainer.removeAllViews()
        (wv.parent as? ViewGroup)?.removeView(wv)
        contentContainer.addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tab.refreshCurrentSubTab()
    }

    private fun showEnginePopup(anchor: View) {
        val tab = currentTab() ?: return
        val popup = PopupMenu(this, anchor)
        SearchEngine.values().forEach { popup.menu.add(it.label) }
        popup.setOnMenuItemClickListener { item ->
            val chosen = SearchEngine.values().first { it.label == item.title }
            tab.searchEngine = chosen
            if (tab.currentSubTab != SubTab.ANSWER) {
                tab.webViewFor(tab.currentSubTab).loadUrl(
                    if (tab.currentSubTab == SubTab.LINKS) chosen.searchUrl(tab.lastQuery.ifBlank { "" })
                    else chosen.imagesUrl(tab.lastQuery.ifBlank { "" })
                )
            }
            renderSubTabsAndContent()
            true
        }
        popup.show()
    }

    private fun cycleEngine() {
        val tab = currentTab() ?: return
        tab.searchEngine = SearchEngine.next(tab.searchEngine)
        if (tab.currentSubTab != SubTab.ANSWER && tab.lastQuery.isNotBlank()) {
            tab.runSearch(tab.lastQuery)
        }
        renderSubTabsAndContent()
    }

    private fun performSearch() {
        val tab = currentTab() ?: return
        val query = searchInput.text.toString().trim()
        if (query.isEmpty()) return
        tab.runSearch(query)
        renderTabStrip()
    }

    // endregion

    override fun onBackPressed() {
        val wv = currentVisibleWebView()
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
