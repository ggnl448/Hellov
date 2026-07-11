package com.maxdev.aisearchbrowser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.google.android.material.bottomsheet.BottomSheetDialog

class MainActivity : AppCompatActivity() {

    private val tabs = mutableListOf<BrowserTab>()
    private var currentTabIndex = -1
    private var nextTabId = 1

    private lateinit var palette: Palette

    // Views that get rebuilt/updated as state changes
    private lateinit var root: LinearLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var engineBadge: TextView
    private lateinit var aiBadge: TextView
    private lateinit var subTabRow: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var contentContainer: FrameLayout
    private lateinit var progressBar: ProgressBar

    // ---- WebView <input type=file> support ----
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@registerForActivityResult
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && data != null) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, data)
        } else null
        callback.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety net: if anything crashes anywhere in the app after this point,
        // show the actual error on screen instead of a silent blank/broken UI or
        // a system "app has stopped" dialog. Screenshot this text and send it over.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                runOnUiThread { showCrashScreen(throwable) }
            } catch (ignored: Throwable) {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            palette = ThemePrefs.resolvePalette(this)
            buildUi()
            if (!restoreTabsState()) {
                addNewTab(switchToIt = true)
            }
        } catch (t: Throwable) {
            showCrashScreen(t)
        }
    }

    override fun onPause() {
        super.onPause()
        saveTabsState()
    }

    // region ---------- Persisting open tabs across app restarts ----------

    private fun tabsPrefs() = getSharedPreferences("tabs_state", MODE_PRIVATE)

    private fun saveTabsState() {
        try {
            val array = org.json.JSONArray()
            tabs.forEach { array.put(it.toJson()) }
            tabsPrefs().edit()
                .putString("tabs", array.toString())
                .putInt("current_index", currentTabIndex)
                .putInt("next_id", nextTabId)
                .apply()
        } catch (t: Throwable) {
            // Saving is best-effort; never crash the app over it.
        }
    }

    /** Returns true if at least one tab was restored. */
    private fun restoreTabsState(): Boolean {
        return try {
            val prefs = tabsPrefs()
            val raw = prefs.getString("tabs", null) ?: return false
            val array = org.json.JSONArray(raw)
            if (array.length() == 0) return false
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val tab = BrowserTab.fromJson(json, createWebView(), createWebView(), createWebView())
                tabs.add(tab)
            }
            nextTabId = prefs.getInt("next_id", tabs.size + 1)
            currentTabIndex = prefs.getInt("current_index", 0).coerceIn(0, tabs.size - 1)
            renderTabStrip()
            renderSubTabsAndContent()
            true
        } catch (t: Throwable) {
            false
        }
    }

    // endregion

    /** Shows the full stack trace as plain, selectable text so it can be read/screenshotted without logcat. */
    private fun showCrashScreen(t: Throwable) {
        val text = TextView(this).apply {
            text = "Произошла ошибка. Скриншот этого текста пришли для диагностики:\n\n" + android.util.Log.getStackTraceString(t)
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(dp(16))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#B00020"))
            addView(text, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(scroll)
    }

    // region ---------- UI construction ----------

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    @SuppressLint("SetTextI18n")
    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        setContentView(root)

        // ---- Tab strip (browser tabs, Chrome-style) + settings gear ----
        val tabStripRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tabStripScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8))
        }
        tabStripScroll.addView(tabStrip)
        val settingsButton = TextView(this).apply {
            text = "⚙"
            textSize = 18f
            setTextColor(palette.textSecondary)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { showSettingsDialog() }
        }
        tabStripRow.addView(tabStripScroll, LinearLayout.LayoutParams(0, dp(48), 1f))
        tabStripRow.addView(settingsButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        root.addView(tabStripRow)

        addDivider()

        // ---- Search box row: engine icon (leading) + input + search button ----
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            minimumHeight = dp(48)
            setBackgroundColor(palette.background)
        }
        engineBadge = circleBadge()
        engineBadge.setOnClickListener { showEnginePicker() }
        searchInput = EditText(this).apply {
            hint = "Задайте вопрос или введите запрос"
            setHintTextColor(palette.textSecondary)
            setTextColor(palette.textPrimary)
            background = pillDrawable(palette.surface, palette.chipBorder)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch(); true
                } else false
            }
        }
        val searchButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            background = pillDrawable(palette.accent, palette.accent)
            setColorFilter(Color.WHITE)
            setOnClickListener { performSearch() }
        }
        searchRow.addView(engineBadge, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(8) })
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchRow.addView(searchButton, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(8) })
        root.addView(searchRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---- Sub-tabs row: Ответ / Ссылки / Изображения + AI icon (trailing) ----
        subTabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
            minimumHeight = dp(40)
            setBackgroundColor(palette.background)
        }
        aiBadge = circleBadge()
        aiBadge.setOnClickListener { showAiPicker() }
        root.addView(subTabRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addDivider()

        // ---- Progress bar ----
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        // ---- Content container that holds the currently visible WebView ----
        contentContainer = FrameLayout(this)
        root.addView(contentContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun addDivider() {
        val divider = View(this).apply { setBackgroundColor(palette.divider) }
        root.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun pillDrawable(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(24).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun circleDrawable(fill: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    /** A small round badge used both for the toolbar icons and inside the picker lists. */
    private fun circleBadge(): TextView = TextView(this).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 14f
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

            // Fixes "file picker doesn't open" for <input type=file> on pages like
            // ChatGPT/Claude/Gemini (attaching files/images) by launching the real
            // Android file picker and returning the chosen file(s) to the webview.
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    pendingFileCallback = null
                    false
                }
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
                    if (index == currentTabIndex) palette.chipSelectedBg else palette.chipBg,
                    palette.chipBorder
                )
                setPadding(dp(12), dp(6), dp(6), dp(6))
                setOnClickListener { switchToTab(index) }
            }
            val title = TextView(this).apply {
                text = tab.title
                maxLines = 1
                setTextColor(palette.textPrimary)
            }
            val close = TextView(this).apply {
                text = " ✕ "
                setTextColor(palette.textSecondary)
                setOnClickListener { closeTab(index) }
            }
            chip.addView(title)
            chip.addView(close)
            tabStrip.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        }
        val addButton = TextView(this).apply {
            text = " + "
            textSize = 18f
            setTextColor(palette.textPrimary)
            background = pillDrawable(palette.surface, palette.chipBorder)
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

        // engine / AI badges
        val (engineColor, engineLetter) = searchEngineAccent(tab.searchEngine)
        engineBadge.background = circleDrawable(engineColor)
        engineBadge.text = engineLetter

        val (aiColor, aiSymbol) = aiServiceAccent(tab.aiService)
        aiBadge.background = circleDrawable(aiColor)
        aiBadge.text = aiSymbol

        searchInput.setText(tab.lastQuery)

        // sub-tabs
        subTabRow.removeAllViews()
        SubTab.values().forEach { st ->
            val selected = st == tab.currentSubTab
            val tv = TextView(this).apply {
                text = st.label
                setTextColor(if (selected) palette.accent else palette.textSecondary)
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
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
        (aiBadge.parent as? ViewGroup)?.removeView(aiBadge)
        subTabRow.addView(aiBadge, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(8) })

        // content: show the correct webview, detaching it from any previous parent first
        val wv = tab.webViewFor(tab.currentSubTab)
        contentContainer.removeAllViews()
        (wv.parent as? ViewGroup)?.removeView(wv)
        contentContainer.addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tab.refreshCurrentSubTab()
    }

    /** Bottom-sheet picker for the search engine (Google / Bing / DuckDuckGo / Yandex). */
    private fun showEnginePicker() {
        val tab = currentTab() ?: return
        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(palette.surface)
        }
        container.addView(TextView(this).apply {
            text = "Поисковая система"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding(0, 0, 0, dp(12))
        })
        SearchEngine.values().forEach { engine ->
            val (color, letter) = searchEngineAccent(engine)
            val selected = engine == tab.searchEngine
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = if (selected) pillDrawable(palette.chipSelectedBg, palette.chipSelectedBg) else null
                setOnClickListener {
                    tab.searchEngine = engine
                    if (tab.currentSubTab != SubTab.ANSWER) {
                        tab.webViewFor(tab.currentSubTab).loadUrl(
                            if (tab.currentSubTab == SubTab.LINKS) engine.searchUrl(tab.lastQuery.ifBlank { "" })
                            else engine.imagesUrl(tab.lastQuery.ifBlank { "" })
                        )
                    }
                    renderSubTabsAndContent()
                    dialog.dismiss()
                }
            }
            row.addView(circleBadge().apply { background = circleDrawable(color); text = letter }, LinearLayout.LayoutParams(dp(36), dp(36)))
            row.addView(TextView(this).apply {
                text = engine.label
                textSize = 16f
                setTextColor(palette.textPrimary)
                setPadding(dp(16), 0, 0, 0)
            })
            container.addView(row)
        }
        dialog.setContentView(container)
        dialog.show()
    }

    /** Bottom-sheet picker for the AI assistant (Gemini / ChatGPT.com / Claude.ai). */
    private fun showAiPicker() {
        val tab = currentTab() ?: return
        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(palette.surface)
        }
        container.addView(TextView(this).apply {
            text = "ИИ-ассистент"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding(0, 0, 0, dp(12))
        })
        AiService.values().forEach { ai ->
            val (color, symbol) = aiServiceAccent(ai)
            val selected = ai == tab.aiService
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = if (selected) pillDrawable(palette.chipSelectedBg, palette.chipSelectedBg) else null
                setOnClickListener {
                    tab.aiService = ai
                    tab.currentSubTab = SubTab.ANSWER
                    tab.answerWebView.loadUrl(if (tab.lastQuery.isNotBlank()) ai.queryUrl(tab.lastQuery) else ai.homeUrl())
                    renderSubTabsAndContent()
                    dialog.dismiss()
                }
            }
            row.addView(circleBadge().apply { background = circleDrawable(color); text = symbol }, LinearLayout.LayoutParams(dp(36), dp(36)))
            row.addView(TextView(this).apply {
                text = ai.label
                textSize = 16f
                setTextColor(palette.textPrimary)
                setPadding(dp(16), 0, 0, 0)
            })
            container.addView(row)
        }
        dialog.setContentView(container)
        dialog.show()
    }

    /** Simple Light / Dark / System theme picker. Rebuilds the toolbar chrome in place, keeping all open tabs and webviews alive. */
    private fun showSettingsDialog() {
        val modes = ThemeMode.values()
        val current = ThemePrefs.getMode(this)
        val labels = modes.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Тема оформления")
            .setSingleChoiceItems(labels, modes.indexOf(current)) { dialog, which ->
                ThemePrefs.setMode(this, modes[which])
                palette = ThemePrefs.resolvePalette(this)
                buildUi()
                renderTabStrip()
                renderSubTabsAndContent()
                dialog.dismiss()
            }
            .setNegativeButton("Закрыть", null)
            .show()
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
