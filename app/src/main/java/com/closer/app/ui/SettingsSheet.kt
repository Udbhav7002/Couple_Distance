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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.closer.app.ui.components.FrostedButton
import com.closer.app.ui.components.FrostedCard
import com.closer.app.ui.components.GlassTheme
import com.closer.app.ui.components.GlowButton
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
    var editColor by remember { mutableIntStateOf(color) }
    var confirmUnpair by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141D),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 36.dp),
        ) {
            Text(
                "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.height(18.dp))

            // Widget Pin Action
            FrostedCard(
                modifier = Modifier.fillMaxWidth(),
                borderBrush = GlassTheme.CardBorderRoseBrush,
                contentPadding = 16.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GlassTheme.RoseGlow.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Widgets, null, tint = GlassTheme.RoseGlow, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spectacles Home Widget", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Add the frosted live spectacles to your home screen", fontSize = 12.sp, color = Color(0xFFBAC5D5))
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlowButton(
                    text = "Add Widget to Home Screen",
                    onClick = { vm.pinWidget() },
                    icon = Icons.Rounded.Widgets,
                )
            }

            Spacer(Modifier.height(20.dp))

            // Profile Section
            Text("YOUR PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))

            FrostedCard(modifier = Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it.take(20) },
                    label = { Text("Your display name", color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GlassTheme.RoseGlow,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                    ),
                )
                Spacer(Modifier.height(14.dp))

                Text("Lens Color", fontSize = 13.sp, color = Color(0xFFBAC5D5))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Palette.accents.forEachIndexed { i, accent ->
                        val selected = i == editColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(accent.start, accent.end)))
                                .border(
                                    width = if (selected) 2.5.dp else 0.dp,
                                    color = if (selected) Color.White else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { editColor = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                FrostedButton(
                    text = "Save Profile",
                    onClick = { vm.updateProfile(editName.trim(), editColor) },
                    enabled = editName.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Alerts & Quiet Hours Section
            Text("ALERTS & QUIET HOURS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))

            FrostedCard(modifier = Modifier.fillMaxWidth(), contentPadding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nearby Alerts 💕", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("Notify when entering your chosen radius", fontSize = 12.sp, color = Color(0xFFBAC5D5))
                    }
                    Switch(
                        checked = nearAlerts,
                        onCheckedChange = { vm.setNearAlerts(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GlassTheme.RoseGlow,
                        ),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Quiet hours (silence alerts):", fontSize = 12.sp, color = Color(0xFFBAC5D5))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "Off" to (0 to 0),
                        "Night 23–7" to (23 to 7),
                        "Late 22–8" to (22 to 8),
                    ).forEach { (label, hours) ->
                        val selected = quietFrom == hours.first && quietTo == hours.second
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) GlassTheme.RoseGlow else Color.White.copy(alpha = 0.06f))
                                .border(
                                    1.dp,
                                    if (selected) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { vm.setQuietHours(hours.first, hours.second) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, fontSize = 12.sp, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }

            // Demo Controls
            if (usingMock) {
                Spacer(Modifier.height(20.dp))
                Text("DEMO SIMULATION CONTROLS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.5.sp)
                Spacer(Modifier.height(10.dp))
                DebugPartnerPanel(vm)
            }

            Spacer(Modifier.height(24.dp))

            // Danger Zone / Unpair
            if (confirmUnpair) {
                FrostedCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderBrush = Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFF8E7C))),
                    contentPadding = 16.dp,
                ) {
                    Text(
                        "Unpair with Partner?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6B6B),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This deletes your local pairing link. You will need a new code to pair again.",
                        fontSize = 13.sp,
                        color = Color(0xFFBAC5D5),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FrostedButton(
                            text = "Cancel",
                            onClick = { confirmUnpair = false },
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFD32F2F))
                                .clickable { vm.unpair(); onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Unpair", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = { confirmUnpair = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Unpair Couple…", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DebugPartnerPanel(vm: AppViewModel) {
    FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp,
    ) {
        Text("Teleport Simulated Partner", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                "5 km" to 5_000.0,
                "1 km" to 1_000.0,
                "200 m" to 200.0,
                "50 m" to 50.0,
                "1 m" to 1.0,
            ).forEach { (label, meters) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .clickable { vm.debugPlacePartner(meters) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Simulated partner walking speed", fontSize = 12.sp, color = Color(0xFFBAC5D5))
        var speed by remember { mutableFloatStateOf(1.2f) }
        Slider(
            value = speed,
            onValueChange = { speed = it; vm.debugPartnerSpeed(it.toDouble()) },
            valueRange = 0f..3f,
            colors = SliderDefaults.colors(
                thumbColor = GlassTheme.RoseGlow,
                activeTrackColor = GlassTheme.RoseGlow,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f),
            ),
        )
        Text(
            "%.1f m/s (drift speed)".format(speed),
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
        )
    }
}
