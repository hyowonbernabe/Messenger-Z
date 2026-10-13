plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.facebookz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.facebookz"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("verCode") as String?)?.toIntOrNull() ?: (project.property("zVersionCode") as String).toInt()
        versionName = project.property("zVersion") as String
        // Facebook base version this build is patched onto (CI passes -PfbVer).
        buildConfigField("String", "FACEBOOK_VER", "\"${(project.findProperty("fbVer") as String?) ?: "dev"}\"")
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    compileOnly(files("../shared/libs/api-82.jar"))
    implementation(libs.dexkit)
    implementation(project(":shared"))
}
