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
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
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
        // TODO: Create an Object that inherits in this order:
        //  Hard Coded Defaults > User Defaults > Per Upload Arguments
        val format = preferences.getString("file_name_format", null) ?: "random"
        Log.d("Api[upload]", "format: $format")
        val originalName = preferences.getBoolean("file_name_original", true)
        Log.d("Api[upload]", "originalName: $originalName")
        val compression = preferences.getInt("file_name_compression", 0).takeIf { it != 0 }
        Log.d("Api[upload]", "compression: $compression")
        val deletesAt = preferences.getString("file_deletes_at", null)
        Log.d("Api[upload]", "deletesAt: $deletesAt")
        val folder = preferences.getString("file_folder", null)
        Log.d("Api[upload]", "folder: $folder")

        val part: MultipartBody.Part = inputStreamToMultipart(inputStream, fileName)
        val response = api.postUpload(part, format, originalName, compression, deletesAt, folder)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                return api.postUpload(part, format, originalName, compression, deletesAt, folder)
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

    //suspend fun recent(take: String = "3"): Response<List<FileResponse>> {
    //    Log.d("Api[stats]", "stats")
    //    val response = api.getRecent(take)
    //    if (response.code() == 401) {
    //        val token = reAuthenticate(api, ziplineUrl)
    //        Log.d("Api[upload]", "reAuthenticate: token: $token")
    //        if (token != null) {
    //            return api.getRecent()
    //        }
    //    }
    //    return response
    //}

    suspend fun folders(noincl: Boolean = false): List<FolderResponse>? {
        Log.d("Api[folders]", "noincl: $noincl")
        var response = api.getFolders(noincl)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                response = api.getFolders(noincl)
            }
        }
        Log.d("Api[files]", "code: ${response.code()}")
        Log.d("Api[files]", "isSuccessful: ${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()
            return body
        }
        return null
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

    suspend fun editSingle(fileId: String, editRequest: FileEditRequest): FileEditRequest? {
        Log.d("Api[editSingle]", "fileId: $fileId - $editRequest")
        var response = api.editFile(fileId, editRequest)
        Log.d("Api[editSingle]", "response: $response")
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[editSingle]", "reAuthenticate: token: $token")
            if (token != null) {
                response = api.editFile(fileId, editRequest)
            }
        }
        Log.d("Api[editSingle]", "isSuccessful: ${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()
            return body
        }
        return null
    }

    suspend fun editMany(transaction: FilesTransaction): Int? {
        //Log.d("Api[deleteMany]", "files: $files")
        Log.d("Api[deleteMany]", "transaction: $transaction")
        var response = api.editFiles(transaction)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                response = api.editFiles(transaction)
            }
        }
        Log.d("Api[files]", "isSuccessful: ${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()
            return body?.count
        }
        return null
    }

    suspend fun deleteMany(files: List<String>): Int? {
        //Log.d("Api[deleteMany]", "files: $files")
        val transaction = FilesTransaction(files = files)
        Log.d("Api[deleteMany]", "transaction: $transaction")
        var response = api.deleteFiles(transaction)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                response = api.deleteFiles(transaction)
            }
        }
        Log.d("Api[files]", "isSuccessful: ${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()
            return body?.count
        }
        return null
    }

    suspend fun deleteSingle(fileId: String): FileResponse? {
        //Log.d("Api[deleteMany]", "files: $files")
        var response = api.deleteFile(fileId)
        if (response.code() == 401) {
            val token = reAuthenticate(api, ziplineUrl)
            Log.d("Api[upload]", "reAuthenticate: token: $token")
            if (token != null) {
                response = api.deleteFile(fileId)
            }
        }
        Log.d("Api[files]", "isSuccessful: ${response.isSuccessful}")
        if (response.isSuccessful) {
            val body = response.body()
            return body
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
            @Part file: MultipartBody.Part,
            @Header("x-zipline-format") format: String,
            @Header("x-zipline-original-name") originalName: Boolean = true,
            @Header("x-zipline-image-compression-percent") compression: Int? = 100,
            @Header("x-zipline-deletes-at") deletesAt: String? = null,
            @Header("x-zipline-folder") folder: String? = null,
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

        @PATCH("user/files/{fileId}")
        suspend fun editFile(
            @Path("fileId") fileId: String,
            @Body request: FileEditRequest,
        ): Response<FileEditRequest>

        @DELETE("user/files/{fileId}")
        suspend fun deleteFile(
            @Path("fileId") fileId: String,
        ): Response<FileResponse>

        @PATCH("user/files/transaction")
        suspend fun editFiles(
            @Body request: FilesTransaction,
        ): Response<CountResponse>

        @HTTP(method = "DELETE", path = "user/files/transaction", hasBody = true)
        suspend fun deleteFiles(
            @Body request: FilesTransaction,
        ): Response<CountResponse>

        @GET("user/folders")
        suspend fun getFolders(
            @Query("noincl") noincl: Boolean = false,
        ): Response<List<FolderResponse>>
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

    //@JsonClass(generateAdapter = true)
    //data class UserResponse(
    //    @Json(name = "user") val user: User
    //)
    //
    //@JsonClass(generateAdapter = true)
    //data class User(
    //    @Json(name = "id") val id: String,
    //    @Json(name = "username") val username: String,
    //    @Json(name = "createdAt") val createdAt: String,
    //    @Json(name = "updatedAt") val updatedAt: String,
    //    @Json(name = "role") val role: String,
    //)

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
        @Json(name = "favorite") var favorite: Boolean,
        @Json(name = "id") val id: String,
        @Json(name = "originalName") val originalName: String?,
        @Json(name = "name") val name: String,
        @Json(name = "size") val size: Long,
        @Json(name = "type") val type: String,
        @Json(name = "views") val views: Int,
        @Json(name = "maxViews") val maxViews: Int?,
        @Json(name = "password") val password: Boolean?,
        @Json(name = "folderId") val folderId: String?,
        @Json(name = "thumbnail") val thumbnail: Thumbnail?,
        //@Json(name = "tags") val tags: List<Tags>?,
        @Json(name = "url") val url: String?,
    ) {
        @JsonClass(generateAdapter = true)
        data class Thumbnail(
            val path: String,
        )
    }

    @JsonClass(generateAdapter = true)
    data class FilesResponse(
        @Json(name = "page") val page: List<FileResponse>,
        @Json(name = "total") val total: Int?,
        @Json(name = "pages") val pages: Int?,
    )

    @JsonClass(generateAdapter = true)
    data class FolderResponse(
        @Json(name = "id") val id: String,
        @Json(name = "name") val name: String,
        @Json(name = "public") val public: Boolean,
        @Json(name = "allowUploads") val allowUploads: Boolean,
    )

    @JsonClass(generateAdapter = true)
    data class FilesTransaction(
        @Json(name = "files") val files: List<String>,
        @Json(name = "delete_datasourceFiles") val deleteDatasourceFiles: Boolean? = null,
        @Json(name = "favorite") val favorite: Boolean? = null,
        @Json(name = "folder") val folder: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class FileEditRequest(
        @Json(name = "id") val id: String? = null,
        @Json(name = "favorite") val favorite: Boolean? = null,
        @Json(name = "maxViews") val maxViews: Int? = null,
        @Json(name = "password") val password: String? = null,
        @Json(name = "originalName") val originalName: String? = null,
        @Json(name = "type") val type: String? = null,
        @Json(name = "tags") val tags: List<String>? = null
    )

    @JsonClass(generateAdapter = true)
    data class CountResponse(val count: Int)

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
