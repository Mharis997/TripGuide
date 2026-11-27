package com.tripguide.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeedTest {
    @Test fun reloadAddsOnlyMissingPlacesAndKeepsUserData() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDb::class.java).allowMainThreadQueries().build()
        val dao = db.dao()
        Seed.load(ctx, dao)
        val all = dao.allPlaces()
        assertEquals(36, all.size)

        // Simulate an older install: favourite one place, remove another built-in one, add a user place.
        val fav = all.first { it.name == "Munnar" }
        dao.upsert(fav.copy(saved = "Plan to go"))
        dao.deleteSeedPlaces(); dao.insertPlaces(all.filter { it.name != "Thrissur" }.map { it.copy(id = 0, saved = if (it.name == "Munnar") "Plan to go" else "") })
        dao.upsert(Place(name = "My spot", userAdded = true))

        Seed.load(ctx, dao) // app update ships Thrissur again
        val after = dao.allPlaces()
        assertEquals(37, after.size)
        assertEquals(1, after.count { it.name == "Thrissur" })
        assertEquals("Plan to go", after.first { it.name == "Munnar" }.saved)
        db.close()
    }
}
