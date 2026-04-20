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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.masemainegourmande.importer.KNOWN_SITES
import com.masemainegourmande.importer.detectSite
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.ImportState
import com.masemainegourmande.viewmodel.ImportViewModel
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private enum class ImportTab { URL, PASTE, HISTORY }

private val _json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    importVm:      ImportViewModel,
    shoppingVm:    ShoppingViewModel,
    onRecipeSaved: (savedId: String) -> Unit = {}
) {
    val state   by importVm.state.collectAsState()
    val history by importVm.importHistory.collectAsState()
    var tab     by remember { mutableStateOf(ImportTab.URL) }

    // Notify parent when saved — pass empty string so parent just closes, no edit screen
    LaunchedEffect(state) {
        if (state is ImportState.Success) {
            val s = state as ImportState.Success
            if (s.savedId != null) onRecipeSaved("") // empty = close only, no edit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title  = { Text("📥 Importer une recette", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = PriOrange,
                    titleContentColor = Color.White
                )
            )
        }
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            // Tab row
            TabRow(selectedTabIndex = tab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor   = PriOrange) {
                Tab(selected = tab == ImportTab.URL,   onClick = { tab = ImportTab.URL;   importVm.reset() }, text = { Text("🔗 URL") })
                Tab(selected = tab == ImportTab.PASTE, onClick = { tab = ImportTab.PASTE; importVm.reset() }, text = { Text("📋 Coller") })
                if (history.isNotEmpty())
                    Tab(selected = tab == ImportTab.HISTORY, onClick = { tab = ImportTab.HISTORY; importVm.reset() }, text = { Text("🕘 Historique") })
            }

            AnimatedContent(
                targetState   = tab to state,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label          = "import"
            ) { (t, st) ->
                when {
                    st is ImportState.Success ->
                        SuccessPanel(st.recipe, onReset = importVm::reset,
                            onConfirm = { r -> importVm.confirmImport(r) },
                            onAddToShopping = { r, p ->
                                val entity = r.toTempEntity()
                                shoppingVm.addRecipe(entity, p)
                            })
                    t == ImportTab.URL   -> UrlPanel(importVm, st, history)
                    t == ImportTab.PASTE -> PastePanel(importVm, st)
                    else                 -> HistoryPanel(history)
                }
            }
        }
    }
}

// ─── Success panel ────────────────────────────────────────────

