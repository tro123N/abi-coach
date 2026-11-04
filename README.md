# AbiCoach Englisch

Android-App zur Vorbereitung auf die **mündliche Abiturprüfung Englisch in NRW (4. Fach, Grundkurs)** – mit echtem Sprechen statt Tippen. Design und Bedienung wie in [Mental Math Plus](https://github.com/tro123N/MentalMathPlus).

> **Hinweis:** Dieses Projekt wurde mit der KI **Claude (Anthropic)** erstellt. Privates Projekt ohne Gewährleistung, kein offizielles Angebot des Landes NRW.

## Die vier Bereiche (Leiste unten)

### Abi-Weg
- **Kletterweg wie in Mental Math Plus:** 6 Welten zu Format und Aufgaben (Startklar, Zusammenfassen, Analysieren, Kommentieren, Vortrag, Prüfungsgespräch), je eine Welt pro Themenfeld (die mit dem meisten Material zuerst) und die Generalprobe mit drei Simulationen. Jedes Level: Erklärung mit Beispiel zum Anhören, dann die Übung; Sterne nach Punkten; Wiederholen nach 1, 3, 7, 14 und 30 Tagen; eingerostete Levels werden aufgefrischt.
- **Punktprognose** mit Spanne, Countdown bis zur Prüfung, Tage in Folge, Übungszeit heute.
- **Heute dran:** Die App wählt aus deinem Lernprofil die Übung, die am meisten Punkte bringt (z. B. „Aufgabe 2 üben: Analysieren – dein größter Hebel“).
- **Meilensteine** bis zur Prognose von 13 Punkten.
- **Einstufung** (ca. 15 Min.): kurzer Text, Vortrag und Gespräch – danach kennt die App deinen Stand und öffnet Welten, die du schon kannst.
- **Tägliche Erinnerung** zur gewählten Uhrzeit mit dem nächsten Schritt – nur, wenn du an dem Tag noch nicht genug geübt hast.

### Training
- **Prüfungssimulation** wie im Abi: neuer Text mit drei Aufgaben (1. zusammenfassen, 2. analysieren, z. B. „Examine how the author perceives …“, 3. kommentieren), Vorbereitungszeit, Vortrag, Prüfungsgespräch mit Themenwechsel.
- **Einzelne Aufgaben** üben (nur Zusammenfassen, nur Analysieren, nur Kommentieren), **Prüfungsgespräch** und **freies Gesprächstraining**.
- Drei Rückmelde-Stufen: *Prüfung* (keine Unterbrechung), *Flüstern* (starke Vibration, die Korrektur steht groß in einer Leiste am Bildschirm, mit Kopfhörern sagt sie eine leise Stimme), *Coach* (im Gespräch korrigiert dich der Prüfer selbst mit Stimme und lässt dich den Satz wiederholen; im Vortrag flüstert der Coach nur).
- **Gezielt trainieren:** *Gesprächs-Sprint* (3 Minuten, so viel wie möglich sprechen), *Wortschatz* mit Wiederholung nach Plan (Redemittel, 15-Punkte-Sätze, deine Fehler und der Wortschatz aus deinem Kurs), *Aussprache-Labor* (anhören, nachsprechen, Bewertung pro Wort) und der *KI-Coach*, der dir passende Übungen als Knöpfe vorschlägt.
- **KI-Tagesplan:** Ganz oben auf dem Abi-Weg plant der Coach (Gemini 4 Argon) jeden Tag drei Schritte aus deinem Stand: Kompetenzen, letzte Auswertung, Fehler, Lehrkraft-Kommentare, Prüfungstermin. Jeder Schritt startet mit einem Tipp.
- **Alles läuft im Hintergrund weiter:** Auswertung, Einlesen von Kursmaterial und das Schreiben des Prüfungstexts laufen weiter, wenn du die App schließt oder das Handy sperrst, und melden sich mit einer Benachrichtigung. Der nächste Prüfungstext wird auf Wunsch schon vorab erstellt, damit der Start sofort geht.
- **Vorbereitung geht nicht verloren:** Text, Aufgaben und deine Notizen werden gespeichert; wenn die Vorbereitungszeit zu Ende ist, klingelt eine Benachrichtigung – auch bei gesperrtem Handy.
- **Live bleibt zuverlässig:** Während des Gesprächs bleibt der Bildschirm an und ein Mikrofon-Dienst („AbiCoach hört zu“) hält die Verbindung am Leben, auch wenn du kurz in eine andere App wechselst.
- **Das Ohr (Flüstern und Coach):** Die App erkennt selbst, wann du eine Pause machst, und Gemini hört sich jede Äußerung direkt an – Mitschrift und Fehler in einem Schritt, meist 2–3 Sekunden nach der Pause. Das funktioniert auch, wenn die Live-Mitschrift hängt, und vermeidet falsche Hinweise durch verhörte Wörter. Der **Coach** spricht in allen Teilen: im Gespräch sagt der Prüfer die Korrektur selbst, im Vortrag eine Coach-Stimme; danach wartet er, bis du den Satz richtig wiederholst (✓ auf dem Bildschirm). Wichtig sind ihm vor allem deine bekannten Fehlermuster. Nach dem Vortrag folgt eine kurze **Korrekturrunde**.
- **Live-Status und Technik-Protokoll:** Kleine Anzeigen zeigen während des Übens, ob Mikrofon, Mitschrift, Ohr, Coach und Prüfer arbeiten; in der Auswertung steht ein kopierbares Protokoll. Unter Einstellungen → System prüfen gibt es zusätzlich einen **Mikrofon-Test**, bei dem du selbst einen Satz mit Fehler sagst.
- **Fehlergedächtnis:** Jede Fehlerart (z. B. „third person -s“, „since vs for“) aus Übungen, Auswertungen und Klausuren wird mit Beispielen und Verlauf gespeichert: neu, aktiv, wird besser, behoben. Der Prüfer stellt in Übungen gezielt Fragen, die genau diese Strukturen verlangen.
- **Sprech-Training:** Kurze Sprech-Übungen (richtig sagen, umformen, Satz vollenden, Redemittel einsetzen, kurz antworten) aus deinen eigenen Fehlern, mit Sätzen zu deinen Kursthemen. Gemini hört sofort zu; was sitzt, kommt seltener wieder, was hakt, bald wieder.
- **Musterfassung:** In der Auswertung schreibt die KI deinen eigenen Vortrag (oder deine Antworten) auf 15-Punkte-Niveau um – mit deinen Ideen, Top-Sprache und dem, was gefehlt hat, zum Anhören.
- **Sag es richtig:** Bei jedem Fehler und jedem „So klingt es nach 15 Punkten“-Satz gibt es einen Knopf – du sprichst den Satz nach und bekommst gleich Rückmeldung.
- **Zweiter Versuch:** Nach der Auswertung kannst du die schwächste Aufgabe mit demselben Text noch einmal machen (2 Min. Vorbereitung). Die Auswertung zeigt vorher/nachher; die Bewertung selbst kennt frühere Punkte nicht und bleibt fair.
- **Fortschritt:** Die Auswertung vergleicht mit der letzten Sitzung: „behoben ✓“ und „noch 3× aufgetreten“.
- **Vortrags-Coach** in der Auswertung: Zeit pro Aufgabe gegen die Empfehlung, Tempo (Wörter pro Minute), Füllwörter, lange Pausen und eine Warnung, wenn du abliest.
- **Auswertung** nach dem NRW-Raster (Inhalt 40 %, Darstellungsleistung 60 %): Punkte und Sterne, größte Hebel, jede Aufgabe gegen den Erwartungshorizont, Kompetenzen, Fehler mit Zitat, „So klingt es nach 15 Punkten“ zum Anhören, Aussprache, passende Inhalte aus deinem Kurs, Redemittel.

### Mein Kurs – das Gedächtnis
- **Komplettes Schulbuch, alle Klausuren, Unterrichtsmaterial, später Abi-Vorbereitungsbücher** hochladen: PDF (auch mehrere hundert Seiten), Fotos, Word, Text oder Seiten direkt fotografieren.
- Die KI liest alles im Hintergrund Seite für Seite (auch Handschrift und Lehrer-Korrekturen), ordnet es den 14 Themenfeldern der Abiturvorgaben 2027 zu und merkt sich Fakten, Wortschatz und Texte, die als Prüfungsmaterial taugen.
- Aus **Klausuren** lernt die App deine typischen Fehler und die Kommentare deiner Lehrkraft.
- **Alles hängt zusammen:** Prüfungstexte entstehen passend zu deinem Kurs oder direkt aus deinem Material, der Prüfer fragt nach euren Unterrichtsinhalten, die Auswertung nennt Beispiele aus deinem Kurs.
- **Frag dein Material:** Chat mit dem KI-Coach, der mit Fundstellen aus deinem Material antwortet.
- **Themen-Dossiers:** Für jedes Themenfeld verdichtet die KI alles Material zu einem Dossier: Kernwissen, Beispiele mit Fundstelle, Zitate, Wortschatz, Argumente pro/contra, Verbindungen zu anderen Themenfeldern und typische Prüferfragen. Prüfer, Prüfungstexte, Bewertung und Coach nutzen es; es aktualisiert sich bei neuem Material von selbst.
- **Echte Abi-Prüfungsaufgaben:** Lädst du echtes Abi-Material hoch (Art „Echte Abi-Prüfungsaufgaben“ oder „Abi-Vorbereitung“), findet die App darin Text, Original-Aufgaben, Erwartungshorizont und Lösungsvorschlag. Du übst genau diese Aufgaben und wirst nach dem Original bewertet; neue Texte richten sich nach ihrem Stil.

### Statistik
- **Prüfungsbereitschaft** je Prüfungsteil, **Trends** je Kompetenz (↑/↓), **Fehlergedächtnis** und **Themen-Kompass** (wie gut jedes Themenfeld sitzt, welche du noch nie geübt hast). Lange nicht geübte Kompetenzen werden mit der Zeit wieder unsicherer und kommen zurück in den Plan.
- **So bewertet deine Lehrkraft:** Kommentare und häufigste Anstreichungen aus deinen Klausuren.
- Verlauf der Prognose, alle Kompetenzen, Übungszeit pro Tag, häufigste Fehler (Übungen und Klausuren) mit „Gezielt üben“, Themenfelder (geübt und Material), alle Sitzungen.

## Welche KI was macht

Alles kommt von **einem Anbieter: Google**. Du brauchst nur einen Schlüssel.

| Aufgabe | Modell |
|---|---|
| Prüfer im Gespräch (hört und spricht gleichzeitig) | **Gemini 3.8 Live** |
| Wortgetreue Mitschrift | **Gemini 3.5 Transcribe Live** (Modus VERBATIM) |
| Bewertung, Aufgaben, Coach | **Gemini 4 Argon** (mit Denkstufe) → Ausweichmodell Gemini 3.8 Flash |
| Ohr: hört jede Äußerung an (Fehler, Mitschrift), Sag-es-richtig, Sprech-Training | **Gemini 3.8 Flash** (Audio, niedrige Denkstufe, schnell) |
| Coach-Stimme im Vortrag | **Gemini 3.8 Flash TTS** → Android-Stimme |
| Themen-Dossiers, Sprech-Übungen, Musterfassung, echte Aufgaben auslesen | **Gemini 4 Argon** → Gemini 3.8 Flash |
| Material lesen | **Gemini 3.8 Flash** |
| Suche im Material | **Gemini Embedding 2** |
| Aufnahme anhören | **Gemini 4 Argon** → Gemini 3.8 Flash |
| Vorlesen | **Gemini 3.8 Flash TTS** → Android-Stimme |

Den Schlüssel gibt es kostenlos zu erstellen unter aistudio.google.com/apikey. Unter Einstellungen → KI wählst du **Beste Qualität** oder **Sparsam** (Gemini 3.8 Flash für alles). Mit **System prüfen → Alles testen** prüft die App Schlüssel, alle Modelle, Prüfer-Stimme und Coach-Anweisung einmal durch.

## Installation

1. Unter **Releases → latest** die Datei `AbiCoach.apk` auf dem Handy öffnen und installieren.
2. In der App: Einstellungen → KI → Google-Schlüssel einfügen → „Prüfen“, dann **System prüfen → Alles testen**.
3. Unter **Mein Kurs** dein Material hochladen, dann die **Einstufung** machen.
4. Tipp: Mit Kopfhörern üben – dann kannst du den Prüfer auch unterbrechen.

## Kosten und Datenschutz

- Nur Google-Kosten: das Live-Gespräch mit dem Prüfer ist der größte Posten (je nach Tarif einige Cent pro Gesprächsminute), eine Auswertung kostet wenige Cent, ein ganzes Schulbuch einlesen unter 1 $. Mit **Sparsam** wird es noch günstiger.
- Sitzungen, Lernprofil und Material liegen nur auf dem Handy; der Schlüssel liegt verschlüsselt im Android Keystore. Zum Lesen und Bewerten gehen Seiten, Mitschrift und Aufnahme an Google. Im kostenlosen Gemini-Tarif darf Google die Daten zur Verbesserung nutzen.

## Aufbau

- `ai/` – Gemini (REST mit Denkstufen, Live-WebSocket, Einbettungen, Vorlesen); `AiHub` verteilt die Aufgaben auf die Modelle und springt bei Ausfall auf das Ausweichmodell
- `memory/` – Import, Lesen im Hintergrund (WorkManager), Abschnitte, Vektoren, Auswertung, hybride Suche, Kurs-Kontext für alle Prompts
- `profile/` – Lernprofil nach dem NRW-Raster, Prognose, Hebel
- `path/` – Abi-Weg: Welten und Levels (`Curriculum`), Freischalten, Sterne und Wiederholungsplan (`PathEngine`), Erinnerung
- `practice/` – Wortschatz-Karten mit Wiederholungsplan, Vortrags-Kennzahlen, Aussprache-Labor, „Sag es richtig“
- `learn/` – Fehlergedächtnis (Fehlermuster mit Zustand und Wiederholplan) und Sprech-Training
- `memory/` zusätzlich: Themen-Dossiers (`Dossier.kt`) und echte Prüfungsaufgaben (`ExamUnits.kt`)
- `session/` – Prompts, Live-Gespräch (mit Mikrofon-Dienst), Live-Fehlerprüfung, Material im Hintergrund, gespeicherte Vorbereitung und Alarm, Auswertung und Fortschritt, Systemprüfung
- `ui/` – Oberfläche im Stil von Mental Math Plus (Farbthemen, Karten, Sterne, Töne)

Jeder Push auf `main` baut automatisch eine neue APK. Mit `[smoke]` in der Commit-Nachricht läuft zusätzlich ein Test auf einem virtuellen Handy; die Bildschirmfotos liegen danach auf dem Branch `ci-smoke`.

## Lizenz

GNU GPL v3 (siehe `LICENSE.md`). Design und Bauteile stammen aus Mental Math Plus (tro123N), das auf [Mental Math](https://codeberg.org/Mental-Math/MentalMath) von HeldDerTierwelt aufbaut.
