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
import androidx.compose.ui.zIndex
import com.masemainegourmande.data.model.ShoppingItemEntity
import com.masemainegourmande.ui.theme.*
import com.masemainegourmande.viewmodel.ShoppingGroup
import com.masemainegourmande.viewmodel.ShoppingViewModel
import kotlinx.coroutines.delay

// Couleurs locales (pas dans le Theme pour ne pas tout casser)
private val CatHeaderBg = Color(0xFF181C24)  // fond catégorie — même que CardSurface
private val ItemRowBg   = Color(0xFF222840)  // fond article — plus clair/bleuté

private val CAT_EMOJI = mapOf(
    "Viandes" to "🥩", "Produits" to "🧀", "Œufs" to "🥚",
    "Fruits"  to "🥦", "Féculents" to "🌾", "Épicerie" to "🫙",
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
    val haptic      = LocalHapticFeedback.current
    val density     = LocalDensity.current

    // ── Drag-drop local state ─────────────────────────────────
    val localGroups = remember { mutableStateListOf<ShoppingGroup>() }
    var isDragging  by remember { mutableStateOf(false) }
    // pendingSync: blocks LaunchedEffect from resetting localGroups until vmGroups
    // has caught up with the new order we set (otherwise categories snap back!)
    var pendingSync by remember { mutableStateOf(false) }

    val localItems = remember { mutableStateMapOf<String, MutableList<ShoppingItemEntity>>() }

    LaunchedEffect(vmGroups) {
        when {
            isDragging  -> return@LaunchedEffect
            pendingSync -> {
                val localIds = localGroups.map { it.categoryId }
                val vmIds    = vmGroups.map    { it.categoryId }
                if (localIds == vmIds || localGroups.isEmpty()) {
                    pendingSync = false
                    vmGroups.forEach { g -> localItems[g.categoryId] = g.items.toMutableList() }
                }
            }
            else -> {
                localGroups.clear(); localGroups.addAll(vmGroups)
                vmGroups.forEach { g -> localItems[g.categoryId] = g.items.toMutableList() }
            }
        }
    }



    // Drag: categories
    val catHeightPx     = with(density) { 50.dp.toPx() }
    var catDragIdx      by remember { mutableStateOf<Int?>(null) }
    var catDeltaAcc     by remember { mutableFloatStateOf(0f) }
    var catDragOffsetPx by remember { mutableFloatStateOf(0f) }

    // Drag: items
    val itemHeightPx = with(density) { 46.dp.toPx() }
    val itemDragIdx  = remember { mutableStateMapOf<String, Int>() }
    val itemDeltaAcc = remember { mutableStateMapOf<String, Float>() }
    val itemDragOffPx= remember { mutableStateMapOf<String, Float>() }

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
        focusedBorderColor        = PriOrange,
        unfocusedBorderColor      = BorderBeige,
        focusedTextColor          = TextBrown,
        unfocusedTextColor        = TextBrown,
        focusedContainerColor     = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
        focusedPlaceholderColor   = TextMuted,
        unfocusedPlaceholderColor = TextMuted
    )

    Box(Modifier.fillMaxSize().background(BgCream)) {
        LazyColumn(contentPadding = PaddingValues(
            start=12.dp, end=12.dp, top=10.dp, bottom=80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // ── Header ─────────────────────────────────────────
            item(key = "header") {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🛒", fontSize = 22.sp)
                    Text("Liste de courses", fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp, color = TextBrown, modifier = Modifier.weight(1f))
                    if (totalCount > 0) {
                        Surface(color = AccGreen, shape = CircleShape,
                            modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(totalCount.toString(), color = Color(0xFF002A1E),
                                    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    if (vmGroups.isNotEmpty()) {
                        IconButton(onClick = { showShare = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = TextMuted, modifier = Modifier.size(18.dp)) }
                    }
                }
            }

            // ── Add bar ─────────────────────────────────────────
            item(key = "add_bar") {
                Card(shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CatHeaderBg),
                    border = BorderStroke(1.dp, BorderBeige)) {
                    Column(Modifier.padding(start=12.dp, end=12.dp, top=10.dp, bottom=11.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ajouter manuellement", fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = TextBrown)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value=addName, onValueChange={addName=it},
                                placeholder={Text("Nom de l'article…", fontSize=11.sp)},
                                modifier=Modifier.weight(1f).height(44.dp),
                                shape=RoundedCornerShape(8.dp), singleLine=true,
                                textStyle=LocalTextStyle.current.copy(fontSize=12.sp, color=TextBrown),
                                keyboardOptions=KeyboardOptions(
                                    capitalization=androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                                    imeAction=androidx.compose.ui.text.input.ImeAction.Next),
                                colors=fieldColors)
                            OutlinedTextField(value=addQty, onValueChange={addQty=it},
                                placeholder={Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center){
                                    Text("Qté", fontSize=11.sp, color=TextMuted)}},
                                modifier=Modifier.width(52.dp).height(44.dp),
                                shape=RoundedCornerShape(8.dp), singleLine=true,
                                textStyle=LocalTextStyle.current.copy(fontSize=12.sp, color=TextBrown,
                                    textAlign=TextAlign.Center),
                                keyboardOptions=KeyboardOptions(
                                    keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal,
                                    imeAction=androidx.compose.ui.text.input.ImeAction.Next),
                                colors=fieldColors)
                            OutlinedTextField(value=addUnit, onValueChange={addUnit=it},
                                placeholder={Box(Modifier.fillMaxWidth(), contentAlignment=Alignment.Center){
                                    Text("U.", fontSize=11.sp, color=TextMuted)}},
                                modifier=Modifier.width(50.dp).height(44.dp),
                                shape=RoundedCornerShape(8.dp), singleLine=true,
                                textStyle=LocalTextStyle.current.copy(fontSize=12.sp, color=TextBrown,
                                    textAlign=TextAlign.Center),
                                keyboardOptions=KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions=KeyboardActions(onDone={addItem();keyboard?.hide()}),
                                colors=fieldColors)
                            Surface(
                                color=if(addName.isNotBlank()) PriOrangeDark else BorderBeige,
                                shape=RoundedCornerShape(8.dp),
                                modifier=Modifier.size(44.dp).clickable(enabled=addName.isNotBlank()){addItem()}) {
                                Box(contentAlignment=Alignment.Center) {
                                    Icon(Icons.Default.Add, null,
                                        tint=if(addName.isNotBlank()) Color.White else TextMuted,
                                        modifier=Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── Empty ───────────────────────────────────────────
            if (localGroups.isEmpty()) {
                item(key="empty") {
                    Column(Modifier.fillMaxWidth().padding(top=48.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("🛒", fontSize=52.sp)
                        Text("Liste vide", fontWeight=FontWeight.Bold, fontSize=16.sp, color=TextBrown)
                        Text("Planifiez des repas ou ajoutez des articles manuellement",
                            fontSize=13.sp, color=TextMuted, fontStyle=FontStyle.Italic,
                            textAlign=TextAlign.Center)
                    }
                }
            } else {
                // ── Category groups ─────────────────────────────
                localGroups.forEachIndexed { catIdx, group ->
                    item(key="grp_${group.categoryId}") {
                        val catItems      = localItems[group.categoryId] ?: group.items
                        val isCatDragging = catDragIdx == catIdx

                        val catVisualOffset by animateFloatAsState(
                            targetValue   = if (isCatDragging) catDragOffsetPx else 0f,
                            animationSpec = if (isCatDragging) snap()
                                            else spring(dampingRatio=Spring.DampingRatioMediumBouncy, stiffness=Spring.StiffnessMediumLow),
                            label = "catOff")
                        val catScale by animateFloatAsState(
                            targetValue   = if (isCatDragging) 1.03f else 1f,
                            animationSpec = spring(dampingRatio=Spring.DampingRatioMediumBouncy),
                            label = "catScale")

                        Card(
                            shape  = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CatHeaderBg),
                            border = BorderStroke(1.dp, if(isCatDragging) PriOrange else BorderBeige),
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isCatDragging) 10f else 0f)
                                .graphicsLayer {
                                    translationY    = catVisualOffset
                                    scaleX          = catScale; scaleY = catScale
                                    shadowElevation = if (isCatDragging) 20f else 1f
                                }
                        ) {
                            // Category header — long-press to drag
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(CatHeaderBg)
                                    .pointerInput(group.categoryId) {   // stable key!
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                isDragging = true
                                                catDragIdx = localGroups.indexOfFirst { it.categoryId == group.categoryId }
                                                catDeltaAcc = 0f; catDragOffsetPx = 0f
                                            },
                                            onDrag = { ch, drag ->
                                                ch.consume()
                                                catDeltaAcc     += drag.y
                                                catDragOffsetPx += drag.y
                                                val steps = (catDeltaAcc / catHeightPx).toInt()
                                                if (steps != 0) {
                                                    val from = catDragIdx ?: return@detectDragGesturesAfterLongPress
                                                    val to = (from + steps).coerceIn(0, localGroups.size - 1)
                                                    if (to != from) {
                                                        localGroups.add(to, localGroups.removeAt(from))
                                                        catDragIdx      = to
                                                        catDeltaAcc    -= steps * catHeightPx
                                                        catDragOffsetPx -= steps * catHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                catDragOffsetPx = 0f
                                                // pendingSync=true BEFORE isDragging=false
                                                // so LaunchedEffect won't reset localGroups
                                                pendingSync = true
                                                localGroups.forEachIndexed { i, g ->
                                                    vm.reorderCategoryByIndex(g.categoryId, i)
                                                }
                                                catDragIdx = null; isDragging = false
                                            },
                                            onDragCancel = {
                                                catDragOffsetPx = 0f; catDragIdx = null; isDragging = false
                                                pendingSync = false
                                                localGroups.clear(); localGroups.addAll(vmGroups)
                                            }
                                        )
                                    }
                                    .padding(horizontal=12.dp, vertical=10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("⠿", fontSize=14.sp,
                                    color=if(isCatDragging) PriOrange else TextMuted.copy(alpha=0.4f))
                                Text(catEmoji(group.categoryName), fontSize=15.sp)
                                Text(group.categoryName.uppercase(),
                                    fontWeight=FontWeight.ExtraBold, fontSize=11.sp,
                                    color=TextBrown, letterSpacing=0.8.sp,
                                    modifier=Modifier.weight(1f))
                                Surface(color=AccGreenLight, shape=RoundedCornerShape(10.dp)) {
                                    Text(catItems.size.toString(), fontSize=11.sp,
                                        fontWeight=FontWeight.ExtraBold, color=AccGreen,
                                        modifier=Modifier.padding(horizontal=8.dp, vertical=2.dp))
                                }
                            }

                            HorizontalDivider(color=BorderBeige, thickness=1.dp)

                            // Items — lighter background than category header
                            catItems.forEachIndexed { itemIdx, item ->
                                val isItemDragging = itemDragIdx[group.categoryId] == itemIdx
                                val rawOff = itemDragOffPx[group.categoryId] ?: 0f

                                val itemVisualOffset by animateFloatAsState(
                                    targetValue   = if (isItemDragging) rawOff else 0f,
                                    animationSpec = if (isItemDragging) snap()
                                                    else spring(dampingRatio=Spring.DampingRatioMediumBouncy, stiffness=Spring.StiffnessMedium),
                                    label = "itemOff_$itemIdx")
                                val itemScale by animateFloatAsState(
                                    targetValue   = if (isItemDragging) 1.02f else 1f,
                                    animationSpec = spring(dampingRatio=Spring.DampingRatioMediumBouncy),
                                    label = "itemSc_$itemIdx")

                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(if(isItemDragging) Color(0xFF2A3558) else ItemRowBg)
                                        .zIndex(if(isItemDragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY    = itemVisualOffset
                                            scaleX          = itemScale; scaleY = itemScale
                                            shadowElevation = if(isItemDragging) 10f else 0f
                                        }
                                        .pointerInput("${group.categoryId}_$itemIdx") {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    isDragging = true
                                                    itemDragIdx[group.categoryId]   = itemIdx
                                                    itemDeltaAcc[group.categoryId]  = 0f
                                                    itemDragOffPx[group.categoryId] = 0f
                                                },
                                                onDrag = { ch, drag ->
                                                    ch.consume()
                                                    val prev    = itemDeltaAcc[group.categoryId]  ?: 0f
                                                    val prevOff = itemDragOffPx[group.categoryId] ?: 0f
                                                    val next    = prev + drag.y
                                                    itemDeltaAcc[group.categoryId]  = next
                                                    itemDragOffPx[group.categoryId] = prevOff + drag.y
                                                    val steps = (next / itemHeightPx).toInt()
                                                    if (steps != 0) {
                                                        val from = itemDragIdx[group.categoryId]  ?: return@detectDragGesturesAfterLongPress
                                                        val list = localItems[group.categoryId] ?: return@detectDragGesturesAfterLongPress
                                                        val to   = (from + steps).coerceIn(0, list.size - 1)
                                                        if (to != from) {
                                                            list.add(to, list.removeAt(from))
                                                            itemDragIdx[group.categoryId]   = to
                                                            itemDeltaAcc[group.categoryId]  = next - steps * itemHeightPx
                                                            itemDragOffPx[group.categoryId] = (prevOff + drag.y) - steps * itemHeightPx
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    itemDragOffPx[group.categoryId] = 0f
                                                    localItems[group.categoryId]?.let {
                                                        vm.setItemOrder(group.categoryId, it.map { i -> i.id })
                                                    }
                                                    itemDragIdx.remove(group.categoryId); isDragging = false
                                                },
                                                onDragCancel = {
                                                    itemDragOffPx[group.categoryId] = 0f
                                                    itemDragIdx.remove(group.categoryId); isDragging = false
                                                    localItems[group.categoryId] = vmGroups
                                                        .find { it.categoryId == group.categoryId }
                                                        ?.items?.toMutableList() ?: mutableListOf()
                                                }
                                            )
                                        }
                                        .padding(horizontal=12.dp, vertical=9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⠿", fontSize=11.sp,
                                        color=if(isItemDragging) PriOrange else BorderBeige)
                                    // ✓ green circle
                                    Surface(color=AccGreenLight, shape=CircleShape,
                                        border=BorderStroke(1.5.dp, AccGreen),
                                        modifier=Modifier.size(26.dp)
                                            .clickable { undoItem=item; vm.deleteItem(item.id) }) {
                                        Box(contentAlignment=Alignment.Center) {
                                            Text("✓", fontSize=12.sp, color=AccGreen,
                                                fontWeight=FontWeight.ExtraBold)
                                        }
                                    }
                                    // Name
                                    Text(item.name, fontSize=13.sp, fontWeight=FontWeight.Medium,
                                        color=TextBrown, modifier=Modifier.weight(1f),
                                        maxLines=1, overflow=TextOverflow.Ellipsis)
                                    // Qty
                                    if (item.qty > 0 || item.unit.isNotBlank()) {
                                        Row(verticalAlignment=Alignment.CenterVertically,
                                            horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                                            if (item.qty > 0)
                                                Text(fmtQty(item.qty), fontSize=14.sp,
                                                    fontWeight=FontWeight.ExtraBold, color=PriOrange)
                                            if (item.unit.isNotBlank())
                                                Text(item.unit, fontSize=12.sp, color=TextMuted)
                                        }
                                    }
                                    if (item.fromRecipeId != null)
                                        Text("📅", fontSize=9.sp, color=TextMuted)
                                }
                                if (itemIdx < catItems.size - 1)
                                    HorizontalDivider(color=BorderBeige.copy(alpha=0.3f),
                                        thickness=0.5.dp, modifier=Modifier.padding(horizontal=12.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Undo ─────────────────────────────────────────────
        AnimatedVisibility(visible=undoItem!=null,
            enter=slideInVertically{it}+fadeIn(),
            exit=slideOutVertically{it}+fadeOut(tween(250)),
            modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=80.dp)) {
            Surface(color=Color(0xFF131720), shape=RoundedCornerShape(24.dp),
                shadowElevation=10.dp, border=BorderStroke(1.dp, BorderBeige),
                modifier=Modifier.padding(horizontal=16.dp)) {
                Row(Modifier.padding(start=16.dp,end=10.dp,top=10.dp,bottom=10.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("✅  ${undoItem?.name?:""} supprimé",
                        color=TextBrown, fontSize=13.sp, modifier=Modifier.weight(1f))
                    Button(onClick={undoItem?.let{vm.addManualItem(it.name,it.qty,it.unit);undoItem=null}},
                        colors=ButtonDefaults.buttonColors(containerColor=PriOrangeDark),
                        shape=RoundedCornerShape(20.dp),
                        contentPadding=PaddingValues(horizontal=14.dp,vertical=6.dp)) {
                        Text("Annuler", fontWeight=FontWeight.Bold, fontSize=12.sp)
                    }
                }
            }
        }
    }

    if (showShare) ShareSheet(vm.buildShareText(localGroups.toList()),{showShare=false},context)
    if (showMenu) AlertDialog(onDismissRequest={showMenu=false},
        containerColor=CardSurface, shape=RoundedCornerShape(16.dp),
        title={Text("Actions",color=TextBrown)},
        text={Column{
            TextButton(onClick={vm.clearChecked();showMenu=false}){Text("✓ Vider les cochés",color=TextBrown)}
            TextButton(onClick={vm.clearAll();showMenu=false},
                colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){
                Text("🗑 Tout vider")}
        }},
        confirmButton={TextButton(onClick={showMenu=false}){Text("Fermer",color=TextMuted)}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(text:String, onDismiss:()->Unit, context:Context) {
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest=onDismiss, containerColor=CardSurface) {
        Column(Modifier.padding(start=20.dp,end=20.dp,bottom=40.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("📤 Partager la liste",fontWeight=FontWeight.ExtraBold,fontSize=18.sp,color=TextBrown)
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(12.dp),
                border=BorderStroke(1.dp,BorderBeige)) {
                Text(text,fontSize=13.sp,lineHeight=22.sp,color=TextBrown,
                    modifier=Modifier.fillMaxWidth().padding(14.dp).heightIn(max=260.dp).verticalScroll(rememberScrollState()))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={
                    val cb=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("courses",text));copied=true
                },modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,BorderBeige)){
                    Icon(if(copied)Icons.Default.Check else Icons.Default.ContentCopy,null,modifier=Modifier.size(18.dp),tint=TextBrown)
                    Spacer(Modifier.width(6.dp));Text(if(copied)"Copié!" else "Copier",color=TextBrown)
                }
                Button(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,text)},"Partager"))},
                    modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=PriOrangeDark)){
                    Icon(Icons.Default.Share,null,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp));Text("Partager")
                }
            }
        }
    }
}

private fun fmtQty(v:Double):String =
    if(v==v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)
