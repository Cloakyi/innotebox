package de.notizen.core.data.db

import androidx.room.TypeConverter
import de.notizen.core.data.model.Anhangsrolle
import de.notizen.core.data.model.ArchiveTrigger
import de.notizen.core.data.model.Bereich
import de.notizen.core.data.model.EntityType
import de.notizen.core.data.model.Herkunft
import de.notizen.core.data.model.NoteColor
import de.notizen.core.data.model.NoteType
import de.notizen.core.data.model.Stage
import de.notizen.core.data.model.SyncStatus

/**
 * Aufzaehlungstypen werden als NAME gespeichert, nie als Ordinalzahl.
 *
 * Das ist keine Geschmacksfrage: bei Ordinalzahlen wuerde das Einfuegen einer
 * neuen Konstante in der Mitte alle bestehenden Datensaetze stillschweigend
 * umdeuten. Mit Namen faellt ein unbekannter Wert stattdessen auf -- siehe
 * SYNC.md 14.1.
 */
class Converters {

    @TypeConverter fun stageToString(value: Stage): String = value.name

    @TypeConverter fun stringToStage(value: String): Stage = Stage.valueOf(value)

    @TypeConverter fun noteTypeToString(value: NoteType): String = value.name

    @TypeConverter fun stringToNoteType(value: String): NoteType = NoteType.valueOf(value)

    @TypeConverter fun noteColorToString(value: NoteColor): String = value.name

    @TypeConverter fun stringToNoteColor(value: String): NoteColor = NoteColor.valueOf(value)

    @TypeConverter fun triggerToString(value: ArchiveTrigger): String = value.name

    @TypeConverter fun stringToTrigger(value: String): ArchiveTrigger = ArchiveTrigger.valueOf(value)

    @TypeConverter fun herkunftToString(value: Herkunft): String = value.name

    @TypeConverter fun stringToHerkunft(value: String): Herkunft = Herkunft.valueOf(value)

    @TypeConverter fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter fun anhangsrolleToString(value: Anhangsrolle): String = value.name

    @TypeConverter fun stringToAnhangsrolle(value: String): Anhangsrolle =
        Anhangsrolle.valueOf(value)

    @TypeConverter fun entityTypeToString(value: EntityType): String = value.name

    @TypeConverter fun stringToEntityType(value: String): EntityType = EntityType.valueOf(value)

    @TypeConverter fun bereichToString(value: Bereich): String = value.name

    @TypeConverter fun stringToBereich(value: String): Bereich = Bereich.valueOf(value)
}
