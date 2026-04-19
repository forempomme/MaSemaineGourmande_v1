package com.masemainegourmande.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.ShoppingItemEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.ShoppingGroup
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay

// Emoji per category name (first word match)
private val CAT_EMOJI = mapOf(
    "Viandes" to "🥩", "Produits" to "🧀", "Œufs" to "🥚",
    "Fruits" to "🥦", "Féculents" to "🌾", "Épicerie" to "🫙",
    "Boissons" to "🥤", "Boulangerie" to "🥖", "Autre" to "🛍️"
)

private fun catEmoji(name: String) =
    CAT_EMOJI.entries.firstOrNull { name.startsWith(it.key) }?.value ?: "📦"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val groups     by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current

    var showAdd    by remember { mutableStateOf(false) }
    var showShare  by remember { mutableStateOf(false) }
    var showMenu   by remember { mutableStateOf(false) }
    var undoItem   by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    LaunchedEffect(undoItem) {
        if (undoItem != null) { delay(3000); undoItem = null }
    }

    Box(Modifier.fillMaxSize().background(BgCream)) {
        if (groups.isEmpty()) {
            EmptyShoppingState(Modifier.fillMaxSize())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                groups.forEach { group ->
                    stickyHeader(key = "h_${group.categoryId}") {
                        Row(
                            Modifier.fillMaxWidth().background(BgCream)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(catEmoji(group.categoryName), fontSize = 18.sp)
                            Text(group.categoryName.uppercase(),
                                fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
                                color = TextMuted, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                            Surface(color = PriOrangeLight, shape = RoundedCornerShape(20.dp)) {
                                Text(group.items.size.toString(), fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold, color = PriOrange,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                    items(group.items, key = { it.id }) { item ->
                        ShoppingItemRow(
                            item     = item,
                            onDelete = { undoItem = item; vm.deleteItem(item.id) }
                        )
                    }
                    item(key = "sp_${group.categoryId}") { Spacer(Modifier.height(4.dp)) }
                }
            }
        }

        // ── Floating action buttons ──────────────────────
        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (groups.isNotEmpty()) {
                SmallFloatingActionButton(onClick = { showShare = true },
                    containerColor = PriOrangeLight, contentColor = PriOrange) {
                    Icon(Icons.Default.Share, "Partager")
                }
                SmallFloatingActionButton(onClick = { showMenu = true },
                    containerColor = PriOrangeLight, contentColor = PriOrange) {
                    Icon(Icons.Default.MoreVert, "Plus")
                }
            }
            FloatingActionButton(onClick = { showAdd = true },
                containerColor = PriOrange, contentColor = Color.White) {
                Icon(Icons.Default.Add, "Ajouter")
            }
        }

        // ── Undo bar ──────────────────────────────────────
        AnimatedVisibility(
            visible  = undoItem != null,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        ) {
            Surface(color = Color(0xFF2C1A0E), shape = RoundedCornerShape(28.dp),
                shadowElevation = 10.dp, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.padding(start=18.dp, end=10.dp, top=10.dp, bottom=10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("🗑  ${undoItem?.name ?: ""} supprimé",
                        color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        undoItem?.let { vm.addManualItem(it.name, it.qty, it.unit); undoItem = null }
                    }, colors = ButtonDefaults.buttonColors(containerColor = PriOrange),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal=16.dp, vertical=6.dp)) {
                        Text("Annuler", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddManualItemDialog(
            onDismiss = { showAdd = false },
            onAdd     = { name, qty, unit -> vm.addManualItem(name, qty, unit); showAdd = false }
        )
    }
    if (showShare) {
        ShareSheet(text = vm.buildShareText(groups), onDismiss = { showShare = false }, context = context)
    }
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Actions") },
            text  = {
                Column {
                    TextButton(onClick = { vm.clearChecked(); showMenu = false }) { Text("✓ Vider les cochés") }
                    TextButton(onClick = { vm.clearAll(); showMenu = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("🗑 Tout vider")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Fermer") } },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ─── Shopping item row ───────────────────────────────────────

@Composable
private fun ShoppingItemRow(item: ShoppingItemEntity, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(item.name, fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.qty > 0) {
            Text("${fmtQty(item.qty)} ${item.unit}".trim(),
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = PriOrange)
        }
        if (item.fromRecipeId != null) {
            Text("📅", fontSize = 11.sp, color = TextMuted)
        }
        // ✕ delete button
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Supprimer", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
    HorizontalDivider(color = BorderBeige, thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 16.dp))
}

// ─── Dialogs ─────────────────────────────────────────────────

@Composable
fun AddManualItemDialog(onDismiss: () -> Unit, onAdd: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty  by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un article", fontWeight = FontWeight.ExtraBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Article *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it },
                        label = { Text("Quantité") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(value = unit, onValueChange = { unit = it },
                        label = { Text("Unité") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { if(name.isNotBlank()) onAdd(name.trim(), qty.toDoubleOrNull() ?: 0.0, unit.trim()) },
                enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                Text("Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(text: String, onDismiss: () -> Unit, context: Context) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start=20.dp, end=20.dp, bottom=40.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("📤 Partager la liste", fontWeight=FontWeight.ExtraBold, fontSize=18.sp)
            Surface(color=MaterialTheme.colorScheme.surfaceVariant, shape=RoundedCornerShape(12.dp)) {
                Text(text, fontSize=13.sp, lineHeight=22.sp,
                    modifier=Modifier.fillMaxWidth().padding(14.dp).heightIn(max=260.dp).verticalScroll(rememberScrollState()))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("courses", text)); copied=true
                }, modifier=Modifier.weight(1f), shape=RoundedCornerShape(12.dp)) {
                    Icon(if(copied) Icons.Default.Check else Icons.Default.ContentCopy, null, modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if(copied) "Copié !" else "Copier")
                }
                Button(onClick={
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
                        "Partager"))
                }, modifier=Modifier.weight(1f), shape=RoundedCornerShape(12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=PriOrange)) {
                    Icon(Icons.Default.Share, null, modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Partager")
                }
            }
        }
    }
}

@Composable
private fun EmptyShoppingState(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
        Text("🛒", fontSize=64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Liste vide", fontWeight=FontWeight.ExtraBold, fontSize=20.sp)
        Spacer(Modifier.height(8.dp))
        Text("Ajoutez des repas dans le planning\nou appuyez sur + pour ajouter manuellement.",
            fontSize=14.sp, color=TextMuted, fontStyle=FontStyle.Italic,
            textAlign=TextAlign.Center, modifier=Modifier.padding(horizontal=40.dp))
    }
}

private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
