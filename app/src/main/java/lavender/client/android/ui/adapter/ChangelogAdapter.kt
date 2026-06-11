package lavender.client.android.ui.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.changelog.MarkdownRenderer
import lavender.client.android.data.changelog.ReleaseInfo
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import java.text.SimpleDateFormat
import java.util.Locale

class ChangelogAdapter(
    private val onAssetClick: (String) -> Unit = {}
) : RecyclerView.Adapter<ChangelogAdapter.ReleaseViewHolder>() {

    private var releases: List<ReleaseInfo> = emptyList()

    // Colors will be resolved from context in onBindViewHolder
    private var textColor: Int = 0
    private var headingColor: Int = 0
    private var linkColor: Int = 0
    private var tagOldColor: Int = 0

    fun setReleases(newReleases: List<ReleaseInfo>) {
        releases = newReleases
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReleaseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_release, parent, false)
        return ReleaseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReleaseViewHolder, position: Int) {
        val release = releases[position]
        val context = holder.itemView.context

        // Resolve colors from ThemeStore for consistent appearance on custom themes
        if (textColor == 0) {
            val theme = ThemeStore.currentTheme()
            val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, android.graphics.Color.BLACK)
            textColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor,
                if (ThemeUtils.isLight(bgColor)) android.graphics.Color.BLACK else 0xFFCAC4D0.toInt())
            headingColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor,
                if (ThemeUtils.isLight(bgColor)) android.graphics.Color.BLACK else 0xFFE0D4F5.toInt())
            linkColor = ThemeUtils.parseSafeColor(theme.primaryColor, 0xFFA78BDA.toInt())
            tagOldColor = 0xFF94A3B8.toInt()
        }

        holder.tvVersionName.text = release.displayName
        holder.tvVersionName.setTextColor(textColor)

        // Tag: Latest / Pre / Tag name
        if (release.isLatest) {
            holder.tvTag.text = context.getString(R.string.tag_latest)
            holder.tvTag.background = ContextCompat.getDrawable(context, R.drawable.bg_tag_latest)
            holder.tvTag.setTextColor(0xFF4ADE80.toInt())
        } else {
            holder.tvTag.text = if (release.isPrerelease) context.getString(R.string.tag_prerelease) else release.tagName
            holder.tvTag.background = ContextCompat.getDrawable(context, R.drawable.bg_tag_old)
            holder.tvTag.setTextColor(0xFF94A3B8.toInt())
        }

        // Date
        holder.tvDate.text = formatDate(release.publishedAt)
        holder.tvDate.alpha = 0.5f

        // File count
        if (release.assets.isNotEmpty()) {
            holder.tvFileCount.text = context.getString(R.string.files_count, release.assets.size)
            holder.tvFileCount.alpha = 0.5f
            holder.tvFileCount.visibility = View.VISIBLE
        } else {
            holder.tvFileCount.visibility = View.GONE
        }

        // Body (markdown rendered)
        if (release.body.isNotEmpty()) {
            val rendered = MarkdownRenderer.render(
                markdown = release.body,
                textColor = textColor,
                headingColor = headingColor,
                linkColor = linkColor,
                codeBgColor = 0x33FFFFFF.toInt()
            )
            holder.tvBody.text = rendered
            holder.tvBody.visibility = View.VISIBLE
        } else {
            holder.tvBody.visibility = View.GONE
        }

        // Assets
        if (release.assets.isNotEmpty()) {
            holder.assetsContainer.visibility = View.VISIBLE
            holder.tvAssetsTitle.text = context.getString(R.string.downloads)
            holder.tvAssetsTitle.alpha = 0.4f
            holder.assetsList.removeAllViews()

            for (asset in release.assets) {
                val assetView = LayoutInflater.from(context)
                    .inflate(R.layout.item_release_asset, holder.assetsList, false)
                val tvAssetName = assetView.findViewById<TextView>(R.id.tvAssetName)
                val tvAssetSize = assetView.findViewById<TextView>(R.id.tvAssetSize)

                tvAssetName.text = asset.name
                tvAssetName.setTextColor(linkColor)
                tvAssetSize.text = asset.sizeFormatted
                tvAssetSize.alpha = 0.5f

                assetView.setOnClickListener {
                    onAssetClick(asset.downloadUrl)
                }

                holder.assetsList.addView(assetView)
            }
        } else {
            holder.assetsContainer.visibility = View.GONE
        }

        // GitHub link
        holder.tvViewOnGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = releases.size

    private fun formatDate(isoDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(isoDate) ?: return isoDate
            val outputFormat = SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("ru"))
            outputFormat.format(date)
        } catch (e: Exception) {
            isoDate
        }
    }

    class ReleaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvVersionName: TextView = itemView.findViewById(R.id.tvVersionName)
        val tvTag: TextView = itemView.findViewById(R.id.tvTag)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvFileCount: TextView = itemView.findViewById(R.id.tvFileCount)
        val tvBody: TextView = itemView.findViewById(R.id.tvBody)
        val assetsContainer: LinearLayout = itemView.findViewById(R.id.assetsContainer)
        val tvAssetsTitle: TextView = itemView.findViewById(R.id.tvAssetsTitle)
        val assetsList: LinearLayout = itemView.findViewById(R.id.assetsList)
        val tvViewOnGithub: TextView = itemView.findViewById(R.id.tvViewOnGithub)
    }
}
