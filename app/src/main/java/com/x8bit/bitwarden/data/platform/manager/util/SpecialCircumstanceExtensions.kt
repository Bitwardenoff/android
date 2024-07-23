package com.x8bit.bitwarden.data.platform.manager.util

import com.x8bit.bitwarden.data.autofill.fido2.model.Fido2CredentialRequest
import com.x8bit.bitwarden.data.autofill.model.AutofillSaveItem
import com.x8bit.bitwarden.data.autofill.model.AutofillSelectionData
import com.x8bit.bitwarden.data.platform.manager.model.SpecialCircumstance

/**
 * Returns [AutofillSaveItem] when contained in the given [SpecialCircumstance].
 */
fun SpecialCircumstance.toAutofillSaveItemOrNull(): AutofillSaveItem? =
    when (this) {
        is SpecialCircumstance.AutofillSave -> this.autofillSaveItem
        is SpecialCircumstance.AutofillSelection -> null
        is SpecialCircumstance.PasswordlessRequest -> null
        is SpecialCircumstance.ShareNewSend -> null
        SpecialCircumstance.GeneratorShortcut -> null
        SpecialCircumstance.VaultShortcut -> null
        is SpecialCircumstance.Fido2Save -> null
        is SpecialCircumstance.CompleteRegistration -> null
    }

/**
 * Returns [AutofillSelectionData] when contained in the given [SpecialCircumstance].
 */
fun SpecialCircumstance.toAutofillSelectionDataOrNull(): AutofillSelectionData? =
    when (this) {
        is SpecialCircumstance.AutofillSave -> null
        is SpecialCircumstance.AutofillSelection -> this.autofillSelectionData
        is SpecialCircumstance.PasswordlessRequest -> null
        is SpecialCircumstance.ShareNewSend -> null
        SpecialCircumstance.GeneratorShortcut -> null
        SpecialCircumstance.VaultShortcut -> null
        is SpecialCircumstance.Fido2Save -> null
        is SpecialCircumstance.CompleteRegistration -> null
    }

/**
 * Returns [Fido2CredentialRequest] when contained in the given [SpecialCircumstance].
 */
fun SpecialCircumstance.toFido2RequestOrNull(): Fido2CredentialRequest? =
    when (this) {
        is SpecialCircumstance.Fido2Save -> this.fido2CredentialRequest
        else -> null
    }
