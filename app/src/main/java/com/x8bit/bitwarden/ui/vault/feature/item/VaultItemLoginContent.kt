package com.x8bit.bitwarden.ui.vault.feature.item

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.ui.platform.components.BitwardenCircularCountdownIndicator
import com.x8bit.bitwarden.ui.platform.components.BitwardenHiddenPasswordField
import com.x8bit.bitwarden.ui.platform.components.BitwardenIconButtonWithResource
import com.x8bit.bitwarden.ui.platform.components.BitwardenListHeaderText
import com.x8bit.bitwarden.ui.platform.components.BitwardenPasswordFieldWithActions
import com.x8bit.bitwarden.ui.platform.components.BitwardenTextField
import com.x8bit.bitwarden.ui.platform.components.BitwardenTextFieldWithActions
import com.x8bit.bitwarden.ui.platform.components.model.IconResource
import com.x8bit.bitwarden.ui.platform.theme.LocalNonMaterialTypography
import com.x8bit.bitwarden.ui.vault.feature.item.handlers.VaultCommonItemTypeHandlers
import com.x8bit.bitwarden.ui.vault.feature.item.handlers.VaultLoginItemTypeHandlers
import com.x8bit.bitwarden.ui.vault.feature.item.model.TotpCodeItemData

private const val AUTH_CODE_SPACING_INTERVAL = 3

/**
 * The top level content UI state for the [VaultItemScreen] when viewing a Login cipher.
 */
