package de.notizen.app.kalender

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Church
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalPostOffice
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import de.notizen.core.data.model.Terminart

/**
 * Ein Symbol fuer Termine: Schluessel, Zeichen und Woerter, an denen es
 * erkannt wird.
 *
 * Der Schluessel steht in `symbol` (SYNC.md 16.2) und geht damit auch an
 * andere Geraete. Er ist deshalb ein festes englisches Wort und nie der Name
 * einer Klasse; ein anderer Client ordnet ihm ein eigenes Zeichen zu.
 */
data class Terminsymbol(
    val schluessel: String,
    val zeichen: ImageVector,
    val beschreibung: String,
    val woerter: List<String>,
)

/**
 * Die feste Auswahl, aus der die KI waehlt.
 *
 * Fest, weil ein Modell, das frei ein Symbol benennt, auch eines benennt, das
 * es nicht gibt. So wird nur eine Antwort angenommen, die in dieser Liste
 * steht, und alles andere faellt auf die Woerter zurueck.
 */
object Terminsymbole {

    val alle: List<Terminsymbol> = listOf(
        Terminsymbol("doctor", Icons.Outlined.MedicalServices, "Arzt, Praxis, Untersuchung",
            listOf("arzt", "ärztin", "praxis", "doktor", "hausarzt", "zahnarzt", "termin beim", "klinik", "krankenhaus", "untersuchung", "orthopäd", "frauenarzt", "augenarzt", "hno", "physio")),
        Terminsymbol("vaccine", Icons.Outlined.Vaccines, "Impfung, Blutabnahme",
            listOf("impf", "blutabnahme", "spritze", "booster")),
        Terminsymbol("medicine", Icons.Outlined.Medication, "Medikamente, Apotheke, Tabletten",
            listOf("medikament", "tablette", "apotheke", "rezept", "pille")),
        Terminsymbol("therapy", Icons.Outlined.Psychology, "Therapie, Beratung, Coaching",
            listOf("therapie", "therapeut", "psycholog", "beratung", "coaching", "ergo")),
        Terminsymbol("birthday", Icons.Outlined.Cake, "Geburtstag",
            listOf("geburtstag", "bday", "birthday")),
        Terminsymbol("party", Icons.Outlined.Celebration, "Feier, Party, Hochzeit, Jubiläum",
            listOf("party", "feier", "fest", "hochzeit", "jubiläum", "silvester", "grill")),
        Terminsymbol("gym", Icons.Outlined.FitnessCenter, "Training, Fitness",
            listOf("gym", "fitness", "training", "krafttraining", "workout", "studio")),
        Terminsymbol("run", Icons.AutoMirrored.Outlined.DirectionsRun, "Laufen, Joggen, Sport",
            listOf("laufen", "joggen", "lauf", "marathon", "sport", "yoga")),
        Terminsymbol("swim", Icons.Outlined.Pool, "Schwimmen, Schwimmbad",
            listOf("schwimm", "bad", "therme", "sauna")),
        Terminsymbol("football", Icons.Outlined.SportsSoccer, "Fußball, Mannschaftssport, Spiel",
            listOf("fußball", "fussball", "handball", "basketball", "volleyball", "verein", "spiel gegen")),
        Terminsymbol("bike", Icons.AutoMirrored.Outlined.DirectionsBike, "Fahrrad, Radtour",
            listOf("fahrrad", "rad", "radtour", "bike")),
        Terminsymbol("school", Icons.Outlined.School, "Schule, Uni, Prüfung, Kurs",
            listOf("schule", "uni", "vorlesung", "prüfung", "klausur", "kurs", "seminar", "unterricht", "elternabend", "nachhilfe", "berufsschule")),
        Terminsymbol("study", Icons.AutoMirrored.Outlined.MenuBook, "Lernen, Lesen, Hausaufgaben",
            listOf("lernen", "hausaufgabe", "lesen", "buch", "bibliothek", "referat")),
        Terminsymbol("work", Icons.Outlined.Work, "Arbeit, Schicht, Büro",
            listOf("arbeit", "schicht", "büro", "dienst", "job", "bewerbung", "vorstellungsgespräch", "kunde")),
        Terminsymbol("meeting", Icons.Outlined.Groups, "Besprechung, Treffen",
            listOf("meeting", "besprechung", "treffen", "sitzung", "team", "konferenz", "stammtisch")),
        Terminsymbol("call", Icons.Outlined.Call, "Anruf, Telefonat",
            listOf("anruf", "anrufen", "telefon", "telefonat", "call")),
        Terminsymbol("video", Icons.Outlined.Videocam, "Videoanruf, Stream, Aufnahme",
            listOf("video", "zoom", "teams", "stream", "livestream", "discord", "youtube")),
        Terminsymbol("music", Icons.Outlined.MusicNote, "Musik, Konzert, Probe",
            listOf("konzert", "musik", "probe", "chor", "band", "gitarre", "klavier", "festival")),
        Terminsymbol("movie", Icons.Outlined.Movie, "Kino, Film, Serie",
            listOf("kino", "film", "serie", "netflix")),
        Terminsymbol("theater", Icons.Outlined.TheaterComedy, "Theater, Show, Comedy",
            listOf("theater", "musical", "show", "comedy", "oper")),
        Terminsymbol("gaming", Icons.Outlined.SportsEsports, "Zocken, Spieleabend",
            listOf("zocken", "gaming", "spieleabend", "lan", "konsole", "minecraft")),
        Terminsymbol("shopping", Icons.Outlined.ShoppingCart, "Einkaufen",
            listOf("einkauf", "einkaufen", "supermarkt", "shoppen", "shopping", "markt", "abholen")),
        Terminsymbol("food", Icons.Outlined.Restaurant, "Essen, Restaurant, Kochen",
            listOf("essen", "restaurant", "abendessen", "mittagessen", "frühstück", "brunch", "kochen", "pizza")),
        Terminsymbol("coffee", Icons.Outlined.LocalCafe, "Kaffee, Café",
            listOf("kaffee", "café", "cafe", "kuchen")),
        Terminsymbol("travel", Icons.Outlined.Luggage, "Reise, Urlaub, Packen",
            listOf("reise", "urlaub", "packen", "koffer", "hotel", "ferien")),
        Terminsymbol("flight", Icons.Outlined.Flight, "Flug, Flughafen",
            listOf("flug", "flughafen", "abflug", "boarding")),
        Terminsymbol("train", Icons.Outlined.Train, "Zug, Bahn",
            listOf("zug", "bahn", "ice", "bahnhof", "s-bahn")),
        Terminsymbol("bus", Icons.Outlined.DirectionsBus, "Bus, Fahrt",
            listOf("bus", "fahrt", "abfahrt")),
        Terminsymbol("car", Icons.Outlined.DirectionsCar, "Auto, Werkstatt, TÜV, Fahrstunde",
            listOf("auto", "werkstatt", "tüv", "reifen", "fahrstunde", "fahrschule", "tanken", "inspektion")),
        Terminsymbol("home", Icons.Outlined.Home, "Zuhause, Wohnung, Umzug",
            listOf("zuhause", "wohnung", "umzug", "vermieter", "handwerker", "besichtigung")),
        Terminsymbol("cleaning", Icons.Outlined.CleaningServices, "Putzen, Aufräumen",
            listOf("putzen", "aufräumen", "sauber", "müll", "staubsaugen")),
        Terminsymbol("laundry", Icons.Outlined.LocalLaundryService, "Wäsche",
            listOf("wäsche", "waschen", "bügeln")),
        Terminsymbol("repair", Icons.Outlined.Build, "Reparatur, Basteln",
            listOf("reparatur", "reparieren", "basteln", "montage", "aufbauen")),
        Terminsymbol("garden", Icons.Outlined.Yard, "Garten, Pflanzen",
            listOf("garten", "pflanzen", "rasen", "gießen")),
        Terminsymbol("pet", Icons.Outlined.Pets, "Haustier, Tierarzt, Gassi",
            listOf("hund", "katze", "tierarzt", "gassi", "haustier", "futter")),
        Terminsymbol("haircut", Icons.Outlined.ContentCut, "Friseur",
            listOf("friseur", "frisör", "haare", "barber", "nägel")),
        Terminsymbol("sleep", Icons.Outlined.Bedtime, "Schlafen, Ruhe",
            listOf("schlafen", "bett", "ruhe", "nickerchen")),
        Terminsymbol("money", Icons.Outlined.Payments, "Rechnung, Zahlung, Miete",
            listOf("rechnung", "zahlung", "bezahlen", "überweisung", "miete", "gehalt", "steuer")),
        Terminsymbol("office", Icons.Outlined.AccountBalance, "Amt, Bank, Behörde",
            listOf("amt", "behörde", "bank", "bürgeramt", "finanzamt", "jobcenter", "agentur", "gericht", "notar")),
        Terminsymbol("mail", Icons.Outlined.LocalPostOffice, "Post, Paket",
            listOf("post", "paket", "brief", "sendung")),
        Terminsymbol("document", Icons.Outlined.Description, "Unterlagen, Formular, Vertrag",
            listOf("unterlagen", "formular", "antrag", "vertrag", "frist", "abgabe", "kündigung")),
        Terminsymbol("computer", Icons.Outlined.Computer, "Computer, Programmieren, Technik",
            listOf("computer", "pc", "laptop", "programmieren", "code", "server", "update")),
        Terminsymbol("photo", Icons.Outlined.PhotoCamera, "Fotos, Fotoshooting",
            listOf("foto", "shooting", "kamera")),
        Terminsymbol("art", Icons.Outlined.Palette, "Malen, Zeichnen, Kunst",
            listOf("malen", "zeichnen", "kunst", "museum", "ausstellung")),
        Terminsymbol("design", Icons.Outlined.Brush, "Gestaltung, Design",
            listOf("design", "gestalten", "logo", "thumbnail")),
        Terminsymbol("family", Icons.Outlined.FamilyRestroom, "Familie, Kinder, Besuch",
            listOf("familie", "eltern", "mama", "papa", "oma", "opa", "kinder", "kita", "besuch")),
        Terminsymbol("love", Icons.Outlined.Favorite, "Date, Jahrestag, Partner",
            listOf("date", "jahrestag", "valentin", "freundin", "freund")),
        Terminsymbol("church", Icons.Outlined.Church, "Gottesdienst, Taufe",
            listOf("kirche", "gottesdienst", "taufe", "konfirmation", "kommunion", "moschee")),
        Terminsymbol("task", Icons.Outlined.TaskAlt, "Aufgabe, Erledigung",
            listOf("erledigen", "aufgabe", "todo")),
        Terminsymbol("event", Icons.Outlined.Event, "Allgemeiner Termin",
            emptyList()),
    )

