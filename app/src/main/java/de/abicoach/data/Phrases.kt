package de.abicoach.data

/** Useful phrases (Redemittel) for the three tasks of part 1 and for the exam conversation. Own wording. */
object Phrases {
    data class Group(val title: String, val hint: String, val phrases: List<String>)

    val PART1 =
        listOf(
            Group(
                "Aufgabe 1 – Zusammenfassen",
                "Simple present, keine Zitate, keine eigene Meinung, nur das Wichtigste.",
                listOf(
                    "The text \"…\" by …, published in … in …, deals with …",
                    "The author's main point is that …",
                    "First, the author explains … / He then goes on to argue that …",
                    "In addition, she points out that …",
                    "Finally, the author concludes that …",
                ),
            ),
            Group(
                "Aufgabe 2 – Analysieren",
                "Behauptung – Beleg mit Zeilenangabe – Wirkung. Immer zurück zur Aufgabe.",
                listOf(
                    "The author's attitude towards … is clearly critical, which becomes obvious in line …",
                    "The author perceives … as …, as the words \"…\" (l. …) show.",
                    "By using emotive language such as \"…\", the author appeals to the reader's feelings.",
                    "The rhetorical question in line … makes the reader think about …",
                    "The contrast between … and … emphasises …",
                    "The first-person narrator's limited perspective shows …",
                ),
            ),
            Group(
                "Aufgabe 3 – Kommentieren",
                "Klare Position, Argumente mit Beispielen aus dem Unterricht, Fazit.",
                listOf(
                    "In my opinion, the author is right / only partly right when he claims that …",
                    "On the one hand …, on the other hand …",
                    "A good example of this is …, which we discussed in class.",
                    "This is also true for …, as we saw in …",
                    "Critics might object that … However, …",
                    "Taking everything into account, I believe that …",
                ),
            ),
            Group(
                "Überleitungen im Vortrag",
                "Sag immer, wo du gerade bist – das ist Präsentationskompetenz.",
                listOf(
                    "I would like to start with a short summary of the text.",
                    "Having summarised the text, I would now like to turn to the analysis.",
                    "This brings me to my last task, the comment.",
                    "To sum up, …",
                ),
            ),
        )

    val PART2 =
        listOf(
            Group(
                "Im Gespräch",
                "Eingehen, begründen, Beispiele bringen, selbst Impulse setzen.",
                listOf(
                    "That's an interesting question. I think …",
                    "Let me give you an example: …",
                    "I see your point, but I would argue that …",
                    "This reminds me of …, which we dealt with in class.",
                    "Could you repeat the question, please? / Do you mean …?",
                    "If I may add one more aspect: …",
                ),
            ),
        )
}
