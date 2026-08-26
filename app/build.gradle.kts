import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun buildConfigStringProperty(name: String): String {
    val value = localProperties.getProperty(name, "")
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

android {
    namespace = "com.prehmus.selli"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prehmus.selli"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            buildConfigStringProperty("selli.googleServerClientId"),
        )
        buildConfigField(
            "String",
            "ICS_FEED_URL",
            buildConfigStringProperty("selli.icsFeedUrl"),
        )
        buildConfigField(
            "String",
            "PLACES_API_KEY",
            buildConfigStringProperty("selli.placesApiKey"),
        )
        buildConfigField(
            "String",
            "MELLI_ICS_FEED_URL",
            buildConfigStringProperty("selli.melliIcsFeedUrl"),
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildConfigStringProperty("selli.supabaseUrl"),
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            buildConfigStringProperty("selli.supabaseAnonKey"),
        )

        // Die Maps SDK liest ihren Schlüssel aus dem Manifest, nicht aus BuildConfig.
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("selli.mapsApiKey", "")
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
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.calendar)
    implementation(libs.google.http.client.gson)
    implementation(libs.gson)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.maps.compose)
    implementation(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
