package dev.gwaboard.companion.ui.navigation

/**
 * Navigation routes for the companion app.
 *
 * The onboarding flow (welcome → permissions → default SMS) is linear
 * and only shown on first launch. After onboarding, the app opens
 * directly to the dashboard.
 */
object CompanionRoutes {
    const val WELCOME = "welcome"
    const val PERMISSIONS = "permissions"
    const val DEFAULT_SMS = "default_sms"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}
