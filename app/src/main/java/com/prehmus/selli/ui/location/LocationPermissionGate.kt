package com.prehmus.selli.ui.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prehmus.selli.R

/** Welche Stufe der Standort-Berechtigung noch fehlt. */
enum class LocationPermissionStep { FOREGROUND, BACKGROUND, GRANTED }

/**
 * Android verlangt Hintergrund-Standort in einem **zweiten, separaten** Systemdialog nach dem
 * Vordergrund-Dialog — beide gleichzeitig anzufragen lehnt es stillschweigend ab. Diese
 * Karte führt deshalb in zwei Schritten mit eigener Erklärung durch die Freigabe.
 *
 * Wird die Freigabe verweigert, bleibt die Karte nutzbar und zeigt die Position der anderen
 * Person weiter — nur die eigene fehlt dann.
 */
@Composable
fun LocationPermissionCard(
    step: LocationPermissionStep,
    onGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var deniedTwice by remember { mutableStateOf(false) }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) onGranted() else deniedTwice = true
    }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Auch ohne Hintergrund-Freigabe läuft das Tracking im Vordergrund weiter —
        // deshalb hier kein Fehlerzustand, nur die Meldung "fertig".
        onGranted()
        if (!granted) deniedTwice = true
    }

    val (title, body, buttonLabel) = when (step) {
        LocationPermissionStep.FOREGROUND -> Triple(
            "Wo seid ihr gerade?",
            "Damit Selli euch beide auf der Karte zeigen kann, braucht die App Zugriff auf " +
                "deinen Standort.",
            "Standort freigeben",
        )
        LocationPermissionStep.BACKGROUND -> Triple(
            "Auch unterwegs mitlaufen",
            "Damit Selli auch mitläuft, wenn das Handy in der Tasche steckt, braucht die " +
                "Freigabe noch ein „Immer erlauben“.",
            "Immer erlauben",
        )
        LocationPermissionStep.GRANTED -> Triple("", "", "")
    }

    if (step == LocationPermissionStep.GRANTED) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.mascot_pondering),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (deniedTwice) {
                Text(
                    text = "Android fragt nicht mehr nach — die Freigabe geht jetzt nur noch " +
                        "über die Systemeinstellungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { context.openAppSettings() }) { Text("Einstellungen öffnen") }
            } else {
                Button(
                    onClick = {
                        when (step) {
                            LocationPermissionStep.FOREGROUND -> foregroundLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                            LocationPermissionStep.BACKGROUND -> backgroundLauncher.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            )
                            LocationPermissionStep.GRANTED -> Unit
                        }
                    },
                ) { Text(buttonLabel) }
            }
        }
    }
}

/** Welche Stufe als nächstes fällig ist — vor API 29 gibt es keine Hintergrund-Berechtigung. */
fun Context.locationPermissionStep(): LocationPermissionStep {
    val foreground = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!foreground) return LocationPermissionStep.FOREGROUND
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return LocationPermissionStep.GRANTED
    if (!hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
        return LocationPermissionStep.BACKGROUND
    }
    return LocationPermissionStep.GRANTED
}

/** Reicht die Berechtigung, um überhaupt Fixes zu bekommen? Hintergrund ist dafür optional. */
fun Context.hasForegroundLocationPermission(): Boolean =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
    )
}
