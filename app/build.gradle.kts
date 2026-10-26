import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Resolve Groq API keys from (in order): root `.env`, `local.properties`,
// then the GROQ_API_KEYS / GROQ_API_KEY environment variables. Multiple keys can
// be supplied as a comma-separated list to rotate between them safely.
fun groqApiKeys(): List<String> {
    val parsed = linkedSetOf<String>()

    fun addValues(raw: String?) {
        raw ?: return
        raw.split(',', ';', '\n', '\r')
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotBlank() }
            .forEach { parsed += it }
    }

    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("GROQ_API_KEY=") || trimmed.startsWith("GROQ_API_KEYS=")) {
                addValues(trimmed.substringAfter('='))
            }
        }
    }

    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties()
        localProps.inputStream().use { props.load(it) }
        addValues(props.getProperty("groq.api.key"))
        addValues(props.getProperty("groq.api.keys"))
    }

    addValues(System.getenv("GROQ_API_KEYS"))
    addValues(System.getenv("GROQ_API_KEY"))
    return parsed.toList()
}

fun groqApiKey(): String = groqApiKeys().joinToString(",")

android {
    namespace = "app.drift"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.drift"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "GROQ_API_KEY", "\"${groqApiKey()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tensorflow.lite)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
