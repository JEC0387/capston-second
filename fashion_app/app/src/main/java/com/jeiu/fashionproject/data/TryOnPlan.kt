package com.jeiu.fashionproject.data

import java.net.URI
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

data class TryOnGarment(val id: String, val name: String, val category: String, val image: String)

data class TryOnPlan(val garments: List<TryOnGarment>, val unsupported: List<TryOnGarment>, val issue: String?) {
    val requestCount: Int get() = garments.size
    companion object {
        fun categoryFor(category: String): String? = when (category) {
            "상의", "아우터" -> "tops"
            "하의" -> "bottoms"
            "원피스" -> "one-pieces"
            else -> null
        }

        fun build(selected: List<TryOnGarment>): TryOnPlan {
            val supported = selected.filter { categoryFor(it.category) != null }
            val unsupported = selected.filter { categoryFor(it.category) == null }
            val issue = when {
                supported.isEmpty() -> "AI로 입어볼 상의·하의 또는 원피스를 선택해주세요."
                supported.any { it.image.isBlank() } -> "선택한 옷의 사진을 찾을 수 없어요."
                supported.any { it.category == "원피스" } && supported.size > 1 -> "원피스는 단독으로 선택해주세요."
                supported.groupBy { categoryFor(it.category) }.any { it.value.size > 1 } -> "AI 피팅은 상의·아우터 중 한 벌과 하의 한 벌을 선택해주세요."
                else -> null
            }
            return TryOnPlan(supported.sortedBy { if (categoryFor(it.category) == "bottoms") 1 else 0 }, unsupported, issue)
        }
    }
}

class TryOnFailure(message: String) : Exception(message)

/** One generation per garment. No automatic retry of a paid generation request. */
object TryOnBatch {
    suspend fun run(
        plan: TryOnPlan,
        person: String,
        generate: suspend (String, TryOnGarment) -> String,
        progress: (Int, Int) -> Unit = { _, _ -> }
    ): String {
        if (plan.issue != null || plan.garments.isEmpty() || person.isBlank())
            throw TryOnFailure(plan.issue ?: "전신 사진과 입어볼 옷을 선택해주세요.")
        var current = person
        for ((index, garment) in plan.garments.withIndex()) {
            coroutineContext.ensureActive()
            progress(index + 1, plan.requestCount)
            val generated = generate(current, garment)
            coroutineContext.ensureActive()
            if (!isResultUrl(generated)) throw TryOnFailure("AI 결과 이미지 주소를 확인하지 못했어요.")
            current = generated
        }
        return current
    }

    fun isResultUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
