import java.io.FileInputStream
import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

fun resolveConfigProperty(key: String, fallback: String = ""): String {
    val envVal = System.getenv(key)?.trim()
    if (!envVal.isNullOrBlank()) {
        return envVal
    }
    val gradleProp = (project.findProperty(key) as? String)?.trim()
    if (!gradleProp.isNullOrBlank()) {
        return gradleProp
    }
    val localProp = localProperties.getProperty(key)?.trim()
    if (!localProp.isNullOrBlank()) {
        return localProp
    }
    return fallback
}

/**
 * Escapes characters for Java string literals generated in BuildConfig.java.
 * Backslash, quotes, newlines, carriage returns, and tabs are escaped.
 * Dollar signs ($) are NOT escaped because '\$' is illegal in Java string literals.
 */
fun escapeJavaStringLiteral(value: String): String {
    val sb = StringBuilder()
    for (ch in value) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '\"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

fun validateVariantFirebase(variantName: String, expectedAppId: String) {
    val configFile = project.file("src/$variantName/google-services.json")
    if (!configFile.exists()) {
        throw GradleException("Missing Firebase configuration: src/$variantName/google-services.json is required for the $variantName build.")
    }
    val content = configFile.readText()
    val slurper = groovy.json.JsonSlurper()
    val parsed = try {
        slurper.parseText(content) as? Map<*, *>
    } catch (e: Exception) {
        throw GradleException("Malformed JSON in src/$variantName/google-services.json.")
    }
    val clientList = parsed?.get("client") as? List<*>
    if (clientList.isNullOrEmpty()) {
        throw GradleException("Firebase configuration in src/$variantName/google-services.json contains no client entries.")
    }
    var foundMatchingPackage = false
    for (item in clientList) {
        val clientMap = item as? Map<*, *>
        val clientInfo = clientMap?.get("client_info") as? Map<*, *>
        val androidClientInfo = clientInfo?.get("android_client_info") as? Map<*, *>
        val packageName = androidClientInfo?.get("package_name") as? String
        if (packageName == expectedAppId) {
            foundMatchingPackage = true
            break
        }
    }
    if (!foundMatchingPackage) {
        throw GradleException("Firebase package mismatch for $variantName: expected '$expectedAppId' in src/$variantName/google-services.json.")
    }
}

android {
    namespace = "com.innly.hotelbooking"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.innly.hotelbooking"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${escapeJavaStringLiteral(resolveConfigProperty("API_BASE_URL", "http://10.0.2.2:8080/api/v1/"))}\"",
            )
            buildConfigField(
                "String",
                "RAZORPAY_KEY_ID",
                "\"${escapeJavaStringLiteral(resolveConfigProperty("RAZORPAY_KEY_ID", "rzp_test_placeholder"))}\"",
            )
            buildConfigField("String", "HTTP_LOG_LEVEL", "\"BASIC\"")
        }
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            resValue("string", "app_name", "Innly Staging")
            val stagingApiUrl = resolveConfigProperty("STAGING_API_BASE_URL", "")
            val stagingRazorpayKey = resolveConfigProperty("STAGING_RAZORPAY_KEY_ID", "")
            buildConfigField("String", "API_BASE_URL", "\"${escapeJavaStringLiteral(stagingApiUrl)}\"")
            buildConfigField("String", "RAZORPAY_KEY_ID", "\"${escapeJavaStringLiteral(stagingRazorpayKey)}\"")
            buildConfigField("String", "HTTP_LOG_LEVEL", "\"BASIC\"")
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseApiUrl = resolveConfigProperty("RELEASE_API_BASE_URL", "")
            val releaseRazorpayKey = resolveConfigProperty("RELEASE_RAZORPAY_KEY_ID", "")
            buildConfigField("String", "API_BASE_URL", "\"${escapeJavaStringLiteral(releaseApiUrl)}\"")
            buildConfigField("String", "RAZORPAY_KEY_ID", "\"${escapeJavaStringLiteral(releaseRazorpayKey)}\"")
            buildConfigField("String", "HTTP_LOG_LEVEL", "\"NONE\"")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("com.google.firebase:firebase-auth-ktx:23.1.0")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")

    implementation("com.razorpay:checkout:1.6.39")

    implementation("javax.inject:javax.inject:1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

tasks.register("validateFirebaseConfigDebug") {
    group = "verification"
    description = "Validates presence and package match of src/debug/google-services.json"
    doLast {
        validateVariantFirebase("debug", "com.innly.hotelbooking")
    }
}

tasks.register("validateFirebaseConfigStaging") {
    group = "verification"
    description = "Validates presence and package match of src/staging/google-services.json"
    doLast {
        validateVariantFirebase("staging", "com.innly.hotelbooking.staging")
    }
}

tasks.register("validateFirebaseConfigRelease") {
    group = "verification"
    description = "Validates presence and package match of src/release/google-services.json"
    doLast {
        validateVariantFirebase("release", "com.innly.hotelbooking")
    }
}

tasks.register("validateStagingEnvironment") {
    group = "verification"
    description = "Validates that staging builds have secure HTTPS endpoints and Razorpay Test keys."
    doLast {
        val stagingApiUrl = resolveConfigProperty("STAGING_API_BASE_URL", "")
        val stagingRazorpayKey = resolveConfigProperty("STAGING_RAZORPAY_KEY_ID", "")

        if (stagingApiUrl.isBlank()) {
            throw GradleException("Staging build failed: STAGING_API_BASE_URL is missing or blank.")
        }

        val uri = try {
            URI(stagingApiUrl)
        } catch (e: Exception) {
            throw GradleException("Staging build failed: STAGING_API_BASE_URL is not a valid URI.")
        }

        if (uri.scheme?.lowercase() != "https") {
            throw GradleException("Staging build failed: STAGING_API_BASE_URL must use HTTPS protocol.")
        }

        if (uri.rawUserInfo != null || uri.userInfo != null) {
            throw GradleException("Staging build failed: STAGING_API_BASE_URL cannot contain user credentials.")
        }

        val host = uri.host?.lowercase()
        if (host.isNullOrBlank()) {
            throw GradleException("Staging build failed: STAGING_API_BASE_URL is missing a valid host.")
        }

        val isForbidden = host == "localhost" || host.endsWith(".localhost") ||
                host == "127.0.0.1" || host.startsWith("127.") ||
                host == "10.0.2.2" || host == "::1" || host == "[::1]" || host == "0.0.0.0" ||
                host == "example.com" || host.endsWith(".example.com") ||
                host == "example.org" || host.endsWith(".example.org") ||
                host == "example.net" || host.endsWith(".example.net")

        if (isForbidden) {
            throw GradleException("Staging build failed: STAGING_API_BASE_URL cannot point to localhost, emulator loopback, or placeholder domains.")
        }

        if (stagingRazorpayKey.isBlank()) {
            throw GradleException("Staging build failed: STAGING_RAZORPAY_KEY_ID is missing or blank.")
        }
        if (!stagingRazorpayKey.startsWith("rzp_test_", ignoreCase = true) || stagingRazorpayKey.contains("placeholder", ignoreCase = true)) {
            throw GradleException("Staging build failed: STAGING_RAZORPAY_KEY_ID must be a valid test key starting with 'rzp_test_'.")
        }
    }
}

tasks.register("validateReleaseEnvironment") {
    group = "verification"
    description = "Validates that release builds have secure, dedicated HTTPS endpoints and live Razorpay keys."
    doLast {
        val releaseApiUrl = resolveConfigProperty("RELEASE_API_BASE_URL", "")
        val releaseRazorpayKey = resolveConfigProperty("RELEASE_RAZORPAY_KEY_ID", "")

        if (releaseApiUrl.isBlank()) {
            throw GradleException("Release build failed: RELEASE_API_BASE_URL is missing or blank. Please configure a valid HTTPS API endpoint in environment or local.properties.")
        }

        val uri = try {
            URI(releaseApiUrl)
        } catch (e: Exception) {
            throw GradleException("Release build failed: RELEASE_API_BASE_URL is not a valid URI.")
        }

        if (uri.scheme?.lowercase() != "https") {
            throw GradleException("Release build failed: RELEASE_API_BASE_URL must use HTTPS protocol.")
        }

        if (uri.rawUserInfo != null || uri.userInfo != null) {
            throw GradleException("Release build failed: RELEASE_API_BASE_URL cannot contain user credentials.")
        }

        val host = uri.host?.lowercase()
        if (host.isNullOrBlank()) {
            throw GradleException("Release build failed: RELEASE_API_BASE_URL is missing a valid host.")
        }

        val isForbidden = host == "localhost" || host.endsWith(".localhost") ||
                host == "127.0.0.1" || host.startsWith("127.") ||
                host == "10.0.2.2" || host == "::1" || host == "[::1]" || host == "0.0.0.0" ||
                host == "example.com" || host.endsWith(".example.com") ||
                host == "example.org" || host.endsWith(".example.org") ||
                host == "example.net" || host.endsWith(".example.net")

        if (isForbidden) {
            throw GradleException("Release build failed: RELEASE_API_BASE_URL cannot point to localhost, emulator loopback, or placeholder domains.")
        }

        if (releaseRazorpayKey.isBlank()) {
            throw GradleException("Release build failed: RELEASE_RAZORPAY_KEY_ID is missing or blank.")
        }
        if (releaseRazorpayKey.startsWith("rzp_test_", ignoreCase = true) || releaseRazorpayKey.contains("placeholder", ignoreCase = true)) {
            throw GradleException("Release build failed: RELEASE_RAZORPAY_KEY_ID cannot use test or placeholder keys.")
        }
        if (!releaseRazorpayKey.startsWith("rzp_live_", ignoreCase = true)) {
            throw GradleException("Release build failed: RELEASE_RAZORPAY_KEY_ID must be a valid live key starting with 'rzp_live_'.")
        }
    }
}

afterEvaluate {
    tasks.findByName("preDebugBuild")?.dependsOn("validateFirebaseConfigDebug")
    tasks.findByName("preStagingBuild")?.dependsOn("validateFirebaseConfigStaging", "validateStagingEnvironment")
    tasks.findByName("preReleaseBuild")?.dependsOn("validateFirebaseConfigRelease", "validateReleaseEnvironment")
}
