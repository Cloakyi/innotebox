# Entscheidungen hinter dem Code

Was hier steht, erklärt, warum der Code so ist, wie er ist. Die Kommentare im Code verweisen
auf diese Abschnitte. Wer etwas ändert, das einer dieser Entscheidungen widerspricht, ändert
zuerst hier.

## 1. Die Idee

Notizen sind eine Arbeitsfläche, kein Langzeitspeicher. Drei Stufen (Eingang, Workspace,
Archiv), jede Notiz steht auf genau einer. Erfassen ohne Reibung: kein Titel, kein Ordner,
keine Entscheidung vor dem Schreiben. Wiederfinden über die Suche, nicht über Ablagen. Und
die Wartung macht die App: nachts, nach Alter und Menge, mit Rückgängig am Morgen.

Daneben gibt es einen Ordnermodus für alle, die lieber in Ordnern denken. **Ordnersystem und
Stufensystem sind zwei getrennte Systeme.** Eine Notiz trägt beides (`stage` und
`folderId`), keins der beiden schreibt am anderen, beide haben ein eigenes Archiv, und der
Wechsel zwischen den Modi verschiebt nichts.

Für jede neue Funktion drei Fragen: Macht sie das Erfassen leichter oder schwerer?
Verschiebt sie Arbeit vom Nutzer zur App oder umgekehrt? Ist sie Schreibtisch oder
Bücherregal? Der Schreibtisch hat Vorrang.

## 2. Aufbau

| Modul | Inhalt |
|---|---|
| `core/data` | Room-Datenbank, Modelle, DAOs, Repositories, Einstellungen (DataStore). Keine UI-Abhängigkeit |
| `core/sync` | Abgleich mit Google Drive nach [SYNC.md](../SYNC.md), Sicherung als `.notesbak`, tägliche Snapshots |
| `app` | Oberfläche (Jetpack Compose, Material 3), Aufnahme und Erkennung, KI auf dem Gerät, Übersetzung, Sperre |

Kotlin mit Coroutines und Flow, MVVM mit unidirektionalem Datenfluss, Hilt für die
Abhängigkeiten, WorkManager für Hintergrundläufe, AlarmManager für Erinnerungen. Kennungen
sind UUID-Strings, Zeitstempel UTC-Millis.

**Die lokale Datenbank ist die Quelle der Wahrheit.** Es gibt keinen Server. Der Abgleich
läuft ausschließlich in das eigene Google Drive des Nutzers, in einen Ordner, den die App
anlegt und der der einzige ist, den sie sieht (`drive.file`).

## 3. Vier Abweichungen von der ersten Spezifikation

Diese Abweichungen sind bewusst und bleiben.

1. **FTS4, nicht FTS5.** Room bietet nur `@Fts3` und `@Fts4`. Präfixsuche kann FTS4; für
   die Hervorhebung gibt es `snippet()`.
2. **Der Volltextindex ist eine eigenständige Tabelle** (`note_fts`), die das Repository bei
   jeder Schreiboperation mitpflegt. Er umfasst Titel, Text, Listeneinträge und Transkripte,
   also drei Quelltabellen; eine External-Content-Tabelle kann nur eine.
3. **`archive_run_items` mit `previousStage`.** Die automatische Archivierung muss einen
   ganzen Lauf zurücknehmen können. `archive_runs` kennt nur Zählwerte, und die vorherige
   Stufe jeder Notiz stünde sonst nirgends.
4. **`Scaffold` wird überschrieben.** Karten liegen auf `surface`, der Hintergrund auf
   `surfaceContainerLow` (Abschnitt 4). `Scaffold` nimmt aber `background`, und das ist im
   Fallback-Schema dieselbe Farbe wie `surface`; die Karten wären unsichtbar. Deshalb
   `NotizenScaffold` auf jedem Bildschirm.

## 4. Farben

**Karten liegen auf `surface`, der Hintergrund auf `surfaceContainerLow`.** Die Karten
sinken dadurch optisch ein, statt zu schweben. Material empfiehlt es andersherum; die
Umkehrung ist gewollt und am Gerät geprüft, auch mit Dynamic Color. Nicht „korrigieren".

