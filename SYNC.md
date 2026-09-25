# Abgleich und Sicherung: das Datenformat (Schema 5)

Dieses Dokument ist der Vertrag zwischen allen Programmen, die den Bestand von InNoteBox
lesen oder schreiben: der Android-App, einem späteren Desktop- oder Web-Client und
Assistenten mit Zugriff auf den Drive-Ordner. Was hier steht, gilt; was der Code anders
macht, ist ein Fehler im Code.

Abschnitte 1 bis 13 beschreiben den Abgleich über Google Drive und die Sicherung. Abschnitt
14 führt jedes Feld jeder Tabelle auf, Abschnitt 15 die Sicherungsdatei.

**Jede Änderung an diesem Dokument hebt `schemaVersion` an**, auch eine additive.

---

## 1. Die vier Grundregeln

1. **Abwesenheit ist niemals eine Information.** Eine fehlende Datei bedeutet weder
   „gelöscht" noch „neu". Aus einer fehlenden Datei wird nie eine Aktion abgeleitet. Fehlt
   eine Datei, die es nach der lokalen Buchführung geben müsste, ist das ein Befund für den
   Prüfbericht (Abschnitt 9), und die Selbstheilung lädt sie neu hoch.
2. **Löschen ist ein Zustand, kein Entfernen.** Der Papierkorb ist `TRASHED`, das endgültige
   Löschen ist `DELETED`. Beides ist eine Datei, die weiter existiert. Der Papierkorb wird
   geleert, indem `state` wechselt, nicht indem eine Datei verschwindet.
3. **Verglichen wird `rev`, nie Existenz und nie Zeit.** Jede Entität trägt einen Zähler.
   Zeitstempel dienen der Anzeige und höchstens als Gleichstandsentscheider.
4. **Genau eine Stelle entfernt Dateien endgültig**: der Purge (Abschnitt 7). Keine andere
   Funktion darf eine Entitätsdatei aus Drive löschen.

---

## 2. Spiegel und Backup, zwei Ordner

| | Spiegel | Backup |
|---|---|---|
| Ordner | `/InNoteBox/` | `/InNoteBox-Backup/` |
| Inhalt | aktueller Arbeitsstand | eingefrorene Tagesstände |
| Schreibzeitpunkt | sofort bei jeder Änderung | einmal täglich beim ersten App-Start, oder auf Knopfdruck |
| Veränderbar | ja | nein, niemals |

**Die Abgleichschicht hat keinen Schreibzugriff auf den Backup-Ordner.** Im Code ist das
eine eigene Klasse (`Schnappschuss`) mit eigenem Drive-Ordner; `Abgleich` kennt den
Backup-Ordner nicht.

Ein Ordner `Notizen-App` aus einer früheren Fassung des Formats (Schema 3) bleibt
unangetastet liegen. Er wird weder gelesen noch geschrieben noch gelöscht (Abschnitt 11).

---

## 3. Ablage im Spiegel

```
/InNoteBox/
├── SCHEMA.md                 Beschreibung des Formats im Klartext, für Menschen und Assistenten
├── index.json                Schema-Version, Geräteliste, Purge-Wasserzeichen
├── notes/<uuid>.json         eine Datei je Notiz
├── folders/<uuid>.json       eine Datei je Ordner
├── tags/<uuid>.json          eine Datei je Tag
└── attachments/<uuid>.<ext>  Bilder und Aufnahmen, unveränderlich
```

Es gibt keine Sammeldateien (`folders.json`, `tags.json`, `tombstones.json`). Die Grabsteine
sind Entitätsdateien mit `state: DELETED`.

**Der Dateiname ist der Schlüssel, nie die Drive-Kennung.** `notes/<uuid>.json` findet sich
auch dann wieder, wenn ein Assistent die Datei neu angelegt hat (dabei ändert sich die
Drive-Kennung) oder die lokale Buchführung verloren ging. Ein Client darf sich die
Drive-Kennung merken, um beim Schreiben einen Suchlauf zu sparen, muss aber bei jedem Lauf
über die Dateiliste prüfen, ob sie noch gilt, und sonst über den Namen gehen.

### 3.1 Die Hülle

Notiz, Ordner und Tag benutzen dieselbe äußere Form. Nur `payload` unterscheidet sich.

```json
{
  "schemaVersion": 5,
  "id": "734dd71f-64af-4296-8b9e-f7ce84d64b21",
  "type": "NOTE",
  "rev": 17,
  "lastEditor": "a3f2c1d0",
  "origin": "APP",
  "state": "ACTIVE",
  "stateChangedAt": 1789398014690,
  "updatedAt": 1788772728252,
  "purgeAfter": null,
  "payload": { }
}
```

| Feld | Bedeutung |
|---|---|
| `type` | `NOTE`, `FOLDER` oder `TAG` |
| `rev` | Zähler, steigt bei jedem Schreiben. Regel: `rev = max(lokal, entfernt) + 1` |
| `lastEditor` | `deviceId` des Clients, der zuletzt geschrieben hat; `assistant` für Assistenten |
| `origin` | `APP` oder `EXTERNAL` (Abschnitt 8) |
| `state` | `ACTIVE`, `TRASHED`, `DELETED` |
| `stateChangedAt` | wann `state` zuletzt gewechselt hat, UTC-Millis |
| `updatedAt` | letzte Inhaltsänderung, UTC-Millis. Nur zur Anzeige und als Gleichstandsentscheider |
| `purgeAfter` | nur bei `DELETED`: ab wann der Purge die Datei entfernen darf, sonst `null` |
| `payload` | bei `DELETED` `null`, sonst vollständig |

**Es gibt kein `ARCHIVED`.** Die App führt zwei getrennte Ordnungssysteme (Stufen und
Ordner) mit je eigenem Archiv; beides sind Nutzdaten im `payload` (`stage` und die
Archivfelder des Ordnermodus, Abschnitt 13). Der Abgleich weiß nichts davon.

### 3.2 Die drei Zustände

