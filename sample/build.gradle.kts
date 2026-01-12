// We use refreshVersions to manage dependencies in this project -- https://splitties.github.io/refreshVersions/
// If you notice unusual dependency version syntax, it's from refreshVersions
import de.fayard.refreshVersions.core.versionFor
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Get extra properties from root project
val javaVersion: JavaVersion by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Read properties from gradle.properties
val klaviyoGroupId: String by project

repositories {
    google()
    mavenCentral()
    // SETUP NOTE: Add Jitpack maven repository in order to install Klaviyo Android SDK from JitPack.
    // Note: It is more typical to use dependencyResolutionManagement block in settings.gradle.
    // For the purposes of the sample app, we put it here so that important steps are in one place,
    // and the sample app dependencies do not affect the main SDK config.
    maven { url = uri("https://jitpack.io") }
}

android {
    namespace = "$klaviyoGroupId.sample"
    buildToolsVersion = versionFor("version.android.buildTools")

    defaultConfig {
        // SETUP NOTE: Set your own application ID here
        applicationId = "$klaviyoGroupId.sample"

        // Klaviyo Android SDK minimum supported Android version is 23
        minSdk = versionFor("version.android.minSdk").toInt()
        targetSdk = versionFor("version.android.targetSdk").toInt()
        compileSdk = versionFor("version.android.compileSdk").toInt()

        versionCode = versionFor("version.klaviyo.versionCode").toInt()
        versionName = readXmlValue("src/main/res/values/strings.xml", "klaviyo_sdk_version_override", project(":sdk:core"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        // Set your klaviyo public API key in your local.properties file in the root directory e.g. klaviyoPublicApiKey=ApiKey
        val localProperties = Properties()
        val localPropertiesFile = file("../local.properties")
        if (localPropertiesFile.exists() && localPropertiesFile.canRead()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }

        val klaviyoPublicApiKey = (localProperties["klaviyoPublicApiKey"] as String?) ?: "KLAVIYO_PUBLIC_API_KEY"

        // Optional: register a Google Maps API key and set it in your local.properties file in the root directory e.g. googleMapsApiKey=ApiKey
        // This just enables a little map UI element in the sample app for testing location features, and is not required for Klaviyo SDK functionality
        val googleMapsApiKey = (localProperties["googleMapsApiKey"] as String?) ?: "GOOGLE_MAPS_API_KEY"

        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "KLAVIYO_PUBLIC_KEY", "\"$klaviyoPublicApiKey\"")
            manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
        }
        debug {
            isDebuggable = true
            buildConfigField("String", "KLAVIYO_PUBLIC_KEY", "\"$klaviyoPublicApiKey\"")
            manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
        }
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = versionFor("version.androidx.compose.compiler")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // SETUP NOTE: Add the Klaviyo Android SDK dependencies.
    // This sample app uses the project dependencies, but you can also use the published SDK version
    // by replacing with the published SDK dependencies, per instructions in the README.
    implementation(project(path = ":sdk:analytics"))
    implementation(project(path = ":sdk:forms"))
    implementation(project(path = ":sdk:push-fcm"))
    implementation(project(path = ":sdk:location"))

    // Note: Developers need not import the core package.
    //  This is included in the sample app only to demonstrate some inner workings of the SDK
    implementation(project(path = ":sdk:core"))

    // See https://splitties.github.io/refreshVersions/ for more info on refreshVersions syntax

    // Firebase dependencies for FCM integration
    implementation(platform(Firebase.bom))
    implementation(Firebase.cloudMessagingKtx)

    // GoogleMaps dependency for map UI integration
    implementation(Google.android.maps.compose)
    implementation(Google.android.playServices.location)

    // Other app and testing dependencies (refreshVersions syntax)
    implementation(platform(AndroidX.compose.bom))
    implementation(AndroidX.core.ktx)
    implementation(AndroidX.lifecycle.runtime.ktx)
    implementation(AndroidX.lifecycle.viewModelCompose)
    implementation(AndroidX.activity.compose)
    implementation(AndroidX.compose.ui)
    implementation(AndroidX.compose.ui.toolingPreview)
    implementation(AndroidX.compose.material)
    implementation(AndroidX.compose.material.icons.extended)
    implementation(AndroidX.compose.material3)
    implementation(AndroidX.appCompat)

    testImplementation(Testing.junit4)

    androidTestImplementation(platform(AndroidX.compose.bom))
    androidTestImplementation(AndroidX.test.ext.junit)
    androidTestImplementation(AndroidX.test.espresso.core)
    androidTestImplementation(AndroidX.compose.ui.testJunit4)
    debugImplementation(AndroidX.compose.ui.tooling)
    debugImplementation(AndroidX.compose.ui.testManifest)
}
