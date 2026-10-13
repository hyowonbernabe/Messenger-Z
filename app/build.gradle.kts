plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.messengerz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.messengerz"
        minSdk = 26
        targetSdk = 36
        // CI overrides versionCode via -PverCode (run-number based) so each build
        // outranks the last and Obtainium/Android can update over it. Local builds use the default.
        versionCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: (project.property("zVersionCode") as String).toInt()
        versionName = project.property("zVersion") as String

        // Messenger base version this build is patched onto. CI passes -PmsgrVer=<version>;
        // local builds show "dev". Lets the in-app version line auto-track the base.
        buildConfigField("String", "MESSENGER_VER", "\"${(project.findProperty("msgrVer") as String?) ?: "dev"}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.preference.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Added
    implementation(project(":shared"))
    compileOnly(files("../shared/libs/api-82.jar"))
    implementation(libs.dexkit) // finds obfuscated Messenger methods by string/signature at runtime
    runtimeOnly(libs.androidx.preference.ktx)
}