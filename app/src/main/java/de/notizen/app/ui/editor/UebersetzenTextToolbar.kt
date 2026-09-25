package de.notizen.app.ui.editor

import android.graphics.Rect as AndroidRect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Die schwebende Auswahlleiste des Textes, mit „Übersetzen" (Phase 16).
 *
 * Compose bringt eine eigene Leiste mit (`AndroidTextToolbar`), aber die
 * kennt nur Ausschneiden, Kopieren, Einfügen und Alles auswählen, und sie ist
 * `internal`. Diese Fassung ist derselbe Aufbau (`ActionMode.Callback2`,
 * `TYPE_FLOATING`, in der Quelle von ui-android 1.12.0 nachgelesen am
 * 2026-09-19) mit einem fünften Eintrag. Die vier Standardeinträge tragen
 * dieselben Kennungen und Systemtexte wie bei Compose, damit sie sich anfühlen
 * wie immer.
 *
 * Bereitgestellt wird sie über `LocalTextToolbar` nur um das Textfeld des
 * Editors, und nur, wenn es einen Weg zum Übersetzen gibt.
 */
class UebersetzenTextToolbar(
    private val view: View,
    private val onUebersetzen: () -> Unit,
) : TextToolbar {

    private var actionMode: ActionMode? = null
    private var rect: Rect = Rect.Zero
    private var onCopy: (() -> Unit)? = null
    private var onPaste: (() -> Unit)? = null
    private var onCut: (() -> Unit)? = null
    private var onSelectAll: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        this.rect = rect
        onCopy = onCopyRequested
        onPaste = onPasteRequested
        onCut = onCutRequested
        onSelectAll = onSelectAllRequested
        if (actionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = view.startActionMode(Rueckruf(), ActionMode.TYPE_FLOATING)
        } else {
            actionMode?.invalidate()
        }
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    private inner class Rueckruf : ActionMode.Callback2() {

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu ?: return false
            eintraege(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menu ?: return false
            menu.clear()
            eintraege(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                android.R.id.copy -> onCopy?.invoke()
                android.R.id.paste -> onPaste?.invoke()
                android.R.id.cut -> onCut?.invoke()
                android.R.id.selectAll -> onSelectAll?.invoke()
                UEBERSETZEN -> onUebersetzen()
                else -> return false
            }
            mode?.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: AndroidRect?) {
            outRect?.set(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        }

        private fun eintraege(menu: Menu) {
            onCopy?.let { menu.add(0, android.R.id.copy, 0, android.R.string.copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
            onPaste?.let { menu.add(0, android.R.id.paste, 1, android.R.string.paste).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
            onCut?.let { menu.add(0, android.R.id.cut, 2, android.R.string.cut).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
            onSelectAll?.let { menu.add(0, android.R.id.selectAll, 3, android.R.string.selectAll).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
            // Nur mit Auswahl: Ohne Auswahl gibt es Kopieren nicht, und dann
            // gaebe es auch nichts zu uebersetzen.
            if (onCopy != null) {
                menu.add(0, UEBERSETZEN, 4, "Übersetzen").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
        }
    }

    private companion object {
        /** Eine eigene Kennung, weit weg von allem, was `android.R.id` vergibt. */
        const val UEBERSETZEN = 0x0D0E_0001
    }
}
