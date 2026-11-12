package com.closer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closer.app.BuildConfig
import com.closer.app.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val name by vm.myName.collectAsState()
    val color by vm.myColorIndex.collectAsState()
    val nearAlerts by vm.nearAlerts.collectAsState()
    val quietFrom by vm.quietFrom.collectAsState()
    val quietTo by vm.quietTo.collectAsState()
    val usingMock by vm.usingMock.collectAsState()

    var editName by remember { mutableStateOf(name) }
    var editColor by remember { mutableStateOf(color) }
    var confirmUnpair by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.pinWidget() },
                shape = RoundedCornerShape(14.dp),
            ) { Text("✨ Add the widget to your home screen") }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it.take(20) },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Palette.accents.forEachIndexed { i, accent ->
                    val selected = i == editColor
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent.start, accent.end)))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = if (selected) Color.White else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { editColor = i },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.updateProfile(editName.trim(), editColor) },
                enabled = editName.trim().isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Save profile") }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nearby alerts 💞", fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = nearAlerts, onCheckedChange = { vm.setNearAlerts(it) })
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Quiet hours — no alerts during these times",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Off" to (0 to 0),
                    "Night 23–7" to (23 to 7),
                    "Late 22–8" to (22 to 8),
                ).forEach { (label, hours) ->
                    val selected = quietFrom == hours.first && quietTo == hours.second
                    FilterChip(
                        selected = selected,
                        onClick = { vm.setQuietHours(hours.first, hours.second) },
                        label = { Text(label, fontSize = 12.sp) },
                    )
                }
            }

            if (BuildConfig.DEBUG && usingMock) {
                Spacer(Modifier.height(16.dp))
                DebugPartnerPanel(vm)
            }

            Spacer(Modifier.height(22.dp))
            if (confirmUnpair) {
                Text(
                    "Unpairing deletes the local link. You can pair again with a new code.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = { confirmUnpair = false }) { Text("Keep us") }
                    Spacer(Modifier.size(10.dp))
                    Button(
                        onClick = { vm.unpair(); onDismiss() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Unpair") }
                }
            } else {
                TextButton(onClick = { confirmUnpair = true }) {
                    Text("Unpair…", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DebugPartnerPanel(vm: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("Demo · move partner", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "5 km" to 5_000.0,
                "1 km" to 1_000.0,
                "200 m" to 200.0,
                "50 m" to 50.0,
                "1 m" to 1.0,
                "30 cm" to 0.3,
            ).forEach { (label, meters) ->
                FilterChip(
                    selected = false,
                    onClick = { vm.debugPlacePartner(meters) },
                    label = { Text(label, fontSize = 11.sp) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Drift speed (their simulated walking)", fontSize = 12.sp)
        var speed by remember { mutableStateOf(1.2f) }
        Slider(
            value = speed,
            onValueChange = { speed = it; vm.debugPartnerSpeed(it.toDouble()) },
            valueRange = 0f..3f,
        )
        Text(
            "%.1f m/s".format(speed),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
