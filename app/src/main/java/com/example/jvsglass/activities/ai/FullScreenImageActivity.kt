package com.example.jvsglass.activities.ai

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import com.bumptech.glide.Glide
import java.io.File

class FullScreenImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        findViewById<ImageView>(R.id.ivFullScreen).apply {
            intent.getStringExtra("image_uri")?.let { path ->
                Glide.with(this@FullScreenImageActivity)
                    .load(File(path))
                    .into(this)
            }

            setOnClickListener { finish() }
        }
    }
}