| `state` | Bedeutung | `payload` | sichtbar |
|---|---|---|---|
| `ACTIVE` | lebt | vollständig | je nach `payload` in Stufe oder Ordner |
| `TRASHED` | im Papierkorb, wiederherstellbar | vollständig | Papierkorb |
| `DELETED` | endgültig gelöscht, nur noch Grabstein | `null` | nirgends |

Lokal entspricht `TRASHED` dem gesetzten `deletedAt` an der Zeile, und `DELETED` einem
Eintrag in der Grabsteintabelle `tombstones` (die Entitätszeile selbst ist weg, Abschnitt
14.13). Beide Übergänge gehen für alle drei Typen nach Drive.

### 3.3 `payload` je Typ

**`NOTE`** ist das Notizdokument aus Abschnitt 14.15 ohne `schemaVersion`, `id`,
`updatedAt` und `deletedAt` (die stehen in der Hülle). Also: `stage`, `type`, `title`,
`body`, `colorId`, `isFavorite`, `favoritedAt`, `folderId`, `sortIndex`,
`backgroundAttachmentId`, `createdAt`, `stageChangedAt`, `tagIds`, `items`, `attachments`,
`transcripts`, `reminders`, und seit Schema 5 `ordnerArchiviertAt`, `herkunftOrdnerId`,
`ehemaligerOrdnerId` (Abschnitt 13). `attachments[].remoteId` ist die Drive-Kennung der
Datei im Ordner `attachments/`; sie darf fehlen, dann findet der Leser die Datei über den
Namen `<anhangId>.<ext>`.

**`FOLDER`**: `parentId`, `name`, `iconOrEmoji`, `colorArgb`, `sortIndex`, `createdAt`
(Abschnitt 14.11 ohne `deletedAt`), seit Schema 5 dazu `bereich` und `ehemaligerElternId`
(Abschnitt 13).

**`TAG`**: `name`, `colorArgb`, `iconOrEmoji`, `sortIndex`, `createdAt` (Abschnitt 14.3
ohne `deletedAt`).

### 3.4 `index.json`

```json
{
  "schemaVersion": 5,
  "purgeWatermark": 1786800000000,
  "devices": [
    { "deviceId": "a3f2c1d0", "label": "Pixel 10", "lastSeenAt": 1789449818376 }
  ]
}
```

Die einzige Datei mit mehreren Schreibern. Sie ist winzig und enthält keine Nutzdaten; geht
sie kaputt, wird sie aus dem Bestand neu geschrieben. Jeder Client trägt sich beim ersten
Lauf ein und setzt bei jedem Lauf sein `lastSeenAt`. `deviceId` ist eine zufällige Kennung,
die der Client bei der Installation erzeugt und behält; `label` ist der Gerätename zur
Anzeige.

---

## 4. Lokale Buchführung

Je Entität führt der Client `baseRev`: den Zählerstand, mit dem zuletzt erfolgreich
abgeglichen wurde. In der App steht er in `sync_state.baseRev` (Abschnitt 14.12).

**Lokal geändert** heißt: `sync_state.syncStatus = DIRTY`. Der Status wird **in derselben
Datenbanktransaktion** gesetzt wie die Änderung selbst. Damit ist `sync_state` mit Status
`DIRTY` die Outbox: Entweder die Notiz ist gespeichert **und** steht zum Hochladen an, oder
keines von beidem. Der Uploader setzt `SYNCED` erst, wenn Drive den Upload bestätigt hat.

Für eine `DELETED`-Entität liegt der Grabstein in `tombstones` (Typ, Kennung, `deletedAt`,
`rev`). Er bleibt liegen, bis sein `deletedAt` älter ist als `purgeWatermark`.

Der Sync-Status der App ist damit exakt: Keine `DIRTY`-Einträge und letzte Prüfung ohne
Befund = `SYNCED`. Sonst `PENDING` (mit Zahl), `OFFLINE` oder `ERROR`.

---

## 5. Hochladen sofort, nicht nach Zeitplan

Es gibt keinen Takt. Der Uploader läuft als Hintergrundauftrag mit der einzigen Bedingung
„Netz vorhanden" (auf Wunsch: „nur WLAN") und wird ausgelöst von

1. jeder Änderung an einer Notiz, einem Ordner oder Tag (entprellt, ein paar Sekunden),
2. der Rückkehr des Netzes (die Bedingung des Auftrags erledigt das),
3. jedem App-Start und jedem Verlassen der App,
4. dem Antippen des Sync-Symbols.

Fehlgeschlagene Versuche bleiben stehen und werden mit wachsendem Abstand wiederholt.
Solange ein Editor offen ist, wird nicht hochgeladen; beim Schließen geht alles in einem
Zug.

---

## 6. Der Abgleich

Für jede Entität, die lokal oder entfernt bekannt ist, gilt genau einer dieser Fälle:

| lokal | entfernt | Ergebnis |
|---|---|---|
| unbekannt | vorhanden | herunterladen (echte Neuanlage) |
| `SYNCED`, `baseRev = r` | `rev = r` | nichts tun (fehlende Anhangsdateien nachholen) |
| `DIRTY`, `baseRev = r` | `rev = r` oder fehlt | hochladen mit `rev = r + 1` |
| `SYNCED`, `baseRev = r` | `rev > r` | herunterladen, `baseRev = rev` |
| `DIRTY`, `baseRev = r` | `rev > r` | **Konflikt** |
| bekannt (`SYNCED`) | fehlt | Befund „Datei fehlt"; Selbstheilung lädt neu hoch |

**Beim Schreiben gilt immer `rev = max(lokal, entfernt) + 1`.**

### 6.1 Konfliktauflösung, in dieser Reihenfolge

1. Steht eine Seite auf `DELETED`, gewinnt `DELETED`. Endgültiges Löschen wird nie
   überstimmt.
2. Steht eine Seite auf `TRASHED` und die andere hat nur bearbeitet, gewinnt `TRASHED` als
   Zustand, und der **neuere Inhalt** (nach `updatedAt`) bleibt. Es geht nichts verloren; die
   Notiz liegt im Papierkorb und ist wiederherstellbar.
