package com.x8bit.bitwarden.ui.platform.feature.rootnav

import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.UserState
import com.x8bit.bitwarden.data.platform.manager.SpecialCircumstanceManagerImpl
import com.x8bit.bitwarden.data.platform.manager.model.SpecialCircumstance
import com.x8bit.bitwarden.data.platform.repository.model.Environment
import com.x8bit.bitwarden.ui.platform.base.BaseViewModelTest
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootNavViewModelTest : BaseViewModelTest() {
    private val mutableUserStateFlow = MutableStateFlow<UserState?>(null)
    private val authRepository = mockk<AuthRepository>() {
        every { userStateFlow } returns mutableUserStateFlow
        every { updateLastActiveTime() } just runs
    }
    private val specialCircumstanceManager = SpecialCircumstanceManagerImpl()

    @Test
    fun `when there are no accounts the nav state should be Auth`() {
        mutableUserStateFlow.tryEmit(null)
        val viewModel = createViewModel()
        assertEquals(RootNavState.Auth, viewModel.stateFlow.value)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `when the active user is not logged in the nav state should be Auth`() {
        mutableUserStateFlow.tryEmit(
            UserState(
                activeUserId = "activeUserId",
                accounts = listOf(
                    UserState.Account(
                        userId = "activeUserId",
                        name = "name",
                        email = "email",
                        avatarColorHex = "avatarColorHex",
                        environment = Environment.Us,
                        isPremium = true,
                        isLoggedIn = false,
                        isVaultUnlocked = true,
                        organizations = emptyList(),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()
        assertEquals(RootNavState.Auth, viewModel.stateFlow.value)
    }

    @Suppress("MaxLineLength")
    @Test
    fun `when the active user but there are pending account additions the nav state should be Auth`() {
        mutableUserStateFlow.tryEmit(
            UserState(
                activeUserId = "activeUserId",
                accounts = listOf(
                    UserState.Account(
                        userId = "activeUserId",
                        name = "name",
                        email = "email",
                        avatarColorHex = "avatarColorHex",
                        environment = Environment.Us,
                        isPremium = true,
                        isLoggedIn = true,
                        isVaultUnlocked = true,
                        organizations = emptyList(),
                    ),
                ),
                hasPendingAccountAddition = true,
            ),
        )
        val viewModel = createViewModel()
        assertEquals(RootNavState.Auth, viewModel.stateFlow.value)
    }

    @Test
    fun `when the active user has an unlocked vault the nav state should be VaultUnlocked`() {
        mutableUserStateFlow.tryEmit(
            UserState(
                activeUserId = "activeUserId",
                accounts = listOf(
                    UserState.Account(
                        userId = "activeUserId",
                        name = "name",
                        email = "email",
                        avatarColorHex = "avatarColorHex",
                        environment = Environment.Us,
                        isPremium = true,
                        isLoggedIn = true,
                        isVaultUnlocked = true,
                        organizations = emptyList(),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()
        assertEquals(
            RootNavState.VaultUnlocked(activeUserId = "activeUserId"),
            viewModel.stateFlow.value,
        )
    }

    @Suppress("MaxLineLength")
    @Test
    fun `when the active user has an unlocked vault but the is a ShareNewSend special circumstance the nav state should be VaultUnlockedForNewSend`() {
        specialCircumstanceManager.specialCircumstance =
            SpecialCircumstance.ShareNewSend(
                data = mockk(),
                shouldFinishWhenComplete = true,
            )
        mutableUserStateFlow.tryEmit(
            UserState(
                activeUserId = "activeUserId",
                accounts = listOf(
                    UserState.Account(
                        userId = "activeUserId",
                        name = "name",
                        email = "email",
                        avatarColorHex = "avatarColorHex",
                        environment = Environment.Us,
                        isPremium = true,
                        isLoggedIn = true,
                        isVaultUnlocked = true,
                        organizations = emptyList(),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()
        assertEquals(
            RootNavState.VaultUnlockedForNewSend,
            viewModel.stateFlow.value,
        )
    }

    @Test
    fun `when the active user has a locked vault the nav state should be VaultLocked`() {
        mutableUserStateFlow.tryEmit(
            UserState(
                activeUserId = "activeUserId",
                accounts = listOf(
                    UserState.Account(
                        userId = "activeUserId",
                        name = "name",
                        email = "email",
                        avatarColorHex = "avatarColorHex",
                        environment = Environment.Us,
                        isPremium = true,
                        isLoggedIn = true,
                        isVaultUnlocked = false,
                        organizations = emptyList(),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()
        assertEquals(RootNavState.VaultLocked, viewModel.stateFlow.value)
    }

    @Test
    fun `BackStackUpdate should call updateLastActiveTime`() {
        val viewModel = createViewModel()
        viewModel.trySendAction(RootNavAction.BackStackUpdate)
        verify { authRepository.updateLastActiveTime() }
    }

    private fun createViewModel(): RootNavViewModel =
        RootNavViewModel(
            authRepository = authRepository,
            specialCircumstanceManager = specialCircumstanceManager,
        )
}