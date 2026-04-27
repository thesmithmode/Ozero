package ru.ozero.app.ui.permission

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberNotificationPermissionState(): NotificationPermission.State {
    val context = LocalContext.current
    val sdk = Build.VERSION.SDK_INT

    if (!NotificationPermission.isApplicable(sdk)) {
        return NotificationPermission.State.NotApplicable
    }

    var asked by rememberSaveable { mutableStateOf(false) }
    var hasGrant by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, NotificationPermission.PERMISSION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasGrant = granted
        asked = true
    }

    val state = NotificationPermission.resolve(sdk, hasGrant, asked)

    LaunchedEffect(state) {
        if (state is NotificationPermission.State.NeedsRequest) {
            launcher.launch(NotificationPermission.PERMISSION)
        }
    }

    return state
}
