package com.oneclickalias.app.navigation

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.oneclickalias.app.AppContainer
import com.oneclickalias.app.BuildConfig
import com.oneclickalias.app.data.lock.AuthPurpose
import com.oneclickalias.app.data.session.SessionState
import com.oneclickalias.app.data.session.SignOutNotice
import com.oneclickalias.app.platform.PasswordManagerSaveField
import com.oneclickalias.app.platform.SecureWindow
import com.oneclickalias.app.platform.openAppSettings
import com.oneclickalias.app.platform.rememberCameraPermission
import com.oneclickalias.app.platform.rememberPasswordManagerSave
import com.oneclickalias.app.ui.aliases.AliasesScreen
import com.oneclickalias.app.ui.aliases.AliasesViewModel
import com.oneclickalias.app.ui.aliases.CopyCallbacks
import com.oneclickalias.app.ui.aliases.CopyScreen
import com.oneclickalias.app.ui.aliases.CopyViewModel
import com.oneclickalias.app.ui.aliases.HomeFrame
import com.oneclickalias.app.ui.aliases.HomeTab
import com.oneclickalias.app.ui.aliases.callbacks
import com.oneclickalias.app.ui.devices.DevicesCallbacks
import com.oneclickalias.app.ui.devices.DevicesScreen
import com.oneclickalias.app.ui.devices.DevicesViewModel
import com.oneclickalias.app.ui.lock.LockScreen
import com.oneclickalias.app.ui.pairing.BrowserSignInCallbacks
import com.oneclickalias.app.ui.pairing.BrowserSignInScreen
import com.oneclickalias.app.ui.pairing.BrowserSignInViewModel
import com.oneclickalias.app.ui.pairing.PairingCallbacks
import com.oneclickalias.app.ui.pairing.PairingScreen
import com.oneclickalias.app.ui.pairing.PairingViewModel
import com.oneclickalias.app.ui.recipients.RecipientsScreen
import com.oneclickalias.app.ui.recipients.RecipientsViewModel
import com.oneclickalias.app.ui.recipients.callbacks
import com.oneclickalias.app.ui.screens.AccountScreen
import com.oneclickalias.app.ui.screens.AccountViewModel
import com.oneclickalias.app.ui.screens.CreateAccountScreen
import com.oneclickalias.app.ui.screens.CreateAccountUiState
import com.oneclickalias.app.ui.screens.CreateAccountViewModel
import com.oneclickalias.app.ui.screens.LoginScreen
import com.oneclickalias.app.ui.screens.LoginViewModel
import com.oneclickalias.app.ui.screens.NumberConfirmScreen
import com.oneclickalias.app.ui.screens.NumberConfirmTexts
import com.oneclickalias.app.ui.screens.NumberConfirmViewModel
import kotlinx.serialization.Serializable

/** Screens reachable without a session. */
@Serializable
data object SignedOutGraph

/** Screens that need a session. */
@Serializable
data object SignedInGraph

@Serializable
data object CreateAccountRoute

/** First screen without a session: log in, scan a code or create an account. */
@Serializable
data object SignInRoute

/** QR login: scan the code from the dashboard. */
@Serializable
data object ScanCodeRoute

/** Home: the tabs "New alias", "Aliases" and "Account". */
@Serializable
data object AliasesRoute

@Serializable
data object BrowserSignInRoute

@Serializable
data object DevicesRoute

/** "Sign out all devices", confirmed with the account number. */
@Serializable
data object RevokeAllRoute

/** "Delete account", confirmed with the account number. */
@Serializable
data object DeleteAccountRoute

/** @property add opened through "Add recipient": starts with the add sheet. */
@Serializable
data class RecipientsRoute(val add: Boolean = false)

/**
 * App navigation, driven by [SessionManager.state][com.oneclickalias.app.data.session.SessionManager.state]:
 * signed in shows [SignedInGraph], signed out [SignedOutGraph]. Switching
 * clears the back stack, so back never leads from Home to the login screens
 * (or the reverse) and their ViewModels, with the account number, are cleared.
 * While app lock locks the app, [LockScreen] replaces everything.
 */
