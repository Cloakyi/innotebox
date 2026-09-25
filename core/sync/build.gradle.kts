plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.notizen.core.sync"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// KONVENTION: In diesem Modul sind KEINE Compose-/UI-Abhaengigkeiten erlaubt.
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Die Entitaeten kommen aus der Datenschicht. Umgekehrt gilt das NICHT:
    // `:core:data` weiss nichts von Drive, sonst waere die Datenschicht nicht
    // mehr ohne Netz testbar.
    implementation(project(":core:data"))
    // Fuer withTransaction: Der Abgleich schreibt Notiz und Eintraege in EINEM
    // Zug -- eine halb geschriebene Notiz waere schlimmer als eine ungeschriebene.
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Seit Phase 20 laufen hier die Szenarien mit zwei simulierten Clients
    // (SYNC.md 12): zwei echte Room-Datenbanken gegen ein Drive im Speicher.
    // Dieselben drei Vorkehrungen wie in :core:data (Robolectric, NATIVE
    // SQLite, ASM 9.9), aus denselben Gruenden.
    testImplementation(libs.robolectric)
    testImplementation(libs.room.runtime)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.asm)
    testImplementation(libs.asm.commons)
    testImplementation(libs.asm.util)
}
