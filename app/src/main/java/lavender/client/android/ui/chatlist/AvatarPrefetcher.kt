package lavender.client.android.ui.chatlist

import android.content.Context
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.*
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.ui.adapter.FlatItem
import lavender.client.android.ui.adapter.ChatListAdapter
import org.json.JSONArray

/**
 * Prefetches avatars for visible and upcoming chat items.
 *
 * Uses Glide's preload API to fetch avatars into memory/disk cache
 * before they're needed, reducing scroll jank.
 */
@Suppress("Unused")
class AvatarPrefetcher(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AvatarPrefetcher"
        private const val PREFETCH_AHEAD = 10 // Number of items to prefetch ahead
        private const val AVATAR_SIZE = 96 // px, same as override in ChatAdapter
    }

    private var avatarUrlCache: Map<String, String> = emptyMap()
    private var currentUsername: String = ""

    // Track already prefetched URLs to avoid duplicates
    private val prefetchedUrls = mutableSetOf<String>()

    /**
     * Update avatar cache and username. Call when allUsers changes.
     */
    fun updateCache(users: List<lavender.client.android.data.proto.UserInfoProto>, username: String) {
        currentUsername = username
        avatarUrlCache = users.associate { it.username to it.avatarUrl }
    }

    /**
     * Attach scroll listener to RecyclerView for prefetching.
     */
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy > 0) { // Only prefetch when scrolling down
                    prefetchVisibleAndAhead(rv)
                }
            }
        })
    }

    /**
     * Prefetch avatars for currently visible items + items ahead.
     */
    private fun prefetchVisibleAndAhead(recyclerView: RecyclerView) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val adapter = recyclerView.adapter as? ChatListAdapter ?: return
        val totalCount = recyclerView.adapter?.itemCount ?: return

        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) return

        val prefetchEnd = minOf(lastVisible + PREFETCH_AHEAD, totalCount - 1)

        scope.launch(Dispatchers.IO) {
            for (position in firstVisible..prefetchEnd) {
                val item = adapter.getItemAtPosition(position) ?: continue
                if (item is FlatItem.ChatItem) {
                    prefetchAvatar(item.chat)
                }
            }
        }
    }

    /**
     * Prefetch a single chat's avatar.
     */
    private fun prefetchAvatar(chat: ChatInfo) {
        val avatarUrl = getAvatarUrl(chat) ?: return
        if (avatarUrl.isEmpty() || prefetchedUrls.contains(avatarUrl)) return

        prefetchedUrls.add(avatarUrl)

        try {
            Glide.with(context)
                .load(avatarUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(AVATAR_SIZE, AVATAR_SIZE)
                .preload()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prefetch avatar: ${e.message}")
        }
    }

    /**
     * Get avatar URL for a chat.
     */
    private fun getAvatarUrl(chat: ChatInfo): String? {
        return when {
            chat.avatarUrl.isNotEmpty() -> chat.avatarUrl
            chat.type == "direct" || chat.isSecret -> {
                val otherUser = getOtherParticipant(chat)
                avatarUrlCache[otherUser]
            }
            else -> null
        }
    }

    /**
     * Get the other participant's username in a direct chat.
     */
    private fun getOtherParticipant(chat: ChatInfo): String {
        return try {
            val arr = JSONArray(chat.participants)
            for (i in 0 until arr.length()) {
                val p = arr.getString(i)
                if (p != currentUsername) return p
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Clear prefetch cache. Call when adapter is reset.
     */
    fun clear() {
        prefetchedUrls.clear()
    }

    /**
     * Prefetch avatars for a specific list of chats.
     * Useful after loading new chats.
     */
    fun prefetchBatch(chats: List<ChatInfo>) {
        scope.launch(Dispatchers.IO) {
            chats.take(PREFETCH_AHEAD).forEach { chat ->
                prefetchAvatar(chat)
            }
        }
    }
}
