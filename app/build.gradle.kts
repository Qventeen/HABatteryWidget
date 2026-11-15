plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Release signing values come from Gradle properties (for example
// ~/.gradle/gradle.properties) or environment variables, never from this file.
// Without all four values the release build is unsigned.
fun signingValue(property: String, env: String): Provider<String> =
  providers.gradleProperty(property).orElse(providers.environmentVariable(env))

val releaseStoreFile = signingValue("habattery.release.storeFile", "HABATTERY_RELEASE_STORE_FILE")
val releaseStorePassword =
  signingValue("habattery.release.storePassword", "HABATTERY_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("habattery.release.keyAlias", "HABATTERY_RELEASE_KEY_ALIAS")
val releaseKeyPassword =
  signingValue("habattery.release.keyPassword", "HABATTERY_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
  listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it.isPresent }

android {
  namespace = "io.lumensync.habattery"
  compileSdk = 36

  defaultConfig {
    applicationId = "io.lumensync.habattery"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (hasReleaseSigning) {
      create("release") {
        storeFile = file(releaseStoreFile.get())
        storePassword = releaseStorePassword.get()
        keyAlias = releaseKeyAlias.get()
        keyPassword = releaseKeyPassword.get()
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      isPseudoLocalesEnabled = true
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }

  androidResources {
    generateLocaleConfig = true
  }

  lint {
    error += setOf("MissingTranslation", "ExtraTranslation", "MissingQuantity", "HardcodedText")
    abortOnError = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Widget, background sync, storage, network
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.datastore)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  // Local tests
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver3)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.glance.appwidget.testing)

  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
