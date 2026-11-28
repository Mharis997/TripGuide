package com.tripguide.app

/** An Indian (or Latin) writing system, with its OCR pack and speech locale. */
data class Script(val language: String, val range: IntRange, val ocr: String, val speech: String)

/** What the offline rules found in a reel's caption, on-screen text, speech and (optionally) the user's AI reply. */
data class Extracted(
    val languages: List<String> = emptyList(),
    val placeName: String = "",
    val category: String? = null,
    val vibes: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val summary: String = "",
    val tips: List<String> = emptyList(),
    val prices: List<String> = emptyList(),
    val times: List<String> = emptyList(),
    val phones: List<String> = emptyList(),
    val distances: List<String> = emptyList(),
    val months: List<Int> = emptyList(),
    val hashtags: List<String> = emptyList(),
)

/** Rule-based, offline extraction. Works on English and Indian-language text; no AI. */
object Extract {
    val scripts = listOf(
        Script("Malayalam", 0x0D00..0x0D7F, "mal", "ml-IN"),
        Script("Tamil", 0x0B80..0x0BFF, "tam", "ta-IN"),
        Script("Telugu", 0x0C00..0x0C7F, "tel", "te-IN"),
        Script("Kannada", 0x0C80..0x0CFF, "kan", "kn-IN"),
        Script("Hindi / Marathi", 0x0900..0x097F, "hin", "hi-IN"),
        Script("Bengali", 0x0980..0x09FF, "ben", "bn-IN"),
        Script("Gujarati", 0x0A80..0x0AFF, "guj", "gu-IN"),
        Script("Punjabi", 0x0A00..0x0A7F, "pan", "pa-IN"),
        Script("Odia", 0x0B00..0x0B7F, "ori", "or-IN"),
        Script("Urdu", 0x0600..0x06FF, "urd", "ur-IN"),
        Script("English", 0x0041..0x007A, "eng", "en-IN"),
    )

    /** Scripts present in [text], most used first. A handful of letters is enough (captions mix languages). */
    fun scriptsIn(text: String): List<Script> {
        val counts = scripts.associateWith { s -> text.count { it.isLetter() && it.code in s.range } }
        return counts.filter { it.value >= 3 }.entries.sortedByDescending { it.value }.map { it.key }
    }

    // Words for "rupees" in Indian languages: Malayalam, Hindi (2 spellings), Tamil, Telugu, Kannada, Bengali.
    private const val RUPEE_WORDS = "rupees?|രൂപ|रुपये|रुपए|ரூபாய்|రూపాయలు|ರೂಪಾಯಿ|টাকা"
    private val price = Regex("""(?:₹|\brs\.?|\binr)\s?(\d[\d,]*(?:\.\d+)?)\s?(k\b)?|(\d[\d,]*)\s?(?:$RUPEE_WORDS)""", RegexOption.IGNORE_CASE)
    private const val T = """\d{1,2}(?:[:.]\d{2})?\s?(?:am|pm|a\.m\.|p\.m\.)"""
    private val time = Regex("""\b$T(?:\s?(?:-|–|to)\s?$T)?|\d{1,2}(?:[:.]\d{2})?\s?(?:മണി|बजे|மணி)""", RegexOption.IGNORE_CASE)
    private val phone = Regex("""(?<!\d)(?:\+91[\s-]?|0)?([6-9]\d{4})[\s-]?(\d{5})(?!\d)""")
    private val distance = Regex("""\b\d+(?:\.\d+)?\s?(?:km|kms|kilometers?|കി\.?മീ)""", RegexOption.IGNORE_CASE)
    private val hashtag = Regex("""#([\p{L}\p{M}\p{N}_]+)""")
    private val monthWords = listOf(
        listOf("jan", "ജനുവരി", "जनवरी"), listOf("feb", "ഫെബ്രുവരി", "फरवरी"), listOf("mar", "മാർച്ച്", "मार्च"),
        listOf("apr", "ഏപ്രിൽ", "अप्रैल"), listOf("may", "മേയ്", "മെയ്", "मई"), listOf("jun", "ജൂൺ", "जून"),
        listOf("jul", "ജൂലൈ", "जुलाई"), listOf("aug", "ഓഗസ്റ്റ്", "अगस्त"), listOf("sep", "സെപ്റ്റംബർ", "सितंबर"),
        listOf("oct", "ഒക്ടോബർ", "अक्टूबर"), listOf("nov", "നവംബർ", "नवंबर"), listOf("dec", "ഡിസംബർ", "दिसंबर"),
    )
    // Tip-like lines: advice words in English, Malayalam and Hindi, or a leading pointer emoji.
    private val tipWords = listOf(
        "tip", "avoid", "carry", "bring", "don't", "dont", "do not", "must", "best time", "go early", "early morning", "note",
        "recommend", "wear", "book", "closed", "open", "timing", "entry", "ticket", "parking", "careful", "beware", "only",
        "ശ്രദ്ധിക്കുക", "ഒഴിവാക്കുക", "നിർബന്ധം", "ടിക്കറ്റ്", "പാർക്കിംഗ്", "സമയം", "രാവിലെ", "കൊണ്ടുപോകുക",
        "ध्यान", "टिकट", "समय", "सुबह", "जरूर",
    )
    private val pointers = listOf("📍", "💡", "⚠", "✅", "👉", "⏰", "💰", "🚗", "🎟", "🅿", "📌", "⭐", "➡", "•", "-", "*")

