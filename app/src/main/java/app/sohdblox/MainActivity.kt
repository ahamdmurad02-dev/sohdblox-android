package app.sohdblox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.sohdblox.net.GameDto
import app.sohdblox.net.SohdApi
import app.sohdblox.play.PlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SohdRoot((application as SohdApp).api) }
    }
}

private val Night = Color(0xFF1A1024)
private val Card = Color(0xFF2A1838)
private val Coral = Color(0xFFFF7A59)
private val Cream = Color(0xFFFFF4E8)
private val Soft = Color(0xFFD7C4E8)
private val DefaultLua = "Sohdblox.OnPlayerAdded:Connect(function(player)\n  player:LoadCharacter()\nend)\n"

@Composable
fun SohdRoot(api: SohdApi) {
    var screen by remember { mutableStateOf(if (api.signedIn()) "home" else "welcome") }
    var error by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var games by remember { mutableStateOf(listOf<GameDto>()) }
    var mine by remember { mutableStateOf(listOf<GameDto>()) }
    var playing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var leaveAsk by remember { mutableStateOf(false) }
    var studio by remember { mutableStateOf<GameDto?>(null) }
    var studioTab by remember { mutableStateOf("map") }
    var luaText by remember { mutableStateOf(DefaultLua) }
    var ah by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }
    var friendName by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        scope.launch {
            error = ""
            try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { error = e.message ?: "صار خطأ" }
        }
    }
    fun refresh() {
        run {
            games = runCatching { api.publishedGames() }.getOrDefault(emptyList())
            mine = runCatching { api.myGames() }.getOrDefault(emptyList())
            ah = runCatching { api.profileAh() }.getOrDefault(0)
        }
    }

    if (playing != null) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                PlayView(ctx).apply { title = playing!!.first; onLeave = { leaveAsk = true } }
            })
            if (leaveAsk) {
                Column(Modifier.fillMaxSize().background(Color(0xCC1A1024)).padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("تبي تطلع من الخريطة؟", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    BigBtn("اطلع") { playing = null; leaveAsk = false; screen = "home" }
                    Spacer(Modifier.height(8.dp))
                    GhostBtn("كمل لعب") { leaveAsk = false }
                }
            }
        }
        return
    }

    if (studio != null) {
        val g = studio!!
        Column(Modifier.fillMaxSize().background(Night).padding(16.dp)) {
            Text("محرك صحب لوكس", color = Coral, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text(g.name, color = Cream, fontSize = 18.sp)
            Text("الشخصية والكاميرا والحركة جاهزين", color = Soft)
            if (error.isNotBlank()) Text(error, color = Color(0xFFFFC4B8))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TabBtn("خريطة", studioTab == "map") { studioTab = "map" }
                TabBtn("سكربت", studioTab == "lua") { studioTab = "lua" }
                TabBtn("تجربة", studioTab == "test") { studioTab = "test" }
                TabBtn("نشر", studioTab == "pub") { studioTab = "pub" }
            }
            Spacer(Modifier.height(10.dp))
            when (studioTab) {
                "map" -> {
                    Text("الخريطة الأساسية انضافت تلقائي.", color = Cream)
                    Column(Modifier.fillMaxWidth().height(200.dp).background(Card, RoundedCornerShape(16.dp)).padding(16.dp)) {
                        Text("Spawn (0, 2, 8)", color = Coral)
                        Text("Ground 40x40", color = Soft)
                        Text("Character Controller: ON", color = Cream)
                    }
                }
                "lua" -> {
                    OutlinedTextField(value = luaText, onValueChange = { luaText = it }, modifier = Modifier.fillMaxWidth().height(260.dp), colors = fieldColors())
                }
                "test" -> {
                    BigBtn("تشغيل التجربة") { playing = g.name to "test" }
                }
                else -> {
                    BigBtn("نشر اللعبة") { run { api.publish(g.id); studio = g.copy(status = "published"); refresh() } }
                    if (g.status == "published") Text("منشورة", color = Coral)
                }
            }
            Spacer(Modifier.height(10.dp))
            GhostBtn("إغلاق المحرك") { studio = null; screen = "dev"; refresh() }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Night)) {
        Column(Modifier.weight(1f).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("صحب لوكس", color = Coral, fontSize = 24.sp, fontWeight = FontWeight.Black)
                if (api.signedIn()) Text(ah.toString() + " Ah", color = Coral, fontWeight = FontWeight.Bold)
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFFC4B8))
            Spacer(Modifier.height(8.dp))
            when (screen) {
                "welcome" -> {
                    BigBtn("العب تجربة") { playing = "ساحة التجربة" to "guest" }
                    Spacer(Modifier.height(8.dp))
                    BigBtn("إنشاء حساب") { screen = "signup" }
                    Spacer(Modifier.height(8.dp))
                    GhostBtn("عندي حساب") { screen = "login" }
                }
                "signup", "login" -> {
                    val creating = screen == "signup"
                    Text(if (creating) "إنشاء حساب" else "دخول", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Field("اسم اللاعب", user) { user = it }
                    Field("كلمة المرور", pass, true) { pass = it }
                    BigBtn(if (creating) "إنشاء ودخول" else "دخول") {
                        run {
                            if (creating) api.signUp(user.trim(), pass) else api.signIn(user.trim(), pass)
                            refresh(); screen = "home"
                        }
                    }
                    GhostBtn(if (creating) "عندي حساب" else "ما عندي حساب") { screen = if (creating) "login" else "signup" }
                }
                "home", "discover" -> {
                    Text(if (screen == "home") "الرئيسية" else "اكتشف", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (games.isEmpty()) Text("ما في ألعاب منشورة بعد.", color = Soft)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(games) { g -> GameCard(g) { run { playing = g.name to api.play(g.id) } } }
                    }
                }
                "dev" -> {
                    Text("تطوير", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Field("اسم اللعبة", newName) { newName = it }
                    BigBtn("إنشاء وافتح المحرك") {
                        run {
                            val g = api.createGame(newName.trim().ifBlank { "لعبة جديدة" })
                            mine = api.myGames()
                            luaText = DefaultLua
                            studioTab = "map"
                            studio = g
                            newName = ""
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(mine) { g ->
                            Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(12.dp)) {
                                Text(g.name, color = Cream, fontWeight = FontWeight.Bold)
                                Text(if (g.status == "published") "منشورة" else "مسودة", color = Soft)
                                Spacer(Modifier.height(6.dp))
                                BigBtn("افتح المحرك") { studio = g; studioTab = "map" }
                            }
                        }
                    }
                }
                "friends" -> {
                    Text("الأصدقاء", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Field("اسم الصديق", friendName) { friendName = it }
                    BigBtn("إضافة") { if (friendName.isNotBlank()) { friends = friends + friendName.trim(); friendName = "" } }
                    if (friends.isEmpty()) Text("ما ضفت أحد بعد.", color = Soft)
                    friends.forEach { Text("- " + it, color = Cream) }
                }
                "profile" -> {
                    Text("حسابي", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(84.dp).clip(CircleShape).background(Coral), contentAlignment = Alignment.Center) {
                        Text((api.username ?: "?").take(1).uppercase(), color = Night, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(api.username ?: "", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("لاعب ومطوّر", color = Soft)
                    Text(ah.toString() + " Ah", color = Coral, fontSize = 20.sp)
                    Text("ألعابك: " + mine.size, color = Cream)
                    Spacer(Modifier.height(16.dp))
                    GhostBtn("تسجيل خروج") { api.signOut(); screen = "welcome"; games = emptyList(); mine = emptyList() }
                }
            }
        }
        if (api.signedIn() && screen !in listOf("welcome", "signup", "login")) {
            Row(Modifier.fillMaxWidth().background(Card).padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Nav("رئيسية", screen == "home") { screen = "home"; refresh() }
                Nav("اكتشف", screen == "discover") { screen = "discover"; refresh() }
                Nav("تطوير", screen == "dev") { screen = "dev"; refresh() }
                Nav("أصدقاء", screen == "friends") { screen = "friends" }
                Nav("حسابي", screen == "profile") { screen = "profile"; refresh() }
            }
        }
    }
}

@Composable private fun Nav(label: String, on: Boolean, click: () -> Unit) {
    Text(label, color = if (on) Coral else Soft, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { click() }.padding(8.dp))
}
@Composable private fun TabBtn(label: String, on: Boolean, click: () -> Unit) {
    OutlinedButton(onClick = click) { Text(label, color = if (on) Coral else Cream, fontSize = 13.sp) }
}
@Composable private fun GameCard(g: GameDto, play: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(g.name, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text((g.currentPlayers ?: 0).toString() + " بالسيرفر", color = Soft)
        Spacer(Modifier.height(8.dp))
        BigBtn("العب", play)
    }
}
@Composable private fun BigBtn(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Night), shape = RoundedCornerShape(16.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun GhostBtn(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) { Text(label, color = Cream) }
}
@Composable private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true, visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None, colors = fieldColors())
}
@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedTextColor = Cream, unfocusedTextColor = Cream, focusedBorderColor = Coral, unfocusedBorderColor = Soft, focusedLabelColor = Coral, unfocusedLabelColor = Soft)
