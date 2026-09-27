package io.github.degipe.youtubewhitelist.ui.screen.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.degipe.youtubewhitelist.core.data.repository.PinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PinSetupStep { ENTER_NEW, CONFIRM }

data class PinSetupUiState(
    val step: PinSetupStep = PinSetupStep.ENTER_NEW,
    val pin: String = "",
    val error: String? = null,
    val isComplete: Boolean = false
)

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val pinRepository: PinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinSetupUiState())
    val uiState: StateFlow<PinSetupUiState> = _uiState.asStateFlow()

    private var firstPin: String = ""

    fun onPasswordChanged(value: String) {
        _uiState.update { state ->
            if (value.length <= MAX_PIN_LENGTH) {
                state.copy(pin = value, error = null)
            } else {
                state
            }
        }
    }

    fun onSubmit() {
        val state = _uiState.value
        when (state.step) {
            PinSetupStep.ENTER_NEW -> {
                if (state.pin.length < MIN_PIN_LENGTH) {
                    _uiState.update { it.copy(error = "Password must be at least $MIN_PIN_LENGTH characters") }
                    return
                }
                firstPin = state.pin
                _uiState.update { it.copy(step = PinSetupStep.CONFIRM, pin = "", error = null) }
            }
            PinSetupStep.CONFIRM -> {
                if (state.pin != firstPin) {
                    firstPin = ""
                    _uiState.update {
                        it.copy(
                            step = PinSetupStep.ENTER_NEW,
                            pin = "",
                            error = "Passwords do not match. Try again."
                        )
                    }
                    return
                }
                viewModelScope.launch {
                    pinRepository.setupPin(state.pin)
                    _uiState.update { it.copy(isComplete = true) }
                }
            }
        }
    }

    companion object {
        const val MIN_PIN_LENGTH = 6
        const val MAX_PIN_LENGTH = 64
    }
}