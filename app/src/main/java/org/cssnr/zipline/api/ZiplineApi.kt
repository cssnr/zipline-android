package org.cssnr.zipline.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.CookieManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.InputStream
import java.net.URLConnection

class ZiplineApi(private val context: Context) {
    val api: ApiService
    private var ziplineUrl: String
    private var ziplineToken: String

    private val preferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private lateinit var cookieJar: SimpleCookieJar
    private lateinit var client: OkHttpClient

    init {
        ziplineUrl = preferences.getString("ziplineUrl", null) ?: ""
        ziplineToken = preferences.getString("ziplineToken", null) ?: ""
        Log.d("ServerApi", "ziplineUrl: $ziplineUrl")
        Log.d("ServerApi", "ziplineToken: $ziplineToken")
        api = createRetrofit().create(ApiService::class.java)
    }

    suspend fun login(host: String, user: String, pass: String): String? {
        Log.d("login", "host: $host")
        Log.d("login", "user/pass: ${user}/${pass}")

        return try {
            val loginResponse = api.postLogin(LoginRequest(user, pass))
            if (loginResponse.isSuccessful) {
                val tokenResponse = api.getToken()
                val cookies = cookieJar.loadForRequest(host.toHttpUrl())
                val cookieManager = CookieManager.getInstance()
                for (cookie in cookies) {
                    Log.d("login", "setCookie: $cookie")
                    //cookieManager.setCookie(host, cookie.toString())
                    cookieManager.setCookie(host, cookie.toString()) {
                        cookieManager.flush()
                    }
                }
                tokenResponse.token
            } else {
                Log.e("login", "Exception: ${loginResponse.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("login", "Exception: ${e.message}")
            null
        }
    }

    suspend fun shorten(url: String, vanity: String?, ziplineUrl: String): Response<ShortResponse> {
        Log.d("upload", "url: $url")
        Log.d("upload", "vanity: $vanity")
        Log.d("upload", "ziplineUrl: $ziplineUrl")

        val response = api.postShort(ziplineToken, ShortRequest(url, vanity, true))
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "token: $token")
            if (token != null) {
                return api.postShort(token, ShortRequest(url, vanity, true))
            }
        }
        return response
    }

    suspend fun upload(
        fileName: String,
        inputStream: InputStream,
    ): Response<FileResponse> {
        Log.d("upload", "fileName: $fileName")
        val fileNameFormat = preferences.getString("file_name_format", null) ?: "random"
        Log.d("upload", "fileNameFormat: $fileNameFormat")
        val multiPart: MultipartBody.Part = inputStreamToMultipart(inputStream, fileName)
        val response = api.postUpload(ziplineToken, fileNameFormat.toString(), multiPart)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "token: $token")
            if (token != null) {
                return api.postUpload(token, fileNameFormat, multiPart)
            }
        }
        return response
    }

    private suspend fun reAuthenticate(api: ApiService, ziplineUrl: String): String? {
        return try {
            val cookies = CookieManager.getInstance().getCookie(ziplineUrl)
            Log.d("reAuthenticate", "cookies: $cookies")
            val httpUrl = ziplineUrl.toHttpUrl()
            cookieJar.setCookie(httpUrl, cookies)

            val tokenResponse = api.getToken()
            Log.d("reAuthenticate", "tokenResponse: $tokenResponse")

            preferences.edit { putString("ziplineToken", tokenResponse.token) }
            ziplineToken = tokenResponse.token
            Log.d("reAuthenticate", "ziplineToken: ${tokenResponse.token}")

            tokenResponse.token
        } catch (e: Exception) {
            Log.e("reAuthenticate", "Exception: ${e.message}")
            null
        }
    }

    private suspend fun inputStreamToMultipart(
        file: InputStream,
        fileName: String
    ): MultipartBody.Part {
        val contentType =
            URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        Log.d("inputStreamToMultipart", "contentType: $contentType")
        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
        val requestBody = bytes.toRequestBody(contentType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("file", fileName, requestBody)
    }

    private fun createRetrofit(): Retrofit {
        val baseUrl = "${ziplineUrl}/api/"
        Log.d("createRetrofit", "baseUrl: $baseUrl")
        cookieJar = SimpleCookieJar()
        client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    interface ApiService {
        @POST("auth/login")
        suspend fun postLogin(@Body request: LoginRequest): Response<Unit>

        @GET("user/token")
        suspend fun getToken(): TokenResponse

        @Multipart
        @POST("upload")
        suspend fun postUpload(
            @Header("authorization") token: String,
            @Header("x-zipline-format") format: String,
            @Part file: MultipartBody.Part,
        ): Response<FileResponse>

        @POST("user/urls")
        suspend fun postShort(
            @Header("authorization") token: String,
            @Body request: ShortRequest,
        ): Response<ShortResponse>
    }

    data class LoginRequest(
        val username: String,
        val password: String,
    )

    data class TokenResponse(
        val token: String
    )

    data class FileResponse(
        val files: List<UploadedFile>
    )

    data class UploadedFile(
        val id: String,
        val type: String,
        val url: String,
    )

    data class ShortRequest(
        val destination: String,
        val vanity: String?,
        val enabled: Boolean,
    )

    data class ShortResponse(
        val id: String,
        val createdAt: String,
        val updatedAt: String,
        val code: String,
        val vanity: String,
        val destination: String,
        val views: Int,
        val maxViews: Int?,
        val enabled: Boolean,
        val userId: String,
        val url: String
    )

    inner class SimpleCookieJar : CookieJar {
        private val cookieStore = mutableMapOf<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }

        fun setCookie(url: HttpUrl, rawCookie: String) {
            val cookies = Cookie.parseAll(url, Headers.headersOf("Set-Cookie", rawCookie))
            cookieStore[url.host] = cookies
        }

        //fun getCookie(host: String, name: String): Cookie? {
        //    return cookieStore[host]?.find { it.name == name }
        //}
    }
}

//data class LoginResponse(
//    val user: TokenUser
//)

//data class TokenUser(
//    val id: String,
//    val username: String,
//    val token: String,
//)

//fun getFileNameFromUri(context: Context, uri: Uri): String? {
//    var fileName: String? = null
//    context.contentResolver.query(uri, null, null, null, null).use { cursor ->
//        if (cursor != null && cursor.moveToFirst()) {
//            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//            if (nameIndex != -1) {
//                fileName = cursor.getString(nameIndex)
//            }
//        }
//    }
//    return fileName
//}
