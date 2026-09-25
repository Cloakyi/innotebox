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
 * Gleiche Lizenztexte stehen nur einmal in der Datei; die Eintraege verweisen
 * auf sie. Die Texte der Apache-Lizenz und der OFL liegen in gradle/lizenztexte.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.util.zip.ZipFile
import org.gradle.api.attributes.Attribute
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

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

        // Name der Lizenz aus der POM-Datei, notfalls aus der Eltern-POM.
        fun pomLizenz(gruppe: String, artefakt: String, version: String, tiefe: Int = 0): String? {
            val text = pom(gruppe, artefakt, version) ?: return null
            Regex("""<license>\s*<name>(.*?)</name>""", RegexOption.DOT_MATCHES_ALL).find(text)
                ?.let { return it.groupValues[1].trim() }
            val eltern = Regex(
                """<parent>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(text) ?: return null
            if (tiefe >= 4) return null
            val (g, a, v) = eltern.destructured
            return pomLizenz(g.trim(), a.trim(), v.trim(), tiefe + 1)
        }

        // Fasst die Schreibweisen derselben Lizenz zusammen.
        fun einheitlich(name: String?): String? = when {
            name == null -> null
            "Apache" in name -> "Apache License 2.0"
            name.trim() in setOf("The MIT License", "MIT", "MIT License") -> "MIT License"
            "BSD-3" in name || "BSD 3" in name -> "BSD 3-Clause License"
            else -> name.trim()
        }

        val lizenzdatei = Regex("""(^|/)LICENSE(\.txt)?$""")

        // Ein mitgelieferter Lizenztext (MIT, BSD), falls das Paket einen hat.
        fun lizenztextImPaket(datei: File): String? = ZipFile(datei).use { zip ->
            for (eintrag in zip.entries()) {
                if (lizenzdatei.containsMatchIn(eintrag.name)) {
                    return zip.getInputStream(eintrag).readBytes().toString(Charsets.UTF_8)
                }
                if (eintrag.name.endsWith(".jar")) {
                    java.util.zip.ZipInputStream(zip.getInputStream(eintrag)).use { innen ->
                        generateSequence { innen.nextEntry }.forEach { n ->
                            if (lizenzdatei.containsMatchIn(n.name)) return innen.readBytes().toString(Charsets.UTF_8)
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
            val lizenz = einheitlich(pomLizenz(gruppe, artefakt, version))
                ?: throw GradleException("Keine Lizenz gefunden fuer $k")
            val eintrag = mutableMapOf<String, Any>("name" to k, "version" to version, "lizenz" to lizenz)
            val paket = pakete[k]
            when {
                lizenz == "Apache License 2.0" -> eintrag["text"] = apache
                lizenz in verweise -> eintrag["url"] = verweise.getValue(lizenz)
                else -> eintrag["text"] = textnummer(
                    paket?.let(::lizenztextImPaket) ?: throw GradleException("Kein Lizenztext fuer $k ($lizenz)"),
                )
            }
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

        val daten = mapOf(
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
