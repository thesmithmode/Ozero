package ru.ozero.app.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.ozero.app.logging.AppLogger
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.ui.onboarding.OnboardingScreen

private const val TAG = "OnboardingGate"

@Composable
fun OnboardingGate(
    userFlags: UserFlagsRepository,
    main: @Composable () -> Unit,
) {
    var checked by rememberSaveable { mutableStateOf(false) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val completed = runCatching { userFlags.isOnboardingCompleted() }
            .onFailure { AppLogger.w(TAG, "isOnboardingCompleted threw — пропускаю онбординг", it) }
            .getOrDefault(true)
        showOnboarding = !completed
        checked = true
    }
    if (!checked) return
    if (showOnboarding) {
        OnboardingScreen(onCompleted = { showOnboarding = false })
        return
    }
    main()
}
