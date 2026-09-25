"""
Erzeugt app/src/main/assets/lizenzen.json, die Grundlage der Seite
"Lizenzen" in den Einstellungen.

Aufruf im Projektstamm, nach jeder Aenderung an den Abhaengigkeiten:

    python werkzeug/lizenzen.py

Was hineinkommt:

1. Jede Bibliothek, die im Release-Build steckt (releaseRuntimeClasspath),
   mit der Lizenz aus ihrer POM-Datei.
2. Die Drittsoftware, die Google in ML Kit und den Play-Diensten mitliefert.
   Diese Pakete tragen eine eigene Liste (third_party_licenses.json/.txt);
   sie wird aus dem Gradle-Cache gelesen.
3. Die Schrift Google Sans Flex (SIL Open Font License 1.1).

Gleiche Lizenztexte stehen nur einmal in der Datei; die Eintraege verweisen
auf sie. Das Skript braucht Netz fuer die POM-Dateien und ein vorher
einmal gebautes Projekt, damit der Gradle-Cache die Pakete enthaelt.
"""

import concurrent.futures
import glob
import io
import json
import os
import re
import subprocess
import sys
import urllib.request
import zipfile

STAMM = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZIEL = os.path.join(STAMM, "app", "src", "main", "assets", "lizenzen.json")
CACHE = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "modules-2", "files-2.1")
MAVEN = ("https://dl.google.com/dl/android/maven2/", "https://repo1.maven.org/maven2/")

APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0.txt"
OFL_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/googlesansflex/OFL.txt"

# Lizenzen ohne mitgelieferten Text: Die Bedingungen stehen beim Anbieter.
VERWEISE = {
    "Android Software Development Kit License": "https://developer.android.com/studio/terms",
    "ML Kit Terms of Service": "https://developers.google.com/ml-kit/terms",
    "CC0": "https://creativecommons.org/publicdomain/zero/1.0/",
}


def laden(url):
    with urllib.request.urlopen(url, timeout=30) as antwort:
        return antwort.read().decode("utf-8", "replace")


def abhaengigkeiten():
    """Gruppe:Artefakt -> aufgeloeste Version, aus dem Baum von Gradle."""
    gradlew = os.path.join(STAMM, "gradlew.bat" if os.name == "nt" else "gradlew")
    baum = subprocess.run(
        [gradlew, "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"],
        cwd=STAMM, capture_output=True, text=True, check=True,
    ).stdout
    ergebnis = {}
    for zeile in baum.splitlines():
        m = re.search(r"--- ([\w.\-]+):([\w.\-]+)(?::([\w.\-]+))?(?: -> ([\w.\-]+))?", zeile)
        if not m or m.group(1) == "project":
            continue
        gruppe, artefakt, version, ersetzt = m.groups()
        ergebnis[f"{gruppe}:{artefakt}"] = ersetzt or version
    return ergebnis


def pom_lizenz(gruppe, artefakt, version, tiefe=0):
    """Name der Lizenz aus der POM-Datei, notfalls aus der Eltern-POM."""
    for basis in MAVEN:
        url = f"{basis}{gruppe.replace('.', '/')}/{artefakt}/{version}/{artefakt}-{version}.pom"
        try:
            pom = laden(url)
        except Exception:
            continue
        namen = re.findall(r"<license>\s*<name>(.*?)</name>", pom, re.S)
        if namen:
            return namen[0].strip()
        eltern = re.search(
            r"<parent>.*?<groupId>(.*?)</groupId>.*?<artifactId>(.*?)</artifactId>.*?<version>(.*?)</version>",
            pom, re.S)
        if eltern and tiefe < 4:
            return pom_lizenz(*[x.strip() for x in eltern.groups()], tiefe + 1)
        return None
    return None


def einheitlich(name):
    """Fasst die Schreibweisen derselben Lizenz zusammen."""
    if name is None:
        return None
    if "Apache" in name:
        return "Apache License 2.0"
    if name.strip() in ("The MIT License", "MIT", "MIT License"):
        return "MIT License"
    if "BSD-3" in name or "BSD 3" in name:
        return "BSD 3-Clause License"
    return name.strip()


