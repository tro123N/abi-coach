package de.abicoach.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.abicoach.R
import de.abicoach.data.Knowledge
import de.abicoach.data.SessionKind
import de.abicoach.memory.CourseDoc
import de.abicoach.memory.DocKind
import de.abicoach.memory.DocStatus
import de.abicoach.memory.Importer
import de.abicoach.memory.MemoryStats
import de.abicoach.ui.AppViewModel
import de.abicoach.ui.AskMessage
import de.abicoach.ui.DetailScaffold
import de.abicoach.ui.Screen
import de.abicoach.ui.SessionSetup
import de.abicoach.ui.TabScaffold
import de.abicoach.ui.components.AbiCard
import de.abicoach.ui.components.AbiTextField
import de.abicoach.ui.components.AbiTopBar
import de.abicoach.ui.components.ActionButton
import de.abicoach.ui.components.AreaIntro
import de.abicoach.ui.components.Banner
import de.abicoach.ui.components.BarRow
import de.abicoach.ui.components.BodyText
import de.abicoach.ui.components.BulletText
import de.abicoach.ui.components.CaptionText
import de.abicoach.ui.components.CardTitle
import de.abicoach.ui.components.ChoiceChips
import de.abicoach.ui.components.ExpandableCard
import de.abicoach.ui.components.Gap
import de.abicoach.ui.components.LevelBadge
import de.abicoach.ui.components.LoadingCard
import de.abicoach.ui.components.MarkdownText
import de.abicoach.ui.components.PillRow
import de.abicoach.ui.components.PopUp
import de.abicoach.ui.components.ProgressLine
import de.abicoach.ui.components.StatTile
import de.abicoach.ui.components.Tab
import de.abicoach.ui.components.TopBarAction
import de.abicoach.ui.theme.AbiTheme
import de.abicoach.ui.theme.accent
import de.abicoach.ui.theme.green
import de.abicoach.ui.theme.red
import de.abicoach.ui.theme.yellow
import java.io.File

