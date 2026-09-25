# Beitragen

Fehlerberichte und Vorschläge sind willkommen, am besten als Issue mit einer kurzen
Beschreibung, was du getan hast, was du erwartet hast und was stattdessen passiert ist.
Gerät und Android-Version helfen fast immer. Zum Testen gibt es die Liste in
[TESTEN.md](TESTEN.md) und beim Anlegen eines Issues die Vorlage **Testbericht**.

## Lizenz deiner Beiträge

Wer Code, Texte oder Grafiken zu InNoteBox beiträgt, stellt sie unter dieselben Bedingungen
wie das Projekt: die GNU General Public License, Version 3 (GPL-3.0-only), **einschließlich
der zusätzlichen Erlaubnis** in [ZUSATZERLAUBNIS.md](ZUSATZERLAUBNIS.md). Ohne diese
Erlaubnis könnte die App mit deinem Beitrag nicht mehr weitergegeben werden, weil sie
Bibliotheken von Google enthält, die nicht frei sind.

Mit einem Pull Request bestätigst du, dass du den Beitrag selbst geschaffen hast oder ihn
unter diesen Bedingungen weitergeben darf.

## Bevor du etwas einreichst

- `./gradlew testDebugUnitTest` läuft grün.
- Die Entscheidungen hinter dem Code stehen in [docs/ENTSCHEIDUNGEN.md](docs/ENTSCHEIDUNGEN.md);
  manches, was ungewöhnlich aussieht, ist dort begründet.
- Ändert sich eine Abhängigkeit, erzeugt `./gradlew :app:lizenzen` die Lizenzliste der App neu.
- Texte in der App stehen in ganzen Sätzen und ohne Gedankenstrich.