3. Haben beide Seiten den Inhalt bearbeitet: Die lokale Fassung bleibt, die entfernte wird
   als **neue Notiz mit neuer UUID** angelegt, Titel mit dem Zusatz „(Konflikt TT.MM.JJJJ)",
   im Eingang. Der Nutzer sieht eine Meldung. Für Ordner und Tags gibt es keine Kopie; dort
   gewinnt die Fassung mit dem höheren `updatedAt`, bei Gleichstand die entfernte.

In allen drei Fällen wird die Entscheidung mit `rev = max + 1` hochgeladen, damit die
Gegenseite sie beim nächsten Lauf als neuer erkennt.

### 6.2 Der Fall Geisterdatei, durchgespielt

Notiz X wird auf dem Handy in den Papierkorb gelegt und endgültig gelöscht → lokal Grabstein
mit `rev 18`. In Drive liegt `rev 17`, `ACTIVE`. Der Abgleich vergleicht 18 gegen 17, erkennt
„lokal neuer", lädt die Hülle mit `state: DELETED` und leerem `payload` hoch. Nichts wird
heruntergeladen. Die Notiz kann nicht zurückkommen, weil der Löschvorgang eine gewöhnliche,
höherwertige Änderung ist.

### 6.3 Reihenfolge eines Laufs

1. Dateilisten holen (`notes/`, `folders/`, `tags/`), `index.json` lesen.
2. Tags, dann Ordner, dann Notizen abgleichen (Notizen verweisen auf beide).
3. Je Entität den Fall aus der Tabelle oben bestimmen und ausführen. Anhänge werden vor dem
   Notizdokument hochgeladen, damit das Dokument nie auf eine Datei zeigt, die es drüben nicht
   gibt.
4. `index.json` schreiben (eigenes `lastSeenAt`).

Ein Lauf hält ein Schloss: nie zwei gleichzeitig.

### 6.4 Was nie nach Drive geht

