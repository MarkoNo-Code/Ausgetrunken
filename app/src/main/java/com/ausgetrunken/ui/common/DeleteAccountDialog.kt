package com.ausgetrunken.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@Composable
fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Account?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = "Your account will be flagged for deletion and you will no longer be able to sign in. Your data will be preserved for recovery purposes and will be permanently deleted by an administrator at a later time.\n\n" +
                        "Data that will be flagged for deletion:\n" +
                        "• Your profile information\n" +
                        "• All wineyards you've created\n" +
                        "• All wines in your wineyards\n" +
                        "• Your subscription history\n" +
                        "• All other account data\n\n" +
                        "Are you sure you want to flag your account for deletion?",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "FLAG FOR DELETION",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.error,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}