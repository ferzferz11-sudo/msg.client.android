package lavender.client.android

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class FullScreenImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        val imageUrl = intent.getStringExtra("image_url") ?: ""
        val imageView = findViewById<ImageView>(R.id.fullScreenImageView)
        val btnClose = findViewById<ImageButton>(R.id.btnClose)

        if (imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .into(imageView)
        }

        btnClose.setOnClickListener {
            finish()
        }
    }
}