package com.closer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.closer.app.ui.AppViewModel
import com.closer.app.ui.HomeScreen
import com.closer.app.ui.OnboardingScreen
import com.closer.app.ui.theme.CloserTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CloserTheme {
                CloserRoot()
            }
        }
    }
}

@Composable
private fun CloserRoot(vm: AppViewModel = viewModel()) {
    val onboarded by vm.onboarded.collectAsState()
    AnimatedContent(
        targetState = onboarded,
        transitionSpec = {
            if (targetState) {
                // Onboarding → Home: slide in from right + fade
                (slideInHorizontally { it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                // Home → Onboarding (e.g. unpair): slide in from left + fade
                (slideInHorizontally { -it / 3 } + fadeIn())
                    .togetherWith(slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "root-transition",
    ) { isOnboarded ->
        if (isOnboarded) HomeScreen(vm) else OnboardingScreen(vm)
    }
}
