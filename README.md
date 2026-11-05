# AbiCoach Englisch

Android-App zur Vorbereitung auf die **mündliche Abiturprüfung Englisch in NRW (4. Fach, Grundkurs)** – mit echtem Sprechen statt Tippen. Ursprünglich im Design von [Mental Math Plus](https://github.com/tro123N/MentalMathPlus), seit Version 2.0.21 im **Living-Flow-Stil** (nach ColorOS 17) mit schwebender Glas-Leiste.

> **Hinweis:** Dieses Projekt wurde mit der KI **Claude (Anthropic)** erstellt. Privates Projekt ohne Gewährleistung, kein offizielles Angebot des Landes NRW.

## Design: Living Flow

- **Fließendes Licht:** Hinter allem liegen drei weiche Farblichter im Ton des Farbthemas. Wechselst du die Seite, fließen sie langsam an einen neuen Platz – beim Lesen bewegt sich nichts.
- **Milchglas:** Karten und Knöpfe sind halbtransparentes Glas mit heller Lichtkante oben und weichem Schatten; wichtige Zahlen stehen auf einer farbigen Hero-Karte.
- **Schwebende Glas-Leiste** unten (nach der „glass navigation bar“ aus Morphe für YouTube): eine Kapsel mit echter Unschärfe (ab Android 12), Glanz, Lichtkante und Schatten; die Markierung gleitet zum gewählten Bereich und dehnt sich dabei wie ein Tropfen. Der Inhalt scrollt dahinter weiter.
- **Federndes Feedback:** Knöpfe, Kacheln und Karten geben beim Tippen nach und federn zurück; Seiten gleiten ineinander.
- **Übersichtlicher:** große Titel, Kacheln statt langer Knopflisten, Umschalter statt endloser Seiten, aufklappbare Details in der Auswertung.

## Die vier Bereiche (Glas-Leiste unten)

**Beim ersten Start** führt eine **Einrichtung** Schritt für Schritt durch alles, was die App zum Funktionieren braucht: KI wählen, Schlüssel holen, eintragen und prüfen, Prüfungstermin, Kursthemen und Mikrofon. Am Ende zeigt eine Checkliste, was noch fehlt (ohne Schlüssel erinnert die Startseite daran). Wer eine Sicherung hat, lädt sie gleich auf der ersten Seite.

Danach startet auf Wunsch eine **geführte Tour durch die echte App**: Der Bildschirm wird abgedunkelt, das erklärte Element leuchtet hervor, eine Sprechblase sagt, wofür es da ist – von der Prognose über Üben, Fehler-Werkstatt und Kurs bis zum Fortschritt-Graphen und den Einstellungen. Die App wechselt dabei selbst die Bereiche und scrollt hin. Die Tour lässt sich jederzeit überspringen und unter Einstellungen → Über AbiCoach neu starten (dort auch „Einrichtung öffnen“).

### Heute
- **Nur das Wichtigste:** Prognose mit Hochrechnung auf einer Hero-Karte, **Dein Plan für heute** (der Plan des Lern-Gehirns, vom KI-Coach in zwei Sätzen erklärt), das nächste Level und die letzte Auswertung als Kacheln. Kletterweg, Meilensteine und „Dein Stand“ sind nach „Üben → Abi-Weg“ und „Fortschritt“ umgezogen.
- **Kletterweg wie in Mental Math Plus:** 6 Welten zu Format und Aufgaben (Startklar, Zusammenfassen, Analysieren, Kommentieren, Vortrag, Prüfungsgespräch), je eine Welt pro Themenfeld (die mit dem meisten Material zuerst) und die Generalprobe mit drei Simulationen. Jedes Level: Erklärung mit Beispiel zum Anhören, dann die Übung; Sterne nach Punkten; Wiederholen nach 1, 3, 7, 14 und 30 Tagen; eingerostete Levels werden aufgefrischt.
- **Punktprognose** mit Spanne, Countdown bis zur Prüfung, Tage in Folge, Übungszeit heute.
- **Heute dran – das Lern-Gehirn:** Die App rechnet für jede mögliche Übung aus, wie viele Punkte sie dir *pro Minute* gerade bringt – aus allen Daten zusammen: jede Kompetenz mit ihrer Unsicherheit und wie lange sie nicht geübt wurde, Trends, Punkte je Aufgabe, Themen-Kompass, Fehlergedächtnis, Lerner-Gedächtnis, Wortschatz, Aussprache, Abwechslung und Tage bis zur Prüfung. Daraus wird der Tagesplan (kurzes Warm-up, Hauptübung, kurzer Abschluss) mit Begründung. Je nach **Phase** (Aufbau → Vertiefung → Generalprobe → Endspurt) plant sie anders, z. B. mehr ganze Prüfungen kurz vor dem Abi.
- **Hochrechnung:** Aus dem Verlauf deiner Prognose rechnet die App hoch, wo du am Prüfungstag voraussichtlich stehst, und zeigt dein Tempo pro Woche.
- **Meilensteine** bis zur Prognose von 13 Punkten.
- **Einstufung** (ca. 15 Min.): kurzer Text, Vortrag und Gespräch – danach kennt die App deinen Stand und öffnet Welten, die du schon kannst.
- **Tägliche Erinnerung** zur gewählten Uhrzeit mit dem nächsten Schritt – nur, wenn du an dem Tag noch nicht genug geübt hast.

### Üben
- **Umschalter „Frei üben | Abi-Weg“:** freies Üben als Kacheln, nach Prüfungsteil sortiert (ganze Prüfung, Teil 1, Teil 2, gezielt) mit den letzten Punkten und fälligen Dingen direkt auf der Kachel; die Aufgabe mit deinem größten Hebel ist markiert. Unter „Abi-Weg“ liegen der Kletterweg mit allen Welten und Levels.
- **Prüfungssimulation** wie im Abi: neuer Text mit drei Aufgaben (1. zusammenfassen, 2. analysieren, z. B. „Examine how the author perceives …“, 3. kommentieren), Vorbereitungszeit, Vortrag, Prüfungsgespräch mit Themenwechsel.
- **Einzelne Aufgaben** üben (nur Zusammenfassen, nur Analysieren, nur Kommentieren), **Prüfungsgespräch** und **freies Gesprächstraining**.
- Drei Rückmelde-Stufen: *Prüfung* (keine Unterbrechung), *Flüstern* (starke Vibration, die Korrektur steht groß in einer Leiste am Bildschirm, mit Kopfhörern sagt sie eine leise Stimme), *Coach* (im Gespräch korrigiert dich der Prüfer selbst mit Stimme und lässt dich den Satz wiederholen; im Vortrag flüstert der Coach nur).
- **Gezielt trainieren:** *Gesprächs-Sprint* (3 Minuten, so viel wie möglich sprechen), *Wortschatz* mit Wiederholung nach Plan (Redemittel, 15-Punkte-Sätze, deine Fehler und der Wortschatz aus deinem Kurs), *Aussprache-Labor* (anhören, nachsprechen, Bewertung pro Wort) und der *KI-Coach*, der dir passende Übungen als Knöpfe vorschlägt.
- **KI-Tagesplan:** In der Karte „Dein Plan für heute“ erklärt der Coach (Gemini 4 Argon) den berechneten Plan des Lern-Gehirns in zwei Sätzen – mit deinen Zahlen, Fehlern, Gewohnheiten und Lehrkraft-Kommentaren. Jeder Schritt startet direkt aus der Karte.
- **Ziele für jede Übung:** Vor jeder Übung setzt die App dir bis zu drei konkrete Ziele (ein Fehlermuster vermeiden, die schwächste Kompetenz dieser Aufgabe, eine Gewohnheit ablegen). Die Auswertung sagt dir, ob du sie erreicht hast – mit Beleg aus deinem Gesagten.
- **Lerner-Gedächtnis:** Die KI merkt sich nach jeder Sitzung deine Gewohnheiten (z. B. „antwortet im Gespräch oft nur mit einem Satz“) und Stärken, zählt, wie oft sie vorkommen, und streicht sie, wenn du das Ziel dagegen zweimal erreicht hast. Prüfer, Ziele, Plan und Coach richten sich danach.
- **Mehrere Themenfelder in Teil 2 – über die Halbjahre verteilt:** Wie im Abitur (§ 38 APO-GOSt: die Prüfung darf nicht bei einem Kurshalbjahr bleiben) wählst du beim Einrichten bis zu zwei weitere Themenfelder, die zusammen geprüft werden; an jedem Themenfeld steht, aus welchem Halbjahr (Q1.1–Q2.2) es kommt, und die App warnt, wenn alles aus einem Halbjahr ist. Ohne Auswahl nimmt die App Themen aus **anderen Halbjahren als Teil 1**, die du am nötigsten hast. Der Prüfer weiß, welches Thema aus welchem Halbjahr stammt, wechselt zur richtigen Zeit und fragt nach Zusammenhängen über die Halbjahre hinweg.
- **Gesprächsstil wählbar:** *Wie im Abitur* (der Prüfer führt straff durch seine Fragen), *Ausgewogen* oder *Geht auf dich ein* (er greift deine Ideen und Beispiele auf, fragt dort nach, wo du gerade bist, und lenkt das Gespräch trotzdem in Richtung der Aufgabe und der anderen Themen). Den Stil stellst du pro Übung beim Einrichten ein, die Grundeinstellung unter Einstellungen → Prüfung.
- **Mitschrift mit „Mitscrollen“:** Im Live-Gespräch und im Vortrag steht unten die Mitschrift – wer was gesagt hat (Prüfer, Coach, du), farbig getrennt. Mit „Mitscrollen an“ scrollt die Seite von selbst mit, sodass die neueste Zeile immer zu sehen ist; ein kleiner Knopf schaltet das aus, wenn du selbst nach oben lesen willst. Die Wahl bleibt gespeichert.
- **Härte des Prüfers:** *Freundlich*, *Normal*, *Streng* oder *Stresstest*. Im Stresstest ist der Prüfer kalt, ungeduldig und skeptisch: er unterbricht, nennt vage Antworten beim Namen, konfrontiert dich mit Widersprüchen, verlangt Belege, springt unerwartet zu anderen Themen und lässt unangenehme Pausen stehen – zum Üben, unter Druck ruhig zu bleiben. Er greift nur die Antworten an, nie die Person. Die Punkte bleiben fair nach dem NRW-Raster; nur der Ton der schriftlichen Rückmeldung wird schonungsloser, und die Auswertung beurteilt zusätzlich, wie souverän du unter Druck geblieben bist. Einstellbar beim Einrichten und unter Einstellungen → Prüfung.
- **Knopf gedrückt halten (wählbar):** Statt „einfach sprechen“ (der Prüfer antwortet nach einer kurzen Pause) kannst du im Gespräch einen großen Knopf gedrückt halten, solange du sprichst. Beim Drücken verstummt der Prüfer sofort, beim Loslassen antwortet er sofort – ohne auf eine Pause zu warten (Gemini: manuelle Sprechphasen der Live-API, OpenAI: Turn wird beim Loslassen abgeschickt, Claude: das Handy schreibt sofort fertig). Damit kein Wortanfang fehlt, nimmt die App die letzten 0,3 Sekunden vor dem Drücken mit, nach dem Loslassen 0,12 Sekunden. Einstellbar beim Einrichten jeder Übung und unter Einstellungen → Prüfung.
- **Füllwörter zählen:** Die Mitschrift schreibt Füllwörter („um“, „uh“, „er“, „hmm“) mit, soweit die Spracherkennung sie liefert; zusätzlich zählt die KI beim Anhören der Aufnahme alle Füllwörter (auch „like“/„you know“ als Füller) direkt im Ton. Die Auswertung zeigt „Füllwörter in deiner Aufnahme: 23× – vor allem ‚ähm‘“, und Flüssigkeit wird danach bewertet.
- **Abbrechen ohne Auswertung:** Neben „Auswerten“ liegt ein kleiner Knopf „Abbrechen“ (oder oben das ✕). Nach einer Rückfrage wird die Übung beendet und **nicht ausgewertet** – Aufnahme und Mitschrift werden gelöscht, auch ein schon gespeicherter Teil 1 einer Simulation; sie zählt nicht für deinen Lernstand.
- **Fehler-Werkstatt:** Eine eigene Lernumgebung für jedes deiner Fehlermuster in vier Schritten: *Verstehen* (Regel in einfachen Worten, Eselsbrücke, richtig/falsch im Vergleich, zum Anhören), *Erkennen* (Sätze beurteilen: richtig oder falsch?), *Sagen* (kurze Sprech-Übungen, die KI hört zu) und *Frei sprechen* (eine Prüfungsfrage, bei der du die Struktur selbst verwenden musst). Danach schreibt die KI als **Tutor ein Gedächtnis** für dieses Muster: was du schon verstanden hast, was noch schwerfällt, welche Erklärung geholfen hat und was sie als Nächstes übt. Die nächste Werkstatt, das Sprech-Training, das Fehlergedächtnis und der Prüfer in Übungen (Flüstern und Coach) richten sich danach; die Stufe steigt bei ≥ 80 % und sinkt unter 50 %. Erreichbar über „Üben → Gezielt trainieren“, das Fehlergedächtnis und direkt aus der Auswertung.
- **Kompetenzen einzeln üben:** Für jede der 11 Kompetenzen des NRW-Rasters (Inhalt und Darstellung) gibt es eigene Übungen – z. B. nur Aufgabenbezug, nur Sachwissen, nur Interaktion, Flüssigkeit, Wortschatz, Aussprache oder Kommentieren. Jede Übung hat die Kompetenz als erstes Ziel, der Prüfer achtet darauf und die Auswertung schaut besonders genau hin. Pro Kompetenz siehst du einen kleinen Verlauf.
- **Gesprächsregie:** Während des Gesprächs achtet die App wie eine Prüfungsvorsitzende auf Zeit und Antworten und gibt dem Prüfer unhörbar kurze Hinweise: Themenwechsel (bei zwei Themen nach etwa der Hälfte, bei drei nach einem und zwei Dritteln), tiefer nachfragen nach mehreren kurzen Antworten, in Übungen Fragen, die deine Fehlermuster provozieren, eine wertende Schlussfrage in der letzten Minute; bei sehr langen Monologen hakt er ein.
- **Für dich angepasstes Material:** Neue Prüfungstexte wählen Textart, Operatoren (die du am wenigsten geübt hast) und Niveau passend zu dir; das Prüfungsgespräch geht zu den Themenfeldern, die du am nötigsten hast.
- **Zweitbewertung:** Ganze Prüfungen, Einstufung, ganze Vorträge und lange Gespräche bewertet eine zweite, unabhängige Prüferin; die Note ist der Mittelwert – wie im Prüfungsausschuss. So wird dein Lernprofil verlässlicher.
- **Auswertung in der Cloud (Google):** Die App lädt die Aufnahme hoch und gibt Bewertung und Zweitbewertung als Hintergrund-Aufträge an Google (Interactions API) – ab dann läuft alles in Googles Rechenzentrum weiter, auch wenn du die App schließt, das Handy sperrst oder offline bist. Sie hängt nie fest: Scheitert der Auftrag, ist die Antwort kaputt oder dauert er länger als 6 Minuten, wertet die App sofort direkt aus; nur bei fehlendem Netz versucht sie es höchstens dreimal später. Erst- und Zweitbewertung laufen immer gleichzeitig. Jede Auswertung lässt sich **abbrechen** (auch in der Cloud); die Sitzung bleibt gespeichert.
- **Sitzungen aufräumen:** Unter „Alle Sitzungen“ wählst du mehrere Sitzungen aus (oder alle nicht ausgewerteten) und löschst sie ganz – samt Aufnahme und wartender Auswertung. Ein Tipp auf eine nicht ausgewertete Sitzung startet keine Auswertung mehr von selbst: Du entscheidest, ob sie ausgewertet oder gelöscht wird.
- **Alles läuft im Hintergrund weiter:** Einlesen von Kursmaterial und das Schreiben des Prüfungstexts laufen weiter, wenn du die App schließt oder das Handy sperrst, und melden sich mit einer Benachrichtigung. Der nächste Prüfungstext wird auf Wunsch schon vorab erstellt, damit der Start sofort geht.
- **Vorbereitung geht nicht verloren:** Text, Aufgaben und deine Notizen werden gespeichert; wenn die Vorbereitungszeit zu Ende ist, klingelt eine Benachrichtigung – auch bei gesperrtem Handy.
- **Live bleibt zuverlässig:** Während des Gesprächs bleibt der Bildschirm an und ein Mikrofon-Dienst („AbiCoach hört zu“) hält die Verbindung am Leben, auch wenn du kurz in eine andere App wechselst.
- **Das Ohr (Flüstern und Coach):** Die App erkennt selbst, wann du eine Pause machst (0,4 Sekunden reichen), und die KI hört sich jede Äußerung direkt an – Mitschrift und Fehler in einem Schritt. Zusätzlich prüft eine schnelle Spur jeden fertigen Satz der Live-Mitschrift sofort (nur klare, schwere Fehler, damit ein verhörtes Wort keinen falschen Hinweis auslöst). Der **Coach** unterbricht in Echtzeit: in deiner nächsten Atempause (spätestens nach 2,5 Sekunden), höchstens alle 6 Sekunden, mit eigener Stimme – der Satz wird schon vorbereitet, während du weitersprichst; der Prüfer bekommt eine Notiz und wiederholt nichts. Danach wartet der Coach, bis du den Satz richtig wiederholst (✓ auf dem Bildschirm). Wichtig sind ihm vor allem deine bekannten Fehlermuster. Nach dem Vortrag folgt eine kurze **Korrekturrunde**.
- **Vorlesen ohne Wartezeit und ohne Knall:** Die Stimme kommt gestreamt (sie beginnt, bevor der ganze Satz fertig ist) und wird gespeichert – beim zweiten Tippen kommt sie sofort. Wörter im Aussprache-Labor werden schon vorab gemacht. Das Rauschen, das manche KI-Stimmen nach dem letzten Wort anhängen, schneidet die App ab (sie erkennt das Ende der echten Stimme an ihrer Tonhöhe) und blendet weich aus. Kommt die KI-Stimme nicht innerhalb von 3 Sekunden, liest die Handy-Stimme.
- **Live-Status und Technik-Protokoll:** Kleine Anzeigen zeigen während des Übens, ob Mikrofon, Mitschrift, Ohr, Coach und Prüfer arbeiten; in der Auswertung steht ein kopierbares Protokoll. Unter Einstellungen → System prüfen gibt es zusätzlich einen **Mikrofon-Test**, bei dem du selbst einen Satz mit Fehler sagst.
- **Fehlergedächtnis:** Jede Fehlerart (z. B. „third person -s“, „since vs for“) aus Übungen, Auswertungen und Klausuren wird mit Beispielen und Verlauf gespeichert: neu, aktiv, wird besser, behoben. Der Prüfer stellt in Übungen gezielt Fragen, die genau diese Strukturen verlangen.
- **Sprech-Training:** Kurze Sprech-Übungen (richtig sagen, umformen, Satz vollenden, Redemittel einsetzen, kurz antworten) aus deinen eigenen Fehlern, mit Sätzen zu deinen Kursthemen. Die KI hört sofort zu; was sitzt, kommt seltener wieder, was hakt, bald wieder.
- **Musterfassung:** In der Auswertung schreibt die KI deinen eigenen Vortrag (oder deine Antworten) auf 15-Punkte-Niveau um – mit deinen Ideen, Top-Sprache und dem, was gefehlt hat, zum Anhören.
- **Sag es richtig:** Bei jedem Fehler und jedem „So klingt es nach 15 Punkten“-Satz gibt es einen Knopf – du sprichst den Satz nach und bekommst gleich Rückmeldung.
- **Zweiter Versuch:** Nach der Auswertung kannst du die schwächste Aufgabe mit demselben Text noch einmal machen (2 Min. Vorbereitung). Die Auswertung zeigt vorher/nachher; die Bewertung selbst kennt frühere Punkte nicht und bleibt fair.
- **Fortschritt:** Die Auswertung vergleicht mit der letzten Sitzung: „behoben ✓“ und „noch 3× aufgetreten“.
- **Vortrags-Coach** in der Auswertung: Zeit pro Aufgabe gegen die Empfehlung, Tempo (Wörter pro Minute), Füllwörter, lange Pausen und eine Warnung, wenn du abliest.
- **Auswertung** nach dem NRW-Raster (Inhalt 40 %, Darstellungsleistung 60 %): Punkte und Sterne, größte Hebel, jede Aufgabe gegen den Erwartungshorizont, Kompetenzen, Fehler mit Zitat, „So klingt es nach 15 Punkten“ zum Anhören, Aussprache, passende Inhalte aus deinem Kurs, Redemittel.

### Kurs – das Gedächtnis
- **Komplettes Schulbuch, alle Klausuren, Unterrichtsmaterial, später Abi-Vorbereitungsbücher** hochladen: PDF (auch mehrere hundert Seiten), Fotos, Word, Text oder Seiten direkt fotografieren.
- Die KI liest alles im Hintergrund Seite für Seite (auch Handschrift und Lehrer-Korrekturen), ordnet es den 14 Themenfeldern der Abiturvorgaben 2027 zu und merkt sich Fakten, Wortschatz und Texte, die als Prüfungsmaterial taugen.
- Aus **Klausuren** lernt die App deine typischen Fehler und die Kommentare deiner Lehrkraft.
- **Alles hängt zusammen:** Prüfungstexte entstehen passend zu deinem Kurs oder direkt aus deinem Material, der Prüfer fragt nach euren Unterrichtsinhalten, die Auswertung nennt Beispiele aus deinem Kurs.
- **Frag dein Material:** Chat mit dem KI-Coach, der mit Fundstellen aus deinem Material antwortet.
- **Themen-Dossiers:** Für jedes Themenfeld verdichtet die KI alles Material zu einem Dossier: Kernwissen, Beispiele mit Fundstelle, Zitate, Wortschatz, Argumente pro/contra, Verbindungen zu anderen Themenfeldern und typische Prüferfragen. Prüfer, Prüfungstexte, Bewertung und Coach nutzen es; es aktualisiert sich bei neuem Material von selbst.
- **Halbjahre:** Die App erkennt aus deinem Material, welches Themenfeld in welchem Kurshalbjahr (Q1.1, Q1.2, Q2.1, Q2.2) dran war; unter „Kurs → Halbjahre“ kannst du es selbst einstellen. Die Prüfungssimulation mischt danach die Halbjahre wie im Abi.
- **Echte Abi-Prüfungsaufgaben:** Lädst du echtes Abi-Material hoch (Art „Echte Abi-Prüfungsaufgaben“ oder „Abi-Vorbereitung“), findet die App darin Text, Original-Aufgaben, Erwartungshorizont und Lösungsvorschlag. Du übst genau diese Aufgaben und wirst nach dem Original bewertet; neue Texte richten sich nach ihrem Stil.

### Fortschritt
- **Vier Abschnitte statt einer langen Liste:** Überblick (Entwicklung, Prognose-Verlauf, Prüfungsbereitschaft, Lerner-Gedächtnis, Meilensteine, Übungszeit), Kompetenzen (Entwicklung je Kompetenz, Dein Stand, alle Kompetenzen, Prüferprofil, „Kompetenzen einzeln üben“), Fehler (häufigste Fehler, Fehlergedächtnis) und Themen (Themen-Kompass).
- **Entwicklung über die Zeit:** Ein Graph zeigt deine Punkte aus allen ausgewerteten Übungen mit Trendlinie – umschaltbar zwischen *Gesamt*, *Inhalt & Sprache* (zwei Linien) und einzelnen *Kompetenzen*. Darunter steht, um wie viel du dich seit den ersten Übungen verbessert hast.
- **Hochrechnung** auf den Prüfungstag und die aktuelle Phase, **Das weiß die App über dich** (Gewohnheiten, Stärken, Überwundenes), **Prüfungsbereitschaft** je Prüfungsteil, **Trends** je Kompetenz (↑/↓), **Fehlergedächtnis** und **Themen-Kompass** (wie gut jedes Themenfeld sitzt, welche du noch nie geübt hast). Lange nicht geübte Kompetenzen werden mit der Zeit wieder unsicherer und kommen zurück in den Plan.
- **So bewertet deine Lehrkraft:** Kommentare und häufigste Anstreichungen aus deinen Klausuren.
- Verlauf der Prognose, alle Kompetenzen, Übungszeit pro Tag, häufigste Fehler (Übungen und Klausuren) mit „Gezielt üben“, Themenfelder (geübt und Material), alle Sitzungen.

## Einstellungen

Die Einstellungen sind in sieben Bereiche geordnet, jeder mit einer Zeile, was gerade eingestellt ist: **KI und Schlüssel** (Firma, Qualität, Schlüssel, System prüfen, wer was macht, Modelle), **Prüfung und Üben** (Termin, Übungszeit, Rückmeldung, Kursthemen), **Prüfer und Stimmen** (Gesprächsstil, Härte, Knopf halten, Stimmen), **Aufnahme, Anzeige, Erinnerung**, **Aussehen und Töne**, **Daten und Sicherung** sowie **Hilfe und Über AbiCoach** (Tour, Einrichtung, Version). Beim Einrichten einer Übung sind die Prüfer-Optionen und die KI-Übersicht zusammengeklappt – mit einer Zusammenfassung, was gilt.

## Update direkt in der App

Unter Einstellungen → **Update, Hilfe, Über** sucht „Nach Update suchen“ auf GitHub nach dem neuesten Build. Ist einer da, lädt „Jetzt aktualisieren“ die APK mit Fortschrittsbalken herunter, prüft, dass es wirklich eine neuere AbiCoach-Version ist, und übergibt sie direkt an den Android-Installer – ohne Browser und ohne Datei suchen. Die App fragt außerdem höchstens alle 12 Stunden von selbst nach und zeigt auf „Heute“ einen Hinweis, wenn es ein Update gibt.

- Weil das Projekt auf GitHub privat ist, braucht die App einmal einen **GitHub-Schlüssel nur zum Lesen** (Fine-grained Token: nur das Repository „abi-coach“, Berechtigung „Contents: Read-only“). Er bleibt verschlüsselt auf dem Handy und kommt nie in eine Sicherung.
- Beim ersten Update fragt Android einmal, ob AbiCoach Apps installieren darf („Aus dieser Quelle zulassen“). Je nach Android-Version bestätigst du die Installation noch mit „Aktualisieren“; AbiCoach schließt sich dabei kurz und meldet sich mit einer Benachrichtigung, sobald die neue Version da ist. Der Lernstand bleibt erhalten.

## Welche KI was macht

Unter Einstellungen → KI wählst du **eine Firma, die alles macht**: **Google**, **OpenAI** oder **Anthropic** – zum Vergleichen kannst du jederzeit wechseln. Jede Firma braucht ihren eigenen Schlüssel.

| Aufgabe | Google | OpenAI | Anthropic |
|---|---|---|---|
| Prüfer im Gespräch | **Gemini 3.8 Live** (hört und spricht gleichzeitig) | **GPT-Live-1** (hört und spricht gleichzeitig) | **Claude Haiku 5.5** mit Handy-Stimme (antwortet, wenn du fertig bist) |
| Mitschrift | **Gemini 3.5 Transcribe Live** (wortgetreu) | **GPT-Live-Transcribe** | Spracherkennung des Handys (Android 13+) |
| Bewertung, Aufgaben, Coach, Dossiers | **Gemini 4 Argon** → Gemini 3.8 Flash | **GPT 6.1 Sol** → GPT 6.1 Terra | **Claude Opus 5.5** → Claude Sonnet 5.5 |
| Ohr, Sag-es-richtig, Sprech-Training | **Gemini 3.8 Flash** (hört selbst) | **GPT Audio 1.5** (hört selbst) | Handy-Spracherkennung + **Claude Haiku 5.5** |
| Fehlerprüfung der Mitschrift | **Gemini 3.8 Flash** | **GPT 6 Luna** | **Claude Haiku 5.5** |
| Material lesen | **Gemini 3.8 Flash** | **GPT 6.1 Terra** | **Claude Sonnet 5.5** |
| Suche im Material | **Gemini Embedding 2** | **text-embedding-3-large** | Stichwortsuche (Claude hat keine Vektoren) |
| Aufnahme anhören | **Gemini 4 Argon** | **GPT Audio 1.5** (Ausschnitte) | – (Claude hört nicht) |
| Vorlesen und Coach-Stimme | **Gemini 3.8 Flash TTS** | **GPT-4o mini TTS** | Stimme des Handys |

Schlüssel gibt es unter aistudio.google.com/apikey (Google), platform.openai.com/api-keys (OpenAI) und platform.claude.com/settings/keys (Anthropic). **Beste Qualität** nutzt das stärkste Modell der Firma, **Sparsam** das günstigere. Wechselst du zwischen Google und OpenAI, bekommt dein Kursmaterial im Hintergrund neue Suchvektoren (bis dahin sucht die App mit Stichwörtern). Mit **System prüfen → Alles testen** prüft die App Schlüssel, Modelle, Stimme, Mitschrift, Prüfer und Coach der gewählten Firma einmal durch.

## Kosten und Datenschutz

- Es zahlt nur die gewählte Firma: das Live-Gespräch mit dem Prüfer ist der größte Posten (je nach Tarif einige Cent pro Gesprächsminute), eine Auswertung kostet wenige Cent, ein ganzes Schulbuch einlesen unter 1 $. Mit **Sparsam** wird es noch günstiger. Mit Claude fällt der Live-Ton weg (das Handy hört und spricht).
- Sitzungen, Lernprofil und Material liegen nur auf dem Handy; die Schlüssel liegen verschlüsselt im Android Keystore. Zum Lesen und Bewerten gehen Seiten, Mitschrift und (bei Google und OpenAI) die Aufnahme an die gewählte Firma. Im kostenlosen Gemini-Tarif darf Google die Daten zur Verbesserung nutzen.
- **Lernstand sichern und laden:** Unter Einstellungen → Daten speicherst du deinen ganzen Lernstand als eine Datei (ZIP): alle Sitzungen mit Auswertungen, Lernprofil, Fehlergedächtnis mit Tutor-Notizen, Lerner-Gedächtnis, Abi-Weg, Wortschatz, Aussprache, Dossiers und Einstellungen – auf Wunsch auch das Kursmaterial. Die **Schlüssel kommen nie mit in die Datei.** Auf einem anderen Handy lädst du die Datei unter „Lernstand laden“; die App startet danach neu.

## Aufbau

- `ai/` – Google (`GeminiRest`, `GeminiLive`), OpenAI (`OpenAiRest`, `OpenAiLive`), Anthropic (`ClaudeRest`) und die Handy-Sprache (`PhoneSpeech`); `AiHub` verteilt alle Aufgaben auf die gewählte Firma, springt bei Ausfall auf ihr Ausweichmodell, streamt und speichert die Stimme; `audio/SpeechClean` schneidet Rauschen nach dem letzten Wort ab
- `memory/` – Import, Lesen im Hintergrund (WorkManager), Abschnitte, Vektoren, Auswertung, hybride Suche, Kurs-Kontext für alle Prompts
- `profile/` – Lernprofil nach dem NRW-Raster, Prognose, Hebel
- `path/` – Abi-Weg: Welten und Levels (`Curriculum`), Freischalten, Sterne und Wiederholungsplan (`PathEngine`), Erinnerung
- `practice/` – Wortschatz-Karten mit Wiederholungsplan, Vortrags-Kennzahlen, Aussprache-Labor, „Sag es richtig“
- `learn/` – Lern-Gehirn (`Brain.kt`: Tagesplan nach erwartetem Punktgewinn, Phasen, Ziele, angepasstes Material, Hochrechnung), Lerner-Gedächtnis (`Insights.kt`), Fehlergedächtnis (Fehlermuster mit Zustand und Wiederholplan), Sprech-Training und Fehler-Werkstatt mit Tutor-Gedächtnis (`Workshop.kt`)
- `memory/` zusätzlich: Themen-Dossiers (`Dossier.kt`), echte Prüfungsaufgaben (`ExamUnits.kt`) und Halbjahre (`HalfYears.kt`)
- `data/Backup.kt` – Lernstand als ZIP sichern und laden (ohne Schlüssel)
- `session/` – Prompts, Live-Gespräch (mit Mikrofon-Dienst), Live-Fehlerprüfung, Material im Hintergrund, gespeicherte Vorbereitung und Alarm, Auswertung und Fortschritt, Systemprüfung
- `ui/` – Oberfläche im Stil von Mental Math Plus (Farbthemen, Karten, Sterne, Töne)

Jeder Push auf `main` baut automatisch eine neue APK. Mit `[smoke]` in der Commit-Nachricht läuft zusätzlich ein Test auf einem virtuellen Handy; die Bildschirmfotos liegen danach auf dem Branch `ci-smoke`.

## Lizenz

GNU GPL v3 (siehe `LICENSE.md`). Design und Bauteile stammen aus Mental Math Plus (tro123N), das auf [Mental Math](https://codeberg.org/Mental-Math/MentalMath) von HeldDerTierwelt aufbaut.
