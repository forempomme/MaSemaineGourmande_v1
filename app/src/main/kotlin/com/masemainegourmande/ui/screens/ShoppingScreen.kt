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
import androidx.compose.foundation.lazy.items
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
private fun catEmoji(name: String) =
    CAT_EMOJI.entries.firstOrNull { name.startsWith(it.key) }?.value ?: "📦"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val groups     by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current
    val density     = LocalDensity.current

    // Inline add state
    var addName  by remember { mutableStateOf("") }
    var addQty   by remember { mutableStateOf("") }
    var addUnit  by remember { mutableStateOf("") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    var showShare by remember { mutableStateOf(false) }
    var showMenu  by remember { mutableStateOf(false) }
    var undoItem  by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    // ── Drag-drop: local mutable copy of groups for live reordering ──────────
    // Categories: local order updated live during drag, committed to VM on release
    val localGroups = remember { mutableStateListOf<ShoppingGroup>() }
    LaunchedEffect(groups) {
        // Only sync from VM when not dragging
        localGroups.clear(); localGroups.addAll(groups)
    }

    // Per-category item drag state
    val itemDragDelta  = remember { mutableStateMapOf<String, Float>() }
    val itemDragFrom   = remember { mutableStateMapOf<String, Int>() }   // catId -> dragging index
    val catDragDelta   = remember { mutableFloatStateOf(0f) }
    var catDragFromIdx by remember { mutableStateOf<Int?>(null) }

    val itemHeightPx = with(density) { 48.dp.toPx() }
    val catHeightPx  = with(density) { 42.dp.toPx() }

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
        focusedTextColor          = Color.Black,
        unfocusedTextColor        = Color.Black,
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

            // ── 1. Action bar (count + icons) ─────────────────────
            item(key = "action_bar") {
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (totalCount > 0) "🛒  $totalCount article${if (totalCount > 1) "s" else ""}"
                        else "🛒  Liste vide",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = TextMuted, modifier = Modifier.weight(1f)
                    )
                    if (groups.isNotEmpty()) {
                        IconButton(onClick = { showShare = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = BorderBeige)
            }

            // ── 2. Inline add bar ──────────────────────────────────
            item(key = "add_bar") {
                Row(
                    Modifier.background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                        ), colors = inputColors
                    )
                    OutlinedTextField(
                        value = addQty, onValueChange = { addQty = it },
                        placeholder = { Text("Qté", fontSize = 11.sp) },
                        modifier = Modifier.width(54.dp),
                        shape = RoundedCornerShape(10.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color.Black),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        ), colors = inputColors
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
                                tint = if (addName.isNotBlank()) Color.White else PriOrange,
                                modifier = Modifier.size(20.dp))
                        }
                    }
                }
                HorizontalDivider(color = BorderBeige)
                Spacer(Modifier.height(8.dp))
            }

            // ── 3. Empty state ─────────────────────────────────────
            if (localGroups.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 60.dp),
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

                // ── 4. Category groups ─────────────────────────────
                localGroups.forEachIndexed { catIdx, group ->
                    item(key = "group_${group.categoryId}") {
                        val isCatDragging = catDragFromIdx == catIdx
                        Card(
                            shape  = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, BorderBeige),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .shadow(if (isCatDragging) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                        ) {
                            // ── Category header ── BgCream background, left-aligned
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(BgCream)
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                                    // Long-press drag handle for category
                                    .pointerInput(group.categoryId) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                catDragFromIdx = catIdx
                                                catDragDelta.floatValue = 0f
                                            },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                catDragDelta.floatValue += drag.y
                                                val steps = (catDragDelta.floatValue / catHeightPx).toInt()
                                                if (steps != 0) {
                                                    val from = catDragFromIdx ?: return@detectDragGesturesAfterLongPress
                                                    val to   = (from + steps).coerceIn(0, localGroups.size - 1)
                                                    if (to != from) {
                                                        val moved = localGroups.removeAt(from)
                                                        localGroups.add(to, moved)
                                                        catDragFromIdx = to
                                                        catDragDelta.floatValue -= steps * catHeightPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                // Persist new category order to VM
                                                val from = catDragFromIdx
                                                if (from != null && localGroups.size == groups.size) {
                                                    val fromGroup = localGroups[from]
                                                    val origGroup = groups.find { it.categoryId == fromGroup.categoryId }
                                                    if (origGroup != null && groups.indexOf(origGroup) != from) {
                                                        vm.reorderCategory(origGroup.categoryId, localGroups[from].categoryId)
                                                    }
                                                }
                                                catDragFromIdx = null
                                            },
                                            onDragCancel = { catDragFromIdx = null }
                                        )
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text("⠿", fontSize = 14.sp, color = TextBrown.copy(alpha = 0.4f))
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

                            // ── Items ── CardSurface background, no gap between them
                            group.items.forEachIndexed { itemIdx, item ->
                                val draggingIdx = itemDragFrom[group.categoryId]
                                val isDraggingThis = draggingIdx == itemIdx
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            if (isDraggingThis) PriOrangeLight
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .pointerInput("${group.categoryId}_$itemIdx") {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    itemDragFrom[group.categoryId] = itemIdx
                                                    itemDragDelta[group.categoryId] = 0f
                                                },
                                                onDrag = { change, drag ->
                                                    change.consume()
                                                    val prev = itemDragDelta[group.categoryId] ?: 0f
                                                    val next = prev + drag.y
                                                    itemDragDelta[group.categoryId] = next
                                                    val steps = (next / itemHeightPx).toInt()
                                                    if (steps != 0) {
                                                        val curFrom = itemDragFrom[group.categoryId] ?: return@detectDragGesturesAfterLongPress
                                                        val curTo = (curFrom + steps).coerceIn(0, group.items.size - 1)
                                                        if (curTo != curFrom) {
                                                            vm.reorderItem(
                                                                group.categoryId,
                                                                group.items[curFrom].id,
                                                                group.items[curTo].id
                                                            )
                                                            itemDragFrom[group.categoryId] = curTo
                                                            itemDragDelta[group.categoryId] = next - steps * itemHeightPx
                                                        }
                                                    }
                                                },
                                                onDragEnd   = { itemDragFrom.remove(group.categoryId) },
                                                onDragCancel = { itemDragFrom.remove(group.categoryId) }
                                            )
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⠿", fontSize = 12.sp, color = BorderBeige,
                                        modifier = Modifier.align(Alignment.CenterVertically))
                                    Text(item.name, fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium, color = TextBrown,
                                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    // Quantity right-aligned with unit
                                    if (item.qty > 0 || item.unit.isNotBlank()) {
                                        Text(
                                            buildString {
                                                if (item.qty > 0) append(fmtQty(item.qty))
                                                if (item.unit.isNotBlank()) {
                                                    if (item.qty > 0) append(" ")
                                                    append(item.unit)
                                                }
                                            },
                                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = PriOrange, textAlign = TextAlign.End
                                        )
                                    }
                                    if (item.fromRecipeId != null) {
                                        Text("📅", fontSize = 9.sp, color = TextMuted)
                                    }
                                    IconButton(onClick = { undoItem = item; vm.deleteItem(item.id) },
                                        modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, null, tint = TextMuted,
                                            modifier = Modifier.size(14.dp))
                                    }
                                }
                                // Separator between items (not after last)
                                if (itemIdx < group.items.size - 1) {
                                    HorizontalDivider(
                                        color = BorderBeige.copy(alpha = 0.5f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
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

    if (showShare) ShareSheet(vm.buildShareText(localGroups.toList()), { showShare = false }, context)
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
