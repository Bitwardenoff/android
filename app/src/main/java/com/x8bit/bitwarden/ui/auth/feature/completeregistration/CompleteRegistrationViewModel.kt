package com.x8bit.bitwarden.ui.auth.feature.completeregistration

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.data.auth.datasource.sdk.model.PasswordStrength
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.PasswordStrengthResult
import com.x8bit.bitwarden.data.auth.repository.model.RegisterResult
import com.x8bit.bitwarden.data.platform.manager.SpecialCircumstanceManager
import com.x8bit.bitwarden.data.platform.repository.EnvironmentRepository
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.CheckDataBreachesToggle
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.CloseClick
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.ConfirmPasswordInputChange
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.ContinueWithBreachedPasswordClick
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.CreateAccountClick
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.ErrorDialogDismiss
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.Internal
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.Internal.ReceivePasswordStrengthResult
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.PasswordHintChange
import com.x8bit.bitwarden.ui.auth.feature.completeregistration.CompleteRegistrationAction.PasswordInputChange
import com.x8bit.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.ui.platform.base.util.Text
import com.x8bit.bitwarden.ui.platform.base.util.asText
import com.x8bit.bitwarden.ui.platform.base.util.concat
import com.x8bit.bitwarden.ui.platform.base.util.isValidEmail
import com.x8bit.bitwarden.ui.platform.components.dialog.BasicDialogState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.jetbrains.annotations.VisibleForTesting
import javax.inject.Inject

private const val KEY_STATE = "state"
private const val MIN_PASSWORD_LENGTH = 12

