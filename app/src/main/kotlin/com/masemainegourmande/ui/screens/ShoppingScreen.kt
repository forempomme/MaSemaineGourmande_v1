package com.masemainegourmande.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
private fun catEmoji(name: String) =
    CAT_EMOJI.entries.firstOrNull { name.startsWith(it.key) }?.value ?: "📦"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val groups     by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current
    val density     = LocalDensity.current

    var addName  by remember { mutableStateOf("") }
    var addQty   by remember { mutableStateOf("") }
    var addUnit  by remember { mutableStateOf("") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var showShare by remember { mutableStateOf(false) }
    var showMenu  by remember { mutableStateOf(false) }
    var undoItem  by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    // ── Local mutable state for drag-drop ────────────────────────────────────
    // We keep a fully local snapshot; LaunchedEffect only syncs when NOT dragging
    val localGroups = remember { mutableStateListOf<ShoppingGroup>() }
    var isDragging  by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        if (!isDragging) {
            localGroups.clear()
            localGroups.addAll(groups)
        }
    }

    // Category drag state
    val catHeightPx  = with(density) { 44.dp.toPx() }
    var catDragIdx   by remember { mutableStateOf<Int?>(null) }
    var catDragDelta by remember { mutableFloatStateOf(0f) }

    // Item drag state  (per category id → dragging item index)
    val itemHeightPx = with(density) { 46.dp.toPx() }
    val itemDragIdx  = remember { mutableStateMapOf<String, Int>() }   // catId → idx
    val itemDragDelta= remember { mutableStateMapOf<String, Float>() } // catId → accumulated px

    LaunchedEffect(undoItem) {
        if (undoItem != null) { delay(3000); undoItem = null }
    }

    fun addItem() {
        if (addName.isNotBlank()) {
            vm.addManualItem(addName.trim(), addQty.toDoubleOrNull() ?: 0.0, addUnit.trim())
            addName = ""; addQty = ""; addUnit = ""
        }
    }

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor        = PriOrange,
        unfocusedBorderColor      = BorderBeige,
        focusedTextColor          = Color(0xFF1A1A1A),
        unfocusedTextColor        = Color(0xFF1A1A1A),
        focusedContainerColor     = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor   = MaterialTheme.colorScheme.surface,
        focusedPlaceholderColor   = TextMuted,
        unfocusedPlaceholderColor = TextMuted
    )

    Box(Modifier.fillMaxSize().background(BgCream)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // ── 1. Add bar (compact, on top) ───────────────────────
            item(key = "add_bar") {
                Row(
                    Modifier.background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = addName, onValueChange = { addName = it },
                        placeholder = { Text("Article…", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1A1A1A)),
                        keyboardOptions = KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        ), colors = inputColors
                    )
                    OutlinedTextField(
                        value = addQty, onValueChange = { addQty = it },
                        placeholder = { Text("Qté", fontSize = 10.sp) },
                        modifier = Modifier.width(48.dp).height(44.dp),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1A1A1A)),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        ), colors = inputColors
                    )
                    OutlinedTextField(
                        value = addUnit, onValueChange = { addUnit = it },
                        placeholder = { Text("U.", fontSize = 10.sp) },
                        modifier = Modifier.width(46.dp).height(44.dp),
                        shape = RoundedCornerShape(8.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF1A1A1A)),
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addItem(); keyboard?.hide() }),
                        colors = inputColors
                    )
                    Surface(
                        color    = if (addName.isNotBlank()) PriOrange else PriOrangeLight,
                        shape    = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(44.dp).clickable(enabled = addName.isNotBlank()) { addItem() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null,
                                tint = if (addName.isNotBlank()) Color.White else PriOrange,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
                HorizontalDivider(color = BorderBeige)
            }

            // ── 2. Action bar (full-width, taller) ────────────────
            item(key = "action_bar") {
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (totalCount > 0) "🛒  $totalCount article${if (totalCount > 1) "s" else ""}"
                        else "🛒  Liste vide",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = TextBrown, modifier = Modifier.weight(1f)
                    )
                    if (groups.isNotEmpty()) {
                        IconButton(onClick = { showShare = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Share, null, tint = PriOrange, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = PriOrange, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                HorizontalDivider(color = BorderBeige)
                Spacer(Modifier.height(8.dp))
            }

            // ── 3. Empty state ────────────────────────────────────
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
                // ── 4. Category cards with drag-drop ──────────────
                localGroups.forEachIndexed { catIdx, group ->
                    item(key = "cat_${group.categoryId}") {
                        val isCatBeingDragged = catDragIdx == catIdx
                        val catScale by animateFloatAsState(
                            targetValue = if (isCatBeingDragged) 1.03f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "catScale"
                        )
                        val catAlpha by animateFloatAsState(
                            targetValue = if (isCatBeingDragged) 0.92f else 1f,
                            label = "catAlpha"
                        )

                        Card(
                            shape  = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, if (isCatBeingDragged) PriOrange else BorderBeige),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .scale(catScale)
                                .alpha(catAlpha)
                                .shadow(if (isCatBeingDragged) 10.dp else 1.dp, RoundedCornerShape(14.dp))
                        ) {
                            // Category header — BgCream bg, left aligned
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(BgCream)
                                    .pointerInput(catIdx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                isDragging = true
                                                catDragIdx = catIdx
                                                catDragDelta = 0f
                                            },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                catDragDelta += drag.y
                                                val steps = (catDragDelta / catHeightPx).toInt()
                                                if (steps != 0 && catDragIdx != null) {
                                                    val from = catDragIdx!!
                                                    val to = (from + steps).coerceIn(0, localGroups.size - 1)
                                                    if (to != from) {
                                                        val moved = localGroups.removeAt(from)
                                                        localGroups.add(to, moved)
                                                        catDragIdx = to
                                                        catDragDelta -= steps * catHeightPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                // Persist to VM: update sortOrder of each category
                                                localGroups.forEachIndexed { i, g ->
                                                    groups.find { it.categoryId == g.categoryId }?.let {
                                                        vm.reorderCategoryByIndex(g.categoryId, i)
                                                    }
                                                }
                                                catDragIdx = null
                                                isDragging = false
                                            },
                                            onDragCancel = {
                                                catDragIdx = null
                                                isDragging = false
                                                localGroups.clear()
                                                localGroups.addAll(groups)
                                            }
                                        )
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("⠿", fontSize = 14.sp, color = TextBrown.copy(alpha = 0.5f))
                                Text(catEmoji(group.categoryName), fontSize = 14.sp)
                                Text(group.categoryName.uppercase(),
                                    fontWeight = FontWeight.ExtraBold, fontSize = 10.sp,
                                    color = TextBrown, letterSpacing = 0.8.sp,
                                    modifier = Modifier.weight(1f))
                                Surface(color = PriOrange, shape = RoundedCornerShape(20.dp)) {
                                    Text(group.items.size.toString(), fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold, color = Color.White,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                                }
                            }

                            HorizontalDivider(color = BorderBeige, thickness = 1.dp)

                            // Items — surface bg, no gap between them
                            // Use local snapshot for item order
                            val localItems = remember(group.categoryId) {
                                mutableStateListOf<ShoppingItemEntity>().also { it.addAll(group.items) }
                            }
                            LaunchedEffect(group.items) {
                                if (itemDragIdx[group.categoryId] == null) {
                                    localItems.clear(); localItems.addAll(group.items)
                                }
                            }

                            localItems.forEachIndexed { itemIdx, item ->
                                val isItemDragged = itemDragIdx[group.categoryId] == itemIdx
                                val itemScale by animateFloatAsState(
                                    targetValue = if (isItemDragged) 1.02f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    label = "itemScale_$itemIdx"
                                )
                                val itemElev by animateDpAsState(
                                    targetValue = if (isItemDragged) 6.dp else 0.dp,
                                    label = "itemElev_$itemIdx"
                                )

                                Row(
                                    Modifier.fillMaxWidth()
                                        .shadow(itemElev, RoundedCornerShape(0.dp))
                                        .background(
                                            if (isItemDragged) PriOrangeLight
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .scale(itemScale)
                                        .pointerInput("item_${group.categoryId}_$itemIdx") {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    isDragging = true
                                                    itemDragIdx[group.categoryId] = itemIdx
                                                    itemDragDelta[group.categoryId] = 0f
                                                },
                                                onDrag = { change, drag ->
                                                    change.consume()
                                                    val prev = itemDragDelta[group.categoryId] ?: 0f
                                                    val next = prev + drag.y
                                                    itemDragDelta[group.categoryId] = next
                                                    val steps = (next / itemHeightPx).toInt()
                                                    if (steps != 0) {
                                                        val curFrom = itemDragIdx[group.categoryId] ?: return@detectDragGesturesAfterLongPress
                                                        val curTo = (curFrom + steps).coerceIn(0, localItems.size - 1)
                                                        if (curTo != curFrom) {
                                                            val moved = localItems.removeAt(curFrom)
                                                            localItems.add(curTo, moved)
                                                            itemDragIdx[group.categoryId] = curTo
                                                            itemDragDelta[group.categoryId] = next - steps * itemHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    // Persist new item order to VM
                                                    val fromIdx = itemDragIdx[group.categoryId]
                                                    val origItem = if (fromIdx != null && fromIdx < localItems.size) localItems[fromIdx] else null
                                                    if (origItem != null) {
                                                        localItems.forEachIndexed { i, it ->
                                                            if (i != fromIdx) {
                                                                vm.reorderItem(group.categoryId, origItem.id, it.id)
                                                            }
                                                        }
                                                    }
                                                    itemDragIdx.remove(group.categoryId)
                                                    isDragging = false
                                                },
                                                onDragCancel = {
                                                    itemDragIdx.remove(group.categoryId)
                                                    isDragging = false
                                                    localItems.clear()
                                                    localItems.addAll(group.items)
                                                }
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⠿", fontSize = 11.sp, color = BorderBeige)
                                    Text(item.name, fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1A1A1A),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    // Quantity + unit right-aligned
                                    if (item.qty > 0 || item.unit.isNotBlank()) {
                                        Text(
                                            buildString {
                                                if (item.qty > 0) append(fmtQty(item.qty))
                                                if (item.unit.isNotBlank()) {
                                                    if (item.qty > 0) append("\u00A0")
                                                    append(item.unit)
                                                }
                                            },
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = PriOrangeDark,
                                            textAlign = TextAlign.End,
                                            maxLines = 1
                                        )
                                    }
                                    if (item.fromRecipeId != null) {
                                        Text("📅", fontSize = 9.sp, color = TextMuted)
                                    }
                                    IconButton(onClick = { undoItem = item; vm.deleteItem(item.id) },
                                        modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, null,
                                            tint = Color(0xFF8B5E3C),
                                            modifier = Modifier.size(14.dp))
                                    }
                                }
                                if (itemIdx < localItems.size - 1) {
                                    HorizontalDivider(color = BorderBeige.copy(alpha = 0.4f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 10.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Undo snackbar ──────────────────────────────────────────
        AnimatedVisibility(
            visible  = undoItem != null,
            enter    = slideInVertically { it } + fadeIn(),
            exit     = slideOutVertically { it } + fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Surface(color = Color(0xFF2C1A0E), shape = RoundedCornerShape(28.dp),
                shadowElevation = 10.dp, modifier = Modifier.padding(horizontal = 16.dp)) {
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
    if (showMenu) {
        AlertDialog(onDismissRequest = { showMenu = false },
            title = { Text("Actions") },
            text = { Column {
                TextButton(onClick = { vm.clearChecked(); showMenu = false }) { Text("✓ Vider les cochés") }
                TextButton(onClick = { vm.clearAll(); showMenu = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("🗑 Tout vider")
                }
            }},
            confirmButton = { TextButton(onClick = { showMenu = false }) { Text("Fermer") } },
            shape = RoundedCornerShape(16.dp))
    }
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
                        Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Partager"))
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
