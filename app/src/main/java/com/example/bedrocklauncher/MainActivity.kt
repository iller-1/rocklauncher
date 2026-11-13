package com.example.bedrocklauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile

const val MC_PACKAGE = "com.mojang.minecraftpe"
val VERSIONS = listOf(
    "1.21", "1.20", "1.19", "1.18", "1.17", "1.16", "1.14", "1.13", "1.12",
    "1.11", "1.10", "1.9", "1.8", "1.7", "1.6", "1.5", "1.4", "1.2", "1.1", "1.0"
)
val TEXT_EXT = listOf("txt", "json", "properties", "lang", "mcfunction", "js", "material", "fragment", "vertex")
const val HINT_PATH = "Android/data/$MC_PACKAGE/files/games/com.mojang"

fun installedVersion(ctx: Context): String? = try {
    ctx.packageManager.getPackageInfo(MC_PACKAGE, 0).versionName
} catch (e: Exception) { null }

fun playMinecraft(ctx: Context) {
    val i = ctx.packageManager.getLaunchIntentForPackage(MC_PACKAGE)
    if (i != null) ctx.startActivity(i)
    else ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$MC_PACKAGE")))
}

fun readText(ctx: Context, f: DocumentFile): String =
    ctx.contentResolver.openInputStream(f.uri)?.bufferedReader()?.use { it.readText() } ?: ""

fun writeText(ctx: Context, f: DocumentFile, text: String) {
    ctx.contentResolver.openOutputStream(f.uri, "wt")?.bufferedWriter()?.use { it.write(text) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface { App() } } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    var root by remember { mutableStateOf(prefs.getString("root", null)?.let { Uri.parse(it) }) }
    var screen by remember { mutableStateOf("home") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            prefs.edit().putString("root", uri.toString()).apply()
            root = uri; screen = "files"
        }
    }
    val initial = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata")
    if (screen == "files" && root != null) {
        FileBrowser(root!!, onExit = { screen = "home" }, onChange = { picker.launch(initial) })
    } else {
        Home(
            onFiles = { if (root != null) screen = "files" else picker.launch(initial) }
        )
    }
}

@Composable
fun Home(onFiles: () -> Unit) {
    val ctx = LocalContext.current
    val installed = remember { installedVersion(ctx) }
    Column(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding()) {
        Text("Rock", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (installed == null) "Minecraft غير مثبتة على الجهاز" else "النسخة المثبتة: $installed",
            Modifier.padding(vertical = 8.dp)
        )
        OutlinedButton(onClick = onFiles, Modifier.fillMaxWidth()) { Text("ملفات اللعبة (عوالم / باكات / إعدادات)") }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VERSIONS) { v ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bedrock $v", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { playMinecraft(ctx) }) { Text("PLAY") }
                    }
                }
            }
        }
    }
}

@Composable
fun FileBrowser(root: Uri, onExit: () -> Unit, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val top = remember(root) { DocumentFile.fromTreeUri(ctx, root) }
    var stack by remember(root) { mutableStateOf(listOfNotNull(top)) }
    var editing by remember { mutableStateOf<DocumentFile?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var confirmDelete by remember { mutableStateOf<DocumentFile?>(null) }

    BackHandler {
        when {
            editing != null -> editing = null
            stack.size > 1 -> stack = stack.dropLast(1)
            else -> onExit()
        }
    }

    if (top == null) { Text("تعذر فتح المجلد", Modifier.padding(16.dp)); return }

    val ed = editing
    if (ed != null) {
        var text by remember(ed) { mutableStateOf(readText(ctx, ed)) }
        Column(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding()) {
            Text(ed.name ?: "", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(text, { text = it }, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { writeText(ctx, ed, text); editing = null }) { Text("حفظ") }
                OutlinedButton(onClick = { editing = null }) { Text("إلغاء") }
            }
        }
        return
    }

    val cur = stack.last()
    val children = remember(cur, refresh) {
        cur.listFiles().sortedWith(compareBy({ !it.isDirectory }, { it.name?.lowercase() }))
    }
    Column(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding()) {
        Text(stack.joinToString(" / ") { it.name ?: "" }, style = MaterialTheme.typography.titleSmall)
        Text("اختر المجلد: $HINT_PATH", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = onExit) { Text("رجوع") }
            OutlinedButton(onClick = onChange) { Text("تغيير المجلد") }
        }
        LazyColumn {
            items(children) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val ext = f.name?.substringAfterLast('.', "")?.lowercase() ?: ""
                        if (f.isDirectory) stack = stack + f
                        else if (ext in TEXT_EXT) editing = f
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text((if (f.isDirectory) "📁 " else "📄 ") + (f.name ?: ""))
                    TextButton(onClick = { confirmDelete = f }) { Text("حذف") }
                }
            }
        }
    }
    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف ${f.name}؟") },
            text = { Text("ما في تراجع. خذ نسخة احتياطية قبل ما تحذف عوالمك.") },
            confirmButton = { TextButton(onClick = { f.delete(); confirmDelete = null; refresh++ }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("إلغاء") } }
        )
    }
}
