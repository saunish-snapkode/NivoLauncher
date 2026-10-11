package com.snapkode.nivolauncher.data

/** One built-in category. [id] is stable; [name] is only the default and can be renamed by the user. */
data class LibraryCategory(val id: String, val name: String)

/** What the user changed about the automatic organisation. Everything here survives restarts and backups. */
data class LibraryConfig(
    val renamed: Map<String, String> = emptyMap(),
    /** appId -> category id, for apps the user moved by hand. */
    val overrides: Map<String, String> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val order: List<String> = emptyList(),
    /** User-created categories: id -> name. */
    val custom: Map<String, String> = emptyMap(),
)

/** Apps of one category, ready to display. */
data class LibraryGroup(val id: String, val name: String, val apps: List<AppInfo>)

/**
 * Offline, deterministic app classification: Android's own app category first, then keyword rules on
 * the package name and label, then a fallback. No network, no usage data.
 */
object AppClassifier {
    val categories = listOf(
        LibraryCategory("productivity", "Productivity"),
        LibraryCategory("social", "Social"),
        LibraryCategory("communication", "Communication"),
        LibraryCategory("entertainment", "Entertainment"),
        LibraryCategory("photography", "Photography"),
        LibraryCategory("finance", "Finance"),
        LibraryCategory("shopping", "Shopping"),
        LibraryCategory("travel", "Travel"),
        LibraryCategory("games", "Games"),
        LibraryCategory("education", "Education"),
        LibraryCategory("health", "Health & Fitness"),
        LibraryCategory("utilities", "Utilities"),
    )

    // Mirrors ApplicationInfo.CATEGORY_* so this stays unit-testable without the Android framework.
    const val CAT_UNDEFINED = -1
    private const val CAT_GAME = 0
    private const val CAT_AUDIO = 1
    private const val CAT_VIDEO = 2
    private const val CAT_IMAGE = 3
    private const val CAT_SOCIAL = 4
    private const val CAT_NEWS = 5
    private const val CAT_MAPS = 6
    private const val CAT_PRODUCTIVITY = 7

    private val rules = listOf(
        "games" to listOf("game", "games", "puzzle", "chess", "ludo"),
        "finance" to listOf("bank", "phonepe", "paytm", "gpay", "upi", "wallet", "finance", "invest", "stock", "crypto", "hdfc", "icici", "sbi", "axis", "credit", "loan", "insur", "tachyon.pay"),
        "communication" to listOf("whatsapp", "telegram", "signal", "messag", "mms", "sms", "dialer", "contacts", "phone", "gmail", "mail", "outlook", "meet", "zoom", "teams", "skype", "duo", "slack", "chat", "call"),
        "social" to listOf("facebook", "instagram", "twitter", "snapchat", "linkedin", "reddit", "pinterest", "tiktok", "threads", "tumblr", "discord", "mastodon", "social"),
        "photography" to listOf("camera", "photo", "gallery", "lens", "picture", "selfie", "editor"),
        "entertainment" to listOf("youtube", "netflix", "spotify", "music", "video", "prime", "hotstar", "cinema", "player", "radio", "podcast", "tv", "movie", "news", "kindle", "stream"),
        "shopping" to listOf("amazon", "flipkart", "myntra", "blinkit", "zepto", "swiggy", "zomato", "bigbasket", "ebay", "aliexpress", "shop", "cart", "mart", "store.shop", "deal"),
        "travel" to listOf("maps", "uber", "ola", "olacabs", "irctc", "train", "flight", "hotel", "booking", "airbnb", "makemytrip", "goibibo", "redbus", "cab", "metro", "navigat", "travel", "trip", "transit"),
        "education" to listOf("duolingo", "learn", "edu", "school", "course", "coursera", "udemy", "khan", "byju", "dictionary", "translate", "study", "classroom"),
        "health" to listOf("fit", "fitness", "health", "yoga", "meditat", "calm", "workout", "pedometer", "sleep", "medic", "pharma", "wellbeing"),
        "productivity" to listOf("docs", "sheets", "slides", "drive", "calendar", "keep", "notes", "office", "word", "excel", "powerpoint", "notion", "todo", "task", "pdf", "scanner", "tasks", "evernote", "trello"),
    )

    fun classify(packageName: String, label: String, androidCategory: Int = CAT_UNDEFINED): String {
        val haystack = "${packageName.lowercase()} ${label.lowercase()}"
        val tokens = haystack.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        if (androidCategory == CAT_GAME) return "games"
        for ((id, keywords) in rules) {
            if (keywords.any { matches(it, tokens) }) return id
        }
        return when (androidCategory) {
            CAT_AUDIO, CAT_VIDEO, CAT_NEWS -> "entertainment"
            CAT_IMAGE -> "photography"
            CAT_SOCIAL -> "social"
            CAT_MAPS -> "travel"
            CAT_PRODUCTIVITY -> "productivity"
            else -> "utilities"
        }
    }

    /**
     * Long keywords may appear anywhere in a word, four-letter ones must start a word, and very short
     * ones must be a whole word, so "ola" matches the Ola app but not "nivolauncher".
     */
    private fun matches(keyword: String, tokens: List<String>): Boolean = when {
        keyword.length >= 5 -> tokens.any { keyword in it }
        keyword.length == 4 -> tokens.any { it.startsWith(keyword) }
        else -> keyword in tokens
    }

    /** Builds the displayed groups from apps, their automatic categories, and the user's edits. */
    fun group(
        apps: List<AppInfo>,
        automatic: (AppInfo) -> String,
        config: LibraryConfig,
    ): List<LibraryGroup> {
        val names = categories.associate { it.id to (config.renamed[it.id] ?: it.name) } +
            config.custom.mapValues { (id, name) -> config.renamed[id] ?: name }
        val buckets = LinkedHashMap<String, MutableList<AppInfo>>()
        for (app in apps) {
            val id = config.overrides[app.id]?.takeIf { it in names } ?: automatic(app)
            buckets.getOrPut(id) { ArrayList() }.add(app)
        }
        val defaultOrder = categories.map { it.id } + config.custom.keys
        val ordered = (config.order + defaultOrder).distinct()
        return ordered
            .filter { it !in config.hidden }
            .mapNotNull { id ->
                val list = buckets[id]
                // Custom categories stay visible even when empty so the user can fill them.
                if (list == null && id !in config.custom) null
                else LibraryGroup(id, names[id] ?: id, (list ?: emptyList()).sortedBy { it.label.lowercase() })
            }
    }
}
