package com.jeiu.fashionproject.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.TastePreferenceStore
import com.jeiu.fashionproject.recommend.PreferredStyles
import com.jeiu.fashionproject.ui.components.OutfitPhotoImage
import com.jeiu.fashionproject.ui.components.outfitQuery
import com.jeiu.fashionproject.ui.components.rememberOutfitPhoto
import com.jeiu.fashionproject.ui.theme.*
import kotlin.math.abs

/**
 * 스타일 취향 학습 화면.
 *
 * 8가지 스타일(캐주얼 · 스트릿 · 미니멀 · 댄디 · 아메카지 · 유니크 · 스포티 · 포멀)의 코디 사진 카드를
 * 좋아요(+2) / 싫어요(−1)로 평가하면 스타일 점수와 색 · 종류 · 조합 방식 취향이 쌓인다.
 * 쌓인 점수는 OutfitEngine 코디 점수에 바로 반영된다 — 좋아한 스타일에 맞는 옷이 많은 코디일수록 위로.
 * 사진은 성별 + 스타일 검색어로 네이버 이미지 검색에서 가져온다 (서버가 꺼져 있으면 스타일 색 카드로 대신).
 */
private data class PrefStyleCard(
    val style: String, val title: String, val desc: String, val query: String,
    val colors: List<String>, val kinds: List<String>, val pattern: String
)

/** 스타일마다 카드 색 (사진을 못 불러올 때의 바탕 · 선호도 막대 색) */
private val prefStyleColors = mapOf(
    "캐주얼" to Color(0xFF8BA888),
    "스트릿" to Color(0xFFC88FA8),
    "미니멀" to Color(0xFFB59B80),
    "댄디" to Color(0xFF6F86A6),
    "아메카지" to Color(0xFFA9824F),
    "유니크" to Color(0xFFD58A5E),
    "스포티" to Color(0xFF5FA3A3),
    "포멀" to Color(0xFF4F5A6E)
)

private fun colorOf(style: String) = prefStyleColors[style] ?: Camel

// 스타일마다 2장 — 1바퀴(8스타일 한 장씩) → 2바퀴 순서로 섞어 한 스타일이 연달아 나오지 않게 한다
private val firstRound = listOf(
    PrefStyleCard("캐주얼", "데일리 캐주얼", "맨투맨 + 데님", "캐주얼룩 맨투맨 청바지 코디",
        listOf("그레이", "블루"), listOf("맨투맨", "청바지"), "무채색 조합"),
    PrefStyleCard("댄디", "댄디 니트", "니트 + 슬랙스 + 로퍼", "댄디룩 니트 슬랙스 로퍼 코디",
        listOf("네이비", "베이지"), listOf("니트", "슬랙스"), "톤온톤"),
    PrefStyleCard("스트릿", "스트릿 무드", "후드 + 카고팬츠", "스트릿룩 후드티 카고팬츠 코디",
        listOf("블랙", "그레이"), listOf("후드티", "면바지"), "무채색 조합"),
    PrefStyleCard("미니멀", "미니멀 셋업", "셔츠 + 와이드 슬랙스", "미니멀룩 셔츠 슬랙스 코디",
        listOf("화이트", "블랙"), listOf("셔츠", "슬랙스"), "무채색 조합"),
    PrefStyleCard("아메카지", "아메카지 워크", "워크 자켓 + 데님", "아메카지 워크자켓 데님 코디",
        listOf("브라운", "블루"), listOf("자켓", "청바지"), "톤온톤"),
    PrefStyleCard("스포티", "애슬레저", "트레이닝 셋업 + 운동화", "스포티룩 트레이닝 셋업 코디",
        listOf("블랙", "화이트"), listOf("트레이닝팬츠", "운동화"), "무채색 조합"),
    PrefStyleCard("유니크", "컬러 포인트", "컬러 니트 + 와이드 팬츠", "유니크 패션 컬러 포인트 코디",
        listOf("옐로우", "블랙"), listOf("니트", "슬랙스"), "포인트 컬러"),
    PrefStyleCard("포멀", "포멀 수트", "테일러드 자켓 + 슬랙스", "포멀룩 자켓 슬랙스 코디",
        listOf("네이비", "그레이"), listOf("자켓", "슬랙스"), "무채색 조합"),
)
private val secondRound = listOf(
    PrefStyleCard("스트릿", "오버핏 스트릿", "그래픽 티 + 와이드 데님", "스트릿 패션 오버핏 그래픽티 와이드팬츠",
        listOf("블랙", "블루"), listOf("반팔티", "청바지"), "포인트 컬러"),
    PrefStyleCard("포멀", "오피스 포멀", "셔츠 + 울 코트 + 구두", "오피스룩 코트 셔츠 코디",
        listOf("화이트", "베이지"), listOf("셔츠", "코트"), "톤온톤"),
    PrefStyleCard("캐주얼", "주말 캐주얼", "반팔티 + 면바지", "캐주얼룩 반팔티 면바지 코디",
        listOf("화이트", "베이지"), listOf("반팔티", "면바지"), "톤온톤"),
    PrefStyleCard("유니크", "패턴 믹스", "패턴 셔츠 + 데님", "유니크룩 패턴 셔츠 코디",
        listOf("퍼플", "블루"), listOf("셔츠", "청바지"), "포인트 컬러"),
    PrefStyleCard("미니멀", "톤온톤 미니멀", "베이지 니트 + 크림 팬츠", "미니멀룩 니트 톤온톤 코디",
        listOf("베이지", "아이보리"), listOf("니트", "슬랙스"), "톤온톤"),
    PrefStyleCard("아메카지", "아메카지 체크", "체크 셔츠 + 카고 + 부츠", "아메카지룩 체크셔츠 코디",
        listOf("레드", "카키"), listOf("셔츠", "면바지"), "포인트 컬러"),
    PrefStyleCard("댄디", "댄디 셔츠", "옥스퍼드 셔츠 + 치노 팬츠", "댄디룩 셔츠 면바지 코디",
        listOf("블루", "베이지"), listOf("셔츠", "면바지"), "포인트 컬러"),
    PrefStyleCard("스포티", "고프코어", "바람막이 + 조거 팬츠", "고프코어 바람막이 코디",
        listOf("그린", "블랙"), listOf("점퍼", "트레이닝팬츠"), "포인트 컬러"),
)
private val prefStyleCards = firstRound + secondRound

