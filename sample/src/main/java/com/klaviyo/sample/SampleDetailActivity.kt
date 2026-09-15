package com.klaviyo.sample

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.klaviyo.sample.ui.theme.KlaviyoAndroidSdkTheme

private object DetailUiConstants {
    const val TITLE = "Deep Link Destination"
    const val NO_DEEP_LINK = "No deep link provided"
    const val BACK = "Back"
}

/**
 * SETUP NOTE: A second screen the sample navigates to when a notification tap delivers a deep link,
 * so taps have a visible destination on top of [SampleActivity] rather than only a toast.
 *
 * Started from the `automatic` flavor's `SampleActivity`, which reads the link off the tap Intent
 * via `Klaviyo.getKlaviyoDeepLink`. The handler registered in [SampleApplication] only shows a
 * toast; under `automatic_push_open_tracking` it is not invoked for notification taps at all.
 *
 * This exists to demonstrate the back-stack contract: after tapping a deep link notification, the
 * screen you navigated to should still be on top. A toast could not show that, since nothing can
 * pop it off the stack.
 *
 * Your app does not need an Activity like this — navigate however you already do (a second
 * Activity, a Fragment transaction, a Compose NavController route).
 */
class SampleDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deepLink = intent?.getStringExtra(EXTRA_DEEP_LINK)

        setContent {
            KlaviyoAndroidSdkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = DetailUiConstants.TITLE,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = deepLink ?: DetailUiConstants.NO_DEEP_LINK,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { finish() }) {
                            Text(DetailUiConstants.BACK)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_DEEP_LINK = "deep_link"

        /**
         * Build an intent that opens this screen on top of the app's existing task.
         *
         * Started from an Activity context, so no flags are needed: it stacks onto the caller's
         * task, which is what makes the post-tap back stack observable.
         */
        fun intent(context: Context, deepLink: String) =
            Intent(context, SampleDetailActivity::class.java)
                .putExtra(EXTRA_DEEP_LINK, deepLink)
    }
}
