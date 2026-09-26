# InNoteBox

Eine Notizen-App für Android nach einer einfachen Idee: **Notizen sind eine Arbeitsfläche,
kein Archiv.** Alles Neue landet im Eingang, ohne Titel und ohne Nachdenken. Womit du
arbeitest, liegt im Workspace. Was ruht, räumt die App nachts ins Archiv, mit Rückgängig am
Morgen, und die Suche findet es wieder. Warum das so ist und wie die App gebaut ist, steht in
[docs/ENTSCHEIDUNGEN.md](docs/ENTSCHEIDUNGEN.md).

Webseite: [innotebox.de](https://innotebox.de)

## Was drin ist

- Drei Stufen (Eingang, Workspace, Archiv) mit Wischgesten, dazu ein umschaltbarer
  Ordnermodus mit eigenem Archiv und Papierkorb
- Text mit Auszeichnung, Listen zum Abhaken mit Bearbeitungszustand, Bilder auch als
  Hintergrund einer Karte; Fotos verlieren beim Einfügen ihre Metadaten samt Standort
- Sprachnotizen: aufnehmen, später auf dem Gerät in Text umwandeln, Wellenform, Pausen beim
  Abhören überspringen
- Volltextsuche über Titel, Text, Listen und Transkripte, mit Filtern nach Stufe, Tag,
  Farbe, Ordner und Papierkorb
- Tags, Farben, Favoriten, Erinnerungen auf die Minute (auch im Kalender des Geräts)
- KI nur auf dem Gerät (Titelvorschlag, Umwandeln in Text, Aufbereiten von Transkripten),
  erst nach Zustimmung und jederzeit abschaltbar
- Übersetzung über das System, die KI auf dem Gerät oder ML Kit; letzteres nur nach
  ausdrücklicher Zustimmung, weil es Sprachpakete aus dem Netz lädt
- Abgleich zwischen Geräten ausschließlich in das eigene Google Drive, in einen Ordner, den
  die App anlegt (`drive.file`); Sicherung als eine Datei (`.notesbak`), die du in der Hand hast
- Sperre mit Fingerabdruck, Gesicht oder Bildschirmsperre, Aufnahmeschutz gegen
  Bildschirmfotos
- Kein eigener Server, kein Konto bei uns, keine Werbung, keine Auswertung durch uns

Die App läuft ohne Netz. Auf Geräten ohne Google-Dienste fallen KI, Übersetzung über
ML Kit und der Drive-Abgleich weg; alles andere funktioniert unverändert.

## Stand

Alpha, für Android ab Version 12. Die fertige App gibt es als signierte APK unter
[Releases](https://github.com/Cloakyi/innotebox/releases); was sich je Fassung geändert hat,
steht in [CHANGELOG.md](CHANGELOG.md). Offen ist noch die Verschlüsselung der Datenbank.

Impressum und Datenschutzerklärung stehen auf der Webseite:
[innotebox.de/impressum](https://innotebox.de/impressum),
[innotebox.de/datenschutz](https://innotebox.de/datenschutz).

## Selbst bauen

Voraussetzungen: Android Studio mit dem mitgelieferten JDK (JetBrains Runtime), Android
SDK mit API 37.

```bash
./gradlew assembleDebug
```

Die Tests laufen ohne Gerät:

```bash
./gradlew testDebugUnitTest
```

Die Liste der Bibliotheken für die Seite „Lizenzen" in der App entsteht mit

```bash
./gradlew :app:lizenzen
```

und wird nach jeder Änderung an den Abhängigkeiten neu erzeugt.

Für eine Release-Fassung braucht es einen eigenen Signaturschlüssel und eine Datei
`keystore.properties` im Projektstamm (siehe `app/build.gradle.kts`); ohne sie baut Release
unsigniert.

## Aufbau

- `app/`: Oberfläche (Jetpack Compose, Material 3), Aufnahme, KI, Übersetzung, Sperre
- `core/data/`: Datenbank (Room), Modelle, Repositories, Einstellungen
- `core/sync/`: Abgleich mit Google Drive, Sicherung

Die Entscheidungen hinter dem Code stehen in [docs/ENTSCHEIDUNGEN.md](docs/ENTSCHEIDUNGEN.md),
das Datenformat für Abgleich und Sicherung in [SYNC.md](SYNC.md). Beides ist auch der
Vertrag für einen zweiten Client, der denselben Drive-Ordner liest und schreibt.

## Beitragen

Fehlerberichte und Vorschläge sind als Issue willkommen. Wer mittesten möchte, findet in
[TESTEN.md](TESTEN.md) eine Liste zum Abhaken, vom Schnelltest in zehn Minuten bis zu jedem
Bereich der App. Wie Beiträge lizenziert werden und was vor einem Pull Request zu prüfen ist,
steht in [CONTRIBUTING.md](CONTRIBUTING.md).

## Lizenz

Der Code von InNoteBox steht unter der GNU General Public License, Version 3, und nur unter
dieser Fassung (SPDX: `GPL-3.0-only`), siehe [LICENSE](LICENSE). Copyright 2026 Cloak Studio.

Die App enthält Bibliotheken von Google, die keine freie Software sind: die Play-Dienste für
die Anmeldung bei Google Drive und ML Kit für Spracherkennung, KI und Übersetzung. Die GPL
allein erlaubt es nicht, GPL-Code zusammen mit solchen Bibliotheken weiterzugeben. Deshalb
gilt eine zusätzliche Erlaubnis nach Abschnitt 7 der GPL; der Wortlaut steht in
[ZUSATZERLAUBNIS.md](ZUSATZERLAUBNIS.md).

### Was im Repo nicht unter der GPL steht

| Datei | Was | Lizenz |
|---|---|---|
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` | Gradle Wrapper | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| `app/src/main/res/font/google_sans_flex.ttf` | Schrift Google Sans Flex, unverändert aus [Google Fonts](https://github.com/google/fonts/tree/main/ofl/googlesansflex) | [SIL Open Font License 1.1](gradle/lizenztexte/ofl-1.1.txt) |
| `gradle/lizenztexte/` | die Lizenztexte selbst (Apache 2.0, OFL 1.1) | unverändert, wie von den Herausgebern |

Google, Google Sans und Google Sans Flex sind Marken der Google LLC; InNoteBox steht in
keiner Verbindung zu Google.

### Bibliotheken und Dienste anderer

Die fertige App enthält Bibliotheken anderer Hersteller. Sie liegen nicht im Repo; Gradle lädt
sie beim Bauen. Für sie gelten ihre eigenen Lizenzen:

| Teil | Wofür | Lizenz |
|---|---|---|
| AndroidX, Jetpack Compose, Material 3, Room, WorkManager, DataStore, Hilt, Dagger | Oberfläche, Datenbank, Hintergrundarbeit | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Kotlin, kotlinx.coroutines, kotlinx.serialization, OkHttp | Sprache, Nebenläufigkeit, Netz | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Google Play-Dienste (Anmeldung) | Anmeldung bei Google Drive | [Android Software Development Kit License](https://developer.android.com/studio/terms) |
| ML Kit (Spracherkennung, Prompt API, Übersetzung) | Umwandlung in Text, KI auf dem Gerät, Übersetzung | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) und [ML Kit GenAI Additional Terms](https://developers.google.com/ml-kit/genai-terms) |
| Google Drive API | Abgleich zwischen Geräten | [Google APIs Terms of Service](https://developers.google.com/terms) |

Drei kleine Hilfsbibliotheken, die AndroidX mitbringt, stehen unter MIT, BSD und CC0.

Die vollständige Liste mit jeder einzelnen Bibliothek, ihrer Version, ihrem Lizenztext und
gegebenenfalls ihrer NOTICE-Datei, dazu die Software, die Google in ML Kit und den
Play-Diensten mitliefert, steht in der App unter **Einstellungen, Über die App, Lizenzen**.
Dort stehen auch die GPL und die zusätzliche Erlaubnis im Wortlaut.

### Daten an Google

ML Kit arbeitet auf dem Gerät, schickt Google nach dessen Angaben aber Kennzahlen über die
Nutzung der Schnittstellen (Geräte- und App-Angaben, eine Kennung der Installation,
Leistung, Fehlercodes, eingestellte Sprachen), nie die Inhalte der Notizen. Deshalb ist die
KI zunächst aus, und die App fragt, bevor sie ML Kit zum ersten Mal startet. Mit Google Drive
spricht die App erst, wenn du den Abgleich selbst verbindest. Einzelheiten
stehen in der [Datenschutzerklärung](https://innotebox.de/datenschutz) und in Googles
[ML Kit Data Disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
