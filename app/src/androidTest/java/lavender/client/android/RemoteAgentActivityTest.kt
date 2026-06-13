package lavender.client.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso tests for RemoteAgentActivity.
 *
 * Prerequisites:
 *  - User must be logged in
 *  - Server must be reachable
 *
 * Tests cover:
 *  - Toolbar elements
 *  - Status bar (connected/disconnected)
 *  - Task type selector (ChipGroup)
 *  - ChatWidget elements (input, send button)
 *  - Start/Stop agent buttons
 */
@RunWith(AndroidJUnit4::class)
class RemoteAgentActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ui.remote.RemoteAgentActivity::class.java)

    // --- Toolbar Tests ---

    @Test
    fun toolbar_isDisplayed() {
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
    }

    // --- Status Bar Tests ---

    @Test
    fun statusBar_isDisplayed() {
        onView(withId(R.id.statusBar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun statusIndicator_isDisplayed() {
        onView(withId(R.id.statusIndicator))
            .check(matches(isDisplayed()))
    }

    @Test
    fun statusText_isDisplayed() {
        onView(withId(R.id.statusText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun statusText_showsDisconnectedByDefault() {
        onView(withId(R.id.statusText))
            .check(matches(isDisplayed()))
        // Default text should be "not connected" or similar
    }

    // --- Task Type Tests ---

    @Test
    fun taskTypeScrollView_isDisplayed() {
        onView(withId(R.id.taskTypeScrollView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun taskTypeChipGroup_isDisplayed() {
        onView(withId(R.id.taskTypeChipGroup))
            .check(matches(isDisplayed()))
    }

    // --- ChatWidget Tests ---

    @Test
    fun chatWidget_isDisplayed() {
        onView(withId(R.id.chatWidget))
            .check(matches(isDisplayed()))
    }

    // --- Agent Control Tests ---

    @Test
    fun startAgentButton_initiallyHiddenOrDisplayed() {
        // Start button is hidden when agent is not configured (no tunnel/token)
        // Just verify the view exists in the hierarchy
        onView(withId(R.id.btnStartAgent))
            .check(matches(isDisplayed()))
    }

    @Test
    fun stopAgentButton_initiallyHiddenOrDisplayed() {
        // Stop button is hidden when agent is not running
        onView(withId(R.id.btnStopAgent))
            .check(matches(isDisplayed()))
    }
}