    private val categoryWords = mapOf(
        "Beach" to listOf("beach", "sea shore", "ബീച്ച്", "കടപ്പുറം", "കടൽത്തീരം", "கடற்கரை", "बीच", "समुद्र तट"),
        "Backwaters" to listOf("backwater", "houseboat", "kayal", "കായൽ", "ഹൗസ്ബോട്ട്", "കെട്ടുവള്ളം"),
        "Waterfall" to listOf("waterfall", "falls", "വെള്ളച്ചാട്ടം", "அருவி", "जलप्रपात", "झरना"),
        "Mountains" to listOf("hill", "peak", "mountain", "hilltop", "മല", "കുന്ന്", "മലനിര", "மலை", "पहाड़"),
        "Wildlife" to listOf("wildlife", "sanctuary", "safari", "national park", "tiger reserve", "വന്യജീവി", "ആന", "पक्षी"),
        "Lake" to listOf("lake", "dam", "reservoir", "തടാകം", "ഡാം", "അണക്കെട്ട്", "ஏரி", "झील"),
        "Forest" to listOf("forest", "jungle", "കാട്", "വനം", "காடு", "जंगल"),
        "Historical" to listOf("fort", "palace", "museum", "heritage", "കോട്ട", "കൊട്ടാരം", "മ്യൂസിയം", "किला", "महल"),
        "Religious" to listOf("temple", "church", "mosque", "shrine", "ക്ഷേത്രം", "അമ്പലം", "പള്ളി", "கோயில்", "मंदिर"),
        "City" to listOf("city", "street food", "mall", "നഗരം", "शहर"),
        "Adventure" to listOf("zipline", "paragliding", "rafting", "adventure", "rock climbing", "സാഹസിക"),
    )
    private val vibeWords = mapOf(
        "Beaches" to listOf("beach", "ബീച്ച്", "கடற்கரை", "बीच"),
        "Mountains" to listOf("hill", "mountain", "peak", "മല", "மலை", "पहाड़"),
        "Nature" to listOf("nature", "greenery", "waterfall", "forest", "പ്രകൃതി", "പച്ചപ്പ്", "இயற்கை", "प्रकृति"),
        "Chill" to listOf("chill", "relax", "peaceful", "calm", "quiet", "ശാന്ത", "സമാധാന"),
        "Party" to listOf("party", "dj", "nightlife", "pub", "പാർട്ടി"),
        "Adventure" to listOf("trek", "adventure", "offroad", "zipline", "rafting", "ട്രെക്കിംഗ്", "സാഹസിക"),
        "Romantic" to listOf("couple", "honeymoon", "romantic", "candle light"),
        "Family" to listOf("family", "kids", "children", "കുടുംബ", "കുട്ടികൾ", "परिवार"),
        "Culture" to listOf("culture", "heritage", "festival", "theyyam", "kathakali", "ഉത്സവം", "തെയ്യം", "പൂരം"),
        "Food" to listOf("food", "restaurant", "biryani", "toddy", "meals", "seafood", "ഭക്ഷണം", "ഊണ്", "ബിരിയാണി", "കള്ള്"),
        "Photography" to listOf("photo", "viewpoint", "view point", "sunrise", "sunset", "drone", "സൂര്യോദയം", "അസ്തമയം"),
        "Road trip" to listOf("road trip", "roadtrip", "drive", "bike ride", "ride"),
        "Spiritual" to listOf("temple", "pilgrimage", "church", "ക്ഷേത്രം", "मंदिर"),
        "Wellness" to listOf("ayurveda", "yoga", "spa", "massage", "ആയുർവേദ"),
        "Just escape" to listOf("escape", "offbeat", "hidden gem", "unexplored", "less crowded"),
    )
    private val amenityWords = mapOf(
        "Parking" to listOf("parking", "പാർക്കിംഗ്", "पार्किंग"),
        "Restrooms" to listOf("toilet", "restroom", "washroom", "ടോയ്‌ലറ്റ്", "ശുചിമുറി", "शौचालय"),
        "Restaurants" to listOf("restaurant", "cafe", "eatery", "food stall", "തട്ടുകട", "ഹോട്ടൽ"),
        "Hotels" to listOf("homestay", "resort", "stay", "rooms", "cottage", "താമസം", "റിസോർട്ട്"),
        "Camping" to listOf("camping", "tent", "campsite", "ക്യാമ്പ്"),
        "Mobile signal" to listOf("signal", "network coverage"),
        "Internet" to listOf("wifi", "wi-fi"),
        "ATM" to listOf("atm"),
        "Fuel station" to listOf("petrol pump", "fuel station", "petrol bunk"),
        "Medical aid" to listOf("hospital", "first aid", "clinic"),
        "Wheelchair access" to listOf("wheelchair"),
        "Kid friendly" to listOf("kids", "children", "കുട്ടികൾ"),
        "Pet friendly" to listOf("pet friendly", "dogs allowed"),
        "Local guides" to listOf("guide available", "local guide", "guided"),
        "4x4 jeep needed" to listOf("jeep", "4x4", "offroad", "off-road", "ജീപ്പ്"),
        "Entry ticket" to listOf("ticket", "entry fee", "entrance fee", "ടിക്കറ്റ്", "प्रवेश शुल्क", "टिकट"),
    )
    private val genericTags = setOf("kerala", "keralatourism", "godsowncountry", "travel", "travelgram", "india", "incredibleindia", "reels",
        "reelsinstagram", "explore", "explorepage", "instagood", "trending", "viral", "nature", "naturelovers", "photography", "mallu", "keralagram")

