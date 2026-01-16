package com.screenshot.monitor.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.screenshot.monitor.model.StatusResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ApiService(private val context: Context) {

    companion object {
        private const val TAG = "ApiService"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun getStatus(pcIp: String): StatusResponse? {
        return try {
            // 智能处理协议和端口号
            val url = when {
                // 如果已经包含 http:// 或 https://，直接使用并添加路径
                pcIp.startsWith("http://", ignoreCase = true) ||
                pcIp.startsWith("https://", ignoreCase = true) -> {
                    // 移除末尾的斜杠（如果有）
                    val baseUrl = pcIp.trimEnd('/')
                    "$baseUrl/api/status"
                }
                // 如果是域名（包含点号但不是纯 IP），使用 HTTPS
                pcIp.contains(".") && !pcIp.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+.*")) -> {
                    val serverAddress = if (pcIp.contains(":")) pcIp else pcIp
                    "https://${serverAddress.trimEnd('/')}/api/status"
                }
                // 如果是局域网 IP 地址，使用 HTTP
                else -> {
                    val serverAddress = if (pcIp.contains(":")) {
                        // 已包含端口号，直接使用
                        pcIp
                    } else {
                        // 未包含端口号，添加默认端口 5001
                        "$pcIp:5001"
                    }
                    "http://$serverAddress/api/status"
                }
            }

            Log.d(TAG, "Requesting URL: $url on Android ${android.os.Build.VERSION.SDK_INT}")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            Log.d(TAG, "Response code: ${response.code}, successful: ${response.isSuccessful}")

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    Log.d(TAG, "Response body: $body")
                    gson.fromJson(body, StatusResponse::class.java)
                } else {
                    Log.w(TAG, "Response body is null")
                    null
                }
            } else {
                Log.w(TAG, "Request failed with code: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during API call on Android ${android.os.Build.VERSION.SDK_INT}: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
}
