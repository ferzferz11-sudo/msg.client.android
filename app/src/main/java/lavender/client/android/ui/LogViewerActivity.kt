package lavender.client.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import lavender.client.android.R
import lavender.client.android.data.models.AppLog
import lavender.client.android.data.models.LogEntry
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

/**
 * Activity для просмотра логов ошибок
 * Доступна из админ панели
 */
class LogViewerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        ThemeUi.bind(this, SessionManager.session.value.username)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.error_logs)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)

        adapter = LogAdapter { entry ->
            // Долгое нажатие — копировать полный лог
            copyToClipboard(entry.toFullString())
            Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadLogs()
    }

    override fun onResume() {
        super.onResume()
        loadLogs()
    }

    private fun loadLogs() {
        val logs = AppLog.getAll().reversed() // Новые сверху
        adapter.setItems(logs)
        emptyText.visibility = if (logs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        recyclerView.visibility = if (logs.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Log", text))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.log_viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_copy_all -> {
                val text = AppLog.getLogsText()
                copyToClipboard(text)
                Toast.makeText(this, getString(R.string.all_logs_copied), Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_clear -> {
                AppLog.clear()
                loadLogs()
                Toast.makeText(this, getString(R.string.logs_cleared), Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

/**
 * Адаптер для списка логов
 */
class LogAdapter(
    private val onLongClick: (LogEntry) -> Unit
) : RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    private var items = listOf<LogEntry>()

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val timeText: TextView = view.findViewById(R.id.logTime)
        val levelText: TextView = view.findViewById(R.id.logLevel)
        val sourceText: TextView = view.findViewById(R.id.logSource)
        val messageText: TextView = view.findViewById(R.id.logMessage)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.timeText.text = item.formattedTime()
        holder.levelText.text = item.level
        holder.sourceText.text = item.source
        holder.messageText.text = item.message

        // Цвет по уровню
        val levelColor = when (item.level) {
            "ERROR" -> android.graphics.Color.parseColor("#FF5252")
            "WARN" -> android.graphics.Color.parseColor("#FF9800")
            else -> android.graphics.Color.parseColor("#4CAF50")
        }
        holder.levelText.setTextColor(levelColor)

        holder.itemView.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    override fun getItemCount() = items.size

    fun setItems(newItems: List<LogEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
