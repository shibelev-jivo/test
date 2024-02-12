plugins {
	alias(libs.plugins.androidLibrary).apply(false)
	alias(libs.plugins.androidApplication).apply(false)
	alias(libs.plugins.kotlinAndroid).apply(false)
	alias(libs.plugins.kotlinMultiplatform).apply(false)
	alias(libs.plugins.kotlinCocoapods).apply(false)
	alias(libs.plugins.kover).apply(false)
}

tasks.register("clean", Delete::class) {
	delete(rootProject.buildDir)
}

subprojects {
	afterEvaluate {
		tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
			kotlinOptions {
				jvmTarget = JavaVersion.VERSION_17.toString()
			}
		}
	}
}