/** "Mein Kurs": upload everything from the Oberstufe; the AI reads it into a permanent memory. */
@Composable
fun CourseScreen(vm: AppViewModel) {
    val app = vm.app
    val context = LocalContext.current
    val docs by app.memory.docs.collectAsState()
    val stats = remember(docs) { app.memory.stats() }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.onPicked(uris) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris -> vm.onPicked(uris) }
    var shots by remember { mutableStateOf(listOf<Uri>()) }
    var pendingShot by remember { mutableStateOf<Uri?>(null) }
    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val shot = pendingShot
            if (ok && shot != null) shots = shots + shot
            pendingShot = null
        }

    fun newShot() {
        vm.tap()
        val file = File(context.cacheDir, "camera/seite_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        pendingShot = uri
        takePicture.launch(uri)
    }

    LaunchedEffect(Unit) { app.resumeReading() }

    TabScaffold(
        vm,
        Tab.KURS,
        "Mein Kurs",
        actions = { TopBarAction(R.drawable.forum_24dp, "Frag dein Material", { vm.tap(); vm.nav.push(Screen.Ask) }, tint = accent, tag = "action_ask") },
    ) {
        item {
            AreaIntro(
                icon = R.drawable.school_24dp,
                title = "Dein Gedächtnis",
                text = "Lade das komplette Schulbuch, alle Klausuren und dein Unterrichtsmaterial hoch. Die KI liest alles und nutzt es für Prüfungstexte, Fragen und Auswertungen.",
            )
        }
        if (vm.errorMessage != null || vm.notice != null) item { MessageBanners(vm) }
        item {
            AbiCard(modifier = Modifier.testTag("course_add")) {
                CardTitle("Hinzufügen", icon = R.drawable.cloud_upload_24dp)
                ActionButton(
                    "PDF oder Dokument",
                    R.drawable.upload_file_24dp,
                    accent,
                    {
                        vm.tap()
                        pickFiles.launch(
                            arrayOf(
                                "application/pdf",
                                "image/*",
                                "text/plain",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            ),
                        )
                    },
                    subtitle = "Schulbuch, Abi-Buch, Arbeitsblätter, Word-Dateien",
                    enabled = !vm.importBusy,
                    container = MaterialTheme.colorScheme.background,
                    modifier = Modifier.testTag("add_files"),
                )
                Gap(8.dp)
                ActionButton(
                    "Fotos aus der Galerie",
                    R.drawable.image_24dp,
                    accent,
                    { vm.tap(); pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    subtitle = "Mehrere Fotos werden zu einem Dokument (z. B. eine Klausur)",
                    enabled = !vm.importBusy,
                    container = MaterialTheme.colorScheme.background,
                )
                Gap(8.dp)
                ActionButton(
                    if (shots.isEmpty()) "Seiten fotografieren" else "Nächste Seite fotografieren",
                    R.drawable.add_a_photo_24dp,
                    accent,
                    { newShot() },
                    subtitle = if (shots.isEmpty()) "Seite für Seite, auch mit Lehrer-Korrekturen" else "${shots.size} ${if (shots.size == 1) "Seite" else "Seiten"} fotografiert",
                    enabled = !vm.importBusy,
                    container = MaterialTheme.colorScheme.background,
                )
                if (shots.isNotEmpty()) {
                    Gap(8.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Fertig", R.drawable.check_24dp, green, { vm.tap(); vm.onPicked(shots); shots = emptyList() }, Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
                        ActionButton("Verwerfen", R.drawable.delete_24dp, red, { vm.tap(); shots = emptyList() }, Modifier.weight(1f), container = MaterialTheme.colorScheme.background)
                    }
                }
                if (vm.importBusy) {
                    Gap(10.dp)
                    ProgressLine(null)
                    CaptionText("Speichere die Dateien …")
                }
            }
        }
        if (docs.isNotEmpty()) item { StatusCard(stats, docs) }
        if (stats.passages > 0) item { KnowledgeMapCard(stats) }
        DocKind.entries.forEach { kind ->
            val list = docs.filter { it.kind == kind }
            if (list.isNotEmpty()) {
                item(key = kind.name) {
                    AbiCard {
                        CardTitle(kind.plural, icon = kind.icon)
                        list.forEach { d -> DocRow(d) { vm.tap(); vm.nav.push(Screen.Doc(d.id)) } }
                    }
                }
            }
        }
        item {
            AbiCard {
                CardTitle("Datenschutz", icon = R.drawable.info_24dp)
                CaptionText(
                    "Deine Dateien bleiben auf dem Handy. Zum Lesen schickt die App die Seiten an Google Gemini. " +
                        "Im Gratis-Tarif darf Google die Daten zur Verbesserung nutzen, mit Abrechnung nicht.",
                )
            }
        }
    }
    vm.pendingImport?.let { files -> ImportDialog(files, onConfirm = { kind, title, halbjahr -> vm.confirmImport(kind, title, halbjahr) }, onDismiss = { vm.pendingImport = null }) }
}

@Composable
private fun StatusCard(stats: MemoryStats, docs: List<CourseDoc>) {
    val active = docs.firstOrNull { it.status == DocStatus.READING || it.status == DocStatus.INDEXING || it.status == DocStatus.DIGESTING } ?: docs.firstOrNull { it.status == DocStatus.QUEUED }
    AbiCard(modifier = Modifier.testTag("course_status")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(stats.documents.toString(), "Dokumente", Modifier.weight(1f), icon = R.drawable.folder_24dp, color = accent)
            StatTile("${stats.pagesRead}/${stats.pages}", "Seiten gelesen", Modifier.weight(1f), icon = R.drawable.menu_book_24dp, color = if (stats.pagesRead >= stats.pages) green else yellow)
            StatTile(stats.passages.toString(), "Abschnitte", Modifier.weight(1f), icon = R.drawable.hub_24dp, color = accent)
        }
        if (active != null) {
            Gap(12.dp)
            BodyText(statusLine(active))
            Gap(6.dp)
            ProgressLine(active.progress)
            CaptionText("Läuft im Hintergrund weiter – du kannst die App schließen.")
        }
        val waiting = docs.count { it.status == DocStatus.WAITING_KEY }
        if (waiting > 0) {
            Gap(8.dp)
            CaptionText("$waiting ${if (waiting == 1) "Dokument wartet" else "Dokumente warten"} auf deinen Gemini-Schlüssel (Einstellungen → KI).", color = yellow)
        }
    }
}

