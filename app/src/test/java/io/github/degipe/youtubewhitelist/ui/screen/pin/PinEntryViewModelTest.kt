package io.github.degipe.youtubewhitelist.ui.screen.pin

import com.google.common.truth.Truth.assertThat
import io.github.degipe.youtubewhitelist.core.data.model.PinVerificationResult
import io.github.degipe.youtubewhitelist.core.data.repository.PinRepository
import io.mockk.coEvery
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
class PinEntryViewModelTest {

    private lateinit var pinRepository: PinRepository
    private lateinit var viewModel: PinEntryViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pinRepository = mockk(relaxed = true)
        viewModel = PinEntryViewModel(pinRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() {
        val state = viewModel.uiState.value
        assertThat(state.pin).isEmpty()
        assertThat(state.error).isNull()
        assertThat(state.isVerified).isFalse()
        assertThat(state.isLockedOut).isFalse()
    }

    @Test
    fun `correct pin sets isVerified`() = runTest(testDispatcher) {
        coEvery { pinRepository.verifyPin("old123") } returns PinVerificationResult.Success

        viewModel.onPasswordChanged("old123")
        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.isVerified).isTrue()
    }

    @Test
    fun `incorrect pin shows error with attempts remaining`() = runTest(testDispatcher) {
        coEvery { pinRepository.verifyPin("wrong789") } returns PinVerificationResult.Failure(4)

        viewModel.onPasswordChanged("wrong789")
        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isVerified).isFalse()
        assertThat(state.error).isNotNull()
        assertThat(state.attemptsRemaining).isEqualTo(4)
        assertThat(state.pin).isEmpty()
    }

    @Test
    fun `locked out shows lockout state`() = runTest(testDispatcher) {
        coEvery { pinRepository.verifyPin("old123") } returns PinVerificationResult.LockedOut(30)

        viewModel.onPasswordChanged("old123")
        viewModel.onSubmit()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLockedOut).isTrue()
        assertThat(state.lockoutSeconds).isEqualTo(30)
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
}
