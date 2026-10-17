plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.moocengg.harmonica_indicator"
    compileSdk {
        version = release(37)
    }
    buildFeatures {
        buildConfig = true
        resValues = true
    }
    defaultConfig {
        applicationId = "com.moocengg.harmonica_indicator"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.19.5"
        //将版本号同步至strings.xml
        resValue("string", "app_version", versionName!!)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("dev.atsushieno:ktmidi-android:0.12.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}