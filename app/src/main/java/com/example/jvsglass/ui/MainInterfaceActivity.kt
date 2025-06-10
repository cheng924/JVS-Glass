package com.example.jvsglass.ui

import android.os.Bundle
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.jvsglass.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainInterfaceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_interface)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
        bottomNav.itemIconTintList = null

        val initialMargin = (bottomNav.layoutParams as ConstraintLayout.LayoutParams).bottomMargin
        val initialPadding = bottomNav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = initialMargin + navBarInsets.bottom
            }
            view.updatePadding(bottom = initialPadding + navBarInsets.bottom)
            insets
        }

        ViewCompat.requestApplyInsets(bottomNav)
    }
}