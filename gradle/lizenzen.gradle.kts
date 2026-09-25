/*
 * Erzeugt app/src/main/assets/lizenzen.json, die Grundlage der Seite
 * "Lizenzen" in den Einstellungen.
 *
 *     ./gradlew :app:lizenzen
 *
 * Nach jeder Aenderung an den Abhaengigkeiten neu laufen lassen und das
 * Ergebnis mit einchecken. Was hineinkommt:
 *
 * 1. Jede Bibliothek, die im Release-Build steckt (releaseRuntimeClasspath),
 *    mit der Lizenz aus ihrer POM-Datei.
 * 2. Die Drittsoftware, die Google in ML Kit und den Play-Diensten mitliefert.
 *    Diese Pakete tragen eine eigene Liste (third_party_licenses.json/.txt).
 * 3. Die Schrift Google Sans Flex (SIL Open Font License 1.1).
 *
 * 4. Die eigene Lizenz der App: LICENSE (GPL-3.0) und ZUSATZERLAUBNIS.md aus
 *    dem Projektstamm, damit die App sie ohne Netz zeigen kann.
 *
 * Gleiche Lizenztexte stehen nur einmal in der Datei; die Eintraege verweisen
 * auf sie. Die Texte der Apache-Lizenz und der OFL liegen in gradle/lizenztexte.
 * Bringt eine Bibliothek eine NOTICE-Datei mit (Apache 2.0, Abschnitt 4 d),
 * steht sie als "hinweis" am Eintrag.
 *
 * Eine Lizenz, die hier nicht ausdruecklich bekannt ist, bricht die Aufgabe
 * ab. Dann muss ein Mensch nachsehen, was sie verlangt, und sie in `bekannt`
 * eintragen; geraten wird nicht.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.zip.ZipFile
import org.gradle.api.attributes.Attribute
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

// Die Namen, unter denen die POM-Dateien ihre Lizenz fuehren, und wie sie in
// der App heissen. Nur was hier steht, gilt als bekannt.
val bekannt = mapOf(
    "The Apache Software License, Version 2.0" to "Apache License 2.0",
    "The Apache License, Version 2.0" to "Apache License 2.0",
    "Apache License, Version 2.0" to "Apache License 2.0",
    "Apache 2.0" to "Apache License 2.0",
    "Apache-2.0" to "Apache License 2.0",
    "The MIT License" to "MIT License",
    "BSD-3-Clause" to "BSD 3-Clause License",
    "Android Software Development Kit License" to "Android Software Development Kit License",
    "ML Kit Terms of Service" to "ML Kit Terms of Service",
    "CC0" to "CC0",
)

// Lizenzen ohne mitgelieferten Text: Die Bedingungen stehen beim Anbieter.
val verweise = mapOf(
    "Android Software Development Kit License" to "https://developer.android.com/studio/terms",
    "ML Kit Terms of Service" to "https://developers.google.com/ml-kit/terms",
    "CC0" to "https://creativecommons.org/publicdomain/zero/1.0/",
)

tasks.register("lizenzen") {
    group = "build"
    description = "Schreibt die Lizenzliste fuer die Seite Lizenzen in der App."
    notCompatibleWithConfigurationCache("liest Abhaengigkeiten und POM-Dateien zur Ausfuehrungszeit")

    doLast {
        val klassenpfad = configurations.getByName("releaseRuntimeClasspath")

        // Gruppe:Artefakt -> aufgeloeste Version.
        val versionen = klassenpfad.incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .associate { "${it.group}:${it.module}" to it.version }

        // Die Pakete selbst, als AAR oder JAR, so wie Gradle sie geladen hat.
        val pakete = mutableMapOf<String, File>()
        for (typ in listOf("jar", "aar")) {
            klassenpfad.incoming.artifactView {
                isLenient = true
                attributes { attribute(Attribute.of("artifactType", String::class.java), typ) }
            }.artifacts.forEach { a ->
                val id = a.id.componentIdentifier as? ModuleComponentIdentifier ?: return@forEach
                pakete["${id.group}:${id.module}"] = a.file
            }
        }

        // Die Varianten -android und -jvm und die Stuecklisten (BOM) sind
        // dieselbe Bibliothek wie ihr Stamm; sie stehen nicht einzeln in der Liste.
        val varianten = Regex("-(android|jvm)$")
        val namen = versionen.keys.filter { k ->
            !k.endsWith("-bom") && ":compose-bom" !in k &&
                !(varianten.containsMatchIn(k) && k.replace(varianten, "") in versionen)
        }.sorted()

        fun pom(gruppe: String, artefakt: String, version: String): String? =
            dependencies.createArtifactResolutionQuery()
                .forModule(gruppe, artefakt, version)
                .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                .execute()
                .resolvedComponents
                .flatMap { it.getArtifacts(MavenPomArtifact::class.java) }
                .filterIsInstance<ResolvedArtifactResult>()
                .firstOrNull()?.file?.readText()

        // Alle Lizenzen aus der POM-Datei, notfalls aus der Eltern-POM.
        fun pomLizenzen(gruppe: String, artefakt: String, version: String, tiefe: Int = 0): List<String> {
            val text = pom(gruppe, artefakt, version) ?: return emptyList()
            val namen = Regex("""<license>\s*<name>(.*?)</name>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(text).map { it.groupValues[1].trim() }.toList()
            if (namen.isNotEmpty()) return namen
            val eltern = Regex(
                """<parent>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(text) ?: return emptyList()
            if (tiefe >= 4) return emptyList()
            val (g, a, v) = eltern.destructured
            return pomLizenzen(g.trim(), a.trim(), v.trim(), tiefe + 1)
        }

        // Genau eine bekannte Lizenz, sonst Abbruch. Zwei verschiedene (eine
        // Wahl zwischen Lizenzen) entscheidet ebenfalls ein Mensch.
        fun einheitlich(k: String, namen: List<String>): String {
            if (namen.isEmpty()) throw GradleException("Keine Lizenz gefunden fuer $k")
            val unbekannt = namen.filter { it !in bekannt }
            if (unbekannt.isNotEmpty()) {
                throw GradleException(
                    "Unbekannte Lizenz fuer $k: $unbekannt. Pruefen und in gradle/lizenzen.gradle.kts eintragen.",
                )
            }
            val gleich = namen.map { bekannt.getValue(it) }.distinct()
            if (gleich.size > 1) throw GradleException("Mehrere Lizenzen fuer $k: $gleich. Von Hand entscheiden.")
            return gleich.single()
        }

        val lizenzdatei = Regex("""(^|/)LICENSE(\.txt)?$""")
        val hinweisdatei = Regex("""(^|/)NOTICE(\.txt|\.md)?$""")

        // Die erste Datei im Paket, deren Name passt, auch in einem inneren JAR.
        fun dateiImPaket(datei: File, muster: Regex): String? = ZipFile(datei).use { zip ->
            for (eintrag in zip.entries()) {
                if (muster.containsMatchIn(eintrag.name)) {
                    return zip.getInputStream(eintrag).readBytes().toString(Charsets.UTF_8)
                }
                if (eintrag.name.endsWith(".jar")) {
                    java.util.zip.ZipInputStream(zip.getInputStream(eintrag)).use { innen ->
                        generateSequence { innen.nextEntry }.forEach { n ->
                            if (muster.containsMatchIn(n.name)) return innen.readBytes().toString(Charsets.UTF_8)
                        }
                    }
                }
            }
            null
        }

        // Die Liste, die Google in seine Pakete legt: Name -> Lizenztext.
        fun drittsoftware(datei: File): Map<String, String> = ZipFile(datei).use { zip ->
            val verzeichnis = zip.getEntry("third_party_licenses.json") ?: return emptyMap()
            val text = zip.getInputStream(zip.getEntry("third_party_licenses.txt")).readBytes()
            @Suppress("UNCHECKED_CAST")
            val stellen = JsonSlurper().parse(zip.getInputStream(verzeichnis)) as Map<String, Map<String, Int>>
            stellen.mapValues { (_, s) ->
                val start = s.getValue("start")
                text.copyOfRange(start, start + s.getValue("length")).toString(Charsets.UTF_8).trim()
            }
        }

        val texte = mutableListOf<String>()
        val stellen = mutableMapOf<String, Int>()
        fun textnummer(text: String): Int {
            val schluessel = text.replace(Regex("\\s+"), " ").trim()
            return stellen.getOrPut(schluessel) { texte.add(text.trim()); texte.size - 1 }
        }

        val textordner = rootProject.file("gradle/lizenztexte")
        val apache = textnummer(File(textordner, "apache-2.0.txt").readText())

        val bibliotheken = mutableListOf<Map<String, Any>>()
        val enthalten = linkedMapOf<String, MutableSet<Int>>()
        for (k in namen) {
            val (gruppe, artefakt) = k.split(":")
            val version = versionen.getValue(k)
            val lizenz = einheitlich(k, pomLizenzen(gruppe, artefakt, version))
            val eintrag = mutableMapOf<String, Any>("name" to k, "version" to version, "lizenz" to lizenz)
            val paket = pakete[k]
            when {
                lizenz == "Apache License 2.0" -> eintrag["text"] = apache
                lizenz in verweise -> eintrag["url"] = verweise.getValue(lizenz)
                else -> eintrag["text"] = textnummer(
                    paket?.let { dateiImPaket(it, lizenzdatei) }
                        ?: throw GradleException("Kein Lizenztext fuer $k ($lizenz)"),
                )
            }
            paket?.let { dateiImPaket(it, hinweisdatei) }?.let { eintrag["hinweis"] = textnummer(it) }
            bibliotheken += eintrag
            paket?.let(::drittsoftware)?.forEach { (name, text) ->
                enthalten.getOrPut(name) { sortedSetOf() } += textnummer(text)
            }
        }

        val schrift = mapOf(
            "name" to "Google Sans Flex",
            "lizenz" to "SIL Open Font License 1.1",
            "text" to textnummer(File(textordner, "ofl-1.1.txt").readText()),
        )

        // Die eigene Lizenz. Aus der Zusatzerlaubnis fallen die Zeichen der
        // Markdown-Auszeichnung weg; in der App steht sie als schlichter Text.
        val zusatz = rootProject.file("ZUSATZERLAUBNIS.md").readLines()
            .joinToString("\n") { it.removePrefix("# ").removePrefix("> ").removePrefix(">") }
        val app = mapOf(
            "lizenz" to textnummer(rootProject.file("LICENSE").readText()),
            "zusatz" to textnummer(zusatz),
        )

        val daten = mapOf(
            "app" to app,
            "bibliotheken" to bibliotheken,
            "enthalten" to enthalten.entries.sortedBy { it.key.lowercase() }
                .map { (name, t) -> mapOf("name" to name, "texte" to t.toList()) },
            "schriften" to listOf(schrift),
            "texte" to texte,
        )
        val ziel = file("src/main/assets/lizenzen.json")
        ziel.parentFile.mkdirs()
        ziel.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(daten)) + "\n")
        logger.lifecycle(
            "${bibliotheken.size} Bibliotheken, ${enthalten.size} enthaltene Teile, " +
                "${texte.size} verschiedene Texte, ${ziel.length() / 1024} KB",
        )
    }
}
