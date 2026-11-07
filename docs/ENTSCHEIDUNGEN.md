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

## 3. Vier Abweichungen vom ersten Entwurf

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

Titelvorschlag und Aufbereiten von Transkripten laufen über ML Kit GenAI (AICore, Gemini
Nano). Die Spracherkennung läuft seit Alpha 12 über die Spracherkennung, die Android selbst
mitbringt (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, `Systemerkennung`). Bis
Alpha 11 lief sie über ML Kit (`genai-speech-recognition`, nur als Alpha veröffentlicht);
seit Oktober 2026 lehnt Googles Sprachdienst diese Schnittstelle für fremde Apps mit
`PERMISSION_DENIED: Api access revoked` ab, auf dem Pixel 10 in beiden Modi. Der Weg über das
System ist eine feste Schnittstelle seit Android 12, rechnet auf dem Gerät und nimmt ab
Android 13 Ton aus einer Datei an; darunter gibt es kein Transkript, die Aufnahme geht
trotzdem. Das alles gibt es nicht auf jedem Gerät, und Modell oder Sprachpaket können fehlen
oder nachladbar sein. Deshalb:

- **Erst fragen, dann anbieten.** Jede Stelle prüft `checkStatus()` (ML Kit) oder
  `checkRecognitionSupport()` (Spracherkennung), bevor sie ein
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
  ohne das ruft die App ML Kit nicht auf, auch kein `checkStatus()`. Seit Alpha 10 startet
  ML Kit auch nicht mehr von selbst: Der `MlKitInitProvider` ist im Manifest entfernt, und
  `MlKitStart` startet ML Kit erst unmittelbar vor dem ersten Aufruf. Der Dialog
  (`KiZustimmungDialog`) kommt beim Start, solange nicht entschieden ist, und hinter dem
  Schalter; er verlangt die Bestätigung der Volljährigkeit, weil die GenAI-Bedingungen von
  Google die Schnittstellen nur für Anwendungen erlauben, die sich nicht an Minderjährige
  richten. Ausschalten nimmt die Zustimmung zurück.
- **Die Zustimmung ist versiegelt.** Sie ist eine kurze Aufzeichnung (Zweck, Prüfsumme des
  Dialogtextes, Volljährigkeit, Zeitpunkt, App-Version), signiert mit einem EC-Schlüssel im
  Android Keystore (StrongBox, wo vorhanden), der nur für diese Installation existiert und
  nicht exportierbar ist (`Zustimmungssiegel`, `AndroidSchluesselbund`). Gültig ist sie nur,
  wenn die Signatur mit diesem Schlüssel aufgeht und die Prüfsumme zum heutigen Text passt;
  sonst fragt die App neu. Jede Zustimmung bekommt einen neuen Schlüssel, Ausschalten löscht
  Aufzeichnung und Schlüssel. Beim Deinstallieren löscht Android den Schlüssel selbst; auf
  ein anderes Gerät lässt er sich nicht mitnehmen, dort wird also neu gefragt.
- **Die KI arbeitet nur im Vordergrund.** Google lässt die GenAI-Schnittstellen nur zu,
  solange die App vorn ist; sonst antworten sie mit `BACKGROUND_USE_BLOCKED`. Der nächtliche
  Lauf gibt deshalb nur den Titel aus dem Text und merkt die Notizen vor.
  `Nachbeschriftung` holt Titel und Tag beim nächsten Öffnen nach, aber nur, solange der
  Titel noch der abgeleitete ist; einen inzwischen geänderten Titel fasst sie nicht an.

