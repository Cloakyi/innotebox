# Sicherheit

InNoteBox hält Notizen, Aufnahmen und Bilder, die niemand anderen etwas angehen. Wenn du eine
Schwachstelle findest, freuen wir uns, davon zu erfahren, und zwar bevor alle anderen davon
erfahren.

## Eine Lücke melden

**Bitte nicht als öffentliches Issue.** Melde sie vertraulich auf einem dieser Wege:

- über GitHub: [Sicherheitslücke melden](https://github.com/Cloakyi/innotebox/security/advisories/new)
  (Reiter **Security**, dann **Report a vulnerability**),
- oder per Mail an owner@cloaki.de mit dem Betreff „Sicherheit InNoteBox“.

Hilfreich sind:

- was die Lücke ermöglicht und unter welchen Bedingungen,
- die Schritte, um sie nachzustellen,
- Handy, Android-Version und Fassung der App.

Du bekommst innerhalb einer Woche eine Antwort. Wir halten dich auf dem Laufenden, bis die
Lücke behoben ist, und nennen dich im Release, wenn du das möchtest.

## Welche Fassungen

Unterstützt wird nur die jeweils neueste Fassung unter
[Releases](https://github.com/Cloakyi/innotebox/releases). Ältere Fassungen bekommen keine
Korrekturen; ihre APK wird mit jedem neuen Release entfernt.

## Was dazugehört

Alles, was die App selbst tut: die Sperre und den Aufnahmeschutz, das Einlesen von
Sicherungen und geteilten Inhalten, den Abgleich mit dem eigenen Google Drive, den Umgang mit
Dateien und Berechtigungen, und den Bau der APK auf GitHub.

Nicht dazu gehören Lücken in Android, in den Diensten von Google oder in Bibliotheken anderer.
Die meldest du bitte dort; wenn InNoteBox sie verschlimmert, sag es uns trotzdem.

## Was bekannt ist

- Die Datenbank auf dem Gerät ist noch nicht verschlüsselt. Die Sperre schützt die
  Oberfläche, nicht die Datei. Das steht als Nächstes auf der Liste.
- Ist die Sperre an, ist beim Öffnen der Übersicht der letzten Apps direkt aus InNoteBox
  heraus noch der Inhalt zu sehen.

## Herkunft der APK prüfen

Ab Alpha 11 baut und signiert GitHub jede APK aus dem Quelltext und bescheinigt ihre Herkunft.
Ob eine APK echt ist, prüfst du mit der Befehlszeile von GitHub:

```
gh attestation verify InNoteBox-Alpha-12.1.apk --repo Cloakyi/innotebox
```

Der Fingerabdruck des Signaturschlüssels (SHA-256) lautet
`26:6B:54:8B:B3:CE:C3:A2:C4:55:DB:A9:F1:00:35:D4:A2:44:01:CE:F1:9F:85:D4:87:E3:B1:74:3C:D3:40:90`.
