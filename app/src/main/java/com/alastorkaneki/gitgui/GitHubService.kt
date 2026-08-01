package com.alastorkaneki.gitgui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GitHubService {
    private val client = OkHttpClient()

    suspend fun requestDeviceCode(clientId: String): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "repo read:user user:email")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val json = JSONObject(response.body.string())
            if (!response.isSuccessful) error(json.optString("error_description", "GitHub rejected the device request."))
            DeviceCode(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.getString("verification_uri"),
                expiresIn = json.getInt("expires_in"),
                interval = json.optInt("interval", 5)
            )
        }
    }

    suspend fun pollToken(clientId: String, code: DeviceCode): String {
        var interval = code.interval.coerceAtLeast(5)
        val end = System.currentTimeMillis() + code.expiresIn * 1000L
        while (System.currentTimeMillis() < end) {
            delay(interval * 1000L)
            val result = withContext(Dispatchers.IO) {
                val body = FormBody.Builder()
                    .add("client_id", clientId)
                    .add("device_code", code.deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()
                val request = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response -> JSONObject(response.body.string()) }
            }
            result.optString("access_token").takeIf { it.isNotBlank() }?.let { return it }
            when (result.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "access_denied" -> error("GitHub authorization was denied.")
                "expired_token" -> error("The GitHub login code expired.")
                else -> result.optString("error_description").takeIf { it.isNotBlank() }?.let { error(it) }
            }
        }
        error("The GitHub login code expired.")
    }

    suspend fun profile(token: String): GitHubProfile = withContext(Dispatchers.IO) {
        val json = getJson("https://api.github.com/user", token) as JSONObject
        GitHubProfile(json.getString("login"), json.optString("name").takeIf { it.isNotBlank() })
    }

    suspend fun repositories(token: String): List<GitHubRepository> = withContext(Dispatchers.IO) {
        val array = getJson("https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member", token) as JSONArray
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GitHubRepository(
                        name = item.getString("name"),
                        fullName = item.getString("full_name"),
                        cloneUrl = item.getString("clone_url"),
                        privateRepo = item.getBoolean("private")
                    )
                )
            }
        }
    }

    private fun getJson(url: String, token: String): Any {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val error = runCatching { JSONObject(text).optString("message") }.getOrNull()
                throw IllegalStateException(error ?: "GitHub request failed with ${response.code}.")
            }
            return if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
        }
    }
}
