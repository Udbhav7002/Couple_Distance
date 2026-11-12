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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closer.app.ui.components.AmbientGlassBackground
import com.closer.app.ui.components.FrostedButton
import com.closer.app.ui.components.FrostedCard
import com.closer.app.ui.components.GlassTheme
import com.closer.app.ui.components.GlowButton
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

    AmbientGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

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
        modifier = Modifier.fillMaxWidth().height(115.dp),
    )
    Spacer(Modifier.height(20.dp))

    Text(
        "Closer",
        fontSize = 42.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = (-0.5).sp,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "How far apart are the two of you, right now?",
        fontSize = 15.sp,
        color = Color(0xFFBAC5D5),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))

    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Your Identity",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(20) },
            placeholder = { Text("What should your partner see?", color = Color.White.copy(alpha = 0.4f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = GlassTheme.RoseGlow,
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                cursorColor = GlassTheme.RoseGlow,
            ),
        )
        Spacer(Modifier.height(20.dp))

        Text("Choose your lens color", fontSize = 13.sp, color = Color(0xFFBAC5D5))
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Palette.accents.forEachIndexed { i, accent ->
                val selected = i == color
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent.start, accent.end)))
                        .border(
                            width = if (selected) 2.5.dp else 0.dp,
                            color = if (selected) Color.White else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { color = i },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    GlowButton(
        text = if (isEdit) "Update Profile" else "Continue",
        onClick = { onContinue(name.trim(), color) },
        enabled = name.trim().isNotEmpty(),
        icon = Icons.Rounded.ArrowForward,
    )
}

@Composable
private fun PermissionsStep(onContinue: () -> Unit) {
    var locationGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        locationGranted = grants.values.any { it }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationGranted = granted
    }

    Text(
        "Permissions",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = (-0.5).sp,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Closer keeps location strictly between the two of you — zero history kept.",
        fontSize = 14.sp,
        color = Color(0xFFBAC5D5),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    // Location Permission Card
    FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        borderBrush = if (locationGranted) GlassTheme.CardBorderRoseBrush else GlassTheme.CardBorderBrush,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E86DE).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocationOn, null, tint = Color(0xFF54D3F0), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Location Access", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                Text("Measures the live distance between you two", fontSize = 12.sp, color = Color(0xFFBAC5D5))
            }
        }
        Spacer(Modifier.height(14.dp))

        if (locationGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = Color(0xFF3ECFC0), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Location permission granted", fontSize = 13.sp, color = Color(0xFF3ECFC0), fontWeight = FontWeight.Medium)
            }
        } else {
            FrostedButton(
                text = "Allow Location",
                onClick = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // Notifications Card (Android 13+)
    if (Build.VERSION.SDK_INT >= 33) {
        FrostedCard(
            modifier = Modifier.fillMaxWidth(),
            borderBrush = if (notificationGranted) GlassTheme.CardBorderRoseBrush else GlassTheme.CardBorderBrush,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GlassTheme.RoseGlow.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Notifications, null, tint = GlassTheme.RoseGlow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Nearby Alerts", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                    Text("Gentle nudge when you both enter reach", fontSize = 12.sp, color = Color(0xFFBAC5D5))
                }
            }
            Spacer(Modifier.height(14.dp))

            if (notificationGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF3ECFC0), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Alerts enabled", fontSize = 13.sp, color = Color(0xFF3ECFC0), fontWeight = FontWeight.Medium)
                }
            } else {
                FrostedButton(
                    text = "Allow Nearby Alerts",
                    onClick = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    } else {
        Spacer(Modifier.height(16.dp))
    }

    if (!locationGranted) {
        Text(
            "Location is required to calculate the distance to your partner.",
            fontSize = 12.sp,
            color = Color(0xFFFF8E7C),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    GlowButton(
        text = "Continue",
        onClick = onContinue,
        enabled = locationGranted,
        icon = Icons.Rounded.ArrowForward,
    )
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

    Text(
        "Pair With Partner",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        letterSpacing = (-0.5).sp,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Create a link or enter the code shared by your partner.",
        fontSize = 14.sp,
        color = Color(0xFFBAC5D5),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))

    if (mock) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✨ Demo Mode: Simulated partner ready. No setup needed.",
                fontSize = 12.sp,
                color = Color(0xFFFFB3C6),
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    // Card 1: Create our couple
    FrostedCard(
        modifier = Modifier.fillMaxWidth(),
        borderBrush = GlassTheme.CardBorderRoseBrush,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.RoseGlow.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, null, tint = GlassTheme.RoseGlow, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Start a New Couple", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Get your private link & code to share", fontSize = 12.sp, color = Color(0xFFBAC5D5))
            }
        }
        Spacer(Modifier.height(16.dp))

        GlowButton(
            text = "Create Our Couple",
            onClick = {
                creating = true
                vm.createCouple(name, colorIndex) { creating = false }
            },
            loading = creating,
            icon = Icons.Rounded.Favorite,
        )
    }

    Spacer(Modifier.height(20.dp))

    // Divider
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
        Text(
            "  OR JOIN EXISTING LINK  ",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
    }

    Spacer(Modifier.height(20.dp))

    // Card 2: Join partner's couple
    FrostedCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlassTheme.VioletGlow.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Link, null, tint = GlassTheme.VioletGlow, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Enter Partner's Code", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("Type the 6-character code they shared", fontSize = 12.sp, color = Color(0xFFBAC5D5))
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = joinCode,
            onValueChange = { joinCode = it.uppercase().take(6) },
            placeholder = { Text("e.g. AB7K9X", color = Color.White.copy(alpha = 0.35f), fontFamily = FontFamily.Monospace) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = GlassTheme.VioletGlow,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                cursorColor = GlassTheme.VioletGlow,
            ),
        )
        Spacer(Modifier.height(14.dp))

        GlowButton(
            text = "Join Partner",
            onClick = {
                joining = true
                vm.joinCouple(joinCode, name, colorIndex) { joining = false }
            },
            enabled = joinCode.length == 6 && !joining,
            loading = joining,
            gradient = GlassTheme.VioletGradient,
            icon = Icons.Rounded.Link,
        )
    }

    error?.let {
        Spacer(Modifier.height(14.dp))
        Text(
            it,
            color = Color(0xFFFF6B6B),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(24.dp))
    FrostedButton(
        text = "Edit name or color",
        onClick = onEditProfile,
        icon = Icons.Rounded.Edit,
    )
}