/**
 * Models logic for the create account screen.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class CompleteRegistrationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val environmentRepository: EnvironmentRepository,
    private val specialCircumstanceManager: SpecialCircumstanceManager,
) : BaseViewModel<CompleteRegistrationState, CompleteRegistrationEvent, CompleteRegistrationAction>(
    initialState = savedStateHandle[KEY_STATE]
        ?: run {
            val args = CompleteRegistrationArgs(savedStateHandle)
            CompleteRegistrationState(
                userEmail = args.emailAddress,
                emailVerificationToken = args.verificationToken,
                fromEmail = args.fromEmail,
                passwordInput = "",
                confirmPasswordInput = "",
                passwordHintInput = "",
                isCheckDataBreachesToggled = true,
                dialog = null,
                passwordStrengthState = PasswordStrengthState.NONE,
            )
        },
) {

    /**
     * Keeps track of async request to get password strength. Should be cancelled
     * when user input changes.
     */
    private var passwordStrengthJob: Job = Job().apply { complete() }

    init {
        verifyEmailAddress()
        // As state updates, write to saved state handle:
        stateFlow
            .onEach { savedStateHandle[KEY_STATE] = it }
            .launchIn(viewModelScope)
    }

    @VisibleForTesting
    public override fun onCleared() {
        // clean the specialCircumstance after being handled
        specialCircumstanceManager.specialCircumstance = null
        super.onCleared()
    }

    override fun handleAction(action: CompleteRegistrationAction) {
        when (action) {
            is CreateAccountClick -> handleCreateAccountClick()
            is ConfirmPasswordInputChange -> handleConfirmPasswordInputChanged(action)
            is PasswordHintChange -> handlePasswordHintChanged(action)
            is PasswordInputChange -> handlePasswordInputChanged(action)
            is CloseClick -> handleCloseClick()
            is ErrorDialogDismiss -> handleDialogDismiss()
            is CheckDataBreachesToggle -> handleCheckDataBreachesToggle(action)
            is Internal.ReceiveRegisterResult -> {
                handleReceiveRegisterAccountResult(action)
            }

            ContinueWithBreachedPasswordClick -> handleContinueWithBreachedPasswordClick()
            is ReceivePasswordStrengthResult -> handlePasswordStrengthResult(action)
        }
    }

    private fun verifyEmailAddress() {
        if (!state.fromEmail) {
            return
        }

        viewModelScope.launch {
            sendEvent(
                CompleteRegistrationEvent.ShowToast(
                    message = R.string.email_verified.asText(),
                ),
            )
        }
    }

    private fun handlePasswordStrengthResult(action: ReceivePasswordStrengthResult) {
        when (val result = action.result) {
            is PasswordStrengthResult.Success -> {
                val updatedState = when (result.passwordStrength) {
                    PasswordStrength.LEVEL_0 -> PasswordStrengthState.WEAK_1
                    PasswordStrength.LEVEL_1 -> PasswordStrengthState.WEAK_2
                    PasswordStrength.LEVEL_2 -> PasswordStrengthState.WEAK_3
                    PasswordStrength.LEVEL_3 -> PasswordStrengthState.GOOD
                    PasswordStrength.LEVEL_4 -> PasswordStrengthState.STRONG
                }
                mutableStateFlow.update { oldState ->
                    oldState.copy(
                        passwordStrengthState = updatedState,
                    )
                }
            }

            PasswordStrengthResult.Error -> Unit
        }
    }

    @Suppress("LongMethod", "MaxLineLength")
    private fun handleReceiveRegisterAccountResult(
        action: Internal.ReceiveRegisterResult,
    ) {
        when (val registerAccountResult = action.registerResult) {
            // TODO PM-6675: Remove captcha from RegisterResult when old flow gets removed
            is RegisterResult.CaptchaRequired -> {
                throw IllegalStateException(
                    "Captcha should not be required for the new registration flow",
                )
            }

            is RegisterResult.Error -> {
                mutableStateFlow.update {
                    it.copy(
                        dialog = CompleteRegistrationDialog.Error(
                            BasicDialogState.Shown(
                                title = R.string.an_error_has_occurred.asText(),
                                message = registerAccountResult.errorMessage?.asText()
                                    ?: R.string.generic_error_message.asText(),
                            ),
                        ),
                    )
                }
            }

            is RegisterResult.Success -> {
                mutableStateFlow.update { it.copy(dialog = null) }
                sendEvent(
                    CompleteRegistrationEvent.NavigateToLanding,
                )
            }

            RegisterResult.DataBreachFound -> {
                mutableStateFlow.update {
                    it.copy(
                        dialog = CompleteRegistrationDialog.HaveIBeenPwned(
                            title = R.string.exposed_master_password.asText(),
                            message = R.string.password_found_in_a_data_breach_alert_description.asText(),
                        ),
                    )
                }
            }

            RegisterResult.DataBreachAndWeakPassword -> {
                mutableStateFlow.update {
                    it.copy(
                        dialog = CompleteRegistrationDialog.HaveIBeenPwned(
                            title = R.string.weak_and_exposed_master_password.asText(),
                            message = R.string.weak_password_identified_and_found_in_a_data_breach_alert_description.asText(),
                        ),
                    )
                }
            }

            RegisterResult.WeakPassword -> {
                mutableStateFlow.update {
                    it.copy(
                        dialog = CompleteRegistrationDialog.HaveIBeenPwned(
                            title = R.string.weak_master_password.asText(),
                            message = R.string.weak_password_identified_use_a_strong_password_to_protect_your_account.asText(),
                        ),
                    )
                }
            }
        }
    }

    private fun handleCheckDataBreachesToggle(action: CheckDataBreachesToggle) {
        mutableStateFlow.update {
            it.copy(isCheckDataBreachesToggled = action.newState)
        }
    }

    private fun handleDialogDismiss() {
        mutableStateFlow.update {
            it.copy(dialog = null)
        }
    }

    private fun handleCloseClick() {
        sendEvent(CompleteRegistrationEvent.NavigateBack)
    }

    private fun handlePasswordHintChanged(action: PasswordHintChange) {
        mutableStateFlow.update { it.copy(passwordHintInput = action.input) }
    }

    private fun handlePasswordInputChanged(action: PasswordInputChange) {
        // Update input:
        mutableStateFlow.update { it.copy(passwordInput = action.input) }
        // Update password strength:
        passwordStrengthJob.cancel()
        if (action.input.isEmpty()) {
            mutableStateFlow.update {
                it.copy(passwordStrengthState = PasswordStrengthState.NONE)
            }
        } else {
            passwordStrengthJob = viewModelScope.launch {
                val result = authRepository.getPasswordStrength(
                    email = state.userEmail,
                    password = action.input,
                )
                trySendAction(ReceivePasswordStrengthResult(result))
            }
        }
    }

    private fun handleConfirmPasswordInputChanged(action: ConfirmPasswordInputChange) {
        mutableStateFlow.update { it.copy(confirmPasswordInput = action.input) }
    }

    private fun handleCreateAccountClick() = when {
        state.userEmail.isBlank() -> {
            val dialog = BasicDialogState.Shown(
                title = R.string.an_error_has_occurred.asText(),
                message = R.string.validation_field_required
                    .asText(R.string.email_address.asText()),
            )
            mutableStateFlow.update { it.copy(dialog = CompleteRegistrationDialog.Error(dialog)) }
        }

        !state.userEmail.isValidEmail() -> {
            val dialog = BasicDialogState.Shown(
                title = R.string.an_error_has_occurred.asText(),
                message = R.string.invalid_email.asText(),
            )
            mutableStateFlow.update { it.copy(dialog = CompleteRegistrationDialog.Error(dialog)) }
        }

        state.passwordInput.length < MIN_PASSWORD_LENGTH -> {
            val dialog = BasicDialogState.Shown(
                title = R.string.an_error_has_occurred.asText(),
                message = R.string.master_password_length_val_message_x.asText(MIN_PASSWORD_LENGTH),
            )
            mutableStateFlow.update { it.copy(dialog = CompleteRegistrationDialog.Error(dialog)) }
        }

        state.passwordInput != state.confirmPasswordInput -> {
            val dialog = BasicDialogState.Shown(
                title = R.string.an_error_has_occurred.asText(),
                message = R.string.master_password_confirmation_val_message.asText(),
            )
            mutableStateFlow.update { it.copy(dialog = CompleteRegistrationDialog.Error(dialog)) }
        }

        else -> {
            submitRegisterAccountRequest(
                shouldCheckForDataBreaches = state.isCheckDataBreachesToggled,
                shouldIgnorePasswordStrength = false,
            )
        }
    }

    private fun handleContinueWithBreachedPasswordClick() {
        submitRegisterAccountRequest(
            shouldCheckForDataBreaches = false,
            shouldIgnorePasswordStrength = true,
        )
    }

    private fun submitRegisterAccountRequest(
        shouldCheckForDataBreaches: Boolean,
        shouldIgnorePasswordStrength: Boolean,
    ) {
        mutableStateFlow.update {
            it.copy(dialog = CompleteRegistrationDialog.Loading)
        }
        viewModelScope.launch {
            // Update region accordingly to a user email
            environmentRepository.loadEnvironmentForEmail(state.userEmail)
            val result = authRepository.register(
                shouldCheckDataBreaches = shouldCheckForDataBreaches,
                isMasterPasswordStrong = shouldIgnorePasswordStrength ||
                    state.isMasterPasswordStrong,
                emailVerificationToken = state.emailVerificationToken,
                email = state.userEmail,
                masterPassword = state.passwordInput,
                masterPasswordHint = state.passwordHintInput.ifBlank { null },
                captchaToken = null,
            )
            sendAction(
                Internal.ReceiveRegisterResult(
                    registerResult = result,
                ),
            )
        }
    }
}

