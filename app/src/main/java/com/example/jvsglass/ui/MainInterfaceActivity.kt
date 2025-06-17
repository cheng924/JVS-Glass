package com.example.jvsglass.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.jvsglass.R
import com.example.jvsglass.utils.LogUtils
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

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            LogUtils.info("Applying WindowInsets: $insets")
            val density = resources.displayMetrics.density
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            LogUtils.info("Navigation Bar Bottom: ${navBarInsets.bottom}")
            val navBarHeightDp = navBarInsets.bottom / density
            LogUtils.info("Navigation Bar Height in DP: $navBarHeightDp")
            view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = initialMargin + navBarInsets.bottom
            }
            insets
        }

        ViewCompat.requestApplyInsets(bottomNav)
    }
}