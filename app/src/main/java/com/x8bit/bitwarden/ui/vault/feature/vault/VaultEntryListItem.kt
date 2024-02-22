package com.x8bit.bitwarden.ui.vault.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.ui.platform.components.BitwardenListItem
import com.x8bit.bitwarden.ui.platform.components.SelectionItemData
import com.x8bit.bitwarden.ui.platform.components.model.IconData
import com.x8bit.bitwarden.ui.platform.components.model.IconResource
import com.x8bit.bitwarden.ui.platform.theme.BitwardenTheme
import com.x8bit.bitwarden.ui.vault.feature.itemlisting.model.ListingItemOverflowAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A Composable function that displays a row item for different types of vault entries.
 *
 * @param label The primary text label to display for the item.
 * @param supportingLabel An optional secondary text label to display beneath the primary label.
 * @param onClick The lambda to be invoked when the item is clicked.
 * @param overflowOptions List of options to display for the item.
 * @param onOverflowOptionClick The lambda to be invoked when an overflow option is clicked.
 * @param modifier An optional [Modifier] for this Composable, defaulting to an empty Modifier.
 * This allows the caller to specify things like padding, size, etc.
 */
@Composable
fun VaultEntryListItem(
    startIcon: IconData,
    label: String,
    onClick: () -> Unit,
    overflowOptions: List<ListingItemOverflowAction.VaultAction>,
    onOverflowOptionClick: (ListingItemOverflowAction.VaultAction) -> Unit,
    modifier: Modifier = Modifier,
    trailingLabelIcons: ImmutableList<IconResource> = persistentListOf(),
    supportingLabel: String? = null,
) {
    BitwardenListItem(
        modifier = modifier,
        label = label,
        supportingLabel = supportingLabel,
        startIcon = startIcon,
        trailingLabelIcons = trailingLabelIcons,
        onClick = onClick,
        selectionDataList = overflowOptions
            .map { option ->
                SelectionItemData(
                    text = option.title(),
                    onClick = { onOverflowOptionClick(option) },
                )
            }
            .toImmutableList(),
    )
}

@Preview(showBackground = true)
@Composable
private fun VaultEntryListItem_preview() {
    BitwardenTheme {
        VaultEntryListItem(
            startIcon = IconData.Local(R.drawable.ic_login_item),
            label = "Example Login",
            supportingLabel = "Username",
            onClick = {},
            overflowOptions = emptyList(),
            onOverflowOptionClick = {},
            modifier = Modifier,
        )
    }
}