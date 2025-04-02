package com.example.jvsglass.activities.jvsai

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import androidx.core.net.toUri
import com.bumptech.glide.Glide

class FullScreenImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        val imageUri = intent.getStringExtra("image_uri")?.toUri()
        val imageView: ImageView = findViewById(R.id.ivFullScreen)

        Glide.with(this)
            .load(imageUri)
            .into(imageView)

        imageView.setOnClickListener { finish() }
    }
}