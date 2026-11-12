package com.closer.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closer.app.ui.components.SpectaclesVisual
import com.closer.app.ui.theme.Palette

@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var colorIndex by remember { mutableIntStateOf(0) }
    val existingName by vm.myName.collectAsState()
    val existingColor by vm.myColorIndex.collectAsState()

    // Unpaired-but-onboarded (e.g. after unpair): reuse saved identity.
    LaunchedEffect(Unit) {
        if (existingName.isNotEmpty()) {
            name = existingName
            colorIndex = existingColor
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))

        when (step) {
            0 -> ProfileStep(name, colorIndex, existingName.isNotEmpty()) { newName, newColor ->
                name = newName
                colorIndex = newColor
                step = 1
            }
            1 -> PermissionsStep { step = 2 }
            else -> PairStep(
                vm = vm,
                name = name,
                colorIndex = colorIndex,
                onEditProfile = { step = 0 },
            )
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ProfileStep(
    initialName: String,
    initialColor: Int,
    isEdit: Boolean,
    onContinue: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }

    SpectaclesVisual(
        myInitial = if (name.isEmpty()) "?" else name,
        partnerInitial = "?",
        myColorIndex = color,
        partnerColorIndex = 5,
        distanceText = "you + 💕",
        nearby = false,
        refreshing = false,
        modifier = Modifier.fillMaxWidth().height(110.dp),
    )
    Spacer(Modifier.height(24.dp))

    Text("Closer", fontSize = 40.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Text(
        "How far apart are the two of you, right now?",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))

    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(20) },
        label = { Text("Your name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(20.dp))

    Text("Pick your color", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Palette.accents.forEachIndexed { i, accent ->
            val selected = i == color
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.start, accent.end)))
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = if (selected) Color.White else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { color = i },
            )
        }
    }
    Spacer(Modifier.height(32.dp))

    Button(
        onClick = { onContinue(name.trim(), color) },
        enabled = name.trim().isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(if (isEdit) "Update" else "Continue", fontSize = 16.sp)
    }
}

@Composable
private fun PermissionsStep(onContinue: () -> Unit) {
    var locationGranted by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        locationGranted = grants.values.any { it }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("📍 Location", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Closer measures the distance between you two. Your location is shared only with your partner — nobody else, no history kept.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (locationGranted) {
                Text("✅ Location granted", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedButton(
                    onClick = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Allow location") }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    if (Build.VERSION.SDK_INT >= 33) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("💞 Nearby alerts", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "A gentle nudge when the two of you come within reach — so you can meet up.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Allow alerts") }
            }
        }
        Spacer(Modifier.height(24.dp))
    } else {
        Spacer(Modifier.height(8.dp))
    }

    if (!locationGranted) {
        Text(
            "Location access is needed to measure the distance between you two.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    Button(
        onClick = onContinue,
        enabled = locationGranted,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) { Text("Continue", fontSize = 16.sp) }
}

@Composable
private fun PairStep(
    vm: AppViewModel,
    name: String,
    colorIndex: Int,
    onEditProfile: () -> Unit,
) {
    var joinCode by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    val error by vm.lastError.collectAsState()
    val mock by vm.usingMock.collectAsState()

    Text("Pair up", fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        "One of you creates the couple, the other joins with the code.",
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    if (mock) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                "Demo mode · simulated partner. Add google-services.json to go live.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    Button(
        onClick = {
            creating = true
            vm.createCouple(name, colorIndex) { creating = false }
        },
        enabled = !creating,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (creating) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Create our couple", fontSize = 16.sp)
        }
    }

    Spacer(Modifier.height(20.dp))
    Text("— or —", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))

    OutlinedTextField(
        value = joinCode,
        onValueChange = { joinCode = it.uppercase().take(6) },
        label = { Text("Partner's code") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = {
            joining = true
            vm.joinCouple(joinCode, name, colorIndex) { joining = false }
        },
        enabled = joinCode.length == 6 && !joining,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        if (joining) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text("Join their couple", fontSize = 16.sp)
        }
    }

    error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }

    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = onEditProfile, shape = RoundedCornerShape(12.dp)) {
        Text("Edit name / color")
    }
}