@Suppress("LongMethod")
@Composable
fun VaultItemLoginContent(
    commonState: VaultItemState.ViewState.Content.Common,
    loginItemState: VaultItemState.ViewState.Content.ItemType.Login,
    vaultCommonItemTypeHandlers: VaultCommonItemTypeHandlers,
    vaultLoginItemTypeHandlers: VaultLoginItemTypeHandlers,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
    ) {
        item {
            BitwardenListHeaderText(
                label = stringResource(id = R.string.item_information),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            BitwardenTextField(
                label = stringResource(id = R.string.name),
                value = commonState.name,
                onValueChange = { },
                readOnly = true,
                singleLine = false,
                modifier = Modifier
                    .semantics { testTag = "ItemRow" }
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        loginItemState.username?.let { username ->
            item {
                Spacer(modifier = Modifier.height(8.dp))
                UsernameField(
                    username = username,
                    onCopyUsernameClick = vaultLoginItemTypeHandlers.onCopyUsernameClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        loginItemState.passwordData?.let { passwordData ->
            item {
                Spacer(modifier = Modifier.height(8.dp))
                PasswordField(
                    passwordData = passwordData,
                    onShowPasswordClick = vaultLoginItemTypeHandlers.onShowPasswordClick,
                    onCheckForBreachClick = vaultLoginItemTypeHandlers.onCheckForBreachClick,
                    onCopyPasswordClick = vaultLoginItemTypeHandlers.onCopyPasswordClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        loginItemState.totpCodeItemData?.let { totpCodeItemData ->
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TotpField(
                    totpCodeItemData = totpCodeItemData,
                    isPremiumUser = loginItemState.isPremiumUser,
                    onCopyTotpClick = vaultLoginItemTypeHandlers.onCopyTotpCodeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        loginItemState.uris.takeUnless { it.isEmpty() }?.let { uris ->
            item {
                Spacer(modifier = Modifier.height(4.dp))
                BitwardenListHeaderText(
                    label = stringResource(id = R.string.ur_is),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            items(uris) { uriData ->
                Spacer(modifier = Modifier.height(8.dp))
                UriField(
                    uriData = uriData,
                    onCopyUriClick = vaultLoginItemTypeHandlers.onCopyUriClick,
                    onLaunchUriClick = vaultLoginItemTypeHandlers.onLaunchUriClick,
                    modifier = Modifier
                        .semantics { testTag = "UriRow" }
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        commonState.notes?.let { notes ->
            item {
                Spacer(modifier = Modifier.height(4.dp))
                BitwardenListHeaderText(
                    label = stringResource(id = R.string.notes),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                NotesField(
                    notes = notes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        commonState.customFields.takeUnless { it.isEmpty() }?.let { customFields ->
            item {
                Spacer(modifier = Modifier.height(4.dp))
                BitwardenListHeaderText(
                    label = stringResource(id = R.string.custom_fields),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            items(customFields) { customField ->
                Spacer(modifier = Modifier.height(8.dp))
                CustomField(
                    customField = customField,
                    onCopyCustomHiddenField = vaultCommonItemTypeHandlers.onCopyCustomHiddenField,
                    onCopyCustomTextField = vaultCommonItemTypeHandlers.onCopyCustomTextField,
                    onShowHiddenFieldClick = vaultCommonItemTypeHandlers.onShowHiddenFieldClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        commonState.attachments.takeUnless { it?.isEmpty() == true }?.let { attachments ->
            item {
                Spacer(modifier = Modifier.height(4.dp))
                BitwardenListHeaderText(
                    label = stringResource(id = R.string.attachments),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            items(attachments) { attachmentItem ->
                AttachmentItemContent(
                    modifier = Modifier
                        .semantics { testTag = "CipherAttachment" }
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    attachmentItem = attachmentItem,
                    onAttachmentDownloadClick =
                    vaultCommonItemTypeHandlers.onAttachmentDownloadClick,
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            VaultItemUpdateText(
                header = "${stringResource(id = R.string.date_updated)}: ",
                text = commonState.lastUpdated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        loginItemState.passwordRevisionDate?.let { revisionDate ->
            item {
                VaultItemUpdateText(
                    header = "${stringResource(id = R.string.date_password_updated)}: ",
                    text = revisionDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        loginItemState.passwordHistoryCount?.let { passwordHistoryCount ->
            item {
                PasswordHistoryCount(
                    passwordHistoryCount = passwordHistoryCount,
                    onPasswordHistoryClick = vaultLoginItemTypeHandlers.onPasswordHistoryClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(88.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun NotesField(
    notes: String,
    modifier: Modifier = Modifier,
) {
    BitwardenTextField(
        label = stringResource(id = R.string.notes),
        value = notes,
        onValueChange = { },
        readOnly = true,
        singleLine = false,
        modifier = modifier,
    )
}

@Composable
private fun PasswordField(
    passwordData: VaultItemState.ViewState.Content.ItemType.Login.PasswordData,
    onShowPasswordClick: (Boolean) -> Unit,
    onCheckForBreachClick: () -> Unit,
    onCopyPasswordClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (passwordData.canViewPassword) {
        BitwardenPasswordFieldWithActions(
            label = stringResource(id = R.string.password),
            value = passwordData.password,
            showPasswordChange = { onShowPasswordClick(it) },
            showPassword = passwordData.isVisible,
            onValueChange = { },
            readOnly = true,
            singleLine = false,
            actions = {
                BitwardenIconButtonWithResource(
                    iconRes = IconResource(
                        iconPainter = painterResource(id = R.drawable.ic_check_mark),
                        contentDescription = stringResource(
                            id = R.string.check_known_data_breaches_for_this_password,
                        ),
                    ),
                    onClick = onCheckForBreachClick,
                )
                BitwardenIconButtonWithResource(
                    iconRes = IconResource(
                        iconPainter = painterResource(id = R.drawable.ic_copy),
                        contentDescription = stringResource(id = R.string.copy_password),
                    ),
                    onClick = onCopyPasswordClick,
                )
            },
            modifier = modifier,
        )
    } else {
        BitwardenHiddenPasswordField(
            label = stringResource(id = R.string.password),
            value = passwordData.password,
            modifier = modifier,
        )
    }
}

@Composable
private fun PasswordHistoryCount(
    passwordHistoryCount: Int,
    onPasswordHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { },
    ) {
        Text(
            text = "${stringResource(id = R.string.password_history)}: ",
            style = LocalNonMaterialTypography.current.labelMediumProminent,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = passwordHistoryCount.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onPasswordHistoryClick),
        )
    }
}

@Composable
private fun TotpField(
    totpCodeItemData: TotpCodeItemData,
    isPremiumUser: Boolean,
    onCopyTotpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isPremiumUser) {
        Row {
            BitwardenTextFieldWithActions(
                label = stringResource(id = R.string.verification_code_totp),
                value = totpCodeItemData.verificationCode
                    .chunked(AUTH_CODE_SPACING_INTERVAL)
                    .joinToString(" "),
                onValueChange = { },
                readOnly = true,
                singleLine = true,
                actions = {
                    BitwardenCircularCountdownIndicator(
                        timeLeftSeconds = totpCodeItemData.timeLeftSeconds,
                        periodSeconds = totpCodeItemData.periodSeconds,
                    )
                    BitwardenIconButtonWithResource(
                        iconRes = IconResource(
                            iconPainter = painterResource(id = R.drawable.ic_copy),
                            contentDescription = stringResource(id = R.string.copy_totp),
                        ),
                        onClick = onCopyTotpClick,
                    )
                },
                modifier = modifier,
            )
        }
    } else {
        BitwardenTextField(
            label = stringResource(id = R.string.verification_code_totp),
            value = stringResource(id = R.string.premium_subscription_required),
            enabled = false,
            singleLine = false,
            onValueChange = { },
            readOnly = true,
            modifier = modifier,
        )
    }
}

@Composable
private fun UriField(
    uriData: VaultItemState.ViewState.Content.ItemType.Login.UriData,
    onCopyUriClick: (String) -> Unit,
    onLaunchUriClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BitwardenTextFieldWithActions(
        label = stringResource(id = R.string.uri),
        value = uriData.uri,
        onValueChange = { },
        readOnly = true,
        singleLine = false,
        actions = {
            if (uriData.isLaunchable) {
                BitwardenIconButtonWithResource(
                    iconRes = IconResource(
                        iconPainter = painterResource(id = R.drawable.ic_launch),
                        contentDescription = stringResource(id = R.string.launch),
                    ),
                    onClick = { onLaunchUriClick(uriData.uri) },
                )
            }
            if (uriData.isCopyable) {
                BitwardenIconButtonWithResource(
                    iconRes = IconResource(
                        iconPainter = painterResource(id = R.drawable.ic_copy),
                        contentDescription = stringResource(id = R.string.copy),
                    ),
                    onClick = { onCopyUriClick(uriData.uri) },
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun UsernameField(
    username: String,
    onCopyUsernameClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BitwardenTextFieldWithActions(
        label = stringResource(id = R.string.username),
        value = username,
        onValueChange = { },
        readOnly = true,
        singleLine = false,
        actions = {
            BitwardenIconButtonWithResource(
                iconRes = IconResource(
                    iconPainter = painterResource(id = R.drawable.ic_copy),
                    contentDescription = stringResource(id = R.string.copy_username),
                ),
                onClick = onCopyUsernameClick,
            )
        },
        modifier = modifier,
    )
}