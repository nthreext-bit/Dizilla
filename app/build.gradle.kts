plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.example.dizillaapp"
    minSdk = 26
    // 34: Android 15+ kenardan kenara (edge-to-edge) zorunluluğu targetSdk 35 ile başlar.
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Özel imza dosyaları repoda bulunmaz (.gitignore). Dosya yoksa Android'in varsayılan
  // debug anahtarı kullanılır; böylece "Keystore file not found" hatası oluşmaz.
  val customDebugKeystore = file("${rootDir}/debug.keystore")
  val customReleaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
  val hasCustomReleaseKey = file(customReleaseKeystorePath).exists() &&
    System.getenv("STORE_PASSWORD") != null && System.getenv("KEY_PASSWORD") != null

  signingConfigs {
    if (hasCustomReleaseKey) {
      create("release") {
        storeFile = file(customReleaseKeystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    if (customDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = customDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Release anahtarı yoksa imzasız release APK üretilir (kurmak için debug APK'yı kullanın).
      if (hasCustomReleaseKey) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    debug {
      signingConfig = if (customDebugKeystore.exists()) {
        signingConfigs.getByName("debugConfig")
      } else {
        signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Kullanılmayan bağımlılıklar (Firebase, Room, Retrofit, Moshi, OkHttp, KSP, Google Services,
// Secrets eklentisi) APK'yı şişirdiği ve Google Play Services gerektirdiği için kaldırıldı.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.webkit)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.core.splashscreen)
  // Cast isteğe bağlı: Play Services yoksa (ör. Raspberry Pi Android TV) kod bunu algılayıp kapatır.
  implementation(libs.play.services.cast.framework)
  implementation(libs.androidx.mediarouter)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
