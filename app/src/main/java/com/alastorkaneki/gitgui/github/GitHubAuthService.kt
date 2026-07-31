package com.alastorkaneki.gitgui.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun requestDeviceCode(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "GITHUB_CLIENT_ID is not configured." }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "repo read:user user:email")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(body)
            .build()
        executeJson(request).let { json ->
            DeviceCode(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.getString("verification_uri"),
                expiresIn = json.getInt("expires_in"),
                interval = json.optInt("interval", 5)
            )
        }
    }

    suspend fun pollToken(clientId: String, deviceCode: String): TokenPollResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .post(body)
            .build()
        runCatching { executeJson(request) }.fold(
            onSuccess = { json ->
                when {
                    json.has("access_token") -> TokenPollResult.Success(json.getString("access_token"))
                    json.optString("error") == "authorization_pending" -> TokenPollResult.Pending
                    json.optString("error") == "slow_down" -> TokenPollResult.SlowDown()
                    else -> TokenPollResult.Failure(
                        json.optString("error_description").ifBlank { json.optString("error", "Authorization failed.") }
                    )
                }
            },
            onFailure = { TokenPollResult.Failure(it.message ?: "Authorization failed.") }
        )
    }

    suspend fun fetchUser(token: String): GitHubUser = withContext(Dispatchers.IO) {
        val request = apiRequest("https://api.github.com/user", token)
        executeJson(request).let { json ->
            GitHubUser(
                login = json.getString("login"),
                name = json.optString("name").takeIf { it.isNotBlank() && it != "null" },
                avatarUrl = json.optString("avatar_url").takeIf { it.isNotBlank() },
                publicRepos = json.optInt("public_repos"),
                privateRepos = json.optInt("total_private_repos")
            )
        }
    }

    suspend fun fetchRepositories(token: String): List<GitHubRepositoryInfo> = withContext(Dispatchers.IO) {
        val request = apiRequest(
            "https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member",
            token
        )
        val array = executeArray(request)
        buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                add(
                    GitHubRepositoryInfo(
                        name = json.getString("name"),
                        fullName = json.getString("full_name"),
                        description = json.optString("description").takeIf { it.isNotBlank() && it != "null" },
                        private = json.optBoolean("private"),
                        cloneUrl = json.getString("clone_url"),
                        defaultBranch = json.optString("default_branch", "main"),
                        updatedAt = json.optString("updated_at"),
                        language = json.optString("language").takeIf { it.isNotBlank() && it != "null" },
                        stars = json.optInt("stargazers_count")
                    )
                )
            }
        }
    }

    suspend fun validateToken(token: String): GitHubUser {
        require(token.isNotBlank()) { "Token cannot be empty." }
        return fetchUser(token.trim())
    }

    private fun apiRequest(url: String, token: String): Request = Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "Bearer $token")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "GIT-GUI-Android")
        .get()
        .build()

    private fun executeJson(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(readError(text, response.code))
        JSONObject(text)
    }

    private fun executeArray(request: Request): JSONArray = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw IOException(readError(text, response.code))
        JSONArray(text)
    }

    private fun readError(text: String, code: Int): String = runCatching {
        JSONObject(text).optString("message").ifBlank { "GitHub request failed with HTTP $code." }
    }.getOrDefault("GitHub request failed with HTTP $code.")
}
