package com.example.nlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppItem(val label: String, val pkg: String)

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { Launcher(tick) } }
    }

    // Обновляем список при возврате (установка/удаление приложений)
    override fun onResume() { super.onResume(); tick++ }

    // Кнопка «Домой» закрывает список
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); tick++ }
}

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

private fun letterOf(a: AppItem): Char =
    a.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'

private fun Context.launch(a: AppItem) =
    packageManager.getLaunchIntentForPackage(a.pkg)?.let { startActivity(it) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Launcher(tick: Int) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("nl", Context.MODE_PRIVATE) }
    val apps = remember(tick) { loadApps(ctx) }
    var favs by remember {
        mutableStateOf(prefs.getString("favs", "")!!.split(",").filter { it.isNotEmpty() })
    }
    var showList by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppItem?>(null) }

    LaunchedEffect(tick) { showList = false }
    BackHandler(showList) { showList = false }

    fun saveFavs(l: List<String>) { favs = l; prefs.edit().putString("favs", l.joinToString(",")).apply() }

    val favApps = favs.mapNotNull { p -> apps.find { it.pkg == p } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (showList) 0.88f else 0.35f))
            .pointerInput(showList) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = { if (dy < -120 && !showList) showList = true },
                ) { _, d -> dy += d }
            }
    ) {
        if (!showList) {
            HomeScreen(favApps, onClick = { ctx.launch(it) }, onLong = { menuFor = it },
                onOpenList = { showList = true })
        } else {
            AppList(apps, favs, onClick = { ctx.launch(it) }, onLong = { menuFor = it })
        }
    }

    menuFor?.let { app ->
        val pinned = app.pkg in favs
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton(onClick = {
                        saveFavs(if (pinned) favs - app.pkg else (favs + app.pkg).takeLast(8))
                        menuFor = null
                    }) { Text(if (pinned) "Открепить с главного" else "Закрепить на главном") }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${app.pkg}")))
                        menuFor = null
                    }) { Text("О приложении") }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                        menuFor = null
                    }) { Text("Удалить") }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("Закрыть") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(favs: List<AppItem>, onClick: (AppItem) -> Unit, onLong: (AppItem) -> Unit, onOpenList: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                color = Color.White, fontSize = 56.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                color = Color.White.copy(alpha = .7f), fontSize = 16.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (favs.isEmpty()) {
                Text("Свайп вверх — список приложений.\nДолгое нажатие — закрепить.",
                    color = Color.White.copy(alpha = .6f), fontSize = 16.sp)
            }
            favs.forEach { app ->
                Text(app.label, color = Color.White, fontSize = 32.sp, maxLines = 1,
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { onClick(app) }, onLongClick = { onLong(app) }).padding(vertical = 6.dp))
            }
            Text("Все приложения ↑", color = Color.White.copy(alpha = .5f), fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp).combinedClickable(onClick = onOpenList))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppList(apps: List<AppItem>, favs: List<String>, onClick: (AppItem) -> Unit, onLong: (AppItem) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letters = remember(apps) { apps.map(::letterOf).distinct() }
    var railH by remember { mutableFloatStateOf(1f) }

    fun jump(y: Float) {
        if (letters.isEmpty()) return
        val idx = (y / railH * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val target = apps.indexOfFirst { letterOf(it) == letters[idx] }
        if (target >= 0) scope.launch { state.scrollToItem(target) }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = 32.dp, end = 64.dp, top = 48.dp, bottom = 48.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        ) {
            items(apps, key = { it.pkg }) { app ->
                Text(
                    app.label, maxLines = 1,
                    color = if (app.pkg in favs) Color(0xFFFFD54F) else Color.White,
                    fontSize = 26.sp,
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onClick = { onClick(app) }, onLongClick = { onLong(app) }).padding(vertical = 10.dp)
                )
            }
        }
        // Правая полоса с буквами: тап или проведите пальцем — быстрый переход
        Column(
            Modifier.align(Alignment.CenterEnd).statusBarsPadding().navigationBarsPadding()
                .fillMaxHeight().width(44.dp)
                .onSizeChanged { railH = it.height.toFloat() }
                .pointerInput(letters) { detectTapGestures { jump(it.y) } }
                .pointerInput(letters) {
                    detectDragGestures(onDragStart = { jump(it.y) }) { c, _ -> jump(c.position.y) }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { Text(it.toString(), color = Color.White.copy(alpha = .8f), fontSize = 13.sp) }
        }
    }
}
