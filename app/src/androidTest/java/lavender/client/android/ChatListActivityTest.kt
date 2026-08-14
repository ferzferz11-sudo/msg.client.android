package lavender.client.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso tests for ChatListActivity.
 *
 * Prerequisites:
 *  - User must be logged in (SessionManager has valid token)
 *  - Server must be reachable
 *
 * Tests cover:
 *  - Toolbar elements visibility
 *  - Chat list (RecyclerView) presence
 *  - FAB buttons visibility
 */
@RunWith(AndroidJUnit4::class)
class ChatListActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ChatListActivity::class.java)

    // --- Toolbar Tests ---

    @Test
    fun toolbar_isDisplayed() {
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
    }

    @Test
    fun toolbarTitle_isDisplayed() {
        onView(withId(R.id.tvToolbarTitle))
            .check(matches(isDisplayed()))
    }

    @Test
    fun toolbarUserAvatar_isDisplayed() {
        onView(withId(R.id.ivToolbarUserAvatar))
            .check(matches(isDisplayed()))
    }

    // --- Chat List Tests ---

    @Test
    fun chatListRecyclerView_isDisplayed() {
        onView(withId(R.id.rvChatList))
            .check(matches(isDisplayed()))
    }

    @Test
    fun swipeRefreshLayout_isDisplayed() {
        onView(withId(R.id.srlChatList))
            .check(matches(isDisplayed()))
    }

    // --- FAB Tests ---

    @Test
    fun fabAddChat_isDisplayed() {
        onView(withId(R.id.fabAddChat))
            .check(matches(isDisplayed()))
    }

    @Test
    fun fabAi_isDisplayed() {
        onView(withId(R.id.fabAi))
            .check(matches(isDisplayed()))
    }

    // --- Navigation Tests ---

    @Test
    fun fabAddChat_clickable() {
        onView(withId(R.id.fabAddChat))
            .check(matches(isClickable()))
    }

    @Test
    fun fabAi_clickable() {
        onView(withId(R.id.fabAi))
            .check(matches(isClickable()))
    }

    @Test
    fun toolbarUserAvatar_clickable() {
        onView(withId(R.id.ivToolbarUserAvatar))
            .check(matches(isClickable()))
    }
}
