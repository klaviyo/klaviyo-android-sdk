description = "Mobile inbox functionality for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra
val klaviyoGroupId: String by project

apply(plugin = "kotlin-kapt")

android {
    namespace = "$klaviyoGroupId.mobileInbox"

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
    implementation(project(":sdk:push-fcm"))
    // Firebase is an impl dep of push-fcm, so not on our compile classpath — needed for KAPT stubs
    compileOnly(platform(Firebase.bom))
    compileOnly(Firebase.cloudMessaging)
    testImplementation(platform(Firebase.bom))
    testImplementation(Firebase.cloudMessaging)

    implementation(AndroidX.room.runtime)
    implementation(AndroidX.room.ktx)
    "kapt"(AndroidX.room.compiler)

    implementation(KotlinX.coroutines.android)

    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "mobile-inbox"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project(":sdk:core")
                )
            }
        }
    }
}
