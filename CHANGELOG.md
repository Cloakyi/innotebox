# Änderungen

## Alpha 13 (2026-11-07)

InNoteBox hat jetzt einen eigenen Kalender mit Terminen, Aufgaben und Geburtstagen.

- Der Kalender zeigt eine Terminübersicht, einen Tag, drei Tage, eine Woche oder einen Monat.
  In der Seitenspalte lassen sich die Kalender ein- und ausblenden, oben rechts stehen Suche,
  Aufgaben und Heute. Über das Plus unten rechts entstehen Termine, Aufgaben und Geburtstage.
- Drei Kalender legt die App von selbst an: Mein Kalender, Aufgaben und Geburtstage. Weitere
  Kalender lassen sich mit Namen und Farbe anlegen, ändern und löschen. Zur Wahl stehen
  24 Farben.
- Der Editor ist ein Blatt über dem Kalender. Halb offen sieht man den Termin als Block im
  Raster, verschiebt ihn mit dem Finger und zieht ihn an zwei Punkten länger oder kürzer.
  Ein Tipp auf eine freie Stelle legt dort einen Termin an.
- Termine können sich wiederholen: täglich, wöchentlich, monatlich, jährlich oder nach eigener
  Regel mit Abstand, Wochentagen und einem Ende nach Datum oder Anzahl.
- Aufgaben dürfen ohne Datum sein. Sie stehen dann nur in der Aufgabenliste und klingeln nie.
- Ein Termin zeigt oben ein passendes Symbol oder ein eigenes Bild. Das Symbol suchen die
  Wörter des Titels aus und, wenn die KI eingeschaltet ist, das Modell auf dem Gerät.
- Wichtige Termine klingeln wie ein Wecker, auf dem ganzen Bildschirm und auch über dem
  Sperrbildschirm, mit Schlummern und einem großen Knopf zum Ausschalten.
- Erinnerungen aus Notizen erscheinen auf Wunsch als eigene Ebene im Kalender. Ein Tipp öffnet
  die Notiz.
- In den Kalendereinstellungen gibt es Standardkalender, Standardbenachrichtigung und
  Standarddauer für neue Termine. Dazu kommen Kalenderwochen, Wochenende ausblenden und ein
  Schalter, mit dem neue Termine nur noch über das Plus entstehen.
- Jeder eigene Kalender kann vorgeben, mit welcher Benachrichtigung und Dauer seine neuen
  Termine beginnen und ob sie ganztägig sind.
- Kalender und Termine gehen beim Abgleich in dein Google Drive und stehen in der
  Sicherungsdatei, genau wie Notizen. Die Einstellungen des Kalenders bleiben auf dem Gerät.
- Die App liest und beschreibt den Kalender des Geräts nicht mehr. Termine, die frühere
  Fassungen für Erinnerungen dort eingetragen haben, räumt sie beim Start weg. Der Schalter
  „Erinnerungen im Kalender“ ist deshalb aus den Einstellungen verschwunden.

## Alpha 12.1 (2026-10-13)

- Ein Tipp auf die freie Fläche unter dem Inhalt öffnet die Tastatur jetzt auch dann, wenn
  man sie vorher eingefahren hat und der Cursor noch im Textfeld steht. Vorher passierte dann
  nichts, und man musste das Textfeld selbst antippen.

## Alpha 12 (2026-10-13)

Das Umwandeln in Text geht wieder, jetzt über die Spracherkennung von Android selbst.

- Aufnahmen werden wieder in Text umgewandelt. Seit Oktober lässt Google fremde Apps nicht
  mehr an die Vorabversion seiner Spracherkennung, auf die InNoteBox bisher setzte; die App
  meldete dann „keine Spracherkennung für Deutsch“. Jetzt nimmt sie die Spracherkennung, die
  Android selbst mitbringt. Sie läuft ganz auf dem Gerät, der Ton verlässt das Handy nicht.
  Transkripte gehen damit ab Android 13.
