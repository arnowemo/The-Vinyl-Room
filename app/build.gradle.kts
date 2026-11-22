import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.thevinylroom"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.thevinylroom"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { compose = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

val decodeRoomBackground by tasks.registering {
    val encodedFile = layout.projectDirectory.file("src/main/assets/room_background.jpg.b64")
    val outputFile = layout.projectDirectory.file("src/main/res/drawable-nodpi/room_background.jpg")
    val coverEncodedFile = layout.projectDirectory.file("src/main/assets/eits_cover.jpg.b64")
    val coverOutputFile = layout.projectDirectory.file("src/main/res/drawable-nodpi/cover_eits.jpg")
    outputs.files(outputFile, coverOutputFile)
    doLast {
        val target = outputFile.asFile
        target.parentFile.mkdirs()
        target.writeBytes(Base64.getDecoder().decode(encodedFile.asFile.readText().trim()))
        val coverTarget = coverOutputFile.asFile
        coverTarget.parentFile.mkdirs()
        coverTarget.writeBytes(java.util.Base64.getDecoder().decode(coverEncodedFile.asFile.readText().trim()))
    }
}

tasks.named("preBuild").configure {
    dependsOn(decodeRoomBackground)
}
