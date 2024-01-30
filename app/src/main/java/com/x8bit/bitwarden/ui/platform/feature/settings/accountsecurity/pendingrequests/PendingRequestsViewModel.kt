package com.x8bit.bitwarden.ui.platform.feature.settings.accountsecurity.pendingrequests

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.auth.repository.model.AuthRequest
import com.x8bit.bitwarden.data.auth.repository.model.AuthRequestsResult
import com.x8bit.bitwarden.ui.platform.base.BaseViewModel
import com.x8bit.bitwarden.ui.platform.base.util.Text
import com.x8bit.bitwarden.ui.platform.base.util.isOverFiveMinutesOld
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import javax.inject.Inject

private const val KEY_STATE = "state"

/**
 * View model for the pending login requests screen.
 */
@HiltViewModel
class PendingRequestsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<PendingRequestsState, PendingRequestsEvent, PendingRequestsAction>(
    initialState = savedStateHandle[KEY_STATE] ?: PendingRequestsState(
        authRequests = emptyList(),
        viewState = PendingRequestsState.ViewState.Loading,
    ),
) {
    private val dateTimeFormatter
        get() = DateTimeFormatter
            .ofPattern("M/d/yy hh:mm a")
            .withZone(TimeZone.getDefault().toZoneId())

    init {
        updateAuthRequestList()
    }

    override fun handleAction(action: PendingRequestsAction) {
        when (action) {
            PendingRequestsAction.CloseClick -> handleCloseClicked()
            PendingRequestsAction.DeclineAllRequestsConfirm -> handleDeclineAllRequestsConfirmed()
            PendingRequestsAction.LifecycleResume -> handleOnLifecycleResumed()
            is PendingRequestsAction.PendingRequestRowClick -> {
                handlePendingRequestRowClicked(action)
            }

            is PendingRequestsAction.Internal.AuthRequestsResultReceive -> {
                handleAuthRequestsResultReceived(action)
            }
        }
    }

    private fun handleCloseClicked() {
        sendEvent(PendingRequestsEvent.NavigateBack)
    }

    private fun handleDeclineAllRequestsConfirmed() {
        viewModelScope.launch {
            mutableStateFlow.update {
                it.copy(
                    viewState = PendingRequestsState.ViewState.Loading,
                )
            }
            mutableStateFlow.value.authRequests.forEach { request ->
                authRepository.updateAuthRequest(
                    requestId = request.id,
                    masterPasswordHash = request.masterPasswordHash,
                    publicKey = request.publicKey,
                    isApproved = false,
                )
            }
            updateAuthRequestList()
        }
    }

    private fun handleOnLifecycleResumed() {
        updateAuthRequestList()
    }

    private fun handlePendingRequestRowClicked(
        action: PendingRequestsAction.PendingRequestRowClick,
    ) {
        sendEvent(PendingRequestsEvent.NavigateToLoginApproval(action.fingerprint))
    }

    private fun handleAuthRequestsResultReceived(
        action: PendingRequestsAction.Internal.AuthRequestsResultReceive,
    ) {
        when (val result = action.authRequestsResult) {
            is AuthRequestsResult.Success -> {
                val requests = result
                    .authRequests
                    .filterRespondedAndExpired()
                    .sortedByDescending { request -> request.creationDate }
                    .map { request ->
                        PendingRequestsState.ViewState.Content.PendingLoginRequest(
                            fingerprintPhrase = request.fingerprint,
                            platform = request.platform,
                            timestamp = dateTimeFormatter.format(
                                request.creationDate,
                            ),
                        )
                    }
                if (requests.isEmpty()) {
                    mutableStateFlow.update {
                        it.copy(
                            authRequests = emptyList(),
                            viewState = PendingRequestsState.ViewState.Empty,
                        )
                    }
                } else {
                    mutableStateFlow.update {
                        it.copy(
                            authRequests = result.authRequests,
                            viewState = PendingRequestsState.ViewState.Content(
                                requests = requests,
                            ),
                        )
                    }
                }
            }

            AuthRequestsResult.Error -> {
                mutableStateFlow.update {
                    it.copy(
                        authRequests = emptyList(),
                        viewState = PendingRequestsState.ViewState.Error,
                    )
                }
            }
        }
    }

    private fun updateAuthRequestList() {
        // TODO BIT-1574: Display pull to refresh
        viewModelScope.launch {
            trySendAction(
                PendingRequestsAction.Internal.AuthRequestsResultReceive(
                    authRequestsResult = authRepository.getAuthRequests(),
                ),
            )
        }
    }
}

/**
 * Models state for the Pending Login Requests screen.
 */
@Parcelize
data class PendingRequestsState(
    val authRequests: List<AuthRequest>,
    val viewState: ViewState,
) : Parcelable {
    /**
     * Represents the specific view states for the [PendingRequestsScreen].
     */
    @Parcelize
    sealed class ViewState : Parcelable {
        /**
         * Content state for the [PendingRequestsScreen] listing pending request items.
         */
        @Parcelize
        data class Content(
            val requests: List<PendingLoginRequest>,
        ) : ViewState() {
            /**
             * Models the data for a pending login request.
             */
            @Parcelize
            data class PendingLoginRequest(
                val fingerprintPhrase: String,
                val platform: String,
                val timestamp: String,
            ) : Parcelable
        }

        /**
         * Represents the state wherein there are no pending login requests.
         */
        @Parcelize
        data object Empty : ViewState()

        /**
         * Represents a state where the [PendingRequestsScreen] is unable to display data due to an
         * error retrieving it.
         */
        @Parcelize
        data object Error : ViewState()

        /**
         * Loading state for the [PendingRequestsScreen], signifying that the content is being
         * processed.
         */
        @Parcelize
        data object Loading : ViewState()
    }
}

/**
 * Models events for the delete account screen.
 */
sealed class PendingRequestsEvent {
    /**
     * Navigates back.
     */
    data object NavigateBack : PendingRequestsEvent()

    /**
     * Navigates to the Login Approval screen with the given fingerprint.
     */
    data class NavigateToLoginApproval(
        val fingerprint: String,
    ) : PendingRequestsEvent()

    /**
     * Displays the [message] in a toast.
     */
    data class ShowToast(
        val message: Text,
    ) : PendingRequestsEvent()
}

/**
 * Models actions for the delete account screen.
 */
sealed class PendingRequestsAction {

    /**
     * The user has clicked the close button.
     */
    data object CloseClick : PendingRequestsAction()

    /**
     * The user has confirmed they want to deny all login requests.
     */
    data object DeclineAllRequestsConfirm : PendingRequestsAction()

    /**
     * The screen has been re-opened and should be updated.
     */
    data object LifecycleResume : PendingRequestsAction()

    /**
     * The user has clicked one of the pending request rows.
     */
    data class PendingRequestRowClick(
        val fingerprint: String,
    ) : PendingRequestsAction()

    /**
     * Models actions sent by the view model itself.
     */
    sealed class Internal : PendingRequestsAction() {
        /**
         * Indicates that a new auth requests result has been received.
         */
        data class AuthRequestsResultReceive(
            val authRequestsResult: AuthRequestsResult,
        ) : Internal()
    }
}

/**
 * Filters out [AuthRequest]s that match one of the following criteria:
 * * The request has been approved.
 * * The request has been declined (indicated by it not being approved & having a responseDate).
 * * The request has expired (it is at least 5 minutes old).
 */
private fun List<AuthRequest>.filterRespondedAndExpired() =
    filterNot { request ->
        request.requestApproved ||
            request.responseDate != null ||
            request.creationDate.isOverFiveMinutesOld()
    }