- Fehlt das Sprachpaket für die eingestellte Sprache, lädt es „Sprachpaket laden“ über das
  System nach.
- Im Editor öffnet ein Tipp auf die freie Fläche unter dem Inhalt das Schreiben: bei Text-
  und Bildnotizen mit dem Cursor am Ende des Textes, bei Listen in „Eintrag hinzufügen“.
  Vorher musste man genau auf „Schreib einfach los“ tippen.
- Nach einem Update blieb die App beim ersten Öffnen manchmal auf dem Logo stehen. Das
  passiert nicht mehr.
- Alle Bibliotheken sind auf dem neuesten stabilen Stand, dazu Gradle 9.8.

## Alpha 11 (2026-09-29)

Teilen aus anderen Apps, Bedienung mit TalkBack und ein zuverlässigerer Abgleich.

- Aus anderen Apps lassen sich Text, Links und Bilder mit InNoteBox teilen. Daraus wird eine
  neue Notiz im Eingang, die gleich im Editor aufgeht. Der Titel einer Webseite steht im Text,
  den Titel der Notiz vergibst du weiter selbst. Ist die Sperre an, entsteht die Notiz erst
  nach dem Entsperren.
- Mit TalkBack lassen sich Notizen ohne Wischen verschieben, in den Papierkorb legen,
  zurückholen und endgültig löschen, über die Aktionen der Karte. Überschriften sagt TalkBack
  als solche an.
- Auf Android 12 bis 15 bricht das Umwandeln von Aufnahmen in Text nicht mehr ab. Auf
  Android 12 erscheinen jetzt auch Wellenform und Pausensprung.
- Wer die Abfrage der Sperre wegklickt, wird beim Drehen des Handys nicht mehr sofort wieder
  gefragt, sondern erst über „Entsperren“. Der Satz „vom Nutzer abgebrochen“ erscheint nicht mehr.
- Der Abgleich lädt viele Änderungen in einem Zug hoch statt in vielen einzelnen Läufen.
  Drosselt Google Drive oder ist es gestört, versucht die App es im Hintergrund später noch
  einmal, statt aufzugeben.
- Jede Änderung am Quelltext wird auf GitHub automatisch gebaut und geprüft: Tests, Lint und
  die Liste der Lizenzen.
- Die APK baut und signiert GitHub jetzt selbst aus dem Quelltext dieses Stands und
  bescheinigt ihre Herkunft. Wie man das nachprüft, steht in der README.
- Automatische Prüfungen stellen bei jeder Änderung fest, dass ML Kit nur in den dafür
  vorgesehenen Teilen der App vorkommt, nur über eine Stelle gestartet wird und beim Start der
  App nicht von selbst anläuft. Ohne bestandene Prüfungen baut GitHub keine APK.

## Alpha 10 (2026-09-26)

Sicherheit und Datenschutz nachgebessert, nach einer gründlichen Prüfung von außen.

- Die tägliche Sicherung in Google Drive nimmt nur noch Notizen mit, die auch abgeglichen
  werden. Was du vom Abgleich ausgenommen hast, bleibt wirklich auf dem Gerät. Die
  Sicherungsdatei, die du selbst ablegst, enthält weiter alles.
- Eine Sicherung wird erst nach einer Rückfrage eingelesen, die zeigt, was in der Datei
  steckt. Reicht eine andere App die Datei herein, kommt die Rückfrage erst nach dem
  Entsperren. Eine zu große oder präparierte Datei bricht das Einlesen ab, ohne Reste zu
  hinterlassen.
- Die Sperre misst die Zeit im Hintergrund unabhängig von der Uhrzeit des Handys. Bis
  feststeht, ob gesperrt wird, liegt eine Abdeckung über der App. Hat man die App verlassen,
  zeigt die Übersicht der letzten Apps mit eingeschalteter Sperre kein Vorschaubild mehr.
- Erinnerungen zeigen auf dem Sperrbildschirm des Handys keinen Inhalt der Notiz. Ist die
  Sperre der App an, steht auch in der Benachrichtigung selbst nur „Erinnerung“.
