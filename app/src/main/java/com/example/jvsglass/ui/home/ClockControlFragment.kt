package com.example.jvsglass.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.jvsglass.BuildConfig
import com.example.jvsglass.R
import com.example.jvsglass.network.WeatherService
import com.example.jvsglass.utils.LocationHelper
import com.example.jvsglass.utils.LocationListener
import com.example.jvsglass.utils.LogUtils
import com.tencent.mmkv.MMKV
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
import kotlin.math.roundToInt

class ClockControlFragment : Fragment() {
    private val tag = "[ClockControlFragment]"
    private var cityName = "Shenzhen,cn"

    private lateinit var flClock: View
    private lateinit var llZoomControl: LinearLayout
    private lateinit var seekPan: SeekBar
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var ivWeather: ImageView
    private lateinit var tvWeatherTemp: TextView
    private lateinit var ivMove: ImageView
    private lateinit var tvHeight: TextView

    private var isMove: Boolean = true
    private var dragStartTranslationY = 0f
    private var dragStartRawY = 0f
    private var lastDragProgress = -1

    private val minScale = 1.0f
    private val maxScale = 1.5f
    private val usableRatio = 0.5f
    private val updateWeatherInterval = 1_000L * 60 * 30

    private lateinit var seekDistance: SeekBar
    private lateinit var tvDistance: TextView
    private var lastPos = -1

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
        seekDistance = view.findViewById(R.id.seek_distance)
        tvDistance = view.findViewById(R.id.tvDistance)
        seekPan = view.findViewById(R.id.sb_pan_control)
        tvTime = view.findViewById(R.id.tv_time)
        tvDate = view.findViewById(R.id.tv_date)
        ivWeather = view.findViewById(R.id.iv_weather)
        tvWeatherTemp = view.findViewById(R.id.tv_weather_temp)
        ivMove = view.findViewById(R.id.iv_move)
        tvHeight = view.findViewById(R.id.tv_height)

        setView()
        setupScaleSeekBar()
        setupFlClockDrag()
        setupTranslateSeekBar()
        startClockAndDateTicker()
        getMoveData()

        getCity()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(updateWeatherInterval)
                fetchWeather()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setView() {
        ivMove.setOnClickListener {
            if (isMove) {
                llZoomControl.visibility = View.VISIBLE
                seekPan.visibility = View.VISIBLE
                tvHeight.visibility = View.VISIBLE
            } else {
                llZoomControl.visibility = View.INVISIBLE
                seekPan.visibility = View.INVISIBLE
                tvHeight.visibility = View.INVISIBLE
                saveMoveData()
            }
            isMove = !isMove
        }
    }

    private fun setupScaleSeekBar() {
        seekDistance.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser || progress == lastPos) return
                lastPos = progress

                vibrationAction()

                tvDistance.text = "${(progress + 1) * 0.5 + 0.5}m"

                val frac  = progress / 8f
                val scale = minScale + (maxScale - minScale) * frac
                flClock.scaleX = scale
                flClock.scaleY = scale
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupTranslateSeekBar() {
        seekPan.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (progress in 3..9 ) {
                    vibrationAction()

                    val parentH = (flClock.parent as View).height
                    val maxTy = (parentH - flClock.height * flClock.scaleY) * usableRatio
                    val frac = (progress - 3) / 6f
                    val desiredY = -maxTy / 2 + frac * maxTy
                    flClock.translationY = desiredY.coerceIn(-maxTy / 2, maxTy / 2)
                } else {
                    if (progress < 3) {seekBar.progress = 3}
                    if (progress > 9) {seekBar.progress = 9}
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFlClockDrag() {
        flClock.setOnTouchListener { v, ev ->
            val parentH = (flClock.parent as View).height
            val maxTy = (parentH - flClock.height * flClock.scaleY) * usableRatio
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawY = ev.rawY
                    dragStartTranslationY = flClock.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.rawY - dragStartRawY
                    val newTy = (dragStartTranslationY + dy).coerceIn(-maxTy/2, maxTy/2)
                    flClock.translationY = newTy

                    val frac = (newTy + maxTy/2) / maxTy
                    val prog = (frac * 6f).roundToInt().coerceIn(0,6) + 3
                    if (prog != lastDragProgress) {
                        vibrationAction()
                        lastDragProgress = prog
                    }
                    seekPan.progress = prog
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> { true }
                else -> false
            }
        }
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
                            ivWeather.setImageResource(getWeatherIcon(iconCode))
//                            val iconUrl = "https://openweathermap.org/img/wn/${iconCode}@2x.png"
//                            Glide.with(this@ClockControlFragment).load(iconUrl).into(ivWeather)
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

    private fun vibrationAction() {
        val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    private fun getWeatherIcon(iconCode: String): Int {
        return when (iconCode.substring(0, 2)) {
            "01" -> R.drawable.ic_weather_clear_sky
            "02" -> R.drawable.ic_weather_few_clouds
            "03" -> R.drawable.ic_weather_scattered_clouds
            "04" -> R.drawable.ic_weather_broken_clouds
            "09" -> R.drawable.ic_weather_shower_rain
            "10" -> R.drawable.ic_weather_rain
            "11" -> R.drawable.ic_weather_thunderstorm
            "13" -> R.drawable.ic_weather_snow
            "50" -> R.drawable.ic_weather_mist
            else -> R.drawable.ic_weather_clear_sky
        }
    }

    private fun saveMoveData() {
        val mmkv = MMKV.defaultMMKV()
        mmkv.encode("horizontalData", seekDistance.progress)
        mmkv.encode("verticalData", seekPan.progress)
    }

    private fun getMoveData() {
        val mmkv = MMKV.defaultMMKV()
        val horizontalProgress = mmkv.decodeInt("horizontalData", 0)
        val verticalProgress = mmkv.decodeInt("verticalData", 6)

        seekDistance.progress = horizontalProgress
        seekPan.progress = verticalProgress

        flClock.post {
            // 更新flClock的缩放
            val frac = horizontalProgress / 8f
            val scale = minScale + (maxScale - minScale) * frac
            flClock.scaleX = scale
            flClock.scaleY = scale

            // 更新flClock的位置
            val parentH = (flClock.parent as View).height
            val maxTy = (parentH - flClock.height * scale) * usableRatio
            val fracY = (verticalProgress - 3) / 6f
            val desiredY = -maxTy / 2 + fracY * maxTy
            flClock.translationY = desiredY.coerceIn(-maxTy / 2, maxTy / 2)
        }
    }
}