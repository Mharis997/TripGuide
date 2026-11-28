package com.tripguide.app

import org.junit.Assert.*
import org.junit.Test

class ExtractTest {
    private val places = listOf("Munnar", "Fort Kochi & Mattancherry", "Thekkady (തേക്കടി)", "Athirappilly & Vazhachal Falls", "Varkala")
        .map { Place(name = it) }

    @Test fun detectsIndianScriptsMostUsedFirst() {
        assertEquals(listOf("Malayalam", "English"), Extract.scriptsIn("മൂന്നാറിലെ കൊളുക്കുമല സൂര്യോദയം Sunrise").map { it.language })
        assertEquals(listOf("Tamil"), Extract.scriptsIn("மூணாறு அருவி").map { it.language })
        assertEquals(listOf("Hindi / Marathi"), Extract.scriptsIn("केरल में घूमने की जगह").map { it.language })
        assertEquals(listOf("Kannada", "Telugu"), Extract.scriptsIn("ಕೇರಳ ಪ್ರವಾಸ ತುಂಬಾ ಚೆನ್ನಾಗಿದೆ కేరళ").map { it.language })
        assertTrue(Extract.scriptsIn("ok 👍").isEmpty())
    }

    @Test fun malayalamCaption() {
        val cap = """മൂന്നാറിലെ ഏറ്റവും മനോഹരമായ സൂര്യോദയം കാണാൻ കൊളുക്കുമല പോകൂ 🌄
            |📍 Kolukkumalai, Munnar
            |ജീപ്പ് ചാർജ് 2500 രൂപ, എൻട്രി ടിക്കറ്റ് ₹150
            |രാവിലെ 4.30 am ന് സൂര്യനെല്ലിയിൽ നിന്ന് പുറപ്പെടുക
            |ഡിസംബർ - ഫെബ്രുവരി ആണ് നല്ല സമയം
            |ബന്ധപ്പെടാൻ: 94470 12345
            |#munnar #kerala #kolukkumalai""".trimMargin()
        val x = Extract.from(cap, places)
        assertEquals("Munnar", x.placeName)
        assertEquals(listOf("Malayalam", "English"), x.languages)
        assertTrue(x.prices.containsAll(listOf("₹2,500", "₹150")))
        assertTrue(x.times.any { it.startsWith("4.30") })
        assertEquals(listOf("+91 94470 12345"), x.phones)
        assertEquals(listOf(1, 2, 12), x.months)
        assertTrue("4x4 jeep needed" in x.amenities && "Entry ticket" in x.amenities)
        assertTrue("Photography" in x.vibes)
        assertTrue(x.tips.any { it.contains("ജീപ്പ്") })
        assertEquals(listOf("munnar", "kerala", "kolukkumalai"), x.hashtags)
    }

    @Test fun englishCaptionWithNegationAndMonsoon() {
        val cap = """Athirappilly falls in full flow this monsoon! 💦
            |Entry fee Rs. 50 per person, parking Rs 40. Open 8am - 5pm.
            |No toilets near the viewpoint, carry water.
            |It's 60 km from Kochi. Must visit with family!""".trimMargin()
        val x = Extract.from(cap, places)
        assertEquals("Athirappilly & Vazhachal Falls", x.placeName)
        assertEquals("Waterfall", x.category)
        assertEquals(listOf("₹50", "₹40"), x.prices)
        assertEquals(listOf("8am - 5pm"), x.times)
        assertEquals(listOf(6, 7, 8, 9), x.months)
        assertEquals(listOf("60 km"), x.distances)
        assertTrue("Parking" in x.amenities)
        assertFalse("Restrooms" in x.amenities) // "No toilets"
        assertTrue("Family" in x.vibes)
        assertTrue(x.tips.any { it.startsWith("No toilets") })
        assertTrue(x.summary.startsWith("Athirappilly falls"))
    }

    @Test fun hindiAndTamilPricesAndTimes() {
        val hi = Extract.from("फोर्ट कोच्चि में टिकट 30 रुपये है, सुबह 9 बजे खुलता है", places)
        assertTrue("₹30" in hi.prices && "9 बजे" in hi.times && "Entry ticket" in hi.amenities)
        val ta = Extract.from("வர்க்கலா கடற்கரை நுழைவு கட்டணம் 100 ரூபாய்", places)
        assertEquals(listOf("₹100"), ta.prices)
        assertEquals("Beach", ta.category)
    }

    @Test fun malayalamNegationAndNamesFindPlaces() {
        assertFalse("Parking" in Extract.from("ഇവിടെ പാർക്കിംഗ് ഇല്ല", places).amenities)
        assertTrue("Parking" in Extract.from("പാർക്കിംഗ് സൗകര്യം ഉണ്ട്", places).amenities)
        assertEquals("Thekkady (തേക്കടി)", Extract.matchPlace(places, "തേക്കടിയിൽ ബോട്ടിംഗ്")?.name) // Malayalam part of a reel-added name
        assertEquals("Fort Kochi & Mattancherry", Extract.matchPlace(places, "", listOf("FortKochi"))?.name)
        assertEquals("Fort Kochi & Mattancherry", Extract.matchPlace(places, "walk around mattancherry jew town")?.name)
        assertNull(Extract.matchPlace(places, "Kanthalloor apple farms"))
    }