- Die App fragt erst bei Google an, wenn du Drive zum ersten Mal verbindest.
- ML Kit startet erst, wenn es gebraucht wird, also nach deiner Zustimmung zur KI oder mit
  „Verarbeitung im Netz“, und nicht mehr bei jedem Start der App.
- Die KI arbeitet nur, solange die App offen ist. Titel und Tags für Notizen, die nachts ins
  Archiv gewandert sind, holt sie deshalb beim nächsten Öffnen nach. Weil sich dadurch der
  Text der Zustimmung geändert hat, fragt die App einmal neu.
- `SCHEMA.md` in Drive nennt die richtigen Notiztypen und sagt, dass die App nur ihre
  eigenen Dateien sieht.

## Alpha 9 (2026-09-25)

Datenschutz und Lizenzen nachgeschärft.

- Die KI auf dem Gerät fragt vor dem ersten Einsatz um Zustimmung: Die Schnittstelle ML Kit
  schickt Google Kennzahlen über ihre Nutzung. Ohne Zustimmung ruft die App ML Kit gar nicht
  erst auf, auch nicht zum Prüfen, was das Gerät kann. Wer die KI schon benutzt hat, wird
  einmal beim Start gefragt.
- Die Zustimmung samt Angabe zur Volljährigkeit wird mit einem Schlüssel aus dem geschützten
  Schlüsselspeicher des Geräts versiegelt. Passt das Siegel nicht (verändert, von einem anderen
  Gerät, zu einem anderen Text), fragt die App neu; Ausschalten löscht Zustimmung und
  Schlüssel.
- Der Schalter „Verarbeitung im Netz" erklärt jetzt, was er erlaubt und was dabei an Google
  geht, statt eines Platzhalters.
- Android nimmt die Daten der App nicht mehr in seine Cloud-Sicherung mit; der Umzug von
  Gerät zu Gerät bleibt.
- Unter „Über die App" stehen Hinweise zur Testfassung, die Datenschutzerklärung und das
  Impressum; die Seite „Lizenzen" zeigt die GPL, die zusätzliche Erlaubnis und die
  NOTICE-Dateien der Bibliotheken.
- Die Schrift ist jetzt die unveränderte Datei aus Google Fonts.
- Die Beschriftungen der Wischgesten tragen ihre Umlaute („Nächste Stufe“, „Endgültig löschen“,
  „Zurückholen“).

## Alpha 8 (2026-09-25)

Erste öffentliche Fassung, als Testfassung auf GitHub.

- Drei Stufen (Eingang, Workspace, Archiv) mit Wischgesten, nächtliches Auto-Archiv mit
  Rückgängig, Papierkorb mit Frist
- Ordnermodus als zweites, getrenntes System: Ordnerbaum, eigenes Archiv, Papierkorb mit
  früherem Inhalt, Ordnerfarben, eigene Reihenfolge per Ziehen
- Editor: Text mit Auszeichnung, Listen mit Lese- und Bearbeitungszustand, Bilder auch als
  Hintergrund, Erinnerungen auf die Minute
- Sprachnotizen: Aufnahme, Erkennung auf dem Gerät, Wellenform, Pausen beim Abhören überspringen,
  Rohtranskript und bearbeitete Fassung
- Volltextsuche mit Filtern nach Stufe, Tag, Farbe, Ordner und Papierkorb
- KI auf dem Gerät für Titel und Aufbereiten, abschaltbar; Gerätestand beim Start
- Übersetzung über das System, die KI auf dem Gerät oder ML Kit (nur nach Zustimmung)
- Abgleich mit Google Drive (Schema 5), Sicherung als `.notesbak`, tägliche Snapshots
- Sperre mit Fingerabdruck, Gesicht oder Bildschirmsperre; Aufnahmeschutz
- Webseite `innotebox.de`
- Seite „Lizenzen" unter „Über die App" mit allen Bibliotheken, ihren Lizenztexten und der
  Drittsoftware aus ML Kit und den Play-Diensten
