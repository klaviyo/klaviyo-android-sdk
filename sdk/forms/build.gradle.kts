import org.gradle.api.component.AdhocComponentWithVariants
import org.jetbrains.dokka.gradle.DokkaTask

description = "In-app forms functionality for the Klaviyo SDK suite"
evaluationDependsOn(":sdk")

// Get extra properties from root project
val publishBuildVariant: String by rootProject.extra
val readXmlValue: (String, String, Project) -> String by rootProject.extra

// Read properties from gradle.properties
val klaviyoGroupId: String by project

val dokkaJavadoc = tasks.named<DokkaTask>("dokkaJavadoc")
val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    archiveClassifier.set("javadoc")
    from(dokkaJavadoc.flatMap { it.outputDirectory })
    dependsOn(dokkaJavadoc)
}
val dokkaJavadocElements = configurations.create("dokkaJavadocElements") {
    isCanBeConsumed = false
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.JAVADOC))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
    }
    outgoing.artifact(dokkaJavadocJar)
}

android {
    namespace = "$klaviyoGroupId.forms"

    publishing {
        singleVariant(publishBuildVariant) {
            withSourcesJar()
        }
    }
}

dependencies {
    api(project(":sdk:forms-core"))
    implementation(project(":sdk:core"))

    implementation(AndroidX.appCompat)
    implementation(AndroidX.webkit)

    testImplementation(project(":sdk:fixtures"))
}

afterEvaluate {
    (components[publishBuildVariant] as AdhocComponentWithVariants).addVariantsFromConfiguration(
        dokkaJavadocElements
    ) {
        mapToMavenScope("runtime")
        mapToOptional()
    }
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