private fun statusLine(d: CourseDoc): String =
    when (d.status) {
        DocStatus.READING -> "Liest „${d.title}“: Seite ${d.pagesRead + 1} von ${d.pageCount}"
        DocStatus.INDEXING -> "Verknüpft „${d.title}“ für die Suche …"
        DocStatus.DIGESTING -> "Wertet „${d.title}“ aus: Themen, Fakten, Wortschatz …"
        DocStatus.QUEUED -> "„${d.title}“ ist als Nächstes dran"
        else -> d.status.label
    }

@Composable
private fun KnowledgeMapCard(stats: MemoryStats) {
    val max = (stats.chunksPerTopic.maxOrNull() ?: 1).coerceAtLeast(1)
    val covered = stats.chunksPerTopic.mapIndexed { id, n -> id to n }.filter { it.second > 0 }.sortedByDescending { it.second }
    val missing = stats.chunksPerTopic.mapIndexed { id, n -> id to n }.filter { it.second == 0 }.map { Knowledge.topicShort(it.first) }
    AbiCard(modifier = Modifier.testTag("course_map")) {
        CardTitle("Wissenskarte", icon = R.drawable.hub_24dp)
        CaptionText("So viel Material hat die App zu jedem Themenfeld der Abiturvorgaben 2027.")
        Gap(8.dp)
        covered.forEach { (id, n) ->
            BarRow(Knowledge.topicShort(id), n.toFloat() / max, "$n", accent)
            Gap(6.dp)
        }
        if (missing.isNotEmpty()) CaptionText("Noch nichts zu: " + missing.joinToString(", "))
    }
}

@Composable
private fun DocRow(d: CourseDoc, onClick: () -> Unit) {
    val dimens = AbiTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(dimens.components.cornerRadius)).clickable { onClick() }.padding(vertical = 6.dp),
    ) {
        LevelBadge(
            label = "",
            locked = d.status == DocStatus.PAUSED,
            mastered = d.status == DocStatus.DONE,
            attention = d.status == DocStatus.FAILED || d.status == DocStatus.WAITING_KEY,
            highlighted = d.isActive,
            size = dimens.components.button * 0.55f,
            icon = d.kind.icon,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.title, fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val pages = if (d.format == "text") "Text" else "${d.pageCount} ${if (d.pageCount == 1) "Seite" else "Seiten"}"
            val state =
                when (d.status) {
                    DocStatus.DONE -> d.topics.take(2).joinToString(", ") { Knowledge.topicShort(it) }.ifBlank { "fertig" }
                    DocStatus.READING -> "liest Seite ${d.pagesRead + 1}/${d.pageCount}"
                    DocStatus.FAILED -> "Fehler: ${d.error ?: "unbekannt"}"
                    else -> d.status.label
                }
            CaptionText(listOf(pages, d.halbjahr.takeIf { it != "unbekannt" }, state).filterNotNull().joinToString(" · "), color = if (d.status == DocStatus.FAILED) red else null)
            if (d.isActive) {
                Gap(4.dp)
                ProgressLine(d.progress)
            }
        }
    }
}