    private fun ascii(s: String) = s.all { it.code < 128 }

    // Old-style Malayalam chillu letters (consonant + virama + ZWJ), as OCR and older keyboards write them → modern atomic letters.
    private val chillus = mapOf("ണ്\u200D" to "ൺ", "ന്\u200D" to "ൻ", "ര്\u200D" to "ർ", "ല്\u200D" to "ൽ", "ള്\u200D" to "ൾ", "ക്\u200D" to "ൿ")

    /** One spelling for the same words: modern Malayalam chillus, no invisible joiners, no NBSP. */
    fun normalize(text: String) = chillus.entries.fold(text) { t, (old, new) -> t.replace(old, new) }
        .replace("\u200C", "").replace("\u200D", "").replace('\u00A0', ' ')

    /** OCR line worth keeping: confident enough and not a 1–2 character fragment of background texture. */
    fun ocrLineOk(line: String, confidence: Float) = confidence >= 55f && line.count { it.isLetterOrDigit() } >= 3

    /** Keyword match: word-boundary for English, substring for Indian scripts; "no X" / "X ഇല്ല" (Malayalam "not available") negate. */
    fun mentions(text: String, kw: String): Boolean {
        val lower = text.lowercase()
        if (ascii(kw)) {
            val hit = Regex("""\b${Regex.escape(kw)}""", RegexOption.IGNORE_CASE).findAll(lower).toList()
            return hit.any { m -> !Regex("""\b(no|not|without|zero)\s+(\w+\s+)?$""").containsMatchIn(lower.substring(maxOf(0, m.range.first - 20), m.range.first)) }
        }
        var i = lower.indexOf(kw)
        while (i >= 0) {
            val after = lower.substring(i + kw.length, minOf(lower.length, i + kw.length + 12))
            if (!after.contains("ഇല്ല") && !after.contains("नहीं")) return true
            i = lower.indexOf(kw, i + 1)
        }
        return false
    }

