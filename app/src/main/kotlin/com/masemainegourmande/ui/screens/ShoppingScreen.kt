package com.masemainegourmande.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.ShoppingItemEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.ShoppingGroup
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {
    val groups     by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val context     = LocalContext.current

    var showAddDialog  by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showClearMenu  by remember { mutableStateOf(false) }

    // ── Undo delete state ─────────────────────────────────────
    // Use a simple nullable state — LaunchedEffect auto-clears after 2.5s
    var undoItem by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    // Auto-dismiss the undo bar after 2.5 seconds
    // The key resets the timer every time a new item is deleted
    LaunchedEffect(undoItem) {
        if (undoItem != null) {
            delay(2500)
            undoItem = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Liste de courses", fontWeight = FontWeight.ExtraBold)
                        if (totalCount > 0)
                            Text("$totalCount article${if (totalCount > 1) "s" else ""}",
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PriOrange,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Default.Share, "Partager")
                    }
                    Box {
                        IconButton(onClick = { showClearMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Plus")
                        }
                        DropdownMenu(expanded = showClearMenu, onDismissRequest = { showClearMenu = false }) {
                            DropdownMenuItem(text = { Text("✓ Vider les cochés") },
                                onClick = { vm.clearChecked(); showClearMenu = false })
                            DropdownMenuItem(
                                text    = { Text("🗑 Tout vider", color = MaterialTheme.colorScheme.error) },
                                onClick = { vm.clearAll(); showClearMenu = false })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PriOrange, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Ajouter", fontWeight = FontWeight.Bold) }
            )
        }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize()) {

            if (groups.isEmpty()) {
                EmptyState(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 14.dp, end = 14.dp, top = 8.dp,
                        bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    groups.forEach { group ->
                        stickyHeader(key = "hdr_${group.categoryId}") {
                            CategoryHeader(group)
                        }
                        items(items = group.items, key = { it.id }) { item ->
                            SwipeableItem(
                                item     = item,
                                onCheck  = { vm.setChecked(item.id, !item.checked) },
                                onDelete = {
                                    undoItem = item
                                    vm.deleteItem(item.id)
                                }
                            )
                            Spacer(Modifier.height(5.dp))
                        }
                        item(key = "sp_${group.categoryId}") { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }

            // ── Undo bar (slides up from bottom for 2.5s) ────
            AnimatedVisibility(
                visible  = undoItem != null,
                enter    = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(250)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
            ) {
                Surface(
                    color           = Color(0xFF2C1A0E),
                    shape           = RoundedCornerShape(28.dp),
                    shadowElevation = 10.dp,
                    modifier        = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        Modifier.padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("✅  ${undoItem?.name ?: ""} supprimé",
                            color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                undoItem?.let { item ->
                                    vm.addManualItem(item.name, item.qty, item.unit)
                                    undoItem = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PriOrange),
                            shape  = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("Annuler", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddManualItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd     = { name, qty, unit -> vm.addManualItem(name, qty, unit); showAddDialog = false }
        )
    }
    if (showShareSheet) {
        ShareShoppingSheet(text = vm.buildShareText(groups), onDismiss = { showShareSheet = false }, context = context)
    }
}

// ─── Category header ──────────────────────────────────────────

@Composable
private fun CategoryHeader(group: ShoppingGroup) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.categoryName.uppercase(), fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp, color = TextMuted, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Surface(color = PriOrangeLight, shape = CircleShape) {
                Text(group.items.size.toString(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                    color = PriOrange, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
    }
}

// ─── Swipeable item ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableItem(item: ShoppingItemEntity, onCheck: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
        positionalThreshold = { it * 0.4f }
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val visible = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                    .background(if (visible) Color(0xFFE05050) else Color.Transparent)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (visible) Icon(Icons.Default.Delete, null, tint = Color.White)
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        ItemCard(item = item, onCheck = onCheck)
    }
}

// ─── Item card ────────────────────────────────────────────────

@Composable
private fun ItemCard(item: ShoppingItemEntity, onCheck: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.checked) Color(0xFFF9F9F9) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, if (item.checked) BorderBeige.copy(alpha = 0.4f) else BorderBeige),
        modifier = Modifier.fillMaxWidth().alpha(if (item.checked) 0.6f else 1f)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Circular checkbox
            Surface(
                color  = if (item.checked) AccGreen else Color.Transparent,
                border = BorderStroke(2.dp, if (item.checked) AccGreen else BorderBeige),
                shape  = CircleShape,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (item.checked) Text("✓", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }

            Text(item.name, fontWeight = if (item.checked) FontWeight.Normal else FontWeight.SemiBold,
                fontSize = 15.sp, modifier = Modifier.weight(1f),
                color = if (item.checked) TextMuted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (item.qty > 0) {
                Surface(
                    color  = if (item.checked) Color.Transparent else PriOrangeLight,
                    shape  = RoundedCornerShape(8.dp)
                ) {
                    Text("${fmtQty(item.qty)} ${item.unit}".trim(),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = if (item.checked) TextMuted else PriOrange,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            if (item.fromRecipeId != null && !item.checked) {
                Surface(color = PriOrangeLight.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp)) {
                    Text("📅", fontSize = 10.sp, modifier = Modifier.padding(3.dp))
                }
            }

            // Check/uncheck button
            IconButton(onClick = onCheck, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (item.checked) Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                    null,
                    tint = if (item.checked) AccGreen else BorderBeige,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─── Dialogs / sheets ────────────────────────────────────────

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
            Button(onClick = { if (name.isNotBlank()) onAdd(name.trim(), qty.toDoubleOrNull() ?: 0.0, unit.trim()) },
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
private fun ShareShoppingSheet(text: String, onDismiss: () -> Unit, context: android.content.Context) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("📤 Partager la liste", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Text(text, fontSize = 13.sp, lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().padding(14.dp)
                        .heightIn(max = 250.dp).verticalScroll(rememberScrollState()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val cb = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText("courses", text))
                    copied = true
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (copied) "Copié !" else "Copier")
                }
                Button(onClick = {
                    context.startActivity(android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }, "Partager"))
                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PriOrange)) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Partager")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Text("🛒", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text("Liste vide", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text("Importez une recette pour remplir\nautomatiquement votre liste.",
            fontSize = 14.sp, color = TextMuted, fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
    }
}

private fun fmtQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
