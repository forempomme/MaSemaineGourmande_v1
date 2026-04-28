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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
private fun catEmoji(n: String) = CAT_EMOJI.entries.firstOrNull { n.startsWith(it.key) }?.value ?: "📦"

// Couleur de fond des cartes catégorie (légèrement plus claire que BgCream)
private val CardBg = Color(0xFF1A2035)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val vmGroups   by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current
    val haptic      = LocalHapticFeedback.current
    val density     = LocalDensity.current

    // ── localGroups + localCatOrder : protection contre race condition Room ──
    val localGroups   = remember { mutableStateListOf<ShoppingGroup>() }
    var localCatOrder by remember { mutableStateOf<List<String>?>(null) }
    var isDragging    by remember { mutableStateOf(false) }

    LaunchedEffect(vmGroups) {
        if (!isDragging) {
            if (localCatOrder == null) {
                localGroups.clear(); localGroups.addAll(vmGroups)
            } else {
                val map = vmGroups.associateBy { it.categoryId }
                val ordered = localCatOrder!!.mapNotNull { map[it] }
                    .plus(vmGroups.filter { it.categoryId !in localCatOrder!! })
                localGroups.clear(); localGroups.addAll(ordered)
            }
        }
    }

    val localItems = remember { mutableStateMapOf<String, MutableList<ShoppingItemEntity>>() }
    LaunchedEffect(vmGroups) {
        if (!isDragging) vmGroups.forEach { g -> localItems[g.categoryId] = g.items.toMutableList() }
    }

    // Drag state
    val catHeightPx     = with(density) { 46.dp.toPx() }
    val itemHeightPx    = with(density) { 44.dp.toPx() }
    var catDragIdx      by remember { mutableStateOf<Int?>(null) }
    var catDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var catDeltaAcc     by remember { mutableFloatStateOf(0f) }
    val itemDragIdx     = remember { mutableStateMapOf<String, Int>() }
    val itemDeltaAcc    = remember { mutableStateMapOf<String, Float>() }

    // Add bar
    var addName  by remember { mutableStateOf("") }
    var addQty   by remember { mutableStateOf("") }
    var addUnit  by remember { mutableStateOf("") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var showShare by remember { mutableStateOf(false) }
    var showMenu  by remember { mutableStateOf(false) }
    var undoItem  by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    LaunchedEffect(undoItem) { if (undoItem != null) { delay(3000); undoItem = null } }

    fun addItem() {
        if (addName.isNotBlank()) {
            vm.addManualItem(addName.trim(), addQty.toDoubleOrNull() ?: 0.0, addUnit.trim())
            addName = ""; addQty = ""; addUnit = ""
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor      = PriOrange,  unfocusedBorderColor    = BorderBeige,
        focusedTextColor        = TextBrown,  unfocusedTextColor      = TextBrown,
        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedPlaceholderColor = TextMuted,  unfocusedPlaceholderColor = TextMuted
    )

    Box(Modifier.fillMaxSize().background(BgCream)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // ── Section header ─────────────────────────────────────────
            item(key = "action_bar") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🛒", fontSize = 22.sp)
                    Text("Liste de courses",
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                        color = TextBrown, modifier = Modifier.weight(1f))
                    // Badge count vert
                    if (totalCount > 0) {
                        Surface(color = AccGreen, shape = CircleShape,
                            modifier = Modifier.size(30.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(totalCount.toString(), fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                    }
                    IconButton(onClick = { showShare = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Share, null, tint = TextBrown, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.MoreVert, null, tint = TextBrown, modifier = Modifier.size(18.dp)) }
                }
                HorizontalDivider(color = BorderBeige.copy(alpha = 0.3f))
            }

            // ── Add bar ───────────────────────────────────────────────
            item(key = "add_bar") {
                Card(
                    shape  = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text("Ajouter manuellement",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextBrown, modifier = Modifier.padding(bottom = 8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            // Nom
                            OutlinedTextField(
                                value = addName, onValueChange = { addName = it },
                                placeholder = { Text("Nom de l'article…", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(8.dp), singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp, color = TextBrown,
                                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                colors = fieldColors)
                            // Quantité
                            OutlinedTextField(
                                value = addQty, onValueChange = { addQty = it },
                                placeholder = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("Qté", fontSize = 10.sp) }},
                                modifier = Modifier.width(56.dp).height(48.dp),
                                shape = RoundedCornerShape(8.dp), singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.sp, color = TextBrown,
                                    textAlign = TextAlign.Center,
                                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                                colors = fieldColors)
                            // Unité — dropdown, vide par défaut
                            var unitMenuOpen by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = unitMenuOpen, onExpandedChange = { unitMenuOpen = it },
                                modifier = Modifier.width(72.dp)) {
                                OutlinedTextField(
                                    value = addUnit, onValueChange = {},
                                    readOnly = true, singleLine = true,
                                    placeholder = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text("U.", fontSize = 11.sp, color = TextMuted) }},
                                    modifier = Modifier.menuAnchor().height(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 12.sp, color = TextBrown,
                                        textAlign = TextAlign.Center,
                                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitMenuOpen) },
                                    colors = fieldColors)
                                ExposedDropdownMenu(expanded = unitMenuOpen,
                                    onDismissRequest = { unitMenuOpen = false }) {
                                    listOf("","pcs","g","kg","ml","cl","l","c.s.","c.c.",
                                        "botte","sachet","boîte","tranche").forEach { u ->
                                        DropdownMenuItem(
                                            text = { Text(if (u.isEmpty()) "— aucune —" else u, fontSize = 13.sp) },
                                            onClick = { addUnit = u; unitMenuOpen = false })
                                    }
                                }
                            }
                            // Bouton +
                            Surface(
                                color    = if (addName.isNotBlank()) PriOrange else BorderBeige,
                                shape    = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(48.dp).clickable(enabled = addName.isNotBlank()) { addItem() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, null,
                                        tint = if (addName.isNotBlank()) Color.White else TextMuted,
                                        modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = BorderBeige)
                Spacer(Modifier.height(10.dp))
            }

            // ── Empty state ────────────────────────────────────────────
            if (localGroups.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛒", fontSize = 52.sp)
                        Text("Liste vide", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextBrown)
                        Text("Planifiez des repas ou ajoutez\ndes articles manuellement",
                            fontSize = 13.sp, color = TextMuted, fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center)
                    }
                }
            } else {
                // ── Category groups ────────────────────────────────────
                localGroups.forEachIndexed { catIdx, group ->
                    item(key = "grp_${group.categoryId}") {
                        val catItems      = localItems[group.categoryId] ?: group.items
                        val isCatDragging = catDragIdx == catIdx

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .graphicsLayer {
                                    translationY  = if (isCatDragging) catDragOffsetPx else 0f
                                    shadowElevation = if (isCatDragging) 16f else 0f
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            // ── Category header — draggable ──────────────
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pointerInput(group.categoryId) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isDragging = true; catDragIdx = catIdx
                                                catDragOffsetPx = 0f; catDeltaAcc = 0f
                                            },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                catDragOffsetPx += drag.y
                                                catDeltaAcc += drag.y
                                                val steps = (catDeltaAcc / catHeightPx).toInt()
                                                if (steps != 0) {
                                                    val from = catDragIdx ?: return@detectDragGesturesAfterLongPress
                                                    val to = (from + steps).coerceIn(0, localGroups.size - 1)
                                                    if (to != from) {
                                                        val moved = localGroups.removeAt(from)
                                                        localGroups.add(to, moved)
                                                        catDragIdx = to
                                                        catDeltaAcc -= steps * catHeightPx
                                                        catDragOffsetPx -= steps * catHeightPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                val orderedIds = localGroups.map { it.categoryId }
                                                localCatOrder = orderedIds
                                                vm.reorderAllCategories(orderedIds)
                                                catDragIdx = null; catDragOffsetPx = 0f; isDragging = false
                                            },
                                            onDragCancel = {
                                                catDragIdx = null; catDragOffsetPx = 0f; isDragging = false
                                                localGroups.clear(); localGroups.addAll(vmGroups)
                                            }
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⠿", fontSize = 14.sp, color = TextBrown.copy(alpha = 0.4f))
                                Text(catEmoji(group.categoryName), fontSize = 16.sp)
                                Text(group.categoryName.uppercase(),
                                    fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
                                    color = TextBrown, letterSpacing = 0.5.sp,
                                    modifier = Modifier.weight(1f))
                                // Badge count — cercle outline vert (même style que ✓)
                                Surface(
                                    color  = Color.Transparent,
                                    shape  = CircleShape,
                                    border = BorderStroke(1.5.dp, AccGreen),
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(catItems.size.toString(),
                                            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                                            color = AccGreen)
                                    }
                                }
                            }

                            // ── Items (dans la même carte, sans card individuelle) ──
                            catItems.forEachIndexed { itemIdx, item ->
                                val isItemDragging = itemDragIdx[group.categoryId] == itemIdx

                                HorizontalDivider(
                                    color = BorderBeige.copy(alpha = 0.3f),
                                    thickness = 0.5.dp)

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            translationY = if (isItemDragging)
                                                (itemDeltaAcc[group.categoryId] ?: 0f) else 0f
                                            shadowElevation = if (isItemDragging) 8f else 0f
                                        }
                                        .background(
                                            if (isItemDragging) AccGreenLight.copy(alpha = 0.15f)
                                            else Color.Transparent)
                                        .pointerInput("drag_${item.id}") {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    isDragging = true
                                                    itemDragIdx[group.categoryId] = itemIdx
                                                    itemDeltaAcc[group.categoryId] = 0f
                                                },
                                                onDrag = { change, drag ->
                                                    change.consume()
                                                    val prev = itemDeltaAcc[group.categoryId] ?: 0f
                                                    val next = prev + drag.y
                                                    itemDeltaAcc[group.categoryId] = next
                                                    val steps = (next / itemHeightPx).toInt()
                                                    if (steps != 0) {
                                                        val curFrom = itemDragIdx[group.categoryId] ?: return@detectDragGesturesAfterLongPress
                                                        val items = localItems[group.categoryId] ?: return@detectDragGesturesAfterLongPress
                                                        val curTo = (curFrom + steps).coerceIn(0, items.size - 1)
                                                        if (curTo != curFrom) {
                                                            val moved = items.removeAt(curFrom)
                                                            items.add(curTo, moved)
                                                            itemDragIdx[group.categoryId] = curTo
                                                            itemDeltaAcc[group.categoryId] = next - steps * itemHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    val items = localItems[group.categoryId]
                                                    if (items != null) vm.setItemOrder(group.categoryId, items.map { it.id })
                                                    itemDragIdx.remove(group.categoryId)
                                                    isDragging = false
                                                },
                                                onDragCancel = {
                                                    itemDragIdx.remove(group.categoryId)
                                                    isDragging = false
                                                    localItems[group.categoryId] = vmGroups
                                                        .find { it.categoryId == group.categoryId }
                                                        ?.items?.toMutableList() ?: mutableListOf()
                                                }
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Nom — centré
                                    Text(item.name,
                                        fontSize = 14.sp, fontWeight = FontWeight.Normal,
                                        color = TextBrown,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    // Quantité + unité
                                    if (item.qty > 0 || item.unit.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (item.qty > 0) Text(fmtQty(item.qty),
                                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                                color = PriOrange)
                                            if (item.unit.isNotBlank()) Text(item.unit,
                                                fontSize = 12.sp, color = TextMuted,
                                                fontWeight = FontWeight.Normal)
                                        }
                                    }
                                    // 📅 indicateur planning
                                    if (item.fromRecipeId != null) {
                                        Text("📅", fontSize = 11.sp)
                                    }
                                    // ✓ cercle vert (supprime avec undo)
                                    Surface(
                                        color    = Color.Transparent,
                                        shape    = CircleShape,
                                        border   = BorderStroke(1.5.dp, AccGreen),
                                        modifier = Modifier.size(28.dp).clickable {
                                            undoItem = item; vm.deleteItem(item.id)
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("✓", fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold, color = AccGreen)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Undo ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = undoItem != null,
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Surface(color = Color(0xFF2C1A0E), shape = RoundedCornerShape(28.dp),
                shadowElevation = 10.dp, modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✅  ${undoItem?.name ?: ""} supprimé",
                        color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Button(
                        onClick = { undoItem?.let { vm.addManualItem(it.name, it.qty, it.unit); undoItem = null } },
                        colors = ButtonDefaults.buttonColors(containerColor = PriOrange),
                        shape  = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("Annuler", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showShare) ShareSheet(vm.buildShareText(localGroups.toList()), { showShare = false }, context)
    if (showMenu) AlertDialog(
        onDismissRequest = { showMenu = false },
        title   = { Text("Actions") },
        text    = { Column {
            TextButton(onClick = { vm.clearChecked(); showMenu = false }) { Text("✓ Vider les cochés") }
            TextButton(onClick = { vm.clearAll(); showMenu = false },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("🗑 Tout vider") }
        }},
        confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Fermer") } },
        shape = RoundedCornerShape(16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(text: String, onDismiss: () -> Unit, context: Context) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("📤 Partager la liste", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(text, fontSize = 13.sp, lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().padding(14.dp).heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("courses", text)); copied = true
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (copied) "Copié!" else "Copier")
                }
                Button(onClick = {
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply { type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text) }, "Partager"))
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
