package org.cssnr.zipline

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.CookieManager
import androidx.core.content.edit
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
import retrofit2.HttpException
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

    private val preferences: SharedPreferences =
        context.getSharedPreferences("default_preferences", MODE_PRIVATE)
    private lateinit var cookieJar: SimpleCookieJar
    private lateinit var client: OkHttpClient

    suspend fun login(host: String, user: String, pass: String): String? {
        Log.d("login", "host: $host")
        Log.d("login", "user/pass: ${user}/${pass}")

        val api = createRetrofit(host).create(ApiService::class.java)

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

    suspend fun upload(uri: Uri, ziplineUrl: String): FileResponse? {
        Log.d("upload", "uri: $uri")
        val fileName = getFileNameFromUri(context, uri)
        Log.d("upload", "fileName: $fileName")
        val ziplineToken = preferences.getString("ziplineToken", null)
        Log.d("upload", "ziplineToken: $ziplineToken")
        val inputStream = context.contentResolver.openInputStream(uri)
        if (fileName == null || ziplineToken == null || inputStream == null) {
            Log.e("upload", "fileName/inputStream/ziplineToken is null")
            return null
        }
        val api = createRetrofit(ziplineUrl).create(ApiService::class.java)
        val multiPart: MultipartBody.Part = inputStreamToMultipart(inputStream, fileName)
        return try {
            api.postUpload(ziplineToken, multiPart)
        } catch (e: HttpException) {
            Log.e("upload", "HttpException: ${e.message}")
            val response = e.response()?.errorBody()?.string()
            Log.d("upload", "response: $response")
            if (e.code() == 401) {
                try {
                    val token = reAuthenticate(api, ziplineUrl)
                    if (!token.isNullOrEmpty()) {
                        return api.postUpload(token, multiPart)
                    }
                } catch (e: Exception) {
                    Log.w("upload", "Exception: ${e.message}")
                }
            }
            null
        } catch (e: Exception) {
            Log.e("upload", "Exception: ${e.message}")
            null
        }
    }

    private suspend fun reAuthenticate(api: ApiService, ziplineUrl: String): String? {
        return try {
            val cookies = CookieManager.getInstance().getCookie(ziplineUrl)
            Log.d("reAuthenticate", "cookies: $cookies")
            val httpUrl = ziplineUrl.toHttpUrl()
            cookieJar.setCookie(httpUrl, cookies)

            val tokenResponse = api.getToken()
            Log.d("reAuthenticate", "tokenResponse: $tokenResponse")

            val sharedPreferences =
                context.getSharedPreferences("default_preferences", MODE_PRIVATE)
            sharedPreferences.edit { putString("ziplineToken", tokenResponse.token) }
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

    private fun createRetrofit(host: String): Retrofit {
        //Log.d("createRetrofit", "host: $host")
        //var url = host
        //if (url.endsWith("/")) {
        //    url = url.substring(0, url.length - 1)
        //}
        //Log.d("createRetrofit", "url: $url")
        val baseUrl = "${host}/api/"
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
            @Part file: MultipartBody.Part,
        ): FileResponse
    }

    data class FileResponse(
        val files: List<UploadedFile>
    )

    data class UploadedFile(
        val id: String,
        val type: String,
        val url: String,
    )


    data class LoginRequest(
        val username: String,
        val password: String,
    )

    data class TokenResponse(
        val token: String
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


fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null).use { cursor ->
        if (cursor != null && cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    return fileName
}
