package com.chinmai.yogasense.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chinmai.yogasense.catalog.Category
import com.chinmai.yogasense.catalog.Difficulty
import com.chinmai.yogasense.ui.components.AsanaCard
import com.chinmai.yogasense.ui.components.EmptyState
import com.chinmai.yogasense.ui.components.SecondaryButton
import com.chinmai.yogasense.ui.components.SelectChip
import com.chinmai.yogasense.ui.components.YsIcons
import com.chinmai.yogasense.ui.theme.CardWhite
import com.chinmai.yogasense.ui.theme.Hairline
import com.chinmai.yogasense.ui.theme.Ink
import com.chinmai.yogasense.ui.theme.InkMuted
import com.chinmai.yogasense.ui.theme.Midnight
import com.chinmai.yogasense.ui.theme.Spacing

@Composable
fun ExploreScreen(onOpenAsana: (String) -> Unit, viewModel: ExploreViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp), // 2 columns on 360dp phones (OPPO F15)
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md - 4.dp),
    ) {
        item(span = full) {
            Column(Modifier.padding(top = Spacing.md)) {
                Text("LIBRARY", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                Text("Find your pose", style = MaterialTheme.typography.displaySmall, color = Ink)
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search poses…", maxLines = 1) },
                    leadingIcon = { Icon(YsIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(YsIcons.Close, "Clear search") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite, unfocusedContainerColor = CardWhite,
                        focusedBorderColor = Midnight, unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedLeadingIconColor = Ink, unfocusedLeadingIconColor = InkMuted,
                        cursorColor = Midnight,
                    ),
                )
            }
        }
        item(span = full) {
            FilterRow(
                options = listOf<Difficulty?>(null) + Difficulty.entries,
                selected = state.difficulty,
                label = { it?.label ?: "All levels" },
                onSelect = viewModel::onDifficulty,
            )
        }
        item(span = full) {
            FilterRow(
                options = listOf<Category?>(null) + Category.entries,
                selected = state.category,
                label = { it?.label ?: "All types" },
                onSelect = viewModel::onCategory,
            )
        }
        item(span = full) {
            Text(
                "${state.results.size} of ${state.total} poses",
                style = MaterialTheme.typography.labelMedium, color = InkMuted,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        if (state.results.isEmpty()) {
            item(span = full) {
                EmptyState(
                    YsIcons.Search, "No poses match",
                    "Try a different search term or clear the filters.",
                    action = { SecondaryButton("Clear filters", viewModel::clearFilters) },
                )
            }
        } else {
            items(state.results, key = { it.id }) { a -> AsanaCard(a, onClick = { onOpenAsana(a.id) }) }
        }
    }
}

@Composable
private fun <T> FilterRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(options, key = { label(it) }) { o -> SelectChip(label(o), selected = o == selected, onClick = { onSelect(o) }) }
    }
}
