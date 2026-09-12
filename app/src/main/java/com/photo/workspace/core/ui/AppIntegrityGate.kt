package com.photo.workspace.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photo.workspace.BuildConfig
import com.photo.workspace.core.security.AppIntegrityChecker
import com.photo.workspace.ui.theme.StudioObsidian
import com.photo.workspace.ui.theme.TextPrimary
import com.photo.workspace.ui.theme.TextSecondary

/**
 * Gates [content] behind an app self-signature check. Debug builds always pass through
 * untouched — otherwise every local Android Studio run (signed with the shared debug
 * keystore) would fail this and you'd never be able to test your own app.
 *
 * Unlike [StoragePermissionGate], this has no "continue anyway" option: the whole point is
 * that a modified/re-signed copy shouldn't just be nagged, it shouldn't run.
 */
@Composable
fun AppIntegrityGate(content: @Composable () -> Unit) {
    if (BuildConfig.DEBUG) {
        content()
        return
    }

    val context = LocalContext.current
    val result = remember { AppIntegrityChecker.verify(context) }

    when (result) {
        is AppIntegrityChecker.Result.Verified,
        is AppIntegrityChecker.Result.NotConfigured -> {
            // NotConfigured (placeholder fingerprint still in place) intentionally does NOT
            // block — otherwise the very first release build you sign would lock you out
            // before you've had a chance to fill in the real fingerprint. Once you set
            // EXPECTED_APP_SIGNATURE_SHA256, mismatches are enforced for real.
            content()
        }
        is AppIntegrityChecker.Result.Mismatch, is AppIntegrityChecker.Result.Error -> {
            IntegrityFailedScreen()
        }
    }
}

@Composable
private fun IntegrityFailedScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.GppBad,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            "App Integrity Check Failed",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "This copy of the app was not signed with the expected certificate. It may have " +
                "been modified or repackaged. Please reinstall from a trusted source.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
