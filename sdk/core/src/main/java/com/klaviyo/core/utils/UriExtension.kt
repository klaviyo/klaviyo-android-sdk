package com.klaviyo.core.utils

import android.net.Uri
import com.klaviyo.core.Constants.ALLOWED_OPEN_URL_SCHEMES

/**
 * True if this URI's scheme is in [ALLOWED_OPEN_URL_SCHEMES]. Shared by the `push-fcm` and
 * `forms` modules so the `web_url`/`open_url`/form-CTA allowlist gate can never diverge
 * between them.
 */
fun Uri.hasAllowedOpenUrlScheme(): Boolean = scheme?.lowercase() in ALLOWED_OPEN_URL_SCHEMES
