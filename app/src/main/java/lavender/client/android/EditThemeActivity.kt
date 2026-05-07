package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class EditThemeActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru" // Default to Russian for first launch
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        // Deprecated screen: ThemePaletteActivity is the new editor.
        startActivity(
            Intent(this, ThemePaletteActivity::class.java).apply {
                putExtras(intent)
            }
        )
        finish()
        return
    }

}
