package app.sohdblox.net

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SohdApi(context: Context) {
    private val prefs = context.getSharedPreferences("sohdblox", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    val url = "https://jbqenbfksnpkwwrvfybl.supabase.co"
    val anon = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpicWVuYmZrc25wa3d3cnZmeWJsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA0NzkxMjksImV4cCI6MjEwNjA1NTEyOX0.R0OOeXo4nFN3nAhvh3D8iOub_lJb4Ni7PDrjKgaljdA"
    private val domain = "accounts.sohdblox.app"

    var accessToken: String?
        get() = prefs.getString("access", null)
        set(v) { prefs.edit().putString("access", v).apply() }
    var refreshToken: String?
        get() = prefs.getString("refresh", null)
        set(v) { prefs.edit().putString("refresh", v).apply() }
    var username: String?
        get() = prefs.getString("username", null)
        set(v) { prefs.edit().putString("username", v).apply() }
    var userId: String?
        get() = prefs.getString("uid", null)
        set(v) { prefs.edit().putString("uid", v).apply() }

    fun emailOf(name: String) = name.trim().lowercase() + "@" + domain
    fun signedIn() = !accessToken.isNullOrBlank()
    fun signOut() { accessToken = null; refreshToken = null; username = null; userId = null }

    private fun headers(auth: Boolean = true): Request.Builder {
        val b = Request.Builder().addHeader("apikey", anon).addHeader("Content-Type", "application/json")
        b.addHeader("Authorization", "Bearer " + if (auth && !accessToken.isNullOrBlank()) accessToken else anon)
        return b
    }

    private fun post(path: String, body: String, auth: Boolean = true): String {
        val req = headers(auth).url(url + path).post(body.toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException(errorMessage(text, res.code))
            return text
        }
    }

    private fun get(path: String): String {
        val req = headers().url(url + path).get().build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException(errorMessage(text, res.code))
            return text
        }
    }

    private fun errorMessage(text: String, code: Int): String {
        val raw = try {
            val obj = json.parseToJsonElement(text).jsonObject
            obj["msg"]?.jsonPrimitive?.content ?: obj["message"]?.jsonPrimitive?.content ?: text
        } catch (_: Exception) { text }
        val lower = raw.lowercase()
        return when {
            "invalid login" in lower || "invalid_grant" in lower -> "ما في حساب بهالاسم، أو كلمة المرور غلط. اضغط إنشاء حساب."
            "already registered" in lower || "already been registered" in lower -> "هذا الاسم مستخدم. اضغط دخول أو غيّر الاسم."
            else -> raw.ifBlank { "خطأ $code" }
        }
    }

    fun signUp(user: String, password: String) {
        if (!user.matches(Regex("^[A-Za-z][A-Za-z0-9_]{2,19}$"))) throw RuntimeException("الاسم يبدأ بحرف، من 3 إلى 20.")
        if (password.length < 8) throw RuntimeException("كلمة المرور لازم 8 أحرف على الأقل.")
        val body = json.encodeToString(buildJsonObject {
            put("email", emailOf(user)); put("password", password)
            put("data", buildJsonObject { put("username", user); put("display_name", user) })
        })
        runCatching { post("/auth/v1/signup", body, false) }
        signIn(user, password)
    }

    fun signIn(user: String, password: String) {
        val body = json.encodeToString(buildJsonObject { put("email", emailOf(user)); put("password", password) })
        saveSession(post("/auth/v1/token?grant_type=password", body, false), user)
    }

    private fun saveSession(text: String, fallbackUser: String) {
        val obj = json.parseToJsonElement(text).jsonObject
        accessToken = obj["access_token"]?.jsonPrimitive?.content
        refreshToken = obj["refresh_token"]?.jsonPrimitive?.content
        val user = obj["user"]?.jsonObject
        userId = user?.get("id")?.jsonPrimitive?.content
        username = user?.get("user_metadata")?.jsonObject?.get("username")?.jsonPrimitive?.content ?: fallbackUser
        if (accessToken.isNullOrBlank()) throw RuntimeException("الحساب انحفظ. اضغط دخول.")
    }

    fun publishedGames(): List<GameDto> {
        return json.decodeFromString(get("/rest/v1/games?status=eq.published&select=*&order=play_count.desc"))
    }

    fun myGames(): List<GameDto> {
        val uid = userId ?: return emptyList()
        return json.decodeFromString(get("/rest/v1/games?owner_id=eq.$uid&select=*&order=created_at.desc"))
    }

    fun createGame(name: String): GameDto {
        val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").take(40) + "-" + (1000..9999).random()
        val body = json.encodeToString(buildJsonObject {
            put("owner_id", userId ?: ""); put("name", name); put("slug", slug)
            put("status", "draft"); put("category", "adventure")
        })
        val req = headers().url("$url/rest/v1/games").addHeader("Prefer", "return=representation")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val text = http.newCall(req).execute().use { res ->
            val t = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw RuntimeException(errorMessage(t, res.code)); t
        }
        val game = json.decodeFromString<List<GameDto>>(text).first()
        post("/rest/v1/game_maps", "{\"game_id\":\"${game.id}\",\"name\":\"Main\",\"is_active\":true,\"map_data\":{\"version\":1}}")
        post("/rest/v1/game_scripts", json.encodeToString(buildJsonObject {
            put("game_id", game.id); put("filename", "main.lua")
            put("source", "Sohdblox.OnPlayerAdded:Connect(function(player) player:LoadCharacter() end)")
        }))
        return game
    }

    fun publish(gameId: String) { rpc("publish_game", buildJsonObject { put("p_game_id", gameId) }) }

    fun play(gameId: String): String {
        val text = rpc("find_or_create_public_server", buildJsonObject { put("p_game_id", gameId) })
        val obj = json.parseToJsonElement(text)
        return if (obj is JsonObject) obj["id"]?.jsonPrimitive?.content ?: text
        else obj.jsonArray.first().jsonObject["id"]?.jsonPrimitive?.content ?: text
    }

    fun leave(serverId: String) { runCatching { rpc("leave_server", buildJsonObject { put("p_server_id", serverId) }) } }

    fun profileAh(): Int {
        val uid = userId ?: return 0
        val list = json.parseToJsonElement(get("/rest/v1/profiles?id=eq.$uid&select=ah_balance,username")).jsonArray
        if (list.isEmpty()) return 0
        val row = list.first().jsonObject
        username = row["username"]?.jsonPrimitive?.content ?: username
        return row["ah_balance"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    }

    private fun rpc(name: String, body: JsonObject) = post("/rest/v1/rpc/$name", json.encodeToString(body))
}

@Serializable
data class GameDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: String? = null,
    @SerialName("current_players") val currentPlayers: Int? = 0,
    @SerialName("play_count") val playCount: Int? = 0
)
