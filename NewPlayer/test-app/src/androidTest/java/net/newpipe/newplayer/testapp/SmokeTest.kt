package net.newpipe.newplayer.testapp

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class SmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun smokeTest() {
        onView(ViewMatchers.withText("Start 6502 Stream")).perform(ViewActions.click())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun checkNewPlayerIsPlayingTest() {
        onView(ViewMatchers.withText("Start 6502 Stream")).perform(ViewActions.click())

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            var isPlaying = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                isPlaying = composeTestRule.activity.newPlayer.exoPlayer.value?.isPlaying ?: false
            }
            isPlaying
        }

        // catuall test
        var isPlaying = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            isPlaying = composeTestRule.activity.newPlayer.exoPlayer.value?.isPlaying ?: false
        }
        assert(isPlaying)
    }
}
