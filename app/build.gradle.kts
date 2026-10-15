plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "id.kayn.platformer"
    compileSdk = 35
    defaultConfig {
        applicationId = "id.kayn.platformer"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}