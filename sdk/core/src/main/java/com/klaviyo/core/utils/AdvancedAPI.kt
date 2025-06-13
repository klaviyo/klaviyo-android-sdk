package com.klaviyo.core.utils

@RequiresOptIn(
    message = """
        This is an advanced API requiring explicit opt-in. 
        It may be changed or removed in future releases.
        See documentation comments for more information.
        """,
    level = RequiresOptIn.Level.ERROR
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR
)
annotation class AdvancedAPI
