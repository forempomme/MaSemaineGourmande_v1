package com.masemainegourmande.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
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

private val CAT_EMOJI = mapOf(
    "Viandes" to "🥩", "Produits" to "🧀", "Œufs" to "🥚",
    "Fruits" to "🥦", "Féculents" to "🌾", "Épicerie" to "🫙",
    "Boissons" to "🥤", "Boulangerie" to "🥖", "Autre" to "🛍️"
)
private fun catEmoji(n: String) =
    CAT_EMOJI.entries.firstOrNull { n.startsWith(it.key) }?.value
        ?: n.firstOrNull()?.takeIf { it.code > 127 }?.toString() ?: "📦"

private val COMMON_UNITS = listOf("pcs", "g", "kg", "ml", "cl", "l", "c.s.", "c.c.", "botte", "sachet", "boîte")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val vmGroups   by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current
    val density     = LocalDensity.current

    // ── Local drag state ─────────────────────────────────────────
    val localGroups = remember { mutableStateListOf<ShoppingGroup>() }
    var isDragging  by remember { mutableStateOf(false) }
    LaunchedEffect(vmGroups) {
        if (!isDragging) { localGroups.clear(); localGroups.addAll(vmGroups) }
    }
    val localItems = remember { mutableStateMapOf<String, MutableList<ShoppingItemEntity>>() }
    LaunchedEffect(vmGroups) {
        if (!isDragging) vmGroups.forEach { g ->
            localItems[g.categoryId] = g.items.toMutableList()
        }
    }
    val catHeightPx  = with(density) { 48.dp.toPx() }
    val itemHeightPx = with(density) { 48.dp.toPx() }
    var catDragIdx   by remember { mutableStateOf<Int?>(null) }
    var catDeltaAcc  by remember { mutableFloatStateOf(0f) }
    val itemDragIdx  = remember { mutableStateMapOf<String, Int>() }
    val itemDeltaAcc = remember { mutableStateMapOf<String, Float>() }

    // ── Add bar state ─────────────────────────────────────────────
    var addName  by remember { mutableStateOf("") }
    var addQty   by remember { mutableStateOf("") }
    var addUnit  by remember { mutableStateOf("pcs") }
    var unitExpanded by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var showShare  by remember { mutableStateOf(false) }
    var showCatMgr by remember { mutableStateOf(false) }
    var undoItem   by remember { mutableStateOf<ShoppingItemEntity?>(null) }
    LaunchedEffect(undoItem) { if (undoItem != null) { delay(3000); undoItem = null } }

    fun addItem() {
        if (addName.isNotBlank()) {
            vm.addManualItem(addName.trim(), addQty.toDoubleOrNull() ?: 0.0, addUnit.trim())
            addName = ""; addQty = ""
        }
    }

    Box(Modifier.fillMaxSize().background(BgCream)) {
        LazyColumn(
            contentPadding = PaddingValues(start=14.dp, end=14.dp, top=12.dp, bottom=80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Page header ────────────────────────────────────────
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🛒", fontSize = 22.sp)
                    Text("Liste de courses", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                        color = TextBrown, modifier = Modifier.weight(1f))
                    // Article count badge
                    if (totalCount > 0) {
                        Surface(color = AccGreen, shape = CircleShape,
                            modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(totalCount.toString(), color = Color.White,
                                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    // Categories button
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderBeige),
                        modifier = Modifier.clickable { showCatMgr = true }) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(Icons.Default.Settings, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Text("Catégories", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                        }
                    }
                    // Share
                    if (vmGroups.isNotEmpty()) {
                        IconButton(onClick = { showShare = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── Inline add bar ─────────────────────────────────────
            item(key = "add_bar") {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderBeige)) {
                    Column(Modifier.padding(horizontal = 14.dp, top = 10.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ajouter manuellement", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = TextBrown)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = addName, onValueChange = { addName = it },
                                placeholder = { Text("Nom de l'article…", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(8.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PriOrange, unfocusedBorderColor = BorderBeige,
                                    focusedTextColor = TextBrown, unfocusedTextColor = TextBrown))
                            OutlinedTextField(
                                value = addQty, onValueChange = { addQty = it },
                                placeholder = { Text("Qté", fontSize = 12.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.width(56.dp).height(48.dp),
                                shape = RoundedCornerShape(8.dp), singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PriOrange, unfocusedBorderColor = BorderBeige,
                                    focusedTextColor = TextBrown, unfocusedTextColor = TextBrown))
                            // Unit dropdown
                            ExposedDropdownMenuBox(expanded = unitExpanded,
                                onExpandedChange = { unitExpanded = it },
                                modifier = Modifier.width(76.dp)) {
                                OutlinedTextField(value = addUnit, onValueChange = {},
                                    readOnly = true, singleLine = true,
                                    modifier = Modifier.menuAnchor().height(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PriOrange, unfocusedBorderColor = BorderBeige,
                                        focusedTextColor = TextBrown, unfocusedTextColor = TextBrown))
                                ExposedDropdownMenu(expanded = unitExpanded,
                                    onDismissRequest = { unitExpanded = false }) {
                                    COMMON_UNITS.forEach { u ->
                                        DropdownMenuItem(text = { Text(u, fontSize = 13.sp) },
                                            onClick = { addUnit = u; unitExpanded = false })
                                    }
                                }
                            }
                            // + button
                            Surface(color = if (addName.isNotBlank()) PriOrange else PriOrangeLight,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(48.dp).clickable(enabled = addName.isNotBlank()) { addItem() }) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null,
                                        tint = if (addName.isNotBlank()) Color.White else PriOrange,
                                        modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Empty ─────────────────────────────────────────────
            if (localGroups.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛒", fontSize = 52.sp)
                        Text("Liste vide", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextBrown)
                        Text("Planifiez des repas ou ajoutez des articles manuellement",
                            fontSize = 13.sp, color = TextMuted, textAlign = TextAlign.Center)
                    }
                }
            } else {
                // ── Category groups ────────────────────────────────
                localGroups.forEachIndexed { catIdx, group ->
                    item(key = "grp_${group.categoryId}") {
                        val catItems  = localItems[group.categoryId] ?: group.items
                        val isCatDrag = catDragIdx == catIdx

                        Card(
                            shape  = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, if (isCatDrag) PriOrange else BorderBeige),
                            modifier = Modifier.fillMaxWidth()
                                .shadow(if (isCatDrag) 6.dp else 0.dp, RoundedCornerShape(14.dp))
                        ) {
                            // Category header — long-press to drag
                            Row(
                                Modifier.fillMaxWidth()
                                    .pointerInput(group.categoryId) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                isDragging = true
                                                catDragIdx = localGroups.indexOfFirst { it.categoryId == group.categoryId }
                                                catDeltaAcc = 0f
                                            },
                                            onDrag = { ch, drag ->
                                                ch.consume(); catDeltaAcc += drag.y
                                                val steps = (catDeltaAcc / catHeightPx).toInt()
                                                if (steps != 0) {
                                                    val from = catDragIdx ?: return@detectDragGesturesAfterLongPress
                                                    val to = (from + steps).coerceIn(0, localGroups.size - 1)
                                                    if (to != from) {
                                                        localGroups.add(to, localGroups.removeAt(from))
                                                        catDragIdx = to; catDeltaAcc -= steps * catHeightPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                localGroups.forEachIndexed { i, g -> vm.reorderCategoryByIndex(g.categoryId, i) }
                                                catDragIdx = null; isDragging = false
                                            },
                                            onDragCancel = {
                                                catDragIdx = null; isDragging = false
                                                localGroups.clear(); localGroups.addAll(vmGroups)
                                            }
                                        )
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⠿", fontSize = 14.sp, color = TextMuted.copy(alpha = 0.4f))
                                Text(catEmoji(group.categoryName), fontSize = 16.sp)
                                Text(group.categoryName, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = TextBrown, modifier = Modifier.weight(1f))
                                Surface(color = AccGreenLight, shape = RoundedCornerShape(8.dp)) {
                                    Text(catItems.size.toString(), fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold, color = AccGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }

                            HorizontalDivider(color = BorderBeige, thickness = 1.dp)

                            // Items
                            catItems.forEachIndexed { itemIdx, item ->
                                val isItemDrag = itemDragIdx[group.categoryId] == itemIdx
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if (isItemDrag) PriOrangeLight else Color.Transparent)
                                        .pointerInput("${group.categoryId}_$itemIdx") {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    isDragging = true
                                                    itemDragIdx[group.categoryId] = itemIdx
                                                    itemDeltaAcc[group.categoryId] = 0f
                                                },
                                                onDrag = { ch, drag ->
                                                    ch.consume()
                                                    val prev = itemDeltaAcc[group.categoryId] ?: 0f
                                                    val next = prev + drag.y
                                                    itemDeltaAcc[group.categoryId] = next
                                                    val steps = (next / itemHeightPx).toInt()
                                                    if (steps != 0) {
                                                        val curFrom = itemDragIdx[group.categoryId] ?: return@detectDragGesturesAfterLongPress
                                                        val list = localItems[group.categoryId] ?: return@detectDragGesturesAfterLongPress
                                                        val curTo = (curFrom + steps).coerceIn(0, list.size - 1)
                                                        if (curTo != curFrom) {
                                                            list.add(curTo, list.removeAt(curFrom))
                                                            itemDragIdx[group.categoryId] = curTo
                                                            itemDeltaAcc[group.categoryId] = next - steps * itemHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    localItems[group.categoryId]?.let {
                                                        vm.setItemOrder(group.categoryId, it.map { i -> i.id })
                                                    }
                                                    itemDragIdx.remove(group.categoryId); isDragging = false
                                                },
                                                onDragCancel = {
                                                    itemDragIdx.remove(group.categoryId); isDragging = false
                                                    localItems[group.categoryId] = vmGroups
                                                        .find { it.categoryId == group.categoryId }
                                                        ?.items?.toMutableList() ?: mutableListOf()
                                                }
                                            )
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("⠿", fontSize = 12.sp, color = BorderBeige)
                                    // Name centered
                                    Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                        color = TextBrown, modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    // Quantity: number orange bold + unit normal
                                    if (item.qty > 0 || item.unit.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.Baseline,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (item.qty > 0) {
                                                Text(fmtQty(item.qty), fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold, color = PriOrange)
                                            }
                                            if (item.unit.isNotBlank()) {
                                                Text(item.unit, fontSize = 13.sp, color = TextBrown)
                                            }
                                        }
                                    }
                                    // Planning badge
                                    if (item.fromRecipeId != null) {
                                        Text("📅", fontSize = 10.sp, color = TextMuted)
                                    }
                                    // ✓ green circle — tap to remove with undo
                                    Surface(color = AccGreenLight, shape = CircleShape,
                                        border = BorderStroke(2.dp, AccGreen),
                                        modifier = Modifier.size(30.dp)
                                            .clickable { undoItem = item; vm.deleteItem(item.id) }) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("✓", fontSize = 14.sp, color = AccGreen,
                                                fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                                if (itemIdx < catItems.size - 1) {
                                    HorizontalDivider(color = BorderBeige.copy(alpha = 0.5f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Undo ─────────────────────────────────────────────────
        AnimatedVisibility(visible = undoItem != null,
            enter = slideInVertically { it } + fadeIn(),
            exit  = slideOutVertically { it } + fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)) {
            Surface(color = Color(0xFF2C1A0E), shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.padding(start=16.dp, end=10.dp, top=10.dp, bottom=10.dp),
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

    if (showShare) ShareSheet(vm.buildShareText(localGroups.toList()), { showShare = false }, context)
    if (showCatMgr) CategoriesManagerDialog(vm = vm, onDismiss = { showCatMgr = false })
}

// ─── Categories manager dialog (inline in shopping) ──────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesManagerDialog(vm: ShoppingViewModel, onDismiss: () -> Unit) {
    val groups by vm.groups.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("⚙️ Catégories", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (groups.isEmpty()) Text("Aucune catégorie pour l'instant.", color = TextMuted)
                else groups.forEach { g ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(catEmoji(g.categoryName), fontSize = 16.sp,
                            modifier = Modifier.padding(end = 8.dp))
                        Text(g.categoryName, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                            modifier = Modifier.weight(1f))
                        Text("${g.items.size}", fontSize = 12.sp, color = TextMuted)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Gérez les catégories dans Options → Catégories.",
                    fontSize = 11.sp, color = TextMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

// ─── Share ────────────────────────────────────────────────────────

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
