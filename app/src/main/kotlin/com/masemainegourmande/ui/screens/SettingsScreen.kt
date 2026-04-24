package com.masemainegourmande.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.CategoryEntity
import com.masemainegourmande.data.model.encodeJson
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.PlanningViewModel
import com.masemainegourmande.viewmodel.RecipesViewModel
import com.masemainegourmande.viewmodel.SettingsViewModel
import com.masemainegourmande.viewmodel.ShoppingViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    recipesVm: RecipesViewModel,
    planningVm: PlanningViewModel,
    shoppingVm: ShoppingViewModel,
    recipeCount: Int, mealCount: Int, importCount: Int
) {
    val defaultPersons by vm.defaultPersons.collectAsState()
    val categories     by vm.categories.collectAsState()
    val context         = LocalContext.current
    val snackHost       = remember { SnackbarHostState() }

    val exportJson     by vm.exportJson.collectAsState()
    var showCategories by remember { mutableStateOf(false) }
    var resetConfirm   by remember { mutableStateOf(false) }
    var toastMsg       by remember { mutableStateOf("") }
    var importError    by remember { mutableStateOf("") }

    LaunchedEffect(toastMsg) {
        if (toastMsg.isNotEmpty()) { snackHost.showSnackbar(toastMsg); toastMsg = "" }
    }

    // ── Export: create JSON file on device ──────────────────
    val createDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val json = vm.exportJson.value ?: return@rememberLauncherForActivityResult
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            toastMsg = "✅ Données exportées !"
        } catch (e: Exception) {
            toastMsg = "❌ Erreur : ${e.message}"
        }
    }

    // ── Import: open JSON file ───────────────────────────────
    val openDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: ""
            vm.importFromJson(json)
            toastMsg = "✅ Données restaurées !"
        } catch (e: Exception) {
            importError = "❌ Fichier invalide : ${e.message}"
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackHost) }) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start=14.dp, end=14.dp, top=8.dp,
                bottom=16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Default persons ───────────────────────────
            item {
                Card(shape=RoundedCornerShape(14.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("👤 Personnes par défaut", fontWeight=FontWeight.Bold, fontSize=15.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            Surface(color=PriOrangeLight, shape=RoundedCornerShape(8.dp),
                                modifier=Modifier.size(38.dp).clickable { if(defaultPersons>1) vm.setDefaultPersons(defaultPersons-1) }) {
                                Box(contentAlignment=Alignment.Center) { Text("−", fontSize=20.sp, fontWeight=FontWeight.ExtraBold, color=PriOrange) }
                            }
                            Text(defaultPersons.toString(), fontWeight=FontWeight.ExtraBold, fontSize=28.sp, color=PriOrange)
                            Surface(color=PriOrangeLight, shape=RoundedCornerShape(8.dp),
                                modifier=Modifier.size(38.dp).clickable { vm.setDefaultPersons(defaultPersons+1) }) {
                                Box(contentAlignment=Alignment.Center) { Text("+", fontSize=20.sp, fontWeight=FontWeight.ExtraBold, color=PriOrange) }
                            }
                            Text("personne${if(defaultPersons>1) "s" else ""}", color=TextMuted, fontSize=14.sp)
                        }
                    }
                }
            }

            // ── Categories ────────────────────────────────
            item {
                Card(shape=RoundedCornerShape(14.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige),
                    modifier=Modifier.clickable { showCategories=true }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📂 Catégories", fontWeight=FontWeight.Bold, fontSize=15.sp)
                            Text("${categories.size} catégories configurées", fontSize=12.sp, color=TextMuted)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint=TextMuted)
                    }
                }
            }

            // ── Export / Import ───────────────────────────
            item {
                Card(shape=RoundedCornerShape(14.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige)) {
                    Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text("💾 Sauvegarde & Restauration", fontWeight=FontWeight.Bold, fontSize=15.sp)

                        if (importError.isNotEmpty()) {
                            Surface(color=Color(0xFFFFE8E8), shape=RoundedCornerShape(8.dp)) {
                                Text(importError, fontSize=12.sp, color=Color(0xFFC0392B),
                                    modifier=Modifier.padding(10.dp))
                            }
                        }

                        // Export to file
                        Button(onClick={
                            vm.prepareExport()
                            createDocLauncher.launch("MaSemaineGourmande_backup.json")
                        },
                            modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp),
                            colors=ButtonDefaults.buttonColors(containerColor=PriOrange)) {
                            Icon(Icons.Default.SaveAlt, null, modifier=Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Exporter mes données (.json)")
                        }

                        // Share JSON via any app
                        OutlinedButton(onClick={
                            vm.prepareExport()
                            exportJson?.let { json ->
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply{ type="text/plain"; putExtra(Intent.EXTRA_TEXT,json) },
                                    "Partager les données"))
                            }
                        }, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp)) {
                            Icon(Icons.Default.Share, null, modifier=Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Partager / envoyer vers Drive")
                        }

                        // Import from file
                        Button(onClick={ importError=""; openDocLauncher.launch("application/json") },
                            modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp),
                            colors=ButtonDefaults.buttonColors(containerColor=AccGreen)) {
                            Icon(Icons.Default.FolderOpen, null, modifier=Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Importer depuis un fichier")
                        }
                    }
                }
            }

            // ── About ─────────────────────────────────────
            item {
                Card(shape=RoundedCornerShape(14.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderBeige)) {
                    Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("ℹ️ À propos", fontWeight=FontWeight.Bold, fontSize=15.sp)
                        InfoRow("📱 Version", "1.6.0")
                        InfoRow("📖 Recettes", recipeCount.toString())
                        InfoRow("🍽️ Repas planifiés", mealCount.toString())
                        InfoRow("📥 Imports URL", importCount.toString())
                        Spacer(Modifier.height(4.dp))
                        Card(colors=CardDefaults.cardColors(containerColor=PriOrangeLight), shape=RoundedCornerShape(10.dp)) {
                            Text("🍽️ Ma Semaine Gourmande v1.6.0", fontSize=13.sp, color=PriOrange,
                                fontWeight=FontWeight.Bold, modifier=Modifier.fillMaxWidth().padding(12.dp),
                                textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }

            // ── Danger zone ───────────────────────────────
            item {
                Card(shape=RoundedCornerShape(14.dp),
                    colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                    border=BorderStroke(1.dp, Color(0xFFFFBBBB))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("⚠️ Zone de danger", fontWeight=FontWeight.Bold, fontSize=14.sp, color=Color(0xFFC0392B))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick={ if(!resetConfirm) resetConfirm=true },
                            modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(10.dp),
                            colors=ButtonDefaults.buttonColors(
                                containerColor=if(resetConfirm) Color(0xFFC0392B) else Color(0xFFFFE0E0),
                                contentColor=if(resetConfirm) Color.White else Color(0xFFC0392B))) {
                            Text(if(resetConfirm) "⚠️ Cliquez encore — IRRÉVERSIBLE" else "🗑 Réinitialiser l'application")
                        }
                        if(resetConfirm) Text("Toutes vos recettes et votre planning seront effacés.",
                            fontSize=12.sp, color=TextMuted, textAlign=androidx.compose.ui.text.style.TextAlign.Center,
                            modifier=Modifier.fillMaxWidth().padding(top=4.dp))
                    }
                }
            }
        }
    }

    if (showCategories) CategoriesSheet(categories, onDismiss={ showCategories=false },
        onUpsert={ vm.upsertCategory(it) }, onDelete={ vm.deleteCategory(it) },
        onNew={ n,kws -> vm.newCategory(n,kws) })
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label, fontSize=13.sp, color=TextMuted)
        Text(value, fontSize=13.sp, fontWeight=FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesSheet(
    categories: List<CategoryEntity>, onDismiss: ()->Unit,
    onUpsert: (CategoryEntity)->Unit, onDelete: (String)->Unit, onNew: (String,List<String>)->Unit
) {
    var editCat by remember { mutableStateOf<CategoryEntity?>(null) }
    var showNew by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.padding(bottom=32.dp)) {
            Row(Modifier.padding(horizontal=20.dp, vertical=12.dp), verticalAlignment=Alignment.CenterVertically) {
                Text("📂 Catégories", fontWeight=FontWeight.ExtraBold, fontSize=17.sp, modifier=Modifier.weight(1f))
                Button(onClick={ showNew=true }, colors=ButtonDefaults.buttonColors(containerColor=PriOrange),
                    contentPadding=PaddingValues(horizontal=12.dp, vertical=8.dp)) {
                    Icon(Icons.Default.Add, null, modifier=Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Nouvelle")
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick=onDismiss) { Icon(Icons.Default.Close, null) }
            }
            LazyColumn(Modifier.heightIn(max=500.dp), contentPadding=PaddingValues(horizontal=16.dp)) {
                items(categories.sortedBy{ it.sortOrder }, key={ it.id }) { cat ->
                    Card(shape=RoundedCornerShape(10.dp),
                        colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),
                        border=BorderStroke(1.dp,BorderBeige), modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                val emoji = catEmojiForName(cat.name)
                                Text(emoji, fontSize=18.sp, modifier=Modifier.padding(end=6.dp))
                                Text(cat.name, fontWeight=FontWeight.Bold, fontSize=14.sp, modifier=Modifier.weight(1f))
                                IconButton(onClick={ editCat=cat }, modifier=Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Edit, null, tint=PriOrange, modifier=Modifier.size(16.dp))
                                }
                                IconButton(onClick={ onDelete(cat.id) }, modifier=Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Delete, null, tint=Color(0xFFD04040), modifier=Modifier.size(16.dp))
                                }
                            }
                            val kws=cat.parseKeywords()
                            if(kws.isNotEmpty()) Text(kws.take(6).joinToString(", ")+(if(kws.size>6) "..." else ""),
                                fontSize=11.sp, color=TextMuted)
                        }
                    }
                }
            }
        }
    }
    if(editCat!=null || showNew) {
        CatEditDialog(editCat, onDismiss={ editCat=null; showNew=false }) { name, emoji, kws ->
            if(editCat!=null) onUpsert(editCat!!.copy(name="${emoji} ${name}".trim(), keywords=kws.encodeJson()))
            else onNew("${emoji} ${name}".trim(), kws)
            editCat=null; showNew=false
        }
    }
}

