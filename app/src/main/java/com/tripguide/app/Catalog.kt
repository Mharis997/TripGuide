package com.tripguide.app

/** Fixed vocabularies used for filtering, planning and the add-place form. */
object Catalog {
    val categories = listOf("Mountains", "Backwaters", "Beach", "Waterfall", "Wildlife", "Lake", "Forest", "Valley", "Historical", "Religious", "City", "Adventure", "Theme park")
    val vibes = listOf("Just escape", "Mountains", "Beaches", "Party", "Chill", "Nature", "Adventure", "Romantic", "Family", "Culture", "Food", "Photography", "Road trip", "Spiritual", "Wellness")
    val amenities = listOf(
        "Parking", "Restrooms", "Restaurants", "Hotels", "Camping", "Mobile signal", "Internet", "ATM", "Fuel station",
        "Medical aid", "Wheelchair access", "Kid friendly", "Pet friendly", "Local guides", "4x4 jeep needed", "Entry ticket",
    )
    val transport = listOf("Car", "Bus", "Train", "Flight", "Motorbike")
    val updateTypes = listOf("Open", "Temporarily closed", "Road blocked", "Weather alert", "Crowded", "General info")
    /** Update types that change a place's visible status badge. */
    val statusTypes = setOf("Open", "Temporarily closed", "Road blocked")
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val currencies = listOf("INR", "USD", "EUR", "GBP", "AED", "SAR")

    data class City(val name: String, val lat: Double, val lng: Double)
    val cities = listOf(
        City("Kochi", 9.9312, 76.2673), City("Thiruvananthapuram", 8.5241, 76.9366), City("Kozhikode", 11.2588, 75.7804),
        City("Thrissur", 10.5276, 76.2144), City("Kollam", 8.8932, 76.6141), City("Kannur", 11.8745, 75.3704),
        City("Kottayam", 9.5916, 76.5222), City("Alappuzha", 9.4981, 76.3388), City("Palakkad", 10.7867, 76.6548),
        City("Malappuram", 11.0510, 76.0711), City("Kasaragod", 12.4996, 74.9869), City("Pathanamthitta", 9.2648, 76.7870),
        // Common origins for visitors from neighbouring states
        City("Bengaluru", 12.9716, 77.5946), City("Chennai", 13.0827, 80.2707), City("Coimbatore", 11.0168, 76.9558), City("Mangaluru", 12.9141, 74.8560),
    )

    fun packingList(p: Place, transport: String): List<String> = buildList {
        addAll(listOf("ID card / passport", "Phone charger & power bank", "Cash / UPI app", "First-aid kit", "Personal medicines", "Water bottle", "Toiletries", "Umbrella / raincoat", "Mosquito repellent"))
        when (p.category) {
            "Mountains", "Valley", "Lake", "Adventure", "Forest", "Waterfall" -> addAll(listOf("Warm jacket", "Hiking shoes", "Sunglasses", "Sunscreen"))
            "Beach" -> addAll(listOf("Swimwear", "Sunscreen", "Hat", "Flip-flops", "Towel"))
            "Backwaters" -> addAll(listOf("Light cotton clothes", "Sunscreen", "Hat", "Dry bag for phone"))
            "Wildlife" -> addAll(listOf("Binoculars", "Earth-tone clothes", "Leech socks (monsoon)", "Trekking shoes"))
            "Religious" -> addAll(listOf("Temple dress (mundu / saree)", "Bag for footwear"))
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
