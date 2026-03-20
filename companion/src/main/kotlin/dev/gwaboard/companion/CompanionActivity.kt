package dev.gwaboard.companion

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.gwaboard.companion.ui.navigation.CompanionRoutes
import dev.gwaboard.companion.ui.screen.DashboardScreen
import dev.gwaboard.companion.ui.screen.DefaultSmsScreen
import dev.gwaboard.companion.ui.screen.PermissionsScreen
import dev.gwaboard.companion.ui.screen.ProfileDetailLevel
import dev.gwaboard.companion.ui.screen.ReanalyzeFrequency
import dev.gwaboard.companion.ui.screen.SettingsScreen
import dev.gwaboard.companion.ui.screen.WelcomeScreen
import dev.gwaboard.companion.ui.theme.CompanionTheme

/**
 * Single-activity UI for the companion SMS app.
 *
 * Hosts a Jetpack Compose navigation graph with two flows:
 * 1. Onboarding (first launch): welcome → permissions → default SMS → dashboard
 * 2. Main (subsequent launches): dashboard with bottom navigation to settings
 *
 * All state is managed locally via mutableStateOf for now.
 * Future milestones will introduce ViewModel + repository layers
 * once the ContentProvider IPC is operational.
 */
class CompanionActivity : ComponentActivity() {

    companion object {
        private const val TAG = "CompanionActivity"
        private const val PREFS_NAME = "companion_prefs"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }

    /** Required SMS and contacts permissions */
    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
    )

    // Observable UI state
    private var permissionsGranted by mutableStateOf(false)
    private var onboardingComplete by mutableStateOf(false)

    // Settings state (will be backed by SharedPreferences / DataStore in the future)
    private var reanalyzeFrequency by mutableStateOf(ReanalyzeFrequency.DAILY)
    private var profileDetailLevel by mutableStateOf(ProfileDetailLevel.STANDARD)
    private var profilesBuilt by mutableIntStateOf(0)

    /** Permission request launcher */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        Log.i(TAG, "Permissions result: granted=$permissionsGranted")
    }

    /** Default SMS app role launcher */
    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i(TAG, "Default SMS result: ${result.resultCode}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check initial state
        permissionsGranted = checkPermissions()
        onboardingComplete = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETE, false)

        setContent {
            CompanionTheme {
                CompanionNavHost(
                    startDestination = if (onboardingComplete) {
                        CompanionRoutes.DASHBOARD
                    } else {
                        CompanionRoutes.WELCOME
                    },
                )
            }
        }
    }

    @Composable
    private fun CompanionNavHost(
        startDestination: String,
        navController: NavHostController = rememberNavController(),
    ) {
        // Show bottom nav only on dashboard/settings screens
        val showBottomNav = startDestination == CompanionRoutes.DASHBOARD

        Scaffold(
            bottomBar = {
                if (showBottomNav) {
                    CompanionBottomNav(navController = navController)
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(innerPadding),
            ) {
                // Onboarding flow
                composable(CompanionRoutes.WELCOME) {
                    WelcomeScreen(
                        onGetStarted = {
                            navController.navigate(CompanionRoutes.PERMISSIONS)
                        },
                    )
                }

                composable(CompanionRoutes.PERMISSIONS) {
                    PermissionsScreen(
                        onGrantPermissions = { requestPermissions() },
                        onSkip = {
                            navController.navigate(CompanionRoutes.DEFAULT_SMS)
                        },
                        permissionsGranted = permissionsGranted,
                    )
                }

                composable(CompanionRoutes.DEFAULT_SMS) {
                    DefaultSmsScreen(
                        onSetDefault = { requestDefaultSmsApp() },
                        onSkip = {
                            markOnboardingComplete()
                            navController.navigate(CompanionRoutes.DASHBOARD) {
                                popUpTo(CompanionRoutes.WELCOME) { inclusive = true }
                            }
                        },
                    )
                }

                // Main screens
                composable(CompanionRoutes.DASHBOARD) {
                    DashboardScreen(
                        profilesBuilt = profilesBuilt,
                        lastSync = getString(R.string.dashboard_never),
                        isKeyboardConnected = false,
                        onNavigateToSettings = {
                            navController.navigate(CompanionRoutes.SETTINGS)
                        },
                    )
                }

                composable(CompanionRoutes.SETTINGS) {
                    SettingsScreen(
                        reanalyzeFrequency = reanalyzeFrequency,
                        onReanalyzeFrequencyChanged = { reanalyzeFrequency = it },
                        profileDetailLevel = profileDetailLevel,
                        onProfileDetailLevelChanged = { profileDetailLevel = it },
                        onDeleteAllData = { deleteAllData() },
                    )
                }
            }
        }
    }

    @Composable
    private fun CompanionBottomNav(navController: NavHostController) {
        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_dashboard)) },
                selected = true,
                onClick = {
                    navController.navigate(CompanionRoutes.DASHBOARD) {
                        popUpTo(CompanionRoutes.DASHBOARD) { inclusive = true }
                    }
                },
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
                selected = false,
                onClick = {
                    navController.navigate(CompanionRoutes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
            )
        }
    }

    private fun checkPermissions(): Boolean =
        requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    /**
     * Request to become the default SMS app.
     *
     * Uses RoleManager on Android Q+ (API 29+) or the legacy
     * Telephony.Sms.getDefaultSmsPackage approach on older versions.
     */
    private fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                defaultSmsLauncher.launch(intent)
            }
        } else {
            @Suppress("DEPRECATION")
            val intent = android.content.Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            defaultSmsLauncher.launch(intent)
        }
    }

    private fun markOnboardingComplete() {
        onboardingComplete = true
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETE, true)
            .apply()
        Log.i(TAG, "Onboarding marked as complete")
    }

    /**
     * Delete all user data (GDPR compliance).
     *
     * Clears preferences, resets profile count, and will eventually
     * wipe the local database and cached profiles.
     */
    private fun deleteAllData() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
        profilesBuilt = 0
        onboardingComplete = false
        Log.i(TAG, "All user data deleted (GDPR request)")
        // TODO: Clear local database and cached profile data
        recreate()
    }
}
