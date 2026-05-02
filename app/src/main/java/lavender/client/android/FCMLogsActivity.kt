package lavender.client.android

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.FCMLogEntryProto
import java.util.*
import androidx.core.graphics.toColorInt

class FCMLogsActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private lateinit var adapter: FCMLogsAdapter
    private lateinit var progressBar: View

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fcm_logs)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Handle window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
        
        lavender.client.android.ui.ThemeManager.applyTheme(this)

        progressBar = findViewById(R.id.progressBar)
        val recycler = findViewById<RecyclerView>(R.id.recyclerLogs)
        
        adapter = FCMLogsAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    private fun loadLogs() {
        progressBar.visibility = View.VISIBLE
        grpcClient.getFCMLogs { logs ->
            runOnUiThread {
                progressBar.visibility = View.GONE
                adapter.setData(logs)
            }
        }
    }

    private fun applySavedColorScheme() {
        setTheme(R.style.Theme_Lavender_Dark_NoActionBar)
    }

    class FCMLogsAdapter : RecyclerView.Adapter<FCMLogsAdapter.ViewHolder>() {
        private var items = listOf<FCMLogEntryProto>()

        fun setData(newItems: List<FCMLogEntryProto>) {
            items = newItems.reversed() // Show newest first
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fcm_log, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.timestamp.text = item.timestamp
            holder.level.text = item.level
            holder.message.text = item.message
            
            val color = when (item.level) {
                "ERROR"   -> Color.RED
                "WARN"    -> "#FFA500".toColorInt()
                "SUCCESS" -> "#4CAF50".toColorInt()
                else      -> "#2196F3".toColorInt()
            }
            holder.level.setTextColor(color)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val timestamp: TextView = v.findViewById(R.id.logTimestamp)
            val level: TextView = v.findViewById(R.id.logLevel)
            val message: TextView = v.findViewById(R.id.logMessage)
        }
    }
}