Alles, was ins Netz ginge, kommt erst nach einer ausdrücklichen Zustimmung (Schalter
„Verarbeitung im Netz", Zustimmen erst nach dem Lesen bis unten und nach zehn Sekunden). Der
Dialog sagt, was der Schalter heute erlaubt: Sprachpakete von ML Kit Translate laden, rund
30 MB je Sprache, und was dabei an Google geht. Kommt später etwas dazu, etwa eine KI im Netz,
deckt die alte Zustimmung das nicht; dann ändert sich der Text, und der Schalter fragt neu.

**Dass ML Kit nur nach Zustimmung läuft, prüft der Build.** ML Kit bringt einen eigenen
ContentProvider mit, der es beim Start jeder App startet; das Manifest nimmt ihn heraus
(`tools:node="remove"`), gestartet wird nur über `MlKitStart.sicherstellen()`. Zwei Prüfungen
halten das fest, ohne an der App etwas zu ändern:

- `MlKitZugangTest` liest den Quelltext: ML Kit kommt nur in einer festen Liste von Dateien
  vor, jede davon ruft `MlKitStart.sicherstellen()`, und `MlKit.initialize` steht nur in
  `MlKitStart`. Braucht eine neue Datei ML Kit, schlägt der Test an; in die Liste kommt sie
  erst, wenn ihr Weg an der Zustimmung hängt.
- `manifestPruefen` läuft bei jedem Bauen und liest das fertig zusammengeführte Manifest. Steht
  dort ein Provider oder ein Initializer von androidx.startup von ML Kit oder Firebase, bricht
  der Build ab. Anlass: Bis Alpha 9 startete ML Kit über seinen Provider bei jedem Start der
  App, obwohl alle Knöpfe richtig gesperrt waren.

Der Workflow „Veröffentlichen“ baut, signiert und bescheinigt nur, wenn beide Prüfungen
bestehen (Abschnitt 11).

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
erteilten Bereiche werden geprüft, der Hintergrundlauf zeigt nie einen Dialog. Aufgerufen
wird `authorize()` erst, wenn Drive auf dem Gerät einmal bewusst verbunden wurde
(`Einstellungen.syncJeVerbunden()`); vorher fragt die App gar nicht bei Google an. „Trennen"
ist ein lokaler Schalter plus ein Widerruf bei Google, der scheitern darf. Fehlercodes der
Play-Dienste werden nie roh angezeigt.

Die OAuth-Client-ID steht nicht im Code: Android findet den passenden Eintrag über Paketname
und Signatur. `applicationId` (`de.innotebox.app`) und Code-Paket (`de.notizen.app`) sind mit
Absicht verschieden.

Antworten von Drive werden in vier Arten sortiert (`drivefehlerZu`): 401 und ein 403 ohne
Kontingentgrund heißen neu anmelden, fehlendes Netz heißt später, 429, alles ab 500 und ein
403 wegen überschrittener Quote heißen überlastet, alles andere heißt abgelehnt. Nur
Überlastung und fehlendes Netz wiederholt der Hintergrundlauf mit wachsendem Abstand, eine
Überlastung höchstens achtmal; ein abgelehnter Lauf käme beim nächsten Versuch nur wieder an
dieselbe Stelle.

Anlass für einen Abgleich ist nur eine **steigende** Zahl ungesicherter Einträge. Eine sinkende
ist der Abgleich selbst, der gerade hochlädt. Und ein neuer Anstoß bestellt nie einen
laufenden Abgleich ab, sondern nur die Wartezeit davor; der neue Lauf wartet am Schloss von
`Abgleich`.

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
Veröffentlichungen auf GitHub.

Ab Alpha 11 baut der Workflow „Veröffentlichen“ die Release-APK aus einem Tag wie
`alpha-11`. Der Schlüssel liegt dafür zusätzlich als Geheimnis in der Umgebung `release`, und
dieselben vier Angaben wie in `keystore.properties` kommen als Umgebungsvariablen herein.
Vorher laufen Tests und Lint; danach prüft der Workflow, dass die APK den Fingerabdruck des
Release-Schlüssels trägt, bescheinigt ihre Herkunft (`actions/attest`) und legt sie als
Entwurf eines Releases ab. Die Bescheinigung verbindet die APK mit dem Commit, aus dem sie
stammt, und damit auch mit dem Zustimmungstext, der in diesem Stand gilt. Ohne Zwischenspeicher,
damit eine signierte Fassung immer aus einem frischen Stand entsteht. Die Domain `innotebox.de` steht in Androids
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
an einer Einstellung.

Seit Alpha 10: Die Zeit im Hintergrund misst `SystemClock.elapsedRealtime()`, nicht die
Uhrzeit, damit ein Zurückstellen der Uhr die Sperre nicht umgeht. Bis feststeht, ob gesperrt
wird, liegt eine Abdeckung über der App (`Sperre.entschieden`). Mit eingeschalteter Sperre
zeigt die Übersicht der letzten Apps kein Vorschaubild (ab Android 13
`setRecentsScreenshotEnabled`, davor `FLAG_SECURE`). Erinnerungen zeigen dann keinen Inhalt
der Notiz, und auf dem Sperrbildschirm des Geräts steht immer nur die neutrale Fassung
(`VISIBILITY_PRIVATE` mit `publicVersion`). Eine Sicherungsdatei, die eine andere App
hereinreicht, kommt erst nach dem Entsperren zur Rückfrage.

Es wird nichts verschlüsselt; eine Verschlüsselung der Datenbank und
der Anhänge ist offen und wird vor dem Bau einzeln geplant.

## 13. Oberfläche

- Randlos (`enableEdgeToEdge`); Insets werden selbst gesetzt. Der Abstand zur Tastatur im
  Editor ist `tastaturAbstand()`, nicht `imePadding()`: Compose bleibt nach einer
  abgebrochenen Tastaturanimation auf der Tastaturhöhe hängen (`InsetsListener` in
  foundation-layout 1.12.0 ignoriert nach `onPrepare` alle Insets, bis `onStart` oder
  `onEnd` kommt); `WindowInsets.imeAnimationTarget` stimmt dagegen immer.
- Die Wischgeste auf Karten ist selbst gebaut: Widerstand, Auslösen beim Loslassen, keine
  Tempo-Auslösung, zwei Ausgänge (Verschieben reißt ab, Löschen rastet ein), Federphysik.
- Für TalkBack stehen dieselben Wege als Aktionen an der Karte (`wischAktionen`), und zwar
  am antippbaren Knoten: An der äußeren Karte fände TalkBack sie nicht. Überschriften tragen
  `heading()`.
- Teilen aus anderen Apps nimmt nur `text/plain` und Bilder an; ein offener Typ setzte
  InNoteBox in das Teilen-Menü jeder Datei. Übernommen wird erst nach dem Entsperren, als
  neue Notiz im Eingang, deren Editor aufgeht. Der Betreff wird kein Titel, den vergibt man
  selbst. Bilder nur von `content`-Adressen und nicht aus dem eigenen Provider.
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

**Sicherungen** (seit Alpha 10): Die täglichen Snapshots in Drive lassen Notizen weg, die vom
Abgleich ausgenommen sind; die Sicherungsdatei für die eigene Ablage enthält alles. Eingelesen
wird nur nach einer Rückfrage, die den Kopf der Datei zeigt. Beim Auspacken gelten
Obergrenzen (`Grenzen`: Größe je JSON-Teil und je Datei, Zahl der Einträge, freier Platz);
wird eine überschritten, bricht das Einlesen ab und räumt Ausgepacktes wieder weg.

## 15. Kalender und Termine

Seit 2026-10-30 hat die App einen eigenen Kalender. Aufbau und Ansichten sind die, die man
von verbreiteten Kalender-Apps kennt: Terminübersicht, Tag, 3 Tage, Woche und Monat, eine eigene Seitenspalte mit den
Kalendern zum Ein- und Ausblenden, ein Plus für Termin, Aufgabe und Geburtstag. Drei Kalender
legt die App von selbst an (`Mein Kalender`, `Aufgaben`, `Geburtstage`) und gibt ihnen feste
Kennungen, damit zwei Geräte dieselben haben statt je drei eigene.

**Keine Verbindung zu Google Kalender, in keine Richtung.** Kalender und Termine gehen
denselben Weg wie Notizen: als Hüllen in die Ordner `calendars/` und `events/` des eigenen
Drive (SYNC.md 16) und in die Sicherungsdatei (Fassung 5). Die Kalender-API von Google bleibt
aus demselben Grund draußen wie in Abschnitt 10. Seit dem 2026-10-30 liest die App auch die
Termine des Geräts nicht mehr, und sie schreibt die Erinnerungen der Notizen nicht mehr in
dessen Kalender. Was frühere Fassungen dort eingetragen haben, räumt `Kalenderspiegel` beim
Start weg, solange die Erlaubnis dafür noch besteht; deshalb bleiben `READ_CALENDAR` und
`WRITE_CALENDAR` im Manifest.

**Überlappende Blöcke im Raster** (`kaskade`). Termine, die innerhalb einer halben Stunde
beginnen, stehen in einer Reihe nebeneinander; jeder greift dabei um das 1,7fache seiner Breite
unter den rechten Nachbarn, der spätere liegt oben. Beginnt ein Termin später, während ein
anderer läuft, liegt er um 12 Prozent eingerückt darüber, und der lange bleibt dahinter
sichtbar. Bliebe dabei weniger als die halbe Breite, geht er in die Reihe. Blöcke, die über
oder unter anderen liegen, tragen einen schmalen Rand in der Farbe des Hintergrunds.

**Oben steht nur, was einen ganzen Tag dauert.** Ganztägige Termine und solche ab 24 Stunden
stehen in der Leiste über dem Raster. Ein kürzerer Termin über Mitternacht steht im Raster,
geteilt in einen Block je Tag mit „(1/2)“ und „(2/2)“ (`teil`).

**Der Editor ist ein Blatt über dem Kalender**, keine eigene Seite. Halb offen sieht man den
Kalender und darin den Block des Termins, den man mit dem Finger verschieben und an zwei
Punkten in Viertelstunden länger oder kürzer ziehen kann. Ein Tipp auf eine freie Stelle legt
so einen Block in der eingestellten Standarddauer an; das lässt sich abschalten, dann gibt es
neue Termine nur über das Plus.

**Aufgaben dürfen ohne Datum sein** (`undated`, SYNC.md 16.2). Sie haben dann weder Zeit noch
Wiederholung noch Erinnerung, stehen nie im Kalender und klingeln nie (`Terminregeln` gibt für
sie kein Vorkommen). Zu sehen sind sie in der Aufgabenliste oben rechts, die alle Aufgaben der
Kalender nach Zeitpunkt ordnet. Listen in Notizen gehören nicht dazu.

**Wiederholung** (2026-10-30). Die
Auswahl bietet Einmalig, Täglich, Wöchentlich, Monatlich, Jährlich und „Benutzerdefiniert…“;
dort gibt es den Abstand, bei Wochen die Tage und das Ende nach Datum oder Anzahl. Gespeichert
wird das in vier Feldern neben `recurrence` (SYNC.md 16.2, Room 10), nicht als Regeltext nach
iCalendar: Die Felder kann ein älterer Client einfach übergehen und rechnet dann die alte
Serie. Die Anzahl zählt ausgelassene Vorkommen mit, wie bei iCalendar. `WERKTAGS` bleibt für
alte Termine erhalten und erscheint in der Auswahl als eigene Serie. Monatlich gibt es nur
den Tag des Monats, nicht „am letzten Freitag“. Konto, Zeitzone und Gäste aus Google gibt es
nicht, weil die App ohne einen fremden Kalenderdienst auskommt.

**Ein Termin zeigt oben sein Symbol oder ein eigenes Bild** (2026-10-30). Das Symbol suchen die Wörter sofort und, wenn die KI an ist, danach das
Modell auf dem Gerät aus (`Symbolwahl`). Ein eigenes Bild liegt als `bannerId` am Termin und
als Datei neben den Anhängen der Notizen, mit denselben Regeln: normalisiert, nie verändert,
ein neues Bild bekommt eine neue Kennung. Der Abgleich holt und schickt die Datei über ihren
Namen (`terminbilderAbgleichen`), die Sicherung nimmt sie mit. Ein eigenes Anhangsmodell für
Termine wäre sauberer, kostete aber eine zweite Tabelle für genau ein Bild je Termin.

**Erinnerungen aus Notizen erscheinen im Kalender** als eigene Ebene, abschaltbar. Sie bleiben
Erinnerungen ihrer Notiz; der Kalender schreibt sie nicht um.

**Ganztägige Termine liegen in UTC-Mitternacht**, das Ende ist der letzte Tag einschließlich.
Ein Geburtstag ist damit überall auf der Welt am selben Datum. Ihre Erinnerungen zählen Minuten
vor der lokalen Mitternacht des Tages; negativ heißt danach (-540 ist 9 Uhr am selben Tag).
Serien werden in Ortszeit ausgerollt (`Terminregeln`), damit ein wöchentlicher Termin um
10 Uhr auch nach der Zeitumstellung um 10 Uhr steht.

**Ein Wecker je Art, nicht je Termin** (`Terminwecker`). Geplant wird immer nur der nächste
fällige Zeitpunkt für normale und für wichtige Termine; beim Klingeln werden alle fälligen
gezeigt und der nächste geplant. Ein Termin mehr oder weniger ändert damit nichts an der Zahl
der Alarme, und nach Neustart, Zeitumstellung oder Wechsel der Zeitzone wird einfach neu
gerechnet. Was bis zu zwei Stunden verpasst wurde, kommt nach.

**Wichtige Termine klingeln wie ein Wecker.** Sie gehen über `setAlarmClock`, einen eigenen
Kanal mit Weckerton und eine Benachrichtigung mit Vollbild, die bis zum Ausschalten klingelt
(höchstens zehn Minuten). Der Vollbildschirm zeigt Symbol, Titel, Zeit und Ort; unten stehen
Schlummern und ein großer Knopf zum Ausschalten. Fehlt die Erlaubnis für exakte Alarme oder für
das Vollbild, sagt es der Editor und führt in die Einstellung. Bei gesperrter App steht in der
Benachrichtigung nur „Termin“.

**Das Symbol wählen zuerst die Wörter, dann die KI.** Beim Speichern setzt eine feste
Wortliste sofort ein Symbol (das längste passende Wort am Wortanfang gewinnt, „Zahnarzt“ vor
„Arzt“). Ist die KI eingeschaltet und zugestimmt, fragt die App danach das Modell auf dem
Gerät und nimmt dessen Antwort nur, wenn sie ein Wort aus derselben festen Liste ist und der
Titel sich inzwischen nicht geändert hat. Ein Geburtstag ist immer ein Kuchen.

**Einstellungen und Vorgaben bleiben auf dem Gerät** (2026-11-07). Standarddauer,
Kalenderwochen, Wochenende ausblenden, das Anlegen per Tipp und die Vorgaben je Kalender
(Benachrichtigung, Dauer, ganztägig) liegen in den Einstellungen des Geräts, nicht in Drive,
wie schon die ausgeblendeten Kalender: Was man sehen will, ist eine Frage des Geräts. Aufgaben
und Geburtstage haben keine Vorgaben, für sie gelten feste Regeln. Wechselt man bei einem neuen
Termin den Kalender, folgt nur, was noch so steht, wie der alte Kalender es vorgab.
