// Top-level build file where you can add configuration options common to all sub-projects/modules.
import org.w3c.dom.Element
import java.io.FileNotFoundException
import javax.xml.parsers.DocumentBuilderFactory

buildscript {
    // Extra properties shared across the project
    extra["javaVersion"] = JavaVersion.VERSION_11
    extra["publishBuildVariant"] = "release"

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        // NOTE: These are dependencies for the build script itself
        // Do not place your application dependencies here;
        // they belong in the individual module build.gradle.kts files
        classpath(Android.tools.build.gradlePlugin)
        classpath(Kotlin.gradlePlugin)

        // Needed (alongside the google-services.json) to connect to google services and use fcm logic in sample app
        classpath(Google.playServicesGradlePlugin)

        classpath("org.jetbrains.dokka:dokka-gradle-plugin:_")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:_")
    }
}

plugins {
    // Project-wide dependencies
    id("org.jetbrains.dokka")
    id("org.jlleitschuh.gradle.ktlint")
}

dependencies {
    dokkaHtmlMultiModulePlugin("org.jetbrains.dokka:versioning-plugin:_")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

/**
 * Reads a value from an XML file by tag name attribute.
 */
fun readXmlValue(filePath: String, tagName: String, project: Project): String {
    val xmlFile = File(project.projectDir, filePath)
    // Check if the file exists
    if (!xmlFile.exists()) {
        throw FileNotFoundException("The XML file does not exist: ${xmlFile.absolutePath}")
    }

    // Parse the XML file using standard Java XML parsing
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val document = try {
        documentBuilder.parse(xmlFile)
    } catch (e: Exception) {
        throw RuntimeException("Failed to parse XML file: ${xmlFile.absolutePath}. Error: ${e.message}", e)
    }

    // Look for the string element with the specific name attribute
    val strings = document.getElementsByTagName("string")
    for (i in 0 until strings.length) {
        val node = strings.item(i) as Element
        if (node.getAttribute("name") == tagName) {
            return node.textContent
        }
    }

    throw IllegalArgumentException("No string found with the name '$tagName' in the file: ${xmlFile.absolutePath}")
}

// Make readXmlValue accessible to subprojects
extra["readXmlValue"] = ::readXmlValue

tasks.named<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>("dokkaHtmlMultiModule") {
    val versionName = readXmlValue(
        "sdk/core/src/main/res/values/strings.xml",
        "klaviyo_sdk_version_override",
        project
    )
    val oldVersionsDir = layout.buildDirectory.dir("../docs/")
    outputDirectory.set(layout.buildDirectory.dir("../docs/$versionName"))
    includes.from("README.md")

    val versioningConfiguration = """
        {
          "version": "$versionName",
          "olderVersionsDir": "${oldVersionsDir.get().asFile.absolutePath}"
        }
    """.trimIndent()

    pluginsMapConfiguration.set(
        mapOf("org.jetbrains.dokka.versioning.VersioningPlugin" to versioningConfiguration)
    )
}
