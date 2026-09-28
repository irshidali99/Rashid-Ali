package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Hosted copy of this policy (required for the Google Play store listing).
 * TODO: publish this text on a public URL (e.g. GitHub Pages) and paste the
 * link here + in Play Console -> App content -> Privacy policy.
 */
const val PRIVACY_POLICY_URL = "https://example.com/duplicate-remover/privacy-policy"

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PrivacyTip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Privacy Policy",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PolicySection(
                        title = "1. Overview",
                        body = "Duplicate Remover (\"the App\") is a 100% offline utility. All scanning, " +
                                "duplicate detection and deletion happens entirely on your device. " +
                                "The App has no internet access and never uploads, transmits, shares " +
                                "or sells any of your files or personal data."
                    )
                    PolicySection(
                        title = "2. Data we access on your device",
                        body = "To find duplicate files, the App reads file metadata (name, size, type " +
                                "and modification date) and file contents (to compute a SHA-256 content " +
                                "hash) for photos, audio, videos and documents on your shared storage. " +
                                "This data never leaves your phone — it is processed in memory and " +
                                "discarded when the scan finishes."
                    )
                    PolicySection(
                        title = "3. Why each permission is used",
                        body = "• Photos & videos access: to scan your media library for identical " +
                                "duplicate images and videos.\n" +
                                "• Audio access: to scan music and recordings for duplicates.\n" +
                                "• All files access (Android 11+): to scan and clean duplicate " +
                                "documents, archives (.zip, .rar) and text files that are not part of " +
                                "the media library.\n" +
                                "Denying a permission only limits which categories can be scanned; the " +
                                "App keeps working with whatever access you grant."
                    )
                    PolicySection(
                        title = "4. Data we store",
                        body = "The App stores only its own settings (theme, scan mode, enabled " +
                                "categories) and anonymous scan statistics (files scanned, duplicates " +
                                "found, space recoverable) in the App's private on-device storage. No " +
                                "account, analytics, advertising or crash-reporting SDK is included."
                    )
                    PolicySection(
                        title = "5. Data sharing & third parties",
                        body = "We do not collect, share or disclose any data to any third party, " +
                                "because no data ever leaves your device."
                    )
                    PolicySection(
                        title = "6. Data deletion",
                        body = "Only the duplicate copies you explicitly select are deleted, and only " +
                                "after your confirmation (including the Android system delete prompt " +
                                "on Android 11+). Uninstalling the App removes its settings and scan " +
                                "history from your device."
                    )
                    PolicySection(
                        title = "7. Children's privacy",
                        body = "The App does not collect any information from anyone, including " +
                                "children under 13."
                    )
                    PolicySection(
                        title = "8. Changes & contact",
                        body = "If this policy changes, the updated version will ship inside the App. " +
                                "Questions: contact the developer through the Play Store listing."
                    )
                    Text(
                        text = "Last updated: September 2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, body: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )
    }
}
