package com.codeframe78.twentyfourseven.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaserStageLayoutTest {
    @Test fun allLensesBelongToOneValidBankIncludingOddFinalLens() {
        val visited = mutableListOf<Int>()
        repeat(LaserStageLayout.bankCount) { bank ->
            repeat(2) { lamp ->
                val i = bank * 2 + lamp
                if (i < LaserStageLayout.lensCount) {
                    visited.add(i)
                    assertTrue(LaserStageLayout.lensX(i) in 0f..1f)
                    assertTrue(kotlin.math.abs(LaserStageLayout.lensX(i) - LaserStageLayout.bankX(bank)) <= .0325f)
                }
            }
        }
        assertEquals((0 until LaserStageLayout.lensCount).toList(), visited)
        assertEquals(LaserStageLayout.lensX(18), LaserStageLayout.bankX(9), .00001f)
    }
}
