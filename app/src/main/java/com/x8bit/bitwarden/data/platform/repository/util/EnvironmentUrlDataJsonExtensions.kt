package com.x8bit.bitwarden.data.platform.repository.util

import com.x8bit.bitwarden.data.auth.datasource.disk.model.EnvironmentUrlDataJson
import com.x8bit.bitwarden.data.platform.repository.model.Environment
import java.net.URI

private const val DEFAULT_WEB_VAULT_URL: String = "https://vault.bitwarden.com"
private const val DEFAULT_WEB_SEND_URL: String = "https://send.bitwarden.com/#"

/**
 * Returns the base web vault URL. This will check for a custom [EnvironmentUrlDataJson.webVault]
 * before falling back to the [EnvironmentUrlDataJson.base]. This can still return null if both are
 * null or blank.
 */
val EnvironmentUrlDataJson.baseWebVaultUrlOrNull: String?
    get() =
        this
            .webVault
            .takeIf { !it.isNullOrBlank() }
            ?: base.takeIf { it.isNotBlank() }

/**
 * Returns the base web vault URL or the default value if one is not present.
 *
 * See [baseWebVaultUrlOrNull] for more details.
 */
val EnvironmentUrlDataJson.baseWebVaultUrlOrDefault: String
    get() = this.baseWebVaultUrlOrNull ?: DEFAULT_WEB_VAULT_URL

/**
 * Returns the base web send URL or the default value if one is not present.
 */
val EnvironmentUrlDataJson.baseWebSendUrl: String
    get() =
        this
            .baseWebVaultUrlOrNull
            ?.let { "$it/#/send/" }
            ?: DEFAULT_WEB_SEND_URL

/**
 * Returns the appropriate pre-defined labels for environments matching the known US/EU values.
 * Otherwise returns the host of the custom base URL.
 */
val EnvironmentUrlDataJson.labelOrBaseUrlHost: String
    get() = when (this) {
        EnvironmentUrlDataJson.DEFAULT_US -> Environment.Us.label
        EnvironmentUrlDataJson.DEFAULT_EU -> Environment.Eu.label
        else -> {
            // Grab the domain
            // Ex:
            // - "https://www.abc.com/path-1/path-1" -> "www.abc.com"
            URI
                .create(this.base)
                .host
                .orEmpty()
        }
    }

/**
 * Converts a raw [EnvironmentUrlDataJson] to an externally-consumable [Environment].
 */
fun EnvironmentUrlDataJson.toEnvironmentUrls(): Environment =
    when (this) {
        Environment.Us.environmentUrlData -> Environment.Us
        Environment.Eu.environmentUrlData -> Environment.Eu
        else -> Environment.SelfHosted(environmentUrlData = this)
    }

/**
 * Converts a nullable [EnvironmentUrlDataJson] to an [Environment], where `null` values default to
 * the US environment.
 */
fun EnvironmentUrlDataJson?.toEnvironmentUrlsOrDefault(): Environment =
    this?.toEnvironmentUrls() ?: Environment.Us
