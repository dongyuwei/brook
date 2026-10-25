package com.txthinking.brookvpn

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.txthinking.brookvpn.ui.theme.BrookVPNTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobile.Mobile

class MainActivity : ComponentActivity() {

    private val importedLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        importedLink.value = handleIntent(intent)

        setContent {
            BrookVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(initialLink = importedLink.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importedLink.value = handleIntent(intent)
    }

    /** Returns a brook link if this intent carries one (VIEW brook:// or SEND text). */
    private fun handleIntent(intent: Intent?): String? {
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        return text.trim().takeIf { it.contains("://") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(initialLink: String? = null) {
    val context = LocalContext.current
    var link by remember(initialLink) { mutableStateOf(initialLink ?: Prefs.link(context)) }
    var showAppList by remember { mutableStateOf(false) }
    var whitelistCount by remember { mutableStateOf(Prefs.whitelist(context).size) }
    var running by remember { mutableStateOf(BrookVpnService.running) }
    var error by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }
    // Link is masked like a password; the eye button reveals it.
    var showLink by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Track the VPN service lifecycle.
    val receiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: Intent?) {
                i ?: return
                running = i.getBooleanExtra(BrookVpnService.EXTRA_RUNNING, false)
                val err = i.getStringExtra(BrookVpnService.EXTRA_ERROR)
                if (err != null) error = err
            }
        }
    }
    LaunchedEffect(Unit) {
        ContextCompat.registerReceiver(
            context, receiver,
            android.content.IntentFilter(BrookVpnService.ACTION_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun saveLink() {
        Prefs.saveLink(context, link.trim())
    }

    fun testLink() {
        saveLink()
        testResult = null
        scope.launch(Dispatchers.IO) {
            val message = try {
                Mobile.testLink(link.trim())
                "Link OK"
            } catch (e: Throwable) {
                "Invalid link: ${e.message}"
            }
            withContext(Dispatchers.Main) { testResult = message }
        }
    }

    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            BrookVpnService.start(context)
        } else {
            error = "VPN permission denied"
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Continue with or without notification permission.
        val prepare = android.net.VpnService.prepare(context)
        if (prepare != null) vpnConsent.launch(prepare)
        else BrookVpnService.start(context)
    }

    fun connect() {
        error = null
        saveLink()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val prepare = android.net.VpnService.prepare(context)
        if (prepare != null) {
            vpnConsent.launch(prepare)
            return
        }
        BrookVpnService.start(context)
    }

    if (showAppList) {
        AppListScreen(
            onDone = {
                whitelistCount = Prefs.whitelist(context).size
                showAppList = false
            },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it; saveLink() },
                label = { Text("Brook link") },
                placeholder = { Text("brook://...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = if (showLink) 2 else 1,
                visualTransformation = if (showLink) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation('•')
                },
                // Masked: password keyboard (no suggestions); shown: URI keys.
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (showLink) KeyboardType.Uri else KeyboardType.Password,
                ),
                trailingIcon = {
                    // Text-only toggle keeps the dependency set minimal.
                    TextButton(onClick = { showLink = !showLink }) {
                        Text(if (showLink) "Hide" else "Show")
                    }
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                enabled = !running,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    val text = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank()) {
                        link = text.trim()
                        saveLink()
                    }
                }, enabled = !running) { Text("Paste") }
                OutlinedButton(onClick = { testLink() }) { Text("Test") }
            }
            testResult?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !running) { showAppList = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Whitelisted apps", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (whitelistCount == 0) "None selected - VPN will not start"
                        else "$whitelistCount app(s) proxied, others direct",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Edit", color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = {
                    if (running) {
                        BrookVpnService.stop(context)
                    } else {
                        connect()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running) "Disconnect" else "Connect")
            }

            Text(
                if (running) "Connected" else "Disconnected",
                style = MaterialTheme.typography.titleMedium,
                color = if (running) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: Drawable,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    // Launchable apps only: what a user would call "installed apps".
    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { ri ->
                    AppEntry(
                        label = ri.loadLabel(pm).toString(),
                        packageName = ri.activityInfo.packageName,
                        icon = ri.activityInfo.loadIcon(pm),
                    )
                }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    var selected by remember { mutableStateOf(Prefs.whitelist(context)) }
    var query by remember { mutableStateOf("") }

    fun persist(next: Set<String>) {
        selected = next
        Prefs.saveWhitelist(context, next)
    }

    // Selected apps float to the top (alphabetical among themselves),
    // then the rest; both halves filtered by the search query on label
    // or package name.
    val display = remember(apps, selected, query) {
        val q = query.trim().lowercase()
        val matches = { a: AppEntry ->
            q.isEmpty() || a.label.lowercase().contains(q) || a.packageName.lowercase().contains(q)
        }
        val (chosen, others) = apps.filter(matches).partition { it.packageName in selected }
        chosen + others
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Whitelist apps") },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("←") }
                },
                actions = {
                    TextButton(onClick = {
                        persist(apps.map { it.packageName }.toSet())
                    }) { Text("All") }
                    TextButton(onClick = { persist(emptySet()) }) { Text("Clear") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (apps.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("Loading…") }
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(display, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    val bitmap by produceState(initialValue = null as Bitmap?, app) {
                        value = withContext(Dispatchers.IO) { app.icon.toBitmap(96) }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                persist(
                                    if (checked) selected - app.packageName
                                    else selected + app.packageName,
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bmp = bitmap
                        if (bmp != null) {
                            Image(
                                bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                persist(
                                    if (checked) selected - app.packageName
                                    else selected + app.packageName,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
