package org.cssnr.zipline.api

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
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
import org.cssnr.zipline.R
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.io.InputStream
import java.net.URLConnection

class ServerApi(private val context: Context, url: String? = null) {

    val api: ApiService
    private var ziplineUrl: String
    private var ziplineToken: String

    private lateinit var cookieJar: SimpleCookieJar

    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    init {
        ziplineUrl = url ?: preferences.getString("ziplineUrl", null) ?: ""
        ziplineToken = preferences.getString("ziplineToken", null) ?: ""
        Log.d("ServerApi[init]", "ziplineUrl: $ziplineUrl")
        Log.d("ServerApi[init]", "ziplineToken: $ziplineToken")
        api = createRetrofit().create(ApiService::class.java)
    }

    suspend fun login(host: String, user: String, pass: String): String? {
        Log.d("Api[login]", "host: $host")
        Log.d("Api[login]", "user/pass: ${user}/${pass}")

        return try {
            val loginResponse = api.postLogin(LoginRequest(user, pass))
            Log.i("Api[login]", "loginResponse.code(): ${loginResponse.code()}")
            if (loginResponse.isSuccessful) {
                val tokenResponse = api.getToken()
                val cookies = cookieJar.loadForRequest(host.toHttpUrl())
                val cookieManager = CookieManager.getInstance()
                for (cookie in cookies) {
                    Log.d("Api[login]", "setCookie: $cookie")
                    //cookieManager.setCookie(host, cookie.toString())
                    cookieManager.setCookie(host, cookie.toString()) {
                        cookieManager.flush()
                    }
                }
                tokenResponse.token
            } else {
                //loginResponse.errorBody()?.string()?.take(200)?.let {
                //    Log.i("Api[login]", "errorBody: $it")
                //}
                Log.i("Api[login]", "errorBody: ${loginResponse.errorBody()?.string()?.take(255)}")
                null
            }
        } catch (e: Exception) {
            Log.e("Api[login]", "Exception: ${e.message}")
            null
        }
    }

    suspend fun shorten(url: String, vanity: String?): Response<ShortResponse> {
        Log.d("Api[upload]", "url: $url")
        Log.d("Api[upload]", "vanity: $vanity")

        val response = api.postShort(ShortRequest(url, vanity, true))
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                return api.postShort(ShortRequest(url, vanity, true))
            }
        }
        return response
    }

    suspend fun upload(fileName: String, inputStream: InputStream): Response<UploadedFiles> {
        Log.d("Api[upload]", "fileName: $fileName")
        val fileNameFormat = preferences.getString("file_name_format", null) ?: "random"
        Log.d("Api[upload]", "fileNameFormat: $fileNameFormat")
        val multiPart: MultipartBody.Part = inputStreamToMultipart(inputStream, fileName)
        val response = api.postUpload(fileNameFormat.toString(), multiPart)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                return api.postUpload(fileNameFormat, multiPart)
            }
        }
        return response
    }

    suspend fun stats(): Response<StatsResponse> {
        Log.d("Api[stats]", "stats")
        val response = api.getStats()
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                return api.getStats()
            }
        }
        return response
    }

    suspend fun recent(take: String = "3"): Response<List<FileResponse>> {
        Log.d("Api[stats]", "stats")
        val response = api.getRecent(take)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                return api.getRecent()
            }
        }
        return response
    }

    suspend fun files(page: Int, perpage: Int = 25): List<FileResponse>? {
        Log.d("Api[files]", "page: $page - perpage: $perpage")
        var response = api.getFiles(page, perpage)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                response = api.getFiles(page, perpage)
            }
        }
        Log.d("Api[files]", "isSuccessful: ${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()
            return body?.page
        }
        return null
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
        fileName: String,
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
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val userAgent = "${context.getString(R.string.app_name)}/${versionName}"
        Log.d("createRetrofit", "versionName: $versionName")
        cookieJar = SimpleCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("authorization", ziplineToken)
                    .build()
                chain.proceed(request)
            }
            .build()
        val moshi = Moshi.Builder().build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
    }

    interface ApiService {
        @POST("auth/login")
        suspend fun postLogin(
            @Body request: LoginRequest,
        ): Response<Unit>

        @GET("user/token")
        suspend fun getToken(): TokenResponse

        @GET("user/stats")
        suspend fun getStats(): Response<StatsResponse>

        @GET("user/recent")
        suspend fun getRecent(
            @Query("take") take: String = "3"
        ): Response<List<FileResponse>>

        @Multipart
        @POST("upload")
        suspend fun postUpload(
            @Header("x-zipline-format") format: String,
            @Part file: MultipartBody.Part,
        ): Response<UploadedFiles>

        @POST("user/urls")
        suspend fun postShort(
            @Body request: ShortRequest,
        ): Response<ShortResponse>

        @GET("user/files")
        suspend fun getFiles(
            @Query("page") amount: Int,
            @Query("perpage") start: Int,
        ): Response<FilesResponse>
    }

    @JsonClass(generateAdapter = true)
    data class LoginRequest(
        val username: String,
        val password: String,
    )

    @JsonClass(generateAdapter = true)
    data class TokenResponse(
        val token: String,
    )

    @JsonClass(generateAdapter = true)
    data class UploadedFiles(
        val files: List<UploadResponse>,
    )

    @JsonClass(generateAdapter = true)
    data class UploadResponse(
        val id: String,
        val type: String,
        val url: String,
    )

    @JsonClass(generateAdapter = true)
    data class ShortRequest(
        val destination: String,
        val vanity: String?,
        val enabled: Boolean,
    )

    @JsonClass(generateAdapter = true)
    data class ShortResponse(
        val id: String,
        val createdAt: String,
        val updatedAt: String,
        val code: String,
        val vanity: String?,
        val destination: String,
        val views: Int,
        val maxViews: Int?,
        val enabled: Boolean,
        val userId: String,
        val url: String,
    )

    @JsonClass(generateAdapter = true)
    data class StatsResponse(
        @Json(name = "filesUploaded") val filesUploaded: Int,
        @Json(name = "favoriteFiles") val favoriteFiles: Int,
        @Json(name = "views") val views: Int,
        @Json(name = "avgViews") val avgViews: Double,
        @Json(name = "storageUsed") val storageUsed: Long,
        @Json(name = "avgStorageUsed") val avgStorageUsed: Double,
        @Json(name = "urlsCreated") val urlsCreated: Int,
        @Json(name = "urlViews") val urlViews: Int,
    )

    @JsonClass(generateAdapter = true)
    data class FileResponse(
        @Json(name = "createdAt") val createdAt: String,
        @Json(name = "updatedAt") val updatedAt: String,
        @Json(name = "deletesAt") val deletesAt: String?,
        @Json(name = "favorite") val favorite: Boolean,
        @Json(name = "id") val id: String,
        @Json(name = "originalName") val originalName: String?,
        @Json(name = "name") val name: String,
        @Json(name = "size") val size: Int,
        @Json(name = "type") val type: String,
        @Json(name = "views") val views: Int,
        @Json(name = "maxViews") val maxViews: Int?,
        @Json(name = "folderId") val folderId: String?,
        //@Json(name = "thumbnail") val thumbnail: String?,
        @Json(name = "password") val password: String?,
        @Json(name = "url") val url: String
    )

    @JsonClass(generateAdapter = true)
    data class FilesResponse(
        @Json(name = "page") val page: List<FileResponse>,
        @Json(name = "total") val total: Int?,
        @Json(name = "pages") val pages: Int?,
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