    private fun normPrice(m: MatchResult): String {
        val v = (m.groups[1]?.value ?: m.groups[3]?.value ?: return m.value).replace(",", "")
        val n = v.toDoubleOrNull() ?: return m.value
        val rupees = if (m.groups[2] != null) n * 1000 else n
        return "₹" + "%,d".format(rupees.toLong())
    }

    private fun lines(text: String) = text.split(Regex("""\n+|(?<=[.!?।])\s+""")).map { it.trim() }.filter { it.isNotEmpty() }

    /** Months mentioned, with ranges filled in ("Oct to March", "ഡിസംബർ - ഫെബ്രുവരി" → wraps around the year). */
    fun months(text: String): List<Int> {
        val lower = text.lowercase()
        val hits = monthWords.flatMapIndexed { i, words ->
            words.flatMap { w ->
                if (ascii(w)) Regex("""\b$w[a-z]*\b""").findAll(lower).filterNot { w == "may" && Regex("""^may\s+(be|not|have|get|need)\b""").containsMatchIn(lower.substring(it.range.first)) }
                    .map { it.range to i + 1 }.toList()
                else Regex(Regex.escape(w)).findAll(lower).map { it.range to i + 1 }.toList()
            }
        }.sortedBy { it.first.first }
        val out = hits.map { it.second }.toMutableSet()
        hits.zipWithNext().forEach { (a, b) ->
            if (Regex("""^\s*(-|–|to|till|until|through|മുതൽ|से)\s*$""").matches(lower.substring(a.first.last + 1, b.first.first))) {
                var m = a.second
                while (m != b.second) { out += m; m = m % 12 + 1 }
            }
        }
        if (listOf("monsoon", "മഴക്കാല", "मानसून").any { lower.contains(it) }) out += listOf(6, 7, 8, 9)
        return out.sorted()
    }

