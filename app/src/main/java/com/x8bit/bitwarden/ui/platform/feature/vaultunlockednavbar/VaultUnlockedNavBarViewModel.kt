package com.x8bit.bitwarden.ui.platform.feature.vaultunlockednavbar

import androidx.annotation.StringRes
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.platform.manager.SpecialCircumstanceManager
import com.x8bit.bitwarden.data.platform.manager.model.SpecialCircumstance
import com.x8bit.bitwarden.ui.platform.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Manages bottom tab navigation of the application.
 */
@HiltViewModel
class VaultUnlockedNavBarViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    specialCircumstancesManager: SpecialCircumstanceManager,
) : BaseViewModel<VaultUnlockedNavBarState, VaultUnlockedNavBarEvent, VaultUnlockedNavBarAction>(
    initialState = run {
        val hasOrganization = authRepository
            .userStateFlow
            .value
            ?.activeAccount
            ?.organizations
            ?.isNotEmpty()
            ?: false
        val vaultRes = if (hasOrganization) R.string.vaults else R.string.my_vault
        VaultUnlockedNavBarState(
            vaultNavBarLabelRes = vaultRes,
            vaultNavBarContentDescriptionRes = vaultRes,
        )
    },
) {
    init {
        when (specialCircumstancesManager.specialCircumstance) {
            SpecialCircumstance.GeneratorShortcut -> {
                sendEvent(VaultUnlockedNavBarEvent.NavigateToGeneratorScreen)
                specialCircumstancesManager.specialCircumstance = null
            }

            SpecialCircumstance.VaultShortcut -> {
                sendEvent(VaultUnlockedNavBarEvent.NavigateToVaultScreen)
                specialCircumstancesManager.specialCircumstance = null
            }

            else -> Unit
        }
    }

    override fun handleAction(action: VaultUnlockedNavBarAction) {
        when (action) {
            VaultUnlockedNavBarAction.GeneratorTabClick -> handleGeneratorTabClicked()
            VaultUnlockedNavBarAction.SendTabClick -> handleSendTabClicked()
            VaultUnlockedNavBarAction.SettingsTabClick -> handleSettingsTabClicked()
            VaultUnlockedNavBarAction.VaultTabClick -> handleVaultTabClicked()
            VaultUnlockedNavBarAction.BackStackUpdate -> handleBackStackUpdate()
        }
    }
    // #region BottomTabViewModel Action Handlers
    /**
     * Attempts to send [VaultUnlockedNavBarEvent.NavigateToGeneratorScreen] event
     */
    private fun handleGeneratorTabClicked() {
        sendEvent(VaultUnlockedNavBarEvent.NavigateToGeneratorScreen)
    }

    /**
     * Attempts to send [VaultUnlockedNavBarEvent.NavigateToSendScreen] event
     */
    private fun handleSendTabClicked() {
        sendEvent(VaultUnlockedNavBarEvent.NavigateToSendScreen)
    }

    /**
     * Attempts to send [VaultUnlockedNavBarEvent.NavigateToVaultScreen] event
     */
    private fun handleVaultTabClicked() {
        sendEvent(VaultUnlockedNavBarEvent.NavigateToVaultScreen)
    }

    /**
     * Attempts to send [VaultUnlockedNavBarEvent.NavigateToSettingsScreen] event
     */
    private fun handleSettingsTabClicked() {
        sendEvent(VaultUnlockedNavBarEvent.NavigateToSettingsScreen)
    }

    private fun handleBackStackUpdate() {
        authRepository.updateLastActiveTime()
    }
    // #endregion BottomTabViewModel Action Handlers
}

/**
 * Models state for the [VaultUnlockedNavBarViewModel].
 */
data class VaultUnlockedNavBarState(
    @StringRes val vaultNavBarLabelRes: Int,
    @StringRes val vaultNavBarContentDescriptionRes: Int,
)

/**
 * Models actions for the bottom tab of the vault unlocked portion of the app.
 */
sealed class VaultUnlockedNavBarAction {
    /**
     * click Generator tab.
     */
    data object GeneratorTabClick : VaultUnlockedNavBarAction()

    /**
     * click Send tab.
     */
    data object SendTabClick : VaultUnlockedNavBarAction()

    /**
     * click Vault tab.
     */
    data object VaultTabClick : VaultUnlockedNavBarAction()

    /**
     * click Settings tab.
     */
    data object SettingsTabClick : VaultUnlockedNavBarAction()

    /**
     * Indicates the backstack has changed.
     */
    data object BackStackUpdate : VaultUnlockedNavBarAction()
}

/**
 * Models events for the bottom tab of the vault unlocked portion of the app.
 */
sealed class VaultUnlockedNavBarEvent {
    /**
     * Navigate to the Generator screen.
     */
    data object NavigateToGeneratorScreen : VaultUnlockedNavBarEvent()

    /**
     * Navigate to the Send screen.
     */
    data object NavigateToSendScreen : VaultUnlockedNavBarEvent()

    /**
     * Navigate to the Vault screen.
     */
    data object NavigateToVaultScreen : VaultUnlockedNavBarEvent()

    /**
     * Navigate to the Settings screen.
     */
    data object NavigateToSettingsScreen : VaultUnlockedNavBarEvent()
}
