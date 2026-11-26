package com.tripguide.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class) // org.json needs the Android runtime
@Config(sdk = [34])
class GoogleTest {
    private val sample = """
    {"places":[
     {"id":"abc","displayName":{"text":"Baltit Fort"},"formattedAddress":"Karimabad, Hunza","primaryTypeDisplayName":{"text":"Historical landmark"},
      "location":{"latitude":36.3256,"longitude":74.6694},"rating":4.7,"userRatingCount":2312,"priceLevel":"PRICE_LEVEL_INEXPENSIVE",
      "photos":[{"name":"places/abc/photos/p1","authorAttributions":[{"displayName":"Ali K"}]}],"googleMapsUri":"https://maps.google.com/?cid=1"},
     {"id":"def","displayName":{"text":"Hunza Serena Inn"},"location":{"latitude":36.32,"longitude":74.66},
      "priceRange":{"startPrice":{"currencyCode":"PKR","units":"18000"},"endPrice":{"currencyCode":"PKR","units":"35000"}}}
    ]}""".trimIndent()

    @Test fun parsesRatingsPhotosAndPrices() {
        val (fort, hotel) = Google.parse(sample)
        assertEquals("Baltit Fort", fort.name)
        assertEquals(4.7, fort.rating!!, 0.0)
        assertEquals(2312, fort.ratings)
        assertEquals("$", fort.priceLevel)
        assertEquals("places/abc/photos/p1", fort.photo)
        assertEquals("Ali K", fort.photoBy)
        assertNull(hotel.rating)
        assertNull(hotel.photo)
        assertEquals("PKR 18,000 – 35,000", hotel.priceRange)
    }

    @Test fun emptyResponseIsEmptyList() = assertTrue(Google.parse("{}").isEmpty())

    @Test fun bookingLinkCarriesDatesAndGuests() {
        fun at(d: Int) = LocalDate.of(2027, 5, d).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val url = Google.bookingUrl("Hunza Valley, Gilgit-Baltistan", at(10), at(13), 3)
        assertTrue(url, url.contains("ss=Hunza+Valley%2C+Gilgit-Baltistan"))
        assertTrue(url, url.contains("checkin=2027-05-10&checkout=2027-05-13"))
        assertTrue(url, url.contains("group_adults=3"))
    }
}
