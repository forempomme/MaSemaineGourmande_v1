package com.masemainegourmande.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.CategoryEntity
import com.masemainegourmande.data.model.PantryEntity
import com.masemainegourmande.data.model.encodeJson
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.SettingsViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, recipeCount: Int, mealCount: Int, importCount: Int) {
    val defaultPersons by vm.defaultPersons.collectAsState()
    val categories     by vm.categories.collectAsState()
    val pantry         by vm.pantry.collectAsState()
    val context         = LocalContext.current
    val snackHost       = remember { SnackbarHostState() }

    var showPantry      by remember { mutableStateOf(false) }
    var showCategories  by remember { mutableStateOf(false) }
    var resetConfirm    by remember { mutableStateOf(false) }
    var toastMsg        by remember { mutableStateOf("") }

    LaunchedEffect(toastMsg) {
        if (toastMsg.isNotEmpty()) { snackHost.showSnackbar(toastMsg); toastMsg = "" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Options", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PriOrange, titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackHost) }
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Default persons ───────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("👤 Personnes par défaut", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconButton(onClick = { if (defaultPersons > 1) vm.setDefaultPersons(defaultPersons - 1) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                                Icon(Icons.Default.Remove, null, tint = PriOrange)
                            }
                            Text(defaultPersons.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = PriOrange)
                            IconButton(onClick = { vm.setDefaultPersons(defaultPersons + 1) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = PriOrangeLight)) {
                                Icon(Icons.Default.Add, null, tint = PriOrange)
                            }
                            Text("personne${if (defaultPersons > 1) "s" else ""}", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                }
            }

            // ── Pantry ─────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { showPantry = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🏠 Garde-manger", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val checked = pantry.count { it.checked }
                            Text("$checked/${pantry.size} articles en stock — exclus des courses",
                                fontSize = 12.sp, color = TextMuted)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                    }
                }
            }

            // ── Categories ─────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable { showCategories = true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📂 Catégories", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${categories.size} catégories configurées", fontSize = 12.sp, color = TextMuted)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                    }
                }
            }

            // ── Export / Share ─────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("💾 Sauvegarde", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Button(onClick = {
                            val text = "Ma Semaine Gourmande — $recipeCount recettes, $mealCount repas planifiés"
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
                                "Partager"
                            ))
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Partager les données")
                        }
                        OutlinedButton(onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("stats", "$recipeCount recettes · $mealCount repas"))
                            toastMsg = "✅ Copié dans le presse-papiers !"
                        }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copier les statistiques")
                        }
                    }
                }
            }

            // ── About ──────────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ℹ️ À propos", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        InfoRow("📱 Version", "1.6.0")
                        InfoRow("📖 Recettes", recipeCount.toString())
                        InfoRow("🍽️ Repas planifiés", mealCount.toString())
                        InfoRow("📥 Imports URL", importCount.toString())
                        Spacer(Modifier.height(6.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = PriOrangeLight),
                            shape = RoundedCornerShape(10.dp)) {
                            Text("🍽️ Ma Semaine Gourmande v1.6.0",
                                fontSize = 13.sp, color = PriOrange, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }

            // ── Danger zone ────────────────────────────
            item {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0xFFFFBBBB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⚠️ Zone de danger", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFC0392B))
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { if (resetConfirm) { /* handled by parent */ } else resetConfirm = true },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (resetConfirm) Color(0xFFC0392B) else Color(0xFFFFE0E0),
                                contentColor   = if (resetConfirm) Color.White else Color(0xFFC0392B)
                            )
                        ) {
                            Text(if (resetConfirm) "Cliquez encore pour confirmer — IRRÉVERSIBLE"
                                 else "🗑 Réinitialiser l'application")
                        }
                        if (resetConfirm) {
                            Text("Toutes vos recettes et votre planning seront effacés.",
                                fontSize = 12.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showPantry) {
        PantrySheet(pantry = pantry, onDismiss = { showPantry = false },
            onAdd = { vm.addPantryItem(it) },
            onToggle = { vm.togglePantry(it) },
            onDelete = { vm.deletePantryItem(it.id) },
            onUncheckAll = { vm.uncheckAllPantry() })
    }

    if (showCategories) {
        CategoriesSheet(categories = categories, onDismiss = { showCategories = false },
            onUpsert = { vm.upsertCategory(it) },
            onDelete = { vm.deleteCategory(it) },
            onNew    = { name, kws -> vm.newCategory(name, kws) })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = TextMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Pantry Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PantrySheet(
    pantry: List<PantryEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onToggle: (PantryEntity) -> Unit,
    onDelete: (PantryEntity) -> Unit,
    onUncheckAll: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    val checkedCount = pantry.count { it.checked }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🏠 Garde-manger", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text(if (checkedCount > 0) "$checkedCount article(s) en stock — exclus des courses"
                         else "Cochez ce que vous avez déjà", fontSize = 12.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            // Add input
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newName, onValueChange = { newName = it },
                    placeholder = { Text("Ajouter un article...") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp), singleLine = true)
                Button(onClick = { if (newName.isNotBlank()) { onAdd(newName.trim()); newName = "" } },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                    Icon(Icons.Default.Add, null)
                }
            }
            LazyColumn(Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(pantry, key = { it.id }) { item ->
                    Card(shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BorderBeige),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(checked = item.checked, onCheckedChange = { onToggle(item) },
                                colors = CheckboxDefaults.colors(checkedColor = AccGreen))
                            Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (item.checked) AccGreen else MaterialTheme.colorScheme.onSurface)
                            if (item.checked) {
                                Surface(color = AccGreenLight, shape = RoundedCornerShape(8.dp)) {
                                    Text("En stock", fontSize = 10.sp, color = AccGreen, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFD04040), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            if (checkedCount > 0) {
                OutlinedButton(onClick = onUncheckAll, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
                    shape = RoundedCornerShape(10.dp)) { Text("Tout décocher") }
            }
        }
    }
}

// ─── Categories Sheet ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesSheet(
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onUpsert: (CategoryEntity) -> Unit,
    onDelete: (String) -> Unit,
    onNew: (String, List<String>) -> Unit
) {
    var editCat by remember { mutableStateOf<CategoryEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 32.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📂 Catégories", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Button(onClick = { showNew = true }, colors = ButtonDefaults.buttonColors(containerColor = PriOrange),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Nouvelle")
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(categories.sortedBy { it.sortOrder }, key = { it.id }) { cat ->
                    Card(shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BorderBeige),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = { editCat = cat }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Edit, null, tint = PriOrange, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(cat.id) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFD04040), modifier = Modifier.size(16.dp))
                                }
                            }
                            val kws = cat.parseKeywords()
                            if (kws.isNotEmpty()) {
                                Text(kws.take(6).joinToString(", ") + if (kws.size > 6) "..." else "",
                                    fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    val catToEdit = editCat
    if (catToEdit != null || showNew) {
        CategoryEditDialog(
            cat = catToEdit,
            onDismiss = { editCat = null; showNew = false },
            onSave = { name, kws ->
                if (catToEdit != null) {
                    onUpsert(catToEdit.copy(name = name, keywords = kws.encodeJson()))
                } else {
                    onNew(name, kws)
                }
                editCat = null; showNew = false
            }
        )
    }
}

@Composable
private fun CategoryEditDialog(
    cat: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(cat?.name ?: "") }
    var kws  by remember { mutableStateOf(cat?.parseKeywords()?.joinToString(", ") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (cat != null) "Modifier la catégorie" else "Nouvelle catégorie") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom *") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), singleLine = true)
                OutlinedTextField(value = kws, onValueChange = { kws = it },
                    label = { Text("Mots-clés (séparés par virgule)") },
                    placeholder = { Text("poulet, bœuf, saumon...") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
                    shape = RoundedCornerShape(10.dp), maxLines = 5)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onSave(name.trim(), kws.split(",").map { it.trim() }.filter { it.isNotBlank() })
            }, enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        shape = RoundedCornerShape(14.dp)
    )
}
