package com.closer.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closer.app.data.CoupleState
import com.closer.app.data.ShareMode
import com.closer.app.location.DistanceMath
import com.closer.app.ui.components.AmbientGlassBackground
import com.closer.app.ui.components.FrostedButton
import com.closer.app.ui.components.FrostedCard
import com.closer.app.ui.components.GlassTheme
import com.closer.app.ui.components.GlowButton
import com.closer.app.ui.components.SpectaclesVisual
import com.closer.app.ui.components.StylizedCodeDisplay
import com.closer.app.ui.components.shareCoupleInvite

private val RADIUS_OPTIONS = listOf(100, 200, 500)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val couple by vm.couple.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val mode by vm.mode.collectAsState()
    val error by vm.lastError.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    val prevNearby = remember { mutableStateOf(false) }
    LaunchedEffect(couple) {
        val c = couple ?: return@LaunchedEffect
        val myLoc = c.me.lastLocation
        val partnerLoc = c.partner?.lastLocation
        if (myLoc != null && partnerLoc != null) {
            val dist = com.closer.app.location.DistanceMath.haversineM(myLoc.point, partnerLoc.point)
            if (dist <= c.radiusM && !prevNearby.value) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            prevNearby.value = dist <= c.radiusM
        }
    }

    AmbientGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Closer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color.White,
                            letterSpacing = (-0.5).sp,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    actions = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showSettings = true
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF1E2129))
                                .border(1.dp, Color(0xFF2E323D), CircleShape),
                        ) {
                            Icon(Icons.Rounded.Settings, "Settings", tint = Color.White)
                        }
                    },
                    modifier = Modifier.statusBarsPadding().padding(horizontal = 10.dp),
                )
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val state = couple
                    if (state == null) {
                        NotPairedCard(vm)
                    } else {
                        val partner = state.partner
                        if (partner == null) WaitingCard(state, vm)
                        else PairedContent(vm, state, partner, refreshing, mode)
                    }
                    Spacer(Modifier.height(36.dp))
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(vm) { showSettings = false }
    }
}

@Composable
private fun NotPairedCard(vm: AppViewModel) {
    Spacer(Modifier.height(40.dp))
    SpectaclesVisual(
        myInitial = "?", partnerInitial = "?",
        myColorIndex = 0, partnerColorIndex = 5,
        distanceText = "pair up", nearby = false, refreshing = false,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    Spacer(Modifier.height(28.dp))
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Couple Link Missing",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You are not currently paired with anyone on this device.",
            textAlign = TextAlign.Center,
            color = Color(0xFFBAC5D5),
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        GlowButton(
            text = "Pair Again",
            onClick = { vm.repairPairing() },
        )
    }
}

