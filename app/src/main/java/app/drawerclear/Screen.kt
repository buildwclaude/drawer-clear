@file:OptIn(ExperimentalMaterial3Api::class)

package app.drawerclear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MAX_ROWS = 400

@Composable
fun DrawerClearScreen(
    shizuku: ShizukuShell.Status,
    onGrant: () -> Unit,
    onRefresh: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val ready = shizuku.state == ShizukuShell.State.READY

    var pkg by rememberSaveable { mutableStateOf(LauncherInspector.detectHomePackage(ctx) ?: "com.nothing.launcher") }
    var scan by remember { mutableStateOf<Scan?>(null) }
    var themeId by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var rgb by rememberSaveable { mutableStateOf("000000") }
    var alpha by remember { mutableFloatStateOf(0f) }
    var log by rememberSaveable { mutableStateOf("") }
    var ours by remember { mutableStateOf<List<Overlay.Ours>>(emptyList()) }

    val argb: Int? = rgb.trim().removePrefix("#").takeIf { it.length == 6 }?.toIntOrNull(16)
        ?.let { ((alpha * 255).roundToInt() shl 24) or it }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun work(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } catch (e: Throwable) {
                log = "$label failed: ${e.javaClass.simpleName}: ${e.message}"
                toast("$label failed")
            } finally {
                busy = false
            }
        }
    }

    fun refreshOurs() = scope.launch {
        ours = withContext(Dispatchers.IO) { runCatching { Overlay.list(pkg.trim()) }.getOrDefault(emptyList()) }
    }

    fun doScan() = work("Scan") {
        scan = withContext(Dispatchers.Default) { LauncherInspector.scan(ctx, pkg.trim(), themeId) }
    }

    fun shell(label: String, op: (String) -> String) = work(label) {
        log = withContext(Dispatchers.IO) { op(pkg.trim()) }
        ours = withContext(Dispatchers.IO) { Overlay.list(pkg.trim()) }
    }

    fun pick(r: Resolved) {
        val t = r.targets.firstOrNull()
        if (t == null) {
            toast("${r.entry.name} only uses system colours, so it can't be overlaid here")
        } else {
            target = t
            toast("Target set: ${t.substringAfter(':')}")
        }
    }

    LaunchedEffect(ready) { if (ready) refreshOurs() }

    val matches = remember(scan, query) {
        val s = scan ?: return@remember emptyList<Resolved>()
        val q = query.trim()
        (s.attrs + s.colors).filter { q.isEmpty() || it.entry.name.contains(q, true) || it.chain.contains(q, true) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Drawer Clear") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("1. Shizuku") {
                    when (shizuku.state) {
                        ShizukuShell.State.NOT_RUNNING -> {
                            Text("Not running. Install Shizuku from the Play Store and start it with Wireless debugging.")
                            OutlinedButton(onClick = onRefresh) { Text("Check again") }
                        }
                        ShizukuShell.State.NO_PERMISSION -> {
                            Text("Shizuku is running (${shizuku.uid}), but Drawer Clear isn't allowed to use it yet.")
                            Button(onClick = onGrant) { Text("Grant permission") }
                        }
                        ShizukuShell.State.READY -> Text("Ready, running as ${shizuku.uid}.")
                    }
                }
            }

            item {
                Section("2. Launcher") {
                    OutlinedTextField(
                        value = pkg,
                        onValueChange = { pkg = it.trim() },
                        label = { Text("Package") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    scan?.let { s ->
                        Text("Version ${s.version} · ${s.typeCounts.values.sum()} resources", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Theme: ${s.theme?.name ?: "none"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showThemes = true }) { Text("Change") }
                        }
                    }
                    Button(onClick = { doScan() }, enabled = !busy) { Text(if (scan == null) "Scan launcher" else "Rescan") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            item {
                Section("3. Overlay tester") {
                    Text(
                        "Tap a colour in the list below to set it as the target, then Apply.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it.trim() },
                        label = { Text("Target resource") },
                        placeholder = { Text("$pkg:color/…") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = rgb,
                            onValueChange = { rgb = it.take(7) },
                            label = { Text("Colour (RGB hex)") },
                            singleLine = true,
                            isError = argb == null,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Swatch(argb, 48.dp)
                    }
                    Text("Opacity ${(alpha * 100).roundToInt()}%")
                    Slider(value = alpha, onValueChange = { alpha = it }, steps = 19)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { rgb = "000000"; alpha = 0f }) { Text("Clear") }
                        OutlinedButton(onClick = { rgb = "000000"; alpha = 0.3f }) { Text("Dim 30%") }
                        OutlinedButton(onClick = { rgb = "000000"; alpha = 0.5f }) { Text("Dim 50%") }
                    }
                    if (!ready) {
                        Text("Shizuku isn't ready, see step 1.", color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val c = argb ?: return@Button
                                val t = target
                                shell("Apply") { p -> Overlay.apply(p, t, c) }
                            },
                            enabled = ready && !busy && argb != null && target.contains(":color/"),
                        ) { Text("Apply") }
                        OutlinedButton(
                            onClick = { shell("Remove") { p -> Overlay.disableAll(p) } },
                            enabled = ready && !busy,
                        ) { Text("Remove all") }
                    }
                    OutlinedButton(
                        onClick = { shell("Restart") { p -> Overlay.restartLauncher(p) } },
                        enabled = ready && !busy,
                    ) { Text("Restart launcher") }
                    if (ours.isNotEmpty()) {
                        Text("Overlays:", style = MaterialTheme.typography.labelLarge)
                        ours.forEach {
                            Text(
                                (if (it.enabled) "● on   " else "○ off  ") + it.id,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    if (log.isNotEmpty()) {
                        SelectionContainer {
                            Text(log, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            item {
                Section("4. Share results") {
                    Text(
                        "If the list doesn't make the right colour obvious, send these so it can be found.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = {
                            val s = scan ?: return@OutlinedButton
                            clipboard.setText(AnnotatedString(LauncherInspector.candidatesText(s)))
                            toast("Copied likely colours")
                        },
                        enabled = scan != null,
                    ) { Text("Copy likely colours") }
                    OutlinedButton(
                        onClick = {
                            val s = scan ?: return@OutlinedButton
                            work("Save report") {
                                val path = withContext(Dispatchers.IO) { LauncherInspector.saveReport(ctx, s) }
                                toast("Saved $path")
                            }
                        },
                        enabled = scan != null && !busy,
                    ) { Text("Save full report to Downloads") }
                    OutlinedButton(
                        onClick = {
                            work("Export APK") {
                                val saved = withContext(Dispatchers.IO) { LauncherInspector.exportApk(ctx, pkg.trim()) }
                                log = "Exported:\n" + saved.joinToString("\n")
                                toast("Saved ${saved.size} file(s) to Download/DrawerClear")
                            }
                        },
                        enabled = !busy,
                    ) { Text("Export launcher APK to Downloads") }
                }
            }

            scan?.let { s ->
                item { SectionTitle("Likely drawer colours (${s.candidates.size})") }
                if (s.candidates.isEmpty()) {
                    item { Text("None matched all_apps / scrim / drawer. Try the search below.", style = MaterialTheme.typography.bodySmall) }
                }
                items(s.candidates, key = { "c${it.entry.id}" }) { r -> ResolvedRow(r) { pick(r) } }

                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search ${s.attrs.size} colour attrs + ${s.colors.size} colours") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                items(matches.take(MAX_ROWS), key = { "a${it.entry.id}" }) { r -> ResolvedRow(r) { pick(r) } }
                if (matches.size > MAX_ROWS) {
                    item { Text("…and ${matches.size - MAX_ROWS} more. Narrow the search.", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    val s = scan
    if (showThemes && s != null) {
        AlertDialog(
            onDismissRequest = { showThemes = false },
            confirmButton = { TextButton(onClick = { showThemes = false }) { Text("Close") } },
            title = { Text("Resolve attrs using theme") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(s.themes, key = { it.id }) { t ->
                        Text(
                            t.name,
                            fontWeight = if (t.id == s.theme?.id) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showThemes = false
                                    themeId = t.id
                                    doScan()
                                }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ResolvedRow(r: Resolved, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatch(r.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${r.entry.type}/${r.entry.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                r.chain,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Swatch(argb: Int?, size: Dp = 28.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (argb != null) Color(argb) else Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (argb == null) Text("?", style = MaterialTheme.typography.labelSmall)
    }
}
