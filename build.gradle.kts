import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(24)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm(providers.gradleProperty("platformVersion").get())
        bundledPlugin("com.jetbrains.php")
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.brighterly.experiments"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
        changeNotes = """
            <h3>1.2.0</h3>
            <ul>
                <li><b>Experiments from environment URLs</b> — fetch A/B experiments from per-environment config URLs (development, staging, sandbox, production, local) instead of local files.</li>
                <li><b>Environment switcher in the status bar</b> — pick the active environment and re-fetch with <b>Sync now</b>; the widget shows the active environment and experiment count.</li>
                <li><b>Auto-sync</b> on IDE startup and whenever you switch environments.</li>
                <li><b>Local caching</b> — fetched configs are cached on disk so Ctrl+click navigation and Find Usages keep working.</li>
                <li><b>Local file configs are now deprecated</b> — hidden by default; re-enable under <b>Settings → Tools → AB Tests</b> ("Show experiments from local files"). On a key conflict, the active environment URL wins.</li>
                <li>Compatibility extended to PhpStorm build 261.</li>
            </ul>
        """.trimIndent()
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