/**
 * UI state for the complete registration screen.
 */
@Parcelize
data class CompleteRegistrationState(
    val userEmail: String,
    val emailVerificationToken: String,
    val fromEmail: Boolean,
    val passwordInput: String,
    val confirmPasswordInput: String,
    val passwordHintInput: String,
    val isCheckDataBreachesToggled: Boolean,
    val dialog: CompleteRegistrationDialog?,
    val passwordStrengthState: PasswordStrengthState,
) : Parcelable {

    val passwordLengthLabel: Text
        // Have to concat a few strings here, resulting string is:
        // Important: Your master password cannot be recovered if you forget it! 12
        // characters minimum
        @Suppress("MaxLineLength")
        get() = R.string.important.asText()
            .concat(
                ": ".asText(),
                R.string.your_master_password_cannot_be_recovered_if_you_forget_it_x_characters_minimum
                    .asText(MIN_PASSWORD_LENGTH),
            )

    /**
     * Whether or not the provided master password is considered strong.
     */
    val isMasterPasswordStrong: Boolean
        get() = when (passwordStrengthState) {
            PasswordStrengthState.NONE,
            PasswordStrengthState.WEAK_1,
            PasswordStrengthState.WEAK_2,
            PasswordStrengthState.WEAK_3,
            -> false

            PasswordStrengthState.GOOD,
            PasswordStrengthState.STRONG,
            -> true
        }
}