/** 여성이면 일부 카드를 여성 코디로 바꾼다 (사진 검색어에는 성별이 붙어 남녀 각각 맞는 사진이 나온다) */
private val femaleVariants = mapOf(
    "주말 캐주얼" to PrefStyleCard("캐주얼", "주말 캐주얼", "반팔티 + 데님 스커트", "캐주얼룩 반팔티 데님스커트 코디",
        listOf("화이트", "블루"), listOf("반팔티", "미니스커트"), "톤온톤"),
    "톤온톤 미니멀" to PrefStyleCard("미니멀", "톤온톤 미니멀", "베이지 니트 + 롱스커트", "미니멀룩 니트 롱스커트 코디",
        listOf("베이지", "아이보리"), listOf("니트", "롱스커트"), "톤온톤"),
    "오피스 포멀" to PrefStyleCard("포멀", "오피스 포멀", "블라우스 + 슬랙스 + 코트", "오피스룩 블라우스 슬랙스 코디",
        listOf("화이트", "베이지"), listOf("블라우스", "슬랙스"), "톤온톤"),
    "패턴 믹스" to PrefStyleCard("유니크", "플라워 원피스", "패턴 원피스 + 가디건", "유니크룩 플라워 원피스 코디",
        listOf("퍼플", "화이트"), listOf("긴팔 원피스", "가디건"), "포인트 컬러"),
)

private fun cardsFor(gender: Gender?): List<PrefStyleCard> =
    if (gender == Gender.FEMALE) prefStyleCards.map { femaleVariants[it.title] ?: it } else prefStyleCards

@Composable
fun StylePreferenceScreen(onBack: () -> Unit, gender: Gender? = null) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { StylePreferenceStore.load(context); TastePreferenceStore.load(context) }

    val scores by StylePreferenceStore.scores.collectAsState()
    val ratedCount by StylePreferenceStore.ratedCount.collectAsState()
    val cards = remember(gender) { cardsFor(gender) }
    var cardIndex by remember { mutableIntStateOf(0) }
    val done = cardIndex >= cards.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Column {
                Text("스타일 취향 학습", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text("마음에 드는 코디에 ♥ — 평가한 취향을 규칙 기반 코디 추천에 반영해요", style = MaterialTheme.typography.bodySmall, color = MediumGray)
            }
        }

        if (!done) {
            val card = cards[cardIndex]

            // 진행도
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { cardIndex / cards.size.toFloat() },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = Camel, trackColor = LightGray
                )
                Text("${cardIndex + 1} / ${cards.size}", style = MaterialTheme.typography.labelMedium, color = MediumGray)
            }

            // 코디 카드
            AnimatedContent(targetState = card, label = "styleCard") { c ->
                StyleCardView(c, gender)
            }

            // 싫어요 / 건너뛰기 / 좋아요
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = { rateCard(context, card, like = false); cardIndex++ },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = White)
                ) { Icon(Icons.Filled.Close, contentDescription = "별로예요", tint = MediumGray, modifier = Modifier.size(28.dp)) }

                TextButton(onClick = { cardIndex++ }) { Text("잘 모르겠어요", color = MediumGray) }

                FilledIconButton(
                    onClick = { rateCard(context, card, like = true); cardIndex++ },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Camel)
                ) { Icon(Icons.Filled.Favorite, contentDescription = "좋아요", tint = White, modifier = Modifier.size(28.dp)) }
            }
        } else {
            // 평가 완료 → 결과 요약
            val top = StylePreferenceStore.ranking().filter { it.second > 0 }.take(3).map { it.first }
            Card(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CamelLight)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("학습 완료!", style = MaterialTheme.typography.titleMedium, color = BrownDark, fontWeight = FontWeight.Bold)
                    Text(
                        if (top.isEmpty()) "아직 마음에 든 스타일이 없어요. 한 번 더 평가해 보세요."
                        else "${top.joinToString(" · ")} 스타일을 좋아하시네요.\n이제 홈과 코디 추천에서 이 스타일에 맞는 옷 조합을 먼저 보여 드려요.",
                        style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack
                    )
                }
            }
            OutlinedButton(
                onClick = { cardIndex = 0 },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("한 번 더 평가하기", color = Brown) }
        }

        Spacer(Modifier.height(16.dp))
        StylePreferenceSummary(scores = scores, ratedCount = ratedCount)
        Spacer(Modifier.height(32.dp))
    }
}

