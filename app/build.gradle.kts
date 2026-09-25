import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("dagger.hilt.android.plugin")
    id("com.mikepenz.aboutlibraries.plugin.android")
    id("com.google.devtools.ksp")
}

kotlin.compilerOptions.jvmTarget = JvmTarget.fromTarget("21")

android {
    namespace = "com.cliffracertech.soundaura"
    compileSdk = 36
    sourceSets.getByName("androidTest")
        .assets.srcDir("$projectDir/schemas")

    defaultConfig {
        applicationId = "com.cliffracertech.soundaura"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "1.6.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile(
                "proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions.kotlinCompilerExtensionVersion = "1.5.15"

    packaging.resources.excludes += "/META-INF/*"

    testOptions.unitTests.isIncludeAndroidResources = true
}

class RoomSchemaArgProvider(
    @InputDirectory @PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File
): CommandLineArgumentProvider {
    override fun asArguments() = listOf("room.schemaLocation=${schemaDir.path}")
}
ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.optIn.addAll(
        "androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui:1.9.4")
    implementation("androidx.compose.material:material:1.9.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.4")
    implementation("androidx.compose.animation:animation:1.9.4")
    implementation("androidx.compose.animation:animation-graphics:1.9.4")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("com.google.accompanist:accompanist-insets:0.30.1")
    implementation("com.google.accompanist:accompanist-insets-ui:0.36.0")
    implementation("androidx.media:media:1.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.google.dagger:hilt-android:2.57.2")
    implementation("com.mikepenz:aboutlibraries-core:13.1.0")
    implementation("com.mikepenz:aboutlibraries-compose:13.1.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0")
    implementation("sh.calvin.reorderable:reorderable:3.0.0")
    implementation("androidx.media3:media3-exoplayer:1.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    ksp("androidx.room:room-compiler:2.8.3")
    kapt("com.google.dagger:hilt-compiler:2.57.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:truth:1.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling:1.9.4")
    androidTestImplementation("androidx.test.ext:truth:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.room:room-testing:2.8.3")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.57.2")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:2.57.2")
}