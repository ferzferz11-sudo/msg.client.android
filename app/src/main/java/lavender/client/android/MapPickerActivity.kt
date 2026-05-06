package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MapPickerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnSendLocation: MaterialButton
    private lateinit var locationText: TextView
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0
    private var viewMode: Boolean = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            centerOnCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        
        // Load and apply theme
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        
        lavender.client.android.ui.ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                setContentView(R.layout.activity_map_picker)
                lavender.client.android.ui.ThemeManager.applyTheme(this)
                setupUI()
            }
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        webView = findViewById(R.id.mapWebView)
        btnSendLocation = findViewById(R.id.btnSendLocation)
        locationText = findViewById(R.id.locationText)

        viewMode = intent.getBooleanExtra("view_mode", false)
        selectedLat = intent.getDoubleExtra("lat", 0.0)
        selectedLng = intent.getDoubleExtra("lng", 0.0)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        if (viewMode) {
            supportActionBar?.title = getString(R.string.location)
            btnSendLocation.visibility = android.view.View.GONE
            locationText.text = String.format(Locale.US, "Lat: %.5f, Lng: %.5f", selectedLat, selectedLng)
        }

        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(AndroidInterface(), "Android")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (selectedLat != 0.0 || selectedLng != 0.0) {
                    webView.evaluateJavascript("centerOn($selectedLat, $selectedLng)", null)
                } else {
                    checkLocationPermissions()
                }
            }
        }

        val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body { margin: 0; padding: 0; background: #f0f0f0; }
                #map { height: 100vh; width: 100vw; }
                .leaflet-control-attribution { display: none; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([55.7558, 37.6173], 13);
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19
                }).addTo(map);

                var marker;
                var viewMode = $viewMode;

                map.on('click', function(e) {
                    if (viewMode) return;
                    
                    if (marker) {
                        map.removeLayer(marker);
                    }
                    marker = L.marker(e.latlng).addTo(map);
                    Android.onLocationSelected(e.latlng.lat, e.latlng.lng);
                });

                function centerOn(lat, lng) {
                    map.setView([lat, lng], 16);
                    if (marker) map.removeLayer(marker);
                    marker = L.marker([lat, lng]).addTo(map);
                    if (!viewMode) {
                        Android.onLocationSelected(lat, lng);
                    }
                }
            </script>
        </body>
        </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://unpkg.com", html, "text/html", "UTF-8", null)

        btnSendLocation.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("lat", selectedLat)
            resultIntent.putExtra("lng", selectedLng)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            centerOnCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun centerOnCurrentLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            
            if (location != null) {
                webView.post {
                    webView.evaluateJavascript("centerOn(${location.latitude}, ${location.longitude})", null)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    inner class AndroidInterface {
        @JavascriptInterface
        fun onLocationSelected(lat: Double, lng: Double) {
            runOnUiThread {
                selectedLat = lat
                selectedLng = lng
                locationText.text = String.format(Locale.US, "Lat: %.5f, Lng: %.5f", lat, lng)
                btnSendLocation.isEnabled = true
            }
        }
    }
}
