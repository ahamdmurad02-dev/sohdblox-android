package app.sohdblox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.sohdblox.net.SohdApi
import app.sohdblox.play.PlayView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = (application as SohdApp).api
        setContent { SohdRoot(api) }
    }
}

private val Night = Color(0xFF1A1024)
private val Card = Color(0xFF2A1838)
private val Coral = Color(0xFFFF7A59)
private val Cream = Color(0xFFFFF4E8)
private val Soft = Color(0xFFD7C4E8)

@Composable
fun SohdRoot(api: SohdApi) {
    var screen by remember { mutableStateOf(if (api.signedIn()) "home" else "welcome") }
    var error by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var games by remember { mutableStateOf(listOf<app.sohdblox.net.GameDto>()) }
    var mine by remember { mutableStateOf(listOf<app.sohdblox.net.GameDto>()) }
    var playing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ah by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        scope.launch {
            error = ""
            try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { error = e.message ?: "صار خطأ" }
        }
    }
    fun loadHome() {
        run {
            games = runCatching { api.publishedGames() }.getOrDefault(emptyList())
            ah = runCatching { api.profileAh() }.getOrDefault(0)
            screen = "home"
        }
    }

    if (playing != null) {
        val session = playing!!
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            PlayView(ctx).apply {
                title = session.first
                onLeave = {
                    playing = null
                    if (api.signedIn()) loadHome() else screen = "welcome"
                }
            }
        })
        return
    }

    Column(Modifier.fillMaxSize().background(Night).padding(20.dp), horizontalAlignment = Alignment.Start) {
        Text("صحب لوكس", color = Coral, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Sohdblox", color = Soft, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        if (error.isNotBlank()) Text(error, color = Color(0xFFFFC4B8), fontSize = 16.sp)
        when (screen) {
            "welcome" -> {
                Spacer(Modifier.height(24.dp))
                Text("منصة ألعاب خاصة فيك", color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("العب الحين، أو سو حساب وتحفظ ألعابك.", color = Soft, fontSize = 16.sp)
                Spacer(Modifier.height(24.dp))
                BigBtn("العب تجربة") { playing = "ساحة التجربة" to "guest" }
                Spacer(Modifier.height(10.dp))
                BigBtn("إنشاء حساب") { screen = "signup" }
                Spacer(Modifier.height(10.dp))
                GhostBtn("عندي حساب") { screen = "login" }
            }
            "signup", "login" -> {
                val creating = screen == "signup"
                Text(if (creating) "إنشاء حساب" else "دخول لحساب موجود", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (creating) "بعد الإنشاء تدخل مباشرة." else "اكتب نفس الاسم و كلمة المرور.", color = Soft)
                Field("اسم اللاعب", user) { user = it }
                Field("كلمة المرور", pass, true) { pass = it }
                Spacer(Modifier.height(12.dp))
                BigBtn(if (creating) "إنشاء ودخول" else "دخول") {
                    run {
                        if (creating) api.signUp(user.trim(), pass) else api.signIn(user.trim(), pass)
                        loadHome()
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostBtn(if (creating) "عندي حساب" else "ما عندي حساب") {
                    screen = if (creating) "login" else "signup"
                    error = ""
                }
            }
            "home" -> {
                Text("مرحبا " + (api.username ?: ""), color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(ah.toString() + " Ah", color = Coral)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigBtn("ألعابي") { run { mine = api.myGames(); screen = "dev" } }
                    GhostBtn("خروج") { api.signOut(); screen = "welcome" }
                }
                Spacer(Modifier.height(16.dp))
                Text("الألعاب المنشورة", color = Soft)
                if (games.isEmpty()) Text("ما في ألعاب بعد. اضغط ألعابي وسو واحدة.", color = Cream)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(games) { g ->
                        Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(14.dp)) {
                            Text(g.name, color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            BigBtn("العب") { run { val sid = api.play(g.id); playing = g.name to sid } }
                        }
                    }
                }
            }
            "dev" -> {
                Text("ألعابك", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Field("اسم اللعبة الجديدة", newName) { newName = it }
                BigBtn("إنشاء") { run { api.createGame(newName.trim()); mine = api.myGames(); newName = "" } }
                Spacer(Modifier.height(8.dp))
                GhostBtn("رجوع") { loadHome() }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(mine) { g ->
                        Column(Modifier.fillMaxWidth().background(Card, RoundedCornerShape(18.dp)).padding(14.dp)) {
                            Text(g.name, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(if (g.status == "published") "منشورة" else "مسودة", color = Soft)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BigBtn("العب") { run { val sid = api.play(g.id); playing = g.name to sid } }
                                GhostBtn("نشر") { run { api.publish(g.id); mine = api.myGames() } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigBtn(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Night), shape = RoundedCornerShape(16.dp)) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GhostBtn(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, color = Cream, fontSize = 16.sp)
    }
}

@Composable
private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Cream, unfocusedTextColor = Cream, focusedBorderColor = Coral, unfocusedBorderColor = Soft, focusedLabelColor = Coral, unfocusedLabelColor = Soft)
    )
}
