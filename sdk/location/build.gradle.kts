project.description = "Location functionality for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

// Get the readXmlValue function from root project
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Get extra properties from root project
val publishBuildVariant: String by rootProject.extra

// Read properties from gradle.properties
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.location"

    publishing {
        singleVariant(publishBuildVariant) {
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
    publishing {
        publications {
            // Creates a Maven publication called "release".
            register<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "location"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project(":sdk:core")
                )
            }
        }
    }
}
