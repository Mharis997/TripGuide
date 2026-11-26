package com.tripguide.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PlannerTest {
    private fun at(month: Int, day: Int) = LocalDateTime.of(2027, month, day, 8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val isb = Catalog.cities.first()
    private val beach = Place(id = 1, name = "Beach", lat = 25.4, lng = 65.5, category = "Beach", vibes = listOf("Beaches", "Chill"),
        transport = listOf("Car"), bestMonths = listOf(11, 12, 1), dailyCost = 4000, minDays = 1)
    private val hills = Place(id = 2, name = "Hills", lat = 33.9, lng = 73.39, category = "Mountains", vibes = listOf("Mountains", "Chill"),
        transport = listOf("Car", "Bus"), bestMonths = (1..12).toList(), dailyCost = 7000, minDays = 1)

    private fun input(vibes: Set<String>, budget: Int = 200_000, depart: Long = at(12, 1), back: Long = at(12, 4)) =
        PlanInput(depart, back, budget, 2, "Car", vibes, isb.lat, isb.lng)

    @Test fun haversineIslamabadToLahore() = assertEquals(270.0, Planner.km(33.6844, 73.0479, 31.5204, 74.3587), 15.0)

    @Test fun vibesDriveRanking() {
        assertEquals("Hills", Planner.suggest(listOf(beach, hills), input(setOf("Mountains"))).first().place.name)
    }

    @Test fun shortTripToFarPlaceWarns() {
        val s = Planner.estimate(beach, input(setOf("Beaches"), depart = at(12, 1), back = at(12, 1) + 6 * 3_600_000))
        assertTrue(s.warnings.any { "time" in it })
    }

    @Test fun overBudgetAndOffSeasonAndClosedLowerScore() {
        val good = Planner.estimate(beach, input(setOf("Beaches")))
        val bad = Planner.estimate(beach.copy(status = "Temporarily closed"), input(setOf("Beaches"), budget = 1000, depart = at(6, 1), back = at(6, 4)))
        assertTrue(good.score > bad.score)
        assertEquals(3, bad.warnings.count { "budget" in it || "Off-season" in it || "Reported" in it })
    }

    @Test fun costScalesWithPeopleAndDays() {
        val s = Planner.estimate(hills, input(emptySet()))
        assertEquals(7000 * 2 * 3, s.stayCost)
        assertTrue(s.transportCost > 0)
    }
}
