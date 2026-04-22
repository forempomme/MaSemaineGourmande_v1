package com.masemainegourmande.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.CategoryEntity
import com.masemainegourmande.data.model.ShoppingItemEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.ShoppingGroup
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay

private val CAT_EMOJI = mapOf(
    "Viandes" to "🥩", "Produits" to "🧀", "Œufs" to "🥚",
    "Fruits" to "🥦", "Féculents" to "🌾", "Épicerie" to "🫙",
    "Boissons" to "🥤", "Boulangerie" to "🥖", "Autre" to "🛍️"
)
private fun catEmoji(name: String) =
    CAT_EMOJI.entries.firstOrNull { name.startsWith(it.key) }?.value ?: "📦"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val groups     by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current

    // Inline add state
    var addName  by remember { mutableStateOf("") }
    var addQty   by remember { mutableStateOf("") }
    var addUnit  by remember { mutableStateOf("") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var showShare by remember { mutableStateOf(false) }
    var showMenu  by remember { mutableStateOf(false) }
    var undoItem  by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    // Drag state: track which category / which item is being dragged
    var draggingCatId   by remember { mutableStateOf<String?>(null) }
    var dragOverCatId   by remember { mutableStateOf<String?>(null) }
    var draggingItemKey by remember { mutableStateOf<String?>(null) } // "catId::itemId"
    var dragOverItemKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(undoItem) {
        if (undoItem != null) { delay(3000); undoItem = null }
    }

    fun addItem() {
        if (addName.isNotBlank()) {
            vm.addManualItem(addName.trim(), addQty.toDoubleOrNull() ?: 0.0, addUnit.trim())
            addName = ""; addQty = ""; addUnit = ""
        }
    }

    // Drop handlers
    fun onCatDrop() {
        val from = draggingCatId; val to = dragOverCatId
        if (from != null && to != null && from != to) vm.reorderCategory(from, to)
        draggingCatId = null; dragOverCatId = null
    }
    fun onItemDrop(catId: String) {
        val fromKey = draggingItemKey; val toKey = dragOverItemKey
        if (fromKey != null && toKey != null && fromKey != toKey) {
            val fromId = fromKey.substringAfter("::")
            val toId   = toKey.substringAfter("::")
            vm.reorderItem(catId, fromId, toId)
        }
        draggingItemKey = null; dragOverItemKey = null
    }

    Box(Modifier.fillMaxSize().background(BgCream)) {
        LazyColumn(Modifier.fillMaxSize()) {

            // ── Inline add bar ─────────────────────────────────
            item(key = "add_bar") {
                val inputColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor       = PriOrange,
                    unfocusedBorderColor     = BorderBeige,
                    focusedTextColor         = Color.Black,
                    unfocusedTextColor       = Color.Black,
                    focusedContainerColor    = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor  = MaterialTheme.colorScheme.surface,
                    focusedPlaceholderColor  = TextMuted,
                    unfocusedPlaceholderColor = TextMuted
                )
                Row(
                    Modifier.background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = addName, onValueChange = { addName = it },
                        placeholder = { Text("Article…", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.Black),
                        keyboardOptions = KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        ),
                        colors = inputColors
                    )
                    OutlinedTextField(
                        value = addQty, onValueChange = { addQty = it },
                        placeholder = { Text("Qté", fontSize = 11.sp) },
                        modifier = Modifier.width(54.dp),
                        shape = RoundedCornerShape(10.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.Black),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            imeAction    = androidx.compose.ui.text.input.ImeAction.Next
                        ),
                        colors = inputColors
                    )
                    OutlinedTextField(
                        value = addUnit, onValueChange = { addUnit = it },
                        placeholder = { Text("U.", fontSize = 11.sp) },
                        modifier = Modifier.width(54.dp),
                        shape = RoundedCornerShape(10.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.Black),
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addItem(); keyboard?.hide() }),
                        colors = inputColors
                    )
                    Surface(
                        color    = if (addName.isNotBlank()) PriOrange else PriOrangeLight,
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(42.dp)
                            .clickable(enabled = addName.isNotBlank()) { addItem() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, "Ajouter",
                                tint     = if (addName.isNotBlank()) Color.White else PriOrange,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
                HorizontalDivider(color = BorderBeige)
            }

            // ── Action bar (count + share) ──────────────────────
            item(key = "action_bar") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (totalCount > 0) "🛒 $totalCount article${if (totalCount > 1) "s" else ""}"
                        else "🛒 Liste vide",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = TextMuted, modifier = Modifier.weight(1f)
                    )
                    if (groups.isNotEmpty()) {
                        IconButton(onClick = { showShare = true }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Share, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                HorizontalDivider(color = BorderBeige)
            }

            // ── Empty state ─────────────────────────────────────
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🛒", fontSize = 52.sp)
                        Text("Liste vide", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Planifiez des repas ou ajoutez\ndes articles manuellement",
                            fontSize = 13.sp, color = TextMuted, fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center)
                    }
                }
            } else {
                // ── Categories with drag-drop ────────────────────
                groups.forEachIndexed { catIdx, group ->
                    // Category header — long-press to drag
                    stickyHeader(key = "hdr_${group.categoryId}") {
                        val isHovered = dragOverCatId == group.categoryId && draggingCatId != group.categoryId
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (isHovered) PriOrangeLight else BgCream)
                                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 5.dp)
                                .pointerInput(group.categoryId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggingCatId = group.categoryId },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            // Simple: mark hovered via offset (approximation)
                                        },
                                        onDragEnd   = { onCatDrop() },
                                        onDragCancel = { draggingCatId = null; dragOverCatId = null }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("⠿", fontSize = 14.sp, color = TextMuted.copy(alpha = 0.5f))
                            Text(catEmoji(group.categoryName), fontSize = 14.sp)
                            Text(group.categoryName.uppercase(),
                                fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                                color = TextMuted, letterSpacing = 0.8.sp,
                                modifier = Modifier.weight(1f))
                            Surface(color = PriOrangeLight, shape = RoundedCornerShape(20.dp)) {
                                Text(group.items.size.toString(), fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold, color = PriOrange,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // Items
                    group.items.forEachIndexed { itemIdx, item ->
                        val itemKey = "${group.categoryId}::${item.id}"
                        item(key = item.id) {
                            val isDragging = draggingItemKey == itemKey
                            val isHovered  = dragOverItemKey == itemKey && !isDragging
                            Card(
                                shape  = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDragging) PriOrangeLight
                                                     else if (isHovered) MaterialTheme.colorScheme.surfaceVariant
                                                     else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, if (isHovered) PriOrange else BorderBeige),
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 3.dp)
                                    .shadow(if (isDragging) 6.dp else 0.dp, RoundedCornerShape(12.dp))
                                    .pointerInput(itemKey) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart  = { draggingItemKey = itemKey },
                                            onDrag       = { change, _ -> change.consume(); dragOverItemKey = itemKey },
                                            onDragEnd    = { onItemDrop(group.categoryId) },
                                            onDragCancel = { draggingItemKey = null; dragOverItemKey = null }
                                        )
                                    }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("⠿", fontSize = 12.sp, color = BorderBeige)
                                    Text(item.name, fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold, color = TextBrown,
                                        modifier = Modifier.weight(1f), maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    if (item.qty > 0) {
                                        Text("${fmtQty(item.qty)} ${item.unit}".trim(),
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = PriOrange)
                                    }
                                    if (item.fromRecipeId != null) {
                                        Surface(color = PriOrangeLight, shape = RoundedCornerShape(4.dp)) {
                                            Text("📅", fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    IconButton(onClick = { undoItem = item; vm.deleteItem(item.id) },
                                        modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, null, tint = TextMuted,
                                            modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                    item(key = "sp_${group.categoryId}") { Spacer(Modifier.height(6.dp)) }
                }
                item(key = "bottom_pad") { Spacer(Modifier.height(72.dp)) }
            }
        }

        // ── Undo snackbar ───────────────────────────────────────
        AnimatedVisibility(
            visible  = undoItem != null,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Surface(color = Color(0xFF2C1A0E), shape = RoundedCornerShape(28.dp),
                shadowElevation = 10.dp, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.padding(start=18.dp, end=10.dp, top=10.dp, bottom=10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✅  ${undoItem?.name ?: ""} supprimé",
                        color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        undoItem?.let { vm.addManualItem(it.name, it.qty, it.unit); undoItem = null }
                    }, colors = ButtonDefaults.buttonColors(containerColor = PriOrange),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal=14.dp, vertical=6.dp)) {
                        Text("Annuler", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showShare) ShareSheet(vm.buildShareText(groups), { showShare = false }, context)
    if (showMenu) {
        AlertDialog(onDismissRequest = { showMenu = false },
            title = { Text("Actions") },
            text = {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(text: String, onDismiss: () -> Unit, context: Context) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start=20.dp, end=20.dp, bottom=40.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("📤 Partager la liste", fontWeight=FontWeight.ExtraBold, fontSize=18.sp)
            Surface(color=MaterialTheme.colorScheme.surfaceVariant, shape=RoundedCornerShape(12.dp)) {
                Text(text, fontSize=13.sp, lineHeight=22.sp,
                    modifier=Modifier.fillMaxWidth().padding(14.dp)
                        .heightIn(max=260.dp).verticalScroll(rememberScrollState()))
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

private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
