description = "Public analytics API functionality for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

// Get extra properties from root project
val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Read properties from gradle.properties
val klaviyoGroupId: String by project

android {
    namespace = "$klaviyoGroupId.analytics"

    testOptions {
        unitTests.all {
            // The analytics suite accumulates MockK/kotlin-reflect metadata faster than the
            // default 512MB test-worker heap can reclaim it, which drives the worker into a
            // full-GC death spiral before the suite finishes. Give it headroom.
            it.maxHeapSize = "2g"
        }
    }

    publishing {
        singleVariant(publishBuildVariant) {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(project(":sdk:core"))
    implementation(KotlinX.coroutines.core)
    implementation(KotlinX.coroutines.android)
    implementation(AndroidX.work.runtimeKtx)

    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    publishing {
        publications {
            // Creates a Maven publication called "release".
            create<MavenPublication>("release") {
                from(components[publishBuildVariant])
                groupId = klaviyoGroupId
                artifactId = "analytics"
                version = readXmlValue(
                    "src/main/res/values/strings.xml",
                    "klaviyo_sdk_version_override",
                    project(":sdk:core")
                )
            }
        }
    }
}
