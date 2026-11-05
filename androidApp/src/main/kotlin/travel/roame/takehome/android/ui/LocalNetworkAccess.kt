package travel.roame.takehome.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import travel.roame.takehome.presentation.ExplorerController

private const val LOCAL_NETWORK_RESTRICTED_SINCE = 37

/** Below API 37 the platform doesn't restrict this, so there is nothing to ask for. */
fun hasLocalNetworkAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < LOCAL_NETWORK_RESTRICTED_SINCE ||
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

/**
 * Starts the first fetch only once the permission question is settled. Without the wait, that fetch
 * would be silently blocked and time out behind the dialog. Cached flights show throughout.
 * The saveable flag stops a rotation during the dialog from asking twice.
 */
@Composable
fun StartWithLocalNetworkAccess(controller: ExplorerController) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.start()
    }
    LaunchedEffect(Unit) {
        if (hasLocalNetworkAccess(context) || asked) {
            // After process death `asked` survives but the result callback doesn't, so start regardless.
            controller.start()
        } else {
            asked = true
            request.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}
