package com.tripguide.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDb::class.java)

    @Test fun v1FavouritesBecomeTheFavoritesListAndNothingIsLost() = runBlocking {
        helper.createDatabase("m.db", 1).apply {
            fun place(name: String, fav: Int, mine: Int) = execSQL(
                "INSERT INTO places (name, region, lat, lng, category, vibes, amenities, transport, description, howToReach, guide, tips, safety, " +
                    "bestMonths, dailyCost, minDays, photos, photoCredits, status, favorite, userAdded, updatedAt) VALUES " +
                    "('$name', 'Idukki', 10.0, 77.0, 'Mountains', 'Chill', '', 'Car', 'd', '', '', '', '', '1,2', 3000, 2, '', '', 'Open', $fav, $mine, 1)")
            place("Munnar", 1, 0); place("My spot", 0, 1)
            execSQL("INSERT INTO trips (placeId, title, departAt, returnAt, budget, people, transport, vibes, notes) VALUES (1, 'T', 1, 2, 100, 2, 'Car', '', '')")
            close()
        }
        helper.runMigrationsAndValidate("m.db", 2, true, MIGRATION_1_2).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDb::class.java, "m.db").addMigrations(MIGRATION_1_2).build()
        val places = db.dao().allPlaces().associateBy { it.name }
        assertEquals("Favorites", places.getValue("Munnar").saved)
        assertEquals("", places.getValue("My spot").saved)
        assertEquals(true, places.getValue("My spot").userAdded)
        assertEquals(listOf(1, 2), places.getValue("Munnar").bestMonths)
        assertEquals(1L, db.dao().getTrip(1)!!.placeId)
        db.close()
    }
}
