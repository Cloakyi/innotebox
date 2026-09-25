plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.notizen.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

// Room legt hier die Schema-JSONs ab. Die gehoeren in die Versionskontrolle:
// ohne sie kann Room Migrationen nicht verifizieren, und wir koennen nicht
// nachvollziehen, wie Version N aussah.
//
// DER ORT IST MIT ABSICHT DAS TEST-ASSETS-VERZEICHNIS und nicht ein neutrales
// `schemas/`. `MigrationTestHelper` sucht die Dateien zur Laufzeit ueber den
// AssetManager, nicht im Projektbaum -- sie muessen also als Assets vorliegen.
// Der uebliche Weg dafuer waere
// `sourceSets.getByName("test").assets.srcDir(...)`, aber genau das bricht
// unter AGP 9 mit einem ClassCastException
// (DefaultAndroidLibrarySourceSet_Decorated -> AndroidLibrarySourceSet), schon
// beim Konfigurieren. `src/test/assets` ist ohnehin das Standardverzeichnis
// und braucht keine Registrierung. Wer das zurueckverschiebt, macht den
// Migrationstest wieder unbaubar.
ksp {
    arg("room.schemaLocation", "$projectDir/src/test/assets")
}

// KONVENTION: In diesem Modul sind KEINE Compose-/UI-Abhaengigkeiten erlaubt.
// Der Web-Client teilt diesen Code nicht, aber die Trennung haelt die
// Datenschicht ohne Emulator testbar und den Vertrag in SYNC.md ehrlich.
dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    // Liefert InstrumentationRegistry, das MigrationTestHelper verlangt.
    // Unter Robolectric ist das eine echte, brauchbare Instrumentation.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    // Robolectric bringt ein aelteres ASM mit, das die Klassendateien der
    // JBR 25 nicht lesen kann ("Unsupported class file major version 69").
    // Hier gewinnt die neuere Version. Kann entfallen, sobald Robolectric
    // selbst auf ASM 9.9 oder neuer steht.
    testImplementation(libs.asm)
    testImplementation(libs.asm.commons)
    testImplementation(libs.asm.util)
}
