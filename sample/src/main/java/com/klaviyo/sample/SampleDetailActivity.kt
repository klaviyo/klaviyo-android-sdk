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

/**
 * SETUP NOTE: A second screen the sample's deep link handler navigates to, so notification taps
 * have a visible destination on top of [SampleActivity] rather than only a toast.
 *
 * This exists to demonstrate the back-stack contract: after tapping a deep link notification, the
 * screen your handler navigated to should still be on top. A toast could not show that, since
 * nothing can pop it off the stack.
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
                            text = "Deep Link Destination",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = deepLink ?: "No deep link provided",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { finish() }) {
                            Text("Back")
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
         * [Intent.FLAG_ACTIVITY_NEW_TASK] is required because the sample's deep link handler is
         * registered from [SampleApplication] and therefore starts this from an application
         * context. Since this Activity uses the app's default task affinity, the flag adds it to
         * the existing task rather than creating a separate one.
         */
        fun intent(context: Context, deepLink: String) =
            Intent(context, SampleDetailActivity::class.java)
                .putExtra(EXTRA_DEEP_LINK, deepLink)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
