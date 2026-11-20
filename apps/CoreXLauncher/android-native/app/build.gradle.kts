plugins {
    id("com.android.application")
}

android {
    namespace = "com.corex.launcher"

    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.corex.launcher"

        minSdk = 23
        targetSdk = 28

        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../falcon/src/main/java")
            res.srcDir("../falcon/src/main/res")
        }
    }
}