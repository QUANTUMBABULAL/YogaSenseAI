package com.chinmai.yogasense.ui.explore

import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.catalog.Category
import com.chinmai.yogasense.catalog.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreViewModelTest {
    @Test fun initialStateShowsWholeCatalogue() {
        val vm = ExploreViewModel()
        assertEquals(5, vm.state.value.results.size)
        assertEquals(5, vm.state.value.total)
        assertFalse(vm.state.value.hasFilters)
    }

    @Test fun queryAndFiltersUpdateResults() {
        val vm = ExploreViewModel()
        vm.onQueryChange("tree")
        assertEquals(listOf(Asana.TREE), vm.state.value.results.map { it.asana })
        vm.onQueryChange("")
        vm.onDifficulty(Difficulty.INTERMEDIATE)
        assertEquals(listOf(Asana.TRIANGLE), vm.state.value.results.map { it.asana })
        vm.onDifficulty(null)
        vm.onCategory(Category.BALANCE)
        assertEquals(listOf(Asana.TREE), vm.state.value.results.map { it.asana })
        assertTrue(vm.state.value.hasFilters)
    }

    @Test fun noMatchesThenClear() {
        val vm = ExploreViewModel()
        vm.onQueryChange("tree")
        vm.onCategory(Category.STRENGTH)
        assertTrue(vm.state.value.results.isEmpty())
        vm.clearFilters()
        assertEquals(5, vm.state.value.results.size)
        assertEquals("", vm.state.value.query)
    }
}
