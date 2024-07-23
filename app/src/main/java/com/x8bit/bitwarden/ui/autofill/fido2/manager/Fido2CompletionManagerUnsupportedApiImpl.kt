package com.x8bit.bitwarden.ui.autofill.fido2.manager

import com.x8bit.bitwarden.data.autofill.fido2.model.Fido2CredentialAssertionResult
import com.x8bit.bitwarden.data.autofill.fido2.model.Fido2RegisterCredentialResult
import com.x8bit.bitwarden.data.platform.annotation.OmitFromCoverage

/**
 * A no-op implementation of [Fido2CompletionManagerImpl] provided when the build version is below
 * UPSIDE_DOWN_CAKE (34). These versions do not support [androidx.credentials.CredentialProvider].
 */
@OmitFromCoverage
object Fido2CompletionManagerUnsupportedApiImpl : Fido2CompletionManager {
    override fun completeFido2Registration(result: Fido2RegisterCredentialResult) {
        // no-op
    }

    override fun completeFido2Assertion(result: Fido2CredentialAssertionResult) {
        // no-op
    }
}
