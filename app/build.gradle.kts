import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "gr.pgetsos.cftunnelupdater"
    compileSdk = 37

    defaultConfig {
        applicationId = "gr.pgetsos.cftunnelupdater"
        minSdk = 24
        targetSdk = 37
        versionCode = providers.gradleProperty("releaseVersionCode").orElse("12").get().toInt()
        versionName = providers.gradleProperty("releaseVersionName").orElse("0.7.1").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val signingFile = rootProject.file("signing.properties")
    val signing = Properties()
    if (signingFile.exists()) signingFile.inputStream().use { signing.load(it) }
    fun signingValue(key: String, env: String): String? = System.getenv(env) ?: signing.getProperty(key)
    val keyPath = signingValue("storeFile", "RELEASE_STORE_FILE")
    signingConfigs {
        if (keyPath != null) create("release") {
            storeFile = rootProject.file(keyPath)
            storePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            if (keyPath != null) signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(libs.recyclerview)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
tasks.register("verifyReleaseSigning") {
    doLast {
        check(System.getenv("RELEASE_STORE_FILE") != null || rootProject.file("signing.properties").exists()) {
            "Configure signing.properties or RELEASE_* environment variables before building a release."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn("verifyReleaseSigning") }
