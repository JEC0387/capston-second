package com.jeiu.fashionproject.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 착용 기록 1건 — "이 날짜에 이 옷들을 입었다".
 * 하루에 기록은 1건만 두고, 같은 날짜로 다시 기록하면 그날 기록을 고친다.
 * 옷은 WardrobeItem.id로 가리키므로 옷을 지워도 기록은 남는다(화면에서는 지운 옷을 건너뜀).
 */
data class WearLog(
    val date: String,              // "2026-09-26"
    val itemIds: List<String>,
    val memo: String = ""
)

/** 착용 기록 저장소 — WardrobeStore와 같은 방식(StateFlow + SharedPreferences JSON). */
object WearLogStore {
    private const val PREFS = "wear_log_store"
    private const val KEY = "logs"

    private val _logs = MutableStateFlow<List<WearLog>>(emptyList())
    /** 날짜 오름차순 */
    val logs: StateFlow<List<WearLog>> = _logs

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (raw != null) runCatching {
            val arr = JSONArray(raw)
            _logs.value = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("itemIds")
                WearLog(
                    date = o.getString("date"),
                    itemIds = List(ids.length()) { ids.getString(it) },
                    memo = o.optString("memo", "")
                )
            }.sortedBy { it.date }
        }
        // 처음 켤 때 한 번만 샘플 옷의 지난 착용 기록을 넣는다
        SampleWearHistory.applyOnce(context)
    }

    fun logFor(date: String): WearLog? = _logs.value.find { it.date == date }

    /** 그날 기록을 새로 쓰거나 고친다. 옷을 하나도 안 고르면 그날 기록을 지운다. */
    fun save(context: Context, log: WearLog) {
        val others = _logs.value.filterNot { it.date == log.date }
        _logs.value = (if (log.itemIds.isEmpty()) others else others + log).sortedBy { it.date }
        persist(context)
    }

    fun delete(context: Context, date: String) {
        _logs.value = _logs.value.filterNot { it.date == date }
        persist(context)
    }

    /** 기록이 없는 날짜에만 한꺼번에 넣는다 (직접 기록한 날은 건드리지 않음). 넣은 개수를 돌려준다. */
    fun addIfAbsent(context: Context, newLogs: List<WearLog>): Int {
        val taken = _logs.value.map { it.date }.toSet()
        val toAdd = newLogs.filter { it.date !in taken }.distinctBy { it.date }
        _logs.value = (_logs.value + toAdd).sortedBy { it.date }
        persist(context)
        return toAdd.size
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        _logs.value.forEach { log ->
            arr.put(
                JSONObject()
                    .put("date", log.date)
                    .put("itemIds", JSONArray(log.itemIds))
                    .put("memo", log.memo)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}

/**
 * 날짜 도우미 — 날짜는 "yyyy-MM-dd" 문자열로 다룬다.
 * 앱이 안드로이드 7(API 24)까지 지원해서 java.time 대신 Calendar를 쓴다.
 */
object WearDates {
    private fun format() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = of(Calendar.getInstance())

    fun of(cal: Calendar): String = format().format(cal.time)

    fun fromMillis(ms: Long): String = of(Calendar.getInstance().apply { timeInMillis = ms })

    /** 오늘로부터 n일 전 */
    fun daysAgo(n: Int): String = of(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -n) })

    fun toCalendar(date: String): Calendar =
        Calendar.getInstance().apply { time = format().parse(date)!! }

    fun of(year: Int, month0: Int, day: Int): String =
        of(Calendar.getInstance().apply { clear(); set(year, month0, day) })

    /** 두 날짜 사이 일수 (to − from) */
    fun daysBetween(from: String, to: String): Int {
        val ms = toCalendar(to).timeInMillis - toCalendar(from).timeInMillis
        return Math.round(ms / 86_400_000.0).toInt()
    }

    private val weekdays = listOf("일", "월", "화", "수", "목", "금", "토")

    /** "9월 26일 (토)" */
    fun label(date: String): String {
        val c = toCalendar(date)
        return "${c.get(Calendar.MONTH) + 1}월 ${c.get(Calendar.DAY_OF_MONTH)}일 (${weekdays[c.get(Calendar.DAY_OF_WEEK) - 1]})"
    }

    /** "오늘" / "어제" / "3일 전" / "2개월 전" */
    fun ago(date: String): String {
        val d = daysBetween(date, today())
        return when {
            d <= 0 -> "오늘"
            d == 1 -> "어제"
            d < 30 -> "${d}일 전"
            d < 365 -> "${d / 30}개월 전"
            else -> "${d / 365}년 전"
        }
    }
}
