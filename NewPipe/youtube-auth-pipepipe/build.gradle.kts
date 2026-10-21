plugins { id("com.android.library") }
android {
    namespace = "net.slooopipe.auth.pipepipe"
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { api(project(":youtube-auth-api")) }
