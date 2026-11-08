package org.ods.scanner

import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun fullImagePreservesUnitGeometry() {
        assertTrue(Crop.Full.valid())
        assertEquals(1f, Crop.Full.area(), .0001f)
        assertEquals(Crop.Full, Crop.decode(Crop.Full.encode()))
    }
    @Test fun rejectsCrossedCorners() {
        assertFalse(Crop(listOf(Corner(0f, 0f), Corner(1f, 1f), Corner(1f, 0f), Corner(0f, 1f))).valid())
    }
    @Test fun rejectsConcaveCollapsedAndUnboundedGeometry() {
        assertFalse(Crop(listOf(Corner(0f, 0f), Corner(1f, 0f), Corner(.1f, .1f), Corner(0f, 1f))).valid())
        assertFalse(Crop(List(4) { Corner(.5f, .5f) }).valid())
        assertFalse(Crop(Crop.Full.points.toMutableList().apply { set(0, Corner(-.1f, 0f)) }).valid())
        assertFalse(Crop(Crop.Full.points.toMutableList().apply { set(0, Corner(Float.NaN, 0f)) }).valid())
    }
    @Test fun malformedPersistedCropFallsBackSafely() {
        listOf("", "0,0", "NaN,0;1,0;1,1;0,1", "a,b;c,d").forEach { assertEquals(Crop.Full, Crop.decode(it)) }
    }
    @Test fun filenamesCannotEscapeExportDirectory() {
        assertEquals("_.._invoice", safeName("../../invoice"))
        assertEquals("Document", safeName("..."))
        assertEquals("हिंदी invoice", safeName("हिंदी invoice"))
        assertTrue(safeName("x".repeat(200)).length <= 80)
    }
}