/**
 * Models dialogs that can be displayed on the complete registration screen.
 */
sealed class CompleteRegistrationDialog : Parcelable {
    /**
     * Loading dialog.
     */
    @Parcelize
    data object Loading : CompleteRegistrationDialog()

    /**
     * Confirm the user wants to continue with potentially breached password.
     *
     * @param title The title for the HaveIBeenPwned dialog.
     * @param message The message for the HaveIBeenPwned dialog.
     */
    @Parcelize
    data class HaveIBeenPwned(
        val title: Text,
        val message: Text,
    ) : CompleteRegistrationDialog()

    /**
     * General error dialog with an OK button.
     */
    @Parcelize
    data class Error(val state: BasicDialogState.Shown) : CompleteRegistrationDialog()
}

/**
 * Models events for the complete registration screen.
 */
sealed class CompleteRegistrationEvent {

    /**
     * Navigate back to previous screen.
     */
    data object NavigateBack : CompleteRegistrationEvent()

    /**
     * Show a toast with the given message.
     */
    data class ShowToast(
        val message: Text,
    ) : CompleteRegistrationEvent()

    /**
     * Navigates to the landing screen.
     */
    data object NavigateToLanding : CompleteRegistrationEvent()
}

/**
 * Models actions for the complete registration screen.
 */
sealed class CompleteRegistrationAction {
    /**
     * User clicked create account.
     */
    data object CreateAccountClick : CompleteRegistrationAction()

    /**
     * User clicked close.
     */
    data object CloseClick : CompleteRegistrationAction()

    /**
     * User clicked "Yes" when being asked if they are sure they want to use a breached password.
     */
    data object ContinueWithBreachedPasswordClick : CompleteRegistrationAction()

    /**
     * Password input changed.
     */
    data class PasswordInputChange(val input: String) : CompleteRegistrationAction()

    /**
     * Confirm password input changed.
     */
    data class ConfirmPasswordInputChange(val input: String) : CompleteRegistrationAction()

    /**
     * Password hint input changed.
     */
    data class PasswordHintChange(val input: String) : CompleteRegistrationAction()

    /**
     * User dismissed the error dialog.
     */
    data object ErrorDialogDismiss : CompleteRegistrationAction()

    /**
     * User tapped check data breaches toggle.
     */
    data class CheckDataBreachesToggle(val newState: Boolean) : CompleteRegistrationAction()

    /**
     * Models actions that the [CompleteRegistrationViewModel] itself might send.
     */
    sealed class Internal : CompleteRegistrationAction() {
        /**
         * Indicates a [RegisterResult] has been received.
         */
        data class ReceiveRegisterResult(
            val registerResult: RegisterResult,
        ) : Internal()

        /**
         * Indicates a password strength result has been received.
         */
        data class ReceivePasswordStrengthResult(
            val result: PasswordStrengthResult,
        ) : Internal()
    }
}
