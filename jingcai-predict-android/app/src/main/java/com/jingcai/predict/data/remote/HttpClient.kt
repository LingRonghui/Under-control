package com.jingcai.predict.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 全局 HTTP 客户端 */
object HttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
}
