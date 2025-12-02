package com.github.opentube.error

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug activity that displays crash reports to the user in a modern Jetpack Compose interface.
 * This activity provides functionality for viewing crash details, copying the report to clipboard,
 * sharing the report with other applications, and restarting the application.
 *
 * The crash report is passed through the intent with the key "CRASH_REPORT".
 *
 * Licensed under GNU General Public License (GPL) version 3 or later.
 */
class DebugActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashReport = intent.getStringExtra("CRASH_REPORT") ?: "No crash report available"

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrashReportScreen(
                        crashReport = crashReport,
                        onCopyClick = { copyToClipboard(crashReport) },
                        onShareClick = { shareCrashReport(crashReport) },
                        onRestartClick = { restartApplication() },
                        onCloseClick = { closeApplication() }
                    )
                }
            }
        }
    }

    /**
     * Copies the crash report to the system clipboard and displays a confirmation toast.
     *
     * @param crashReport the crash report text to copy
     */
    private fun copyToClipboard(crashReport: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Crash Report", crashReport)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Crash report copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    /**
     * Shares the crash report using the Android share sheet, allowing the user to send the
     * report through email, messaging apps, or other sharing methods.
     *
     * @param crashReport the crash report text to share
     */
    private fun shareCrashReport(crashReport: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "OpenTube Crash Report")
            putExtra(Intent.EXTRA_TEXT, crashReport)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Crash Report"))
    }

    /**
     * Attempts to restart the application by launching the main activity with a clean task stack.
     * If the restart fails, the application will be closed instead.
     */
    private fun restartApplication() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            } else {
                closeApplication()
            }
        } catch (e: Exception) {
            closeApplication()
        }
    }

    /**
     * Closes the application completely by finishing the activity and terminating the process.
     */
    private fun closeApplication() {
        finish()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

/**
 * Main composable function that displays the crash report screen with a modern material design
 * interface. The screen includes a header with an error icon, the crash report content in a
 * scrollable card, and action buttons for managing the crash.
 *
 * @param crashReport the full crash report text to display
 * @param onCopyClick callback invoked when the copy button is clicked
 * @param onShareClick callback invoked when the share button is clicked
 * @param onRestartClick callback invoked when the restart button is clicked
 * @param onCloseClick callback invoked when the close button is clicked
 */
@Composable
fun CrashReportScreen(
    crashReport: String,
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit,
    onRestartClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CrashReportHeader()

        Spacer(modifier = Modifier.height(24.dp))

        CrashReportCard(
            crashReport = crashReport,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        ActionButtons(
            onCopyClick = onCopyClick,
            onShareClick = onShareClick,
            onRestartClick = onRestartClick,
            onCloseClick = onCloseClick
        )
    }
}

/**
 * Displays the header section of the crash report screen with an error icon and title text.
 * This section provides immediate visual feedback that an application error has occurred.
 */
@Composable
fun CrashReportHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(40.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Crash Icon",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Application Crashed",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "The application encountered an unexpected error",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Displays the crash report content in a scrollable card with monospace font for better
 * readability of stack traces and technical information. The card uses material design
 * elevation to provide visual hierarchy.
 *
 * @param crashReport the full crash report text to display
 * @param modifier the modifier to be applied to the card
 */
@Composable
fun CrashReportCard(crashReport: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Crash Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = crashReport,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

/**
 * Displays the action buttons for managing the crash report. The buttons are arranged in a
 * grid layout with primary actions (copy and share) in the first row and secondary actions
 * (restart and close) in the second row.
 *
 * @param onCopyClick callback invoked when the copy button is clicked
 * @param onShareClick callback invoked when the share button is clicked
 * @param onRestartClick callback invoked when the restart button is clicked
 * @param onCloseClick callback invoked when the close button is clicked
 */
@Composable
fun ActionButtons(
    onCopyClick: () -> Unit,
    onShareClick: () -> Unit,
    onRestartClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCopyClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "📋 Copy")
            }

            OutlinedButton(
                onClick = onShareClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "📤 Share")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRestartClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Restart")
            }

            Button(
                onClick = onCloseClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Close")
            }
        }
    }
}

/**
 * Application theme wrapper that provides Material Design 3 theming for the debug activity.
 * This composable should be replaced with your application's actual theme implementation
 * if you have a custom theme defined elsewhere in your project.
 *
 * @param content the composable content to be themed
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}