Keine Schlagschatten auf Karten, Abstufung nur über Flächen, Haarlinien in `outlineVariant`.
Keine festen Farbwerte im UI-Code außer der Notizpalette (`NoteColor`, gespeichert als Name,
Tonwerte je Theme) und `FAVORIT_GOLD`.

**Alles, was auf einer Notiz sitzt, trägt ihre Farbe**: Knöpfe, Umschalter, Chips,
Fortschrittsanzeigen. Flächen als `onContainer` bei geringer Deckung, Symbole und Text als
voller `onContainer`. `primary` bleibt dort, wo etwas auf der Theme-Fläche liegt (Dialoge,
Auswahlblätter, der Auswahlrahmen einer Karte). Auf einer Notiz gibt es keine zweite
Textfarbe; wer etwas hervorheben will, nimmt einen Textmarker (hinterlegte Fläche), denn der
Kontrast der Palette ist nur für `onContainer` gegen `container` nachgerechnet.

Über einem Hintergrundbild gilt die Palette nicht: Weiß auf einem Abdunkler von 55 Prozent
ist die einzige Kombination, für die sich über jedem Foto Lesbarkeit zusagen lässt (4,75:1
über reinem Weiß, dem schlechtesten Fall).

Ordnerfarben sind Tagfarben. Als Schrift werden sie gegen ihren Hintergrund nachgerechnet
(WCAG 4,5:1) und notfalls Richtung Weiß oder Schwarz gemischt (`Kontrast.kt`).

## 5. Die `updatedAt`-Disziplin

`updatedAt` zählt **nur bei echter Inhaltsänderung** hoch. Öffnen setzt `lastOpenedAt`, und
das ist für den Abgleich nicht relevant. Die Regel sitzt im `NoteRepository`, nicht in den
DAOs: `updateContent()` vergleicht vorher und schreibt nichts, wenn sich nichts geändert hat.
Nötig, weil der Autosave alle 800 Millisekunden feuert, auch wenn nur der Cursor wandert. Ein
Wecker, eine Farbe, ein Favorit sind keine Inhaltsänderung.

## 6. Kein Speichern-Knopf

Autosave mit Entprellung, hart beim Verlassen des Editors, bei `onPause` und beim Wechsel in
den Hintergrund. Wer eine leere Notiz verlässt, wollte sie nicht: Sie wird verworfen. Nach
Löschen, Archivieren und Stufenwechsel gibt es immer eine Undo-Leiste.

## 7. KI nur auf dem Gerät

Titelvorschlag, Aufbereiten von Transkripten und Spracherkennung laufen über ML Kit GenAI
(AICore, Gemini Nano). Das gibt es nicht auf jedem Gerät, und das Modell kann fehlen oder
nachladbar sein (`DOWNLOADABLE`). Deshalb:

- **Erst fragen, dann anbieten.** Jede Stelle prüft `checkStatus()`, bevor sie ein
  Bedienelement zeigt. Was das Gerät nicht kann, steht in den Einstellungen blass mit dem
  Grund; ein Knopf, der da ist und nichts tut, wäre schlechter als keiner.
- **Nie blockierend auf die KI warten.** Der Titeldialog ist leer und zeigt den Vorschlag
  nach, sobald er da ist. Fällt die KI aus, gibt es den abgeleiteten Titel aus dem Text.
- **Messen lädt nichts.** Beim ersten Start misst die App, was das Gerät kann, und merkt
  sich den Stand; das Nachladen eines Modells ist immer ein Knopf, nie ein Automatismus. Bei
  jedem weiteren Start läuft dieselbe Messung ohne Dialog, und nur eine Abweichung führt zur
  vollen Prüfung.
- **Der Schalter „Von der KI aufbereiten lassen"** betrifft Titel, Aufbereiten und das
  Umwandeln in Text. Wer die KI ausschaltet, will nichts von ihr, auch wenn die Erkennung
  technisch etwas anderes ist. Abgeschaltet werden die Bedienelemente.
