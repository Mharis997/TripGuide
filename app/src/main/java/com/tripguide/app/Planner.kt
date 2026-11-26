package com.tripguide.app

import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

data class PlanInput(
    val departAt: Long, val returnAt: Long, val budget: Int, val people: Int,
    val transport: String, val vibes: Set<String>, val homeLat: Double, val homeLng: Double,
)

data class Suggestion(
    val place: Place, val score: Int, val km: Int, val travelHours: Double,
    val transportCost: Int, val stayCost: Int, val reasons: List<String>, val warnings: List<String>,
) { val total get() = transportCost + stayCost }

/** Pure ranking logic: no Android dependencies so it is unit-tested on the JVM. */
object Planner {
    private const val DAY = 86_400_000.0
    private val hilly = setOf("Mountains", "Valley", "Lake", "Adventure", "Forest", "Waterfall", "Wildlife")

    fun km(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 6371 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun days(i: PlanInput) = max(1, ceil((i.returnAt - i.departAt) / DAY).toInt())

    fun estimate(p: Place, i: PlanInput): Suggestion {
        val straight = km(i.homeLat, i.homeLng, p.lat, p.lng)
        val flight = i.transport == "Flight"
        val dist = if (flight) straight else straight * 1.4
        // shortcut: fixed Kerala average speeds and INR per-km rates; upgrade to routing + live fares when a backend exists.
        val speed = mapOf("Car" to 45.0, "Bus" to 35.0, "Train" to 50.0, "Motorbike" to 40.0, "Flight" to 500.0)[i.transport] ?: 45.0
        val hours = if (dist < 1) 0.0 else if (flight) dist / speed + 3 else dist / (if (p.category in hilly) speed * 0.8 else speed)
        val rt = dist * 2
        val transportCost = when (i.transport) {
            "Car" -> rt * 12 * ceil(i.people / 4.0) // fuel + wear per car
            "Motorbike" -> rt * 4 * ceil(i.people / 2.0)
            "Bus" -> rt * 1.5 * i.people // KSRTC fast passenger
            "Train" -> rt * 1.2 * i.people // sleeper / 2S class
            "Flight" -> (3_000 + rt * 6) * i.people
            else -> 0.0
        }.roundToInt()
        val days = days(i)
        val stayCost = p.dailyCost * i.people * days

        val reasons = mutableListOf<String>(); val warnings = mutableListOf<String>()
        var score = 0.0

        val matched = i.vibes.filter { it in p.vibes || it == p.category }
        score += if (i.vibes.isEmpty()) 25.0 else 40.0 * matched.size / i.vibes.size
        if (matched.isNotEmpty()) reasons += "Matches: ${matched.joinToString()}"

        val total = transportCost + stayCost
        if (i.budget <= 0 || total <= i.budget) { score += 25; if (i.budget > 0) reasons += "Fits your budget" }
        else { score += 25.0 * i.budget / total; warnings += "Over budget by ${"%,d".format(total - i.budget)}" }

        val onSite = days - 2 * hours / 24
        when {
            onSite >= p.minDays -> { score += 20; reasons += "Enough time to enjoy it" }
            onSite <= 0 -> warnings += "Not enough time to get there and back"
            else -> { score += 20 * onSite / p.minDays; warnings += "Best with ${p.minDays}+ days on site" }
        }

        val month = Instant.ofEpochMilli(i.departAt).atZone(ZoneId.systemDefault()).monthValue
        if (p.bestMonths.isEmpty() || month in p.bestMonths) { score += 15; reasons += "Good season" }
        else warnings += "Off-season in ${Catalog.months[month - 1]}"

        if (p.transport.isNotEmpty() && i.transport !in p.transport) { score -= 25; warnings += "Not easily reachable by ${i.transport}" }
        if (p.status != "Open") { score -= 30; warnings += "Reported: ${p.status}" }

        return Suggestion(p, score.roundToInt().coerceIn(0, 100), straight.roundToInt(), hours, transportCost, stayCost, reasons, warnings)
    }

    fun suggest(places: List<Place>, i: PlanInput) = places.map { estimate(it, i) }.sortedByDescending { it.score }
}
