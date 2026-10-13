package com.jeiu.fashionproject.data

import android.content.Context

/**
 * 샘플 옷의 "지난 기록" (계획 3 — 옷장).
 *
 * 앱을 처음 켤 때 한 번만, 샘플 옷 12벌이 약 6개월 전부터 옷장에 있었고 그동안 입어 온 것처럼 만든다.
 * 그래서 설치하자마자 달력·TOP 5·"3개월 넘게 안 입은 옷" 알림을 바로 볼 수 있다. (날짜는 처음 켠 날 기준)
 *  - 최근에 자주 입은 옷: 베이지 니트, 블랙 슬랙스, 체크 셔츠, 스트라이프 블라우스, 스니커즈
 *  - 오래 안 입은 옷(알림 대상): 와이드 청바지, 카고 팬츠, 블랙 미니 스커트(한 번도 안 입음), 베이지 블레이저, 로퍼
 *  - 오래 안 입었지만 계절이 안 맞아 빠지는 옷: 화이트 반팔티(여름), 데님 반팔 셔츠(여름) — 가을 기준
 * 이미 설치된 앱에서는 샘플 옷 등록일만 옮기고, 기록이 없는 날짜에만 기록을 넣는다. (직접 등록한 옷·기록은 건드리지 않음)
 * 처음 상태로 되돌리려면 앱 데이터를 초기화한다.
 */
object SampleWearHistory {
    private const val PREFS = "sample_wear_history"
    private const val KEY_APPLIED = "applied"
    private const val SAMPLE_AGE_DAYS = 200

    private var running = false

    /** (며칠 전, 입은 샘플 옷 번호들) */
    private val pattern: List<Pair<Int, List<String>>> = listOf(
        // 최근 두 달 — 자주 입는 옷
        2 to listOf("sample_01", "sample_06", "sample_11"),
        5 to listOf("sample_04", "sample_06", "sample_11"),
        8 to listOf("sample_02", "sample_06", "sample_11"),
        12 to listOf("sample_01", "sample_06", "sample_11"),
        15 to listOf("sample_04", "sample_06", "sample_11"),
        19 to listOf("sample_01", "sample_06", "sample_11"),
        27 to listOf("sample_02", "sample_06", "sample_11"),
        33 to listOf("sample_01", "sample_04", "sample_11"),
        45 to listOf("sample_02", "sample_06", "sample_11"),
        58 to listOf("sample_01", "sample_06", "sample_11"),
        // 3개월 이상 전 — 이후로 안 입은 옷
        100 to listOf("sample_03", "sample_06"),                // 화이트 반팔티 (여름 옷)
        110 to listOf("sample_04", "sample_06", "sample_12"),   // 로퍼
        125 to listOf("sample_02", "sample_05", "sample_11"),   // 와이드 청바지
        140 to listOf("sample_10", "sample_06", "sample_11"),   // 데님 반팔 셔츠 (여름 옷)
        150 to listOf("sample_01", "sample_07", "sample_11"),   // 카고 팬츠
        175 to listOf("sample_09", "sample_05", "sample_12"),   // 베이지 블레이저
    )

    /** 앱을 켤 때 부른다. 처음 한 번만 샘플 등록일을 옮기고 지난 착용 기록을 넣는다. */
    fun applyOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (running || prefs.getBoolean(KEY_APPLIED, false)) return
        running = true
        try {
            WardrobeStore.load(context)
            WearLogStore.load(context)
            val sampleIds = SampleWardrobe.items().map { it.id }.toSet()
            val samples = WardrobeStore.items.value.filter { it.id in sampleIds }

            val oldAddedAt = System.currentTimeMillis() - SAMPLE_AGE_DAYS * 86_400_000L
            samples.forEach { WardrobeStore.update(context, it.copy(addedAt = oldAddedAt)) }

            val alive = samples.map { it.id }.toSet()   // 지운 샘플 옷은 기록에서 뺀다
            val logs = pattern.mapNotNull { (daysAgo, ids) ->
                val worn = ids.filter { it in alive }
                if (worn.isEmpty()) null else WearLog(WearDates.daysAgo(daysAgo), worn)
            }
            WearLogStore.addIfAbsent(context, logs)

            prefs.edit().putBoolean(KEY_APPLIED, true).apply()
            // 예전 [시연용 기록 넣기] 버튼이 남긴 백업은 더 이상 쓰지 않는다
            context.getSharedPreferences("demo_wear_data", Context.MODE_PRIVATE).edit().clear().apply()
        } finally {
            running = false
        }
    }
}
