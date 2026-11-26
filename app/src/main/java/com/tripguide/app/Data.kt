package com.tripguide.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val region: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val category: String = Catalog.categories.first(),
    val vibes: List<String> = emptyList(),
    val amenities: List<String> = emptyList(),
    val transport: List<String> = emptyList(),
    val description: String = "",
    val howToReach: String = "",
    val guide: String = "",
    val tips: String = "",
    val safety: String = "",
    val bestMonths: List<Int> = emptyList(),
    val dailyCost: Int = 0,
    val minDays: Int = 1,
    val photos: List<String> = emptyList(),
    val photoCredits: String = "",
    val status: String = "Open",
    val favorite: Boolean = false,
    val userAdded: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "updates", indices = [Index("placeId")])
data class PlaceUpdate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val author: String,
    val type: String,
    val text: String,
    val photo: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val title: String,
    val departAt: Long,
    val returnAt: Long,
    val budget: Int,
    val people: Int,
    val transport: String,
    val vibes: List<String> = emptyList(),
    val notes: String = "",
)

@Entity(tableName = "expenses", indices = [Index("tripId")])
data class Expense(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: Long, val label: String, val amount: Int)

@Entity(tableName = "checklist", indices = [Index("tripId")])
data class CheckItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tripId: Long, val text: String, val done: Boolean = false)

class Converters {
    @TypeConverter fun fromStrings(v: List<String>) = v.joinToString("\u001F")
    @TypeConverter fun toStrings(v: String) = if (v.isEmpty()) emptyList() else v.split("\u001F")
    @TypeConverter fun fromInts(v: List<Int>) = v.joinToString(",")
    @TypeConverter fun toInts(v: String) = if (v.isEmpty()) emptyList() else v.split(",").map { it.toInt() }
}

@androidx.room.Dao
abstract class Dao {
    @Query("SELECT * FROM places ORDER BY name") abstract fun places(): Flow<List<Place>>
    @Query("SELECT * FROM places") abstract suspend fun allPlaces(): List<Place>
    @Query("SELECT * FROM places WHERE id = :id") abstract fun place(id: Long): Flow<Place?>
    @Query("SELECT * FROM places WHERE id = :id") abstract suspend fun getPlace(id: Long): Place?
    @Upsert abstract suspend fun upsert(p: Place): Long
    @Insert abstract suspend fun insertPlaces(p: List<Place>)
    @Query("DELETE FROM places WHERE userAdded = 0") abstract suspend fun deleteSeedPlaces()

    @Query("SELECT * FROM updates WHERE placeId = :placeId ORDER BY createdAt DESC") abstract fun updates(placeId: Long): Flow<List<PlaceUpdate>>
    @Insert abstract suspend fun insert(u: PlaceUpdate): Long

    @Query("SELECT * FROM trips ORDER BY departAt") abstract fun trips(): Flow<List<Trip>>
    @Query("SELECT * FROM trips WHERE id = :id") abstract fun trip(id: Long): Flow<Trip?>
    @Query("SELECT * FROM trips WHERE id = :id") abstract suspend fun getTrip(id: Long): Trip?
    @Query("SELECT * FROM trips WHERE placeId = :placeId AND returnAt > :now") abstract suspend fun upcomingTripsTo(placeId: Long, now: Long): List<Trip>
    @Insert abstract suspend fun insert(t: Trip): Long
    @Update abstract suspend fun update(t: Trip)

    @Query("SELECT * FROM expenses WHERE tripId = :tripId ORDER BY id DESC") abstract fun expenses(tripId: Long): Flow<List<Expense>>
    @Insert abstract suspend fun insert(e: Expense)
    @Delete abstract suspend fun delete(e: Expense)

    @Query("SELECT * FROM checklist WHERE tripId = :tripId ORDER BY done, id") abstract fun checklist(tripId: Long): Flow<List<CheckItem>>
    @Insert abstract suspend fun insertItems(i: List<CheckItem>)
    @Update abstract suspend fun update(i: CheckItem)
    @Delete abstract suspend fun delete(i: CheckItem)

    @Query("DELETE FROM trips WHERE id = :id") protected abstract suspend fun deleteTripRow(id: Long)
    @Query("DELETE FROM expenses WHERE tripId = :id") protected abstract suspend fun deleteExpenses(id: Long)
    @Query("DELETE FROM checklist WHERE tripId = :id") protected abstract suspend fun deleteChecklist(id: Long)
    @Transaction open suspend fun deleteTrip(id: Long) { deleteExpenses(id); deleteChecklist(id); deleteTripRow(id) }

    @Query("DELETE FROM updates WHERE placeId = :id") protected abstract suspend fun deleteUpdates(id: Long)
    @Query("DELETE FROM places WHERE id = :id") protected abstract suspend fun deletePlaceRow(id: Long)
    @Transaction open suspend fun deletePlace(id: Long) { deleteUpdates(id); deletePlaceRow(id) }

    @Query("DELETE FROM trips") protected abstract suspend fun clearTrips()
    @Query("DELETE FROM expenses") protected abstract suspend fun clearExpenses()
    @Query("DELETE FROM checklist") protected abstract suspend fun clearChecklist()
    @Query("DELETE FROM updates") protected abstract suspend fun clearUpdates()
    @Query("DELETE FROM places") protected abstract suspend fun clearPlaces()
    @Transaction open suspend fun clearAll() { clearExpenses(); clearChecklist(); clearTrips(); clearUpdates(); clearPlaces() }
}

@Database(entities = [Place::class, PlaceUpdate::class, Trip::class, Expense::class, CheckItem::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): Dao
}

/** Built-in places shipped in assets/places.json. Inserts only names not already in the DB, so app updates add new places. */
object Seed {
    suspend fun load(ctx: Context, dao: Dao) {
        val have = dao.allPlaces().map { it.name }.toSet()
        val arr = JSONArray(ctx.assets.open("places.json").bufferedReader().use { it.readText() })
        fun JSONArray.strings() = List(length()) { getString(it) }
        dao.insertPlaces(List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Place(
                name = o.getString("name"), region = o.getString("region"),
                lat = o.getDouble("lat"), lng = o.getDouble("lng"), category = o.getString("category"),
                vibes = o.getJSONArray("vibes").strings(), amenities = o.getJSONArray("amenities").strings(),
                transport = o.getJSONArray("transport").strings(),
                description = o.getString("description"), howToReach = o.getString("howToReach"),
                guide = o.getString("guide"), tips = o.getString("tips"), safety = o.getString("safety"),
                bestMonths = o.getJSONArray("bestMonths").let { a -> List(a.length()) { a.getInt(it) } },
                dailyCost = o.getInt("dailyCost"), minDays = o.getInt("minDays"),
                photos = o.getJSONArray("photos").strings(), photoCredits = o.optString("photoCredits"),
            )
        }.filter { it.name !in have })
    }
}
