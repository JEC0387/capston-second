package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.BodyRecord
import com.jeiu.fashionproject.ui.viewmodel.BodyHistoryAnalyzer
import com.jeiu.fashionproject.ui.viewmodel.BodyMetric
import org.junit.Assert.*
import org.junit.Test

class BodyHistoryAnalyzerTest {
    private fun record(
        id: Int, at: Long, type: String = "직사각형",
        shoulderToHip: Float? = 1.10f, waistToHip: Float? = 0.86f, waistCm: Float? = 30.0f
    ) = BodyRecord(id, type, 80, shoulderToHip, waistToHip, 0.47f, 170f, 42f, waistCm, 35f, 80f, at)

    @Test fun noRecordsAndFirstRecordMessages() {
        assertTrue(BodyHistoryAnalyzer.summary(emptyList()).contains("아직 측정 기록이 없어요"))
        assertTrue(BodyHistoryAnalyzer.summary(listOf(record(1, 1000))).contains("첫 기록"))
        assertTrue(BodyHistoryAnalyzer.compareLatest(listOf(record(1, 1000))).isEmpty())
    }

    @Test fun comparesLatestWithPreviousRegardlessOfInputOrder() {
        // 서버는 최근 것부터 준다 — 순서와 상관없이 시간순으로 비교해야 함
        val records = listOf(record(2, 2000, waistToHip = 0.84f, waistCm = 28.8f), record(1, 1000))
        val waist = BodyHistoryAnalyzer.compareLatest(records).first { it.metric == BodyMetric.WAIST_HIP }
        assertEquals(0.86f, waist.before, 0.0001f)
        assertEquals(0.84f, waist.after, 0.0001f)
        assertTrue(waist.significant)
        assertTrue(waist.text.contains("−2%"))
        val summary = BodyHistoryAnalyzer.summary(records)
        assertTrue(summary.startsWith("지난번보다"))
        assertTrue(summary.contains("허리:엉덩이 −2%"))
        assertTrue(summary.contains("허리 너비 −1.2cm"))
    }

    @Test fun smallChangesAreTreatedAsNoChange() {
        val records = listOf(record(1, 1000), record(2, 2000, waistToHip = 0.865f, waistCm = 30.3f))
        assertTrue(BodyHistoryAnalyzer.compareLatest(records).none { it.significant })
        assertEquals("지난번과 거의 같아요.", BodyHistoryAnalyzer.summary(records))
    }

    @Test fun bodyTypeChangeIsMentioned() {
        val records = listOf(record(1, 1000, type = "직사각형"), record(2, 2000, type = "모래시계형"))
        assertTrue(BodyHistoryAnalyzer.summary(records).startsWith("사진 추정 분류가 직사각형 → 모래시계형"))
    }

    @Test fun seriesSkipsMissingValuesAndKeepsTimeOrder() {
        val records = listOf(record(3, 3000), record(1, 1000), record(2, 2000, waistCm = null))
        val points = BodyHistoryAnalyzer.series(records, BodyMetric.WAIST_CM)
        assertEquals(listOf(1, 3), points.map { it.first.id })
    }

    @Test fun ratioAndCmFormatting() {
        assertEquals("0.86", BodyMetric.WAIST_HIP.format(0.857f))
        assertEquals("30.0cm", BodyMetric.WAIST_CM.format(30f))
    }
}
