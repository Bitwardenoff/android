package com.x8bit.bitwarden.data.auth.datasource.network.api

import com.x8bit.bitwarden.data.auth.datasource.network.model.CreateAccountKeysRequest
import com.x8bit.bitwarden.data.auth.datasource.network.model.DeleteAccountRequestJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.ResetPasswordRequestJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.SetPasswordRequestJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.VerifyOtpRequestJson
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

/**
 * Defines raw calls under the /accounts API with authentication applied.
 */
interface AuthenticatedAccountsApi {

    /**
     * Converts the currently active account to a key-connector account.
     */
    @POST("/accounts/convert-to-key-connector")
    suspend fun convertToKeyConnector(): Result<Unit>

    /**
     * Creates the keys for the current account.
     */
    @POST("/accounts/keys")
    suspend fun createAccountKeys(@Body body: CreateAccountKeysRequest): Result<Unit>

    /**
     * Deletes the current account.
     */
    @HTTP(method = "DELETE", path = "/accounts", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequestJson): Result<Unit>

    @POST("/accounts/request-otp")
    suspend fun requestOtp(): Result<Unit>

    @POST("/accounts/verify-otp")
    suspend fun verifyOtp(
        @Body body: VerifyOtpRequestJson,
    ): Result<Unit>

    /**
     * Resets the temporary password.
     */
    @HTTP(method = "PUT", path = "/accounts/update-temp-password", hasBody = true)
    suspend fun resetTempPassword(@Body body: ResetPasswordRequestJson): Result<Unit>

    /**
     * Resets the password.
     */
    @HTTP(method = "POST", path = "/accounts/password", hasBody = true)
    suspend fun resetPassword(@Body body: ResetPasswordRequestJson): Result<Unit>

    /**
     * Sets the password.
     */
    @POST("/accounts/set-password")
    suspend fun setPassword(@Body body: SetPasswordRequestJson): Result<Unit>
}
