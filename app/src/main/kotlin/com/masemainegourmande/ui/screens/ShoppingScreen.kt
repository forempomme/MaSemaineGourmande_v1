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

private val CAT_EMOJI = mapOf(
    "Viandes" to "🥩", "Produits" to "🧀", "Œufs" to "🥚",
    "Fruits" to "🥦", "Féculents" to "🌾", "Épicerie" to "🫙",
    "Boissons" to "🥤", "Boulangerie" to "🥖", "Autre" to "🛍️"
)
private fun catEmoji(n: String) =
    CAT_EMOJI.entries.firstOrNull { n.startsWith(it.key) }?.value ?: "📦"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val vmGroups   by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current
    val density     = LocalDensity.current

    // ── Local drag-drop state — never reset during drag ─────────────────────
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

    val catHeightPx  = with(density) { 44.dp.toPx() }
    val itemHeightPx = with(density) { 46.dp.toPx() }
    var catDragIdx   by remember { mutableStateOf<Int?>(null) }
    var catDeltaAcc  by remember { mutableFloatStateOf(0f) }
    val itemDragIdx  = remember { mutableStateMapOf<String, Int>() }
    val itemDeltaAcc = remember { mutableStateMapOf<String, Float>() }

    var showAdd   by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var undoItem  by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    LaunchedEffect(undoItem) { if (undoItem != null) { delay(3000); undoItem = null } }

    Box(Modifier.fillMaxSize().background(BgCream)) {
        LazyColumn(
            contentPadding = PaddingValues(all = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── Action buttons (mirrors JSX top row) ──────────────
            item(key = "top_bar") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "+ Ajouter un article" — opens modal (like JSX)
                    Button(
                        onClick  = { showAdd = true },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
                    ) {
                        Text("+ Ajouter un article", fontWeight = FontWeight.Bold)
                    }
                    // Share button
                    Surface(color = PriOrangeLight, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(44.dp).clickable { showShare = true }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Share, null, tint = PriOrange, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Empty state ───────────────────────────────────────
            if (localGroups.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🛒", fontSize = 52.sp)
                        Text("Liste vide", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextBrown)
                        Text("Planifiez des repas ou ajoutez des articles manuellement",
                            fontSize = 13.sp, color = TextMuted, fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center)
                    }
                }
            } else {
                // ── Category groups ────────────────────────────────
                localGroups.forEachIndexed { catIdx, group ->
                    item(key = "grp_${group.categoryId}") {
                        val catItems = localItems[group.categoryId] ?: group.items
                        val isDragCat = catDragIdx == catIdx

                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                .shadow(if (isDragCat) 4.dp else 0.dp, RoundedCornerShape(8.dp))
                        ) {
                            // Category header — long-press to drag whole category
                            Row(
                                Modifier.fillMaxWidth()
                                    .pointerInput(catIdx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                isDragging = true; catDragIdx = catIdx; catDeltaAcc = 0f
                                            },
                                            onDrag = { ch, drag ->
                                                ch.consume()
                                                catDeltaAcc += drag.y
                                                val steps = (catDeltaAcc / catHeightPx).toInt()
                                                if (steps != 0) {
                                                    val from = catDragIdx ?: return@detectDragGesturesAfterLongPress
                                                    val to = (from + steps).coerceIn(0, localGroups.size - 1)
                                                    if (to != from) {
                                                        val mv = localGroups.removeAt(from)
                                                        localGroups.add(to, mv)
                                                        catDragIdx = to
                                                        catDeltaAcc -= steps * catHeightPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                localGroups.forEachIndexed { i, g ->
                                                    vm.reorderCategoryByIndex(g.categoryId, i)
                                                }
                                                catDragIdx = null; isDragging = false
                                            },
                                            onDragCancel = {
                                                catDragIdx = null; isDragging = false
                                                localGroups.clear(); localGroups.addAll(vmGroups)
                                            }
                                        )
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("⠿", fontSize = 14.sp, color = TextMuted.copy(alpha = 0.4f))
                                Text(catEmoji(group.categoryName), fontSize = 14.sp)
                                Text(group.categoryName.uppercase(),
                                    fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                                    color = TextMuted, letterSpacing = 0.8.sp,
                                    modifier = Modifier.weight(1f))
                                Surface(color = PriOrangeLight, shape = RoundedCornerShape(10.dp)) {
                                    Text(catItems.size.toString(), fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold, color = PriOrange,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp))
                                }
                            }

                            // Items within category — each independently draggable
                            catItems.forEachIndexed { itemIdx, item ->
                                val isItemDrag = itemDragIdx[group.categoryId] == itemIdx
                                Card(
                                    shape  = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isItemDrag) PriOrangeLight
                                                         else MaterialTheme.colorScheme.surface
                                    ),
                                    border = BorderStroke(1.dp, if (isItemDrag) PriOrange else BorderBeige),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                        .shadow(if (isItemDrag) 5.dp else 0.dp, RoundedCornerShape(12.dp))
                                        .pointerInput("i_${group.categoryId}_$itemIdx") {
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
                                                        val curFrom = itemDragIdx[group.categoryId]
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        val list = localItems[group.categoryId]
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        val curTo = (curFrom + steps).coerceIn(0, list.size - 1)
                                                        if (curTo != curFrom) {
                                                            val mv = list.removeAt(curFrom)
                                                            list.add(curTo, mv)
                                                            itemDragIdx[group.categoryId] = curTo
                                                            itemDeltaAcc[group.categoryId] = next - steps * itemHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    localItems[group.categoryId]?.let {
                                                        vm.setItemOrder(group.categoryId, it.map { i -> i.id })
                                                    }
                                                    itemDragIdx.remove(group.categoryId)
                                                    isDragging = false
                                                },
                                                onDragCancel = {
                                                    itemDragIdx.remove(group.categoryId); isDragging = false
                                                    localItems[group.categoryId] = vmGroups
                                                        .find { it.categoryId == group.categoryId }
                                                        ?.items?.toMutableList() ?: mutableListOf()
                                                }
                                            )
                                        }
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Drag handle
                                        Text("⠿", fontSize = 12.sp, color = BorderBeige)
                                        // ✓ Check/delete button (round, accent green — like JSX)
                                        Surface(
                                            color  = AccGreenLight,
                                            shape  = RoundedCornerShape(50),
                                            border = BorderStroke(2.dp, AccGreen),
                                            modifier = Modifier.size(28.dp).clickable {
                                                undoItem = item; vm.deleteItem(item.id)
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("✓", fontSize = 13.sp, color = AccGreen,
                                                    fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
                                        // Name
                                        Text(item.name, fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold, color = TextBrown,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        // Quantity — right aligned
                                        if (item.qty > 0) {
                                            Text("${fmtQty(item.qty)} ${item.unit}".trim(),
                                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                                color = PriOrange)
                                        }
                                        // Planning badge
                                        if (item.fromRecipeId != null) {
                                            Surface(color = PriOrangeLight, shape = RoundedCornerShape(5.dp)) {
                                                Text("📅", fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Undo bar ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = undoItem != null,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        ) {
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

    // ── Modals ─────────────────────────────────────────────────────
    if (showAdd) {
        AddManualItemDialog(
            onDismiss = { showAdd = false },
            onAdd     = { n, q, u -> vm.addManualItem(n, q, u); showAdd = false }
        )
    }
    if (showShare) {
        ShareSheet(vm.buildShareText(localGroups.toList()), { showShare = false }, context)
    }
}

// ─── Add manual item dialog ───────────────────────────────────────

@Composable
fun AddManualItemDialog(onDismiss: () -> Unit, onAdd: (String, Double, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty  by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Ajouter un article", fontWeight = FontWeight.ExtraBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Article *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PriOrange))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it },
                        label = { Text("Quantité") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PriOrange))
                    OutlinedTextField(value = unit, onValueChange = { unit = it },
                        label = { Text("Unité") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PriOrange))
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onAdd(name.trim(), qty.toDoubleOrNull() ?: 0.0, unit.trim()) },
                enabled = name.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                Text("Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// ─── Share sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(text: String, onDismiss: () -> Unit, context: Context) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start=20.dp, end=20.dp, bottom=40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("📤 Partager la liste", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(text, fontSize = 13.sp, lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                        .heightIn(max = 260.dp).verticalScroll(rememberScrollState()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("courses", text)); copied = true
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copié !" else "Copier")
                }
                Button(onClick = {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
                        "Partager"))
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Partager")
                }
            }
        }
    }
}

private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
