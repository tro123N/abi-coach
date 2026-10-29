package de.abicoach.data

/** One of the 14 topic fields of the Abiturvorgaben 2027 (Englisch, Grundkurs, NRW). */
data class TopicField(
    val id: Int,
    /** Short German name for chips and lists. */
    val short: String,
    /** Full German name as in the Abiturvorgaben. */
    val german: String,
    /** English name for the prompts. */
    val english: String,
)

/** Facts about the oral Abitur in English (NRW, 4th subject) that the prompts and screens rely on. */
object Knowledge {
    const val RANDOM = -1

    /** Use a topic field the course actually covered, weighted by the uploaded material. */
    const val FROM_COURSE = -2

    val TOPICS =
        listOf(
            TopicField(0, "UK – Wandel & Tradition", "UK: Politik, Kultur, Gesellschaft – zwischen Wandel und Tradition", "The UK: politics, culture and society – between change and tradition"),
            TopicField(1, "UK – Selbstbild & Empire", "UK: Nationale Selbstkonzepte – koloniales Erbe und europäische Verortung", "The UK: national self-concepts – colonial legacy and its place in Europe"),
            TopicField(2, "USA – Wandel & Tradition", "USA: Politik, Kultur, Gesellschaft – zwischen Wandel und Tradition", "The USA: politics, culture and society – between change and tradition"),
            TopicField(3, "USA – Ideale & Realität", "USA: Amerikanische Ideale und Realitäten – Freiheit, Gleichheit, Streben nach Glück", "The USA: American ideals and realities – freedom, equality, the pursuit of happiness"),
            TopicField(4, "Nigeria", "Nigeria: Politik, Kultur, Gesellschaft – zwischen Wandel und Tradition", "Nigeria: politics, culture and society – between change and tradition"),
            TopicField(5, "Identität", "Identität: Ambitionen und Hindernisse, Konformität vs. Individualismus", "Identity: ambitions and obstacles, conformity vs. individualism"),
            TopicField(6, "Vielfalt", "Vielfalt: ethnische, kulturelle, soziale, sexuelle und geschlechtliche Vielfalt", "Diversity: ethnic, cultural, social, sexual and gender diversity"),
            TopicField(7, "Journalismus", "Medien: Journalismus zwischen Information und Unterhaltung", "Media: journalism between information and entertainment"),
            TopicField(8, "Soziale Medien", "Medien: soziale Medien und digitale Plattformen – Teilhabe und Manipulation", "Media: social media and digital platforms – participation and manipulation"),
            TopicField(9, "Literaturformate", "Literatur: klassische und multimodale Literaturformate", "Literature: classic and multimodal literary formats"),
            TopicField(10, "Globalisierung & Migration", "Globalisierung: Nachhaltigkeit (sozial, ökologisch, wirtschaftlich) und Migration", "Globalisation: sustainability (social, ecological, economic) and migration"),
            TopicField(11, "Arbeitsmarkt", "Globaler Arbeitsmarkt: Chancengleichheit, Wettbewerb, Kooperation", "The global job market: equal opportunities, competition, cooperation"),
            TopicField(12, "Fortschritt", "Wissenschaftlicher und technischer Fortschritt: Chancen und Herausforderungen", "Scientific and technological progress: opportunities and challenges"),
            TopicField(13, "Utopie & Dystopie", "Zukunftsvisionen: utopische und dystopische Narrative", "Visions of the future: utopian and dystopian narratives"),
        )

    fun topic(id: Int): TopicField? = TOPICS.getOrNull(id)

    fun topicShort(id: Int): String = topic(id)?.short ?: "Gemischt"

    val HALBJAHRE = listOf("EF", "Q1.1", "Q1.2", "Q2.1", "Q2.2", "unbekannt")

    /** Text types for part 1. Pictures, cartoons and statistics do not occur in this exam. */
    enum class TextType(val label: String, val english: String) {
        SACHTEXT("Sachtext", "a non-fiction text such as a newspaper or online article, a commentary, a blog post, an essay or a speech"),
        LITERARISCH("Literarischer Text", "a literary text: an excerpt from a novel or short story (a scene with characters, conflict and some dialogue)"),
        GEMISCHT("Zufällig", ""),
    }

    /** Where the material for part 1 comes from. */
    enum class Source(val label: String, val description: String) {
        COURSE("Passend zu meinem Kurs", "Ein neuer Text, der genau zu euren Unterrichtsinhalten passt."),
        MATERIAL("Aus meinem Material", "Ein Text aus deinem Schulbuch oder Abi-Buch – mit neuen Aufgaben."),
        NEW("Ganz neu", "Ein neuer Text zum Themenfeld, unabhängig von deinem Material."),
    }

    /**
     * The three tasks of part 1 as they usually appear: 1 summarise/outline (AFB I),
     * 2 analyse/examine, e.g. "Examine how the author perceives …" (AFB II), 3 comment (AFB III).
     */
    data class TaskPattern(val number: Int, val afb: String, val germanName: String, val operators: List<String>, val examples: List<String>)