- **Die KI braucht eine Zustimmung** (seit Alpha 9). ML Kit rechnet auf dem Gerät, schickt
  Google aber Kennzahlen über die Nutzung (Gerät, App, eine Kennung der Installation,
  Leistung, Fehlercodes, Sprachen). Das Auslesen dafür braucht nach § 25 TDDDG eine
  Einwilligung, und die Bedingungen von Google verlangen, dass die Nutzer davon erfahren.
  `Einstellungen.kiAktiv()` ist deshalb nur wahr, wenn eingeschaltet **und** zugestimmt ist;
  ohne das ruft die App ML Kit nicht auf, auch kein `checkStatus()`. Der Dialog
  (`KiZustimmungDialog`) kommt beim Start, solange nicht entschieden ist, und hinter dem
  Schalter; er verlangt die Bestätigung der Volljährigkeit, weil die GenAI-Bedingungen von
  Google die Schnittstellen nur für Anwendungen erlauben, die sich nicht an Minderjährige
  richten. Ausschalten nimmt die Zustimmung zurück.

Alles, was ins Netz ginge, kommt erst nach einer ausdrücklichen Zustimmung (Schalter
„Verarbeitung im Netz", Zustimmen erst nach dem Lesen bis unten und nach zehn Sekunden). Der
Dialog sagt, was der Schalter heute erlaubt: Sprachpakete von ML Kit Translate laden, rund
30 MB je Sprache, und was dabei an Google geht. Kommt später etwas dazu, etwa eine KI im Netz,
deckt die alte Zustimmung das nicht; dann ändert sich der Text, und der Schalter fragt neu.

## 8. Audio

Erst aufnehmen, dann erkennen, zwei getrennte Schritte. Die Aufnahme hängt an nichts (kein
Modell, kein Netz) und ist der Schritt, der sich nicht wiederholen lässt. Format 16 kHz,
Mono, 16 Bit, genau das, was die Erkennung für `AudioSource.fromPfd()` verlangt. Die
Aufnahme wird nie gefiltert; zerlegt wird erst beim Erkennen, an Sprechpausen, gerechnet aus
zwei Perzentilen des Pegels, ohne KI. Häppchen von 15 Sekunden, bei Überlauf halbiert. Die
Pegelanzeige rechnet in Dezibel. Das Rohtranskript wird nie überschrieben; die bearbeitete
Fassung ist der Text der Notiz. Ein Abspieler für die ganze App; ein Vordergrunddienst hält
das Mikrofon, mit dem Typ `microphone` beim Aufnehmen und `dataSync` beim Erkennen.

## 9. Übersetzung

Drei Wege, wählbar in den Einstellungen; was das Gerät nicht kann, steht blass mit Grund:

1. **Die Übersetzung des Systems**, `android.view.translation.TranslationManager` (seit
   API 31). Geprüfte Kette: `getOnDeviceTranslationCapabilities`, `TranslationSpec` mit
   `ULocale`, `TranslationContext.Builder`, `createOnDeviceTranslator`,
   `TranslationRequest.Builder().setTranslationRequestValues`,
   `Translator.translate(request, CancellationSignal, Executor, Consumer)`,
   `TranslationResponse.getTranslationResponseValues()`. Das Sprachpaar wird vorher geprüft,
   weil `createOnDeviceTranslator` bei einem fehlenden Paar nie zurückruft; Sprachpakete
   lädt man in der Systemeinstellung (`getOnDeviceTranslationSettingsActivityIntent`).
2. **Die KI auf dem Gerät** über die Prompt API, je Text eine Anfrage.
3. **ML Kit Translate**, das seine Sprachpakete über das Netz lädt, deshalb nur hinter dem
   Schalter „Verarbeitung im Netz" und nur auf Knopfdruck.

Übersetzt wird die Auswahl im Text oder die ganze Notiz; das Ergebnis ersetzt das Original
(mit Undo) oder wird darunter angehängt. Die Auswahlleiste des Textes ist eine eigene
`TextToolbar` mit dem fünften Eintrag „Übersetzen".

## 10. Google Drive und die Anmeldung

`drive.file` ist ein nicht sensibler Bereich; deshalb darf die App ohne Googles Prüfung
laufen, und deshalb wird die Google-Kalender-API **nicht** benutzt (`calendar.events` wäre
sensibel). Erinnerungen gehen über den Kalender-Anbieter des Geräts in einen bestehenden
Kalender, den der Nutzer wählt.

Angemeldet wird über `AuthorizationClient` der Play-Dienste, nicht über Credential Manager
und nicht über das abgelöste `GoogleSignIn`. Es gibt kein Refresh-Token; `authorize()` liefert
still ein frisches, wenn der Zugriff erteilt ist. `hasResolution()` schlägt alles, die
erteilten Bereiche werden geprüft, der Hintergrundlauf zeigt nie einen Dialog. „Trennen"
ist ein lokaler Schalter plus ein Widerruf bei Google, der scheitern darf. Fehlercodes der
Play-Dienste werden nie roh angezeigt.

Die OAuth-Client-ID steht nicht im Code: Android findet den passenden Eintrag über Paketname
und Signatur. `applicationId` (`de.innotebox.app`) und Code-Paket (`de.notizen.app`) sind mit
Absicht verschieden.

## 11. Versionen und Signatur

`versionCode` ist mindestens die Room-Schemaversion und steigt mit jeder Schemaänderung.
Androids Sicherung spielt Daten einer neueren App-Version nur dann nicht in eine ältere
zurück, wenn die Nummer größer ist; bei gleicher Nummer landete einmal eine Datenbank auf
Stand 6 in einem Build mit Stand 5, und die App stürzte bei jedem Start ab. `versionName`
trägt Stufe und Zählung („Alpha 9"). Die Einstellungen zeigen Version, Build, Paketname und
Webseite, gelesen aus dem Paket.

Release ist nur arm64 (`ndk.abiFilters`), weil ML Kit sonst Bibliotheken für vier
Prozessorarten mitbringt. Der Signaturschlüssel liegt außerhalb des Projekts; `keystore.properties`
im Projektstamm nennt ihn. Die App aktualisiert sich nicht selbst; Updates kommen über die
Veröffentlichungen auf GitHub. Die Domain `innotebox.de` steht in Androids
App-Info über einen App-Links-Filter nur für den Pfad `/app`; wer die ganze Domain
beanspruchte, finge jeden Link auf die Webseite ab.

**Lizenzhinweise gehören in die App.** Beim Packen der APK fallen die Lizenzdateien der
Bibliotheken weg, und die Pakete von ML Kit und den Play-Diensten bringen eine eigene Liste
mitgelieferter Drittsoftware mit. Die Gradle-Aufgabe `./gradlew :app:lizenzen`
(`gradle/lizenzen.gradle.kts`) liest den tatsächlichen Release-Build aus und schreibt alles nach `app/src/main/assets/lizenzen.json`; die Seite
„Lizenzen" unter „Über die App" zeigt es an. Nach jeder Änderung an den Abhängigkeiten läuft
die Aufgabe neu. Für die unfreien Google-Bibliotheken gilt eine zusätzliche Erlaubnis nach
Abschnitt 7 der GPL, im Wortlaut in der README.

## 12. Sperre

Eine Funktion der App, nicht des Systems: `Sperre` sperrt beim Start des Prozesses und nach
der eingestellten Zeit im Hintergrund (eine Drehung zählt nicht). Entsperrt wird über den
`BiometricPrompt` des Systems (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, ohne eigenen
Abbrechen-Knopf, weil das mit `DEVICE_CREDENTIAL` verboten ist), nicht über
`androidx.biometric`, das eine `FragmentActivity` verlangt. Der Sperrbildschirm ist ein
bildschirmfüllender Dialog, damit er auch über den Blättern und Dialogen der App liegt.
Ein- und Ausschalten nur nach erfolgreicher Entsperrung. `FLAG_SECURE` (Aufnahmeschutz) hängt
an einer Einstellung. Es wird nichts verschlüsselt; eine Verschlüsselung der Datenbank und
der Anhänge ist offen und wird vor dem Bau einzeln geplant.

## 13. Oberfläche

- Randlos (`enableEdgeToEdge`); Insets werden selbst gesetzt. Der Abstand zur Tastatur im
  Editor ist `tastaturAbstand()`, nicht `imePadding()`: Compose bleibt nach einer
  abgebrochenen Tastaturanimation auf der Tastaturhöhe hängen (`InsetsListener` in
  foundation-layout 1.12.0 ignoriert nach `onPrepare` alle Insets, bis `onStart` oder
  `onEnd` kommt); `WindowInsets.imeAnimationTarget` stimmt dagegen immer.
- Die Wischgeste auf Karten ist selbst gebaut: Widerstand, Auslösen beim Loslassen, keine
  Tempo-Auslösung, zwei Ausgänge (Verschieben reißt ab, Löschen rastet ein), Federphysik.
- Das Umordnen per Ziehen ist einmal gebaut (`Ziehen.kt`) und für Ordner und Listeneinträge
  benutzt. Die Lambdas der Geste lesen über `rememberUpdatedState`, weil `pointerInput`
  seinen Block nur beim Wechsel der Kennung neu startet.
- Listen im Editor haben zwei Zustände: Lesen (nur abhaken) und Bearbeiten (Griff, X mit
  Undo, Textfeld). Hinzufügen geht in beiden.
- Die Auswahlleiste der Mehrfachauswahl trägt nur Symbole, höchstens sieben.
- `TopAppBar` bekommt `containerColor = Transparent` und die Fläche dahinter, sonst läuft die
  Leiste beim Umfärben der Notiz sichtbar hinterher.
- Bedienelemente werden vollständig gebaut, auch wenn ihre Logik noch fehlt; fehlende
  Funktion sagt es sichtbar. Sichtbare Texte: ganze Sätze, Umlaute, kein Gedankenstrich;
  Bezeichner im Code ohne Umlaute.
- Leerzustände nur über `LeererZustand` und `Hinweisblock`.

## 14. Datenschicht

Kein `fallbackToDestructiveMigration`; lieber ein harter Fehler als stiller Datenverlust.
Migrationen überschreiben `migrate(SQLiteConnection)`. Die Schemadateien liegen unter
`core/data/src/test/assets/`, weil `MigrationTestHelper` sie über den AssetManager sucht.
Tokenizer `unicode61`, damit „Kuche" auch „Küche" findet. `SyncDao.markDirty()` ändert
gezielt zwei Spalten statt eines Upserts, der die Drive-Zuordnung überschriebe. Tests laufen
über Robolectric mit `sqliteMode=NATIVE`, `sdk=34` und ASM 9.9; die Uhr steht in Tests still.

**Keine Cloud-Sicherung durch Android** (seit Alpha 9, `res/xml/datensicherung.xml`). Die App
hat ihre eigenen Wege, die der Mensch in der Hand hat: die Sicherungsdatei und den Abgleich
über das eigene Drive. Eine stille Kopie der ganzen Datenbank in der Gerätesicherung bei
Google nähme auch Notizen mit, die ausdrücklich auf dem Gerät bleiben sollen. Der Umzug von
Gerät zu Gerät beim Einrichten bleibt erlaubt.

## 15. Die Phasen

Die Kommentare im Code nennen Phasen. Das ist die Reihenfolge, in der die App entstanden ist:

| Phase | Inhalt |
|---|---|
| 0 bis 2 | Grundgerüst, Datenschicht, Editor mit Autosave |
| 3 | Die drei Stufen mit Wischgesten und Titelpflicht |
| 4 bis 6 | Tags und Farben, Suche und Filter, Favoriten und Erinnerungen |
| 7 | Audio: Aufnahme, Erkennung, Transkript |
| 8 | Bilder, Textauszeichnung, Auto-Archiv und Papierkorb |
| 9 bis 10 | Abgleich mit Drive, Sichern und Wiederherstellen |
| 12 | Kalender des Geräts |
| 13 bis 14 | Ordnermodus mit Archiv, Papierkorb, Farben, Reihenfolge |
| 15 | KI vollständig abschaltbar, Gerätestand, Titeldialog |
| 16 | Übersetzung |
| 18 | Editor: Tastatur, Formatierleiste, Wellenform, Listen mit Bearbeitungszustand |
| 19 | Sperre und Aufnahmeschutz |
| 20 | Abgleich neu nach Schema 4, tägliche Snapshots |
| 21 | Version, Signatur, Verteilung |
