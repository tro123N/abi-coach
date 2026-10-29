# AbiCoach Englisch

Android-App zur Vorbereitung auf die **mündliche Abiturprüfung Englisch in NRW (4. Fach, Grundkurs)** – mit echtem Sprechen statt Tippen. Design und Bedienung wie in [Mental Math Plus](https://github.com/tro123N/MentalMathPlus).

> **Hinweis:** Dieses Projekt wurde mit der KI **Claude (Anthropic)** erstellt. Privates Projekt ohne Gewährleistung, kein offizielles Angebot des Landes NRW.

## Die vier Bereiche (Leiste unten)

### Abi-Weg
- **Punktprognose** mit Spanne, Countdown bis zur Prüfung, Tage in Folge, Übungszeit heute.
- **Heute dran:** Die App wählt aus deinem Lernprofil die Übung, die am meisten Punkte bringt (z. B. „Aufgabe 2 üben: Analysieren – dein größter Hebel“).
- **Meilensteine** bis zur Prognose von 13 Punkten.
- **Einstufung** (ca. 15 Min.): kurzer Text, Vortrag und Gespräch – danach kennt die App deinen Stand.

### Training
- **Prüfungssimulation** wie im Abi: neuer Text mit drei Aufgaben (1. zusammenfassen, 2. analysieren, z. B. „Examine how the author perceives …“, 3. kommentieren), Vorbereitungszeit, Vortrag, Prüfungsgespräch mit Themenwechsel.
- **Einzelne Aufgaben** üben (nur Zusammenfassen, nur Analysieren, nur Kommentieren), **Prüfungsgespräch** und **freies Gesprächstraining**.
- Drei Rückmelde-Stufen: *Prüfung* (keine Unterbrechung), *Flüstern* (Vibration und Korrektur auf dem Bildschirm), *Coach* (der Prüfer unterbricht dich sofort und lässt dich wiederholen).
- **Auswertung** nach dem NRW-Raster (Inhalt 40 %, Darstellungsleistung 60 %): Punkte und Sterne, größte Hebel, jede Aufgabe gegen den Erwartungshorizont, Kompetenzen, Fehler mit Zitat, „So klingt es nach 15 Punkten“ zum Anhören, Aussprache, passende Inhalte aus deinem Kurs, Redemittel.

### Mein Kurs – das Gedächtnis
- **Komplettes Schulbuch, alle Klausuren, Unterrichtsmaterial, später Abi-Vorbereitungsbücher** hochladen: PDF (auch mehrere hundert Seiten), Fotos, Word, Text oder Seiten direkt fotografieren.
- Die KI liest alles im Hintergrund Seite für Seite (auch Handschrift und Lehrer-Korrekturen), ordnet es den 14 Themenfeldern der Abiturvorgaben 2027 zu und merkt sich Fakten, Wortschatz und Texte, die als Prüfungsmaterial taugen.
- Aus **Klausuren** lernt die App deine typischen Fehler und die Kommentare deiner Lehrkraft.
- **Alles hängt zusammen:** Prüfungstexte entstehen passend zu deinem Kurs oder direkt aus deinem Material, der Prüfer fragt nach euren Unterrichtsinhalten, die Auswertung nennt Beispiele aus deinem Kurs.
- **Frag dein Material:** Chat mit dem KI-Coach, der mit Fundstellen aus deinem Material antwortet.

### Statistik
- Verlauf der Prognose, alle Kompetenzen, Übungszeit pro Tag, häufigste Fehler (Übungen und Klausuren) mit „Gezielt üben“, Themenfelder (geübt und Material), alle Sitzungen.

## Welche KI was macht

| Aufgabe | KI | Ohne eigenen Schlüssel |
|---|---|---|
| Prüfer im Gespräch | OpenAI **GPT-Live-1** (hört und spricht gleichzeitig) | Gemini 3.8 Live |
| Wortgetreue Mitschrift | **Gemini 3.5 Transcribe Live** (Modus VERBATIM) | – (Pflicht) |
| Bewertung, Aufgaben, Coach | **Claude Opus 5.5** | Gemini 4 Argon → Gemini 3.8 Flash |
| Fehlerprüfung beim Sprechen | **Claude Haiku 5.5** | Gemini 3.8 Flash |
| Material lesen, Suche | **Gemini 3.8 Flash**, **Gemini Embedding 2** | – (Pflicht) |
| Aufnahme anhören | **Gemini 4 Argon** | Gemini 3.8 Flash |
| Vorlesen | **Gemini 3.8 Flash TTS** | Android-Stimme |

Pflicht ist nur der **Gemini-Schlüssel** (aistudio.google.com/apikey). Die Schlüssel für OpenAI und Anthropic sind optional; fehlt einer oder hat er kein Guthaben, übernimmt Gemini automatisch. Unter Einstellungen → KI gibt es außerdem den Modus **Sparsam** (nur Gemini).

## Installation

1. Unter **Releases → latest** die Datei `AbiCoach.apk` auf dem Handy öffnen und installieren.
2. In der App: Einstellungen → KI → Schlüssel einfügen → „Prüfen“.
3. Unter **Mein Kurs** dein Material hochladen, dann die **Einstufung** machen.
4. Tipp: Mit Kopfhörern üben – dann kannst du den Prüfer auch unterbrechen.

## Kosten und Datenschutz

- GPT-Live-1 kostet etwa 5 ct pro Gesprächsminute, Claude Opus etwa 10–20 ct pro Auswertung, Gemini wenige Cent. Ein ganzes Schulbuch einlesen: unter 1 $.
- Sitzungen, Lernprofil und Material liegen nur auf dem Handy; die Schlüssel verschlüsselt im Android Keystore. Zum Lesen und Bewerten gehen Seiten, Mitschrift und Aufnahme an die jeweiligen Anbieter. Im kostenlosen Gemini-Tarif darf Google die Daten zur Verbesserung nutzen.

## Aufbau

- `ai/` – Gemini (REST, Live, Einbettungen, Vorlesen), Claude (strukturierte Antworten), GPT-Live-1 (Live-WebSocket), `AiHub` verteilt die Aufgaben mit Ausweichmodellen
- `memory/` – Import, Lesen im Hintergrund (WorkManager), Abschnitte, Vektoren, Auswertung, hybride Suche, Kurs-Kontext für alle Prompts
- `profile/` – Lernprofil nach dem NRW-Raster, Prognose, Hebel
- `session/` – Prompts, Live-Gespräch, Live-Fehlerprüfung, Material und Auswertung
- `ui/` – Oberfläche im Stil von Mental Math Plus (Farbthemen, Karten, Sterne, Töne)

Jeder Push auf `main` baut automatisch eine neue APK. Mit `[smoke]` in der Commit-Nachricht läuft zusätzlich ein Test auf einem virtuellen Handy; die Bildschirmfotos liegen danach auf dem Branch `ci-smoke`.

## Lizenz

GNU GPL v3 (siehe `LICENSE.md`). Design und Bauteile stammen aus Mental Math Plus (tro123N), das auf [Mental Math](https://codeberg.org/Mental-Math/MentalMath) von HeldDerTierwelt aufbaut.