def paketdatei(gruppe, artefakt, version):
    for endung in ("aar", "jar"):
        treffer = glob.glob(os.path.join(CACHE, gruppe, artefakt, version, "*", f"{artefakt}-{version}.{endung}"))
        if treffer:
            return treffer[0]
    return None


def lizenztext_im_paket(pfad):
    """Ein mitgelieferter Lizenztext (MIT, BSD), falls das Paket einen hat."""
    with zipfile.ZipFile(pfad) as z:
        for name in z.namelist():
            if re.search(r"(^|/)LICENSE(\.txt)?$", name):
                return z.read(name).decode("utf-8", "replace")
            if name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(z.read(name))) as innen:
                    for n in innen.namelist():
                        if re.search(r"(^|/)LICENSE(\.txt)?$", n):
                            return innen.read(n).decode("utf-8", "replace")
    return None


def drittsoftware(pfad):
    """Die Liste, die Google in seine Pakete legt: Name -> Lizenztext."""
    with zipfile.ZipFile(pfad) as z:
        if "third_party_licenses.json" not in z.namelist():
            return {}
        verzeichnis = json.loads(z.read("third_party_licenses.json"))
        text = z.read("third_party_licenses.txt")
    return {
        name: text[stelle["start"]:stelle["start"] + stelle["length"]].decode("utf-8", "replace").strip()
        for name, stelle in verzeichnis.items()
    }


def main():
    texte = []
    stellen = {}

    def textnummer(text):
        schluessel = re.sub(r"\s+", " ", text).strip()
        if schluessel not in stellen:
            stellen[schluessel] = len(texte)
            texte.append(text.strip())
        return stellen[schluessel]

    apache = textnummer(laden(APACHE_URL))

    deps = abhaengigkeiten()
    # Die Varianten -android und -jvm und die Stuecklisten (BOM) sind dieselbe
    # Bibliothek wie ihr Stamm; sie stehen nicht einzeln in der Liste.
    namen = sorted(
        k for k in deps
        if not k.endswith("-bom") and ":compose-bom" not in k
        and not (re.search(r"-(android|jvm)$", k) and re.sub(r"-(android|jvm)$", "", k) in deps)
    )

    with concurrent.futures.ThreadPoolExecutor(16) as pool:
        lizenzen = dict(zip(namen, pool.map(lambda k: einheitlich(pom_lizenz(*k.split(":"), deps[k])), namen)))

    fehlend = [k for k, v in lizenzen.items() if v is None]
    if fehlend:
        sys.exit("Keine Lizenz gefunden fuer: " + ", ".join(fehlend))

    bibliotheken = []
    enthalten = {}
    for k in namen:
        gruppe, artefakt = k.split(":")
        version = deps[k]
        lizenz = lizenzen[k]
        eintrag = {"name": k, "version": version, "lizenz": lizenz}
        paket = paketdatei(gruppe, artefakt, version)
        if lizenz == "Apache License 2.0":
            eintrag["text"] = apache
        elif lizenz in VERWEISE:
            eintrag["url"] = VERWEISE[lizenz]
        else:
            text = lizenztext_im_paket(paket) if paket else None
            if text is None:
                sys.exit(f"Kein Lizenztext fuer {k} ({lizenz})")
            eintrag["text"] = textnummer(text)
        bibliotheken.append(eintrag)
        if paket:
            for name, text in drittsoftware(paket).items():
                enthalten.setdefault(name, set()).add(textnummer(text))

    schrift = {"name": "Google Sans Flex", "lizenz": "SIL Open Font License 1.1", "text": textnummer(laden(OFL_URL))}

    daten = {
        "bibliotheken": bibliotheken,
        "enthalten": [{"name": n, "texte": sorted(v)} for n, v in sorted(enthalten.items(), key=lambda x: x[0].lower())],
        "schriften": [schrift],
        "texte": texte,
    }
    os.makedirs(os.path.dirname(ZIEL), exist_ok=True)
    with io.open(ZIEL, "w", encoding="utf-8", newline="\n") as datei:
        json.dump(daten, datei, ensure_ascii=False, indent=1)
        datei.write("\n")
    print(f"{len(bibliotheken)} Bibliotheken, {len(enthalten)} enthaltene Teile, "
          f"{len(texte)} verschiedene Texte, {os.path.getsize(ZIEL) // 1024} KB")


if __name__ == "__main__":
    main()
