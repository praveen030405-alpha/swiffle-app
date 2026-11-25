package com.swiffle.app.ui.settings

/**
 * Navigation routes within the full-screen Swiffle Settings experience.
 * Inspired by Google Account Center & Android System Settings.
 */
sealed interface SettingsRoute {
    data object Main : SettingsRoute
    data object General : SettingsRoute
    data object Appearance : SettingsRoute
    data object Notifications : SettingsRoute
    data object Intelligence : SettingsRoute
    data object Plugins : SettingsRoute
    data object GoogleCloud : SettingsRoute
    data object Subscriptions : SettingsRoute
    data object UsageLimits : SettingsRoute
    data object About : SettingsRoute
    data object HelpFeedback : SettingsRoute
}
