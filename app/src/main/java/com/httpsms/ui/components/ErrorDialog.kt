package com.httpsms.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sevanam.androidsmsgateway.R

/**
 * Reports a request that came back with an error.
 *
 * [message] is the message the request itself returned (the backend's own error
 * body, or the transport failure), so the user sees why it failed instead of a
 * generic "something went wrong".
 */
@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    GatewayDialog(
        title = stringResource(id = R.string.error_dialog_title),
        description = message,
        primaryText = stringResource(id = R.string.dialog_ok),
        onPrimary = onDismiss,
        onDismiss = onDismiss
    )
}
