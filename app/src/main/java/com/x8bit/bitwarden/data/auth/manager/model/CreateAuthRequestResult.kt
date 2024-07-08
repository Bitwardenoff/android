package com.x8bit.bitwarden.data.auth.manager.model

import com.bitwarden.core.AuthRequestResponse

/**
 * Models result of creating a new login approval request.
 */
sealed class CreateAuthRequestResult {
    /**
     * Models the data returned when receiving an update for an auth request.
     */
    data class Update(
        val authRequest: AuthRequest,
    ) : CreateAuthRequestResult()

    /**
     * Models the data returned when a auth request has been approved.
     */
    data class Success(
        val authRequest: AuthRequest,
        val authRequestResponse: AuthRequestResponse,
    ) : CreateAuthRequestResult()

    /**
     * There was a generic error getting the user's auth requests.
     */
    data object Error : CreateAuthRequestResult()

    /**
     * The auth request has been declined.
     */
    data object Declined : CreateAuthRequestResult()

    /**
     * The auth request has expired.
     */
    data object Expired : CreateAuthRequestResult()
}
