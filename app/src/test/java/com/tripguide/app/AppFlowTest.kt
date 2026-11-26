package com.tripguide.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end walk through the main flows on the real Room DB.
 * `./gradlew recordRoborazziDebug` also writes the Play Store phone screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class AppFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        repeat(4) { Thread.sleep(1500); rule.waitForIdle() } // let Coil download and decode remote photos
        rule.onRoot().captureRoboImage("../fastlane/metadata/android/en-US/images/phoneScreenshots/$name.png")
    }

    private fun waitFor(text: String) = rule.waitUntil(20_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Prefs.dynamicColor = false // brand colours in store screenshots
    }

    @Test fun mainFlows() {
        waitFor("18 places")
        shot("1_explore")

        // Filtering by amenity checkbox narrows the list
        rule.onNodeWithText("Filters", substring = true).performClick()
        rule.onNodeWithText("4x4 jeep needed").performClick()
        waitFor("2 places")
        rule.onNodeWithText("Clear all").performClick()
        waitFor("18 places")
        androidx.test.espresso.Espresso.pressBack()

        // Place detail, guide, community update
        rule.onNodeWithText("Search places, regions…").performTextInput("Munnar")
        rule.onAllNodesWithText("Munnar")[1].performClick()
        waitFor("Quick facts")
        shot("2_place")
        rule.onNodeWithText("Guide & tips").performClick()
        shot("3_guide")
        // Stays tab: live-price links work even before a Google key is configured
        rule.onNodeWithText("Stays").performScrollTo().performClick()
        rule.onNodeWithText("Booking.com").assertExists()
        rule.onNodeWithText("Updates (0)").performScrollTo().performClick()
        rule.onNodeWithText("Post an update", substring = true).performClick()
        rule.onNodeWithText("Road blocked").performClick()
        rule.onNodeWithText("What did you find?").performTextInput("Landslide on Gap Road near Devikulam, one lane open. Expect 1-2 hr delay.")
        rule.onNodeWithText("Post update").performClick()
        waitFor("Updates (1)")
        rule.onAllNodesWithText("Road blocked")[0].assertExists()
        shot("4_updates")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        // Plan wizard → suggestions → trip
        rule.onNodeWithText("Plan").performClick()
        rule.onNodeWithText("Mountains").performClick()
        rule.onNodeWithText("Chill").performClick()
        shot("5_plan")
        rule.onNodeWithText("Suggest places", substring = true).performScrollTo().performClick()
        waitFor("Suggestions for you")
        shot("6_suggestions")
        rule.onAllNodesWithText("Choose")[0].performClick()
        waitFor("Packing checklist")
        shot("7_trip")
        rule.onNodeWithText("Packing checklist", substring = true).assertExists()

        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("My trips").performClick()
        waitFor("Upcoming")
        rule.onNodeWithText("Settings").performClick()
        waitFor("Home city")
        rule.onNodeWithText("Dark").performClick()
        assertEquals("dark", Prefs.theme)
        shot("8_settings_dark")
        rule.onNodeWithText("Light").performClick()
        assertEquals("light", Prefs.theme)
    }
}
