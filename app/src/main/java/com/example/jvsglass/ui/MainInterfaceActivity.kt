package com.example.jvsglass.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.jvsglass.R
import com.example.jvsglass.bluetooth.BluetoothConstants
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.MyNotificationListenerService
import com.example.jvsglass.utils.ToastUtils
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainInterfaceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_interface)

        checkPermissions()

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
        bottomNav.itemIconTintList = null

        val initialMargin = (bottomNav.layoutParams as ConstraintLayout.LayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            LogUtils.debug("Applying WindowInsets: $insets")
            val density = resources.displayMetrics.density
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            LogUtils.debug("Navigation Bar Bottom: ${navBarInsets.bottom}")
            val navBarHeightDp = navBarInsets.bottom / density
            LogUtils.debug("Navigation Bar Height in DP: $navBarHeightDp")
            view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = initialMargin + navBarInsets.bottom
            }
            insets
        }

        ViewCompat.requestApplyInsets(bottomNav)
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, BluetoothConstants.REQUEST_LOCATION)
        }

        if (!isNotificationListenerEnabled()) {
            promptEnableNotificationListener()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(this.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains("$pkgName/${MyNotificationListenerService::class.java.name}")
    }

    private fun promptEnableNotificationListener() {
        ToastUtils.show(this, "请开启通知访问权限，以使用通知功能")
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}