/** "Was ist das?" – kind, title and term of the picked files. */
@Composable
private fun ImportDialog(files: List<Importer.Picked>, onConfirm: (DocKind, String, String) -> Unit, onDismiss: () -> Unit) {
    val single = files.size == 1 || files.all { it.mime.startsWith("image/") }
    var kind by remember { mutableStateOf(if (files.all { it.mime.startsWith("image/") }) DocKind.KLAUSUR else DocKind.SCHULBUCH) }
    var title by remember { mutableStateOf(if (files.size == 1) files.first().name.substringBeforeLast('.') else "") }
    var halbjahr by remember { mutableStateOf("unbekannt") }
    val images = files.count { it.mime.startsWith("image/") }
    PopUp(
        title = "Was ist das?",
        description =
            buildString {
                append(if (files.size == 1) files.first().name else "${files.size} Dateien")
                if (images > 1) append(" – die $images Fotos werden ein Dokument")
            },
        confirmText = "Einlesen",
        onConfirm = { onConfirm(kind, title, halbjahr) },
        onDismiss = onDismiss,
    ) {
        ChoiceChips(DocKind.entries, kind, { it.label }, { kind = it }, tagPrefix = "kind")
        Gap(10.dp)
        if (single) {
            AbiTextField(title, { title = it }, label = "Titel", placeholder = "z. B. Klausur Q1.2 Brexit", tag = "import_title")
            Gap(10.dp)
        }
        BodyText("Halbjahr")
        Gap(6.dp)
        ChoiceChips(Knowledge.HALBJAHRE, halbjahr, { it }, { halbjahr = it }, tagPrefix = "halbjahr")
        if (kind == DocKind.KLAUSUR) {
            Gap(8.dp)
            CaptionText("Tipp: Auch die Seiten mit den Korrekturen und dem Kommentar der Lehrkraft mitnehmen – daraus lernt die App deine typischen Fehler.")
        }
    }
}

