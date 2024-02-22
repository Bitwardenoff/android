package com.x8bit.bitwarden.ui.platform.feature.settings.exportvault

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.x8bit.bitwarden.data.platform.repository.util.bufferedMutableSharedFlow
import com.x8bit.bitwarden.ui.platform.base.BaseComposeTest
import com.x8bit.bitwarden.ui.platform.base.util.asText
import com.x8bit.bitwarden.ui.platform.feature.settings.exportvault.model.ExportVaultFormat
import com.x8bit.bitwarden.ui.platform.manager.intent.IntentManager
import com.x8bit.bitwarden.ui.util.assertNoDialogExists
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue

class ExportVaultScreenTest : BaseComposeTest() {
    private var onNavigateBackCalled = false
    private val mutableEventFlow = bufferedMutableSharedFlow<ExportVaultEvent>()
    private val mutableStateFlow = MutableStateFlow(DEFAULT_STATE)
    val viewModel = mockk<ExportVaultViewModel>(relaxed = true) {
        every { eventFlow } returns mutableEventFlow
        every { stateFlow } returns mutableStateFlow
    }

    private val intentManager = mockk<IntentManager>(relaxed = true)

    @Before
    fun setUp() {
        composeTestRule.setContent {
            ExportVaultScreen(
                onNavigateBack = { onNavigateBackCalled = true },
                viewModel = viewModel,
                intentManager = intentManager,
            )
        }
    }

    @Test
    fun `NavigateToSelectExportDataLocation should invoke createDocumentIntent`() {
        mutableEventFlow.tryEmit(ExportVaultEvent.NavigateToSelectExportDataLocation("test.json"))

        verify(exactly = 1) {
            intentManager.createDocumentIntent("test.json")
        }
    }

    @Test
    fun `basicDialog should update according to state`() {
        composeTestRule.onNodeWithText("Error message").assertDoesNotExist()

        mutableStateFlow.update {
            it.copy(
                dialogState = ExportVaultState.DialogState.Error(
                    title = null,
                    message = "Error message".asText(),
                ),
            )
        }

        composeTestRule.onNodeWithText("Error message").isDisplayed()
    }

    @Test
    fun `progress dialog should be displayed according to state`() {
        val loadingMessage = "loading..."
        mutableStateFlow.update {
            it.copy(
                dialogState = ExportVaultState.DialogState.Loading(loadingMessage.asText()),
            )
        }
        composeTestRule
            .onNodeWithText(loadingMessage)
            .assert(hasAnyAncestor(isDialog()))
            .assertIsDisplayed()

        mutableStateFlow.update { it.copy(dialogState = null) }
        composeTestRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `close button click should send CloseButtonClick action`() {
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        verify {
            viewModel.trySendAction(ExportVaultAction.CloseButtonClick)
        }
    }

    @Test
    fun `export vault button click should display confirmation dialog`() {
        composeTestRule.onNodeWithText("Confirm vault export").assertDoesNotExist()

        // Click the export vault button shows the alert.
        composeTestRule
            .onAllNodesWithText("Export vault")
            .onFirst()
            .performClick()
        composeTestRule
            .onNodeWithText("Confirm vault export")
            .assert(hasAnyAncestor(isDialog()))
            .assertIsDisplayed()

        // Clicking the cancel button dismisses the alert.
        composeTestRule
            .onNodeWithText("Cancel")
            .assert(hasAnyAncestor(isDialog()))
            .performClick()
        composeTestRule.assertNoDialogExists()
    }

    @Test
    fun `confirm export vault button click should send ConfirmExportClick action`() {
        composeTestRule.onNodeWithText("Confirm vault export").assertDoesNotExist()

        // Click the export vault button shows the alert.
        composeTestRule
            .onAllNodesWithText("Export vault")
            .onFirst()
            .performClick()
        composeTestRule
            .onNodeWithText("Confirm vault export")
            .assert(hasAnyAncestor(isDialog()))
            .assertIsDisplayed()

        // Clicking the confirm button sends the confirm action.
        composeTestRule
            .onAllNodesWithText("Export vault")
            .filterToOne(hasAnyAncestor(isDialog()))
            .performClick()
        verify {
            viewModel.trySendAction(ExportVaultAction.ConfirmExportVaultClicked)
        }
    }

    @Test
    fun `policy text should update according to state`() {
        val text =
            "One or more organization policies prevents your from exporting your individual vault."
        composeTestRule.onNodeWithText(text).assertDoesNotExist()

        mutableStateFlow.update {
            it.copy(policyPreventsExport = true)
        }

        composeTestRule.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun `file format selection button should send ExportFormatOptionSelect action`() {
        // Open the menu.
        composeTestRule
            .onNodeWithContentDescription(label = "File format, .json")
            .performClick()

        // Choose the option from the menu.
        composeTestRule
            .onNodeWithText(".csv")
            .performScrollTo()
            .performClick()

        verify {
            viewModel.trySendAction(
                ExportVaultAction.ExportFormatOptionSelect(ExportVaultFormat.CSV),
            )
        }
    }

    @Test
    fun `file format selection button should update according to state`() {
        composeTestRule
            .onNodeWithContentDescription(label = "File format, .json")
            .assertIsDisplayed()

        mutableStateFlow.update {
            it.copy(exportFormat = ExportVaultFormat.CSV)
        }

        composeTestRule
            .onNodeWithContentDescription(label = "File format, .csv")
            .assertIsDisplayed()
    }

    @Test
    fun `loadingDialog should update according to state`() {
        composeTestRule.onNodeWithText("Loading...").assertDoesNotExist()

        mutableStateFlow.update {
            it.copy(
                dialogState = ExportVaultState.DialogState.Loading(
                    message = "Loading...".asText(),
                ),
            )
        }

        composeTestRule.onNodeWithText("Loading...").isDisplayed()
    }

    @Test
    fun `NavigateBack event should call onNavigateBack`() {
        mutableEventFlow.tryEmit(ExportVaultEvent.NavigateBack)
        assertTrue(onNavigateBackCalled)
    }

    @Test
    fun `password input change should send PasswordInputChange action`() {
        val input = "Test123"
        composeTestRule.onNodeWithText("Master password").performTextInput(input)
        verify {
            viewModel.trySendAction(ExportVaultAction.PasswordInputChanged("Test123"))
        }
    }
}

private val DEFAULT_STATE = ExportVaultState(
    dialogState = null,
    exportFormat = ExportVaultFormat.JSON,
    passwordInput = "",
    exportData = "",
    policyPreventsExport = false,
)