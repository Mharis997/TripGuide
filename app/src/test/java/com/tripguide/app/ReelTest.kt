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

    @Test fun embedUrlDropsQueryAndAddsEmbed() =
        assertEquals("https://www.instagram.com/reel/C9xYz_12ab/embed/", embedUrl("https://www.instagram.com/reel/C9xYz_12ab/?igsh=x"))

    @Test fun matchesPlacesByNameLoosely() {
        val places = listOf("Munnar", "Fort Kochi & Mattancherry", "Thekkady (Periyar)", "Varkala").map { Place(name = it) }
        assertEquals("Munnar", bestMatch(places, "munnar")?.name)
        assertEquals("Thekkady (Periyar)", bestMatch(places, "Thekkady")?.name)
        assertEquals("Fort Kochi & Mattancherry", bestMatch(places, "Mattancherry Jew Town")?.name)
        assertEquals("Varkala", bestMatch(places, "Varkala Cliff Beach")?.name)
        assertNull(bestMatch(places, "Kanthalloor"))
        assertNull(bestMatch(places, ""))
    }

    @Test fun builtInKeysAreStableSlugsAndCommunityKeysWin() {
        assertEquals("kerala-fort-kochi-mattancherry", Place(name = "Fort Kochi & Mattancherry").cloudKey)
        assertEquals("abc123", Place(name = "X", key = "abc123").cloudKey)
    }
}
