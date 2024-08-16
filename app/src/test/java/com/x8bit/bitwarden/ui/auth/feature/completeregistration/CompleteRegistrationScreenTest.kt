package com.x8bit.bitwarden.ui.auth.feature.completeregistration

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.x8bit.bitwarden.data.platform.repository.util.bufferedMutableSharedFlow
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.CheckDataBreachesToggle
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.CloseClick
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.ConfirmPasswordInputChange
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.ContinueWithBreachedPasswordClick
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.CreateAccountClick
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.ErrorDialogDismiss
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.PasswordHintChange
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.PasswordInputChange
import com.x8bit.bitwarden.ui.platform.base.BaseComposeTest
import com.x8bit.bitwarden.ui.platform.base.util.asText
import com.x8bit.bitwarden.ui.platform.components.dialog.BasicDialogState
import com.x8bit.bitwarden.ui.platform.manager.intent.IntentManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompleteRegistrationScreenTest : BaseComposeTest() {

    private var onNavigateBackCalled = false
    private var onNavigateToLandingCalled = false

    private val intentManager = mockk<IntentManager>(relaxed = true) {
        every { startCustomTabsActivity(any()) } just runs
        every { startActivity(any()) } just runs
    }

    private val mutableStateFlow = MutableStateFlow(DEFAULT_STATE)
    private val mutableEventFlow = bufferedMutableSharedFlow<CompleteRegistrationEvent>()
    private val viewModel = mockk<CompleteRegistrationViewModel>(relaxed = true) {
        every { stateFlow } returns mutableStateFlow
        every { eventFlow } returns mutableEventFlow
        every { trySendAction(any()) } just runs
    }

    @Before
    fun setup() {
        composeTestRule.setContent {
            CompleteRegistrationScreen(
                onNavigateBack = { onNavigateBackCalled = true },
                onNavigateToLanding = { onNavigateToLandingCalled = true },
                intentManager = intentManager,
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `app bar submit click should send CreateAccountClick action`() {
        composeTestRule.onNodeWithText("Create account").performClick()
        verify { viewModel.trySendAction(CreateAccountClick) }
    }

    @Test
    fun `close click should send CloseClick action`() {
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        verify { viewModel.trySendAction(CloseClick) }
    }

    @Test
    fun `check data breaches click should send CheckDataBreachesToggle action`() {
        composeTestRule
            .onNodeWithText("Check known data breaches for this password")
            .performScrollTo()
            .performClick()
        verify { viewModel.trySendAction(CheckDataBreachesToggle(false)) }
    }

    @Test
    fun `NavigateBack event should invoke navigate back lambda`() {
        mutableEventFlow.tryEmit(CompleteRegistrationEvent.NavigateBack)
        assertTrue(onNavigateBackCalled)
    }

    @Test
    fun `NavigateToLogin event should invoke navigate login lambda`() {
        mutableEventFlow.tryEmit(CompleteRegistrationEvent.NavigateToLanding)
        assertTrue(onNavigateToLandingCalled)
    }

    @Test
    fun `password input change should send PasswordInputChange action`() {
        composeTestRule.onNodeWithText("Master password").performTextInput(TEST_INPUT)
        verify { viewModel.trySendAction(PasswordInputChange(TEST_INPUT)) }
    }

    @Test
    fun `confirm password input change should send ConfirmPasswordInputChange action`() {
        composeTestRule.onNodeWithText("Re-type master password").performTextInput(TEST_INPUT)
        verify { viewModel.trySendAction(ConfirmPasswordInputChange(TEST_INPUT)) }
    }

    @Test
    fun `password hint input change should send PasswordHintChange action`() {
        composeTestRule
            .onNodeWithText("Master password hint (optional)")
            .performTextInput(TEST_INPUT)
        verify { viewModel.trySendAction(PasswordHintChange(TEST_INPUT)) }
    }

    @Test
    fun `clicking OK on the error dialog should send ErrorDialogDismiss action`() {
        mutableStateFlow.update {
            it.copy(
                dialog = CompleteRegistrationDialog.Error(
                    BasicDialogState.Shown(
                        title = "title".asText(),
                        message = "message".asText(),
                    ),
                ),
            )
        }
        composeTestRule
            .onAllNodesWithText("Ok")
            .filterToOne(hasAnyAncestor(isDialog()))
            .performClick()
        verify { viewModel.trySendAction(ErrorDialogDismiss) }
    }

    @Test
    fun `clicking No on the HIBP dialog should send ErrorDialogDismiss action`() {
        mutableStateFlow.update {
            it.copy(dialog = createHaveIBeenPwned())
        }
        composeTestRule
            .onAllNodesWithText("No")
            .filterToOne(hasAnyAncestor(isDialog()))
            .performClick()
        verify { viewModel.trySendAction(ErrorDialogDismiss) }
    }

    @Test
    fun `clicking Yes on the HIBP dialog should send ContinueWithBreachedPasswordClick action`() {
        mutableStateFlow.update {
            it.copy(dialog = createHaveIBeenPwned())
        }
        composeTestRule
            .onAllNodesWithText("Yes")
            .filterToOne(hasAnyAncestor(isDialog()))
            .performClick()
        verify { viewModel.trySendAction(ContinueWithBreachedPasswordClick) }
    }

    @Test
    fun `when BasicDialogState is Shown should show dialog`() {
        mutableStateFlow.update {
            it.copy(
                dialog = CompleteRegistrationDialog.Error(
                    BasicDialogState.Shown(
                        title = "title".asText(),
                        message = "message".asText(),
                    ),
                ),
            )
        }
        composeTestRule.onNode(isDialog()).assertIsDisplayed()
    }

    @Test
    fun `password strength should change as state changes`() {
        mutableStateFlow.update {
            DEFAULT_STATE.copy(passwordStrengthState = PasswordStrengthState.WEAK_1)
        }
        composeTestRule.onNodeWithText("Weak").assertIsDisplayed()

        mutableStateFlow.update {
            DEFAULT_STATE.copy(passwordStrengthState = PasswordStrengthState.WEAK_2)
        }
        composeTestRule.onNodeWithText("Weak").assertIsDisplayed()

        mutableStateFlow.update {
            DEFAULT_STATE.copy(passwordStrengthState = PasswordStrengthState.WEAK_3)
        }
        composeTestRule.onNodeWithText("Weak").assertIsDisplayed()

        mutableStateFlow.update {
            DEFAULT_STATE.copy(passwordStrengthState = PasswordStrengthState.GOOD)
        }
        composeTestRule.onNodeWithText("Good").assertIsDisplayed()

        mutableStateFlow.update {
            DEFAULT_STATE.copy(passwordStrengthState = PasswordStrengthState.STRONG)
        }
        composeTestRule.onNodeWithText("Strong").assertIsDisplayed()
    }

    @Test
    fun `toggling one password field visibility should toggle the other`() {
        // should start with 2 Show buttons:
        composeTestRule
            .onAllNodesWithContentDescription("Show")
            .assertCountEquals(2)[0]
            .performClick()

        // after clicking there should be no Show buttons:
        composeTestRule
            .onAllNodesWithContentDescription("Show")
            .assertCountEquals(0)

        // and there should be 2 hide buttons now, and we'll click the second one:
        composeTestRule
            .onAllNodesWithContentDescription("Hide")
            .assertCountEquals(2)[1]
            .performClick()

        // then there should be two show buttons again
        composeTestRule
            .onAllNodesWithContentDescription("Show")
            .assertCountEquals(2)
    }

    companion object {
        private const val EMAIL = "test@test.com"
        private const val TOKEN = "token"
        private const val TEST_INPUT = "input"
        private val DEFAULT_STATE = CompleteRegistrationState(
            userEmail = EMAIL,
            emailVerificationToken = TOKEN,
            fromEmail = true,
            passwordInput = "",
            confirmPasswordInput = "",
            passwordHintInput = "",
            isCheckDataBreachesToggled = true,
            dialog = null,
            passwordStrengthState = PasswordStrengthState.NONE,
        )
    }
}
