package com.tripguide.app

/** Fixed vocabularies used for filtering, planning and the add-place form. */
object Catalog {
    val categories = listOf("Mountains", "Valley", "Lake", "Beach", "Desert", "Forest", "Waterfall", "Historical", "Religious", "City", "Adventure", "Theme park")
    val vibes = listOf("Just escape", "Mountains", "Beaches", "Party", "Chill", "Nature", "Adventure", "Romantic", "Family", "Culture", "Food", "Photography", "Road trip", "Spiritual")
    val amenities = listOf(
        "Parking", "Restrooms", "Restaurants", "Hotels", "Camping", "Mobile signal", "Internet", "ATM", "Fuel station",
        "Medical aid", "Wheelchair access", "Kid friendly", "Pet friendly", "Local guides", "4x4 jeep needed", "Entry ticket",
    )
    val transport = listOf("Car", "Bus", "Train", "Flight", "Motorbike")
    val updateTypes = listOf("Open", "Temporarily closed", "Road blocked", "Weather alert", "Crowded", "General info")
    /** Update types that change a place's visible status badge. */
    val statusTypes = setOf("Open", "Temporarily closed", "Road blocked")
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val currencies = listOf("PKR", "INR", "USD", "EUR", "GBP", "AED", "SAR")

    data class City(val name: String, val lat: Double, val lng: Double)
    val cities = listOf(
        City("Islamabad", 33.6844, 73.0479), City("Rawalpindi", 33.5651, 73.0169), City("Lahore", 31.5204, 74.3587),
        City("Karachi", 24.8607, 67.0011), City("Peshawar", 34.0151, 71.5249), City("Quetta", 30.1798, 66.9750),
        City("Multan", 30.1575, 71.5249), City("Faisalabad", 31.4504, 73.1350), City("Hyderabad", 25.3960, 68.3578),
        City("Sialkot", 32.4945, 74.5229), City("Gujranwala", 32.1877, 74.1945), City("Abbottabad", 34.1688, 73.2215),
    )

    fun packingList(p: Place, transport: String): List<String> = buildList {
        addAll(listOf("ID card / passport", "Phone charger & power bank", "Cash", "First-aid kit", "Personal medicines", "Water bottle", "Toiletries"))
        when (p.category) {
            "Mountains", "Valley", "Lake", "Adventure", "Forest", "Waterfall" -> addAll(listOf("Warm jacket", "Hiking shoes", "Sunglasses", "Sunscreen", "Rain shell"))
            "Beach" -> addAll(listOf("Swimwear", "Sunscreen", "Hat", "Flip-flops", "Towel"))
            "Desert" -> addAll(listOf("Hat", "Extra water", "Sunscreen", "Scarf"))
            else -> add("Comfortable walking shoes")
        }
        if ("Camping" in p.amenities) addAll(listOf("Tent", "Sleeping bag", "Headlamp"))
        if ("Mobile signal" !in p.amenities) add("Download offline maps")
        if ("ATM" !in p.amenities) add("Enough cash (no ATM)")
        when (transport) {
            "Car" -> addAll(listOf("Driving licence & car papers", "Spare tyre & tool kit"))
            "Motorbike" -> addAll(listOf("Helmet", "Riding gloves", "Driving licence"))
            "Flight" -> add("Boarding pass / e-ticket")
        }
    }
}
