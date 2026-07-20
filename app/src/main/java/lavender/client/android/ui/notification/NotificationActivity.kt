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
import lavender.client.android.data.grpc.*
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import com.google.android.material.tabs.TabLayout

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
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout

    private var userId: String = ""
    private var companyId: String = ""
    private var allNotifications: List<lavender.client.android.data.proto.ServerNotificationProto> = emptyList()
    private var selectedTab: Int = 0 // 0=All, 1=Company

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        userId = SessionManager.session.value.userId
        companyId = SessionManager.session.value.companyId

        setupToolbar()
        setupTabs()
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

    private fun setupTabs() {
        tabLayout = findViewById(R.id.tabLayout)
        tabLayout.addTab(tabLayout.newTab().setText(R.string.notifications_all))
        if (companyId.isNotEmpty()) {
            tabLayout.addTab(tabLayout.newTab().setText(R.string.notifications_company))
        }
        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                selectedTab = tab.position
                filterNotifications()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun filterNotifications() {
        val filtered = if (selectedTab == 1 && companyId.isNotEmpty()) {
            allNotifications.filter { it.metadata["company_id"] == companyId }
        } else {
            allNotifications
        }
        adapter.submitList(filtered)
        updateEmptyState(filtered.isEmpty())
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
                allNotifications = history
                filterNotifications()

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
                    if (allNotifications.none { it.id == notif.id }) {
                        allNotifications = listOf(notif) + allNotifications
                        filterNotifications()
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
