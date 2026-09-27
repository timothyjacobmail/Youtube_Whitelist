package io.github.degipe.youtubewhitelist.ui.screen.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.data.repository.KidProfileRepository
import io.github.degipe.youtubewhitelist.core.data.repository.ParentAccountRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

sealed interface SplashUiState {
    data class Loading(val secondsRemaining: Int) : SplashUiState
    data object FirstRun : SplashUiState
    data class ReturningUser(val profileId: String) : SplashUiState
    data object MultipleProfiles : SplashUiState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val parentAccountRepository: ParentAccountRepository,
    private val kidProfileRepository: KidProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(
        SplashUiState.Loading(secondsRemaining = APP_OPEN_DELAY_SECONDS)
    )
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkAppState()
    }

    private fun checkAppState() {
        viewModelScope.launch {
            for (secondsLeft in APP_OPEN_DELAY_SECONDS downTo 1) {
                _uiState.value = SplashUiState.Loading(secondsRemaining = secondsLeft)
                delay(1.seconds)
            }

            val hasAccount = parentAccountRepository.hasAccount()
            if (!hasAccount) {
                _uiState.value = SplashUiState.FirstRun
                return@launch
            }

            val account = parentAccountRepository.getAccount().first()
            if (account == null) {
                _uiState.value = SplashUiState.FirstRun
                return@launch
            }

            val profiles = kidProfileRepository.getProfilesByParent(account.id).first()
            if (profiles.isEmpty()) {
                _uiState.value = SplashUiState.FirstRun
                return@launch
            }

            if (profiles.size > 1) {
                _uiState.value = SplashUiState.MultipleProfiles
            } else {
                _uiState.value = SplashUiState.ReturningUser(profileId = profiles.first().id)
            }
        }
    }

    companion object {
        // Adjust this value (in seconds) to change how long the app
        // waits on the splash screen before opening. Currently, 90 seconds.
        private const val APP_OPEN_DELAY_SECONDS = 90
    }
}