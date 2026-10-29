# AbiCoach Englisch

Android-App zur Vorbereitung auf die **mündliche Abiturprüfung Englisch in NRW (4. Fach, Grundkurs)** – mit echtem Sprechen statt Tippen.

## Was die App kann

- **Prüfungssimulation wie im Abi:** neues Material (Text + 3 Aufgaben nach NRW-Operatoren), Vorbereitung mit 30-Minuten-Timer und Stichpunkten, Teil 1 Vortrag, Teil 2 Prüfungsgespräch mit einem KI-Prüfer, der Themen wechselt und Monologe unterbricht.
- **Gesprächstraining** zu allen Themenfeldern der Abiturvorgaben 2027.
- **Zweites Ohr:** Eine parallele, wortgetreue Mitschrift (Modus *VERBATIM*) hält fest, was du wirklich sagst – inklusive Fehlern und Füllwörtern.
- **Drei Rückmelde-Stufen:** *Prüfung* (keine Unterbrechungen), *Flüstern* (Vibration + Korrektur auf dem Bildschirm), *Coach* (der Prüfer korrigiert dich sofort und lässt dich wiederholen).
- **Nachbesprechung:** Punkteschätzung nach dem NRW-Raster (Inhalt 40 %, Darstellung 60 %), die drei größten Hebel, jeder Fehler mit Zitat und Erklärung, „15-Punkte-Versionen“ deiner Sätze zum Nachsprechen, Aussprache-Hinweise aus deiner Aufnahme, Redemittel.
- **Fehlerprofil** über alle Sitzungen und gezieltes Üben deiner Schwachstellen.

## Installation

1. Unter **Releases → latest** die Datei `AbiCoach.apk` auf dem Handy öffnen und installieren (Installation aus unbekannten Quellen erlauben).
2. Auf [aistudio.google.com](https://aistudio.google.com) einen kostenlosen API-Schlüssel erstellen („Get API key“).
3. In der App unter **Einstellungen** den Schlüssel einfügen und „Speichern und testen“ tippen.
4. Tipp: Mit Kopfhörern üben – dann hört die App nur dich.

Jeder Push auf `main` baut automatisch eine neue APK (siehe `.github/workflows/android.yml`).

## Kosten und Datenschutz

Standardmäßig nutzt die App den **kostenlosen Gemini-Tarif**: Gemini Live (Prüfer), Gemini Transcribe Live (Mitschrift) und Gemini Flash (Material, Fehlererkennung, Auswertung). Im kostenlosen Tarif darf Google die Eingaben zur Produktverbesserung nutzen. Wer das nicht möchte, aktiviert in AI Studio die Abrechnung. Die Sitzungen selbst werden nur auf dem Handy gespeichert; der API-Schlüssel liegt verschlüsselt im Android Keystore.

## Aufbau

- `gemini/` – Live-WebSocket (Prüfer und Mitschrift) und REST (strukturierte Antworten, Datei-Upload)
- `audio/` – Mikrofon mit Echo-Unterdrückung, Wiedergabe, WAV-Aufnahme, Audio-Routing
- `session/` – Ablauf einer Sitzung, Live-Fehlerprüfung, Material-Generator, Auswertung, alle Prompts
- `ui/` – Jetpack-Compose-Oberfläche

Die Modellnamen lassen sich in den Einstellungen ändern, falls Google neue Versionen veröffentlicht.
