package com.tripguide.app

import org.junit.Assert.*
import org.junit.Test

class ReelTest {
    @Test fun findsReelLinkInSharedText() {
        assertEquals("https://www.instagram.com/reel/C9xYz_12ab/?igsh=MTc4", reelLink("Look at this! https://www.instagram.com/reel/C9xYz_12ab/?igsh=MTc4"))
        assertEquals("https://instagram.com/p/Cabcde12/", reelLink("https://instagram.com/p/Cabcde12/"))
        assertNull(reelLink("https://www.instagram.com/kerala.trips/"))
        assertNull(reelLink("http://www.instagram.com/reel/C9xYz_12ab/"))
        assertNull(reelLink(null))
    }

    @Test fun framesAreSpreadAcrossTheVideoAvoidingStartAndEnd() {
        assertEquals(listOf(1_250_000L, 3_750_000L, 6_250_000L, 8_750_000L), frameTimesUs(10_000, 4))
        val t = frameTimesUs(30_000)
        assertEquals(8, t.size)
        assertTrue(t.first() > 0 && t.last() < 30_000_000L && t == t.sorted())
        assertEquals(listOf(0L), frameTimesUs(0))
    }

    @Test fun shareLinksAreRecognised() {
        assertEquals("https://www.instagram.com/share/_xqcwgKq6", reelLink("Check this https://www.instagram.com/share/_xqcwgKq6"))
        assertEquals("https://www.instagram.com/share/reel/BAabc123/", reelLink("https://www.instagram.com/share/reel/BAabc123/"))
        assertTrue(ReelPage.isShareLink("https://www.instagram.com/share/_xqcwgKq6"))
        assertTrue(ReelPage.isShareLink("https://instagram.com/share/p/BAabc123/?igsh=x"))
        assertFalse(ReelPage.isShareLink("https://evil.com/share/_xqcwgKq6"))
        assertNull(ReelPage.canonical("https://www.instagram.com/share/_xqcwgKq6")) // must be resolved first
        // Where Instagram's share redirect points (observed live): the resolved reel is canonical.
        assertEquals("DQW4z-zDw8B" to "https://www.instagram.com/reel/DQW4z-zDw8B/", ReelPage.canonical("https://www.instagram.com/reel/DQW4z-zDw8B/?lqvp=OW50ZWkwaGMyMWg3"))
    }

    @Test fun embedUrlDropsQueryAndAddsEmbed() =
        assertEquals("https://www.instagram.com/reel/C9xYz_12ab/embed/", embedUrl("https://www.instagram.com/reel/C9xYz_12ab/?igsh=x"))

    @Test fun canonicalReelLinks() {
        assertEquals("C9xYz_12ab" to "https://www.instagram.com/reel/C9xYz_12ab/", ReelPage.canonical("https://www.instagram.com/reel/C9xYz_12ab/?igsh=abc"))
        assertEquals("https://www.instagram.com/reel/C9xYz_12ab/", ReelPage.canonical("https://instagram.com/reels/C9xYz_12ab")?.second)
        assertEquals("https://www.instagram.com/p/Cabcde12/", ReelPage.canonical("https://www.instagram.com/p/Cabcde12/")?.second)
        assertEquals("C9xYz_12ab", ReelPage.canonical("https://www.instagram.com/kerala.trips/reel/C9xYz_12ab/")?.first)
        for (bad in listOf("http://www.instagram.com/reel/C9xYz_12ab/", "https://evil.com/reel/C9xYz_12ab/",
            "https://www.instagram.com.evil.com/reel/C9xYz_12ab/", "https://www.instagram.com/explore/", "not a url", "")) assertNull(bad, ReelPage.canonical(bad))
    }

    @Test fun readsCaptionAndAuthorFromPageTags() {
        val html = """<meta property="og:image" content="https://cdn/x.jpg?a=1&amp;b=2" />
            <meta content="Kerala Trips on Instagram: &quot;Munnar&quot;" property="og:title" />
            <meta property="og:description" content="1,234 likes, 56 comments - kerala.trips on May 1, 2026: &quot;കൊളുക്കുമല &#x1F305; Jeep ₹2,500&quot;." />"""
        val tags = ReelPage.tags(html)
        assertEquals("https://cdn/x.jpg?a=1&b=2", tags["og:image"])
        assertEquals("കൊളുക്കുമല 🌅 Jeep ₹2,500" to "kerala.trips", ReelPage.captionAndAuthor(tags))
    }

    @Test fun builtInKeysAreStableSlugsAndCommunityKeysWin() {
        assertEquals("kerala-fort-kochi-mattancherry", Place(name = "Fort Kochi & Mattancherry").cloudKey)
        assertEquals("abc123", Place(name = "X", key = "abc123").cloudKey)
    }
}
