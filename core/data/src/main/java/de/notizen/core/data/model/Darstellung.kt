package de.notizen.core.data.model

/**
 * Wie eine Stufen-Ansicht ihre Notizen zeigt.
 *
 * Liegt bewusst in :core:data und nicht in der UI: die Wahl wird dauerhaft
 * gespeichert und ist damit Einstellung, nicht Bildschirmzustand.
 */
enum class Darstellung {
    /** Versetztes Raster, wie eine Pinnwand. */
    RASTER,

    /** Einspaltige Liste. */
    LISTE,
    ;

    fun umgeschaltet(): Darstellung = if (this == RASTER) LISTE else RASTER
}