/** Everything the app learned from one document. */
@Composable
fun DocScreen(vm: AppViewModel, id: String) {
    val app = vm.app
    val docs by app.memory.docs.collectAsState()
    val doc = docs.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        PopUp(
            title = "Dokument löschen?",
            description = "Es wird aus dem Gedächtnis der App entfernt. Die Datei auf deinem Handy bleibt.",
            confirmText = "Löschen",
            onConfirm = { confirmDelete = false; vm.deleteDoc(id) },
            onDismiss = { confirmDelete = false },
            confirmColor = red,
        )
    }
    if (doc == null) {
        DetailScaffold(title = "Dokument", onBack = { vm.nav.pop() }) { BodyText("Dieses Dokument gibt es nicht mehr.") }
        return
    }
    val digest = remember(doc.digested, doc.status) { app.memory.digest(id) }
    val pages = remember(doc.pagesRead) { app.memory.pages(id) }
    val dimens = AbiTheme.dimens
    DetailScaffold(
        title = doc.title,
        onBack = { vm.tap(); vm.nav.pop() },
        tag = "doc",
        actions = { TopBarAction(R.drawable.delete_24dp, "Löschen", { confirmDelete = true }) },
    ) {
        AbiCard {
            PillRow(listOfNotNull(doc.kind.label, doc.halbjahr.takeIf { it != "unbekannt" }, "${doc.pageCount} ${if (doc.pageCount == 1) "Seite" else "Seiten"}"))
            Gap(10.dp)
            BodyText(if (doc.status == DocStatus.DONE) "Fertig gelesen und ausgewertet." else statusLine(doc), color = if (doc.status == DocStatus.FAILED) red else MaterialTheme.colorScheme.onPrimary)
            if (doc.status != DocStatus.DONE) {
                Gap(6.dp)
                ProgressLine(doc.progress)
            }
            doc.error?.let {
                Gap(8.dp)
                Banner(it, if (doc.status == DocStatus.FAILED) red else yellow, icon = R.drawable.warning_24dp)
            }
            if (doc.vectorless) CaptionText("Ohne Vektorsuche (Modell nicht verfügbar) – die Stichwortsuche funktioniert trotzdem.")
            Gap(10.dp)
            when {
                doc.isActive ->
                    ActionButton("Pausieren", R.drawable.pause_24dp, yellow, { vm.tap(); vm.pauseDoc(id) }, container = MaterialTheme.colorScheme.background)
                doc.status == DocStatus.PAUSED || doc.status == DocStatus.FAILED || doc.status == DocStatus.WAITING_KEY ->
                    ActionButton(
                        if (doc.status == DocStatus.FAILED) "Erneut versuchen" else "Fortsetzen",
                        R.drawable.refresh_24dp,
                        accent,
                        { vm.tap(); vm.resumeDoc(id) },
                        container = MaterialTheme.colorScheme.background,
                    )
                else -> Unit
            }
        }
        if (digest != null) {
            AbiCard {
                CardTitle("Zusammenfassung", icon = R.drawable.summarize_24dp)
                BodyText(digest.summary)
                if (digest.topics.isNotEmpty()) {
                    Gap(8.dp)
                    PillRow(digest.topics.map { Knowledge.topicShort(it) })
                }
            }
            digest.klausur?.let { k ->
                AbiCard {
                    CardTitle("Klausur" + if (k.grade.isNotBlank()) " · ${k.grade}" else "", icon = R.drawable.grading_24dp, iconTint = yellow)
                    if (k.teacherComments.isNotEmpty()) {
                        BodyText("Kommentare der Lehrkraft", color = accent)
                        k.teacherComments.forEach { BulletText(it) }
                        Gap(8.dp)
                    }
                    if (k.strengths.isNotEmpty()) {
                        BodyText("Stärken", color = green)
                        k.strengths.forEach { BulletText(it, bulletColor = green) }
                        Gap(8.dp)
                    }
                    if (k.weaknesses.isNotEmpty()) {
                        BodyText("Schwächen", color = red)
                        k.weaknesses.forEach { BulletText(it, bulletColor = red) }
                        Gap(8.dp)
                    }
                    if (k.errors.isNotEmpty()) {
                        BodyText("Markierte Fehler (${k.errors.size})", color = accent)
                        k.errors.take(25).forEach { e ->
                            Text("„${e.quote}“ → „${e.correction}“", fontSize = dimens.fonts.body, color = MaterialTheme.colorScheme.onPrimary)
                            CaptionText(Knowledge.categoryLabel(e.category) + if (e.explanation.isNotBlank()) " · ${e.explanation}" else "")
                        }
                        CaptionText("Diese Fehlerarten fließen in dein Lernprofil und in „Schwachstellen üben“ ein.")
                    }
                }
            }
            if (digest.examTexts.isNotEmpty()) {
                AbiCard {
                    CardTitle("Texte für Teil 1", icon = R.drawable.article_24dp)
                    digest.examTexts.forEachIndexed { i, ref ->
                        if (i > 0) Gap(10.dp)
                        BodyText(ref.title.ifBlank { "Text" })
                        CaptionText(
                            (if (ref.endPage > ref.startPage) "S. ${ref.startPage}–${ref.endPage}" else "S. ${ref.startPage}") +
                                (if (ref.topic >= 0) " · ${Knowledge.topicShort(ref.topic)}" else "") +
                                (if (ref.literary) " · Literatur" else " · Sachtext") +
                                (if (ref.why.isNotBlank()) " · ${ref.why}" else ""),
                        )
                        Gap(6.dp)
                        ActionButton(
                            "Vortrag mit diesem Text",
                            R.drawable.co_present_24dp,
                            accent,
                            {
                                vm.tap()
                                vm.nav.push(
                                    Screen.Setup(
                                        SessionSetup.defaults(SessionKind.PART1, 0, app.settings.defaultFeedback).copy(
                                            source = Knowledge.Source.MATERIAL,
                                            fixedDocId = id,
                                            fixedPage = ref.startPage,
                                            topicChoice = if (ref.topic >= 0) ref.topic else Knowledge.RANDOM,
                                        ),
                                    ),
                                )
                            },
                            container = MaterialTheme.colorScheme.background,
                        )
                    }
                }
            }
            if (digest.facts.isNotEmpty()) {
                ExpandableCard("Wichtige Inhalte (${digest.facts.size})", subtitle = "Das kann der Prüfer im Gespräch aufgreifen", icon = R.drawable.lightbulb_24dp, iconTint = yellow) {
                    digest.facts.forEach { f -> BulletText(f.fact + if (f.topic >= 0) " (${Knowledge.topicShort(f.topic)})" else "") }
                }
            }
            if (digest.vocabulary.isNotEmpty()) {
                ExpandableCard("Wortschatz (${digest.vocabulary.size})", icon = R.drawable.translate_24dp) {
                    digest.vocabulary.forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f).padding(vertical = 3.dp)) {
                                BodyText(v.en, color = accent)
                                CaptionText(v.de)
                            }
                            IconButton(onClick = { vm.speak(v.en) }) {
                                Icon(painterResource(R.drawable.volume_up_24dp), contentDescription = "Vorlesen", tint = accent, modifier = Modifier.size(dimens.icons.menuItem * 0.75f))
                            }
                        }
                    }
                }
            }
        }
        if (pages.isNotEmpty()) {
            ExpandableCard("Gelesene Seiten (${pages.size})", subtitle = "So hat die KI die Seiten verstanden", icon = R.drawable.menu_book_24dp) {
                pages.take(40).forEach { p ->
                    Text("Seite ${p.page}", fontSize = dimens.fonts.body, color = accent)
                    CaptionText(p.text.take(700).ifBlank { "(leer oder übersprungen)" } + if (p.text.length > 700) " …" else "")
                    Gap(8.dp)
                }
                if (pages.size > 40) CaptionText("… und ${pages.size - 40} weitere Seiten.")
            }
        }
    }
}

