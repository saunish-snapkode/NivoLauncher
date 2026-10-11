package com.snapkode.nivolauncher.data.news

import com.snapkode.nivolauncher.data.NewsTopic
import com.snapkode.nivolauncher.data.NewsTopic.Business
import com.snapkode.nivolauncher.data.NewsTopic.Entertainment
import com.snapkode.nivolauncher.data.NewsTopic.Health
import com.snapkode.nivolauncher.data.NewsTopic.Science
import com.snapkode.nivolauncher.data.NewsTopic.Sports
import com.snapkode.nivolauncher.data.NewsTopic.Technology
import com.snapkode.nivolauncher.data.NewsTopic.Top
import com.snapkode.nivolauncher.data.NewsTopic.World

/** The sources on offer. Only public, official RSS/Atom feeds over HTTPS: no scraping, no accounts, no keys. */
object NewsCatalog {
    private fun bbc(path: String) = "https://feeds.bbci.co.uk/$path"
    private fun npr(n: Int) = "https://feeds.npr.org/$n/rss.xml"
    private fun guardian(path: String) = "https://www.theguardian.com/$path/rss"
    private fun hindu(path: String) = "https://www.thehindu.com/$path/feeder/default.rss"
    private fun toi(id: String) = "https://timesofindia.indiatimes.com/rssfeeds/$id.cms"
    private fun ie(path: String) = "https://indianexpress.com/section/$path/feed/"
    private fun ht(path: String) = "https://www.hindustantimes.com/feeds/rss/$path/rssfeed.xml"

    val providers: List<NewsProvider> = listOf(
        NewsProvider("bbc", "BBC News", "Global news with a picture for every story.", NewsCategory.General, "https://www.bbc.com/news", mapOf(
            Top to bbc("news/rss.xml"), World to bbc("news/world/rss.xml"), Business to bbc("news/business/rss.xml"),
            Technology to bbc("news/technology/rss.xml"), Science to bbc("news/science_and_environment/rss.xml"),
            Sports to bbc("sport/rss.xml"), Entertainment to bbc("news/entertainment_and_arts/rss.xml"), Health to bbc("news/health/rss.xml"),
        )),
        NewsProvider("guardian", "The Guardian", "Independent reporting and long reads from the UK and the world.", NewsCategory.General, "https://www.theguardian.com", mapOf(
            Top to guardian("international"), World to guardian("world"), Business to guardian("business"),
            Technology to guardian("technology"), Science to guardian("science"), Sports to guardian("sport"),
            Entertainment to guardian("culture"), Health to guardian("society/health"),
        )),
        NewsProvider("npr", "NPR", "American public radio: news, analysis and culture.", NewsCategory.General, "https://www.npr.org", mapOf(
            Top to npr(1001), World to npr(1004), Business to npr(1006), Technology to npr(1019), Science to npr(1007),
            Entertainment to npr(1008), Health to npr(1128),
        )),
        NewsProvider("aljazeera", "Al Jazeera", "International news with strong coverage of Asia and the Middle East.", NewsCategory.General, "https://www.aljazeera.com", mapOf(
            Top to "https://www.aljazeera.com/xml/rss/all.xml",
        )),
        NewsProvider("dw", "DW News", "Germany's international broadcaster.", NewsCategory.General, "https://www.dw.com/en", mapOf(
            Top to "https://rss.dw.com/xml/rss-en-top", World to "https://rss.dw.com/xml/rss-en-world",
            Business to "https://rss.dw.com/xml/rss-en-bus",
        )),
        NewsProvider("france24", "France 24", "Round-the-clock international news from Paris.", NewsCategory.General, "https://www.france24.com/en", mapOf(
            Top to "https://www.france24.com/en/rss",
        )),
        NewsProvider("thehindu", "The Hindu", "India's national daily.", NewsCategory.India, "https://www.thehindu.com", mapOf(
            Top to hindu("news/national"), World to hindu("news/international"), Business to hindu("business"),
            Technology to hindu("sci-tech"), Sports to hindu("sport"), Entertainment to hindu("entertainment"),
        )),
        NewsProvider("toi", "Times of India", "India's biggest English-language paper.", NewsCategory.India, "https://timesofindia.indiatimes.com", mapOf(
            Top to "https://timesofindia.indiatimes.com/rssfeedstopstories.cms", World to toi("296589292"), Business to toi("1898055"),
            Technology to toi("66949542"), Sports to toi("4719148"), Entertainment to toi("1081479906"), Science to toi("-2128672765"),
            Health to toi("3908999"),
        )),
        NewsProvider("ndtv", "NDTV", "Indian news channel's headlines.", NewsCategory.India, "https://www.ndtv.com", mapOf(
            Top to "https://feeds.feedburner.com/ndtvnews-top-stories", World to "https://feeds.feedburner.com/ndtvnews-world-news",
            Business to "https://feeds.feedburner.com/ndtvprofit-latest", Technology to "https://feeds.feedburner.com/gadgets360-latest",
            Sports to "https://feeds.feedburner.com/ndtvsports-latest",
        )),
        NewsProvider("indianexpress", "The Indian Express", "Indian news and explainers.", NewsCategory.India, "https://indianexpress.com", mapOf(
            Top to "https://indianexpress.com/feed/", World to ie("world"), Business to ie("business"),
            Technology to ie("technology"), Sports to ie("sports"), Entertainment to ie("entertainment"),
        )),
        NewsProvider("ht", "Hindustan Times", "Indian news, politics and cricket.", NewsCategory.India, "https://www.hindustantimes.com", mapOf(
            Top to ht("india-news"), World to ht("world-news"), Business to ht("business"), Sports to ht("sports"),
            Entertainment to ht("entertainment"),
        )),
        NewsProvider("techcrunch", "TechCrunch", "Startups and the technology industry.", NewsCategory.Tech, "https://techcrunch.com", mapOf(
            Top to "https://techcrunch.com/feed/", Technology to "https://techcrunch.com/feed/",
        )),
        NewsProvider("verge", "The Verge", "Gadgets, software and the culture around them.", NewsCategory.Tech, "https://www.theverge.com", mapOf(
            Top to "https://www.theverge.com/rss/index.xml", Technology to "https://www.theverge.com/rss/index.xml",
        )),
        NewsProvider("ars", "Ars Technica", "In-depth technology and science reporting.", NewsCategory.Tech, "https://arstechnica.com", mapOf(
            Top to "https://feeds.arstechnica.com/arstechnica/index", Technology to "https://feeds.arstechnica.com/arstechnica/technology-lab",
            Science to "https://feeds.arstechnica.com/arstechnica/science",
        )),
        NewsProvider("hn", "Hacker News", "What programmers are reading and discussing.", NewsCategory.Tech, "https://news.ycombinator.com", mapOf(
            Top to "https://hnrss.org/frontpage", Technology to "https://hnrss.org/frontpage",
        )),
        NewsProvider("espn", "ESPN", "Scores and stories across sport.", NewsCategory.Sports, "https://www.espn.com", mapOf(
            Top to "https://www.espn.com/espn/rss/news", Sports to "https://www.espn.com/espn/rss/news",
        )),
        NewsProvider("cricinfo", "ESPNcricinfo", "Cricket news and analysis.", NewsCategory.Sports, "https://www.espncricinfo.com", mapOf(
            Top to "https://www.espncricinfo.com/rss/content/story/feeds/0.xml", Sports to "https://www.espncricinfo.com/rss/content/story/feeds/0.xml",
        )),
        NewsProvider("sciencedaily", "ScienceDaily", "Research news from universities and labs.", NewsCategory.Science, "https://www.sciencedaily.com", mapOf(
            Top to "https://www.sciencedaily.com/rss/top/science.xml", Science to "https://www.sciencedaily.com/rss/all.xml",
            Health to "https://www.sciencedaily.com/rss/health_medicine.xml", Technology to "https://www.sciencedaily.com/rss/computers_math.xml",
        )),
        NewsProvider("nasa", "NASA", "Missions, discoveries and space news.", NewsCategory.Science, "https://www.nasa.gov", mapOf(
            Top to "https://www.nasa.gov/feed/", Science to "https://www.nasa.gov/feed/",
        )),
    )

