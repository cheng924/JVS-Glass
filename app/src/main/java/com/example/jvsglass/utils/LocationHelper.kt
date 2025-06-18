package com.example.jvsglass.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Bundle

interface LocationListener {
    fun onSuccess(location: Location)
    fun onError(reason: String)
}

object LocationHelper {
    @SuppressLint("MissingPermission")
    fun requestLocationViaGPS(context: Context, callback: LocationListener) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            callback.onError("请先打开设备的 GPS 定位开关")
            return
        }

        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
            callback.onSuccess(it)
            return
        }

        lm.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 1000L, 1f,
            object : android.location.LocationListener {
                override fun onLocationChanged(loc: Location) {
                    lm.removeUpdates(this)
                    callback.onSuccess(loc)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
        )
    }
}
