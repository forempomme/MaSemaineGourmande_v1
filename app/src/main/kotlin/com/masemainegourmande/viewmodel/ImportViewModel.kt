package com.masemainegourmande.viewmodel

import androidx.lifecycle.*
import com.masemainegourmande.data.model.ParsedRecipe
import com.masemainegourmande.data.repository.AppRepository
import com.masemainegourmande.importer.ImportException
import com.masemainegourmande.importer.RecipeImporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════
// STATE
// ═══════════════════════════════════════════════════════════════

sealed interface ImportState {
    data object Idle    : ImportState
    data class  Loading(val step: String) : ImportState
    data class  Success(val recipe: ParsedRecipe, val savedId: String? = null) : ImportState
    data class  Error(val message: String) : ImportState
}

// ═══════════════════════════════════════════════════════════════
// VIEWMODEL
// ═══════════════════════════════════════════════════════════════

class ImportViewModel(
    private val repo:     AppRepository,
    private val importer: RecipeImporter
) : ViewModel() {

    // ── Import state ──────────────────────────────────────────

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    // ── Import history ────────────────────────────────────────

    val importHistory = repo.observeImportHistory().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    // ── URL import ────────────────────────────────────────────

    fun importFromUrl(url: String) {
        viewModelScope.launch {
            _state.value = ImportState.Loading("Connexion au site…")
            runCatching {
                // Emit intermediate step after 2 s for slow sites
                kotlinx.coroutines.delay(2_000)
                _state.value = ImportState.Loading("Extraction de la recette…")
                importer.importFromUrl(url)
            }.fold(
                onSuccess = { _state.value = ImportState.Success(it) },
                onFailure = { e ->
                    _state.value = ImportState.Error(
                        when (e) {
                            is ImportException -> e.message ?: "Erreur inconnue"
                            else               -> "Erreur réseau : ${e.message}"
                        }
                    )
                }
            )
        }
    }

    // ── Text (paste) import ───────────────────────────────────

    fun importFromText(text: String) {
        runCatching { importer.importFromText(text) }.fold(
            onSuccess = { _state.value = ImportState.Success(it) },
            onFailure = { _state.value = ImportState.Error(it.message ?: "Format non reconnu") }
        )
    }

    // ── Confirm & save to DB ──────────────────────────────────

    fun confirmImport(recipe: ParsedRecipe) {
        viewModelScope.launch {
            val id = repo.saveRecipe(recipe)
            if (recipe.url.isNotBlank()) {
                repo.saveImportHistory(recipe.url, recipe.name, recipe.emoji)
            }
            _state.value = ImportState.Success(recipe, savedId = id)
        }
    }

    fun reset() { _state.value = ImportState.Idle }

    // ── Factory ───────────────────────────────────────────────

    companion object {
        fun factory(repo: AppRepository, importer: RecipeImporter) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ImportViewModel(repo, importer) as T
            }
    }
}
