description = "In-app forms functionality for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

// Get extra properties from root project
val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Read properties from gradle.properties
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.forms"

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

    implementation(AndroidX.appCompat)
    implementation(AndroidX.webkit)

    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "forms"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project(":sdk:core")
                )
            }
        }
    }
}
