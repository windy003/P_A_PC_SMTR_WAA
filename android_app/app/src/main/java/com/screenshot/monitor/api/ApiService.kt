package com.screenshot.monitor.api

import android.content.Context
import com.google.gson.Gson
import com.screenshot.monitor.model.StatusResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ApiService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun getStatus(pcIp: String): StatusResponse? {
        return try {
            // 智能处理端口号
            val serverAddress = if (pcIp.contains(":")) {
                // 已包含端口号，直接使用
                pcIp
            } else {
                // 未包含端口号，添加默认端口 5001
                "$pcIp:5001"
            }

            val url = "http://$serverAddress/api/status"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    gson.fromJson(body, StatusResponse::class.java)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
