package com.chinmai.yogasense.ui.explore

import androidx.lifecycle.ViewModel
import com.chinmai.yogasense.catalog.AsanaCatalog
import com.chinmai.yogasense.catalog.AsanaFilter
import com.chinmai.yogasense.catalog.AsanaInfo
import com.chinmai.yogasense.catalog.Category
import com.chinmai.yogasense.catalog.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExploreUiState(
    val query: String = "",
    val difficulty: Difficulty? = null,
    val category: Category? = null,
    val results: List<AsanaInfo> = emptyList(),
    val total: Int = 0,
) {
    val hasFilters: Boolean get() = query.isNotBlank() || difficulty != null || category != null
}

/** Search/filter state for Explore. Filtering is synchronous and pure (see [AsanaFilter]). */
class ExploreViewModel(private val catalog: List<AsanaInfo> = AsanaCatalog.all) : ViewModel() {
    private val _state = MutableStateFlow(compute(ExploreUiState()))
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    fun onQueryChange(q: String) = _state.update { compute(it.copy(query = q)) }
    fun onDifficulty(d: Difficulty?) = _state.update { compute(it.copy(difficulty = d)) }
    fun onCategory(c: Category?) = _state.update { compute(it.copy(category = c)) }
    fun clearFilters() = _state.update { compute(ExploreUiState()) }

    private fun compute(s: ExploreUiState) =
        s.copy(results = AsanaFilter.apply(catalog, s.query, s.difficulty, s.category), total = catalog.size)
}
