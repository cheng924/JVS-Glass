package com.example.jvsglass.ui.home

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.jvsglass.BuildConfig
import com.example.jvsglass.R
import com.example.jvsglass.network.WeatherService
import com.example.jvsglass.utils.LocationHelper
import com.example.jvsglass.utils.LocationListener
import com.example.jvsglass.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

class ClockControlFragment : Fragment() {
    private val tag = "[ClockControlFragment]"

    private lateinit var flClock: View
    private lateinit var llZoomControl: LinearLayout
    private lateinit var seekZoom: SeekBar
    private lateinit var seekPan: SeekBar
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var ivWeather: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var ivMove: ImageView

    private var isMove: Boolean = true

    // 缩放范围
    private val minScale = 0.5f
    private val maxScale = 1.5f

    private var cityName = "Shenzhen,cn"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_clock_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        flClock = view.findViewById(R.id.fl_clock)
        llZoomControl = view.findViewById(R.id.ll_zoom_control)
        seekZoom = view.findViewById(R.id.seek_scale)
        seekPan = view.findViewById(R.id.sb_pan_control)
        tvTime = view.findViewById(R.id.tv_time)
        tvDate = view.findViewById(R.id.tv_date)
        ivWeather = view.findViewById(R.id.iv_weather)
        tvWeatherTemp = view.findViewById(R.id.tv_weather_temp)
        ivMove = view.findViewById(R.id.iv_move)

        setView()
        setupScaleSeekBar()
        setupTranslateSeekBar()
        startClockAndDateTicker()

        getCity()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1_000L * 60 * 15)
                getCity()
            }
        }
    }

    private fun setView() {
        ivMove.setOnClickListener {
            if (isMove) {
                llZoomControl.visibility = View.VISIBLE
                seekPan.visibility = View.VISIBLE
            } else {
                llZoomControl.visibility = View.INVISIBLE
                seekPan.visibility = View.INVISIBLE
            }
            isMove = !isMove
        }
    }

    private fun setupScaleSeekBar() {
        seekZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val frac = progress / seekBar.max.toFloat()
                val scale = minScale + (maxScale - minScale) * frac
                flClock.scaleX = scale
                flClock.scaleY = scale
                clampTranslationY()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupTranslateSeekBar() {
        seekPan.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val frac = progress / seekBar.max.toFloat()
                val parentH = (flClock.parent as View).height
                val maxTy = parentH - flClock.height * flClock.scaleY
                flClock.translationY = maxTy * frac
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun clampTranslationY() {
        val ty = flClock.translationY
        val parentH = (flClock.parent as View).height
        val maxTy = parentH - flClock.height * flClock.scaleY
        flClock.translationY = ty.coerceIn(0f, maxTy)
    }

    private fun startClockAndDateTicker() {
        val fmtTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val fmtDate = SimpleDateFormat("M月d日 EEEE", Locale.getDefault())
        Timer().schedule(object : TimerTask() {
            override fun run() {
                val now = Date()
                val sTime = fmtTime.format(now)
                val sDate = fmtDate.format(now)
                tvTime.post { tvTime.text = sTime }
                tvDate.post { tvDate.text = sDate }
            }
        }, 0, TimeUnit.MINUTES.toMillis(1))
    }

    private fun getCity() {
        LocationHelper.requestLocationViaGPS(requireContext(), object: LocationListener {
            override fun onSuccess(location: Location) {
                val geo = Geocoder(requireContext(), Locale.getDefault())
                val list = geo.getFromLocation(location.latitude, location.longitude, 1)
                if (!list.isNullOrEmpty()) {
                    val addr = list[0]
                    val city = addr.locality ?: addr.adminArea ?: ""
                    val country = addr.countryCode?.lowercase(Locale.getDefault()) ?: ""
                    if (city.isNotEmpty() && country.isNotEmpty()) {
                        cityName = "$city,$country"
                        LogUtils.info("$tag GPS 定位成功: $cityName")
                        fetchWeather()
                    } else {
                        LogUtils.error("$tag GPS 定位成功，但地址信息不完整")
                    }
                } else {
                    LogUtils.error("$tag GPS 定位成功，但 Geocoder 未返回地址")
                }
            }

            override fun onError(reason: String) {
                LogUtils.error("$tag GPS 定位失败: $reason")
                fetchWeather()
            }
        })
    }

    private fun fetchWeather() {
        if (!isNetworkAvailable()) {
            tvWeatherTemp.text = "–°"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    WeatherService
                        .getCurrentWeather(cityName, BuildConfig.OPEN_WEATHER_MAP_KEY, "metric", "zh_cn")
                        .execute()
                }
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        val tempInt = body.main.temp?.toInt()
                        tvWeatherTemp.text = tempInt?.let { "$it℃" } ?: "–°"

                        body.weather.firstOrNull()?.icon?.let { iconCode ->
                            val iconUrl = "https://openweathermap.org/img/wn/${iconCode}@2x.png"
                            Glide.with(this@ClockControlFragment)
                                .load(iconUrl)
                                .into(ivWeather)
                        }

                        LogUtils.info("$tag 天气解析成功：$body")
                    } else {
                        LogUtils.warn("$tag 响应 body 为空")
                        tvWeatherTemp.text = "–°"
                    }
                } else {
                    val err = response.errorBody()?.string()
                    LogUtils.warn("$tag 天气 API 错误：code=${response.code()} body=$err")
                    tvWeatherTemp.text = "–°"
                }
            } catch (e: Exception) {
                LogUtils.error("获取天气失败", e)
                tvWeatherTemp.text = "–°"
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}