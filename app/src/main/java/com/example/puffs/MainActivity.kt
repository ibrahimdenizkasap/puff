package com.example.puffs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items // Added import
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState // Added import
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll // Added import
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.puffs.overlay.OverlayService
import com.example.puffs.ui.MainViewModel
import com.example.puffs.ui.theme.PuffsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import com.example.puffs.util.importCsvFromUri
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
// Removed duplicate imports for rememberLauncherForActivityResult, rememberCoroutineScope, launch, importCsvFromUri
import com.example.puffs.util.exportCsvToUri
import com.example.puffs.ui.GroupedTimeline
// Removed duplicate imports for foundation.layout, material3, unit.dp
import com.example.puffs.ui.SessionHistoryList
import com.example.puffs.util.DayRollover

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PuffsTheme { AppUI(vm) } }

        // Force system bars to your dark slate + light (white) icons
        window.statusBarColor = 0xFF0B0F14.toInt()       // top bar
        window.navigationBarColor = 0xFF0B0F14.toInt()   // bottom bar

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false       // false = white status icons
        controller.isAppearanceLightNavigationBars = false   // false = white nav icons
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                value,
                style = MaterialTheme.typography.displaySmall, // big number
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun AppUI(vm: MainViewModel){
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    exportCsvToUri(ctx, uri)
                    Toast.makeText(ctx, "Exported CSV", Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    Toast.makeText(ctx, "Export failed: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {}

            scope.launch {
                try {
                    val added = importCsvFromUri(ctx, uri)
                    // 👇 Rebuild sessions from imported puffs
                    vm.rebuildSessions()

                    Toast.makeText(
                        ctx,
                        if (added > 0) "Imported $added puffs and rebuilt sessions"
                        else "No timestamps found",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (t: Throwable) {
                    Toast.makeText(ctx, "Import failed: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    // collect StateFlows from VM
    val todayCount by vm.todayCount.collectAsState()
    val todayPuffs by vm.todayPuffs.collectAsState()
    val weekTotal by vm.weekTotal.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val draft by vm.activeDraft.collectAsState()
    val allPuffs by vm.allPuffs.collectAsState()

    // Add ×N dialog state
    var showAddN by remember { mutableStateOf(false) }
    var nText by remember { mutableStateOf("3") }

    if (showAddN) {
        AlertDialog(
            onDismissRequest = { showAddN = false },
            title = { Text("Add ×N") },
            text = {
                OutlinedTextField(
                    value = nText,
                    onValueChange = { nText = it.filter(Char::isDigit).take(4) },
                    singleLine = true,
                    label = { Text("How many?") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = nText.toIntOrNull()?.coerceIn(1, 999) ?: 0
                    if (n > 0) vm.addMany(n)
                    showAddN = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddN = false }) { Text("Cancel") } }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),   // ✅ page scroll
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🫁 Puff Counter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            // stats
            // Top row: Current Session & Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Current Session",
                    value = (draft?.puffCount ?: 0).toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Today",
                    value = todayCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Full-width 7-Day Total card
            StatCard(
                title = "7-Day Total",
                value = weekTotal.toString(),
                modifier = Modifier
                    .fillMaxWidth()
            )

            // controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { vm.addPuff() },
                    modifier = Modifier.weight(1f)
                ) { Text("+1 Puff") }

                OutlinedButton(
                    onClick = { vm.undo() },
                    modifier = Modifier.weight(1f)
                ) { Text("Undo") }

                OutlinedButton(
                    onClick = { showAddN = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Add ×N") }
            }


            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(ctx)) {
                            val i = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + ctx.packageName)
                            )
                            ctx.startActivity(i)
                        } else {
                            ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB), // bright blue
                        contentColor = Color.White          // white text
                    )
                ) { Text("Start Bubble") }

                OutlinedButton(onClick = { vm.endSessionNow() }) { Text("Save Session") }
            }

            Text(
                "Sessions",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Show live current session panel (only if a draft exists)
            draft?.let { d ->
                CurrentSessionPanel(
                    draft = d,
                    puffs = vm.currentSessionPuffs.collectAsState().value,
                    onEndSession = { vm.endSessionNow() }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)   // ✅ bounded height, no weight()
            ) {
                items(sessions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${fmtTime(s.startTs)} – ${fmtTime(s.endTs)}")
                        Text("${s.puffCount} puffs")
                    }
                    Divider()
                }
            }



            Text("Timeline", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)   // ✅ bounded container height
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Cumulative", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Divider()
                        GroupedTimeline(
                            allPuffs = allPuffs,
                            daysBack = 7,
                            modifier = Modifier.fillMaxSize()   // ✅ fills the bounded pane
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SessionHistoryList(
                        allPuffs = allPuffs,
                        daysBack = 7,
                        modifier = Modifier.fillMaxSize()       // ✅ fills the bounded pane
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp), // Adjusted top padding
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("puff_data.csv") },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("Export CSV") }

                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("text/*","text/csv")) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) { Text("Import CSV") }
            }
        }
    }
}

private fun fmtTime(ts: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ts))

@Composable
fun CurrentSessionPanel(
    draft: com.example.puffs.data.DraftSession,
    puffs: List<com.example.puffs.data.Puff>,
    onEndSession: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    val start = remember(draft.startTs) { fmtTime(draft.startTs) }
    val last = remember(draft.lastPuffTs) { fmtTime(draft.lastPuffTs) }
    val elapsedMin = ((draft.lastPuffTs - draft.startTs).coerceAtLeast(0) / 60000).toInt()

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {

            // Header row (tap to expand/collapse)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Active Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))

            // Chips row: Start • Last • Elapsed
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(label = { Text("Start $start") })
                AssistChip(label = { Text("Last $last") })
                AssistChip(label = { Text("$elapsedMin min") })
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    // mini timeline for the current session
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        // show time & cumulative within this draft
                        val fmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
                        val ordered = remember(puffs) { puffs.sortedBy { it.timestamp } }
                        LazyColumn {
                            itemsIndexed(ordered) { idx, puff ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(fmt.format(java.util.Date(puff.timestamp)))
                                    Text("${idx + 1}")
                                }
                                Divider()
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(onClick = onEndSession) { Text("End session now") }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistChip(label: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) { label() }
    }
}
