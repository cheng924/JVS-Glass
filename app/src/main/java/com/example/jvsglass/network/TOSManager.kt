package com.example.jvsglass.network

import com.example.jvsglass.BuildConfig
import com.example.jvsglass.utils.LogUtils
import com.volcengine.tos.TOSClientConfiguration
import com.volcengine.tos.TOSV2ClientBuilder
import com.volcengine.tos.auth.StaticCredentials
import com.volcengine.tos.comm.common.ACLType
import com.volcengine.tos.model.`object`.ObjectMetaRequestOptions
import com.volcengine.tos.model.`object`.PutObjectBasicInput
import com.volcengine.tos.model.`object`.PutObjectFromFileInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TOSManager private constructor() {

    private val _tosClient by lazy {
        try {
            val credentials = StaticCredentials(
                BuildConfig.TOS_ACCESS_KEY,
                BuildConfig.TOS_SECRET_KEY
            )

            // 配置构建器
            TOSV2ClientBuilder().build(
                TOSClientConfiguration.builder()
                    .region("cn-beijing")
                    .endpoint("tos-cn-beijing.volces.com")
                    .credentials(credentials)  // 设置凭据对象
                    .build()
            )
        } catch (e: Exception) {
            LogUtils.error("[TOSManager] TOS client init failed, $e")
            null
        }
    }

    fun uploadImageFile(
        bucketName: String = "ar-glass-ai",
        objectKey: String,
        filePath: String,
        callback: (String?) -> Unit // 添加回调参数
    ) {
        // 使用IO线程执行异步操作
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val metaOptions = ObjectMetaRequestOptions().apply {
                    setAclType(ACLType.ACL_PUBLIC_READ)
                }
                val basicInput = PutObjectBasicInput().apply {
                    setBucket(bucketName)
                    setKey(objectKey)
                    setOptions(metaOptions)
                }
                val fileInput = PutObjectFromFileInput().apply {
                    setPutObjectBasicInput(basicInput)
                    setFilePath(filePath)
                }
                _tosClient?.putObjectFromFile(fileInput)
                val url = "https://$bucketName.tos-cn-beijing.volces.com/$objectKey"
                // 切换到主线程回调结果
                withContext(Dispatchers.Main) {
                    callback(url)
                }
            } catch (e: Exception) {
                LogUtils.error("[TOS] 上传失败", e)
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    companion object {
        @Volatile
        private var instance: TOSManager? = null

        fun getInstance(): TOSManager =
            instance ?: synchronized(this) {
                instance ?: TOSManager().also { instance = it }
            }
    }
}