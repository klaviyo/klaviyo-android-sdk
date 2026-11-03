plugins {
    id("com.google.devtools.ksp")
}

description = "Mobile Inbox functionality for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.inbox"

    publishing {
        singleVariant(publishBuildVariant) {
            withSourcesJar()
            withJavadocJar()
        }
    }

    // Exported Room schemas double as the migration test fixtures
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(project(":sdk:inbox-core"))
    implementation(project(":sdk:core"))
    implementation(project(":sdk:push-fcm"))
    implementation(KotlinX.coroutines.core)
    implementation(AndroidX.room.runtime)
    implementation(AndroidX.room.ktx)
    ksp(AndroidX.room.compiler)
    testImplementation(project(":sdk:fixtures"))
    androidTestImplementation(AndroidX.room.testing)
    androidTestImplementation(AndroidX.test.ext.junit)
    androidTestImplementation(KotlinX.coroutines.test)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "inbox"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project(":sdk:core")
                )
            }
        }
    }
}