    private val nachSchluessel = alle.associateBy { it.schluessel }

    /** Das Symbol zu einem gespeicherten Schluessel, oder `null` fuer einen unbekannten. */
    fun von(schluessel: String?): Terminsymbol? = schluessel?.let(nachSchluessel::get)

    /** Das Zeichen, das ein Termin zeigt: sein Symbol, sonst das seiner Art. */
    fun zeichen(schluessel: String?, art: Terminart): ImageVector =
        von(schluessel)?.zeichen ?: von(standard(art))!!.zeichen

    fun standard(art: Terminart): String = when (art) {
        Terminart.GEBURTSTAG -> "birthday"
        Terminart.AUFGABE -> "task"
        Terminart.TERMIN -> "event"
    }

    /**
     * Ein Symbol nur aus den Woertern von Titel, Ort und Beschreibung. Laeuft
     * ohne KI und ohne Warten; der Titel zaehlt vor dem Rest.
     */
    fun ausWoertern(titel: String, rest: String, art: Terminart): String {
        if (art == Terminart.GEBURTSTAG) return "birthday"
        treffer(titel)?.let { return it }
        treffer(rest)?.let { return it }
        return standard(art)
    }

    private fun treffer(text: String): String? {
        val klein = text.lowercase()
        if (klein.isBlank()) return null
        // Das laengste passende Wort gewinnt: "zahnarzt" sagt mehr als "arzt",
        // und "fahrstunde" mehr als "fahrt".
        var bestes: Pair<String, Int>? = null
        for (s in alle) {
            for (w in s.woerter) {
                if (w.length > (bestes?.second ?: 0) && enthaeltWort(klein, w)) bestes = s.schluessel to w.length
            }
        }
        return bestes?.first
    }

    /** Ein Wort am Wortanfang, damit "bad" nicht in "Sabadell" steckt. */
    private fun enthaeltWort(text: String, wort: String): Boolean {
        var i = text.indexOf(wort)
        while (i >= 0) {
            if (i == 0 || !text[i - 1].isLetterOrDigit()) return true
            i = text.indexOf(wort, i + 1)
        }
        return false
    }

    /**
     * Liest die Antwort der KI. Angenommen wird nur ein Schluessel aus der
     * Liste; Satzzeichen, Gross- und Kleinschreibung und Erklaerungen drumherum
     * werden weggelassen.
     */
    fun ausAntwort(antwort: String?): String? {
        val klein = antwort?.lowercase() ?: return null
        val woerter = Regex("[a-z]+").findAll(klein).map { it.value }.toList()
        return woerter.firstOrNull { it in nachSchluessel }
    }
}
