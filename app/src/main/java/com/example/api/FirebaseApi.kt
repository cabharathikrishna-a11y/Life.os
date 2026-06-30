package com.example.api

import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Body
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.JsonClass

import com.example.ui.FocusRecord

@JsonClass(generateAdapter = true)
data class UserRemote(
    val password: String = "",
    val name: String? = null,
    val nickname: String? = null,
    val emoji: String? = null,
    val isFocusing: Boolean? = null,
    val accumulatedTimeMs: Long = 0L,
    val lastResumeTimeMs: Long? = null,
    val currentTaskTitle: String? = null,
    val todaysFocusRecords: List<FocusRecord>? = null,
    val isStopwatchMode: Boolean? = null,
    val lastUpdatedTimestamp: Long? = null,
    val lastButtonClicked: String? = null,
    val lastButtonClickedTimestamp: Long? = null,
    val focusStatus: String? = null,
    val currentTag: String? = null,
    val isGoogleUser: Boolean? = null,
    val email: String? = null
)

@JsonClass(generateAdapter = true)
data class BellSignal(
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val timestamp: Long = 0L,
    val isProcessed: Boolean = false
)

interface FirebaseApi {
    @GET("users.json")
    suspend fun getUsers(): retrofit2.Response<Map<String, UserRemote>?>

    @PUT("users/{username}.json")
    suspend fun putUser(
        @Path("username") username: String,
        @Body user: UserRemote
    ): UserRemote

    @DELETE("users/{username}.json")
    suspend fun deleteUser(
        @Path("username") username: String
    ): retrofit2.Response<Unit>

    @GET("bells/{username}.json")
    suspend fun getBellSignal(
        @Path("username") username: String
    ): retrofit2.Response<BellSignal?>

    @PUT("bells/{username}.json")
    suspend fun putBellSignal(
        @Path("username") username: String,
        @Body signal: BellSignal?
    ): BellSignal?

    @GET("requests/{username}.json")
    suspend fun getPeerRequests(
        @Path("username") username: String
    ): retrofit2.Response<Map<String, Boolean>?>

    @PUT("requests/{username}/{requester}.json")
    suspend fun putPeerRequest(
        @Path("username") username: String,
        @Path("requester") requester: String,
        @Body request: Boolean
    ): Boolean

    @DELETE("requests/{username}/{requester}.json")
    suspend fun deletePeerRequest(
        @Path("username") username: String,
        @Path("requester") requester: String
    ): retrofit2.Response<Unit>

    @GET("transfer/{requester}/{provider}.json")
    suspend fun getTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String
    ): retrofit2.Response<List<FocusRecord>?>

    @PUT("transfer/{requester}/{provider}.json")
    suspend fun putTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String,
        @Body records: List<FocusRecord>?
    ): List<FocusRecord>?

    @DELETE("transfer/{requester}/{provider}.json")
    suspend fun deleteTransferredData(
        @Path("requester") requester: String,
        @Path("provider") provider: String
    ): retrofit2.Response<Unit>
}

object FirebaseClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: FirebaseApi by lazy {
        Retrofit.Builder()
            .baseUrl(FirebaseConfig.DATABASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FirebaseApi::class.java)
    }
}
