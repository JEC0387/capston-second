import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// NAVER API HUB(NCP) 인증키는 git에 올라가는 소스에 두지 않고, gitignore된
// local.properties에서만 읽어온다. (local.properties에 NAVER_API_KEY_ID /
// NAVER_API_KEY 두 줄을 추가해야 쇼핑 탭 검색 · 쇼핑인사이트가 동작한다.)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.jeiu.fashionproject"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jeiu.fashionproject"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // NAVER API HUB(NCP) — 데이터랩 쇼핑인사이트 + 이미지 검색이 같은 키를 쓴다.
        // (옛 developers.naver.com 쇼핑 검색 API는 종료되어 더 못 쓴다.)
        buildConfigField(
            "String", "NAVER_API_KEY_ID",
            "\"${localProperties.getProperty("NAVER_API_KEY_ID", "")}\""
        )
        buildConfigField(
            "String", "NAVER_API_KEY",
            "\"${localProperties.getProperty("NAVER_API_KEY", "")}\""
        )
        // FASHN AI 가상피팅 API 키 (https://app.fashn.ai → API). local.properties에 FASHN_API_KEY=... 한 줄 추가.
        buildConfigField(
            "String", "FASHN_API_KEY",
            "\"${localProperties.getProperty("FASHN_API_KEY", "")}\""
        )
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.play.services.location)
    implementation(libs.mlkit.pose.detection.accurate)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    // Force a 16KB-page-aligned release; the version transitively pulled in by
    // compose-ui (1.0.1) ships an unaligned libandroidx.graphics.path.so.
    implementation(libs.androidx.graphics.path)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
