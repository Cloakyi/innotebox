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

Alpha. Die App ist in der geschlossenen Testphase für Android ab Version 12. Was sich je
Fassung geändert hat, steht in [CHANGELOG.md](CHANGELOG.md).

Die fertige App gibt es unter „Releases" als signierte APK. Offen ist noch die
Verschlüsselung der Datenbank.

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

Für eine Release-Fassung braucht es einen eigenen Signaturschlüssel und eine Datei
`keystore.properties` im Projektstamm (siehe `app/build.gradle.kts`); ohne sie baut Release
unsigniert.

## Aufbau

- `app/` — Oberfläche (Jetpack Compose, Material 3), Aufnahme, KI, Übersetzung, Sperre
- `core/data/` — Datenbank (Room), Modelle, Repositories, Einstellungen
- `core/sync/` — Abgleich mit Google Drive, Sicherung

Die Entscheidungen hinter dem Code stehen in [docs/ENTSCHEIDUNGEN.md](docs/ENTSCHEIDUNGEN.md),
das Datenformat für Abgleich und Sicherung in [SYNC.md](SYNC.md). Beides ist auch der
Vertrag für einen zweiten Client, der denselben Drive-Ordner liest und schreibt.

## Lizenz

GNU General Public License, Version 3 (siehe [LICENSE](LICENSE)).

Copyright 2026 Cloak Studio.

Die App enthält Bibliotheken von Google, die nicht frei sind: die Play-Dienste für die
Anmeldung bei Google Drive und ML Kit für Spracherkennung, KI und Übersetzung. Damit die
fertige App trotzdem unter der GPL weitergegeben werden darf, gilt die folgende zusätzliche
Erlaubnis:

> Additional permission under GNU GPL version 3 section 7
>
> If you modify this Program, or any covered work, by linking or combining it with Google
> Play services or ML Kit libraries published by Google LLC (or modified versions of those
> libraries), containing parts covered by the terms of the Android Software Development Kit
> License or the ML Kit Terms of Service, the licensors of this Program grant you additional
> permission to convey the resulting work.

Alle übrigen Bibliotheken stehen unter der Apache-Lizenz 2.0 oder unter MIT, BSD und CC0.
Die vollständige Liste mit allen Lizenztexten steht in der App unter Einstellungen, Über die
App, Lizenzen; erzeugt wird sie mit `python werkzeug/lizenzen.py`.

ML Kit arbeitet auf dem Gerät, sendet aber nach Angaben von Google Kennzahlen über die
Nutzung der Schnittstellen an Google (Geräte- und App-Angaben, Leistung, Fehlercodes,
eingestellte Sprachen), nie die Inhalte der Notizen. Einzelheiten stehen unter
[ML Kit Data Disclosure](https://developers.google.com/ml-kit/android-data-disclosure).

Die Schrift Google Sans Flex steht unter der SIL Open Font License 1.1.
