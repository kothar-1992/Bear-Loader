plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.bearmod.loader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bearmod.loader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false  // equivalent to extractNativeLibs="false"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true 
    }

    lint {
        // Disable some warnings that are not critical for this project
        disable += setOf(
            "UnusedResources",           // Many resources are used conditionally
            "VectorPath",                // Vector paths are from Material Design icons
            "Overdraw",                  // Background overdraw is intentional for theming
            "LockedOrientationActivity", // Portrait orientation is required for this app
            "DiscouragedApi",           // Portrait orientation warnings
            "HardwareIds",              // ANDROID_ID is needed for KeyAuth
            "UseCompoundDrawables"      // Layout structure is intentional
        )

        // Set baseline to track only new issues
        baseline = file("lint-baseline.xml")

        // Don't abort build on lint errors after fixing critical ones
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Security - Using Android Keystore directly (androidx.security.crypto deprecated)
    // implementation(libs.androidx.security.crypto)  // Removed - deprecated APIs

    // Animation and UI enhancements
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.interpolator)
    implementation(libs.lottie)

    // CardView for modern cards
    implementation(libs.androidx.cardview)

    // ConstraintLayout for complex layouts
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    // Mockito-Kotlin helpers (for nicer Kotlin-friendly mocks/stubs) - pinned to 5.1.0
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    // AndroidX Test dependencies required by some JVM tests (ApplicationProvider, AndroidJUnit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit.v115)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Detekt formatting rules (optional but helpful)
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.1")
}

// Detekt configuration
detekt {
    toolVersion = "1.23.1"
    config = files("${project.rootDir}/detekt.yml")
    buildUponDefaultConfig = true
    // Use the generated baseline to ignore historical issues
    baseline = file("${projectDir}/detekt-baseline.xml")
    allRules = false
}