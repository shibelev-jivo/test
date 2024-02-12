import org.jetbrains.kotlin.gradle.plugin.mpp.BitcodeEmbeddingMode.BITCODE
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.kover)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

val githubProperties = Properties()
githubProperties.load(FileInputStream(rootProject.file("github.properties")))

group = libs.versions.library.group.get()
version = libs.versions.library.version.get()

var testTarget = ""

kotlin {
    testTarget = androidTarget().name
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
//            kotlinOptions {
//                jvmTarget = "1.8"
//            }
        }
    }

    val xcf = XCFramework()
    configure(listOf(iosX64(), iosArm64(), iosSimulatorArm64())) {
        binaries {
            framework {
                //Any dependency you add for ios should be added here using export()
                export(libs.kotlin.stdlib)
                xcf.add(this)
            }
        }
    }

    targets.withType<KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs += listOf("-Xgc=cms")
        }
    }

    cocoapods {
        // val iosDefinitions = libs.versions.ios
        // name = iosDefinitions.basename.get()
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        // authors = iosDefinitions.authors.get()
        version = libs.versions.library.version.get()
        ios.deploymentTarget = "16.0"
        framework {
            baseName = "common"
            isStatic = false
            transitiveExport = true
            embedBitcode(BITCODE)
        }
        specRepos {
            url("https://github.com/user/repo.git") //use your repo here
        }
        publishDir = rootProject.file("./")
    }
    
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlin.stdlib)
        }
        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
        }
        androidMain.dependencies {

        }
        val androidUnitTest by getting {
            dependsOn(androidMain.get())
            dependsOn(commonMain.get())
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain.get())
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                //Add any ios specific dependencies here, remember to also add them to the export block
            }
        }

        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest.get())
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

android {
    namespace = libs.versions.library.group.get()
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    beforeEvaluate {
        libraryVariants.all {
            compileOptions {
                isCoreLibraryDesugaringEnabled = true
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
    dependencies {
//        coreLibraryDesugaring(...)//same as with compileSdk
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 34
    }
}

publishing {
    repositories {
        maven {
            name = "github"
            url = uri("https://maven.pkg.github.com/user/repo")
            credentials {
                username = System.getenv()["MYUSER"]
                password = System.getenv()["MYPAT"]
            }
        }
    }
    val thePublications = listOf(testTarget) + "kotlinMultiplatform"
    publications {
        matching { it.name in thePublications }.all {
            val targetPublication = this@all
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .configureEach { onlyIf { findProperty("isMainHost") == "true" } }
        }
        matching { it.name.contains("ios", true) }.all {
            val targetPublication = this@all
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .forEach { it.enabled = false }
        }
    }
}

afterEvaluate {
    tasks.named("podPublishDebugXCFramework") {
        enabled = false
    }
    tasks.named("podSpecDebug") {
        enabled = false
    }
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }
    tasks.withType<AbstractTestTask>().configureEach {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events("started", "skipped", "passed", "failed")
            showStandardStreams = true
        }
    }
}

val buildIdAttribute = Attribute.of("buildIdAttribute", String::class.java)
configurations.forEach {
    it.attributes {
        attribute(buildIdAttribute, it.name)
    }
}

val moveIosPodToRoot by tasks.registering {
    group = "myGroup"
    doLast {
        val releaseDir = rootProject.file(
            "./release"
        )
        releaseDir.copyRecursively(
            rootProject.file("./"),
            true
        )
        releaseDir.deleteRecursively()
    }
}

tasks.named("podPublishReleaseXCFramework") {
    finalizedBy(moveIosPodToRoot)
}

val publishPlatforms by tasks.registering {
    group = "myGroup"
    dependsOn(
        tasks.named("publishAndroidReleasePublicationToGithubRepository"),
        tasks.named("podPublishReleaseXCFramework")
    )
    doLast {
        exec { commandLine = listOf("git", "add", "-A") }
        exec { commandLine = listOf("git", "commit", "-m", "iOS binary lib for version ${libs.versions.library.version.get()}") }
        exec { commandLine = listOf("git", "push", "origin", "main") }
        exec { commandLine = listOf("git", "tag", libs.versions.library.version.get()) }
        exec { commandLine = listOf("git", "push", "--tags") }
        println("version ${libs.versions.library.version.get()} built and published")
    }
}

val compilePlatforms by tasks.registering {
    group = "myGroup"
    dependsOn(
        tasks.named("compileKotlinIosArm64"),
        tasks.named("compileKotlinIosX64"),
        tasks.named("compileKotlinIosSimulatorArm64"),
        tasks.named("compileReleaseKotlinAndroid")
    )
    doLast {
        println("Finished compilation")
    }
}
