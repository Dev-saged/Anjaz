package com.anjaz.app

import com.anjaz.app.data.db.CampEntity
import com.anjaz.app.data.db.SquareEntity
import com.anjaz.app.domain.SquareTree
import org.junit.Assert.assertEquals
import org.junit.Test

class SquareTreeTest {
    private fun sq(id: String, parent: String? = null, deleted: Long? = null) = SquareEntity(id, "R", id, null, null, null, 0, null, deleted, parent)
    private fun camp(id: String, sq: String, fam: Int?) = CampEntity(id, "R", sq, id, null, null, null, 0, families = fam)

    @Test fun subSquaresRollUpIntoParent() {
        val squares = listOf(sq("sanabel"), sq("amal", "sanabel"), sq("sahel", "sanabel"), sq("tal"))
        val camps = listOf(camp("c1", "amal", 100), camp("c2", "sahel", 50), camp("c3", "sanabel", 10), camp("c4", "tal", 70))
        val tree = SquareTree.build(squares, camps)
        assertEquals(listOf("sanabel", "tal"), tree.map { it.square.id })
        val s = tree.first()
        assertEquals(160, s.families)
        assertEquals(3, s.campCount)
        assertEquals(listOf("amal", "sahel"), s.subs.map { it.square.id })
    }

    @Test fun orphanOrNestedParentsStayTopLevel() {
        val squares = listOf(sq("a", "missing"), sq("b", "gone"), sq("gone", deleted = 5), sq("p"), sq("c", "p"), sq("d", "c"))
        val tree = SquareTree.build(squares, emptyList())
        assertEquals(setOf("a", "b", "p", "d"), tree.map { it.square.id }.toSet())
        assertEquals(listOf("c"), tree.first { it.square.id == "p" }.subs.map { it.square.id })
    }
}
