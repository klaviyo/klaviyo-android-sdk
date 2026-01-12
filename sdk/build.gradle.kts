import de.fayard.refreshVersions.core.versionFor
import java.io.FileInputStream
import java.util.Properties

// Get the readXmlValue function from root project
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Get extra properties from root project
val javaVersion: JavaVersion by rootProject.extra
val publishBuildVariant: String by rootProject.extra

// Read properties from gradle.properties (available via project.properties)
val klaviyoGroupId: String by project
val klaviyoServerUrl: String by project
val klaviyoCdnUrl: String by project
val klaviyoApiRevision: String by project

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")

    configure<com.android.build.gradle.LibraryExtension> {
        compileSdk = project.versionFor("version.android.compileSdk").toInt()
        buildToolsVersion = project.versionFor("version.android.buildTools")

        val localProperties = Properties()
        if (rootProject.file("local.properties").canRead()) {
            localProperties.load(FileInputStream(rootProject.file("local.properties")))
        }
        val serverTarget = localProperties["localKlaviyoServerUrl"] as String? ?: klaviyoServerUrl
        val apiRevision = localProperties["klaviyoApiRevision"] as String? ?: klaviyoApiRevision
        val cdnUrl = localProperties["localKlaviyoCdnUrl"] as String? ?: klaviyoCdnUrl
        val assetSource = localProperties["klaviyoAssetSource"] as String? ?: ""

        defaultConfig {
            minSdk = project.versionFor("version.android.minSdk").toInt()
            @Suppress("DEPRECATION")
            targetSdk = project.versionFor("version.android.targetSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            aarMetadata {
                minCompileSdk = project.versionFor("version.android.minSdk").toInt()
            }
            consumerProguardFiles("consumer-rules.pro")
        }

        buildFeatures {
            buildConfig = true
        }

        compileOptions {
            sourceCompatibility = javaVersion
            targetCompatibility = javaVersion
        }

        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                buildConfigField("String", "KLAVIYO_SERVER_URL", "\"$serverTarget\"")
                buildConfigField("String", "KLAVIYO_API_REVISION", "\"$apiRevision\"")
                buildConfigField("String", "KLAVIYO_CDN_URL", "\"$cdnUrl\"")
                buildConfigField("String", "KLAVIYO_ASSET_SOURCE", "\"$assetSource\"")
            }
            debug {
                buildConfigField("String", "KLAVIYO_SERVER_URL", "\"$serverTarget\"")
                buildConfigField("String", "KLAVIYO_API_REVISION", "\"$apiRevision\"")
                buildConfigField("String", "KLAVIYO_CDN_URL", "\"$cdnUrl\"")
                buildConfigField("String", "KLAVIYO_ASSET_SOURCE", "\"$assetSource\"")
            }
        }

        testOptions {
            unitTests.all {
                // JVM Arguments required for various unit test mocks via Mockk as of Java 17
                it.jvmArgs(
                    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens", "java.base/java.net=ALL-UNNAMED",
                    "--add-opens", "java.base/javax.net.ssl=ALL-UNNAMED",
                    "--add-opens", "java.base/sun.net.www.protocol.https=ALL-UNNAMED",
                    "--add-exports", "java.base/sun.net.www.protocol.https=ALL-UNNAMED"
                )
            }
        }

        lint {
            // Fail the build if any lint errors are found
            abortOnError = true

            // Check release builds for issues
            checkReleaseBuilds = true

            // Treat NewApi errors as fatal (API level compatibility)
            error.add("NewApi")

            // Generate HTML report for local review
            htmlReport = true
        }
    }

    // Configure kotlinOptions
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    dependencies {
        /***** Main Dependencies *****/
        // Kotlin Dependencies
        "implementation"(AndroidX.core.ktx)
        "implementation"(Kotlin.stdlib.jdk8)

        /***** Instrumentation Testing Dependencies *****/
        "androidTestImplementation"(AndroidX.test.runner)
        "androidTestImplementation"(AndroidX.test.espresso.core)
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        verbose.set(true)
        android.set(true)
        ignoreFailures.set(false)
        outputToConsole.set(true)
        outputColorName.set("RED")
        @Suppress("DEPRECATION")
        disabledRules.set(setOf("max-line-length"))
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
            include("**/java/**")
        }
    }

    tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
        dokkaSourceSets.configureEach {
            includeNonPublic.set(false)
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            suppressInheritedMembers.set(true)
            jdkVersion.set(17)
        }
    }
}
