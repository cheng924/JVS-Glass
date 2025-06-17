package com.example.jvsglass.ui.teleprompter

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TeleprompterViewModel : ViewModel() {
    // 点击设置按钮后发出的「请求打开设置页」事件
    private val _requestSettings = MutableLiveData<Unit?>()
    val requestSettings: LiveData<Unit?> = _requestSettings

    /** Activity 调用，发送一次打开设置的信号 */
    fun sendRequestSettings() {
        _requestSettings.value = Unit
    }
    /** Fragment 调用，处理完后清除信号 */
    fun clearRequestSettings() {
        _requestSettings.value = null
    }
}