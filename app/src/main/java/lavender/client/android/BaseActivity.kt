package lavender.client.android

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Base activity class that handles common logic, such as locale management.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        
        val config = newBase.resources.configuration
        config.setLocale(locale)
        
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
}
