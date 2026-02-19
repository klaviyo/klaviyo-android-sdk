description = "Shared interfaces and data types for the Klaviyo Location module"
evaluationDependsOn(":sdk")

val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.location.core"
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
    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "location-core"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project(":sdk:core")
                )
            }
        }
    }
}
