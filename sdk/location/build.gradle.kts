project.description = "Location functionality for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

// Get the readXmlValue function from root project
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Read properties from gradle.properties
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.location"

    flavorDimensions += "permissions"

    productFlavors {
        create("default") {
            dimension = "permissions"
        }
        create("noPermissions") {
            dimension = "permissions"
        }
    }

    publishing {
        singleVariant("defaultRelease") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(project(":sdk:core"))
    implementation(project(":sdk:analytics"))

    implementation(Google.android.playServices.location)
    implementation(KotlinX.coroutines.playServices)

    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    val sdkVersion = readXmlValue(
        "src/main/res/values/strings.xml",
        "klaviyo_sdk_version_override",
        project(":sdk:core")
    )

    publishing {
        publications {
            // Single publication with both variants
            // Default flavor is the main artifact, noPermissions uses a classifier
            create<MavenPublication>("release") {
                from(components["defaultRelease"])
                groupId = klaviyoGroupId
                artifactId = "location"
                version = sdkVersion

                // Add noPermissions AAR as classified artifact
                artifact(tasks.named("bundleNoPermissionsReleaseAar")) {
                    classifier = "noPermissions"
                }
            }
        }
    }
}