/** 코디 사진 카드 — 사진 위에 스타일 · 제목, 아래에 색 · 종류 칩 */
@Composable
private fun StyleCardView(c: PrefStyleCard, gender: Gender?) {
    val accent = colorOf(c.style)
    val photo = rememberOutfitPhoto(null, outfitQuery(gender, c.query))
    Card(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), accent)))
        ) {
            // 사진이 없을 때 보이는 자리 (스타일 이름)
            Text(
                c.style, color = White.copy(alpha = 0.9f), fontSize = 40.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )
            if (photo.primary != null) {
                OutfitPhotoImage(
                    photo = photo, contentDescription = c.title,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            // 아래쪽 글자가 잘 보이게 어둡게
            Box(
                Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
            )
            Box(
                Modifier.padding(14.dp).align(Alignment.TopStart)
                    .clip(RoundedCornerShape(20.dp)).background(accent)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text(c.style, color = White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.title, color = White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(c.desc, color = White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (c.colors + c.kinds + c.pattern).distinct().take(5).forEach { tag ->
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(CreamDark)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) { Text(tag, style = MaterialTheme.typography.labelSmall, color = Brown) }
            }
        }
    }
}

/**
 * 내 스타일 선호도 — 8가지 스타일 점수를 순위 · 막대로.
 * 좋아요가 많을수록 스타일 색 막대가 길어지고, 싫어요가 더 많으면 회색으로 왼쪽에 표시한다.
 */
@Composable
internal fun StylePreferenceSummary(scores: Map<String, Int>, ratedCount: Int, modifier: Modifier = Modifier) {
    val ranked = PreferredStyles.OPTIONS.map { it to (scores[it] ?: 0) }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { PreferredStyles.OPTIONS.indexOf(it.first) })
    val maxAbs = ranked.maxOfOrNull { abs(it.second) }?.coerceAtLeast(1) ?: 1
    val favorite = ranked.firstOrNull()?.takeIf { it.second > 0 }?.first

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("내 스타일 선호도", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                    Text("평가 ${ratedCount}회 · 좋아요 +2 · 별로예요 −1", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                }
                if (favorite != null) {
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(colorOf(favorite).copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("최애 스타일", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        Text(favorite, style = MaterialTheme.typography.titleSmall, color = colorOf(favorite), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (ratedCount == 0 && ranked.all { it.second == 0 }) {
                Text(
                    "아직 평가가 없어요. 위 카드에 ♥ 또는 ✕를 눌러 주세요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center
                )
            }

            ranked.forEachIndexed { index, (style, score) ->
                PreferenceRow(rank = index + 1, style = style, score = score, maxAbs = maxAbs)
            }

            HorizontalDivider(color = LightGray)
            Text(
                "점수가 높은 스타일에 맞는 옷이 많은 코디일수록 홈 · 코디 추천에서 위에 나와요.",
                style = MaterialTheme.typography.labelSmall, color = MediumGray
            )
        }
    }
}

@Composable
private fun PreferenceRow(rank: Int, style: String, score: Int, maxAbs: Int) {
    val accent = colorOf(style)
    val fraction by animateFloatAsState(abs(score) / maxAbs.toFloat(), label = "pref")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (rank <= 3 && score > 0) accent else CreamDark),
            contentAlignment = Alignment.Center
        ) {
            Text("$rank", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (rank <= 3 && score > 0) White else MediumGray)
        }
        Text(style, style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack,
            fontWeight = if (rank == 1 && score > 0) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.width(60.dp))
        Box(
            Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(CreamDark)
        ) {
            if (score != 0) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (score > 0) accent else MediumGray.copy(alpha = 0.5f))
                )
            }
        }
        Text(
            when { score > 0 -> "+$score"; score < 0 -> "−${-score}"; else -> "0" },
            style = MaterialTheme.typography.labelMedium,
            color = when { score > 0 -> accent; score < 0 -> MediumGray; else -> LightGray },
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp)
        )
    }
}

/** 취향 카드 평가 — 스타일(서버 동기화)과 색 · 종류 · 조합 방식(폰 안)을 함께 학습 */
private fun rateCard(context: Context, card: PrefStyleCard, like: Boolean) {
    StylePreferenceStore.rate(context, card.style, like)
    TastePreferenceStore.rateCard(context, card.colors, card.kinds, card.pattern, like, card.style)
}