@Composable
private fun CatEditDialog(cat: CategoryEntity?, onDismiss: ()->Unit, onSave: (String, String, List<String>)->Unit) {
    var name  by remember { mutableStateOf(cat?.name ?: "") }
    var emoji by remember { mutableStateOf(catEmojiForName(cat?.name ?: "")) }
    var kws   by remember { mutableStateOf(cat?.parseKeywords()?.joinToString(", ") ?: "") }
    AlertDialog(onDismissRequest=onDismiss,
        title={ Text(if(cat!=null) "Modifier la catégorie" else "Nouvelle catégorie") },
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value=emoji, onValueChange={ emoji=it }, label={ Text("Emoji") },
                        modifier=Modifier.width(72.dp), shape=RoundedCornerShape(10.dp), singleLine=true)
                    OutlinedTextField(value=name, onValueChange={ name=it }, label={ Text("Nom *") },
                        modifier=Modifier.weight(1f), shape=RoundedCornerShape(10.dp), singleLine=true)
                }
                OutlinedTextField(value=kws, onValueChange={ kws=it }, label={ Text("Mots-clés (virgule)") },
                    modifier=Modifier.fillMaxWidth().heightIn(min=70.dp), shape=RoundedCornerShape(10.dp), maxLines=5)
            }
        },
        confirmButton={
            Button(onClick={ if(name.isNotBlank()) onSave(name.trim(), emoji.trim(), kws.split(",").map{it.trim()}.filter{it.isNotBlank()}) },
                enabled=name.isNotBlank(), colors=ButtonDefaults.buttonColors(containerColor=PriOrange)) { Text("Enregistrer") }
        },
        dismissButton={ TextButton(onClick=onDismiss) { Text("Annuler") } },
        shape=RoundedCornerShape(14.dp))
}

// Derive emoji from category name (kept as a simple helper)
private fun catEmojiForName(name: String): String {
    val n = name.lowercase()
    return when {
        n.startsWith("viande") || n.startsWith("poiss") || n.startsWith("volaille") -> "🥩"
        n.startsWith("produit") || n.startsWith("lait") || n.startsWith("fromage") -> "🧀"
        n.startsWith("œuf") || n.startsWith("oeuf") -> "🥚"
        n.startsWith("fruit") || n.startsWith("légume") -> "🥦"
        n.startsWith("féc") || n.startsWith("pâtes") || n.startsWith("riz") -> "🌾"
        n.startsWith("épic") || n.startsWith("condiment") -> "🫙"
        n.startsWith("bois") -> "🥤"
        n.startsWith("boulang") || n.startsWith("pain") -> "🥖"
        n.startsWith("autre") -> "🛍️"
        else -> "📦"
    }
}
