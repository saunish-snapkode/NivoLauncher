package com.snapkode.nivolauncher.data.news

import com.snapkode.nivolauncher.data.NewsItem

/** Combines several sources into one list the way the user asked for. Pure. */
object NewsMerge {
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")

    /** A story's address without the tracking noise, so the same story from two feeds is recognised. */
    fun key(link: String): String =
        link.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore('#').substringBefore('?').trimEnd('/')

    private fun titleKey(t: String) = nonAlnum.replace(t.lowercase(), "")

    /**
     * @param perSource one list per source, each as the feed gave it (usually newest first).
     * @return at most [limit] stories: no more than [NewsPrefs.perSource] from any one source, duplicates removed (the first
     *   source's copy wins), ordered by [NewsPrefs.order].
     */
    fun merge(perSource: List<List<NewsItem>>, prefs: NewsPrefs, limit: Int = 80): List<NewsItem> {
        // 1. Each source: newest first (stories without a date keep their feed order, after dated ones), capped.
        val capped = perSource.map { items ->
            items.withIndex().sortedWith(compareByDescending<IndexedValue<NewsItem>> { it.value.publishedAt }.thenBy { it.index })
                .map { it.value }.take(prefs.perSource.coerceAtLeast(1))
        }.filter { it.isNotEmpty() }

        // 2. Remove stories already seen, by address and by headline.
        val deduped = run {
            val links = HashSet<String>(); val titles = HashSet<String>()
            capped.map { list -> list.filter { val k = key(it.link); val t = titleKey(it.title); if (k in links || t in titles) false else { links += k; titles += t; true } } }
        }.filter { it.isNotEmpty() }

        // 3. Order.
        val ordered = when (prefs.order) {
            NewsOrder.Newest -> deduped.flatten().withIndex()
                .sortedWith(compareByDescending<IndexedValue<NewsItem>> { it.value.publishedAt }.thenBy { it.index }).map { it.value }
            NewsOrder.Balanced -> {
                val out = ArrayList<NewsItem>()
                val rounds = deduped.maxOfOrNull { it.size } ?: 0
                for (r in 0 until rounds) out += deduped.mapNotNull { it.getOrNull(r) }.sortedByDescending { it.publishedAt }
                out
            }
        }
        return ordered.take(limit)
    }
}
