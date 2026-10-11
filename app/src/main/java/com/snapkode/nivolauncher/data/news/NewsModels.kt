package com.snapkode.nivolauncher.data.news

import com.snapkode.nivolauncher.data.NewsTopic

/** Roughly what a source is about: used to group the list of sources. */
enum class NewsCategory(val label: String) {
    General("General"), India("India"), Tech("Technology"), Sports("Sports"), Science("Science"),
}

/**
 * A news source the user can switch on. [feeds] holds one public RSS/Atom address per topic it offers; [NewsTopic.Top] is
 * always there, and a source with no feed for a topic simply contributes nothing to that topic.
 */
class NewsProvider(
    val id: String,
    val name: String,
    val about: String,
    val category: NewsCategory,
    val home: String,
    val feeds: Map<NewsTopic, String>,
) {
    val topics: Set<NewsTopic> get() = feeds.keys
}

/** A feed the user typed in. [name] is what the cards show as the source. */
data class NewsCustomFeed(val name: String, val url: String)

enum class NewsStyle(val label: String) { Cards("Picture cards"), Compact("Compact list"), Headlines("Headlines only") }

enum class NewsOrder(val label: String, val about: String) {
    Newest("Newest first", "Strictly by time, whichever source it came from."),
    Balanced("Balanced", "Takes turns between your sources so no single one fills the page."),
}

/** Everything the user can change about the News page, in one value. */
data class NewsPrefs(
    val providers: Set<String> = setOf("bbc"),
    val custom: List<NewsCustomFeed> = emptyList(),
    val style: NewsStyle = NewsStyle.Cards,
    val images: Boolean = true,
    /** The most stories one source may contribute to the list. */
    val perSource: Int = 10,
    val order: NewsOrder = NewsOrder.Balanced,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("providers", org.json.JSONArray(providers.sorted()))
        .put("custom", org.json.JSONArray().also { a -> custom.forEach { a.put(org.json.JSONObject().put("name", it.name).put("url", it.url)) } })
        .put("style", style.name)
        .put("images", images)
        .put("perSource", perSource)
        .put("order", order.name)

    companion object {
        val PerSourceChoices = listOf(3, 5, 10, 20)

        /** Forgiving: anything missing or unreadable falls back to [d]. */
        fun fromJson(o: org.json.JSONObject?, d: NewsPrefs = NewsPrefs()): NewsPrefs {
            if (o == null) return d
            return NewsPrefs(
                providers = o.optJSONArray("providers")?.let { a -> List(a.length()) { a.optString(it) }.toSet() } ?: d.providers,
                custom = o.optJSONArray("custom")?.let { a ->
                    List(a.length()) { a.optJSONObject(it) }.mapNotNull { f ->
                        val name = f?.optString("name").orEmpty(); val url = f?.optString("url").orEmpty()
                        if (name.isBlank() || url.isBlank()) null else NewsCustomFeed(name, url)
                    }
                } ?: d.custom,
                style = NewsStyle.entries.firstOrNull { it.name == o.optString("style") } ?: d.style,
                images = o.optBoolean("images", d.images),
                perSource = o.optInt("perSource", d.perSource).coerceIn(1, 50),
                order = NewsOrder.entries.firstOrNull { it.name == o.optString("order") } ?: d.order,
            )
        }
    }
}

/** One address to fetch, remembering whose it is. */
data class NewsFeedRef(val sourceName: String, val url: String)