`lastOpenedAt`, `autoArchivedBatchId`, `syncEnabled`, `calendarEnabled`, `calendarEventId`,
`localPath`, `alarmId`, `isFired`, die Protokolle der Auto-Archiv-Läufe, der Volltextindex,
`sync_state` (Abschnitt 14, Spalte „Sync"). Eine vom Abgleich ausgenommene Notiz
(`syncEnabled = false`) wird aus dem Spiegel entfernt, **ohne** Grabstein: „bleibt bei mir"
ist nicht „überall löschen". Das ist die eine Ausnahme von Regel 4, und sie betrifft nie
eine Entität, die ein anderer Client noch braucht.

---

## 7. Purge

- Beim Wechsel nach `DELETED` wird `purgeAfter = stateChangedAt + 30 Tage` gesetzt.
- Der Purge läuft im täglichen Durchlauf (Abschnitt 9) und entfernt eine Datei nur, wenn
  **alle** Bedingungen gelten:
  - `state == DELETED`,
  - `purgeAfter` liegt in der Vergangenheit,
  - **jedes** in `index.json` eingetragene Gerät hat seit dem Löschen abgeglichen
    (`lastSeenAt > stateChangedAt`).
- Danach steht `purgeWatermark` in `index.json` auf dem ältesten `stateChangedAt`, das
  noch nicht entfernt wurde (oder auf „jetzt", wenn keins übrig ist).
- Ein Client vergisst einen lokalen Grabstein erst, wenn dessen `deletedAt` älter ist als
  `purgeWatermark`.
- Anhangsdateien einer gelöschten Notiz entfernt der Client, der sie gelöscht hat, beim
  nächsten Lauf über den Namen (`<anhangId>.<ext>`). Sie sind keine Entitätsdateien, und die
  `DELETED`-Hülle kennt sie nicht mehr.

Die dritte Bedingung schützt das Handy, das drei Monate in der Schublade lag: Solange es
nicht abgeglichen hat, bleibt der Grabstein liegen und belehrt es beim nächsten Start.

---

## 8. Zugriff durch Assistenten

`SCHEMA.md` liegt selbstbeschreibend im Ordner und wiederholt diesen Abschnitt.

**Erlaubt:**
- eine neue Notiz anlegen (neue UUID, `rev: 1`, `origin: EXTERNAL`, `lastEditor:
  assistant`, `state: ACTIVE`),
- `payload` einer bestehenden Notiz ändern und dabei `rev` um 1 erhöhen,
- `state` auf `TRASHED` setzen (mit `rev + 1` und `stateChangedAt`).

**Verboten:**
- `state` auf `DELETED` setzen: ein Assistent löscht nie endgültig,
- eine Entitätsdatei aus Drive entfernen,
- `index.json` verändern,
- irgendetwas im Backup-Ordner anfassen.

Die App zeigt eine Notiz mit `origin: EXTERNAL` nach dem nächsten Abgleich mit einem kleinen
Hinweis auf der Karte. Sobald die App die Notiz selbst schreibt, wird `origin` wieder `APP`.

---

## 9. Der tägliche Durchlauf

Einmal pro Kalendertag beim ersten Öffnen der App, und auf Knopfdruck („Jetzt sichern"), in
dieser Reihenfolge:

1. **Outbox leeren**: alles Offene hochladen.
2. **Vollabgleich**: alle Dateien im Spiegel auflisten und `rev` gegen `baseRev` prüfen.
3. **Prüfbericht**: fehlende Dateien trotz Buchführung, auseinanderlaufende `rev`,
   unlesbares JSON, `folderId`- oder `tagIds`-Verweise ins Leere, Zählwerke (lokal gegen
   Drive).
4. **Selbstheilung, protokolliert**: Behebbares wird behoben (fehlende Datei neu hochladen,
   Verweis ins Leere lösen) und aufgeschrieben. Der Bericht ist über das Sync-Symbol
   einsehbar.
5. **Purge** nach Abschnitt 7.
6. **Snapshot**, nur wenn Schritt 3 ohne Befund war. Ein kaputter Stand wird nie zum Backup.

---

## 10. Snapshots

- Ablage: `/InNoteBox-Backup/YYYY-MM-DD.notesbak`, ein ZIP-Container. Auf Knopfdruck am
  selben Tag: `YYYY-MM-DD_HHMM.notesbak`.
- Inhalt: die Sicherungsdatei aus Abschnitt 15 in **Fassung 4**. Auch `TRASHED` und
  `DELETED` sind dabei.
- **Aufbewahrung**: die letzten 7 Tage, dazu 4 Wochenstände (je Montag) und 12 Monatsstände
  (je Monatserster). Was darüber hinaus alt ist, wird beim Schreiben des nächsten Snapshots
  entfernt. Das ist die einzige Löschung im Backup-Ordner, und sie trifft nur Snapshots.
- **Wiederherstellung**: ein Snapshot wird eingelesen (Regeln aus Abschnitt 15.3: nie
  löschen, neuerer Stand gewinnt), `rev` bleibt erhalten. Anschließend wird jede übernommene
  Entität als `DIRTY` markiert und geht mit `rev + 1` hoch, damit der Spiegel den
  wiederhergestellten Stand übernimmt und nicht umgekehrt.
- Ein Snapshot wird nie verändert und nie von der Abgleichschicht angefasst.
- Gilt „nur im WLAN" und das Netz ist getaktet, bleibt der Snapshot aus, und der Tag gilt
  nicht als erledigt; beim nächsten Öffnen im WLAN wird er nachgeholt.

---

## 11. Umstellung von Schema 3

Schema 3 hielt den Bestand in Sammeldateien mit Zeitstempeln und einer Grabsteinliste.
Beim ersten Start nach dem Wechsel läuft einmalig und lokal:

1. Die lokale Datenbank ist die Wahrheit. `sync_state` wird auf `DIRTY`, `baseRev = 0` und
   ohne Drive-Kennung gesetzt; `attachments.remoteId` wird geleert. Damit geht beim nächsten
   Lauf alles als Schema 4 in den neuen Ordner `InNoteBox`.
2. Grabsteine aus `tombstones` bekommen `rev = 1` und gehen als `DELETED`-Dateien hoch.
3. Der alte Ordner `Notizen-App` wird nicht angefasst. Wer ihn nicht mehr braucht, löscht
   ihn selbst.

Notizen, die nur in Drive und auf keinem Gerät lagen, kommen dabei nicht mit; das ist ein
bewusster Schnitt.

---

## 12. Prüfszenarien

Das System gilt als fertig, wenn diese Szenarien als Tests mit zwei simulierten Clients
laufen:

1. Notiz auf A löschen, B war offline → nach dem Abgleich ist sie auf B weg und kommt nicht
   zurück.
2. Notiz auf A bearbeiten, gleichzeitig auf B in den Papierkorb → im Papierkorb, Bearbeitung
   erhalten.
3. Papierkorb auf A leeren, B meldet sich 30 Tage später → nichts kommt zurück.
4. App während des Uploads beenden → der Eintrag steht noch an und geht beim nächsten Lauf.
5. Eine Notizdatei in Drive kaputtmachen → Prüfbericht meldet es, kein Snapshot.
6. Assistent legt über Drive eine Notiz an → erscheint mit Hinweis auf externe Herkunft.
7. Snapshot auf leerem Gerät wiederherstellen → identischer Bestand, Spiegel wird
   überschrieben statt überstimmt.

---

## 13. Anhebung auf Schema 5

Fünf Felder für das Archiv im Ordnermodus und den Papierkorb, alle additiv. Ein Client
auf Schema 4 liest ein Schema-5-Dokument, ohne etwas zu verlieren, und ein Schema-4-Dokument
gilt mit den Standardwerten. **Der Abgleich deutet keines davon**; es sind Nutzdaten wie
`stage`, und die Regeln aus Abschnitt 6 bleiben unverändert.

Im `payload` einer **Notiz**:

| Feld | Typ | Default | Bedeutung |
|---|---|---|---|
| `ordnerArchiviertAt` | Long? | `null` | `null` = im normalen Ordnerbaum, sonst im Archivbaum des Ordnermodus (UTC-Millis). Getrennt von `stage`: eine Notiz kann im Fluss im Eingang liegen und im Ordnersystem archiviert sein |
| `herkunftOrdnerId` | String? | `null` | der Ordner, aus dem heraus archiviert wurde, für „Zurück nach …" |
| `ehemaligerOrdnerId` | String? | `null` | der gelöschte Ordner, aus dem die Notiz beim Löschen „nur der Ordner" herausgerückt ist. Wird beim nächsten Verschieben von Hand geleert |

Im `payload` eines **Ordners**:

| Feld | Typ | Default | Bedeutung |
|---|---|---|---|
| `bereich` | `ORDNER` oder `ARCHIV` | `ORDNER` | welchem Baum der Ordner gehört. Ein Ordner wechselt den Bereich nie; `parentId` zeigt immer auf einen Ordner desselben Bereichs |
| `ehemaligerElternId` | String? | `null` | der gelöschte Ordner, aus dem dieser beim Löschen „nur der Ordner" herausgerückt ist. Wird beim nächsten Verschieben von Hand geleert |

`sortIndex` an Ordnern ist die vom Nutzer gezogene Reihenfolge und wird abgeglichen wie
jedes andere Feld.

**Lokal** (Room 6 → 7): dieselben fünf Spalten, `bereich` mit Default `'ORDNER'`, die
anderen `NULL`. Keine Umschreibung der Buchführung; ein Client, der aufsteigt, gleicht
weiter mit seinem `baseRev` ab und schreibt beim nächsten eigenen Schreiben Schema 5.

---

## 14. Die Felder im Einzelnen

Die Spalte „Sync" sagt, ob ein Feld nach Drive geht. Was mit „nein" markiert ist, bleibt auf
dem Gerät und steht in keinem Dokument im Spiegel.

### 14.1 Aufzählungstypen

Über die Leitung gehen immer die **Namen**, nie die Ordinalzahlen. Eine neue Konstante darf
hinten angehängt werden; Umbenennen oder Umsortieren ist ein Schema-Bruch.

| Typ | Werte |
|---|---|
| `Stage` | `INBOX`, `WORKSPACE`, `ARCHIVE` |
| `NoteType` | `TEXT`, `LIST`, `AUDIO`, `DRAWING`, `IMAGE` |
| `NoteColor` | `DEFAULT`, `RED`, `PINK`, `ORANGE`, `YELLOW`, `GREEN`, `DARK_GREEN`, `TEAL`, `BLUE`, `DARK_BLUE`, `PURPLE`, `DARK_PURPLE`, `BROWN`, `DARK_BROWN`, `GREY`, `BLACK` |
| `Anhangsrolle` | `INHALT`, `HINTERGRUND` |
| `Bereich` | `ORDNER`, `ARCHIV` |
| `ArchiveTrigger` | `AGE`, `COUNT`, `BOTH` (rein lokal) |
| `SyncStatus` | `SYNCED`, `DIRTY`, `CONFLICT` (rein lokal) |
| `EntityType` | `NOTE`, `TAG`, `NOTE_ITEM`, `ATTACHMENT`, `TRANSCRIPT`, `REMINDER`, `FOLDER`, `CALENDAR` |

`NoteColor` wird als **Name** gespeichert, nie als Farbwert. Nur so sieht dieselbe Notiz im
hellen und im dunklen Theme richtig aus, und ein anderer Client darf eigene Tonwerte
verwenden. Die Tonwerte sind Sache des Clients und stehen nicht in diesem Dokument.

### 14.2 `notes`

| Feld | Typ | Pflicht | Default | Sync | Bedeutung |
|---|---|---|---|---|---|
| `id` | String (UUID) | ja | — | ja | Primärschlüssel |
| `stage` | `Stage` | ja | `INBOX` | ja | Station im Fluss. Kein Ordner |
| `type` | `NoteType` | ja | `TEXT` | ja | Notizart |
| `title` | String | ja | `""` | ja | Leer erlaubt in `INBOX`, Pflicht ab `WORKSPACE` |
| `body` | String | ja | `""` | ja | Fließtext, Auszeichnung als Markdown (`**fett**`, `*kursiv*`, `__unterstrichen__`, `~~durchgestrichen~~`) |
| `colorId` | `NoteColor` | ja | `DEFAULT` | ja | `DEFAULT` = Theme-Fläche, kein fester Wert |
| `isFavorite` | Bool | ja | `false` | ja | Schützt vor Auto-Archivierung |
| `favoritedAt` | Long? | nein | `null` | ja | UTC-Millis |
| `folderId` | String? | nein | `null` | ja | Der Ordner, `null` ist der Hauptordner |
| `sortIndex` | Int | ja | `0` | ja | Für benutzerdefinierte Sortierung |
| `backgroundAttachmentId` | String? | nein | `null` | ja | Verweist auf einen eigenen Anhang der Notiz (14.6). Gesetzt = Bild als Fläche statt `colorId` |
| `createdAt` | Long | ja | — | ja | UTC-Millis |
| `updatedAt` | Long | ja | — | ja | **Nur bei echter Inhaltsänderung.** Gleichstandsentscheider der Konfliktauflösung |
| `lastOpenedAt` | Long | ja | `createdAt` | **nein** | Nur Ansehen. Steuert Auto-Archiv, pro Client verschieden |
| `stageChangedAt` | Long | ja | `createdAt` | ja | Letzter Stufenwechsel |
| `autoArchivedBatchId` | String? | nein | `null` | nein | Verweist auf `archive_runs.batchId` |
| `deletedAt` | Long? | nein | `null` | ja | Gesetzt = im Papierkorb (`TRASHED`) |
| `syncEnabled` | Bool | ja | `true` | **nein** | `false` = diese Notiz bleibt auf diesem Gerät (14.2a) |
| `calendarEnabled` | Bool | ja | `true` | **nein** | ob die Notiz im Kalender dieses Geräts erscheinen darf |
| `calendarEventId` | Long? | nein | `null` | **nein** | der Termin beim Kalender-Anbieter dieses Geräts |
| `ordnerArchiviertAt` | Long? | nein | `null` | ja | Gesetzt = im Archivbaum des Ordnermodus. Getrennt von `stage` (Abschnitt 13) |
| `herkunftOrdnerId` | String? | nein | `null` | ja | Der Ordner, aus dem archiviert wurde (Abschnitt 13) |
| `ehemaligerOrdnerId` | String? | nein | `null` | ja | Der gelöschte Ordner, aus dem die Notiz herausgerückt ist (Abschnitt 13) |

**`updatedAt` gegen `lastOpenedAt` ist die wichtigste Unterscheidung im ganzen Schema.**
Wer beim Öffnen `updatedAt` hochzählt, lässt jede angesehene Notiz wie eine bearbeitete
aussehen und erzeugt Konflikte, wo keine sind.

**Der Hintergrundverweis zeigt auf einen Anhang, nicht auf eine Adresse.** Das
Hintergrundbild ist damit dieselbe Datei, die auch im Bildraster der Notiz stehen kann,
keine zweite Kopie. Es gibt keinen Fremdschlüssel in diese Richtung: Wer den Anhang löscht,
setzt `backgroundAttachmentId` auf `null`. Zeigt der Verweis ins Leere (Datei noch nicht
heruntergeladen), fällt die Notiz still auf `colorId` zurück; ein Client darf den Zeiger
deshalb **nicht** löschen.

#### 14.2a `syncEnabled`: eine Notiz vom Abgleich ausnehmen

Die Spalte wird ausdrücklich nicht synchronisiert: Sie ist eine Aussage dieses Geräts über
sich selbst. Beim Ausschalten entfernt der nächste Abgleich die Notizdatei und ihre
Anhangsdateien aus dem Spiegel und setzt `remoteId` der Anhänge zurück; es wird **kein
Grabstein** geschrieben (Abschnitt 6.4). Beim Wiedereinschalten wird die Notiz als `DIRTY`
markiert, sonst läge sie für immer nur hier.

#### 14.2b `calendarEnabled` und `calendarEventId`

Rein lokal. Die Kennung eines Termins gilt nur auf dem Gerät, das ihn angelegt hat, und
welcher Kalender gemeint ist, weiß auch nur dieses Gerät. Der Kalender ist keine zweite
Wahrheit: Was gilt, steht in `reminders` (14.8); der Termin ist eine Spiegelung, die bei jeder
Änderung neu gezogen wird. Eine endgültig gelöschte Notiz hinterlässt lokal einen Grabstein
vom Typ `CALENDAR` mit der Terminkennung, damit der Termin aus dem Kalender verschwindet.

### 14.3 `tags`

| Feld | Typ | Pflicht | Default | Sync | Bedeutung |
|---|---|---|---|---|---|
| `id` | String (UUID) | ja | — | ja | |
| `name` | String | ja | — | ja | Eindeutig ohne Beachtung der Groß- und Kleinschreibung |
| `colorArgb` | Int | ja | — | ja | Färbt nur Chip und Punkt, nie die Karte |
| `iconOrEmoji` | String? | nein | `null` | ja | |
| `sortIndex` | Int | ja | `0` | ja | |
| `createdAt` / `updatedAt` | Long | ja | — | ja | |
| `deletedAt` | Long? | nein | `null` | ja | |

Anders als bei Notizen ist `colorArgb` hier ein echter Farbwert: Eine Tagfarbe wählt der
Nutzer frei, sie ist keine Position in einer Palette.

### 14.4 `note_tags`

| Feld | Typ | Pflicht | Sync | Bedeutung |
|---|---|---|---|---|
| `noteId` | String | ja | ja | Teil des Primärschlüssels |
| `tagId` | String | ja | ja | Teil des Primärschlüssels |
| `position` | Int | ja | ja | `0` = erster Tag |

Im Drive-Format kein eigenes Dokument, sondern die geordnete Liste `tagIds` in der
Notizdatei. Die Zuordnung gehört zur Notiz, nicht zum Tag.

### 14.5 `note_items`: Listeneinträge

| Feld | Typ | Pflicht | Default | Sync |
|---|---|---|---|---|
| `id` | String (UUID) | ja | — | ja |
| `noteId` | String | ja | — | ja |
| `text` | String | ja | `""` | ja |
| `isChecked` | Bool | ja | `false` | ja |
| `position` | Int | ja | — | ja |

Liegt eingebettet in der Notizdatei. Konflikte werden auf Notizebene gelöst, nicht je
Eintrag.

### 14.6 `attachments`

| Feld | Typ | Pflicht | Default | Sync | Bedeutung |
|---|---|---|---|---|---|
| `id` | String (UUID) | ja | — | ja | |
| `noteId` | String | ja | — | ja | |
| `localPath` | String | ja | — | **nein** | Gerätepfad, für den anderen Client bedeutungslos |
| `mimeType` | String | ja | — | ja | |
| `sizeBytes` | Long | ja | — | ja | |
| `hash` | String | ja | — | ja | Inhaltshash, erspart erneutes Hochladen |
| `remoteId` | String? | nein | `null` | ja | Drive-Kennung der Datei; darf fehlen |
| `role` | `Anhangsrolle` | ja | `INHALT` | ja | `INHALT` = im Bildraster der Notiz sichtbar. `HINTERGRUND` = ausschließlich Fläche, nicht im Raster |

**`role` und `backgroundAttachmentId` beantworten zwei verschiedene Fragen.** `role` sagt,
ob ein Anhang im Bildraster auftaucht; `notes.backgroundAttachmentId` sagt, welcher Anhang
gerade die Fläche ist:

| Fall | `role` | im Raster? | Fläche? |
|---|---|---|---|
| Bild der Notiz | `INHALT` | ja | nein |
| Bild der Notiz, zugleich Fläche | `INHALT` | ja, mit Marke | ja |
| eigens gewähltes Hintergrundbild | `HINTERGRUND` | nein | ja |

Ein `HINTERGRUND`-Anhang, auf den `backgroundAttachmentId` nicht zeigt, gilt als verwaist
und darf aufgeräumt werden. Für `INHALT` gilt das ausdrücklich nicht.

**Bilder liegen normalisiert.** Jedes Bild wird beim Import einmal dekodiert, auf höchstens
2048 Pixel lange Kante verkleinert und als JPEG neu geschrieben, mit aufgelöster
EXIF-Drehung. Ein Client speichert nie die Originaldatei; jeder Client darf die Datei ohne
Drehungslogik anzeigen. `hash` ist der Hash der normalisierten Datei und keine
Dublettenerkennung.

### 14.7 `transcripts`

| Feld | Typ | Pflicht | Default | Sync | Bedeutung |
|---|---|---|---|---|---|
| `id` | String (UUID) | ja | — | ja | |
| `noteId` | String | ja | — | ja | |
| `startMs` / `endMs` | Long | ja | — | ja | Versatz innerhalb der Aufnahme |
| `text` | String | ja | — | ja | Rohtranskript, bleibt immer erhalten |
| `speakerLabel` | String? | nein | `null` | ja | bleibt leer |
| `soundLabel` | String? | nein | `null` | ja | bleibt leer |
| `isFinal` | Bool | ja | `false` | ja | `false` = Zwischenergebnis |

`speakerLabel` und `soundLabel` sind Platzhalter für eine spätere Sprecher- und
Geräuscherkennung. Kein Client darf sie befüllen, solange das nicht festgelegt ist. Das
Rohtranskript wird nie überschrieben; die bearbeitete Fassung steht in `body`.

### 14.8 `reminders`

| Feld | Typ | Pflicht | Default | Sync | Bedeutung |
|---|---|---|---|---|---|
| `id` | String (UUID) | ja | — | ja | |
| `noteId` | String | ja | — | ja | |
| `triggerAt` | Long | ja | — | ja | UTC-Millis |
| `alarmId` | Int | ja | — | **nein** | Weckerkennung des Systems, rein lokal |
| `isFired` | Bool | ja | `false` | **nein** | Jedes Gerät weckt für sich |

Eine noch nicht ausgelöste Erinnerung schützt die Notiz vor Auto-Archivierung. Erinnerungen
mit vergangenem Termin werden beim Empfang nicht gestellt.

### 14.9 `archive_runs`: Protokoll der Auto-Archiv-Läufe (rein lokal)

| Feld | Typ | Pflicht | Default |
|---|---|---|---|
| `batchId` | String (UUID) | ja | — |
| `runAt` | Long | ja | — |
| `noteCount` | Int | ja | — |
| `trigger` | `ArchiveTrigger` | ja | — |
| `undoneAt` | Long? | nein | `null` |

Was der Lauf bewirkt hat (die neue `stage`), synchronisiert über `notes`.

### 14.10 `archive_run_items` (rein lokal)

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `batchId` | String | ja | Teil des Primärschlüssels |
| `noteId` | String | ja | Teil des Primärschlüssels |
| `previousStage` | `Stage` | ja | Stufe vor dem Lauf |

Ohne `previousStage` ließe sich ein Lauf nicht zurücknehmen: `archive_runs` kennt nur
Zählwerte, und `notes.autoArchivedBatchId` sagt nur, dass eine Notiz betroffen war, nicht,
wohin sie zurückgehört.

### 14.11 `folders`

| Feld | Typ | Pflicht | Default | Sync |
|---|---|---|---|---|
| `id` | String (UUID) | ja | — | ja |
| `parentId` | String? | nein | `null` | ja |
| `name` | String | ja | — | ja |
| `iconOrEmoji` | String? | nein | `null` | ja |
| `colorArgb` | Int? | nein | `null` | ja |
| `sortIndex` | Int | ja | `0` | ja |
| `createdAt` / `updatedAt` | Long | ja | — | ja |
| `deletedAt` | Long? | nein | `null` | ja |
| `bereich` | `Bereich` | ja | `ORDNER` | ja (Abschnitt 13) |
| `ehemaligerElternId` | String? | nein | `null` | ja (Abschnitt 13) |

**Der Baum hat keinen Fremdschlüssel auf sich selbst.** Beim Abgleich kann ein Kind vor
seinem Elternteil ankommen. Ein Ordner, dessen `parentId` ins Leere zeigt, hängt sichtbar an
der obersten Ebene. **Der Inhalt eines gelöschten Ordners rückt eine Ebene höher**, Notizen
wie Unterordner. **Eine Notiz liegt in höchstens einem Ordner**; Tags darf sie beliebig viele
tragen.

### 14.12 `sync_state` (rein lokal)

| Feld | Typ | Pflicht | Default | Bedeutung |
|---|---|---|---|---|
| `entityType` | `EntityType` | ja | — | Teil des Primärschlüssels |
| `entityId` | String | ja | — | Teil des Primärschlüssels |
| `remoteId` | String? | nein | `null` | Drive-Kennung, nur zum Schreiben (Abschnitt 3) |
| `remoteRevision` | String? | nein | `null` | Drive-Revision beim letzten Abgleich |
| `baseRev` | Long | ja | `0` | Zählerstand beim letzten erfolgreichen Abgleich (Abschnitt 4) |
| `localUpdatedAt` | Long | ja | `0` | `updatedAt` beim letzten Abgleich |
| `lastSyncedAt` | Long? | nein | `null` | |
| `syncStatus` | `SyncStatus` | ja | `DIRTY` | `DIRTY` = steht zum Hochladen an |

Geht nie nach Drive: Sonst würde jeder Client die Buchführung des anderen überschreiben.

### 14.13 `tombstones`: endgültige Löschungen (rein lokal)

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `entityType` | `EntityType` | ja | Teil des Primärschlüssels |
| `entityId` | String | ja | Teil des Primärschlüssels |
| `deletedAt` | Long | ja | UTC-Millis |
| `rev` | Long | ja | `baseRev + 1` beim Löschen; damit gewinnt der Grabstein gegen jede ältere Fassung |

**Zwei Stufen von Löschung:** `deletedAt` an der Entität ist der Papierkorb (`TRASHED`),
wiederherstellbar, die Zeile besteht weiter. Ein Eintrag hier ist endgültig (`DELETED`): Die
Zeile ist weg, nur die Kennung überlebt und geht als `DELETED`-Hülle nach Drive (Abschnitt
3.2). Grabsteine vom Typ `ATTACHMENT` (Kennung des Anhangs, die Datei heißt so) und
`CALENDAR` (`entityId` ist die Terminkennung) sind reine Merkzettel dieses Geräts („die
Datei muss noch weg", „der Termin muss noch weg") und gehen nie nach Drive. Ein
Grabstein wird vergessen, sobald `deletedAt` älter ist als `purgeWatermark` (Abschnitt 7).

### 14.14 `note_fts` (rein lokal)

Der Volltextindex über `title`, `body`, die Listeneinträge und die Transkripte. Eine
eigenständige Tabelle, die der Client bei jeder Schreiboperation selbst mitpflegt; sie wird
nie synchronisiert und beim Einlesen einer Sicherung neu gebaut.

### 14.15 Das Notizdokument

Der `payload` einer Notiz (Abschnitt 3.3) bündelt, was auf Notizebene zusammengehört.
Einträge, Tags, Anhang-Metadaten, Transkripte und Erinnerungen liegen eingebettet, nicht als
eigene Dateien:

```json
{
  "stage": "WORKSPACE", "type": "TEXT",
  "title": "…", "body": "…",
  "colorId": "DEFAULT",
  "isFavorite": false, "favoritedAt": null,
  "folderId": null, "sortIndex": 0,
  "backgroundAttachmentId": null,
  "createdAt": 0, "stageChangedAt": 0,
  "ordnerArchiviertAt": null, "herkunftOrdnerId": null, "ehemaligerOrdnerId": null,
  "tagIds": ["…"],
  "items":       [{ "id": "…", "text": "…", "isChecked": false, "position": 0 }],
  "attachments": [{ "id": "…", "mimeType": "…", "sizeBytes": 0, "hash": "…",
                    "remoteId": "…", "role": "INHALT" }],
  "transcripts": [{ "id": "…", "startMs": 0, "endMs": 0, "text": "…",
                    "speakerLabel": null, "soundLabel": null, "isFinal": true }],
  "reminders":   [{ "id": "…", "triggerAt": 0 }]
}
```

`noteId` steht in den eingebetteten Objekten nicht, weil der Dateiname es schon sagt. Alles
mit „Sync: nein" aus Abschnitt 14 fehlt hier bewusst.

---

## 15. Die Sicherungsdatei (`.notesbak`)

Ein ZIP-Container, unverschlüsselt, mit lesbarem JSON darin. Dieselbe Datei schreibt die App
auf Wunsch des Nutzers in einen Ordner seiner Wahl und der tägliche Durchlauf als Snapshot
(Abschnitt 10).

```
manifest.json       formatVersion, schemaVersion, Datum, Herkunft, Zählwerte, SHA-256 über den Inhalt
tags.json           Array von Tagdokumenten
folders.json        Array von Ordnerdokumenten
notes.json          Array von Sicherungsnotizen (15.1)
revs.json           Zählerstände (`type`, `id`, `rev`) je Entität
deleted.json        Grabsteine (`type`, `id`, `deletedAt`, `rev`)
attachments/        eine Datei je Anhang, benannt <attachmentId>.<endung>
```

### 15.1 Eine Notiz in der Sicherung

Das Notizdokument ist Feld für Feld dasselbe wie in Drive (14.15). Drumherum liegt eine Hülle
mit dem, was der Abgleich absichtlich weglässt, und mit den Hüllenfeldern des Spiegels:

```json
{
  "notiz":          { "…": "genau 14.15" },
  "lastOpenedAt":   1787486400000,
  "syncEnabled":    true,
  "rev": 17, "state": "ACTIVE", "stateChangedAt": 1789398014690, "origin": "APP"
}
```

Eine Sicherung stellt dieses eine Gerät wieder her, deshalb geht das Gerätelokale mit:
`lastOpenedAt` steuert die automatische Archivierung (ohne ihn räumte der nächste Lauf den
halben Workspace weg), `syncEnabled` ist die ausdrückliche Ansage, dass eine Notiz nicht in
die Cloud soll. `attachments[].remoteId` steht in der Sicherung immer auf `null`; beim
nächsten Abgleich übernimmt der Upload eine Datei, die drüben schon unter demselben Namen
liegt, statt sie erneut zu schicken.

### 15.2 Was mitgeht

Alles, was Abschnitt 14 als synchronisiert führt, plus die Felder aus 15.1, plus der
Papierkorb und die Grabsteine. Nicht mitgehen: die Einstellungen der App, das Protokoll der
Auto-Archiv-Läufe, der Volltextindex (wird beim Einlesen neu gebaut), `sync_state` und bereits
ausgelöste Erinnerungen.

### 15.3 Beim Wiederherstellen wird nie gelöscht

Verglichen wird je Notiz, Ordner und Tag über `updatedAt`:

| Lage | Was geschieht |
|---|---|
| gibt es hier nicht | wird angelegt |
| Datei ist neuer | ersetzt die hiesige Fassung |
| Datei ist älter oder gleich alt | bleibt, wie es ist |

**Gleichstand heißt behalten.** Zwei Fassungen mit derselben Zeit sind dieselbe Fassung; wer
hier `>=` nähme, schriebe beim Einlesen derselben Sicherung jeden Bestand noch einmal in den
Spiegel. Was es nur hier gibt, bleibt liegen; eine vorhandene Anhangsdatei wird nie
überschrieben. Nach dem Einlesen wird `baseRev` aus `revs.json` gesetzt und jede übernommene
Entität `DIRTY` markiert, damit der nächste Abgleich mit `rev + 1` den Spiegel überschreibt
statt von ihm überstimmt zu werden (Abschnitt 10).

**Eine Notiz mit Grabstein kommt unter neuer Kennung zurück.** Gibt es eine Notiz aus der
Sicherung hier nicht mehr und trägt sie einen Grabstein, wird sie als neue Notiz angelegt,
mit neuer Kennung für sie und für alles, was an ihr hängt, und landet im Eingang. Ein
Grabstein ist die Nachricht an alle Geräte, dass es diese Notiz nicht mehr gibt; eine neue
Kennung hat keinen Grabstein, es gibt nichts zu streiten. Ohne Grabstein wird die Kennung aus
der Datei behalten, damit ein frisches Gerät seine Notizen dort wiederfindet, wo sie waren.

### 15.4 Namen aus einer fremden Datei werden geprüft

Ein Eintrag in einem ZIP-Archiv darf alles heißen, auch `attachments/../../x`. Zulässig ist
genau eine Form: ein Name direkt unter `attachments/`, ohne weitere Ordner und ohne `..`.
Alles andere wird übergangen und gezählt.

### 15.5 Fassungen

`manifest.json` trägt zwei Zahlen. `schemaVersion` sagt, wie eine Notiz darin aussieht, und
ist dieselbe wie beim Abgleich. `formatVersion` sagt, wie die Datei drumherum gebaut ist,
und steht bei **4**. Eine Datei mit höherer `formatVersion` wird abgelehnt, statt sie halb zu
verstehen. Ältere Fassungen lesen sich weiter: Fassung 1 kennt keine Ordner, Fassung 2 keine
Zählerstände und Grabsteine, Fassung 3 nicht die Felder aus Schema 5; was fehlt, gilt mit
seinem Standardwert.
