package com.klaviyo.forms.preview

/**
 * A well-formed forms preview token, matching `[A-Za-z0-9_-]{43}`
 */
internal const val VALID_PREVIEW_TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJ0123-_6"

/**
 * A second well-formed forms preview token, distinct from [VALID_PREVIEW_TOKEN]
 */
internal const val OTHER_PREVIEW_TOKEN = "ZYXWVUTSRQPONMLKJIHGFEDCBAzyxwvutsrqponm987"
