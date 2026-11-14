import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase wiring activates automatically the moment google-services.json lands in app/.
// Until then the app builds and runs fully on the LiveRelayBackend.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.closer.app"
    compileSdk = 35

    // Release signing (personal keystore, generated locally)
    val keystoreProps = Properties()
    val keystoreFile = rootProject.file("keystore.properties")
    val signing = if (keystoreFile.exists()) {
        keystoreFile.inputStream().use { keystoreProps.load(it) }
        signingConfigs.create("closer") {
            storeFile = rootProject.file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias = keystoreProps["keyAlias"] as String
            keyPassword = keystoreProps["keyPassword"] as String
        }
    } else null

    defaultConfig {
        applicationId = "com.closer.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signing?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources.excludes += "META-INF/LICENSE*"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Firebase classes are always compiled in; the google-services plugin above only
    // activates with google-services.json. Until then FirebaseApp is never initialized
    // and the app runs on the LiveRelayBackend.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
