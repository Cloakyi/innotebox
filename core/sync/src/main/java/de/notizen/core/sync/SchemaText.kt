package de.notizen.core.sync

/**
 * Der Inhalt von `SCHEMA.md` im Drive-Ordner (SYNC.md 8).
 *
 * Liegt selbstbeschreibend neben den Daten, damit ein Assistent mit
 * Drive-Zugriff das Format versteht, ohne dass es ihm im Chat erklaert werden
 * muss. Eine Kurzfassung von SYNC.md, nichts Eigenes: Was hier steht, muss
 * dort stehen.
 */
val SCHEMA_TEXT: String = """
# InNoteBox in Google Drive, Schema 5

Dieser Ordner ist der Spiegel einer Notizen-App. Jede Notiz, jeder Ordner und jeder Tag ist
eine eigene JSON-Datei. Der Dateiname ist der Schluessel: `notes/<uuid>.json`,
`folders/<uuid>.json`, `tags/<uuid>.json`. Anhaenge liegen unter `attachments/<uuid>.<ext>`.

## Die Huelle

Jede Datei hat dieselbe aeussere Form:

```json
{
  "schemaVersion": 5,
  "id": "<uuid>",
  "type": "NOTE",
  "rev": 17,
  "lastEditor": "<deviceId oder assistant>",
  "origin": "APP",
  "state": "ACTIVE",
  "stateChangedAt": 1789398014690,
  "updatedAt": 1788772728252,
  "purgeAfter": null,
  "payload": { }
}
```

- `type`: NOTE, FOLDER oder TAG.
- `rev`: Zaehler. Wer schreibt, setzt `rev` auf den bisherigen Wert plus eins.
- `state`: ACTIVE (lebt), TRASHED (Papierkorb, wiederherstellbar), DELETED (endgueltig
  geloescht, Grabstein). Bei DELETED ist `payload` null.
- `stateChangedAt`, `updatedAt`, `purgeAfter`: UTC-Millisekunden.
- `origin`: APP oder EXTERNAL. Wer von aussen schreibt, setzt EXTERNAL.
- Zeitstempel entscheiden nie ueber Vorrang, nur `rev`.

## payload einer Notiz (NOTE)

`stage` (INBOX, WORKSPACE, ARCHIVE), `type` (TEXT, CHECKLIST, AUDIO, IMAGE, DRAWING),
`title`, `body` (Markdown), `colorId`, `isFavorite`, `favoritedAt`, `folderId`, `sortIndex`,
`backgroundAttachmentId`, `createdAt`, `stageChangedAt`, `tagIds` (Liste von Tag-uuids),
`items` (Checkliste: id, text, isChecked, position), `attachments` (id, mimeType,
sizeBytes, hash, remoteId, role), `transcripts`, `reminders` (id, triggerAt).
Seit Schema 5: `ordnerArchiviertAt` (null = im normalen Ordnerbaum, sonst UTC-Millis, seit
wann die Notiz im Archiv des Ordnermodus liegt; unabhaengig von `stage`), `herkunftOrdnerId`
(der Ordner, aus dem archiviert wurde), `ehemaligerOrdnerId` (der geloeschte Ordner, aus dem
die Notiz herausgerueckt ist). Fehlen die drei, gelten sie als null.

## payload eines Ordners (FOLDER)

`parentId` (null = oberste Ebene), `name`, `iconOrEmoji`, `colorArgb`, `sortIndex`,
`createdAt`. Seit Schema 5: `bereich` (ORDNER oder ARCHIV; das Archiv hat einen eigenen
Baum, `parentId` zeigt immer auf einen Ordner desselben Bereichs; fehlt es, gilt ORDNER) und
`ehemaligerElternId` (der geloeschte Ordner, aus dem dieser herausgerueckt ist, sonst null).

## payload eines Tags (TAG)

`name`, `colorArgb`, `iconOrEmoji`, `sortIndex`, `createdAt`.

## index.json

`schemaVersion`, `purgeWatermark` und `devices` (deviceId, label, lastSeenAt). Nicht
anfassen.

## Was ein Assistent darf

- Eine neue Notiz anlegen: neue uuid als Dateiname, `rev: 1`, `origin: "EXTERNAL"`,
  `lastEditor: "assistant"`, `state: "ACTIVE"`, vollstaendiger payload.
- Den payload einer bestehenden Notiz aendern und dabei `rev` um eins erhoehen,
  `updatedAt` und `lastEditor` setzen.
- `state` auf TRASHED setzen (mit `rev` plus eins und neuem `stateChangedAt`).

## Was ein Assistent nicht darf

- `state` auf DELETED setzen. Ein Assistent loescht nie endgueltig, er legt in den Papierkorb.
- Eine Datei aus diesem Ordner entfernen.
- `index.json` oder `SCHEMA.md` aendern.
- Irgendetwas im Ordner `InNoteBox-Backup` anfassen.

Vollstaendig beschrieben in SYNC.md des Projekts.
""".trimIndent()
