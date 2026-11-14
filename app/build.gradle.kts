import java.net.URI
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val versionNameProp = providers.gradleProperty("VERSION_NAME").get()
val versionCodeProp = providers.gradleProperty("VERSION_CODE").get().toInt()
val buildNumber = providers.gradleProperty("BUILD_NUMBER").orElse("0").get().toInt()
val fullVersionCode = versionCodeProp * 10000 + buildNumber

android {
    namespace = "com.anjaz.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anjaz.app"
        minSdk = 24
        targetSdk = 36
        versionCode = fullVersionCode
        versionName = versionNameProp
        buildConfigField("String", "VERSION_NAME", "\"$versionNameProp\"")
        buildConfigField("int", "VERSION_CODE", "$fullVersionCode")
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        val storeFile = System.getenv("SIGNING_STORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

if (file("google-services.json").exists()) {
    pluginManager.apply("com.google.gms.google-services")
}

// مكتبة التعرف على الكلام (Whisper محلياً): تُنزَّل مرة واحدة وتُتحقق ببصمتها، ولا تُحفظ في Git
val sherpaVersion = "1.12.14"
val sherpaSha256 = "d70ebdd8ec18c3bd106c2e95241b19742dfc704c8d026993b6fe9e4aea807ef3"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-$sherpaVersion.aar").asFile
val fetchSherpa by tasks.registering {
    outputs.file(sherpaAar)
    doLast {
        fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        if (!sherpaAar.exists() || sha(sherpaAar) != sherpaSha256) {
            sherpaAar.parentFile.mkdirs()
            URI("https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/sherpa-onnx-$sherpaVersion.aar").toURL().openStream().use { i -> sherpaAar.outputStream().use { i.copyTo(it) } }
            check(sha(sherpaAar) == sherpaSha256) { "بصمة مكتبة sherpa-onnx غير مطابقة" }
        }
    }
}

dependencies {
    implementation(files(sherpaAar).builtBy(fetchSherpa))
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.navigation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play)
    implementation(libs.androidx.work)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
