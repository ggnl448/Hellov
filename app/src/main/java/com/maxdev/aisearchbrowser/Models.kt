package com.maxdev.aisearchbrowser

import java.net.URLEncoder

/** The four switchable search engines used by the "Ссылки" and "Изображения" tabs. */
enum class SearchEngine(val label: String) {
    GOOGLE("Google"),
    BING("Bing"),
    DUCKDUCKGO("DuckDuckGo"),
    YANDEX("Yandex");

    fun searchUrl(query: String): String {
        val q = encode(query)
        return when (this) {
            GOOGLE -> "https://www.google.com/search?q=$q"
            BING -> "https://www.bing.com/search?q=$q"
            DUCKDUCKGO -> "https://duckduckgo.com/?q=$q"
            YANDEX -> "https://yandex.ru/search/?text=$q"
        }
    }

    fun imagesUrl(query: String): String {
        val q = encode(query)
        return when (this) {
            GOOGLE -> "https://www.google.com/search?tbm=isch&q=$q"
            BING -> "https://www.bing.com/images/search?q=$q"
            DUCKDUCKGO -> "https://duckduckgo.com/?q=$q&iax=images&ia=images"
            YANDEX -> "https://yandex.ru/images/search?text=$q"
        }
    }

    /** Home page shown before the user has typed a query. */
    fun homeUrl(): String = when (this) {
        GOOGLE -> "https://www.google.com"
        BING -> "https://www.bing.com"
        DUCKDUCKGO -> "https://duckduckgo.com"
        YANDEX -> "https://yandex.ru"
    }

    companion object {
        fun next(current: SearchEngine): SearchEngine {
            val v = values()
            return v[(current.ordinal + 1) % v.size]
        }
    }
}

/** The three switchable AI assistants used by the "Ответ" tab. */
enum class AiService(val label: String) {
    GEMINI("Gemini"),
    CHATGPT("ChatGPT.com"),
    CLAUDE("Claude.ai");

    fun homeUrl(): String = when (this) {
        GEMINI -> "https://gemini.google.com/app"
        CHATGPT -> "https://chatgpt.com/"
        CLAUDE -> "https://claude.ai/new"
    }

    /**
     * Not every AI site accepts a pre-filled query in the URL.
     * Where it's not supported we just open the home page and the
     * webview's own page will already have the query in the clipboard-free
     * text box for the user to paste/type, matching the original web app's behavior.
     */
    fun queryUrl(query: String): String = when (this) {
        CHATGPT -> "https://chatgpt.com/?q=${encode(query)}"
        GEMINI -> homeUrl()
        CLAUDE -> homeUrl()
    }

    companion object {
        fun next(current: AiService): AiService {
            val v = values()
            return v[(current.ordinal + 1) % v.size]
        }
    }
}

enum class SubTab(val label: String) {
    ANSWER("Ответ"),
    LINKS("Ссылки"),
    IMAGES("Изображения")
}

private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
