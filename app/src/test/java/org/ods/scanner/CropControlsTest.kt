package org.ods.scanner

import org.junit.Assert.*
import org.junit.Test

class CropControlsTest {
    private val crop = Crop(listOf(Corner(.1f,.1f), Corner(.9f,.1f), Corner(.9f,.9f), Corner(.1f,.9f)))
    @Test fun draggingEachEdgeMovesExactlyItsTwoCorners() {
        for (edge in 0..3) {
            val moved = crop.move(edge+4,.02f,.03f)
            assertTrue(moved.valid())
            for (corner in 0..3) {
                if (corner == edge || corner == (edge+1)%4) {
                    assertEquals(crop.points[corner].x+.02f,moved.points[corner].x,.00001f)
                    assertEquals(crop.points[corner].y+.03f,moved.points[corner].y,.00001f)
                } else assertEquals(crop.points[corner],moved.points[corner])
            }
        }
    }
    @Test fun arbitraryDragsNeverProduceInvalidCrop() {
        val random = java.util.Random(42)
        var value = crop
        repeat(5000) {
            value = value.move(random.nextInt(8),random.nextFloat()-.5f,random.nextFloat()-.5f)
            assertTrue(value.valid())
        }
    }
    @Test fun autoCaptureRequiresSharpStableFramingAndRearmsAfterLeaving() {
        val gate = AutoCaptureGate()
        assertFalse(gate.update(crop,false,0)); assertFalse(gate.update(crop,false,1000))
        assertFalse(gate.update(crop,true,1100)); assertTrue(gate.update(crop,true,1700))
        assertFalse(gate.update(crop,true,3000)); assertFalse(gate.update(null,true,3100))
        assertFalse(gate.update(crop,true,3200)); assertTrue(gate.update(crop,true,3800))
    }
}
