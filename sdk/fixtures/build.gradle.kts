/**
 * fixtures is exactly what it sounds like:
 * a module for sharing test fixtures without duplicating effort.
 *
 * This follows a slightly unconventional pattern in that testing code is part of the main package
 * however as long as this module is only depended upon with `testImplementation` there's no leakage
 * of test code into production environment. I adopted this strategy from here
 * https://blog.danlew.net/2022/08/16/sharing-code-between-test-modules/
 *
 * As a side note, "test fixtures" in gradle are now meant to be a first class feature in gradle
 * using `java-test-fixtures` but this method does not yet work in android+kotlin projects,
 * according to my research and testing as of Feb 2023.
 */
description = "Shared test fixtures for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

// Read properties from gradle.properties
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.fixtures"
}

dependencies {
    // Depends upon core interfaces for mocking/fixtures
    implementation(project(":sdk:core"))
    implementation(project(":sdk:analytics"))
    implementation(KotlinX.coroutines.core)

    /***** Testing Dependencies *****/
    api(Testing.junit4)
    api(AndroidX.test.runner)
    api(AndroidX.test.rules)
    api(KotlinX.coroutines.test)
    api(Testing.mockK)

    // JSON is bundled with the Android SDK so we need real objects to test against
    api("org.json:json:_")
}
