package com.sammy.running.core

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

data class GitHubConfig(val owner: String, val repository: String, val branch: String = "main", val token: String) {
    fun validated(): GitHubConfig {
        require(owner.matches(Regex("[A-Za-z0-9_.-]+"))) { "GitHub owner를 확인해 주세요." }
        require(repository.matches(Regex("[A-Za-z0-9_.-]+"))) { "GitHub repository를 확인해 주세요." }
        require(branch.isNotBlank() && !branch.any { it.isISOControl() }) { "GitHub branch를 확인해 주세요." }
        require(token.isNotBlank()) { "GitHub token을 입력해 주세요." }
        return copy(owner = owner.trim(), repository = repository.trim(), branch = branch.trim(), token = token.trim())
    }
}

data class GitHubRequest(val method: String, val url: String, val headers: Map<String, String>, val body: String? = null)
data class GitHubResponse(val status: Int, val body: String)

fun interface GitHubTransport { fun execute(request: GitHubRequest): GitHubResponse }

data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
)

data class OAuthTokens(
    val accessToken: String,
    val expiresInSeconds: Long?,
    val refreshToken: String?,
    val refreshTokenExpiresInSeconds: Long?,
)

sealed interface DevicePollResult {
    data object Pending : DevicePollResult
    data object SlowDown : DevicePollResult
    data object Expired : DevicePollResult
    data object Denied : DevicePollResult
    data class Authorized(val tokens: OAuthTokens) : DevicePollResult
}

class GitHubDeviceAuthClient(private val transport: GitHubTransport = UrlConnectionGitHubTransport()) {
    fun requestCode(clientId: String): DeviceAuthorization {
        require(clientId.isNotBlank()) { "GitHub App Client ID가 없습니다." }
        val response = post("https://github.com/login/device/code", mapOf("client_id" to clientId))
        check(response.status in 200..299) { authError(response) }
        val json = JsonParser.parseString(response.body).asJsonObject
        return DeviceAuthorization(
            json["device_code"].asString,
            json["user_code"].asString,
            json["verification_uri"].asString,
            json["expires_in"].asLong,
            json["interval"].asLong,
        )
    }

    fun poll(clientId: String, deviceCode: String): DevicePollResult {
        val response = post("https://github.com/login/oauth/access_token", mapOf(
            "client_id" to clientId,
            "device_code" to deviceCode,
            "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
        ))
        check(response.status in 200..299) { authError(response) }
        val json = JsonParser.parseString(response.body).asJsonObject
        return when (json["error"]?.asString) {
            null -> DevicePollResult.Authorized(tokens(json))
            "authorization_pending" -> DevicePollResult.Pending
            "slow_down" -> DevicePollResult.SlowDown
            "expired_token" -> DevicePollResult.Expired
            "access_denied" -> DevicePollResult.Denied
            else -> error(json["error_description"]?.asString ?: "GitHub 로그인을 완료하지 못했습니다.")
        }
    }

    fun refresh(clientId: String, refreshToken: String): OAuthTokens {
        val response = post("https://github.com/login/oauth/access_token", mapOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        ))
        check(response.status in 200..299) { authError(response) }
        val json = JsonParser.parseString(response.body).asJsonObject
        json["error"]?.asString?.let { error(json["error_description"]?.asString ?: it) }
        return tokens(json)
    }

    private fun tokens(json: com.google.gson.JsonObject) = OAuthTokens(
        json["access_token"].asString,
        json["expires_in"]?.asLong,
        json["refresh_token"]?.asString,
        json["refresh_token_expires_in"]?.asLong,
    )

    private fun post(url: String, fields: Map<String, String>): GitHubResponse = transport.execute(GitHubRequest(
        "POST", url,
        mapOf("Accept" to "application/json", "Content-Type" to "application/x-www-form-urlencoded", "User-Agent" to "sammy-running-android"),
        fields.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" },
    ))

    private fun authError(response: GitHubResponse): String = "GitHub 로그인 실패 (${response.status})"
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

class UrlConnectionGitHubTransport : GitHubTransport {
    override fun execute(request: GitHubRequest): GitHubResponse {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            request.headers.forEach(connection::setRequestProperty)
            request.body?.let {
                connection.doOutput = true
                connection.outputStream.use { output -> output.write(it.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            return GitHubResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}

sealed interface PublishResult {
    data class Published(val path: String, val alreadyExisted: Boolean = false) : PublishResult
    data class Collision(val path: String) : PublishResult
}

class GitHubPublisher(private val transport: GitHubTransport = UrlConnectionGitHubTransport()) {
    private val gson = Gson()

    fun publish(config: GitHubConfig, run: RunJson, json: String): PublishResult {
        val valid = config.validated()
        val path = "data/runs/${run.id}.json"
        val existing = get(valid, path)
        if (existing.status == 200) return compareExisting(path, json, existing.body)
        check(existing.status == 404) { githubError(existing) }

        val distance = String.format(Locale.ROOT, "%.2f", run.distanceMeters / 1000)
        val requestBody = gson.toJson(mapOf(
            "message" to "Add run: ${run.date} ${distance}km",
            "content" to Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8)),
            "branch" to valid.branch,
        ))
        val created = transport.execute(GitHubRequest("PUT", contentsUrl(valid, path), headers(valid), requestBody))
        if (created.status in 200..201) return PublishResult.Published(path)
        if (created.status == 409 || created.status == 422) {
            val raced = get(valid, path)
            if (raced.status == 200) return compareExisting(path, json, raced.body)
        }
        error(githubError(created))
    }

    private fun get(config: GitHubConfig, path: String): GitHubResponse = transport.execute(
        GitHubRequest("GET", "${contentsUrl(config, path)}?ref=${encode(config.branch)}", headers(config))
    )

    private fun compareExisting(path: String, expected: String, responseBody: String): PublishResult {
        val encoded = runCatching { JsonParser.parseString(responseBody).asJsonObject.get("content").asString }.getOrNull()
        val actual = runCatching { String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8) }.getOrNull()
        return if (actual != null && canonicalJson(actual) == canonicalJson(expected))
            PublishResult.Published(path, alreadyExisted = true)
        else PublishResult.Collision(path)
    }

    private fun canonicalJson(value: String) = runCatching { JsonParser.parseString(value).toString() }.getOrDefault(value.trim())
    private fun contentsUrl(config: GitHubConfig, path: String) =
        "https://api.github.com/repos/${encode(config.owner)}/${encode(config.repository)}/contents/${path.split('/').joinToString("/") { encode(it) }}"
    private fun headers(config: GitHubConfig) = mapOf(
        "Accept" to "application/vnd.github+json",
        "Authorization" to "Bearer ${config.token}",
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to "sammy-running-android",
        "Content-Type" to "application/json; charset=utf-8",
    )
    private fun githubError(response: GitHubResponse): String {
        val message = runCatching { JsonParser.parseString(response.body).asJsonObject.get("message").asString }.getOrNull()
        return "GitHub 게시 실패 (${response.status})${message?.let { ": $it" }.orEmpty()}"
    }
    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}

object PublishedRunIdentity {
    fun key(run: RawRun): String {
        val source = run.sessionId.takeIf { it.isNotBlank() }
            ?: "${run.startTime}|${run.distanceMeters}|${run.durationSeconds}"
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
