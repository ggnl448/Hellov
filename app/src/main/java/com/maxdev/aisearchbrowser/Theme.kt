package com.maxdev.aisearchbrowser

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** The set of colors the whole app chrome (not webview content) is painted with. */
data class Palette(
    val background: Int,
    val surface: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val chipBg: Int,
    val chipBorder: Int,
    val chipSelectedBg: Int,
    val divider: Int,
    val accent: Int
)

val LightPalette = Palette(
    background = Color.WHITE,
    surface = Color.WHITE,
    textPrimary = Color.parseColor("#202124"),
    textSecondary = Color.parseColor("#5F6368"),
    chipBg = Color.parseColor("#F1F3F4"),
    chipBorder = Color.parseColor("#DADCE0"),
    chipSelectedBg = Color.parseColor("#D2E3FC"),
    divider = Color.parseColor("#E0E0E0"),
    accent = Color.parseColor("#1A73E8")
)

val DarkPalette = Palette(
    background = Color.parseColor("#202124"),
    surface = Color.parseColor("#2D2E30"),
    textPrimary = Color.parseColor("#E8EAED"),
    textSecondary = Color.parseColor("#9AA0A6"),
    chipBg = Color.parseColor("#3C4043"),
    chipBorder = Color.parseColor("#5F6368"),
    chipSelectedBg = Color.parseColor("#394457"),
    divider = Color.parseColor("#3C4043"),
    accent = Color.parseColor("#8AB4F8")
)

enum class ThemeMode(val label: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

private const val PREFS_NAME = "settings"
private const val KEY_THEME_MODE = "theme_mode"

object ThemePrefs {
    fun getMode(context: Context): ThemeMode {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(stored ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun setMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun resolvePalette(context: Context): Palette {
        val mode = getMode(context)
        val isDark = when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
        return if (isDark) DarkPalette else LightPalette
    }
}

/** Brand-ish accent colors used only for small circular badges (never real logos/wordmarks). */
fun searchEngineAccent(engine: SearchEngine): Pair<Int, String> = when (engine) {
    SearchEngine.GOOGLE -> Color.parseColor("#4285F4") to "G"
    SearchEngine.BING -> Color.parseColor("#00809D") to "B"
    SearchEngine.DUCKDUCKGO -> Color.parseColor("#DE5833") to "D"
    SearchEngine.YANDEX -> Color.parseColor("#FC3F1D") to "Y"
}

/** Expected drawable filename (without extension) if the user drops in a real logo — see README. */
fun searchEngineDrawableName(engine: SearchEngine): String = when (engine) {
    SearchEngine.GOOGLE -> "ic_google"
    SearchEngine.BING -> "ic_bing"
    SearchEngine.DUCKDUCKGO -> "ic_duckduckgo"
    SearchEngine.YANDEX -> "ic_yandex"
}

fun aiServiceDrawableName(ai: AiService): String = when (ai) {
    AiService.GEMINI -> "ic_gemini"
    AiService.CHATGPT -> "ic_chatgpt"
    AiService.CLAUDE -> "ic_claude"
    AiService.GROK -> "ic_grok"
    AiService.COPILOT -> "ic_copilot"
}

fun aiServiceAccent(ai: AiService): Pair<Int, String> = when (ai) {
    AiService.GEMINI -> Color.parseColor("#4285F4") to "Ge"
    AiService.CHATGPT -> Color.parseColor("#10A37F") to "Ch"
    AiService.CLAUDE -> Color.parseColor("#DA7756") to "Cl"
    AiService.GROK -> Color.parseColor("#1A1A1A") to "Gr"
    AiService.COPILOT -> Color.parseColor("#0078D4") to "Co"
}
