package com.pastq.cbt.data

import com.pastq.cbt.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface CloudService {
    @GET("rest/v1/pastq_questions")
    suspend fun page(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "id",
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): List<QuestionEntity>
}

object CloudApi {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    val service: CloudService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CloudService::class.java)
    }
}
