import com.android.build.api.artifact.SingleArtifact
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
        //   "Alpha n"  Testfassung, öffentlich auf GitHub
        //   "Beta n"   offener Test
        //   "1.0"      die Vollversion, danach 1.1, 1.2 ...
        // Innerhalb der Alpha zaehlt n wie der versionCode. Beginnt die Beta,
        // faengt n wieder bei 1 an, der versionCode laeuft weiter.
        versionCode = 11
        versionName = "Alpha 11"
    }

    // DER RELEASE-SCHLUESSEL liegt ausserhalb des Projekts, nie im Repo.
    // `keystore.properties` im Projektstamm (in .gitignore) nennt ihn:
    //   storeFile=C:/Pfad/zum/innotebox-release.jks
    //   storePassword=...
    //   keyAlias=innotebox
    //   keyPassword=...
    // Auf GitHub baut der Workflow "Veröffentlichen" die Release-APK. Dort
    // kommen dieselben vier Angaben als Umgebungsvariablen RELEASE_STORE_FILE,
    // RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS und RELEASE_KEY_PASSWORD aus den
    // Geheimnissen der Umgebung "release"; eine Datei mit Passwörtern entsteht
    // dort nicht. Fehlt beides, baut `assembleRelease` weiter, nur unsigniert;
    // der Debug-Build ist davon nie betroffen.
    val schluesseldatei = rootProject.file("keystore.properties")
    val umgebung = providers.environmentVariable("RELEASE_STORE_FILE")
    val schluessel: Properties? = when {
        schluesseldatei.exists() -> Properties().apply { schluesseldatei.inputStream().use { load(it) } }
        umgebung.isPresent -> Properties().apply {
            setProperty("storeFile", umgebung.get())
            setProperty("storePassword", providers.environmentVariable("RELEASE_STORE_PASSWORD").get())
            setProperty("keyAlias", providers.environmentVariable("RELEASE_KEY_ALIAS").get())
            setProperty("keyPassword", providers.environmentVariable("RELEASE_KEY_PASSWORD").get())
        }
        else -> null
    }
    if (schluessel != null) {
        signingConfigs {
            create("release") {
                storeFile = file(schluessel.getProperty("storeFile"))
                storePassword = schluessel.getProperty("storePassword")
                keyAlias = schluessel.getProperty("keyAlias")
                keyPassword = schluessel.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (schluessel != null) signingConfig = signingConfigs.getByName("release")
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

    // Hintergrundarbeit: Auto-Archiv und Papierkorb.
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Zuerst nur fuer den AICore-Verfuegbarkeitscheck, inzwischen dauerhaft drin.
    // Google-Anmeldung und Drive-Zugriff.
    implementation(libs.play.services.auth)

    implementation(libs.mlkit.genai.speech.recognition)
    implementation(libs.mlkit.genai.prompt)
    // Uebersetzung, Weg 3: laeuft ohne AICore, laedt seine
    // Sprachmodelle einmal aus dem Netz, deshalb nur hinter dem Netz-Schalter.
    implementation(libs.mlkit.translate)

    testImplementation(libs.junit)
    // Fuer SperreTest: eine echte Einstellungsdatei statt eines
    // Fakes, damit der Test dieselbe Klasse liest wie die App.
    testImplementation(libs.androidx.datastore.preferences)
}

// ./gradlew :app:lizenzen schreibt die Liste fuer die Seite Lizenzen (siehe dort).
apply(from = rootProject.file("gradle/lizenzen.gradle.kts"))

/**
 * Prüft, dass beim Start der App nichts von ML Kit oder Firebase von selbst anläuft.
 *
 * ML Kit bringt einen eigenen ContentProvider mit, der es beim Start jeder App
 * startet; das Manifest nimmt ihn mit tools:node="remove" heraus, damit ML Kit
 * erst nach einer Zustimmung über `MlKitStart` läuft. Diese Prüfung liest bei
 * jedem Bauen das fertig zusammengeführte Manifest und bricht ab, sobald dort
 * wieder ein Provider oder ein Initializer von androidx.startup von ML Kit oder
 * Firebase steht, etwa nach einem Update einer Bibliothek. Ein Dienst von ML Kit
 * darf bleiben: Dienste starten nicht von selbst.
 */
abstract class ManifestPruefung : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun pruefen() {
        val text = manifest.get().asFile.readText()
        val startetVonSelbst = Regex("""<(?:provider|meta-data)\b[^>]*>""").findAll(text)
            .map { it.value }
            .filter { it.startsWith("<provider") || "android:value=\"androidx.startup\"" in it }
            .mapNotNull { Regex("""android:name="([^"]+)"""").find(it)?.groupValues?.get(1) }
        val verboten = startetVonSelbst
            .filter { name -> listOf("mlkit", "firebase").any { it in name.lowercase() } }
            .toList()
        if (verboten.isNotEmpty()) {
            throw GradleException(
                "Beim Start der App liefe von selbst an: " + verboten.joinToString() +
                    ". Im Manifest mit tools:node=\"remove\" herausnehmen.",
            )
        }
    }
}

// MlKitZugangTest liest den Quelltext selbst. Ohne diese Angabe hielte Gradle
// den Test für unverändert, solange sich nur Text ändert, der keine Klasse
// ergibt, und ließe ihn aus.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/java").withPathSensitivity(PathSensitivity.RELATIVE)
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val pruefung = tasks.register<ManifestPruefung>("manifestPruefen$name") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        // Jedes Bauen der App prüft mit, lokal wie auf GitHub.
        tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(pruefung) }
    }
}
