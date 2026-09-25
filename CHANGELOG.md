# Änderungen

## Alpha 9 (2026-09-25)

Datenschutz und Lizenzen nachgeschärft.

- Die KI auf dem Gerät fragt vor dem ersten Einsatz um Zustimmung: Die Schnittstelle ML Kit
  schickt Google Kennzahlen über ihre Nutzung. Ohne Zustimmung ruft die App ML Kit gar nicht
  erst auf, auch nicht zum Prüfen, was das Gerät kann. Wer die KI schon benutzt hat, wird
  einmal beim Start gefragt.
- Die Zustimmung samt Angabe zur Volljährigkeit wird mit einem Schlüssel aus dem
  Sicherheitschip des Geräts versiegelt. Passt das Siegel nicht (verändert, von einem anderen
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
