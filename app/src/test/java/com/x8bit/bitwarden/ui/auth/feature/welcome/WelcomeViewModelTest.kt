package com.x8bit.bitwarden.ui.auth.feature.welcome

import app.cash.turbine.test
import com.x8bit.bitwarden.ui.platform.base.BaseViewModelTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WelcomeViewModelTest : BaseViewModelTest() {
    @Test
    fun `initial state should be correct`() = runTest {
        val viewModel = WelcomeViewModel()

        viewModel.stateFlow.test {
            assertEquals(
                DEFAULT_STATE,
                awaitItem(),
            )
        }
    }

    @Test
    fun `PagerSwipe should update state`() = runTest {
        val viewModel = WelcomeViewModel()
        val newIndex = 2

        viewModel.trySendAction(WelcomeAction.PagerSwipe(index = newIndex))

        viewModel.stateFlow.test {
            assertEquals(
                DEFAULT_STATE.copy(index = newIndex),
                awaitItem(),
            )
        }
    }

    @Test
    fun `DotClick should update state and emit UpdatePager`() = runTest {
        val viewModel = WelcomeViewModel()
        val newIndex = 2

        viewModel.trySendAction(WelcomeAction.DotClick(index = newIndex))

        viewModel.stateFlow.test {
            assertEquals(
                DEFAULT_STATE.copy(index = newIndex),
                awaitItem(),
            )
        }
        viewModel.eventFlow.test {
            assertEquals(
                WelcomeEvent.UpdatePager(index = newIndex),
                awaitItem(),
            )
        }
    }

    @Test
    fun `CreateAccountClick should emit NavigateToCreateAccount`() = runTest {
        val viewModel = WelcomeViewModel()

        viewModel.trySendAction(WelcomeAction.CreateAccountClick)

        viewModel.eventFlow.test {
            assertEquals(
                WelcomeEvent.NavigateToCreateAccount,
                awaitItem(),
            )
        }
    }

    @Test
    fun `LoginClick should emit NavigateToLogin`() = runTest {
        val viewModel = WelcomeViewModel()

        viewModel.trySendAction(WelcomeAction.LoginClick)

        viewModel.eventFlow.test {
            assertEquals(
                WelcomeEvent.NavigateToLogin,
                awaitItem(),
            )
        }
    }
}

private val DEFAULT_STATE = WelcomeState(
    index = 0,
    pages = listOf(
        WelcomeState.WelcomeCard.CardOne,
        WelcomeState.WelcomeCard.CardTwo,
        WelcomeState.WelcomeCard.CardThree,
        WelcomeState.WelcomeCard.CardFour,
    ),
)
