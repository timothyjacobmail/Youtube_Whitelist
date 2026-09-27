package io.github.degipe.youtubewhitelist.ui.screen.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SplashScreen(
    onFirstRun: () -> Unit,
    onReturningUser: (profileId: String) -> Unit,
    onMultipleProfiles: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            SplashUiState.FirstRun -> onFirstRun()
            is SplashUiState.ReturningUser -> onReturningUser(state.profileId)
            SplashUiState.MultipleProfiles -> onMultipleProfiles()
            is SplashUiState.Loading -> { /* wait */ }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()

        val state = uiState
        if (state is SplashUiState.Loading) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Please wait ${formatTime(state.secondsRemaining)} before the app opens",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "%d:%02d".format(minutes, seconds)
    } else {
        "$seconds seconds"
    }
}