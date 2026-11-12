package com.closer.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closer.app.data.CoupleState
import com.closer.app.data.ShareMode
import com.closer.app.location.DistanceMath
import com.closer.app.ui.components.SpectaclesVisual

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

    // Show errors as a snackbar and clear them
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Closer", fontWeight = FontWeight.Bold, fontSize = 22.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, "Settings")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
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
                    if (partner == null) WaitingCard(state)
                    else PairedContent(vm, state, partner, refreshing, mode)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showSettings) {
        SettingsSheet(vm) { showSettings = false }
    }
}

@Composable
private fun NotPairedCard(vm: AppViewModel) {
    Spacer(Modifier.height(60.dp))
    SpectaclesVisual(
        myInitial = "?", partnerInitial = "?",
        myColorIndex = 0, partnerColorIndex = 5,
        distanceText = "pair up", nearby = false, refreshing = false,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    Spacer(Modifier.height(28.dp))
    Text(
        "The couple link is missing on this device.",
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = { vm.repairPairing() },
        shape = RoundedCornerShape(16.dp),
    ) { Text("Pair again") }
}

@Composable
private fun WaitingCard(state: CoupleState) {
    val clipboard = LocalClipboardManager.current
    Spacer(Modifier.height(40.dp))
    SpectaclesVisual(
        myInitial = state.me.initial, partnerInitial = "?",
        myColorIndex = state.me.colorIndex, partnerColorIndex = 5,
        distanceText = "waiting", nearby = false, refreshing = false,
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )
    Spacer(Modifier.height(28.dp))
    Text("Waiting for your partner…", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
    Text(
        "Share this code — they enter it on their phone and you're linked.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
        ) {
            Text(
                state.code,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = { clipboard.setText(AnnotatedString(state.code)) }) {
                Icon(Icons.Rounded.ContentCopy, "Copy code")
            }
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

    Spacer(Modifier.height(8.dp))

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp)) {
            SpectaclesVisual(
                myInitial = state.me.initial,
                partnerInitial = partner.initial,
                myColorIndex = state.me.colorIndex,
                partnerColorIndex = partner.colorIndex,
                distanceText = distance?.let { DistanceMath.formatDistance(it) } ?: "no fix",
                nearby = nearby,
                refreshing = refreshing,
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 18.dp),
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    Text(
        distance?.let { DistanceMath.formatDistance(it) } ?: "waiting for locations…",
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        color = if (nearby) Color(0xFFFF7A9C) else MaterialTheme.colorScheme.onBackground,
    )
    if (nearby) {
        Spacer(Modifier.height(4.dp))
        Text("Within reach 💞", color = Color(0xFFFF7A9C), fontSize = 15.sp)
    }

    // Bearing toward partner
    if (bearing != null && distance != null && distance > 50) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Navigation,
                contentDescription = "Bearing",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp).rotate(bearing),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${compassLabel(bearing)} · ${partner.name}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // last-seen chips
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LastSeenChip(state.me.name, mine?.timestampMs, state.me.sharingOn)
        LastSeenChip(partner.name, theirs?.timestampMs, partner.sharingOn)
    }

    Spacer(Modifier.height(24.dp))

    Button(
        onClick = { vm.refresh() },
        enabled = !refreshing,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(Icons.Rounded.MyLocation, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (refreshing) "Refreshing…" else "Refresh both of us", fontSize = 16.sp)
    }

    Spacer(Modifier.height(22.dp))

    // radius chips
    Text("Notify when within", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RADIUS_OPTIONS.forEach { r ->
            FilterChip(
                selected = state.radiusM == r,
                onClick = { vm.setRadius(r) },
                label = { Text("$r m") },
            )
        }
    }

    Spacer(Modifier.height(20.dp))

    // mode chips
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = mode == ShareMode.ECO,
            onClick = { vm.setMode(ShareMode.ECO) },
            label = { Text("🪫 Eco") },
        )
        FilterChip(
            selected = mode == ShareMode.LIVE,
            onClick = { vm.setMode(ShareMode.LIVE) },
            label = { Text("⚡ Live") },
        )
    }
    Text(
        if (mode == ShareMode.LIVE) "Live keeps your side fresh while you move."
        else "Eco refreshes on tap + every 15 min.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )

    Spacer(Modifier.height(16.dp))

    // pause sharing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(
            if (state.me.sharingOn) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            if (state.me.sharingOn) "Sharing your location" else "Sharing paused",
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = state.me.sharingOn,
            onCheckedChange = { vm.setSharing(it) },
        )
    }
}

@Composable
private fun LastSeenChip(name: String, ts: Long?, sharingOn: Boolean) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = buildString {
                    append(name)
                    append(" · ")
                    if (!sharingOn) append("paused")
                    else append(ts?.let { DistanceMath.relativeTime(it) } ?: "no fix")
                },
                fontSize = 12.sp,
            )
        },
    )
}

private fun compassLabel(bearing: Float): String {
    val labels = arrayOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
    )
    val idx = ((bearing / 22.5f).let { kotlin.math.round(it).toInt() } % 16 + 16) % 16
    return labels[idx]
}
