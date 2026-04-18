package com.masemainegourmande.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.ImportHistoryEntity
import com.masemainegourmande.data.model.ParsedRecipe
import com.masemainegourmande.data.model.RecipeEntity
import com.masemainegourmande.importer.KNOWN_SITES
import com.masemainegourmande.importer.detectSite
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.ImportState
import com.masemainegourmande.viewmodel.ImportViewModel
import com.masemainegourmande.viewmodel.ShoppingViewModel

private enum class ImportTab { URL, PASTE, HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    importVm:   ImportViewModel,
    shoppingVm: ShoppingViewModel,
    onRecipeSaved: (savedId: String) -> Unit = {}
) {
    val state         by importVm.state.collectAsState()
    val history       by importVm.importHistory.collectAsState()
    var activeTab     by remember { mutableStateOf(ImportTab.URL) }
    var showAddSheet  by remember { mutableStateOf<ParsedRecipe?>(null) }
    var savedRecipe   by remember { mutableStateOf<RecipeEntity?>(null) }

    // When import succeeds with a saved ID, let parent know
    LaunchedEffect(state) {
        if (state is ImportState.Success) {
            val s = state as ImportState.Success
            if (s.savedId != null) onRecipeSaved(s.savedId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📥 Importer une recette", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PriOrange,
                    titleContentColor = Color.White
                )
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            // ── Tab Row ──────────────────────────────────────
            TabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor   = MaterialTheme.colorScheme.surface,
                contentColor     = PriOrange
            ) {
                Tab(selected = activeTab == ImportTab.URL,
                    onClick  = { activeTab = ImportTab.URL; importVm.reset() },
                    text     = { Text("🔗 URL") })
                Tab(selected = activeTab == ImportTab.PASTE,
                    onClick  = { activeTab = ImportTab.PASTE; importVm.reset() },
                    text     = { Text("📋 Coller") })
                if (history.isNotEmpty())
                    Tab(selected = activeTab == ImportTab.HISTORY,
                        onClick  = { activeTab = ImportTab.HISTORY; importVm.reset() },
                        text     = { Text("🕘 Historique") })
            }

            // ── Content ───────────────────────────────────────
            AnimatedContent(
                targetState = Pair(activeTab, state),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "importContent"
            ) { (tab, st) ->
                when {
                    st is ImportState.Success -> SuccessPanel(
                        recipe   = st.recipe,
                        onReset  = importVm::reset,
                        onConfirm = { recipe ->
                            importVm.confirmImport(recipe)
                            showAddSheet = recipe
                        }
                    )
                    tab == ImportTab.URL   -> UrlPanel(importVm, st)
                    tab == ImportTab.PASTE -> PastePanel(importVm, st)
                    else                   -> HistoryPanel(history) { url ->
                        activeTab = ImportTab.URL
                        importVm.reset()
                    }
                }
            }
        }
    }

    // ── Add-to-shopping bottom sheet ─────────────────────────
    showAddSheet?.let { recipe ->
        AddToShoppingSheet(
            recipe     = recipe,
            onDismiss  = { showAddSheet = null },
            onAdd      = { persons ->
                // We need the RecipeEntity — saved by confirmImport above
                // For simplicity, create a transient entity for ShoppingVM
                val entity = com.masemainegourmande.data.model.ParsedRecipe(
                    name        = recipe.name,
                    emoji       = recipe.emoji,
                    portions    = recipe.portions,
                    url         = recipe.url,
                    ingredients = recipe.ingredients,
                    steps       = recipe.steps
                ).toTempEntity()
                shoppingVm.addRecipe(entity, persons)
                showAddSheet = null
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// URL PANEL
// ─────────────────────────────────────────────────────────────

@Composable
private fun UrlPanel(vm: ImportViewModel, state: ImportState) {
    var url     by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val siteInfo = remember(url) { detectSite(url) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Intro
        item {
            Text(
                "Collez l'URL d'une recette. Compatible Marmiton, 750g, Jow, Allrecipes…",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        // Site detection badge
        siteInfo?.let { info ->
            item {
                SiteBadge(info)
            }
        }

        // URL input
        item {
            OutlinedTextField(
                value         = url,
                onValueChange = { url = it },
                label         = { Text("URL de la recette") },
                placeholder   = { Text("https://www.marmiton.org/recettes/…") },
                leadingIcon   = { Icon(Icons.Default.Link, contentDescription = null) },
                trailingIcon  = if (url.isNotEmpty()) {
                    { IconButton(onClick = { url = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Effacer")
                    }}
                } else null,
                singleLine    = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = {
                    keyboard?.hide()
                    if (url.isNotBlank()) vm.importFromUrl(url)
                }),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp)
            )
        }

        // Error card
        if (state is ImportState.Error) {
            item { ErrorCard((state as ImportState.Error).message) }
        }

        // Compatible sites chips
        item { CompatibleSitesRow() }

        // Import button
        item {
            val isLoading = state is ImportState.Loading
            Button(
                onClick  = { keyboard?.hide(); vm.importFromUrl(url) },
                enabled  = url.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color    = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text((state as ImportState.Loading).step)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importer depuis ce lien", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PASTE PANEL
// ─────────────────────────────────────────────────────────────

@Composable
private fun PastePanel(vm: ImportViewModel, state: ImportState) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Ouvrez la recette dans votre navigateur, sélectionnez tout (▣ Tout sélectionner) et collez le texte ci-dessous.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it },
            label         = { Text("Texte de la recette") },
            placeholder   = { Text("Collez votre recette ici…") },
            modifier      = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            shape         = RoundedCornerShape(12.dp),
            maxLines      = 40
        )
        if (state is ImportState.Error) {
            ErrorCard((state as ImportState.Error).message)
        }
        Button(
            onClick  = { vm.importFromText(text) },
            enabled  = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Analyser la recette", fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// HISTORY PANEL
// ─────────────────────────────────────────────────────────────

@Composable
private fun HistoryPanel(
    history:   List<ImportHistoryEntity>,
    onReimport: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Recettes importées récemment :", style = MaterialTheme.typography.labelMedium, color = TextMuted) }
        items(history) { entry ->
            Card(
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(entry.emoji, fontSize = 26.sp)
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE)
                                .format(java.util.Date(entry.importedAt)),
                            fontSize = 11.sp, color = TextMuted
                        )
                    }
                    FilledTonalButton(
                        onClick  = { onReimport(entry.url) },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = PriOrangeLight)
                    ) { Text("↗ Re-importer", fontSize = 12.sp, color = PriOrange) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SUCCESS PANEL  (recipe preview)
// ─────────────────────────────────────────────────────────────

@Composable
private fun SuccessPanel(
    recipe:    ParsedRecipe,
    onReset:   () -> Unit,
    onConfirm: (ParsedRecipe) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header card
        item {
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AccGreenLight),
                border = BorderStroke(1.dp, AccGreen.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(recipe.emoji, fontSize = 36.sp)
                        Column {
                            Text(recipe.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("✅ Recette détectée", color = AccGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    // Stats row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip("👤", recipe.portions.toString(), "portions")
                        StatChip("🧂", recipe.ingredients.size.toString(), "ingrédients")
                        StatChip("📋", recipe.steps.size.toString(), "étapes")
                    }
                }
            }
        }

        // Ingredient preview
        if (recipe.ingredients.isNotEmpty()) {
            item {
                Text("🧂 Aperçu des ingrédients", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            items(recipe.ingredients.take(6)) { ing ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(ing.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(
                        if (ing.qty > 0) "${formatQty(ing.qty)} ${ing.unit}".trim() else "—",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriOrange
                    )
                }
                HorizontalDivider(color = BorderBeige)
            }
            if (recipe.ingredients.size > 6) {
                item {
                    Text("+ ${recipe.ingredients.size - 6} autres…", color = TextMuted, fontSize = 12.sp, fontStyle = FontStyle.Italic)
                }
            }
        }

        // Actions
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Button(
                onClick  = { onConfirm(recipe) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter à mes recettes", fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(
                onClick  = onReset,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recommencer")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ADD-TO-SHOPPING BOTTOM SHEET
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToShoppingSheet(
    recipe:   ParsedRecipe,
    onDismiss: () -> Unit,
    onAdd:    (Int) -> Unit
) {
    var persons by remember { mutableIntStateOf(recipe.portions) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(recipe.emoji, fontSize = 36.sp)
                Column {
                    Text("Ajouter à la liste de courses", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(recipe.name, color = TextMuted, fontSize = 13.sp)
                }
            }

            // Persons picker
            Card(
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = PriOrangeLight)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("👤 Nombre de personnes", fontWeight = FontWeight.Bold, color = PriOrange)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { if (persons > 1) persons-- },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrange)) {
                            Icon(Icons.Default.Remove, tint = Color.White, contentDescription = "Moins")
                        }
                        Text(persons.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        IconButton(onClick = { persons++ },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrange)) {
                            Icon(Icons.Default.Add, tint = Color.White, contentDescription = "Plus")
                        }
                    }
                }
            }

            Text(
                "${recipe.ingredients.size} ingrédient${if (recipe.ingredients.size > 1) "s" else ""} seront ajoutés (quantités ajustées pour $persons personnes).",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )

            Button(
                onClick  = { onAdd(persons) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ajouter à la liste 🛒", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// REUSABLE SMALL COMPOSABLES
// ─────────────────────────────────────────────────────────────

@Composable
private fun RowScope.StatChip(icon: String, value: String, label: String) {
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.weight(1f)
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 18.sp)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = PriOrange)
            Text(label, fontSize = 10.sp, color = TextMuted)
        }
    }
}

@Composable
private fun SiteBadge(info: com.masemainegourmande.importer.SiteInfo) {
    val bg    = if (info.compatible) AccGreenLight else Color(0xFFFFF3CD)
    val color = if (info.compatible) AccGreen      else Color(0xFF856404)
    Card(
        shape  = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (info.compatible) "✅" else "⚠️", fontSize = 16.sp)
            Column {
                Text(info.name, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
                info.tip?.let { Text(it, fontSize = 12.sp, color = color.copy(alpha = 0.8f)) }
                if (info.tip == null && info.compatible) Text("Site compatible JSON-LD.", fontSize = 12.sp, color = color.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8E8)),
        border = BorderStroke(1.dp, Color(0xFFFFBBBB)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("❌ Échec de l'import", fontWeight = FontWeight.Bold, color = Color(0xFFC0392B), fontSize = 13.sp)
            Text(message, fontSize = 12.sp, color = Color(0xFFC0392B))
            Text("💡 Essayez l'onglet Coller : ouvrez la page dans votre navigateur et copiez le texte.",
                fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun CompatibleSitesRow() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sites compatibles", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KNOWN_SITES.values.filter { it.compatible }.forEach { site ->
                SuggestionChip(
                    onClick = {},
                    label  = { Text(site.name, fontSize = 11.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = PriOrangeLight
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = PriOrange.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────

private fun formatQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

/** Creates a transient RecipeEntity for passing to ShoppingViewModel before DB save. */
private fun ParsedRecipe.toTempEntity(): com.masemainegourmande.data.model.RecipeEntity =
    toEntity(java.util.UUID.randomUUID().toString())