    @Test fun unknownPlaceFallsBackToPinThenHashtag() {
        assertEquals("Kanthalloor", Extract.from("Apple farms!\n📍 Kanthalloor", places).placeName)
        assertEquals("Kanthalloor Valley", Extract.from("Apple farms #kerala #KanthalloorValley", places).placeName)
        assertEquals("", Extract.from("nice view #kerala #travel", places).placeName)
    }

    @Test fun lakhsAndThousandsAndMayAsVerb() {
        assertEquals(listOf("₹2,500"), Extract.from("Stay costs Rs 2.5k per night").prices)
        assertTrue(Extract.from("You may need a jacket").months.isEmpty())
        assertEquals(listOf(5), Extract.from("Best in May").months)
        assertEquals(listOf(1, 2, 3, 10, 11, 12), Extract.from("Season: October to March").months)
        assertEquals(listOf(6, 7), Extract.from("June, July").months)
        assertEquals(listOf("Free entry"), Extract.from("Entry is free for everyone").prices)
    }

    /** Real Tesseract output (tessdata_fast mal+eng) from a reel-style overlay on a photo; confidences as measured. */
    @Test fun ocrOutputFromMalayalamOverlayIsCleanedAndUnderstood() {
        val raw = listOf("കൊളുക്കുമല സുര്യോദയം" to 94.3f, "ജീപ്പ്\u200c ചാര്\u200dജ്\u200c ₹2500" to 84.4f, "_ രാവിലെ; 30 ന്\u200c" to 64.9f,
            "ib =," to 50.0f, "CF BOOIE ;" to 9.3f, "zd" to 57.9f, "എന്\u200dട്രി ടി," to 76.4f, "൭0:" to 70.8f, "rare" to 27.8f, "ഇ." to 59.5f)
        val kept = raw.filter { (l, c) -> Extract.ocrLineOk(l, c) }.map { Extract.normalize(it.first) }
        assertEquals(listOf("കൊളുക്കുമല സുര്യോദയം", "ജീപ്പ് ചാർജ് ₹2500", "_ രാവിലെ; 30 ന്", "എൻട്രി ടി,"), kept)
        val x = Extract.from(kept.joinToString("\n"), places)
        assertEquals(listOf("Malayalam"), x.languages)
        assertEquals(listOf("₹2,500"), x.prices)
        assertTrue("4x4 jeep needed" in x.amenities)
    }

    @Test fun oldStyleMalayalamSpellingMatchesModernKeywords() =
        assertTrue("Parking" in Extract.from("പാര്\u200dക്കിംഗ്\u200c ഉണ്ട്").amenities)

    /** Real embed markup saved from instagram.com/reel/DQW4z-zDw8B/embed/captioned/ (2026-11-28). */
    @Test fun readsCaptionFromOfficialEmbedPage() {
        val html = javaClass.getResource("/embed_captioned.html")!!.readText()
        val (caption, author) = ReelPage.embedCaption(html)!!
        assertEquals("travel.big.with.a.tiny.budget", author)
        assertTrue(caption.startsWith("A hidden wonder of South India 🔥"))
        assertTrue(caption.contains("📍Badami") && caption.contains("#karnataka"))
        assertFalse(caption.contains("View all") || caption.contains("<") || caption.contains("&#064;"))
        val x = Extract.from(caption)
        assertEquals("Badami", x.placeName)
        assertEquals("Karnataka", x.region)
        assertEquals("Religious", x.category)
        assertNull(ReelPage.embedCaption("<html>login</html>"))
    }

    @Test fun mergeKeepsFirstSummaryAndUnionsLists() {
        val m = Extract.merge(listOf(Extract.from("Great sunrise spot. Entry ₹100."), Extract.from("Parking available. Entry ₹100, jeep ₹2000")))
        assertEquals("Great sunrise spot.", m.summary)
        assertEquals(listOf("₹100", "₹2,000"), m.prices)
    }

    @Test fun guideRanksRepeatedTipsFirst() {
        fun r(tips: List<String>, prices: List<String> = emptyList()) = Reel("c", "u", "k", "Munnar", "", "a", "", tips, 0, prices = prices, languages = listOf("Malayalam"))
        val g = reelGuide(listOf(r(listOf("Go before 5am", "Carry a jacket")), r(listOf("go before 5am")), r(emptyList(), listOf("₹150"))))!!
        assertEquals(3, g.count)
        assertEquals("Go before 5am", g.tips.first())
        assertEquals(listOf("₹150"), g.prices)
        assertEquals(listOf("Malayalam"), g.languages)
        assertNull(reelGuide(emptyList()))
    }

    @Test fun aiPromptAsksForTranslationAndIncludesAllText() {
        val p = aiPrompt("Munnar", "https://www.instagram.com/reel/X1234/", "cap", "screen", "spoken")
        assertTrue(p.contains("Munnar") && p.contains("(https://www.instagram.com/reel/X1234/),") && p.contains("Malayalam"))
        assertTrue(p.contains("cap") && p.contains("screen") && p.contains("spoken"))
    }
}
