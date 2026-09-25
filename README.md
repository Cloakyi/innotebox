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
  Hintergrund einer Karte
- Sprachnotizen: aufnehmen, später auf dem Gerät in Text umwandeln, Wellenform, Stille
  überspringen
- Volltextsuche über Titel, Text, Listen und Transkripte, mit Filtern nach Stufe, Tag,
  Farbe, Ordner und Papierkorb
- Tags, Farben, Favoriten, Erinnerungen auf die Minute (auch im Kalender des Geräts)
- KI nur auf dem Gerät (Titelvorschlag, Aufbereiten von Transkripten), abschaltbar
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

## Lizenz

Der Code von InNoteBox steht unter der GNU General Public License, Version 3 (siehe
[LICENSE](LICENSE)). Copyright 2026 Cloak Studio.

### Bibliotheken und Dienste anderer

Die fertige App enthält Bibliotheken anderer Hersteller. Für sie gelten deren eigene
Lizenzen, nicht die GPL. Im Repo selbst liegt davon nichts; Gradle lädt sie beim Bauen.

| Teil | Wofür | Lizenz |
|---|---|---|
| AndroidX, Jetpack Compose, Material 3, Room, WorkManager, DataStore, Hilt, Dagger | Oberfläche, Datenbank, Hintergrundarbeit | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Kotlin, kotlinx.coroutines, kotlinx.serialization, OkHttp | Sprache, Nebenläufigkeit, Netz | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Google Play-Dienste (Anmeldung) | Anmeldung bei Google Drive | [Android Software Development Kit License](https://developer.android.com/studio/terms) |
| ML Kit (Spracherkennung, Prompt API, Übersetzung) | Umwandlung in Text, KI auf dem Gerät, Übersetzung | [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms) und [ML Kit GenAI Additional Terms](https://developers.google.com/ml-kit/genai-terms) |
| Google Drive API | Abgleich zwischen Geräten | [Google APIs Terms of Service](https://developers.google.com/terms) |
| Google Sans Flex | Schrift | [SIL Open Font License 1.1](https://openfontlicense.org) |

Drei kleine Hilfsbibliotheken, die AndroidX mitbringt, stehen unter MIT, BSD und CC0.

Die vollständige Liste mit jeder einzelnen Bibliothek, ihrer Version und ihrem Lizenztext,
dazu die Software, die Google in ML Kit und den Play-Diensten mitliefert, steht in der App
unter **Einstellungen, Über die App, Lizenzen**.

### Zusätzliche Erlaubnis für die Bibliotheken von Google

Die Play-Dienste und ML Kit sind keine freie Software. Die GPL erlaubt es eigentlich nicht,
GPL-Code zusammen mit solchen Bibliotheken weiterzugeben. Damit die fertige App trotzdem
weitergegeben werden darf, gilt diese zusätzliche Erlaubnis. Sie steht auf Englisch, weil
die GPL selbst englisch ist und nur der englische Wortlaut verbindlich ist:

> Additional permission under GNU GPL version 3 section 7
>
> If you modify this Program, or any covered work, by linking or combining it with Google
> Play services or ML Kit libraries published by Google LLC (or modified versions of those
> libraries), containing parts covered by the terms of the Android Software Development Kit
> License or the ML Kit Terms of Service, the licensors of this Program grant you additional
> permission to convey the resulting work.

Sinngemäß auf Deutsch, nicht verbindlich: Wer dieses Programm verändert oder mit den
Bibliotheken der Google Play-Dienste oder von ML Kit verbindet, darf das Ergebnis
weitergeben, obwohl diese Bibliotheken unter den Bedingungen von Google stehen und nicht
unter der GPL.

### Daten an Google

ML Kit arbeitet auf dem Gerät, sendet aber nach Angaben von Google Kennzahlen über die
Nutzung der Schnittstellen an Google (Geräte- und App-Angaben, Leistung, Fehlercodes,
eingestellte Sprachen), nie die Inhalte der Notizen. Einzelheiten stehen unter
[ML Kit Data Disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
