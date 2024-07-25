package com.x8bit.bitwarden.ui.platform.feature.vaultunlockednavbar

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.x8bit.bitwarden.R
import com.x8bit.bitwarden.ui.platform.base.util.EventsEffect
import com.x8bit.bitwarden.ui.platform.base.util.max
import com.x8bit.bitwarden.ui.platform.base.util.toDp
import com.x8bit.bitwarden.ui.platform.components.scaffold.BitwardenScaffold
import com.x8bit.bitwarden.ui.platform.components.scrim.BitwardenAnimatedScrim
import com.x8bit.bitwarden.ui.platform.components.util.rememberVectorPainter
import com.x8bit.bitwarden.ui.platform.feature.search.model.SearchType
import com.x8bit.bitwarden.ui.platform.feature.settings.SETTINGS_GRAPH_ROUTE
import com.x8bit.bitwarden.ui.platform.feature.settings.navigateToSettingsGraph
import com.x8bit.bitwarden.ui.platform.feature.settings.settingsGraph
import com.x8bit.bitwarden.ui.platform.theme.RootTransitionProviders
import com.x8bit.bitwarden.ui.tools.feature.generator.GENERATOR_GRAPH_ROUTE
import com.x8bit.bitwarden.ui.tools.feature.generator.generatorGraph
import com.x8bit.bitwarden.ui.tools.feature.generator.navigateToGeneratorGraph
import com.x8bit.bitwarden.ui.tools.feature.send.SEND_GRAPH_ROUTE
import com.x8bit.bitwarden.ui.tools.feature.send.navigateToSendGraph
import com.x8bit.bitwarden.ui.tools.feature.send.sendGraph
import com.x8bit.bitwarden.ui.vault.feature.vault.VAULT_GRAPH_ROUTE
import com.x8bit.bitwarden.ui.vault.feature.vault.navigateToVaultGraph
import com.x8bit.bitwarden.ui.vault.feature.vault.vaultGraph
import com.x8bit.bitwarden.ui.vault.model.VaultItemCipherType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.parcelize.Parcelize

/**
 * Top level composable for the Vault Unlocked Screen.
 */
