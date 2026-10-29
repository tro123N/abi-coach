package de.abicoach.path

import de.abicoach.R
import de.abicoach.data.Knowledge
import de.abicoach.profile.Skill

/**
 * The Abi-Weg: worlds and levels from the exam format to the dress rehearsal.
 * The explanations are the app's own texts (German, with English examples).
 */
object Curriculum {
    private val start =
        World(
            id = "w1",
            number = 1,
            title = "Startklar",
            subtitle = "Ablauf, Bewertung, Vorbereitung",
            icon = R.drawable.flag_24dp,
            levels =
                listOf(
                    Level(
                        id = "w1.ablauf",
                        title = "So läuft die Prüfung ab",
                        method = "Format und Bewertung",
                        type = LevelType.LEARN,
                        learn =
                            """
## Der Ablauf
- **30 Minuten Vorbereitung** mit neuem Material: ein Text (meist 200–300 Wörter) und drei Aufgaben. Wörterbuch nach Regel deiner Schule, Notizen auf Papier.
- **Teil 1 – Vortrag (ca. 10 Minuten):** Du bearbeitest die drei Aufgaben in einem zusammenhängenden, freien Vortrag. Der Prüfer hört zu und hilft nur, wenn du festhängst.
- **Teil 2 – Prüfungsgespräch (ca. 10 Minuten):** Ein Gespräch über größere Zusammenhänge, meist mit einem Themenwechsel in ein anderes Halbjahr.

## So wird bewertet
- **Inhalt 40 %:** Aufgabenbezug, Textverständnis, Tiefe der Analyse, Begründung, Sachwissen.
- **Darstellungsleistung 60 %:** freies Sprechen, Aufbau, Gesprächsführung, Aussprache, Wortschatz, Grammatik.
- Für **13–15 Punkte** braucht es Leistungen im **Anforderungsbereich III**: eine eigenständige, begründete Stellungnahme und Verknüpfungen über den Text hinaus.

> Weil die Sprache 60 % zählt, bringt flüssiges, freies Sprechen oft mehr Punkte als ein zusätzliches Detail.
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w1.aufgaben",
                        title = "Die drei Aufgaben",
                        method = "summarise · analyse · comment",
                        type = LevelType.LEARN,
                        learn =
                            """
## Aufgabe 1 – zusammenfassen (AFB I)
Typisch: *Summarise the text.* · *Outline the author's view on …* · *Point out …*
Erwartet: die Kernaussagen knapp, in eigenen Worten, im **simple present**, ohne Zitate und ohne Meinung. Etwa 2 Minuten.

## Aufgabe 2 – analysieren (AFB II)
Typisch: *Examine how the author perceives …* · *Analyse the means the author uses to …* · *Analyse how the main character is presented.*
Erwartet: Behauptung → Beleg aus dem Text (mit Zeile) → Wirkung. Immer auf die Frage bezogen. Etwa 3–4 Minuten.

## Aufgabe 3 – kommentieren (AFB III)
Typisch: *Comment on …* · *Discuss whether …* · *Assess …*
Erwartet: eine klare Position, Argumente mit Beispielen – am besten aus dem Unterricht –, ein Gegenargument und ein Fazit. Etwa 3 Minuten.

> Lies den **Operator** genau: „outline the author's view“ ist keine Inhaltsangabe des ganzen Textes, sondern nur seine Sicht.
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w1.vorbereitung",
                        title = "30 Minuten richtig nutzen",
                        method = "Zeitplan und Notizen",
                        type = LevelType.LEARN,
                        learn =
                            """
## Ein bewährter Zeitplan
1. **5 Minuten:** Aufgaben lesen, dann den Text mit den Aufgaben im Kopf lesen. Markiere Stellen für Aufgabe 2.
2. **15 Minuten:** Notizen je Aufgabe – nur Stichpunkte und Zeilenangaben.
3. **5 Minuten:** Einstieg und Überleitungen notieren, Beispiele aus dem Unterricht für Aufgabe 3 sammeln.
4. **5 Minuten:** Puffer: einmal leise „durchsprechen“.

## So sehen gute Notizen aus
- **A1:** Lagos – young creatives – boom (Nollywood, music) – but: jobs, *japa*
- **A2:** positive: „Nobody is waiting…“ (l. 3) → active · contrast problems ↔ ideas (l. 15)
- **A3:** agree partly · Adichie: single story · limits: only few profit · conclusion

> Ganze Sätze auf dem Zettel verleiten zum Ablesen – und Ablesen kostet Punkte in der Darstellung.
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w1.frei",
                        title = "Frei sprechen",
                        method = "3 Minuten ohne Zettel",
                        type = LevelType.TALK,
                        skill = Skill.FLUESSIGKEIT,
                        minutes = 4,
                        goal = 9,
                        instruction =
                            "This is a short warm-up of about three minutes. Ask easy, open questions about the student's course topics and let the student talk freely. " +
                                "Encourage longer answers with follow-up questions such as \"Why do you think so?\" or \"Can you give an example?\".",
                        learn =
                            """
Hier geht es nur ums **Sprechen ohne Pause und ohne Zettel**. Fehler sind erlaubt – die App achtet vor allem auf Flüssigkeit.

**Tricks, um Zeit zu gewinnen:**
- *That's an interesting question.*
- *Let me think about that for a second.*
- *What I mean is …*

Wenn dir ein Wort fehlt, beschreibe es: *the people who make the laws* statt „Gesetzgeber“.
                            """.trimIndent(),
                        example = "That's an interesting question. Let me think about that for a second. I would say that social media has changed the way young people get their news.",
                    ),
                ),
        )

    private val summary =
        World(
            id = "w2",
            number = 2,
            title = "Aufgabe 1: Zusammenfassen",
            subtitle = "summarise · outline · point out",
            icon = R.drawable.summarize_24dp,
            levels =
                listOf(
                    Level(
                        id = "w2.einleitung",
                        title = "Der Einleitungssatz",
                        method = "Titel, Autor, Textart, Thema",
                        type = LevelType.TASK,
                        skill = Skill.ZUSAMMENFASSEN,
                        focusTask = 1,
                        minutes = 6,
                        goal = 9,
                        instruction = "Task 1 must be \"Summarise the text.\" The student practises mainly the introductory sentence (title, author, text type, date, topic) and the main idea; a good answer takes 60 to 90 seconds.",
                        learn =
                            """
Der erste Satz zeigt sofort, ob du den Text verstanden hast. Er enthält **Textart, Titel, Autor, Quelle/Datum und das Thema**:

> The newspaper article "Lagos Rising" by Ada Okafor, published in The Guardian in 2025, deals with young creative people in Lagos who build careers in the creative industries.

Danach folgt die **Hauptaussage** in einem Satz: *The author's main point is that …*

**Vermeide:** *In the text it is about …* (deutsche Satzstellung) und *The text is talking about …*
                            """.trimIndent(),
                        example = "The newspaper article \"Lagos Rising\", published in 2025, deals with young creative people in Lagos. The author's main point is that they build their own opportunities.",
                    ),
                    Level(
                        id = "w2.kern",
                        title = "Kernaussagen statt Details",
                        method = "Das Wichtigste in 2 Minuten",
                        type = LevelType.TASK,
                        skill = Skill.ZUSAMMENFASSEN,
                        focusTask = 1,
                        minutes = 8,
                        goal = 10,
                        instruction = "Task 1 must be \"Summarise the text.\" The text should contain several examples and details, so that the student has to select the main points.",
                        learn =
                            """
Eine Zusammenfassung ist **kürzer als der Text** und zeigt, was wirklich zählt:
- **Keine Beispiele, Zahlen und Zitate**, außer sie sind die Hauptaussage.
- **Simple present** und **reported speech**: *The author claims that …*
- **Eigene Worte**: Formuliere um, statt Sätze abzuschreiben.
- **Logische Verbindungen** zeigen: *first … then … however … finally …*

Faustregel: Ein Absatz des Textes → ein Satz von dir.
                            """.trimIndent(),
                        example = "First, the author describes the creative boom in Lagos. She then points out that many young people still struggle to find stable jobs. Finally, she concludes that telling their own stories gives them hope.",
                    ),
                    Level(
                        id = "w2.outline",
                        title = "Outline the author's view",
                        method = "Nur die Sicht des Autors",
                        type = LevelType.TASK,
                        skill = Skill.ZUSAMMENFASSEN,
                        focusTask = 1,
                        minutes = 8,
                        goal = 10,
                        instruction = "Task 1 must be \"Outline the author's view on …\" (choose a central issue of the text). The text should be an opinion piece with a clear position.",
                        learn =
                            """
Bei **„Outline the author's view on …“** fasst du nicht den ganzen Text zusammen, sondern nur, **was der Autor zu dieser Frage denkt** – und warum.

**Gute Verben:** *argue, claim, criticise, believe, suggest, warn, point out*

> The author argues that social media gives young people a voice. However, she warns that algorithms often reward outrage rather than arguments.

Nenne seine **Hauptthese**, die **Begründungen** und gegebenenfalls **Einschränkungen**.
                            """.trimIndent(),
                        example = "The author argues that social media gives young people a voice. However, she warns that algorithms often reward outrage rather than arguments.",
                    ),
                    Level(
                        id = "w2.literarisch",
                        title = "Literarische Texte",
                        method = "Figuren, Handlung, Konflikt",
                        type = LevelType.TASK,
                        skill = Skill.ZUSAMMENFASSEN,
                        focusTask = 1,
                        minutes = 8,
                        goal = 10,
                        literary = true,
                        instruction = "Use a literary text (an excerpt from a novel or short story). Task 1 must be \"Summarise the excerpt.\" or \"Point out the problems the main character faces.\"",
                        learn =
                            """
Bei einem Romanauszug oder einer Kurzgeschichte fasst du **Situation, Figuren, Handlung und Konflikt** zusammen:

> The excerpt from the novel "Americanah" by Chimamanda Ngozi Adichie is about Ifemelu, a young Nigerian woman who has just arrived in the USA. She feels lost and realises that she is seen differently because of her skin colour.

- Auch hier **simple present**.
- **Keine Interpretation** in Aufgabe 1 – die gehört in Aufgabe 2.
                            """.trimIndent(),
                        example = "The excerpt is about Ifemelu, a young Nigerian woman who has just arrived in the USA. She feels lost and realises that she is seen differently because of her skin colour.",
                    ),
                    Level(
                        id = "w2.pruefung",
                        title = "Zusammenfassen wie im Abi",
                        method = "Ohne Hilfen, mit Bewertung",
                        type = LevelType.TASK,
                        skill = Skill.ZUSAMMENFASSEN,
                        focusTask = 1,
                        minutes = 10,
                        goal = 11,
                        strict = true,
                        learn = "Jetzt unter Prüfungsbedingungen: keine Korrekturen beim Sprechen, die Bewertung kommt am Ende. Ziel: 11 Punkte in der Zusammenfassung.",
                    ),
                ),
        )

    private val analysis =
        World(
            id = "w3",
            number = 3,
            title = "Aufgabe 2: Analysieren",
            subtitle = "examine · analyse · explain",
            icon = R.drawable.insights_24dp,
            levels =
                listOf(
                    Level(
                        id = "w3.haltung",
                        title = "Haltung und Ton",
                        method = "attitude · tone",
                        type = LevelType.TASK,
                        skill = Skill.ANALYSE,
                        focusTask = 2,
                        minutes = 8,
                        goal = 9,
                        instruction = "Task 2 must be \"Explain the author's attitude towards …\". The text should show a clear, but not too obvious attitude (e.g. ironic, critical, sympathetic).",
                        learn =
                            """
Die **Haltung** (attitude) ist, wie der Autor zu einem Thema steht; der **Ton** (tone) ist, wie das klingt.

**Wörter für die Haltung:** *critical, sceptical, supportive, sympathetic, ambivalent, enthusiastic, dismissive*
**Wörter für den Ton:** *ironic, sarcastic, objective, emotional, humorous, alarming, nostalgic*

> The author's attitude towards the government is highly critical, which becomes obvious in the ironic remark that politicians "promise much and deliver little" (l. 6).

Immer: **Haltung benennen → belegen → erklären, woran man es merkt.**
                            """.trimIndent(),
                        example = "The author's attitude towards the government is highly critical, which becomes obvious in the ironic remark that politicians promise much and deliver little.",
                    ),
                    Level(
                        id = "w3.perceive",
                        title = "Examine how the author perceives …",
                        method = "Die typische NRW-Aufgabe",
                        type = LevelType.TASK,
                        skill = Skill.ANALYSE,
                        focusTask = 2,
                        minutes = 10,
                        goal = 10,
                        instruction = "Task 2 must be \"Examine how the author perceives …\" (a person, a group, a place or a development in the text).",
                        learn =
                            """
**„Examine how the author perceives X“** heißt: Wie sieht der Autor X – und **woran erkennt man das im Text?**

1. **These:** *The author perceives young Nigerians as resourceful and optimistic.*
2. **Belege:** Wortwahl, Zitate von Personen, Vergleiche, Kontraste, Aufbau.
3. **Wirkung:** *By quoting Chioma directly, the author gives young people a voice of their own.*
4. **Differenzieren:** *However, he does not ignore the problems: …*

**Achtung:** Nicht nacherzählen! Jeder Satz muss zur Frage „wie sieht er X?“ passen.
                            """.trimIndent(),
                        example = "The author perceives young Nigerians as resourceful and optimistic. By quoting Chioma directly, he gives them a voice of their own. However, he does not ignore the problems they face.",
                    ),
                    Level(
                        id = "w3.mittel",
                        title = "Sprachliche Mittel",
                        method = "Mittel → Wirkung",
                        type = LevelType.TASK,
                        skill = Skill.ANALYSE,
                        focusTask = 2,
                        minutes = 10,
                        goal = 10,
                        instruction = "Task 2 must be \"Analyse the means the author uses to convince the reader.\" Use a persuasive text (commentary or speech) with rhetorical questions, contrasts, enumerations, emotive language and direct address.",
                        learn =
                            """
Ein Stilmittel zu **benennen** reicht nicht – entscheidend ist die **Wirkung**.

| Mittel | typische Wirkung |
|---|---|
| rhetorical question | makes the reader think, involves him |
| contrast / antithesis | emphasises a difference |
| enumeration | shows how many / how serious |
| emotive language | appeals to feelings |
| direct address (you, we) | creates closeness, involves the audience |
| irony | criticises indirectly |

> The rhetorical question "Who would not want a future like this?" (l. 12) involves the reader and makes disagreement seem unreasonable.
                            """.trimIndent(),
                        example = "The rhetorical question in line twelve involves the reader and makes disagreement seem unreasonable.",
                    ),
                    Level(
                        id = "w3.belege",
                        title = "Belegen mit Zitaten",
                        method = "Behauptung – Zitat – Wirkung",
                        type = LevelType.TASK,
                        skill = Skill.ANALYSE,
                        focusTask = 2,
                        minutes = 10,
                        goal = 10,
                        instruction = "Task 2 must be an analysis task (examine or analyse). Use a text with many quotable phrases.",
                        learn =
                            """
Ohne Beleg ist eine Analyse nur eine Behauptung. Nutze das Dreierpaket:

1. **Behauptung:** *The author presents the city as chaotic but creative.*
2. **Beleg:** *He calls Lagos "loud, chaotic, but full of ideas" (l. 14).*
3. **Wirkung:** *The enumeration ends on a positive note, so the reader remembers the creativity, not the chaos.*

**Zitieren im Vortrag:** *As the author puts it, …* · *In line 14 he writes that …* · *The phrase "…" shows …*
Kurze Zitate (2–6 Wörter) wirken besser als lange.
                            """.trimIndent(),
                        example = "The author presents the city as chaotic but creative. As he puts it, Lagos is loud, chaotic, but full of ideas. Because the enumeration ends on a positive note, the reader remembers the creativity.",
                    ),
                    Level(
                        id = "w3.figuren",
                        title = "Figuren und Erzählperspektive",
                        method = "characterisation · point of view",
                        type = LevelType.TASK,
                        skill = Skill.ANALYSE,
                        focusTask = 2,
                        minutes = 10,
                        goal = 10,
                        literary = true,
                        instruction = "Use a literary text. Task 2 must be \"Analyse how the main character is presented.\" or \"Examine the narrative perspective and its effect.\"",
                        learn =
                            """
**Charakterisierung:** direkt (der Erzähler sagt, wie jemand ist) oder indirekt (Handlungen, Sprache, Gedanken, Reaktionen anderer).

**Erzählperspektive:**
- *first-person narrator* → nah, subjektiv, begrenztes Wissen
- *third-person limited* → wir sehen durch die Augen einer Figur
- *omniscient narrator* → weiß alles, kommentiert

> The first-person narrator's limited perspective makes the reader share her confusion, which creates sympathy for her.
                            """.trimIndent(),
                        example = "The first-person narrator's limited perspective makes the reader share her confusion, which creates sympathy for her.",
                    ),
                    Level(
                        id = "w3.pruefung",
                        title = "Analysieren wie im Abi",
                        method = "Ohne Hilfen, mit Bewertung",
                        type = LevelType.TASK,
                        skill = Skill.ANALYSE,
                        focusTask = 2,
                        minutes = 12,
                        goal = 11,
                        strict = true,
                        learn = "Jetzt unter Prüfungsbedingungen. Ziel: 11 Punkte in der Analyse – mit These, Belegen und Wirkung zu jeder Aussage.",
                    ),
                ),
        )

    private val comment =
        World(
            id = "w4",
            number = 4,
            title = "Aufgabe 3: Kommentieren",
            subtitle = "comment · discuss · assess",
            icon = R.drawable.rate_review_24dp,
            levels =
                listOf(
                    Level(
                        id = "w4.position",
                        title = "Eine klare Position",
                        method = "These und Begründung",
                        type = LevelType.TASK,
                        skill = Skill.KOMMENTAR,
                        focusTask = 3,
                        minutes = 8,
                        goal = 9,
                        instruction = "Task 3 must be \"Comment on …\" with a statement from the text that one can agree or disagree with.",
                        learn =
                            """
Ein Kommentar beginnt mit einer **klaren These** – nicht mit „there are pros and cons“.

> I partly agree with the producer's claim that young Nigerians "can tell their own stories", because …

Danach **2–3 Argumente**, jedes mit **Begründung und Beispiel**. Für gut und sehr gut zählt die **Eigenständigkeit**: eigene Gedanken, nicht nur Wiederholung des Textes.
                            """.trimIndent(),
                        example = "I partly agree with the producer's claim, because telling their own stories gives young people confidence. However, stories alone cannot create jobs.",
                    ),
                    Level(
                        id = "w4.beispiele",
                        title = "Beispiele aus dem Unterricht",
                        method = "Kurswissen nutzen",
                        type = LevelType.TASK,
                        skill = Skill.SACHWISSEN,
                        focusTask = 3,
                        minutes = 8,
                        goal = 10,
                        instruction = "Task 3 must be \"Comment on …, taking into account what you have learnt in class.\" Choose a statement that links well to the student's course material.",
                        learn =
                            """
Nichts überzeugt Prüfer mehr als ein **passendes Beispiel aus dem Unterricht** – es zeigt Sachwissen und Verknüpfung (AFB III).

**So baust du es ein:**
- *This reminds me of Chimamanda Ngozi Adichie's talk "The danger of a single story", which we discussed in class.*
- *A good example of this is …, which we read in our course.*
- *This is also true for the UK, as we saw when we dealt with the Windrush generation.*

Tipp: Notiere dir in der Vorbereitung **zwei Beispiele aus dem Kurs**, die zu Aufgabe 3 passen.
                            """.trimIndent(),
                        example = "This reminds me of Chimamanda Ngozi Adichie's talk The danger of a single story, which we discussed in class.",
                    ),
                    Level(
                        id = "w4.gegen",
                        title = "Gegenargumente",
                        method = "Concession und Widerlegung",
                        type = LevelType.TASK,
                        skill = Skill.KOMMENTAR,
                        focusTask = 3,
                        minutes = 8,
                        goal = 10,
                        instruction = "Task 3 must be \"Discuss whether …\" with a controversial question raised by the text.",
                        learn =
                            """
Ein starkes Argument wird stärker, wenn du die **Gegenseite ernst nimmst**:

1. **Zugeständnis:** *Admittedly, cultural success alone does not create jobs.*
2. **Widerlegung:** *However, the creative industries are among the fastest-growing sectors and employ many young people.*

Weitere Wendungen: *Critics might object that … – but …* · *It is true that … Nevertheless, …*
                            """.trimIndent(),
                        example = "Admittedly, cultural success alone does not create jobs. Nevertheless, the creative industries are among the fastest-growing sectors and employ many young people.",
                    ),
                    Level(
                        id = "w4.fazit",
                        title = "Ein starkes Fazit",
                        method = "Abrunden statt aufhören",
                        type = LevelType.TASK,
                        skill = Skill.KOMMENTAR,
                        focusTask = 3,
                        minutes = 8,
                        goal = 10,
                        instruction = "Task 3 must be \"Comment on …\". The student should practise ending the comment with a clear conclusion.",
                        learn =
                            """
Viele Vorträge hören einfach auf. Ein **Fazit** zeigt Struktur und lässt einen guten letzten Eindruck:

> Taking everything into account, I believe that telling their own stories is an important first step for young Nigerians – but it has to be followed by political change.

**Bausteine:** *All in all, …* · *Taking everything into account, …* · *To sum up, I would say that …* · *In the end, it depends on …*
                            """.trimIndent(),
                        example = "Taking everything into account, I believe that telling their own stories is an important first step, but it has to be followed by political change.",
                    ),
                    Level(
                        id = "w4.pruefung",
                        title = "Kommentieren wie im Abi",
                        method = "Ohne Hilfen, mit Bewertung",
                        type = LevelType.TASK,
                        skill = Skill.KOMMENTAR,
                        focusTask = 3,
                        minutes = 10,
                        goal = 11,
                        strict = true,
                        learn = "Jetzt unter Prüfungsbedingungen. Ziel: 11 Punkte in der Stellungnahme – Position, Argumente mit Beispielen aus dem Unterricht, Gegenargument, Fazit.",
                    ),
                ),
        )

    private val presentation =
        World(
            id = "w5",
            number = 5,
            title = "Der Vortrag",
            subtitle = "Aufbau, Zeit, frei sprechen",
            icon = R.drawable.co_present_24dp,
            levels =
                listOf(
                    Level(
                        id = "w5.struktur",
                        title = "Aufbau und Überleitungen",
                        method = "Signposting",
                        type = LevelType.PRESENTATION,
                        skill = Skill.STRUKTUR,
                        minutes = 15,
                        goal = 10,
                        instruction = "A normal part-1 text with three tasks. The student practises clear transitions between the tasks.",
                        learn =
                            """
Der Prüfer soll immer wissen, **wo du gerade bist**. Das nennt man *signposting* und zählt zur Präsentationskompetenz.

- **Start:** *I'd like to start with a short summary of the text.*
- **1 → 2:** *Having summarised the text, I would now like to turn to the analysis.*
- **2 → 3:** *This brings me to my last task, the comment.*
- **Ende:** *That's all I wanted to say about the text. Thank you.*
                            """.trimIndent(),
                        example = "Having summarised the text, I would now like to turn to the analysis of how the author presents young Nigerians.",
                    ),
                    Level(
                        id = "w5.zeit",
                        title = "10 Minuten einteilen",
                        method = "2 – 4 – 3 Minuten",
                        type = LevelType.PRESENTATION,
                        skill = Skill.AUFGABENBEZUG,
                        minutes = 15,
                        goal = 10,
                        learn =
                            """
Eine gute Verteilung für den Vortrag:
- **Aufgabe 1:** ca. 2 Minuten
- **Aufgabe 2:** ca. 4 Minuten (hier liegen die meisten Punkte im Inhalt)
- **Aufgabe 3:** ca. 3 Minuten
- **Einstieg und Schluss:** zusammen 1 Minute

Die App zeigt dir beim Sprechen die Zeit. Wenn du nach 6 Minuten noch bei Aufgabe 1 bist, kürze!
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w5.notizen",
                        title = "Mit Notizen frei sprechen",
                        method = "Stichpunkte statt Sätze",
                        type = LevelType.PRESENTATION,
                        skill = Skill.FLUESSIGKEIT,
                        minutes = 15,
                        goal = 10,
                        learn =
                            """
Ein Blick auf den Zettel, dann **zum Prüfer sprechen**. So geht es:
- Pro Aufgabe **3–5 Stichpunkte**, keine Sätze.
- **Zeilenangaben** für Zitate notieren, nicht die Zitate selbst.
- Pausen sind erlaubt: lieber kurz schweigen als ablesen.

Die App erkennt beim Anhören der Aufnahme, ob du abliest.
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w5.pruefung",
                        title = "Der ganze Vortrag",
                        method = "Wie im Abi",
                        type = LevelType.PRESENTATION,
                        minutes = 30,
                        goal = 11,
                        strict = true,
                        learn = "Der komplette erste Prüfungsteil unter Prüfungsbedingungen: 20 Minuten Vorbereitung, 10 Minuten Vortrag. Ziel: 11 Punkte.",
                    ),
                ),
        )

    private val conversation =
        World(
            id = "w6",
            number = 6,
            title = "Das Prüfungsgespräch",
            subtitle = "eingehen, Impulse setzen, verknüpfen",
            icon = R.drawable.forum_24dp,
            levels =
                listOf(
                    Level(
                        id = "w6.eingehen",
                        title = "Auf Fragen eingehen",
                        method = "Antworten mit Begründung",
                        type = LevelType.TALK,
                        skill = Skill.INTERAKTION,
                        minutes = 7,
                        goal = 9,
                        instruction = "Ask open questions and always follow up on the student's answer (why, example, consequence). Keep your own turns very short.",
                        learn =
                            """
Eine gute Antwort im Prüfungsgespräch hat **drei Teile**: Antwort – Begründung – Beispiel.

> Q: Do you think social media is good for democracy?
> A: **Partly.** It gives people a voice, **because** anyone can share their opinion. **For example**, the Black Lives Matter movement became global through social media. But …

Zu kurze Antworten („Yes, I think so.“) kosten Punkte in der Gesprächsführung.
                            """.trimIndent(),
                        example = "Partly. It gives people a voice, because anyone can share their opinion. For example, the Black Lives Matter movement became global through social media.",
                    ),
                    Level(
                        id = "w6.impulse",
                        title = "Selbst Impulse setzen",
                        method = "Das Gespräch mitgestalten",
                        type = LevelType.TALK,
                        skill = Skill.INTERAKTION,
                        minutes = 8,
                        goal = 10,
                        instruction = "Leave room for the student's initiative: sometimes ask very open questions and react positively when the student brings in a new aspect or example.",
                        learn =
                            """
Für gute Punkte solltest du das Gespräch **mitgestalten**, nicht nur antworten:
- *If I may add one more aspect: …*
- *This reminds me of …, which we dealt with in class.*
- *I think it's also important to look at …*
- *What I find really interesting is …*

So zeigst du Sachwissen und Interaktion zugleich.
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w6.verknuepfen",
                        title = "Themen verknüpfen",
                        method = "Über das Halbjahr hinaus",
                        type = LevelType.TALK,
                        skill = Skill.SACHWISSEN,
                        minutes = 8,
                        goal = 10,
                        instruction = "Move between at least three topic fields of the course and explicitly ask the student to connect them (e.g. \"How does this relate to what you learnt about the UK?\").",
                        learn =
                            """
Im zweiten Teil wird **über ein Halbjahr hinaus** gefragt. Typische Brücken:
- Nigeria ↔ UK: koloniales Erbe, Commonwealth, Migration
- USA ↔ Identität: American Dream, Rassismus, Diversity
- Medien ↔ Politik: Fake News, Social Media, Wahlen
- Globalisierung ↔ Arbeitsmarkt: Migration, Brain Drain

**Wendungen:** *This is closely connected to …* · *We can see a similar development in …* · *In contrast to …*
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w6.kompensation",
                        title = "Wenn dir ein Wort fehlt",
                        method = "Umschreiben und nachfragen",
                        type = LevelType.TALK,
                        skill = Skill.WORTSCHATZ,
                        minutes = 7,
                        goal = 9,
                        instruction = "Now and then use a more demanding word or idiom in your questions, so that the student has to ask for clarification or paraphrase.",
                        learn =
                            """
**Kompensationsstrategien** werden ausdrücklich bewertet – nutze sie!
- **Umschreiben:** *the people who make the laws* (Gesetzgeber), *a kind of …*, *something like …*
- **Nachfragen:** *Could you rephrase the question, please?* · *Do you mean …?*
- **Zeit gewinnen:** *That's a difficult question. Let me think …*

Lieber umschreiben als ins Deutsche wechseln oder abbrechen.
                            """.trimIndent(),
                    ),
                    Level(
                        id = "w6.pruefung",
                        title = "Prüfungsgespräch wie im Abi",
                        method = "10 Minuten mit Themenwechsel",
                        type = LevelType.TALK,
                        minutes = 11,
                        goal = 11,
                        strict = true,
                        learn = "Zehn Minuten wie im Abi, mit einem Themenwechsel. Ziel: 11 Punkte.",
                    ),
                ),
        )

    private fun topicWorld(topicId: Int, number: Int): World {
        val t = Knowledge.TOPICS[topicId]
        return World(
            id = "t$topicId",
            number = number,
            title = t.short,
            subtitle = "Themenfeld",
            icon = R.drawable.hub_24dp,
            topicId = topicId,
            free = true,
            levels =
                listOf(
                    Level(
                        id = "t$topicId.wissen",
                        title = "Was du dazu weißt",
                        method = "Aus deinem Material",
                        type = LevelType.LEARN,
                        topicId = topicId,
                        learn = "**${t.german}**\n\nHier zeigt dir die App, was in deinem Material zu diesem Themenfeld steht: wichtige Inhalte und Wortschatz.",
                    ),
                    Level(
                        id = "t$topicId.gespraech",
                        title = "Gespräch: ${t.short}",
                        method = "Wie Teil 2",
                        type = LevelType.TALK,
                        skill = Skill.SACHWISSEN,
                        topicId = topicId,
                        minutes = 8,
                        goal = 10,
                        learn = "Ein Gespräch wie im zweiten Prüfungsteil zu **${t.german}**. Der Prüfer kennt euer Unterrichtsmaterial und fragt danach.",
                    ),
                    Level(
                        id = "t$topicId.vortrag",
                        title = "Vortrag: ${t.short}",
                        method = "Text mit drei Aufgaben",
                        type = LevelType.PRESENTATION,
                        topicId = topicId,
                        minutes = 25,
                        goal = 10,
                        learn = "Ein neuer Text zu **${t.german}**, passend zu deinem Kurs, mit den drei typischen Aufgaben.",
                    ),
                ),
        )
    }

    private val rehearsal =
        World(
            id = "g",
            number = 99,
            title = "Generalprobe",
            subtitle = "Drei Simulationen bis zur 15",
            icon = R.drawable.workspace_premium_24dp,
            levels =
                listOf(
                    Level("g.sim1", "Simulation 1", "Ziel: 11 Punkte", LevelType.SIMULATION, learn = "Eine komplette Prüfung: 30 Minuten Vorbereitung, Vortrag, Prüfungsgespräch.", minutes = 55, goal = 11, strict = true),
                    Level("g.sim2", "Simulation 2", "Ziel: 12 Punkte", LevelType.SIMULATION, learn = "Noch eine komplette Prüfung – ein anderes Themenfeld.", minutes = 55, goal = 12, strict = true),
                    Level("g.sim3", "Simulation 3", "Ziel: 13 Punkte", LevelType.SIMULATION, learn = "Die Generalprobe: 13 Punkte heißt „sehr gut“.", minutes = 55, goal = 13, strict = true),
                ),
        )

    /** The skill worlds in order, then one world per topic field (the ones with material first), then the dress rehearsal. */
    fun worlds(topicOrder: List<Int>): List<World> {
        val base = listOf(start, summary, analysis, comment, presentation, conversation)
        val order = (topicOrder + Knowledge.TOPICS.map { it.id }).distinct()
        val topics = order.mapIndexed { i, id -> topicWorld(id, base.size + 1 + i) }
        return base + topics + rehearsal.copy(number = base.size + topics.size + 1)
    }

    fun level(id: String): Level? = worlds(emptyList()).flatMap { it.levels }.firstOrNull { it.id == id }

    fun worldOf(levelId: String): World? = worlds(emptyList()).firstOrNull { w -> w.levels.any { it.id == levelId } }
}
