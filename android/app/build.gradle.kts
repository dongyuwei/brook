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
    // CI passes -PversionName/-PversionCode (e.g. from git tag and run number).
    versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
    versionName = (project.findProperty("versionName") as String?) ?: "1.0"
    // Optional per-device slimming: ./gradlew assembleDebug -Pabi=arm64-v8a
    // (comma-separated list). Without the property all four ABIs are packaged.
    ndk {
      (project.findProperty("abi") as String?)?.split(",")?.let { abiFilters.addAll(it) }
    }
    // Build stamp shown in the app footer: tag-version-hash-date.
    // CI passes -PgitTag/-PgitHash/-PbuildDate; local builds show only the version.
    fun prop(name: String) = (project.findProperty(name) as String?)?.trim().orEmpty()
    buildConfigField("String", "GIT_TAG", "\"${prop("gitTag")}\"")
    buildConfigField("String", "GIT_HASH", "\"${prop("gitHash")}\"")
    buildConfigField("String", "BUILD_DATE", "\"${prop("buildDate")}\"")
  }

  signingConfigs {
    // Release signing from environment (CI decodes the keystore from repo
    // secrets); falls back to the debug key so the APK is always installable.
    create("release") {
      val ksPath = System.getenv("BROOK_KEYSTORE_PATH")
      val ksPass = System.getenv("BROOK_KEYSTORE_PASSWORD")
      val ksAlias = System.getenv("BROOK_KEY_ALIAS")
      if (ksPath != null && ksPass != null && ksAlias != null) {
        storeFile = file(ksPath)
        storePassword = ksPass
        keyAlias = ksAlias
        keyPassword = System.getenv("BROOK_KEY_PASSWORD") ?: ksPass
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = if (System.getenv("BROOK_KEYSTORE_PATH") != null) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
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
