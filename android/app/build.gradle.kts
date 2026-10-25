plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.txthinking.brookvpn"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.txthinking.brookvpn"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    // Optional per-device slimming: ./gradlew assembleDebug -Pabi=arm64-v8a
    // (comma-separated list). Without the property all four ABIs are packaged.
    ndk {
      (project.findProperty("abi") as String?)?.split(",")?.let { abiFilters.addAll(it) }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    // The four tun2socks/gVisor .so files are large; keep the APK uncompressed
    // so Android can mmap them directly instead of extracting.
    jniLibs {
      useLegacyPackaging = false
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // brook Go core + TUN-to-SOCKS5 bridge (built by build-aar.sh)
  implementation(files("libs/brook.aar"))

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
}
