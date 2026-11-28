description = "Public Mobile Inbox API for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.inbox.core"
    publishing {
        singleVariant(publishBuildVariant) {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(project(":sdk:core"))
    api(project(":sdk:analytics"))
    api(KotlinX.coroutines.core)
    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "inbox-core"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project(":sdk:core")
                )
            }
        }
    }
}
