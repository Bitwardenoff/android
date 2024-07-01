package com.x8bit.bitwarden.data.autofill.fido2.model

import android.content.pm.SigningInfo

/**
 * Creates a mock [Fido2CredentialRequest] with a given [number].
 */
fun createMockFido2CredentialRequest(
    number: Int,
    origin: String? = null,
    signingInfo: SigningInfo = SigningInfo(),
): Fido2CredentialRequest =
    Fido2CredentialRequest(
        userId = "mockUserId-$number",
        requestJson = """{"request": {"number": $number}}""",
        packageName = "com.x8bit.bitwarden",
        signingInfo = signingInfo,
        origin = origin,
    )
