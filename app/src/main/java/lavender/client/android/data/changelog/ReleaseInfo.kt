package lavender.client.android.data.changelog

import org.json.JSONArray
import org.json.JSONObject

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
) {
    val sizeFormatted: String
        get() = if (size > 1048576) String.format(java.util.Locale.ROOT, "%.1f MB", size / 1048576.0) else String.format(java.util.Locale.ROOT, "%.0f KB", size / 1024.0)

    companion object {
        fun fromJson(json: JSONObject): ReleaseAsset {
            return ReleaseAsset(
                name = json.optString("name", ""),
                downloadUrl = json.optString("browser_download_url", ""),
                size = json.optLong("size", 0)
            )
        }
    }
}

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val htmlUrl: String,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<ReleaseAsset>
) {
    val displayName: String
        get() = name.ifEmpty { tagName }

    val isLatest: Boolean
        get() = !isPrerelease && !isDraft

    companion object {
        fun fromJson(json: JSONObject): ReleaseInfo {
            val assets = mutableListOf<ReleaseAsset>()
            val assetsArray = json.optJSONArray("assets")
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    assets.add(ReleaseAsset.fromJson(assetsArray.getJSONObject(i)))
                }
            }
            return ReleaseInfo(
                tagName = json.optString("tag_name", ""),
                name = json.optString("name", ""),
                body = json.optString("body", ""),
                publishedAt = json.optString("published_at", ""),
                htmlUrl = json.optString("html_url", ""),
                isPrerelease = json.optBoolean("prerelease", false),
                isDraft = json.optBoolean("draft", false),
                assets = assets
            )
        }
    }
}

object ChangelogParser {
    private const val GITHUB_API_URL = "https://api.github.com/repos/ferzferz11-sudo/msg.client.android/releases"

    fun parseReleases(jsonText: String): List<ReleaseInfo> {
        val releases = mutableListOf<ReleaseInfo>()
        val array = JSONArray(jsonText)
        for (i in 0 until array.length()) {
            releases.add(ReleaseInfo.fromJson(array.getJSONObject(i)))
        }
        return releases
    }

    fun getApiUrl(): String = GITHUB_API_URL
}
