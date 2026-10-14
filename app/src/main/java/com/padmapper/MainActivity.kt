package com.padmapper

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        setContent { MaterialTheme { Surface { Screen() } } }
    }

    @Composable
    fun Screen() {
        var tick by remember { mutableIntStateOf(0) }
        var text by remember { mutableStateOf(Store.dump()) }
        var msg by remember { mutableStateOf("") }
        val bound = remember(tick) { runCatching { Shizuku.pingBinder() }.getOrDefault(false) }
        val perm = remember(tick) { bound && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
        val a11y = remember(tick) { MapperService.instance != null }

        Column(Modifier.padding(16.dp).statusBarsPadding().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PadMapper", style = MaterialTheme.typography.headlineSmall)
            Text("Shizuku running: $bound   permission: $perm   accessibility service: $a11y")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ runCatching { Shizuku.requestPermission(1) }; tick++ }) { Text("Shizuku access") }
                Button({ tick++; MapperService.instance?.reload() }) { Text("Refresh") }
            }
            Text("Android 13+: open App info, tap the three dots, choose 'Allow restricted settings', then enable PadMapper in Accessibility.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }) { Text("App info") }
                Button({ startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Accessibility") }
            }
            Text("Ctrl+Shift+M toggles the dashboard (hidden = mappings active). Scroll Lock pauses mapping.")
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 10, label = { Text("Profile JSON") })
            Button({
                msg = runCatching {
                    Store.save(Store.json.decodeFromString<Profile>(text))
                    MapperService.instance?.reload(); "Saved"
                }.getOrElse { "Invalid JSON: ${it.message}" }
            }) { Text("Save profile") }
            Text(msg)
        }
    }
}
