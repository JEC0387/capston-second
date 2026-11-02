package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.SampleWardrobe
import org.junit.Assert.*
import org.junit.Test

class SampleWardrobePreviewTest {
    private val oldPrefix = "android.resource://com.jeiu.fashionproject/drawable/"
    private val newPackage = "com.jeiu.fashionproject.preview"
    private val newPrefix = "android.resource://$newPackage/drawable/"

    @Test fun bundledSamplesUseTheBuiltApplicationId() {
        assertEquals(12, SampleWardrobe.items().size)
        SampleWardrobe.items().forEach { item ->
            item.photoUris().forEach { uri ->
                assertTrue("Bundled resource should belong to current package: $uri",
                    uri.startsWith("android.resource://${BuildConfig.APPLICATION_ID}/drawable/"))
            }
        }
    }

    @Test fun oldBundledUriIsRebasedWithoutChangingTheUsersOtherFields() {
        val bundled = SampleWardrobe.items().first { it.id == "sample_04" }
        val saved = bundled.copy(
            name = "내가 수정한 체크셔츠", imageUri = oldPrefix + "sample_04",
            originalUri = oldPrefix + "sample_04", favoritedAt = 1234L, keptAt = 5678L
        )
        val fixed = SampleWardrobe.rebaseBundledUris(saved, bundled, newPackage)
        assertEquals(newPrefix + "sample_04", fixed.imageUri)
        assertEquals(newPrefix + "sample_04", fixed.originalUri)
        assertEquals(saved.name, fixed.name)
        assertEquals(saved.favoritedAt, fixed.favoritedAt)
        assertEquals(saved.keptAt, fixed.keptAt)
        assertEquals(fixed, SampleWardrobe.rebaseBundledUris(fixed, bundled, newPackage))
    }

    @Test fun userPhotosAndUnknownResourceNamesRemainUntouched() {
        val bundled = SampleWardrobe.items().first { it.id == "sample_05" }
        val personal = bundled.copy(imageUri = "content://photos/my-item", originalUri = "file:///private/custom.jpg")
        assertEquals(personal, SampleWardrobe.rebaseBundledUris(personal, bundled, newPackage))
        val unknown = bundled.copy(imageUri = oldPrefix + "not_a_sample")
        assertEquals(unknown, SampleWardrobe.rebaseBundledUris(unknown, bundled, newPackage))
        val different = bundled.copy(id = "user-owned")
        assertEquals(different, SampleWardrobe.rebaseBundledUris(different, bundled, newPackage))
    }

    @Test fun bundledOriginalAndGarmentVersionsAreBothRebased() {
        val bundled = SampleWardrobe.items().first { it.id == "sample_01" }
        val old = bundled.copy(imageUri = oldPrefix + "sample_01",
            originalUri = oldPrefix + "casual_daily_look",
            garmentUri = oldPrefix + "sample_01")
        val fixed = SampleWardrobe.rebaseBundledUris(old, bundled, newPackage)
        assertEquals(newPrefix + "sample_01", fixed.imageUri)
        assertEquals(newPrefix + "casual_daily_look", fixed.originalUri)
        assertEquals(newPrefix + "sample_01", fixed.garmentUri)
    }
}