    /** Runs every rule over the combined text. [places] lets the place name match known places, including Malayalam names. */
    fun from(text: String, places: List<Place> = emptyList()): Extracted {
        val clean = normalize(text)
        val tags = hashtag.findAll(clean).map { it.groupValues[1] }.distinct().toList()
        val noTags = clean.replace(hashtag, "").replace(Regex("""@[\w.]+"""), "")
        val ls = lines(noTags)
        fun pick(dict: Map<String, List<String>>) = dict.filter { (_, kws) -> kws.any { mentions(clean, it) } }.keys.toList()
        val catScores = categoryWords.mapValues { (_, kws) -> kws.count { mentions(clean, it) } }.filter { it.value > 0 }
        val matched = matchPlace(places, clean, tags)
        val pin = ls.firstOrNull { it.startsWith("📍") }?.removePrefix("📍")?.trim()?.take(80)
        return Extracted(
            languages = scriptsIn(clean).map { it.language },
            placeName = matched?.name ?: pin ?: tags.firstOrNull { it.lowercase() !in genericTags && ascii(it) }?.let(::fromTag).orEmpty(),
            category = catScores.maxByOrNull { it.value }?.key,
            vibes = pick(vibeWords),
            amenities = pick(amenityWords),
            summary = ls.firstOrNull { l -> l.count { it.isLetter() } >= 15 && !l.startsWith("📍") }?.take(300).orEmpty(),
            tips = ls.filter { l -> l.length in 8..220 && (pointers.any { l.startsWith(it) } || tipWords.any { mentions(l, it) } || price.containsMatchIn(l) || time.containsMatchIn(l)) }
                .map { it.trimStart(*"•-*➡ ".toCharArray()) }.distinctBy { it.lowercase() }.take(12),
            prices = price.findAll(clean).map(::normPrice).distinct().toList() + if (Regex("""free entry|entry (is )?free|no entry fee|സൗജന്യ""", RegexOption.IGNORE_CASE).containsMatchIn(clean)) listOf("Free entry") else emptyList(),
            times = time.findAll(clean).map { it.value.trim() }.distinct().toList(),
            phones = phone.findAll(clean).map { "+91 ${it.groupValues[1]} ${it.groupValues[2]}" }.distinct().toList(),
            distances = distance.findAll(clean).map { it.value }.distinct().toList(),
            months = months(clean),
            hashtags = tags,
        )
    }

    /** "fortkochi" → "Fortkochi"; camelCase tags split: "VarkalaCliff" → "Varkala Cliff". */
    private fun fromTag(t: String) = t.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").replaceFirstChar { it.uppercase() }

    /** Known place mentioned in the text, by English name, Malayalam name or hashtag; the longest match wins. */
    fun matchPlace(places: List<Place>, text: String, tags: List<String> = emptyList()): Place? {
        val lower = text.lowercase()
        val tagSet = tags.map { it.lowercase() }.toSet()
        fun names(p: Place) = (listOf(p.name) + p.name.split(" & ", " (", ", ").map { it.trim(')', ' ') } + Catalog.localNames[p.name].orEmpty())
            .filter { it.length >= 4 }.distinct()
        return places.flatMap { p -> names(p).map { n -> p to n } }
            .filter { (_, n) -> lower.contains(n.lowercase()) || n.lowercase().replace(" ", "") in tagSet }
            .maxByOrNull { it.second.length }?.first
    }

    /** Combines several extractions (caption, screen text, speech, AI reply) into one. */
    fun merge(parts: List<Extracted>): Extracted {
        fun <T> all(f: (Extracted) -> List<T>) = parts.flatMap(f).distinct()
        return Extracted(
            languages = all { it.languages }, placeName = parts.firstOrNull { it.placeName.isNotBlank() }?.placeName.orEmpty(),
            category = parts.firstNotNullOfOrNull { it.category }, vibes = all { it.vibes }, amenities = all { it.amenities },
            summary = parts.firstOrNull { it.summary.isNotBlank() }?.summary.orEmpty(), tips = all { it.tips }.take(15),
            prices = all { it.prices }, times = all { it.times }, phones = all { it.phones }, distances = all { it.distances },
            months = all { it.months }.sorted(), hashtags = all { it.hashtags },
        )
    }
}

/** What travellers' reels say about a place, combined on the phone: most repeated tips first. */
data class ReelGuide(val count: Int, val tips: List<String>, val prices: List<String>, val times: List<String>, val months: List<Int>,
                     val languages: List<String>, val quotes: List<Pair<String, String>>)

