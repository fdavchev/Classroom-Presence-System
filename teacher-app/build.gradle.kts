plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.legacy.kapt) apply false
    id("com.google.gms.google-services") version "4.4.1" apply false
}

allprojects {
    val safeProjectPath = path.replace(':', '_')
    layout.buildDirectory.set(file("${System.getProperty("java.io.tmpdir")}/Teacherapp-NFC-build/$safeProjectPath"))
}