    val TASK_PATTERNS =
        listOf(
            TaskPattern(
                1,
                "I",
                "Zusammenfassen",
                listOf("summarise", "outline", "point out", "describe", "state"),
                listOf("Summarise the text.", "Outline the author's view on …", "Point out the problems the narrator faces."),
            ),
            TaskPattern(
                2,
                "II",
                "Analysieren",
                listOf("analyse", "examine", "explain", "characterise", "compare", "interpret"),
                listOf(
                    "Examine how the author perceives …",
                    "Analyse the means the author uses to convince the reader.",
                    "Analyse how the main character is presented.",
                    "Explain the author's attitude towards …",
                ),
            ),
            TaskPattern(
                3,
                "III",
                "Kommentieren",
                listOf("comment", "discuss", "assess", "evaluate", "justify"),
                listOf("Comment on the author's claim that …", "Discuss whether …", "Assess the author's view in the light of what you have learnt in class."),
            ),
        )

    /** NRW-style operators by Anforderungsbereich. */
    const val OPERATORS =
        "AFB I: summarise, outline, point out, describe, state. " +
            "AFB II: analyse, examine, explain, characterise, compare, interpret. " +
            "AFB III: comment, discuss, assess, evaluate, justify."

    /** Error categories, modelled on the ERRANT error types, with German labels for the app. */
    val ERROR_CATEGORIES =
        linkedMapOf(
            "VERB_TENSE" to "Zeitform",
            "VERB_FORM" to "Verbform",
            "SVA" to "Subjekt-Verb-Kongruenz",
            "PREP" to "Präposition",
            "DET" to "Artikel",
            "NOUN_NUM" to "Singular/Plural",
            "PRON" to "Pronomen",
            "WORD_ORDER" to "Wortstellung",
            "WORD_CHOICE" to "Wortwahl/Kollokation",
            "FALSE_FRIEND" to "Falscher Freund",
            "ADJ_ADV" to "Adjektiv/Adverb",
            "STRUCTURE" to "Satzbau",
            "REGISTER" to "Register/Stil",
            "CONTENT" to "Inhaltlicher Fehler",
            "OTHER" to "Sonstiges",
        )

    fun categoryLabel(key: String): String = ERROR_CATEGORIES[key] ?: key

    /** English names for the examiner prompt when the student practises weak points. */
    fun categoryEnglish(key: String): String =
        when (key) {
            "VERB_TENSE" -> "tenses (e.g. present perfect vs. simple past)"
            "VERB_FORM" -> "verb forms"
            "SVA" -> "subject-verb agreement (third person -s)"
            "PREP" -> "prepositions"
            "DET" -> "articles"
            "NOUN_NUM" -> "singular and plural"
            "PRON" -> "pronouns"
            "WORD_ORDER" -> "word order"
            "WORD_CHOICE" -> "word choice and collocations"
            "FALSE_FRIEND" -> "German-English false friends"
            "ADJ_ADV" -> "adjectives and adverbs"
            "STRUCTURE" -> "complex sentence structures"
            "REGISTER" -> "formal register"
            else -> "accuracy in general"
        }

    /** Proper nouns and terms that help the speech recogniser. */
    val DEFAULT_VOCABULARY =
        listOf(
            "Nigeria", "Lagos", "Abuja", "Igbo", "Yoruba", "Hausa", "Nollywood", "Chimamanda Ngozi Adichie",
            "Brexit", "Commonwealth", "Westminster", "Windrush", "NHS", "Scotland", "Northern Ireland",
            "American Dream", "Declaration of Independence", "pursuit of happiness", "Civil Rights Movement",
            "Black Lives Matter", "Supreme Court", "Congress", "melting pot", "salad bowl",
            "globalisation", "sustainability", "migration", "dystopia", "utopia", "Shakespeare",
            "social media", "fake news", "echo chamber", "artificial intelligence", "multiculturalism",
        )

    /** Rules of the exam, used in prompts. Sources: MSB NRW and Bezirksregierung Düsseldorf. */
    const val EXAM_FACTS =
        "Format (NRW, 4. Abiturfach, Grundkurs): 30 Minuten Vorbereitung mit neuem Material, dann eine zweiteilige " +
            "Prüfung von insgesamt 20–30 Minuten. Teil 1: zusammenhängender, freier Vortrag (ca. 10 Minuten) zu einem " +
            "Text (meist 200–300 Wörter, Sachtext oder literarischer Text) mit drei Aufgaben: 1. zusammenfassen bzw. " +
            "wiedergeben (AFB I, z. B. summarise, outline), 2. analysieren bzw. untersuchen (AFB II, z. B. \"Examine how " +
            "the author perceives …\", analyse the means …), 3. Stellung nehmen (AFB III, meist comment, auch discuss " +
            "oder assess). Notizen sind erlaubt, bloßes Ablesen nicht. Teil 2: Prüfungsgespräch (ca. 10 Minuten) über " +
            "größere fachliche Zusammenhänge, nicht auf ein Kurshalbjahr beschränkt; der Prüfling soll argumentieren, " +
            "auf Impulse eingehen und eigene Impulse setzen; Monologe werden unterbrochen. Bewertung: Inhalt 40 %, " +
            "Darstellungsleistung 60 % (u. a. Präsentationskompetenz, Interaktion, Strategien der Verständnissicherung, " +
            "Aussprache und Intonation, Wortschatz, Syntax, sprachliche Richtigkeit, Kompensationsstrategien). Für gut " +
            "oder sehr gut sind differenzierte Leistungen im Anforderungsbereich III nötig. Notenstufen: 15–13 sehr gut " +
            "(Anforderungen in besonderem Maße erfüllt), 12–10 gut (voll), 9–7 befriedigend (im Allgemeinen), 6–5 " +
            "ausreichend (Mängel, im Ganzen noch), 4 schwach ausreichend, 3–1 mangelhaft, 0 ungenügend."
}