/** "Frag dein Material": chat with the coach, who answers from the uploaded material. */
@Composable
fun AskScreen(vm: AppViewModel) {
    val dimens = AbiTheme.dimens
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.askMessages.size, vm.askBusy) {
        val count = vm.askMessages.size + if (vm.askBusy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count)
    }
    val suggestions =
        listOf(
            "Was hatten wir im Unterricht zu Nigeria?",
            "Welche Texte aus meinem Material passen zum American Dream?",
            "Welche Fehler mache ich in Klausuren am häufigsten?",
            "Wie unterscheidet sich eine Analyse von einer Zusammenfassung?",
        )
    Scaffold(
        topBar = {
            AbiTopBar(
                title = "Frag dein Material",
                onBack = { vm.tap(); vm.nav.pop() },
                actions = { if (vm.askMessages.isNotEmpty()) TopBarAction(R.drawable.delete_24dp, "Verlauf löschen", { vm.clearAsk() }) },
            )
        },
        bottomBar = {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Frag etwas zu deinem Kurs …") },
                    maxLines = 5,
                    shape = RoundedCornerShape(22.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                            unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                            focusedBorderColor = accent,
                            cursorColor = accent,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.primary,
                        ),
                    modifier = Modifier.weight(1f).testTag("ask_input"),
                )
                Spacer(Modifier.width(6.dp))
                val canSend = input.isNotBlank() && !vm.askBusy
                IconButton(
                    onClick = {
                        vm.ask(input)
                        input = ""
                    },
                    enabled = canSend,
                    modifier =
                        Modifier
                            .padding(bottom = 4.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (canSend) accent else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f))
                            .testTag("ask_send"),
                ) {
                    Icon(painterResource(R.drawable.send_24dp), contentDescription = "Senden", tint = Color.White, modifier = Modifier.size(dimens.icons.menuItem * 0.85f))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AreaIntro(
                    R.drawable.forum_24dp,
                    "Dein KI-Coach kennt deinen Kurs",
                    "Er antwortet mit ${vm.app.ai.brainLabel()} und zeigt dir, wo es in deinem Material steht.",
                )
            }
            if (!vm.app.course.hasMaterial()) {
                item { Banner("Noch kein Material eingelesen – der Coach antwortet dann aus seinem Wissen.", yellow) }
            }
            if (vm.askMessages.isEmpty()) {
                item {
                    AbiCard {
                        CardTitle("Zum Beispiel", icon = R.drawable.lightbulb_24dp, iconTint = yellow)
                        suggestions.forEach { s ->
                            Text(
                                s,
                                fontSize = dimens.fonts.body,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.background)
                                        .clickable { vm.ask(s) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            itemsIndexed(vm.askMessages) { _, m -> AskBubble(m) }
            if (vm.askBusy) item { LoadingCard("Ich schaue in dein Material …") }
        }
    }
}

@Composable
private fun AskBubble(m: AskMessage) {
    val dimens = AbiTheme.dimens
    if (m.fromUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                m.text,
                fontSize = dimens.fonts.body,
                lineHeight = dimens.fonts.body * 1.35f,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .widthIn(max = 320.dp)
                        .clip(RoundedCornerShape(18.dp, 4.dp, 18.dp, 18.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            MarkdownText(m.text)
            if (m.sources.isNotEmpty()) {
                Gap(8.dp)
                CaptionText("Aus deinem Material: " + m.sources.joinToString(" · "))
            }
            if (m.by.isNotBlank()) CaptionText(m.by)
        }
    }
}
