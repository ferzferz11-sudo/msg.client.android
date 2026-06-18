package lavender.client.android.ui.notification

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.data.grpc.GrpcClientExtensions.*

/**
 * NotificationActivity — экран просмотра серверных уведомлений.
 *
 * Показывает историю уведомлений и real-time обновления через SubscribeNotifications.
 * Поддерживает отметку всех уведомлений как прочитанных.
 * Непрочитанные уведомления выделяются визуально (bold title + accent background).
 */
class NotificationActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout

    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        userId = SessionManager.session.value.userId

        setupToolbar()
        setupRecyclerView()
        observeNotifications()
        ThemeUi.bind(this, userId)

        // Load history
        loadHistory()

        // Subscribe to real-time notifications
        GrpcClient.subscribeNotifications(userId)

        Log.d("NotificationActivity", "onCreate: userId=$userId")
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter { notif ->
            // Mark as read when clicked
            if (!notif.isRead) {
                lifecycleScope.launch {
                    try {
                        GrpcClient.markNotificationsRead(userId, listOf(notif.id))
                    } catch (e: Exception) {
                        Log.e("NotificationActivity", "Failed to mark notification as read", e)
                    }
                }
            }
        }
        recyclerView = findViewById(R.id.notificationsRecyclerView)
        emptyState = findViewById(R.id.emptyState)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val history = GrpcClient.getNotificationHistory(userId)
                adapter.submitList(history)
                updateEmptyState(history.isEmpty())

                // Mark all loaded notifications as read
                val unreadIds = history.filter { !it.isRead }.map { it.id }
                if (unreadIds.isNotEmpty()) {
                    launch {
                        try {
                            GrpcClient.markNotificationsRead(userId, unreadIds)
                        } catch (e: Exception) {
                            Log.e("NotificationActivity", "Failed to mark notifications as read", e)
                        }
                    }
                }

                Log.d("NotificationActivity", "Loaded ${history.size} notifications (${unreadIds.size} unread)")
            } catch (e: Exception) {
                Log.e("NotificationActivity", "Failed to load notification history", e)
                updateEmptyState(true)
            }
        }
    }

    private fun observeNotifications() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                GrpcClient.serverNotifications.collect { notif ->
                    Log.d("NotificationActivity", "New notification: ${notif.type} - ${notif.title}")
                    // Prepend new notification to the list
                    val currentList = adapter.currentList.toMutableList()
                    // Avoid duplicates
                    if (currentList.none { it.id == notif.id }) {
                        currentList.add(0, notif)
                        adapter.submitList(currentList)
                        updateEmptyState(false)
                    }
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unsubscribe is handled by the gRPC scope cancellation
    }
}
