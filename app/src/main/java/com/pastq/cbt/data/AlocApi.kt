package com.pastq.cbt.data

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pastq.cbt.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class AlocOptions(val a: String = "", val b: String = "", val c: String = "", val d: String = "")

@Serializable
data class AlocQuestion(
    val id: Int = 0,
    val question: String = "",
    val option: AlocOptions = AlocOptions(),
    val section: String = "",
    val image: String = "",
    val answer: String = "",
    val solution: String = "",
    val examtype: String = "",
    val examyear: String = "",
)

@Serializable
data class AlocResponse(
    val subject: String = "",
    val status: Int = 0,
    val data: List<AlocQuestion> = emptyList(),
)

interface AlocService {
    /** Returns up to 40 random questions for a subject. */
    @GET("m")
    suspend fun many(
        @Header("AccessToken") token: String,
        @Query("subject") subject: String,
        @Query("type") type: String? = null,
        @Query("year") year: String? = null,
    ): AlocResponse
}

object AlocApi {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    val service: AlocService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build())
            }
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.ALOC_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AlocService::class.java)
    }

    val hasToken: Boolean get() = BuildConfig.ALOC_TOKEN.isNotBlank()
}
