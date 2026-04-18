# 🍽️ Ma Semaine Gourmande — Android

Application Android native (Kotlin + Jetpack Compose + Material 3) pour :
1. **Importer une recette** depuis une URL (ou texte collé)
2. **Créer automatiquement la liste de courses** avec les ingrédients mis à l'échelle

---

## Prérequis

| Outil | Version |
|---|---|
| Android Studio | Ladybug 2024.2+ |
| JDK | 17+ |
| Kotlin | 2.1.0 |
| compileSdk | 35 (Android 15) |
| minSdk | 26 (Android 8) |

---

## Démarrage rapide

```bash
git clone <repo>
cd MaSemaineGourmande
# Ouvrir dans Android Studio → Run ▶
```

---

## Architecture

```
app/src/main/kotlin/com/masemainegourmande/
│
├── data/
│   ├── model/Models.kt         ← Entités Room + modèles domaine (ParsedRecipe, Ingredient…)
│   ├── db/AppDatabase.kt       ← Room DB + 4 DAOs (Recipe, Shopping, Category, History)
│   ├── repository/AppRepository.kt  ← Source unique de vérité
│   └── DefaultData.kt          ← 9 catégories par défaut + detectCategory()
│
├── importer/                   ← Cascade d'extraction (voir ci-dessous)
│   ├── IngredientParser.kt     ← "200 g de spaghetti" → Ingredient(qty=200, unit="g", name="spaghetti")
│   ├── JsonLdParser.kt         ← JSON-LD + __NEXT_DATA__ + Microdata (Jsoup)
│   ├── EmojiAndTextParser.kt   ← EmojiGuesser + TextParser (onglet Copier-coller)
│   └── RecipeImporter.kt       ← Orchestrateur + Jow API directe + OkHttp
│
├── viewmodel/
│   ├── ImportViewModel.kt      ← État Idle/Loading/Success/Error, importFromUrl(), confirmImport()
│   └── ShoppingViewModel.kt    ← groups: StateFlow<List<ShoppingGroup>>, buildShareText()
│
├── ui/
│   ├── theme/Theme.kt          ← Palette Material 3 (orange chaud #D4622A, crème #FDF8F0)
│   ├── screens/ImportScreen.kt ← Onglets URL / Coller / Historique, prévisualisation, sheet portions
│   ├── screens/ShoppingScreen.kt ← Liste groupée, swipe-to-delete, undo, partage natif
│   └── Navigation.kt           ← Bottom bar + NavHost (Import ↔ Courses)
│
├── MsgApplication.kt           ← DI manuel (DB, Repository, Importer)
└── MainActivity.kt             ← Edge-to-edge, gestion du Share depuis le navigateur
```

---

## Cascade d'extraction (port fidèle du JSX)

```
URL saisie
   │
   ▼ jow.fr ?
   ├─── OUI → API https://api.jow.fr/public/recipe/{id}  ──────────────────► ParsedRecipe ✅
   │
   ▼ NON → OkHttp (User-Agent navigateur, pas de proxy CORS nécessaire sur Android)
   │
   ├─── <script type="application/ld+json">
   │       findRecipeNode()  →  @type:Recipe  /  @graph  /  tableau
   │       deepFindRecipeNode()  →  recherche récursive profondeur ≤ 12
   │    → parseJsonLdRecipe()  ──────────────────────────────────────────────► ParsedRecipe ✅
   │
   ├─── <script id="__NEXT_DATA__">  (Next.js)
   │       deepFindRecipeNode() ────────────────────────────────────────────► ParsedRecipe ✅
   │
   ├─── Microdata  itemtype="schema.org/Recipe"  (Jsoup)  ─────────────────► ParsedRecipe ✅
   │
   └─── ImportException("Aucune recette trouvée. Essayez Copier-coller.")
```

### `IngredientParser` — exemples

| Entrée | qty | unit | name |
|---|---|---|---|
| `"200 g de spaghetti"` | 200.0 | g | spaghetti |
| `"3 c.s. d'huile d'olive"` | 3.0 | c.s. | huile d'olive |
| `"½ citron"` | 0.5 | | citron |
| `"1/3 tasse de farine"` | 0.333 | tasse | farine |
| `"4 gousses d'ail"` | 4.0 | gousses | ail |

---

## Flux utilisateur

```
[Onglet Import]
  ↓ Coller une URL (ex: marmiton.org/recettes/poulet-roti...)
  ↓ Bouton "Importer" → Loading → Success
  ↓ Aperçu (emoji, nom, N ingrédients, N étapes)
  ↓ "Ajouter à mes recettes" → BottomSheet "Combien de personnes ?"
  ↓ Régler les portions → "Ajouter à la liste 🛒"

[Onglet Courses]
  ↓ Ingrédients groupés par catégorie (Viandes, Légumes, Féculents…)
  ↓ Tap ✓ pour cocher / Swipe gauche pour supprimer
  ↓ FAB "+" → ajout manuel
  ↓ Bouton ⬆ → partage texte natif (WhatsApp, SMS, e-mail…)
```

---

## Sites compatibles

| Site | Stratégie |
|---|---|
| Marmiton | JSON-LD |
| 750g | JSON-LD |
| Jow | API directe |
| BBC Good Food | JSON-LD |
| Allrecipes | JSON-LD |
| Epicurious | JSON-LD |
| Serious Eats | JSON-LD |
| Tasty | JSON-LD / Next.js |
| CuisineAZ | ⚠️ Copier-coller recommandé |

---

## Dépendances clés

| Lib | Rôle |
|---|---|
| Jetpack Compose BOM 2024.12 | UI déclarative Material 3 |
| Room 2.6 | Persistance locale SQLite |
| OkHttp 4.12 | Requêtes HTTP (pas de proxy CORS) |
| Jsoup 1.18 | Parser HTML — extraction JSON-LD / Microdata |
| kotlinx.serialization 1.7 | Décodage JSON-LD dynamique via `JsonElement` |
| Navigation Compose 2.8 | Navigation bottom bar |
