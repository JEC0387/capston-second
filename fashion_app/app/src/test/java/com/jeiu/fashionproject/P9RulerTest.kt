package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import org.junit.Test
import org.junit.Assert.*

class P9RulerTest {
    @Test fun dragDirectionScaleSnappingAndBoundsAreExplicit() {
        val range=40f..180f
        assertEquals(77.5f,CmRulerMath.drag(74f,-56f,16f,range))
        assertEquals(70.5f,CmRulerMath.drag(74f,56f,16f,range))
        assertEquals(180f,CmRulerMath.drag(74f,-100000f,16f,range))
        assertEquals(40f,CmRulerMath.drag(74f,100000f,16f,range))
        for(delta in -500..500)assertEquals(0f,(CmRulerMath.drag(74f,delta.toFloat(),16f,range)*2)%1,.0001f)
    }
    @Test fun slowAndFastSamplingReachTheSameFinalValue() {
        val start=74f;var amount=0f;var slow=start
        repeat(112) {amount-=.5f;slow=CmRulerMath.drag(start,amount,16f,40f..180f)}
        assertEquals(slow,CmRulerMath.drag(start,-56f,16f,40f..180f))
    }
    @Test fun directInputUsesTheSameHalfCmStateAndRejectsMalformedNumbers() {
        assertEquals(77.5f,CmRulerMath.parse("77.5",40f..180f))
        for(text in listOf("","NaN","Infinity","-1","77.3","181","7e1","0777"))assertNull(text,CmRulerMath.parse(text,40f..180f))
        assertTrue(runCatching {CmRulerMath.drag(74f,1f,0f,40f..180f)}.isFailure)
    }
    @Test fun dynamicRangesNeverCreateUnsupportedBodyCombinations() {
        val body=AvatarCentimeters.generate(170f,65f,1)!!
        for(part in AvatarPart.entries) {
            val range=CmRulerMath.range(body,part)
            assertNotNull("minimum $part",body.adjusted(part,range.start))
            assertNotNull("maximum $part",body.adjusted(part,range.endInclusive))
            assertEquals(body,body.adjusted(part,body[part]))
        }
    }
    @Test fun adjustmentsKeepAutoAndImportedSourcesSeparate() {
        val original=AvatarCentimeters.generate(170f,65f,1)!!
        val adjusted=original.adjusted(AvatarPart.WAIST,77.5f)!!
        assertEquals(77.5f,adjusted[AvatarPart.WAIST]);assertEquals(original.initial,adjusted.initial)
        assertEquals(if(original[AvatarPart.WAIST]==77.5f)AvatarValueOrigin.AUTO else AvatarValueOrigin.ADJUSTED,
            adjusted.values.getValue(AvatarPart.WAIST).origin)
        assertEquals(original.values,adjusted.resetAll().values)
    }
}
