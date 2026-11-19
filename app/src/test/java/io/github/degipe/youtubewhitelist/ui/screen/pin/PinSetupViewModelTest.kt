package io.github.degipe.youtubewhitelist.ui.screen.pin

import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.data.repository.PinRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinSetupViewModelTest {

    private lateinit var pinRepository: PinRepository
    private lateinit var viewModel: PinSetupViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinRepository = mockk(relaxed = true)
        viewModel = PinSetupViewModel(pinRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is EnterNew`() {
        val state = viewModel.uiState.value
        assertThat(state.step).isEqualTo(PinSetupStep.ENTER_NEW)
        assertThat(state.pin).isEmpty()
        assertThat(state.error).isNull()
    }

    @Test
    fun `editing the password replaces the prior value`() {
        viewModel.onPasswordChanged("ab")
        viewModel.onPasswordChanged("ab3")
        assertThat(viewModel.uiState.value.pin).isEqualTo("ab3")
    }

    @Test
    fun `deleting a character updates the password`() {
        viewModel.onPasswordChanged("12")
        viewModel.onPasswordChanged("1")

        assertThat(viewModel.uiState.value.pin).isEqualTo("1")
    }

    @Test
    fun `empty input clears the password`() {
        viewModel.onPasswordChanged("12")
        viewModel.onPasswordChanged("")
        assertThat(viewModel.uiState.value.pin).isEmpty()
    }

    @Test
    fun `password input longer than 64 characters is rejected`() {
        val accepted = "a".repeat(64)
        viewModel.onPasswordChanged(accepted)
        viewModel.onPasswordChanged("${accepted}b")
        assertThat(viewModel.uiState.value.pin).isEqualTo(accepted)
    }

    @Test
    fun `submit with less than six characters shows error`() {
        viewModel.onPasswordChanged("ab123")
        viewModel.onSubmit()

        assertThat(viewModel.uiState.value.error).isNotNull()
        assertThat(viewModel.uiState.value.step).isEqualTo(PinSetupStep.ENTER_NEW)
    }

    @Test
    fun `submit with six characters moves to confirm step`() {
        viewModel.onPasswordChanged("old123")
        viewModel.onSubmit()

        assertThat(viewModel.uiState.value.step).isEqualTo(PinSetupStep.CONFIRM)
        assertThat(viewModel.uiState.value.pin).isEmpty()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `confirm with matching pin calls setupPin`() = runTest(testDispatcher) {
        coEvery { pinRepository.setupPin("old123") } returns Unit

        viewModel.onPasswordChanged("old123")
        viewModel.onSubmit() // move to confirm
        viewModel.onPasswordChanged("old123")
        viewModel.onSubmit() // confirm

        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { pinRepository.setupPin("old123") }
        assertThat(viewModel.uiState.value.isComplete).isTrue()
    }

    @Test
    fun `confirm with non-matching pin shows error`() {
        viewModel.onPasswordChanged("old123")
        viewModel.onSubmit() // move to confirm
        viewModel.onPasswordChanged("new456")
        viewModel.onSubmit() // mismatched confirm

        assertThat(viewModel.uiState.value.error).isNotNull()
        assertThat(viewModel.uiState.value.step).isEqualTo(PinSetupStep.ENTER_NEW)
    }
}