@Composable
fun OcaNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val session by container.sessionManager.state.collectAsStateWithLifecycle()
    val appLock = container.appLock
    val lock by appLock.state.collectAsStateWithLifecycle()
    // The activity's start and stop decide when the app locks again.
    LifecycleStartEffect(appLock) {
        appLock.onForeground()
        onStopOrDispose { appLock.onBackground() }
    }
    // With app lock on, no screenshots and a blank preview in the recent apps.
    SecureWindow(enabled = lock.enabled && session == SessionState.SignedIn)
    // The open tab of home. Kept out here, so it survives the lock screen (which
    // replaces the whole NavHost); the next session starts at the first tab again.
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.NewAlias) }
    LaunchedEffect(session) { if (session is SessionState.SignedOut) homeTab = HomeTab.NewAlias }
    if (session == SessionState.Loading || !lock.ready) {
        // Reading the stored session takes a moment at start; show a plain page.
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (lock.locked) {
        // The app is not composed behind the lock: nothing to see, reach or read aloud,
        // and no sheet window on top. The back stack stays in the NavController.
        LockScreen(state = lock, onUnlock = appLock::unlock, onLogOut = appLock::logOut, modifier = modifier)
        return
    }
    val signedIn = session == SessionState.SignedIn
    val startSignedIn = remember { signedIn }
    val sessions = container.sessionManager

    NavHost(
        navController = navController,
        startDestination = if (startSignedIn) SignedInGraph else SignedOutGraph,
        modifier = modifier,
        // Screens switch at once, like the tabs: no fade (the default) and no shrinking on the
        // back gesture; while the finger is down the screen stays put.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
        predictivePopEnterTransition = { _ -> EnterTransition.None },
        predictivePopExitTransition = { _ -> ExitTransition.None }
    ) {
        navigation<SignedOutGraph>(startDestination = SignInRoute) {
            composable<SignInRoute> {
                val vm = viewModel { LoginViewModel(sessions) }
                val state by vm.state.collectAsStateWithLifecycle()
                LoginScreen(
                    state = state,
                    notice = (session as? SessionState.SignedOut)?.notice?.text(),
                    onInput = vm::onInput,
                    onToggleReveal = vm::toggleReveal,
                    onConfirmedChange = vm::setConfirmed,
                    onSubmit = vm::submit,
                    onCreateAccount = {
                        sessions.clearNotice()
                        navController.navigate(CreateAccountRoute) { launchSingleTop = true }
                    },
                    onRevokeAllAndLogIn = vm::revokeAllAndLogIn,
                    onScanCode = if (container.hasCamera) {
                        {
                            sessions.clearNotice()
                            navController.navigate(ScanCodeRoute) { launchSingleTop = true }
                        }
                    } else {
                        null
                    }
                )
            }
            composable<CreateAccountRoute> {
                val vm = viewModel {
                    CreateAccountViewModel(container.authApi, sessions, container.clipboard, createSavedStateHandle())
                }
                val state by vm.state.collectAsStateWithLifecycle()
                // Rechecked on every resume: the user may set one up in the system settings meanwhile.
                val activity = LocalContext.current
                var hasPasswordManager by remember { mutableStateOf(container.hasPasswordManager(activity)) }
                LifecycleResumeEffect(Unit) {
                    hasPasswordManager = container.hasPasswordManager(activity)
                    onPauseOrDispose {}
                }
                val passwordManager = rememberPasswordManagerSave()
                if (hasPasswordManager) PasswordManagerSaveField(passwordManager)
                CreateAccountScreen(
                    onSaveToPasswordManager = if (hasPasswordManager) {
                        { (state as? CreateAccountUiState.Ready)?.let { passwordManager.save(it.accountNumber) } }
                    } else {
                        null
                    },
                    state = state,
                    onBack = { navController.popBackStack() },
                    onRetry = vm::retry,
                    onCopy = vm::copy,
                    onSavedChange = vm::setSaved,
                    onSubmit = vm::submit,
                    onLogIn = { navController.popBackStack() },
                    onStartOver = vm::startOver
                )
            }
            composable<ScanCodeRoute> {
                // Polling stops with this ViewModel, when the screen is left.
                val vm =
                    viewModel { PairingViewModel(container.authApi, sessions, clock = SystemClock::elapsedRealtime) }
                val state by vm.state.collectAsStateWithLifecycle()
                val camera = rememberCameraPermission()
                val context = LocalContext.current
                PairingScreen(
                    state = state,
                    camera = camera.access,
                    callbacks = PairingCallbacks(
                        onBack = { navController.popBackStack() },
                        onScanned = vm::onScanned,
                        onScanAgain = vm::scanAgain,
                        onUseAccountNumber = { navController.popBackStack() },
                        onRequestCamera = camera.request,
                        onOpenSettings = { openAppSettings(context) }
                    )
                )
            }
        }
        navigation<SignedInGraph>(startDestination = AliasesRoute) {
            composable<AliasesRoute> {
                // Back from another tab leads to the first one before it leaves the app.
                BackHandler(enabled = homeTab != HomeTab.NewAlias) { homeTab = HomeTab.NewAlias }
                HomeFrame(tab = homeTab, onSelectTab = { homeTab = it }) {
                    when (homeTab) {
                        HomeTab.NewAlias -> {
                            val vm = viewModel {
                                CopyViewModel(
                                    container.amsApi,
                                    container.claimDelivery,
                                    container.pendingClaims::add,
                                    container.aliasClipboard,
                                    container.webLinks,
                                    container.endpoints.webBase,
                                    sessions::accountStatus
                                )
                            }
                            // Every start and every return to the foreground reloads; that also
                            // delivers copies made offline, and notices a device signed out elsewhere.
                            LifecycleStartEffect(vm) {
                                vm.load()
                                onStopOrDispose { }
                            }
                            val state by vm.state.collectAsStateWithLifecycle()
                            val callbacks = remember(vm) {
                                CopyCallbacks(
                                    onRefresh = { vm.load(userRefresh = true) },
                                    onRetry = { vm.load() },
                                    onCopy = vm::copy,
                                    onOpenBilling = vm::openBilling,
                                    onOpenRecipients = { add ->
                                        navController.navigate(RecipientsRoute(add)) { launchSingleTop = true }
                                    },
                                    onDismissMessage = vm::dismissMessage
                                )
                            }
                            CopyScreen(state = state, callbacks = callbacks)
                        }

                        HomeTab.Aliases -> {
                            val vm = viewModel {
                                AliasesViewModel(
                                    container.amsApi,
                                    container.aliasClipboard,
                                    container.webLinks,
                                    container.endpoints.webBase,
                                    sessions::accountStatus
                                )
                            }
                            LifecycleStartEffect(vm) {
                                vm.load()
                                onStopOrDispose { }
                            }
                            val state by vm.state.collectAsStateWithLifecycle()
                            val callbacks = remember(vm) {
                                vm.callbacks().copy(onOpenNewAlias = { homeTab = HomeTab.NewAlias })
                            }
                            AliasesScreen(state = state, callbacks = callbacks)
                        }

                        HomeTab.Account -> {
                            val vm = viewModel {
                                AccountViewModel(sessions, container.webLinks, container.endpoints.webBase) {
                                    sessions.accountStatus()
                                }
                            }
                            // Every start reads the status again: a payment on the website shows after the return.
                            LifecycleStartEffect(vm) {
                                vm.load()
                                onStopOrDispose { }
                            }
                            val state by vm.state.collectAsStateWithLifecycle()
                            AccountScreen(
                                state = state,
                                appLock = lock,
                                onAppLockChange = appLock::setEnabled,
                                versionName = BuildConfig.VERSION_NAME,
                                onLogout = vm::logout,
                                onOpenRecipients = {
                                    navController.navigate(RecipientsRoute()) { launchSingleTop = true }
                                },
                                onOpenDevices = { navController.navigate(DevicesRoute) { launchSingleTop = true } },
                                onSignInBrowser = if (container.hasCamera) {
                                    { navController.navigate(BrowserSignInRoute) { launchSingleTop = true } }
                                } else {
                                    null
                                },
                                onOpenBilling = vm::openBilling,
                                onDeleteAccount = {
                                    navController.navigate(DeleteAccountRoute) { launchSingleTop = true }
                                }
                            )
                        }
                    }
                }
            }
            composable<RecipientsRoute> { entry ->
                val route = entry.toRoute<RecipientsRoute>()
                val vm = viewModel {
                    RecipientsViewModel(
                        container.amsApi,
                        container.webLinks,
                        container.endpoints.webBase,
                        route.add,
                        sessions::accountStatus
                    )
                }
                // Reload at every start: a link opened on the website shows after the return.
                LifecycleStartEffect(vm) {
                    vm.load()
                    onStopOrDispose { }
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val callbacks = remember(vm) { vm.callbacks(onBack = { navController.popBackStack() }) }
                RecipientsScreen(state = state, callbacks = callbacks)
            }
            composable<BrowserSignInRoute> {
                val vm = viewModel {
                    BrowserSignInViewModel(
                        container.authApi,
                        sessions,
                        confirmIdentity = {
                            // With app lock on, approving a new browser needs the screen lock too.
                            !appLock.state.value.enabled ||
                                container.deviceAuthenticator.authenticate(AuthPurpose.ApproveSignIn)
                        },
                        leaveScope = container.appScope
                    )
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val camera = rememberCameraPermission()
                val context = LocalContext.current
                BrowserSignInScreen(
                    state = state,
                    camera = camera.access,
                    callbacks = BrowserSignInCallbacks(
                        onBack = { navController.popBackStack() },
                        onScanned = vm::onScanned,
                        onApprove = vm::approve,
                        onDeny = vm::deny,
                        onScanAgain = vm::scanAgain,
                        onRequestCamera = camera.request,
                        onOpenSettings = { openAppSettings(context) }
                    )
                )
            }
            composable<DevicesRoute> {
                val vm = viewModel { DevicesViewModel(sessions, container.authApi) }
                LifecycleStartEffect(vm) {
                    vm.load()
                    onStopOrDispose { }
                }
                val state by vm.state.collectAsStateWithLifecycle()
                val callbacks = remember(vm) {
                    DevicesCallbacks(
                        onBack = { navController.popBackStack() },
                        onRefresh = { vm.load(userRefresh = true) },
                        onRetry = { vm.load() },
                        onRequestSignOut = vm::requestSignOut,
                        onDismissSignOut = vm::dismissSignOut,
                        onConfirmSignOut = vm::confirmSignOut,
                        onSignOutAll = { navController.navigate(RevokeAllRoute) { launchSingleTop = true } },
                        onDismissMessage = vm::dismissMessage
                    )
                }
                DevicesScreen(state = state, callbacks = callbacks)
            }
            composable<RevokeAllRoute> {
                val vm = viewModel { NumberConfirmViewModel.revokeAll(sessions) }
                NumberConfirmRoute(vm, NumberConfirmTexts.RevokeAll, onBack = { navController.popBackStack() })
            }
            composable<DeleteAccountRoute> {
                val vm = viewModel { NumberConfirmViewModel.deleteAccount(sessions) }
                NumberConfirmRoute(vm, NumberConfirmTexts.DeleteAccount, onBack = { navController.popBackStack() })
            }
        }
    }

    LaunchedEffect(signedIn) {
        val inSignedInArea = navController.currentBackStackEntry?.destination?.hierarchy
            ?.any { it.hasRoute<SignedInGraph>() } == true
        if (signedIn != inSignedInArea) {
            navController.navigate(if (signedIn) SignedInGraph else SignedOutGraph) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }
}

/** Success ends the session; navigation to the login screen follows the session state. */
@Composable
private fun NumberConfirmRoute(vm: NumberConfirmViewModel, texts: NumberConfirmTexts, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    NumberConfirmScreen(
        texts = texts,
        state = state,
        onInput = vm::onInput,
        onToggleReveal = vm::toggleReveal,
        onSubmit = vm::submit,
        onBack = onBack
    )
}

private fun SignOutNotice.text(): String = when (this) {
    SignOutNotice.SessionEnded -> "You have been signed out. Log in again with your account number."

    SignOutNotice.SignedOutLocally ->
        "Signed out on this device. The server could not be reached, so this device may still be listed " +
            "under your devices until you remove it from another device."

    SignOutNotice.AllDevicesSignedOut ->
        "All devices have been signed out. Log in again with your account number."

    SignOutNotice.AccountDeletionRequested ->
        "Your account will be deleted in 12 hours, together with all aliases and recipients. " +
            "Log in with your account number within that time to cancel the deletion."
}