fun reelGuide(reels: List<Reel>): ReelGuide? {
    if (reels.isEmpty()) return null
    fun ranked(f: (Reel) -> List<String>) = reels.flatMap(f).groupBy { it.lowercase().trim() }.values.sortedByDescending { it.size }.map { it.first() }
    return ReelGuide(
        count = reels.size, tips = ranked { it.tips }.take(10), prices = ranked { it.prices }.take(6), times = ranked { it.times }.take(6),
        months = reels.flatMap { it.months }.distinct().sorted(), languages = ranked { it.languages },
        quotes = reels.filter { it.summary.isNotBlank() }.take(3).map { (it.author.ifBlank { "traveller" }) to it.summary },
    )
}

/** Text handed to the user's own AI app ("Ask my AI"). */
fun aiPrompt(place: String, url: String, caption: String, screenText: String, speech: String) = buildString {
    appendLine("I'm planning to visit ${place.ifBlank { "a place in Kerala" }}. Using this Instagram reel" + (if (url.isNotEmpty()) " ($url)" else "") + ",")
    appendLine("please translate anything in Malayalam, Tamil, Hindi or other languages to English and list:")
    appendLine("place name and exact location, what it is, highlights, practical tips, entry fees and prices, opening times, best time to visit, how to get there, and any warnings.")
    if (caption.isNotBlank()) appendLine("\nCaption:\n$caption")
    if (screenText.isNotBlank()) appendLine("\nText seen in the video:\n$screenText")
    if (speech.isNotBlank()) appendLine("\nWhat is said in the video:\n$speech")
}

/** Reel page Open Graph tags (caption + preview), read on the phone. */
object ReelPage {
    fun canonical(raw: String): Pair<String, String>? {
        val u = runCatching { java.net.URI(raw.trim()) }.getOrNull() ?: return null
        if (u.scheme != "https" || u.host?.lowercase()?.removePrefix("www.") != "instagram.com") return null
        val m = Regex("""^/(?:[\w.]+/)?(reel|reels|p|tv)/([A-Za-z0-9_-]{5,40})/?""").find(u.path.orEmpty()) ?: return null
        val kind = if (m.groupValues[1] in setOf("p", "tv")) "p" else "reel"
        return m.groupValues[2] to "https://www.instagram.com/$kind/${m.groupValues[2]}/"
    }

    private fun decode(s: String) = s.replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("&#(\\d+);")) { String(Character.toChars(it.groupValues[1].toInt())) }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { String(Character.toChars(it.groupValues[1].toInt(16))) }
        .replace("&amp;", "&")

    fun tags(html: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        Regex("""<meta\s+[^>]*?(?:property|name)="(og:[\w:]+|description)"[^>]*?content="([^"]*)"""", RegexOption.IGNORE_CASE).findAll(html)
            .forEach { out.putIfAbsent(it.groupValues[1].lowercase(), decode(it.groupValues[2])) }
        Regex("""<meta\s+[^>]*?content="([^"]*)"[^>]*?(?:property|name)="(og:[\w:]+|description)"""", RegexOption.IGNORE_CASE).findAll(html)
            .forEach { out.putIfAbsent(it.groupValues[2].lowercase(), decode(it.groupValues[1])) }
        return out
    }

    fun isShareLink(raw: String) = Regex("""^https://(www\.)?instagram\.com/share/(reel/|p/)?[A-Za-z0-9_-]+/?(\?.*)?$""").matches(raw.trim())

    /** Instagram's og:description: `1,234 likes, 56 comments - user on May 1, 2026: "caption"`. */
    fun captionAndAuthor(tags: Map<String, String>): Pair<String, String> {
        val desc = tags["og:description"] ?: tags["description"] ?: ""
        val quoted = Regex(""":\s*"([\s\S]*)"\s*\.?\s*$""").find(desc)?.groupValues?.get(1)
        val author = Regex("""-\s*([\w.]+)\s+on\s""").find(desc)?.groupValues?.get(1)
            ?: tags["og:title"]?.let { Regex("^(.+?) on Instagram").find(it)?.groupValues?.get(1) } ?: ""
        return (quoted ?: desc).trim() to author
    }
}
