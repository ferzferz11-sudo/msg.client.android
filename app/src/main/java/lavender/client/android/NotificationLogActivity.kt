package lavender.client.android

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import lavender.client.android.data.fcm.NotificationEntry
import lavender.client.android.data.fcm.NotificationHistory
import java.text.SimpleDateFormat
import java.util.*

class NotificationLogActivity : AppCompatActivity() {

    private lateinit var adapterIncoming: NotificationLogAdapter
    private lateinit var adapterOutgoing: NotificationLogAdapter
    private lateinit var emptyLogText: TextView
    private lateinit var recyclerIncoming: RecyclerView
    private lateinit var recyclerOutgoing: RecyclerView

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_log)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Apply theme colors to toolbar
        lavender.client.android.ui.ThemeManager.applyTheme(this)

        emptyLogText = findViewById(R.id.emptyLogText)
        recyclerIncoming = findViewById(R.id.recyclerIncoming)
        recyclerOutgoing = findViewById(R.id.recyclerOutgoing)
        
        adapterIncoming = NotificationLogAdapter()
        adapterOutgoing = NotificationLogAdapter()
        
        recyclerIncoming.layoutManager = LinearLayoutManager(this)
        recyclerIncoming.adapter = adapterIncoming
        
        recyclerOutgoing.layoutManager = LinearLayoutManager(this)
        recyclerOutgoing.adapter = adapterOutgoing

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        if (tabLayout.tabCount == 0) {
            tabLayout.addTab(tabLayout.newTab().setText(R.string.incoming))
            tabLayout.addTab(tabLayout.newTab().setText(R.string.outgoing))
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    recyclerIncoming.isVisible = true
                    recyclerOutgoing.isVisible = false
                    updateList(NotificationHistory.getIncoming())
                } else {
                    recyclerIncoming.isVisible = false
                    recyclerOutgoing.isVisible = true
                    updateList(NotificationHistory.getOutgoing())
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<View>(R.id.btnClearLog).setOnClickListener {
            NotificationHistory.clear()
            adapterIncoming.setData(emptyList())
            adapterOutgoing.setData(emptyList())
            emptyLogText.isVisible = true
        }

        updateList(NotificationHistory.getIncoming())
    }

    private fun updateList(data: List<NotificationEntry>) {
        if (recyclerIncoming.isVisible) adapterIncoming.setData(data)
        else adapterOutgoing.setData(data)
        emptyLogText.isVisible = data.isEmpty()
    }

    private fun applySavedColorScheme() {
        val theme = when (getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", "dark")) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }

    class NotificationLogAdapter : RecyclerView.Adapter<NotificationLogAdapter.ViewHolder>() {
        private var items = listOf<NotificationEntry>()

        fun setData(newItems: List<NotificationEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.body.text = item.body
            holder.time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.notifTitle)
            val body: TextView = v.findViewById(R.id.notifBody)
            val time: TextView = v.findViewById(R.id.notifTime)
        }
    }
}
