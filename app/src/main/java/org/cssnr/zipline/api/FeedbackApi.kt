package org.cssnr.zipline.api

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.cssnr.zipline.R
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

const val HOOK_ID = "1376004891192856586"
const val RELAY_URL = "https://relay.cssnr.com/"

class FeedbackApi(val context: Context) {

    val api: ApiService

    val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
    val userAgent = "${context.packageName}/${versionName}"

    init {
        api = createRetrofit().create(ApiService::class.java)
    }

    suspend fun sendFeedback(messageText: String): Response<Unit> {
        Log.d("sendFeedback", "messageText: $messageText")
        val feedbackText =
            "**${context.getString(R.string.app_name)}** `v${versionName}`\n```\n${messageText}\n```"
        Log.d("sendFeedback", "feedbackText: $feedbackText")
        val message = Message(content = feedbackText)
        Log.d("sendFeedback", "message: $message")
        return try {
            api.postDiscord(message)
        } catch (e: Exception) {
            val errorBody = e.toString().toResponseBody("text/plain".toMediaTypeOrNull())
            Response.error(520, errorBody)
        }
    }

    data class Message(
        val content: String
    )

    interface ApiService {
        @POST(("discord/${HOOK_ID}"))
        suspend fun postDiscord(
            @Body message: Message
        ): Response<Unit>
    }

    private fun createRetrofit(): Retrofit {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .build()
                chain.proceed(request)
            }
            .build()
        val gson = GsonBuilder().create()
        return Retrofit.Builder()
            .baseUrl(RELAY_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
    }
}
