import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties()
val environmentSigning = mapOf(
    "storeFile" to System.getenv("FOCUSPEN_KEYSTORE_FILE"),
    "storePassword" to System.getenv("FOCUSPEN_KEYSTORE_PASSWORD"),
    "keyAlias" to System.getenv("FOCUSPEN_KEY_ALIAS"),
    "keyPassword" to System.getenv("FOCUSPEN_KEY_PASSWORD"),
)
val releaseSigningAvailable = signingPropertiesFile.isFile ||
    environmentSigning.values.all { !it.isNullOrBlank() }
if (releaseSigningAvailable) {
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use(signingProperties::load)
    } else {
        environmentSigning.forEach { (key, value) ->
            signingProperties.setProperty(key, value.orEmpty())
        }
    }
}

android {
    namespace = "io.github.hmqyhm.focuspenpro"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "io.github.hmqyhm.focuspenpro"
        minSdk = 30
        targetSdk = 36
        versionCode = 52
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // The module intentionally targets the verified Android 16 tablet and current vendor APIs.
        disable += setOf(
            "ChromeOsAbiSupport",
            "DiscouragedPrivateApi",
            "GradleDependency",
            "InlinedApi",
            "OldTargetApi",
        )
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.4.0")

    testImplementation("junit:junit:4.13.2")
}
