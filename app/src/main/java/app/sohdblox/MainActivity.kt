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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        val api = (application as SohdApp).api
        setContent { SohdRoot(api) }
    }
}

private val Ink = Color(0xFF0B1220)
private val Panel = Color(0xFF121A2B)
private val Teal = Color(0xFF3EE0B4)
private val TextCol = Color(0xFFE8EEF6)
private val Mute = Color(0xFF8B9BB4)
private val Gold = Color(0xFFFFD166)

@Composable
fun SohdRoot(api: SohdApi) {
    var screen by remember { mutableStateOf(if (api.signedIn()) "home" else "login") }
    var error by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var games by remember { mutableStateOf(listOf<GameDto>()) }
    var mine by remember { mutableStateOf(listOf<GameDto>()) }
    var playing by remember { mutableStateOf<Pair<GameDto, String>?>(null) }
    var ah by remember { mutableIntStateOf(0) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun run(block: suspend () -> Unit) {
        scope.launch {
            error = ""
            try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { error = e.message ?: "فشل" }
        }
    }
    fun loadHome() {
        run { games = api.publishedGames(); ah = api.profileAh(); screen = "home" }
    }

    if (playing != null) {
        val session = playing!!
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            PlayView(ctx).apply {
                title = session.first.name
                onLeave = { run { api.leave(session.second); playing = null; loadHome() } }
            }
        })
        return
    }

    Column(Modifier.fillMaxSize().background(Ink).padding(16.dp)) {
        Text("Sohdblox", color = Teal, fontSize = 28.sp)
        if (screen != "login" && screen != "signup") {
            Text((api.username ?: "") + "  ·  " + ah + " Ah", color = Gold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavBtn("ألعاب", screen == "home") { loadHome() }
                NavBtn("تطوير", screen == "dev") { run { mine = api.myGames(); screen = "dev" } }
                NavBtn("خروج", false) { api.signOut(); screen = "login" }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (error.isNotBlank()) Text(error, color = Color(0xFFF0A0A0))
        when (screen) {
            "login", "signup" -> {
                Text(if (screen == "login") "دخول" else "حساب جديد", color = TextCol, fontSize = 22.sp)
                Field("اسم المستخدم", user) { user = it }
                Field("كلمة المرور", pass, true) { pass = it }
                Spacer(Modifier.height(8.dp))
                MainBtn(if (screen == "login") "دخول" else "إنشاء حساب") {
                    run {
                        if (screen == "login") api.signIn(user, pass) else api.signUp(user, pass)
                        if (api.signedIn()) loadHome()
                    }
                }
                OutlinedButton(onClick = { screen = if (screen == "login") "signup" else "login" }) {
                    Text(if (screen == "login") "حساب جديد" else "عندي حساب", color = TextCol)
                }
            }
            "home" -> {
                Text("اختر لعبة ثم اضغط لعب", color = Mute)
                Spacer(Modifier.height(8.dp))
                if (games.isEmpty()) Text("ما في ألعاب منشورة. سو لعبة من التطوير.", color = Mute)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(games) { g -> GameCard(g) { run { val sid = api.play(g.id); playing = g to sid } } }
                }
            }
            "dev" -> {
                Text("المشاريع", color = TextCol, fontSize = 20.sp)
                Field("اسم اللعبة", newName) { newName = it }
                MainBtn("إنشاء") { run { api.createGame(newName); mine = api.myGames(); newName = "" } }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mine) { g ->
                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(g.name, color = TextCol)
                            Text(g.status ?: "", color = Mute)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MainBtn("لعب") { run { val sid = api.play(g.id); playing = g to sid } }
                                OutlinedButton(onClick = { run { api.publish(g.id); mine = api.myGames() } }) {
                                    Text("نشر", color = TextCol)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBtn(label: String, active: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = if (active) Teal else Panel)) {
        Text(label, color = if (active) Color(0xFF072019) else TextCol)
    }
}

@Composable
private fun MainBtn(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
        Text(label, color = Color(0xFF072019))
    }
}

@Composable
private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextCol, unfocusedTextColor = TextCol,
            focusedBorderColor = Teal, unfocusedBorderColor = Mute,
            focusedLabelColor = Teal, unfocusedLabelColor = Mute
        )
    )
}

@Composable
private fun GameCard(g: GameDto, onPlay: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text(g.name, color = TextCol, fontSize = 18.sp)
        Text((g.currentPlayers ?: 0).toString() + " يلعبون", color = Mute)
        Spacer(Modifier.height(6.dp))
        MainBtn("لعب", onPlay)
    }
}