@Composable
private fun WaitingCard(state: CoupleState, vm: AppViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Spacer(Modifier.height(16.dp))
    SpectaclesVisual(
        myInitial = state.me.initial, partnerInitial = "?",
        myColorIndex = state.me.colorIndex, partnerColorIndex = 5,
        distanceText = "waiting", nearby = false, refreshing = false,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    Spacer(Modifier.height(24.dp))

    Text(
        "Waiting for Your Partner 💕",
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = (-0.5).sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Share this invite code or send a direct link so your partner can connect with you.",
        fontSize = 14.sp,
        color = Color(0xFFBAC5D5),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Spacer(Modifier.height(24.dp))

    // Code Jewel Card
    FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        borderBrush = GlassTheme.CardBorderRoseBrush,
        contentPadding = 24.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "YOUR PAIRING CODE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GlassTheme.RoseGlow,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(16.dp))

            StylizedCodeDisplay(code = state.code)

            Spacer(Modifier.height(24.dp))

            // Main Action: Share Invite Link
            GlowButton(
                text = "Share Invite Link",
                onClick = { shareCoupleInvite(context, state.code) },
                icon = Icons.Rounded.Share,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Secondary Action: Copy Code
            FrostedButton(
                text = "Copy Code",
                onClick = {
                    clipboard.setText(AnnotatedString(state.code))
                    Toast.makeText(context, "Code copied to clipboard! ✨", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Rounded.ContentCopy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PairedContent(
    vm: AppViewModel,
    state: CoupleState,
    partner: com.closer.app.data.MemberProfile,
    refreshing: Boolean,
    mode: ShareMode,
) {
    val mine = state.me.lastLocation
    val theirs = partner.lastLocation
    val distance = if (mine != null && theirs != null) {
        DistanceMath.haversineM(mine.point, theirs.point)
    } else null
    val bearing = if (mine != null && theirs != null) DistanceMath.bearingDeg(mine.point, theirs.point) else null
    val nearby = distance != null && distance <= state.radiusM

    Spacer(Modifier.height(12.dp))

    // Spectacles Frosted Hero Card
    FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        borderBrush = if (nearby) GlassTheme.CardBorderRoseBrush else GlassTheme.CardBorderBrush,
        contentPadding = 22.dp,
    ) {
        SpectaclesVisual(
            myInitial = state.me.initial,
            partnerInitial = partner.initial,
            myColorIndex = state.me.colorIndex,
            partnerColorIndex = partner.colorIndex,
            distanceText = distance?.let { DistanceMath.formatDistance(it) } ?: "no fix",
            nearby = nearby,
            refreshing = refreshing,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
    }

    Spacer(Modifier.height(24.dp))

    // Hero Distance Display
    Text(
        distance?.let { DistanceMath.formatDistance(it) } ?: "waiting for fix…",
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
        color = if (nearby) GlassTheme.AppleRose else Color.White,
    )
    if (nearby) {
        Spacer(Modifier.height(4.dp))
        Text("Within reach 💕", color = GlassTheme.AppleRose, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    // Compass Bearing
    if (bearing != null && distance != null && distance > 50) {
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E2129))
                .border(1.dp, Color(0xFF2E323D), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                Icons.Rounded.Navigation,
                contentDescription = "Bearing",
                tint = GlassTheme.AppleRose,
                modifier = Modifier.size(16.dp).rotate(bearing),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${compassLabel(bearing)} · toward ${partner.name}",
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    Spacer(Modifier.height(18.dp))

    // Last seen chips
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FrostedStatusChip(state.me.name, mine?.timestampMs, state.me.sharingOn)
        FrostedStatusChip(partner.name, theirs?.timestampMs, partner.sharingOn)
    }

    Spacer(Modifier.height(26.dp))

    // Refresh Action
    GlowButton(
        text = if (refreshing) "Refreshing locations…" else "Refresh Both Locations",
        onClick = { vm.refresh() },
        enabled = !refreshing,
        loading = refreshing,
        icon = Icons.Rounded.Refresh,
    )

    Spacer(Modifier.height(24.dp))

    val haptic = LocalHapticFeedback.current

    // Radius selector
    FrostedCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text("Nearby Alert Threshold", fontSize = 13.sp, color = Color(0xFFBAC5D5), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RADIUS_OPTIONS.forEach { r ->
                val selected = state.radiusM == r
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) GlassTheme.AppleRose else Color(0xFF1E2129))
                        .border(
                            width = 1.dp,
                            color = if (selected) Color.White.copy(alpha = 0.25f) else Color(0xFF2E323D),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.setRadius(r)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$r m",
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Mode Selector (Eco / Live)
    FrostedCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Text("Update Mode", fontSize = 13.sp, color = Color(0xFFBAC5D5), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(
                ShareMode.ECO to "🪫 Eco (15 min)",
                ShareMode.LIVE to "⚡ Live (Fast)",
            ).forEach { (m, label) ->
                val selected = mode == m
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) GlassTheme.AppleIndigo else Color(0xFF1E2129))
                        .border(
                            width = 1.dp,
                            color = if (selected) Color.White.copy(alpha = 0.25f) else Color(0xFF2E323D),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.setMode(m)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            if (mode == ShareMode.LIVE) "⚡ Live keeps your widget and distance continuously fresh as you move."
            else "🪫 Eco updates on tap and every 15 minutes to save battery.",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(top = 10.dp),
        )
    }

    Spacer(Modifier.height(16.dp))

    // Sharing Toggle
    FrostedCard(modifier = Modifier.fillMaxWidth(), contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.me.sharingOn) GlassTheme.AppleRose.copy(alpha = 0.2f)
                        else Color(0xFF1E2129)
                    )
                    .border(
                        1.dp,
                        if (state.me.sharingOn) GlassTheme.AppleRose.copy(alpha = 0.4f) else Color(0xFF2E323D),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.me.sharingOn) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    null,
                    tint = if (state.me.sharingOn) GlassTheme.AppleRose else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.me.sharingOn) "Sharing Your Location" else "Location Sharing Paused",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    if (state.me.sharingOn) "Your partner can see your distance" else "Only you can see their last known point",
                    fontSize = 12.sp,
                    color = Color(0xFFBAC5D5),
                )
            }
            Switch(
                checked = state.me.sharingOn,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.setSharing(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GlassTheme.AppleRose,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                    uncheckedTrackColor = Color(0xFF2E323D),
                ),
            )
        }
    }
}

@Composable
private fun FrostedStatusChip(name: String, ts: Long?, sharingOn: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E2129))
            .border(1.dp, Color(0xFF2E323D), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = buildString {
                append(name)
                append(" · ")
                if (!sharingOn) append("paused")
                else append(ts?.let { DistanceMath.relativeTime(it) } ?: "no fix")
            },
            fontSize = 12.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun compassLabel(bearing: Float): String {
    val labels = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )
    val idx = ((bearing / 22.5f).let { kotlin.math.round(it).toInt() } % 16 + 16) % 16
    return labels[idx]
}
