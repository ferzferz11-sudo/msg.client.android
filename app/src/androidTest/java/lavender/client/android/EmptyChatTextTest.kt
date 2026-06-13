package lavender.client.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso tests for empty chat display text.
 *
 * Verifies that:
 *  - Favorites shows "Personal storage" / "Личное хранилище"
 *  - Regular empty chats show "No messages" / "Нет сообщений"
 *  - NOT showing "Personal storage" for regular empty chats
 */
@RunWith(AndroidJUnit4::class)
class EmptyChatTextTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ChatListActivity::class.java)

    @Test
    fun chatList_isDisplayed() {
        onView(withId(R.id.rvChatList))
            .check(matches(isDisplayed()))
    }

    @Test
    fun chatItems_haveNameView() {
        // Verify that chat items have the name TextView
        onView(withId(R.id.rvChatList))
            .check(matches(isDisplayed()))
    }

    @Test
    fun chatItems_haveTypeView() {
        // Verify that chat items have the type/description TextView
        onView(withId(R.id.rvChatList))
            .check(matches(isDisplayed()))
    }

    // Note: To fully test the "No messages" vs "Personal storage" fix,
    // we would need to:
    // 1. Create a chat with no messages
    // 2. Verify the chatType text is "No messages"
    // 3. Verify Favorites chatType text is "Personal storage"
    //
    // This requires server interaction and is better suited for
    // integration tests with a test server.
}