@Composable
private fun SuccessPanel(
    originalRecipe: ParsedRecipe,
    onReset:         () -> Unit,
    onConfirm:       (ParsedRecipe) -> Unit,
    onAddToShopping: (ParsedRecipe, Int) -> Unit
) {
    // Editable copy of the recipe
    var name        by remember { mutableStateOf(originalRecipe.name) }
    var emoji       by remember { mutableStateOf(originalRecipe.emoji) }
    // Start at 6 portions regardless of what was parsed, so user sees scaled quantities immediately
    var portions    by remember { mutableStateOf(6) }
    val basePorts    = remember { originalRecipe.portions.coerceAtLeast(1) }
    val ratio        = portions.toDouble() / basePorts

    val currentRecipe by remember(name, emoji, portions) {
        derivedStateOf {
            originalRecipe.copy(name = name, emoji = emoji, portions = portions)
        }
    }

    LazyColumn(
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier            = Modifier.fillMaxSize()
    ) {
        // ── Orange header card ─────────────────────────────────
        item {
            Card(
                shape  = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PriOrange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.background(
                        Brush.linearGradient(listOf(PriOrange, PriOrangeDark))
                    ).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Emoji + Name
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(emoji, fontSize = 38.sp)
                        Column(Modifier.weight(1f)) {
                            Text("✅ Recette détectée", fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value         = name,
                                onValueChange = { name = it },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth(),
                                shape         = RoundedCornerShape(8.dp),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                    focusedTextColor     = Color.White,
                                    unfocusedTextColor   = Color.White,
                                    cursorColor          = Color.White
                                ),
                                textStyle = LocalTextStyle.current.copy(
                                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp
                                )
                            )
                        }
                    }

                    // Stat cards: Portions / Ingrédients / Étapes
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Portions card with stepper
                        Card(
                            shape  = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Portions", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.SemiBold)
                                Text(portions.toString(), fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                                // Up/down arrows
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color  = Color.White.copy(alpha = 0.25f),
                                        shape  = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(28.dp)
                                            .clickable { if (portions > 1) portions-- }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.KeyboardArrowDown, null,
                                                tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    Surface(
                                        color  = Color.White.copy(alpha = 0.25f),
                                        shape  = RoundedCornerShape(6.dp),
                                        modifier = Modifier.size(28.dp).clickable { portions++ }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.KeyboardArrowUp, null,
                                                tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                        // Ingrédients card
                        StatCard("Ingrédients", originalRecipe.ingredients.size.toString(), Modifier.weight(1f))
                        // Étapes card
                        StatCard("Étapes", originalRecipe.steps.size.toString(), Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Ingrédients (quantités adaptées) ──────────────────
        if (originalRecipe.ingredients.isNotEmpty()) {
            item {
                Text("🧂 Ingrédients${if (ratio != 1.0) " (×${fmtRatio(ratio)})" else ""}",
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextBrown)
            }
            items(originalRecipe.ingredients) { ing ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ing.name, fontSize = 14.sp, modifier = Modifier.weight(1f), color = PriOrange)
                    val qty = if (ing.qty > 0) "${fmtQty(ing.qty * ratio)} ${ing.unit}".trim() else ing.unit.ifEmpty { "—" }
                    Text(qty, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PriOrange)
                }
                HorizontalDivider(color = BorderBeige, thickness = 0.5.dp)
            }
        }

        // ── Actions ────────────────────────────────────────────
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Button(onClick = { onConfirm(currentRecipe) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Enregistrer dans mes recettes", fontWeight = FontWeight.Bold)
            }
        }
        item {
            OutlinedButton(onClick = onReset,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape    = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recommencer")
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(shape  = RoundedCornerShape(12.dp),
        colors  = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f)),
        modifier = modifier) {
        Column(Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

// ─── URL panel ────────────────────────────────────────────────

@Composable
private fun UrlPanel(vm: ImportViewModel, state: ImportState, history: List<ImportHistoryEntity>) {
    var url      by remember { mutableStateOf("") }
    val keyboard  = LocalSoftwareKeyboardController.current
    val siteInfo  = remember(url) { detectSite(url) }

    // Compute outside LazyColumn (remember is @Composable, can't be inside DSL)
    val alreadyImported = remember(url, history) {
        history.firstOrNull { it.url.trim() == url.trim() }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Collez le lien d'une recette. Compatible Marmiton, 750g, Jow, Allrecipes…",
                fontSize = 13.sp, color = TextMuted)
        }
        siteInfo?.let { info ->
            item { SiteBadge(info) }
        }
        alreadyImported?.let { prev ->
            item {
                Card(shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("📖", fontSize = 16.sp)
                        Column {
                            Text("Déjà importée : ${prev.emoji} ${prev.name}",
                                fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E40AF))
                            Text("Vous pouvez importer à nouveau pour mettre à jour.",
                                fontSize = 11.sp, color = Color(0xFF3B5998))
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("URL de la recette") },
                placeholder = { Text("https://www.marmiton.org/recettes/…") },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                trailingIcon = if (url.isNotEmpty()) { { IconButton(onClick = { url = "" }) { Icon(Icons.Default.Clear, null) } } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { keyboard?.hide(); if (url.isNotBlank()) vm.importFromUrl(url) }),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PriOrange)
            )
        }
        if (state is ImportState.Error) item { ErrorCard((state as ImportState.Error).message) }
        item { CompatibleSitesRow() }
        item {
            val loading = state is ImportState.Loading
            Button(
                onClick  = { keyboard?.hide(); vm.importFromUrl(url) },
                enabled  = url.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text((state as ImportState.Loading).step)
                } else {
                    Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp))
                    Text("Importer depuis ce lien", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Paste panel ──────────────────────────────────────────────

@Composable
private fun PastePanel(vm: ImportViewModel, state: ImportState) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ouvrez la recette dans votre navigateur, sélectionnez tout et collez ci-dessous.",
            fontSize = 13.sp, color = TextMuted)
        OutlinedTextField(value = text, onValueChange = { text = it },
            label = { Text("Texte de la recette") }, placeholder = { Text("Collez ici…") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
            shape = RoundedCornerShape(12.dp), maxLines = 40,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PriOrange))
        if (state is ImportState.Error) ErrorCard((state as ImportState.Error).message)
        Button(onClick = { vm.importFromText(text) }, enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
            Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp))
            Text("Analyser la recette", fontWeight = FontWeight.Bold)
        }
    }
}

// ─── History panel ────────────────────────────────────────────

@Composable
private fun HistoryPanel(history: List<ImportHistoryEntity>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Recettes importées récemment :", fontSize = 13.sp, color = TextMuted) }
        items(history) { entry ->
            Card(shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.emoji, fontSize = 26.sp)
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.FRANCE)
                            .format(java.util.Date(entry.importedAt)),
                            fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

// ─── Small reusables ──────────────────────────────────────────

@Composable
private fun SiteBadge(info: com.masemainegourmande.importer.SiteInfo) {
    val bg    = if (info.compatible) AccGreenLight else Color(0xFFFFF0CC)
    val color = if (info.compatible) AccGreen      else Color(0xFF856404)
    Card(shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (info.compatible) "✅" else "⚠️", fontSize = 16.sp)
            Column {
                Text(info.name, fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
                if (info.tip != null) {
                    Text(info.tip, fontSize = 12.sp, color = color.copy(alpha = 0.8f))
                } else if (info.compatible) {
                    Text("Site compatible.", fontSize = 12.sp, color = color.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8E8)),
        border = BorderStroke(1.dp, Color(0xFFFFBBBB)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("❌ Échec de l'import", fontWeight = FontWeight.Bold,
                color = Color(0xFFC0392B), fontSize = 13.sp)
            Text(message, fontSize = 12.sp, color = Color(0xFFC0392B))
            Text("💡 Essayez l'onglet Coller.", fontSize = 12.sp, color = TextMuted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompatibleSitesRow() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Sites compatibles", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KNOWN_SITES.values.filter { it.compatible }.forEach { site ->
                Surface(color = PriOrangeLight, shape = RoundedCornerShape(20.dp)) {
                    Text(site.name, fontSize = 11.sp, color = PriOrange, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────

private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

private fun fmtRatio(r: Double): String =
    if (r == r.toLong().toDouble()) r.toLong().toString() else "%.1f".format(r)

private fun ParsedRecipe.toTempEntity(): com.masemainegourmande.data.model.RecipeEntity {
    val j = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    return com.masemainegourmande.data.model.RecipeEntity(
        id          = java.util.UUID.randomUUID().toString(),
        name        = name, emoji = emoji, portions = portions, url = url,
        ingredients = j.encodeToString(ListSerializer(
            com.masemainegourmande.data.model.Ingredient.serializer()), ingredients),
        steps       = j.encodeToString(ListSerializer(String.serializer()), steps),
        tags        = "[]"
    )
}