@Suppress("LongParameterList")
@Composable
fun VaultUnlockedNavBarScreen(
    viewModel: VaultUnlockedNavBarViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    onNavigateToVaultAddItem: (VaultItemCipherType) -> Unit,
    onNavigateToVaultItem: (vaultItemId: String) -> Unit,
    onNavigateToVaultEditItem: (vaultItemId: String) -> Unit,
    onNavigateToSearchSend: (searchType: SearchType.Sends) -> Unit,
    onNavigateToSearchVault: (searchType: SearchType.Vault) -> Unit,
    onNavigateToAddSend: () -> Unit,
    onNavigateToEditSend: (sendItemId: String) -> Unit,
    onNavigateToDeleteAccount: () -> Unit,
    onNavigateToExportVault: () -> Unit,
    onNavigateToFolders: () -> Unit,
    onNavigateToPendingRequests: () -> Unit,
    onNavigateToPasswordHistory: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    EventsEffect(viewModel = viewModel) { event ->
        navController.apply {
            val navOptions = vaultUnlockedNavBarScreenNavOptions()
            when (event) {
                VaultUnlockedNavBarEvent.NavigateToVaultScreen -> {
                    navigateToVaultGraph(navOptions)
                }

                VaultUnlockedNavBarEvent.NavigateToSendScreen -> {
                    navigateToSendGraph(navOptions)
                }

                VaultUnlockedNavBarEvent.NavigateToGeneratorScreen -> {
                    navigateToGeneratorGraph(navOptions)
                }

                VaultUnlockedNavBarEvent.NavigateToSettingsScreen -> {
                    navigateToSettingsGraph(navOptions)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        navController
            .currentBackStackEntryFlow
            .onEach {
                viewModel.trySendAction(VaultUnlockedNavBarAction.BackStackUpdate)
            }
            .launchIn(this)
    }

    VaultUnlockedNavBarScaffold(
        state = state,
        navController = navController,
        onNavigateToVaultItem = onNavigateToVaultItem,
        onNavigateToVaultEditItem = onNavigateToVaultEditItem,
        navigateToVaultAddItem = onNavigateToVaultAddItem,
        onNavigateToSearchSend = onNavigateToSearchSend,
        onNavigateToSearchVault = onNavigateToSearchVault,
        navigateToAddSend = onNavigateToAddSend,
        onNavigateToEditSend = onNavigateToEditSend,
        navigateToDeleteAccount = onNavigateToDeleteAccount,
        navigateToExportVault = onNavigateToExportVault,
        navigateToFolders = onNavigateToFolders,
        navigateToPendingRequests = onNavigateToPendingRequests,
        navigateToPasswordHistory = onNavigateToPasswordHistory,
        generatorTabClickedAction = remember(viewModel) {
            { viewModel.trySendAction(VaultUnlockedNavBarAction.GeneratorTabClick) }
        },
        sendTabClickedAction = remember(viewModel) {
            { viewModel.trySendAction(VaultUnlockedNavBarAction.SendTabClick) }
        },
        vaultTabClickedAction = remember(viewModel) {
            { viewModel.trySendAction(VaultUnlockedNavBarAction.VaultTabClick) }
        },
        settingsTabClickedAction = remember(viewModel) {
            { viewModel.trySendAction(VaultUnlockedNavBarAction.SettingsTabClick) }
        },
    )
}

/**
 * Scaffold that contains the bottom nav bar for the [VaultUnlockedNavBarScreen]
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
private fun VaultUnlockedNavBarScaffold(
    state: VaultUnlockedNavBarState,
    navController: NavHostController,
    vaultTabClickedAction: () -> Unit,
    sendTabClickedAction: () -> Unit,
    generatorTabClickedAction: () -> Unit,
    settingsTabClickedAction: () -> Unit,
    navigateToVaultAddItem: (VaultItemCipherType) -> Unit,
    onNavigateToVaultItem: (vaultItemId: String) -> Unit,
    onNavigateToVaultEditItem: (vaultItemId: String) -> Unit,
    onNavigateToSearchSend: (searchType: SearchType.Sends) -> Unit,
    onNavigateToSearchVault: (searchType: SearchType.Vault) -> Unit,
    navigateToAddSend: () -> Unit,
    onNavigateToEditSend: (sendItemId: String) -> Unit,
    navigateToDeleteAccount: () -> Unit,
    navigateToExportVault: () -> Unit,
    navigateToFolders: () -> Unit,
    navigateToPendingRequests: () -> Unit,
    navigateToPasswordHistory: () -> Unit,
) {
    var shouldDimNavBar by remember { mutableStateOf(false) }

    // This scaffold will host screens that contain top bars while not hosting one itself.
    // We need to ignore the status bar insets here and let the content screens handle
    // it themselves.
    BitwardenScaffold(
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.statusBars),
        bottomBar = {
            Box {
                var appBarHeightPx by remember { mutableIntStateOf(0) }
                VaultBottomAppBar(
                    state = state,
                    navController = navController,
                    vaultTabClickedAction = vaultTabClickedAction,
                    sendTabClickedAction = sendTabClickedAction,
                    generatorTabClickedAction = generatorTabClickedAction,
                    settingsTabClickedAction = settingsTabClickedAction,
                    modifier = Modifier
                        .onGloballyPositioned {
                            appBarHeightPx = it.size.height
                        },
                )
                BitwardenAnimatedScrim(
                    isVisible = shouldDimNavBar,
                    onClick = {
                        // Do nothing
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(appBarHeightPx.toDp()),
                )
            }
        },
    ) { innerPadding ->
        // Because this Scaffold has a bottom navigation bar, the NavHost will:
        // - consume the navigation bar insets.
        // - consume the IME insets.
        NavHost(
            navController = navController,
            startDestination = VAULT_GRAPH_ROUTE,
            modifier = Modifier
                .consumeWindowInsets(WindowInsets.navigationBars)
                .consumeWindowInsets(WindowInsets.ime)
                .padding(innerPadding.max(WindowInsets.ime)),
            enterTransition = RootTransitionProviders.Enter.fadeIn,
            exitTransition = RootTransitionProviders.Exit.fadeOut,
            popEnterTransition = RootTransitionProviders.Enter.fadeIn,
            popExitTransition = RootTransitionProviders.Exit.fadeOut,
        ) {
            vaultGraph(
                navController = navController,
                onNavigateToVaultAddItemScreen = navigateToVaultAddItem,
                onNavigateToVaultItemScreen = onNavigateToVaultItem,
                onNavigateToVaultEditItemScreen = onNavigateToVaultEditItem,
                onNavigateToSearchVault = onNavigateToSearchVault,
                onDimBottomNavBarRequest = { shouldDim ->
                    shouldDimNavBar = shouldDim
                },
            )
            sendGraph(
                navController = navController,
                onNavigateToAddSend = navigateToAddSend,
                onNavigateToEditSend = onNavigateToEditSend,
                onNavigateToSearchSend = onNavigateToSearchSend,
            )
            generatorGraph(
                onNavigateToPasswordHistory = { navigateToPasswordHistory() },
            )
            settingsGraph(
                navController = navController,
                onNavigateToDeleteAccount = navigateToDeleteAccount,
                onNavigateToExportVault = navigateToExportVault,
                onNavigateToFolders = navigateToFolders,
                onNavigateToPendingRequests = navigateToPendingRequests,
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun VaultBottomAppBar(
    state: VaultUnlockedNavBarState,
    navController: NavHostController,
    vaultTabClickedAction: () -> Unit,
    sendTabClickedAction: () -> Unit,
    generatorTabClickedAction: () -> Unit,
    settingsTabClickedAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        val destinations = listOf(
            VaultUnlockedNavBarTab.Vault(
                labelRes = state.vaultNavBarLabelRes,
                contentDescriptionRes = state.vaultNavBarContentDescriptionRes,
            ),
            VaultUnlockedNavBarTab.Send,
            VaultUnlockedNavBarTab.Generator,
            VaultUnlockedNavBarTab.Settings,
        )
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        destinations.forEach { destination ->
            val isSelected = currentDestination?.hierarchy?.any {
                it.route == destination.route
            } == true

            NavigationBarItem(
                icon = {
                    Icon(
                        painter = rememberVectorPainter(
                            id = if (isSelected) {
                                destination.iconResSelected
                            } else {
                                destination.iconRes
                            },
                        ),
                        contentDescription = stringResource(
                            id = destination.contentDescriptionRes,
                        ),
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selected = isSelected,
                onClick = {
                    when (destination) {
                        is VaultUnlockedNavBarTab.Vault -> vaultTabClickedAction()
                        VaultUnlockedNavBarTab.Send -> sendTabClickedAction()
                        VaultUnlockedNavBarTab.Generator -> generatorTabClickedAction()
                        VaultUnlockedNavBarTab.Settings -> settingsTabClickedAction()
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.testTag(destination.testTag),
            )
        }
    }
}

/**
 * Represents the different tabs available in the navigation bar
 * for the unlocked portion of the vault.
 *
 * Each tab is modeled with properties that provide information on:
 * - Regular icon resource
 * - Icon resource when selected
 * and other essential UI and navigational data.
 *
 * @property iconRes The resource ID for the regular (unselected) icon representing the tab.
 * @property iconResSelected The resource ID for the icon representing the tab when it's selected.
 */
@Parcelize
private sealed class VaultUnlockedNavBarTab : Parcelable {
    /**
     * The resource ID for the icon representing the tab when it is selected.
     */
    abstract val iconResSelected: Int

    /**
     * Resource id for the icon representing the tab.
     */
    abstract val iconRes: Int

    /**
     * Resource id for the label describing the tab.
     */
    abstract val labelRes: Int

    /**
     * Resource id for the content description describing the tab.
     */
    abstract val contentDescriptionRes: Int

    /**
     * Route of the tab.
     */
    abstract val route: String

    /**
     * The test tag of the tab.
     */
    abstract val testTag: String

    /**
     * Show the Generator screen.
     */
    @Parcelize
    data object Generator : VaultUnlockedNavBarTab() {
        override val iconResSelected get() = R.drawable.ic_generator_filled
        override val iconRes get() = R.drawable.ic_generator
        override val labelRes get() = R.string.generator
        override val contentDescriptionRes get() = R.string.generator
        override val route get() = GENERATOR_GRAPH_ROUTE
        override val testTag get() = "GeneratorTab"
    }

    /**
     * Show the Send screen.
     */
    @Parcelize
    data object Send : VaultUnlockedNavBarTab() {
        override val iconResSelected get() = R.drawable.ic_send_filled
        override val iconRes get() = R.drawable.ic_send
        override val labelRes get() = R.string.send
        override val contentDescriptionRes get() = R.string.send
        override val route get() = SEND_GRAPH_ROUTE
        override val testTag get() = "SendTab"
    }

    /**
     * Show the Vault screen.
     */
    @Parcelize
    data class Vault(
        override val labelRes: Int,
        override val contentDescriptionRes: Int,
    ) : VaultUnlockedNavBarTab() {
        override val iconResSelected get() = R.drawable.ic_vault_filled
        override val iconRes get() = R.drawable.ic_vault
        override val route get() = VAULT_GRAPH_ROUTE
        override val testTag get() = "VaultTab"
    }

    /**
     * Show the Settings screen.
     */
    @Parcelize
    data object Settings : VaultUnlockedNavBarTab() {
        override val iconResSelected get() = R.drawable.ic_settings_filled
        override val iconRes get() = R.drawable.ic_settings
        override val labelRes get() = R.string.settings
        override val contentDescriptionRes get() = R.string.settings
        override val route get() = SETTINGS_GRAPH_ROUTE
        override val testTag get() = "SettingsTab"
    }
}

/**
 * Helper function to generate [NavOptions] for [VaultUnlockedNavBarScreen].
 */
private fun NavController.vaultUnlockedNavBarScreenNavOptions(): NavOptions =
    navOptions {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
