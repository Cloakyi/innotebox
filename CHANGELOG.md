# Änderungen

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
  feststeht, ob gesperrt wird, liegt eine Abdeckung über der App. Mit eingeschalteter Sperre
  zeigt die Übersicht der letzten Apps kein Vorschaubild mehr.
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
