package com.masemainegourmande.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masemainegourmande.data.model.ShoppingItemEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.ShoppingGroup
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(vm: ShoppingViewModel) {

    val groups     by vm.groups.collectAsState()
    val totalCount by vm.totalCount.collectAsState()
    val snackHost   = remember { SnackbarHostState() }
    val scope       = rememberCoroutineScope()
    val context     = LocalContext.current

    var showAddDialog  by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showClearMenu  by remember { mutableStateOf(false) }

    // Track last deleted item for undo
    var lastDeleted by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🛒 Liste de courses", fontWeight = FontWeight.ExtraBold)
                        if (totalCount > 0)
                            Text("$totalCount article${if (totalCount > 1) "s" else ""}",
                                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = PriOrange,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    // Share
                    IconButton(onClick = { showShareSheet = true }) {
                        Icon(Icons.Default.Share, contentDescription = "Partager")
                    }
                    // Clear menu
                    Box {
                        IconButton(onClick = { showClearMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Plus")
                        }
                        DropdownMenu(
                            expanded = showClearMenu,
                            onDismissRequest = { showClearMenu = false }
                        ) {
                            DropdownMenuItem(
                                text    = { Text("🗑 Vider les articles cochés") },
                                onClick = { vm.clearChecked(); showClearMenu = false }
                            )
                            DropdownMenuItem(
                                text    = { Text("🗑 Vider toute la liste", color = MaterialTheme.colorScheme.error) },
                                onClick = { vm.clearAll(); showClearMenu = false }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick            = { showAddDialog = true },
                containerColor     = PriOrange,
                contentColor       = Color.White,
                icon               = { Icon(Icons.Default.Add, contentDescription = null) },
                text               = { Text("Ajouter", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(snackHost) }
    ) { inner ->

        if (groups.isEmpty()) {
            EmptyShoppingState(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp,
                    top   = inner.calculateTopPadding() + 8.dp,
                    bottom = inner.calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groups.forEach { group ->
                    // Category header
                    stickyHeader(key = "hdr_${group.categoryId}") {
                        CategoryHeader(group)
                    }
                    // Items
                    items(
                        items = group.items,
                        key   = { it.id }
                    ) { item ->
                        AnimatedVisibility(
                            visible = true,
                            enter   = fadeIn() + expandVertically(),
                            exit    = fadeOut(animationSpec = tween(200)) + shrinkVertically()
                        ) {
                            SwipeableShoppingItem(
                                item      = item,
                                onCheck   = { vm.setChecked(item.id, !item.checked) },
                                onDelete  = {
                                    lastDeleted = item
                                    vm.deleteItem(item.id)
                                    scope.launch {
                                        val result = snackHost.showSnackbar(
                                            message     = "${item.name} supprimé",
                                            actionLabel = "Annuler",
                                            duration    = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            // Restore via re-add manual
                                            lastDeleted?.let { del ->
                                                vm.addManualItem(del.name, del.qty, del.unit)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }

    // ── Dialogs / sheets ─────────────────────────────────────
    if (showAddDialog) {
        AddManualItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd     = { name, qty, unit ->
                vm.addManualItem(name, qty, unit)
                showAddDialog = false
            }
        )
    }

    if (showShareSheet) {
        ShareShoppingSheet(
            text      = vm.buildShareText(groups),
            onDismiss = { showShareSheet = false },
            context   = context
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// CATEGORY HEADER
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CategoryHeader(group: ShoppingGroup) {
    Surface(color = BgCream) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                group.categoryName.uppercase(),
                fontWeight      = FontWeight.ExtraBold,
                fontSize        = 11.sp,
                color           = TextMuted,
                letterSpacing   = 1.sp,
                modifier        = Modifier.weight(1f)
            )
            Surface(
                color  = PriOrangeLight,
                shape  = CircleShape
            ) {
                Text(
                    group.items.size.toString(),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = PriOrange,
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SWIPEABLE ITEM CARD
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableShoppingItem(
    item:     ShoppingItemEntity,
    onCheck:  () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(); true
            } else false
        },
        positionalThreshold = { it * 0.4f }
    )

    SwipeToDismissBox(
        state             = dismissState,
        backgroundContent = { DismissBackground(dismissState) },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        ShoppingItemCard(item = item, onCheck = onCheck)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissBackground(state: SwipeToDismissBoxState) {
    val color = when (state.dismissDirection) {
        SwipeToDismissBoxValue.EndToStart -> Color(0xFFE05050)
        else                             -> Color.Transparent
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(12.dp))
            .padding(end = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.White)
    }
}

@Composable
private fun ShoppingItemCard(item: ShoppingItemEntity, onCheck: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderBeige),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.checked) 0.55f else 1f)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Check button
            IconButton(
                onClick  = onCheck,
                modifier = Modifier.size(34.dp),
                colors   = IconButtonDefaults.iconButtonColors(
                    containerColor = if (item.checked) AccGreen else AccGreenLight
                )
            ) {
                Icon(
                    imageVector   = if (item.checked) Icons.Default.Check else Icons.Outlined.Circle,
                    contentDescription = if (item.checked) "Décocher" else "Cocher",
                    tint          = if (item.checked) Color.White else AccGreen,
                    modifier      = Modifier.size(18.dp)
                )
            }

            // Name
            Text(
                text           = item.name,
                fontWeight     = FontWeight.SemiBold,
                fontSize       = 15.sp,
                modifier       = Modifier.weight(1f),
                color          = if (item.checked) TextMuted else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
            )

            // Quantity + unit
            if (item.qty > 0) {
                Text(
                    "${formatItemQty(item.qty)} ${item.unit}".trim(),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = if (item.checked) TextMuted else PriOrange
                )
            }

            // Planning badge
            if (item.fromRecipeId != null) {
                Surface(color = PriOrangeLight, shape = RoundedCornerShape(4.dp)) {
                    Text("📅", fontSize = 11.sp, modifier = Modifier.padding(3.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ADD MANUAL ITEM DIALOG
// ═══════════════════════════════════════════════════════════════

@Composable
fun AddManualItemDialog(
    onDismiss: () -> Unit,
    onAdd:     (name: String, qty: Double, unit: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var qty  by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Ajouter un article", fontWeight = FontWeight.ExtraBold) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Article *") },
                    placeholder   = { Text("ex : huile d'olive") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = qty,
                        onValueChange = { qty = it },
                        label         = { Text("Quantité") },
                        placeholder   = { Text("0") },
                        singleLine    = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value         = unit,
                        onValueChange = { unit = it },
                        label         = { Text("Unité") },
                        placeholder   = { Text("g, L…") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        shape         = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (name.isNotBlank()) onAdd(name.trim(), qty.toDoubleOrNull() ?: 0.0, unit.trim()) },
                enabled  = name.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
            ) { Text("Ajouter") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

// ═══════════════════════════════════════════════════════════════
// SHARE BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareShoppingSheet(
    text:      String,
    onDismiss: () -> Unit,
    context:   android.content.Context
) {
    val scope   = rememberCoroutineScope()
    var copied  by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("📤 Partager la liste", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

            // Preview
            Surface(
                color  = BgCream,
                shape  = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderBeige)
            ) {
                Text(
                    text     = text,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    lineHeight = 22.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Copy
                OutlinedButton(
                    onClick  = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Liste de courses", text))
                        copied = true
                    },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Copié !" else "Copier")
                }

                // Native share
                Button(
                    onClick  = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type    = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Partager la liste"))
                    },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = PriOrange)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Partager")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════

@Composable
private fun EmptyShoppingState(modifier: Modifier = Modifier) {
    Column(
        modifier             = modifier,
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {
        Text("🛒", fontSize = 60.sp)
        Spacer(Modifier.height(14.dp))
        Text("Liste vide", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Importez une recette et ajoutez ses ingrédients,\nou ajoutez des articles manuellement.",
            fontSize = 14.sp,
            color    = TextMuted,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(horizontal = 40.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// HELPER
// ═══════════════════════════════════════════════════════════════

private fun formatItemQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