    private val byId = providers.associateBy { it.id }
    fun find(id: String): NewsProvider? = byId[id]

    /** The addresses to fetch for [topic]: each chosen provider's feed for it (providers without one are skipped), then the user's own feeds under Top. */
    fun plan(prefs: NewsPrefs, topic: NewsTopic): List<NewsFeedRef> {
        val out = ArrayList<NewsFeedRef>()
        for (p in providers) if (p.id in prefs.providers) p.feeds[topic]?.let { out += NewsFeedRef(p.name, it) }
        if (topic == NewsTopic.Top) prefs.custom.forEach { out += NewsFeedRef(it.name, it.url) }
        // Two sources can point at one feed (Top and Technology on a tech site): fetch it once.
        return out.distinctBy { it.url }
    }

    /** Topics at least one chosen source covers, in the usual order: the chips worth showing. */
    fun topicsFor(prefs: NewsPrefs): List<NewsTopic> {
        val covered = providers.filter { it.id in prefs.providers }.flatMapTo(HashSet()) { it.topics }
        if (prefs.custom.isNotEmpty()) covered += NewsTopic.Top
        return NewsTopic.entries.filter { it in covered }.ifEmpty { listOf(NewsTopic.Top) }
    }

    /** "BBC News, The Guardian +2" for a one-line summary. */
    fun summary(prefs: NewsPrefs): String {
        val names = providers.filter { it.id in prefs.providers }.map { it.name } + prefs.custom.map { it.name }
        return when {
            names.isEmpty() -> "No sources chosen"
            names.size <= 2 -> names.joinToString(", ")
            else -> names.take(2).joinToString(", ") + " +${names.size - 2}"
        }
    }
}
