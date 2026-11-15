package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses the actual mesh builder consumed by AvatarSurface, not a label or fake renderer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class P70LiveAvatarGeometryTest {
    @Test fun approvedBodyTwoCmChangesDeformMeshAndReturnExactlyWithoutChangingTapeReferences() {
        val measured = mapOf(AvatarPart.CHEST to 88f, AvatarPart.WAIST to 71f,
            AvatarPart.HIP to 95f, AvatarPart.SHOULDER to 39f)
        val profile = checkNotNull(AvatarAnalysisInput.build(160f,55f,measured,null,1L))
        val body = checkNotNull(profile.parameters().centimeters)
        val original = PremiumAvatarMesh.build(body)
        for (part in measured.keys) {
            val changed = checkNotNull(body.adjusted(part, body[part] + 2f))
            val mesh = PremiumAvatarMesh.build(changed)
            assertFalse("$part actual vertex array must change", original.contentEquals(mesh))
            assertTrue(mesh.all(Float::isFinite))
            assertEquals(body.references, changed.references)
            if (part != AvatarPart.SHOULDER) {
                fun perimeter(b: AvatarCentimeters, data: FloatArray): Float {
                    val section = PremiumAvatarMesh.Shape(b).section(part)
                    return P9GeometryTest.sectionPerimeter(data,(section.y-.5f)*b.heightCm/100,0f)
                }
                val delta = perimeter(changed,mesh) - perimeter(body,original)
                assertEquals("$part measured cross-section change",2f,delta,.5f)
            } else {
                fun shoulderWidth(b: AvatarCentimeters, data: FloatArray): Float {
                    val y = (PremiumAvatarMesh.Shape(b).shoulder - .53f) * b.heightCm / 100f
                    return data.indices.step(9).filter { kotlin.math.abs(data[it+1]-y)<.015f }
                        .maxOf { kotlin.math.abs(data[it]) } * 2f
                }
                assertTrue(shoulderWidth(changed,mesh) > shoulderWidth(body,original))
            }
            val restored = checkNotNull(changed.adjusted(part,body[part]))
            assertArrayEquals("$part restored geometry",original,PremiumAvatarMesh.build(restored),0f)
        }
    }
}
