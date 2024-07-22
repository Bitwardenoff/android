package com.x8bit.bitwarden.data.auth.util

import android.content.Intent
import android.net.Uri
import com.x8bit.bitwarden.data.platform.manager.model.CompleteRegistrationData
import com.x8bit.bitwarden.data.platform.repository.model.Environment

/**
 * Checks if the given [Intent] contains data to complete registration.
 * The [CompleteRegistrationData] will be returned when present.
 */
fun Intent.getCompleteRegistrationDataIntentOrNull(): CompleteRegistrationData?  {
    val sanitizedUriString = data.toString().replace("/#/","/")
    val uri = runCatching { Uri.parse(sanitizedUriString) }.getOrNull() ?: return null
    uri.host ?: return null
    if (uri.path != "/finish-signup") return null
    val email = uri?.getQueryParameter("email") ?: return null
    val verificationToken = uri.getQueryParameter("token") ?: return null
    val fromEmail = uri.getBooleanQueryParameter("fromEmail", true)
    return CompleteRegistrationData(
        email = email,
        verificationToken = verificationToken,
        fromEmail = fromEmail
    )
}
