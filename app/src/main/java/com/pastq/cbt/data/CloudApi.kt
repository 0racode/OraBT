package com.pastq.cbt.data

import com.pastq.cbt.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class SyncRequest(val subject: String, val calls: Int)

@Serializable
data class SyncReply(
    val subject: String = "",
    val added: Int = 0,
    val fetched: Int = 0,
    val calls: Int = 0,
    val remaining_today: Int = 0,
    val error: String? = null,
)

interface CloudService {
    @POST("functions/v1/explain-question")
    suspend fun explain(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Body body: ExplainRequest,
    ): ExplainReply

    @GET("rest/v1/pastq_questions")
    suspend fun disputedIds(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Query("select") select: String = "id",
        @Query("review_status") status: String = "eq.disputed",
        @Query("order") order: String = "id",
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): List<IdOnly>

    @POST("functions/v1/pastq-auth")
    suspend fun account(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Body body: AuthRequest,
    ): AuthReply

    @POST("rest/v1/rpc/pastq_leaderboard")
    suspend fun leaderboard(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Body body: LeaderboardRequest,
    ): List<LeaderRow>

    @POST("functions/v1/submit-score")
    suspend fun submitScore(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Body body: SubmitRequest,
    ): SubmitReply

    @POST("functions/v1/pastq-live")
    suspend fun live(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Header("x-admin-token") admin: String?,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    /** Question reports: players send action "report"; the admin reads them with "admin_reports" and x-admin-token. */
    @POST("functions/v1/pastq-report")
    suspend fun reportCall(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Header("x-admin-token") admin: String?,
        @Body body: kotlinx.serialization.json.JsonObject,
    ): kotlinx.serialization.json.JsonObject

    @POST("functions/v1/sync-aloc")
    suspend fun syncAloc(
        @Header("apikey") key: String,
        @Header("Authorization") auth: String,
        @Body body: SyncRequest,
    ): SyncReply

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
