package com.vknxworks.immichctrl.data.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.vknxworks.immichctrl.Strings
import com.vknxworks.immichctrl.R

/** A real answer from the server that says no (wrong password, not found, not allowed). */
class ApiException(message: String, val code: Int = 0) : Exception(message)

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
    isLenient = true
}

private val JsonType = "application/json; charset=utf-8".toMediaType()

enum class AuthType { PASSWORD, API_KEY, OAUTH }

/** Request tag: never attach saved credentials to this request. */
object NoAuth

/**
 * Talks to one Immich server as one user. [secret] is an access token (password and OAuth sign-in)
 * or an API key. Works with Immich 2.x and 3.x: requests only use endpoints both versions have,
 * and the models accept either version's shape.
 */
class ImmichClient(
    baseUrl: String,
    val secret: String,
    val authType: AuthType,
    val http: OkHttpClient,
) {
    /** Server root without a trailing slash or /api, e.g. https://photos.example.com */
    val baseUrl: String = baseUrl.trim().trimEnd('/').removeSuffix("/api").trimEnd('/')
    val apiUrl: String = "${this.baseUrl}/api"

    /** Origin the auth interceptor attaches credentials to. */
    val origin: HttpUrl = this.baseUrl.toHttpUrl()

    /** Headers for requests made before an account exists (signing in to a server behind a proxy). */
    var extraHeaders: Map<String, String> = emptyMap()

    /** Set once the server version is known; some request fields changed between 2.x and 3.x. */
    @Volatile var serverMajor: Int = 3

    val authHeader: Pair<String, String>
        get() = if (authType == AuthType.API_KEY) "x-api-key" to secret else "Authorization" to "Bearer $secret"

    fun url(path: String, vararg query: Pair<String, Any?>): HttpUrl {
        val b = "$apiUrl$path".toHttpUrl().newBuilder()
        query.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is Collection<*> -> v.forEach { b.addQueryParameter(k, it.toString()) }
                else -> b.addQueryParameter(k, v.toString())
            }
        }
        return b.build()
    }

    fun request(url: HttpUrl): Request.Builder {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        extraHeaders.forEach { (k, v) -> b.header(k, v) }
        if (secret.isNotBlank()) b.header(authHeader.first, authHeader.second)
        else b.tag(NoAuth::class.java, NoAuth) // signing in: don't let the interceptor add the active account's credentials
        return b
    }

    suspend fun execute(req: Request): Response = execute(http.newCall(req))

    /** Like [execute], for a call the caller keeps so it can cancel it (a long stream it reads itself). */
    suspend fun execute(call: Call): Response {
        val resp = call.await()
        if (!resp.isSuccessful) {
            val body = resp.body?.string().orEmpty()
            resp.close()
            throw ApiException(errorMessage(resp.code, body), resp.code)
        }
        return resp
    }

    suspend fun raw(method: String, path: String, body: JsonElement? = null, vararg query: Pair<String, Any?>): String {
        val rb: RequestBody? = when {
            body != null -> body.toString().toRequestBody(JsonType)
            method == "GET" || method == "HEAD" -> null
            else -> "".toRequestBody(JsonType)
        }
        val req = request(url(path, *query)).method(method, rb).build()
        return execute(req).use { it.body?.string().orEmpty() }
    }

    suspend fun <T> call(
        method: String,
        path: String,
        de: DeserializationStrategy<T>,
        body: JsonElement? = null,
        vararg query: Pair<String, Any?>,
    ): T {
        val text = raw(method, path, body, *query)
        return ApiJson.decodeFromString(de, text.ifBlank { "{}" })
    }

    private fun errorMessage(code: Int, body: String): String {
        val parsed = runCatching { ApiJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val msg = when (val m = parsed?.get("message")) {
            is JsonPrimitive -> m.contentOrNull
            is JsonArray -> m.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString("; ")
            else -> null
        }
        return when {
            code == 401 -> msg?.let { Strings.str(R.string.not_signed_in_2, it) } ?: Strings.str(R.string.your_session_has_expired_sign_in)
            code == 403 -> msg ?: Strings.str(R.string.you_don_t_have_permission_to_do_that)
            code == 404 -> msg ?: Strings.str(R.string.not_found)
            !msg.isNullOrBlank() -> msg
            else -> Strings.str(R.string.server_error, code)
        }
    }

    // ---- URLs for images and media (credentials are added by the app's auth interceptor) ----

    fun thumbnailUrl(assetId: String, size: String = "thumbnail", cacheKey: String? = null): String =
        url("/assets/$assetId/thumbnail", "size" to size, "c" to cacheKey).toString()

    fun originalUrl(assetId: String): String = url("/assets/$assetId/original").toString()

    fun videoUrl(assetId: String): String = url("/assets/$assetId/video/playback").toString()

    fun personThumbUrl(personId: String, updatedAt: String? = null): String =
        url("/people/$personId/thumbnail", "c" to updatedAt).toString()

    fun profileImageUrl(userId: String, changedAt: String? = null): String =
        url("/users/$userId/profile-image", "c" to changedAt).toString()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** "1.2.3" or "v3.3.3" -> 3; null when it can't be read. */
fun majorVersion(version: String?): Int? =
    version?.trim()?.removePrefix("v")?.substringBefore('.')?.toIntOrNull()

internal fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement.asLongOrNull(): Long? = runCatching { jsonPrimitive.contentOrNull?.toLongOrNull() }.getOrNull()
