import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 외부 API 비밀키는 서버 환경변수 전용이다. 앱에는 서버 주소만 포함한다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// CI allocates a strictly increasing distribution code; local builds use 4 and are not releases.
val appVersionCode = providers.environmentVariable("FASHION_VERSION_CODE").orNull?.toIntOrNull() ?: 4
require(appVersionCode in 4..2_100_000_000) { "versionCode must exceed the previous distribution (3)" }
val appVersionName = providers.environmentVariable("FASHION_VERSION_NAME").orNull ?: "1.1-local"
val previewBuild = providers.gradleProperty("fashionPreview").orNull == "true"
// Opt-in device QA has its own Android sandbox; never run storage fixtures in the user's Preview.
val deviceQaBuild = providers.gradleProperty("fashionDeviceQa").orNull == "true"
require(!deviceQaBuild || previewBuild) { "Device QA must retain Preview network isolation" }
// Separate deployment approval; Preview/QA cannot enable production photo uploads.
val outfitServerApproved = !previewBuild && providers.gradleProperty("fashionOutfitServerApproved").orNull == "true"
val packagedVersionName = appVersionName + if (deviceQaBuild) "-qa" else if (previewBuild) "-preview" else ""
require(appVersionName.matches(Regex("[A-Za-z0-9._-]+")))
fun gitValue(vararg args: String) = providers.exec {
    workingDir(rootProject.projectDir.parentFile)
    commandLine("git", *args)
}.standardOutput.asText.get().trim()
val sourceSha = gitValue("rev-parse", "HEAD")
val sourceBranch = gitValue("branch", "--show-current").ifEmpty { "detached" }
// Untracked source/resources can enter an APK; ignored build outputs/secrets still stay excluded.
val sourceDirty = gitValue("status", "--porcelain", "--untracked-files=normal").isNotEmpty()
val buildNumber = providers.environmentVariable("FASHION_BUILD_NUMBER").orNull ?: "local"
require(sourceSha.matches(Regex("[a-f0-9]{40}")))
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val identityDir = layout.buildDirectory.dir("generated/assets/buildIdentity")
val generateBuildIdentity by tasks.registering {
    val identity = """{"versionCode":$appVersionCode,"versionName":${quoted(packagedVersionName)},"gitSha":${quoted(sourceSha)},"branch":${quoted(sourceBranch)},"buildNumber":${quoted(buildNumber)},"sourceDirty":$sourceDirty,"preview":$previewBuild,"deviceQa":$deviceQaBuild}"""
    inputs.property("identity", identity)
    outputs.dir(identityDir)
    doLast {
        identityDir.get().asFile.apply { mkdirs() }.resolve("build_identity.json").writeText(identity)
    }
}

android {
    namespace = "com.jeiu.fashionproject"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jeiu.fashionproject" + if (deviceQaBuild) ".preview.qa" else if (previewBuild) ".preview" else ""
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = packagedVersionName
        resValue("string", "build_app_name", if (deviceQaBuild) "FashionAI QA P16" else if (previewBuild) "FashionAI Preview" else "FashionAI")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 서버(FastAPI) 기본 주소 = Railway 에 올린 서버 (어디서든 접속 가능).
        // 내 PC 서버로 개발할 때는 local.properties 에 SERVER_URL=http://10.0.2.2:8000 (에뮬레이터) 또는
        // SERVER_URL=http://PC의IP:8000 (실제 폰)을 넣거나, 앱 마이페이지 > 서버 주소 에서 바꾼다.
        buildConfigField("String", "SERVER_URL", "\"${localProperties.getProperty("SERVER_URL", "https://fashion-backend-production-aba0.up.railway.app")}\"")
        buildConfigField("String", "GIT_SHA", quoted(sourceSha))
        buildConfigField("String", "BUILD_BRANCH", quoted(sourceBranch))
        buildConfigField("String", "BUILD_NUMBER", quoted(buildNumber))
        buildConfigField("boolean", "SOURCE_DIRTY", sourceDirty.toString())
        buildConfigField("boolean", "PREVIEW_BUILD", previewBuild.toString())
        buildConfigField("boolean", "DEVICE_QA_BUILD", deviceQaBuild.toString())
        buildConfigField("boolean", "SERVER_OUTFIT_APPROVED", outfitServerApproved.toString())


    }

    buildTypes {
        debug {
            // Only explicit signing material may be used for a distributed CI APK.
            System.getenv("ANDROID_DISTRIBUTION_KEYSTORE")?.let { path ->
                require(previewBuild) {
                    "Preview/QA distribution key cannot sign the primary package; a separately approved Release key and policy are required"
                }
                signingConfig = signingConfigs.getByName("debug").apply {
                    storeFile = file(path)
                    storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: "androiddebugkey"
                    keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                }
            }
        }
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    androidResources {
        // .tflite 모델은 압축되면 MediaPipe가 로딩하지 못하므로 압축 제외
        noCompress += "tflite"
    }
    // AGP 9 requires a concrete directory here; preBuild carries the generator dependency.
    sourceSets.getByName("main").assets.srcDir(identityDir.get().asFile)
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}
tasks.named("preBuild") { dependsOn(generateBuildIdentity) }

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
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    // 내일 날씨 알림 (매일 저녁 8시쯤 백그라운드 작업)
    implementation(libs.androidx.work.runtime.ktx)
    // Force a 16KB-page-aligned release; the version transitively pulled in by
    // compose-ui (1.0.1) ships an unaligned libandroidx.graphics.path.so.
    implementation(libs.androidx.graphics.path)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
