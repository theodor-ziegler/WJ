# WJ-Ausarbeitung Theodor Ziegler, Praxisteil
[![Hugging Face](https://img.shields.io/badge/Hugging%20Face-FFD21E?logo=huggingface&logoColor=000)](https://huggingface.co/)
[![Qwen](https://custom-icon-badges.demolab.com/badge/Qwen-605CEC?logo=qwen&logoColor=fff)](https://huggingface.co/Qwen/Qwen3.5-27B)
[![Godot Engine](https://img.shields.io/badge/Godot-%23FFFFFF.svg?logo=godot-engine)](https://www.godotengine.org/)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?logo=intellij-idea&logoColor=white)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-%237F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Inkscape](https://img.shields.io/badge/Inkscape-000000?logo=Inkscape&logoColor=white)](https://inkscape.org/)

# Anleitung

## Minimale Systemanforderungen
- Server
  - 64-bit CPU und Betriebssystem (Für Geschwindigkeit ist Linux präferabel)
  - Nvidia-GPU mit 24GB an VRAM
  - 32GB an RAM
- Client
  - 64-bit CPU und Betriebssystem (Für Geschwindigkeit ist Linux präferabel)
  - Integrierte Grafikkarte
  - 2GB an RAM

## Vorausgesetzte Programme
- Grafische Desktopumgebung
- Dateimanager
- Terminal
- Browser mit stabiler Internetverbindung

## Benötigte Programme
- Server
  - CMake
  - CUDA Toolkit
  - JDK 21
  - IntelliJ IDEA
- Client
  - Godot Engine

## Erste Schritte
1. Lade dieses Repo via das grüne "Code"-Menü als Zip-Datei herunter und extrahiere diese, oder klone dieses Repo via jegliche Git-kompatible Programme
2. Navigiere zum obersten Ordner der lokalen Kopie des Repos, welcher die Ordner `./client` und `./llama-infer-backend-proxy` enthält

## Einrichten des Servers
1. Lade Version "IQ4_XS" von [Qwen 3.5 27B](https://huggingface.co/unsloth/Qwen3.5-27B-GGUF/tree/main) herunter
2. Folge der Anleitung zum Build-Prozess von [llama.cpp](https://github.com/ggml-org/llama.cpp) für CUDA Toolkit
3. Im Ordner von llama.cpp , öffne ein Terminal und navigiere zu `./build/bin` und führe `./llama-server --port 8492 -c {CTX_SIZE} --context-shift -m {/path/to/model} --temp 0.7 -ngl 999999999 --special --verbose-prompt --jinja` aus
4. Öffne den Projektordner `llama-infer-backend-proxy` des Servers in IntelliJ IDEA, markiere das Projekt als "Sicher" falls ein Dialog erscheint
5. Führe das Projekt via den grünen Pfeil neben Zeile `main()` in Datei `llama-infer-backend-proxy/src/main/kotlin/Main.kt` aus

## Einrichten des Clients
1. Öffne den Projektordner `client` des Clients in Godot Engine, akzeptiere die Versionserhöhung falls ein Dialog erscheint
2. Führe das Programm via den Pfeil oben links im Fenster aus
3. Öffne das Einstellungsmenü des Clients und setze die Server-IP auf die Netzwerkadresse auf jene des selbst aufgesetzten Servers (Sollten Server und Client auf einem Gerät aktiv sein, nutze `localhost`)

## Fehlerbehebung des Clients
Es kann passieren, dass die Godot Engine eine Fehlermeldung zu `MarkdownLabel.gd` ausgibt und den Plugin deaktiviert. Ohne MarkdownLabel funktioniert das Anzeigen von Nachrichten nicht korrekt.
Öffne in diesem Fall das Menü "Projekteinstellungen" bzw "Project Settings" im Reiter "Projekt" bzw "Project" oben links, navigiere zum Reiter "Plugins", und setze bei "MarkdownLabel" ein Häckchen. 
Nach schließen des Fensters sollte das Programm korrekt funktionieren.

# Nutzung
Im Hauptfenster befinden sich eine Eingabeleiste (unten mittig), ein Sendeknopf (unten rechts) und eine Navigationsleiste (linker Rand). 
Die Bedienung ähnelt bewusst gängigen LLMs: Der Prompt wird in die Eingabeleiste geschrieben und via den Sendeknopf abgeschickt, danach erscheint die soeben gesendete Nachricht als Chatbox im Verlaufbereich (mittig, Großteil des Bildschirms).
Die Antwort des LLMs wird Token-weise in eine Antwort-Chatbox unter der Anfrage gestreamt. 
Die Schaltflächen auf der Navigationsleiste öffnen andere Fenster, wie das Historienfenster (Other Chats) zum Wechseln von Chatverläufen oder das Einstellungsfenster (Settings) zum Ändern der Einstellungen. 
Ausgegraute Schaltflächen sind nicht implementiert und dienen zur Konzeptdarstellung. Hierzu gehören Profile und Anhänge.
Mithilfe der Schaltfläche "C" lässt sich der Inhalt einer Chatbox kopieren.
