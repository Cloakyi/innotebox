import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.notizen.app"
    compileSdk = 37

    defaultConfig {
        // ACHTUNG: Bei Google Play ist die applicationId UNWIDERRUFLICH -- einmal
        // veroeffentlicht, laesst sie sich nie mehr aendern. Sie muss ausserdem
        // zeichengenau mit dem Paketnamen der OAuth-Client-ID uebereinstimmen,
        // sonst weist Google jede Anmeldung ab, ohne zu sagen warum.
        //
        // `namespace` oben bleibt bewusst `de.notizen.app`: Das ist der
        // Code-Paketname, er steht in jedem `package`-Kopf und geht niemanden
        // draussen etwas an. Beide gleichzusetzen haette bedeutet, jede
        // Quelldatei zu verschieben -- fuer nichts.
        applicationId = "de.innotebox.app"
        minSdk = 31

        // Das Zielgeraet laeuft Android 17 (API 37) -- verifiziert am
        // 2026-08-20 auf dem Pixel 10. targetSdk 37 entspricht damit sowohl
        // der neuesten stabilen API als auch dem Geraet, auf dem die App
        // tatsaechlich laeuft.
        targetSdk = 37

        // Steigt mit jeder Schemaaenderung und ist mindestens die Room-Version
        // (docs/ENTSCHEIDUNGEN.md, Abschnitt 11). Bei gleicher Nummer spielte Androids
        // Sicherung am 2026-09-18 eine Datenbank auf Stand 6 in einen Build mit
        // Stand 5 zurueck, und die App startete nicht mehr.
        //
        // Der Name, den man in den Einstellungen und in Androids App-Info sieht,
        // traegt die Stufe und eine Zaehlung (seit 2026-09-19):
        //   "Alpha n"  geschlossener Test mit wenigen Personen
        //   "Beta n"   offener Test
        //   "1.0"      die Vollversion, danach 1.1, 1.2 ...
        // Innerhalb der Alpha zaehlt n wie der versionCode. Beginnt die Beta,
        // faengt n wieder bei 1 an, der versionCode laeuft weiter.
        versionCode = 8
        versionName = "Alpha 8"
    }

    // DER RELEASE-SCHLUESSEL (Phase 21) liegt beim Nutzer, nie im Projekt.
    // `keystore.properties` im Projektstamm (in .gitignore) nennt ihn:
    //   storeFile=C:/Pfad/zum/innotebox-release.jks
    //   storePassword=...
    //   keyAlias=innotebox
    //   keyPassword=...
    // Fehlt die Datei, baut `assembleRelease` weiter, nur unsigniert; der
    // Debug-Build ist davon nie betroffen.
    val schluesseldatei = rootProject.file("keystore.properties")
    if (schluesseldatei.exists()) {
        val eigenschaften = Properties().apply { schluesseldatei.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = file(eigenschaften.getProperty("storeFile"))
                storePassword = eigenschaften.getProperty("storePassword")
                keyAlias = eigenschaften.getProperty("keyAlias")
                keyPassword = eigenschaften.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (schluesseldatei.exists()) signingConfig = signingConfigs.getByName("release")
            // Nur arm64: ML Kit bringt seine Bibliothek fuer vier
            // Prozessorarten mit, 63 MB, davon braucht ein Handy eine. Jedes
            // Android-Geraet seit Jahren ist arm64; der Debug-Build bleibt
            // universell, falls doch einmal ein Emulator gebraucht wird.
            ndk { abiFilters += listOf("arm64-v8a") }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Hintergrundarbeit: Auto-Archiv und Papierkorb (Phase 8d).
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Nur fuer den AICore-Verfuegbarkeitscheck aus Phase 0.
    // Bleiben ab Phase 7 dauerhaft drin.
    // Google-Anmeldung und Drive-Zugriff (Phase 9).
    implementation(libs.play.services.auth)

    implementation(libs.mlkit.genai.speech.recognition)
    implementation(libs.mlkit.genai.prompt)
    // Uebersetzung, Weg 3 (Phase 16): laeuft ohne AICore, laedt seine
    // Sprachmodelle einmal aus dem Netz, deshalb nur hinter dem Netz-Schalter.
    implementation(libs.mlkit.translate)

    testImplementation(libs.junit)
    // Fuer SperreTest (Phase 19): eine echte Einstellungsdatei statt eines
    // Fakes, damit der Test dieselbe Klasse liest wie die App.
    testImplementation(libs.androidx.datastore.preferences)
}
