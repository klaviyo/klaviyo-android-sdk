description = "Core featureset of the Klaviyo SDK including SDK configuration, session tracking and analytics"
evaluationDependsOn(":sdk")

// Get extra properties from root project
val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Read properties from gradle.properties
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.core"

    publishing {
        singleVariant(publishBuildVariant) {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(KotlinX.coroutines.core)
    implementation(KotlinX.coroutines.android)

    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "core"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project
                )
            }
        }
    }
}
