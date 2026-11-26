package com.tripguide.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.work.testing.WorkManagerTestInitHelper
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
        Thread.sleep(1500) // let Coil finish decoding remote photos
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("../fastlane/metadata/android/en-US/images/phoneScreenshots/$name.png")
    }

    private fun waitFor(text: String) = rule.waitUntil(20_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Before fun initWorkManager() =
        WorkManagerTestInitHelper.initializeTestWorkManager(androidx.test.core.app.ApplicationProvider.getApplicationContext())

    @Test fun mainFlows() {
        waitFor("18 places")
        shot("1_explore")

        // Filtering by amenity checkbox narrows the list
        rule.onNodeWithText("Filters", substring = true).performClick()
        rule.onNodeWithText("4x4 jeep needed").performClick()
        waitFor("5 places")
        rule.onNodeWithText("Clear all").performClick()
        waitFor("18 places")
        androidx.test.espresso.Espresso.pressBack()

        // Place detail, guide, community update
        rule.onNodeWithText("Search places, regions…").performTextInput("Hunza")
        rule.onAllNodesWithText("Hunza Valley")[0].performClick()
        waitFor("Quick facts")
        shot("2_place")
        rule.onNodeWithText("Guide & tips").performClick()
        shot("3_guide")
        rule.onNodeWithText("Updates (0)").performClick()
        rule.onNodeWithText("Post an update", substring = true).performClick()
        rule.onNodeWithText("Road blocked").performClick()
        rule.onNodeWithText("What did you find?").performTextInput("Landslide near Attabad tunnel, cleared by noon. Expect 2-3 hr delay.")
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
        shot("8_settings")
    }
}
