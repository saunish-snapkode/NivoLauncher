package com.snapkode.nivolauncher.ui.pagelet

import com.snapkode.nivolauncher.ui.pagelet.sky.fitByPriority
import org.junit.Assert.assertEquals
import org.junit.Test

class PriorityFitTest {
    @Test fun everythingFitsWhenThereIsRoom() = assertEquals(setOf(0, 1, 2), fitByPriority(listOf(10, 10, 10), listOf(0, 1, 2), 100))

    @Test fun theLeastImportantRowIsDroppedFirst() =
        assertEquals(setOf(0, 1), fitByPriority(listOf(40, 40, 40), listOf(0, 1, 2), 90))

    @Test fun importanceBeatsOrder() =
        assertEquals("the later but more important row wins", setOf(1), fitByPriority(listOf(60, 60), listOf(5, 1), 70))

    @Test fun aRowThatDoesNotFitDoesNotBlockSmallerOnesAfterIt() =
        assertEquals(setOf(0, 2), fitByPriority(listOf(30, 80, 20), listOf(0, 1, 2), 60))

    @Test fun nothingFitsMeansNothingIsShown() = assertEquals(emptySet<Int>(), fitByPriority(listOf(50), listOf(0), 10))

    @Test fun equalImportanceKeepsTheEarlierRow() = assertEquals(setOf(0), fitByPriority(listOf(50, 50), listOf(1, 1), 60))

    @Test fun anExactFitIsKept() = assertEquals(setOf(0, 1), fitByPriority(listOf(30, 30), listOf(0, 1), 60))
}
