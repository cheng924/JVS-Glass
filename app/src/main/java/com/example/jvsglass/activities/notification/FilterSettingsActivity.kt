package com.example.jvsglass.activities.notification

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class FilterSettingsActivity : AppCompatActivity() {
    private lateinit var appItems: MutableList<AppItem>

    @SuppressLint("QueryPermissionsNeeded", "UseKtx")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_settings)

        val packageManager = packageManager
        val apps = packageManager.getInstalledApplications(0)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val enabledPackages = prefs.getStringSet("enabled_packages", emptySet())?.toSet() ?: emptySet()

        appItems = apps.filter { appInfo ->
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 // 只保留非系统应用
        }.map { appInfo ->
            val packageName = appInfo.packageName
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val isEnabled = enabledPackages.contains(packageName)
            val appIcon = packageManager.getApplicationIcon(appInfo)
            AppItem(packageName, appName, appIcon, isEnabled)
        }.sortedBy { it.appName }.toMutableList()

        val adapter = AppListAdapter(appItems)
        findViewById<RecyclerView>(R.id.rv_apps).apply {
            layoutManager = LinearLayoutManager(this@FilterSettingsActivity)
            this.adapter = adapter
        }

        findViewById<Button>(R.id.btn_apply).setOnClickListener {
            val enabledPackageNames = appItems.filter { it.isEnabled }.map { it.packageName }.toSet()
            prefs.edit().putStringSet("enabled_packages", enabledPackageNames).apply()
            finish()
        }
    }
}