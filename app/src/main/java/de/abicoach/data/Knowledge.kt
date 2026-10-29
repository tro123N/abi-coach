package de.abicoach.data

/** Facts about the oral Abitur in English (NRW, 4th subject) that the prompts and screens rely on. */
object Knowledge {
    const val RANDOM = "Zufällig"

    /** Themenfelder Grundkurs, Abiturvorgaben 2027 (identisch mit dem Kernlehrplan). */
    val TOPIC_AREAS =
        listOf(
            "UK: Politik, Kultur, Gesellschaft – zwischen Wandel und Tradition",
            "UK: Nationale Selbstkonzepte – koloniales Erbe und europäische Verortung",
            "USA: Politik, Kultur, Gesellschaft – zwischen Wandel und Tradition",
            "USA: Amerikanische Ideale und Realitäten – Freiheit, Gleichheit, Streben nach Glück",
            "Nigeria: Politik, Kultur, Gesellschaft – zwischen Wandel und Tradition",
            "Identität: Ambitionen und Hindernisse, Konformität vs. Individualismus",
            "Vielfalt: ethnische, kulturelle, soziale, sexuelle und geschlechtliche Vielfalt",
            "Medien: Journalismus zwischen Information und Unterhaltung",
            "Medien: soziale Medien und digitale Plattformen – Teilhabe und Manipulation",
            "Literatur: klassische und multimodale Literaturformate",
            "Globalisierung: Nachhaltigkeit (sozial, ökologisch, wirtschaftlich) und Migration",
            "Globaler Arbeitsmarkt: Chancengleichheit, Wettbewerb, Kooperation",
            "Wissenschaftlicher und technischer Fortschritt: Chancen und Herausforderungen",
            "Zukunftsvisionen: utopische und dystopische Narrative",
        )

    val TEXT_TYPES = listOf("Sachtext", "Literarischer Text")

    /** NRW-style operators by Anforderungsbereich. */
    const val OPERATORS =
        "AFB I: outline, describe, summarize, state, point out. " +
            "AFB II: analyse, examine, explain, compare, characterize, interpret. " +
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
            "Prüfung von insgesamt 20–30 Minuten. Teil 1: zusammenhängender, freier Vortrag (ca. 10 Minuten) zu " +
            "2–3 Arbeitsaufträgen über einen Text von 200–300 Wörtern, ein Bild/Cartoon/eine Statistik oder einen " +
            "kurzen Hör-/Videotext; Notizen erlaubt, bloßes Ablesen nicht. Teil 2: Prüfungsgespräch (ca. 10 Minuten) " +
            "über größere fachliche Zusammenhänge, nicht auf ein Kurshalbjahr beschränkt; der Prüfling soll " +
            "argumentieren, auf Impulse eingehen und eigene Impulse setzen; Monologe werden unterbrochen. " +
            "Bewertung: Inhalt 40 %, Darstellungsleistung 60 % (u. a. Präsentationskompetenz, Interaktion, " +
            "Strategien der Verständnissicherung, Aussprache und Intonation, Wortschatz, Syntax, sprachliche " +
            "Richtigkeit, Kompensationsstrategien). Für gut oder sehr gut sind differenzierte Leistungen im " +
            "Anforderungsbereich III nötig. Notenstufen: 15–13 sehr gut (Anforderungen in besonderem Maße erfüllt), " +
            "12–10 gut (voll), 9–7 befriedigend (im Allgemeinen), 6–5 ausreichend (Mängel, im Ganzen noch), " +
            "4 schwach ausreichend, 3–1 mangelhaft, 0 ungenügend."
}
