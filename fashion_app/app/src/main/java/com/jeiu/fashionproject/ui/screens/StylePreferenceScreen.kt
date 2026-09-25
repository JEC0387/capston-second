package com.jeiu.fashionproject.ui.screens

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.ui.theme.*

/**
 * 스타일 취향 학습 화면 (5주차 보고서: 인터랙티브 취향 학습 시스템).
 *
 * 코디 카드를 좋아요/싫어요로 평가하면 스타일별 점수가 실시간으로 갱신되고,
 * 평가가 쌓일수록 '내 선호 스타일' 순위가 뚜렷해진다.
 */
private data class PrefStyleCard(val style: String, val title: String, val desc: String, val imageUrl: String, val accent: Color)

private val prefStyleCards = listOf(
    PrefStyleCard("미니멀", "미니멀 셋업", "오버핏 셔츠 + 와이드 슬랙스", "https://loremflickr.com/400/520/minimal,fashion?lock=70", StyleMinimal),
    PrefStyleCard("캐주얼", "데일리 캐주얼", "그래픽 티 + 데님", "https://loremflickr.com/400/520/casual,outfit?lock=71", StyleCasual),
    PrefStyleCard("클래식", "클래식 포멀", "테일러드 자켓 + 슬랙스", "https://loremflickr.com/400/520/classic,suit?lock=72", StyleClassic),
    PrefStyleCard("스트릿", "스트릿 무드", "후드 + 카고팬츠", "https://loremflickr.com/400/520/street,fashion?lock=73", StyleStreet),
    PrefStyleCard("빈티지", "빈티지 감성", "니트 베스트 + 코듀로이", "https://loremflickr.com/400/520/vintage,clothing?lock=74", StyleVintage),
    PrefStyleCard("미니멀", "톤온톤 미니멀", "베이지 니트 + 크림 팬츠", "https://loremflickr.com/400/520/beige,knit?lock=75", StyleMinimal),
    PrefStyleCard("캐주얼", "주말 캐주얼", "맨투맨 + 조거팬츠", "https://loremflickr.com/400/520/sweatshirt,casual?lock=76", StyleCasual),
    PrefStyleCard("스트릿", "테크웨어 스트릿", "윈드브레이커 + 나일론 팬츠", "https://loremflickr.com/400/520/techwear,street?lock=77", StyleStreet),
    PrefStyleCard("클래식", "오피스 클래식", "셔츠 + 울 코트", "https://loremflickr.com/400/520/office,coat?lock=78", StyleClassic),
    PrefStyleCard("빈티지", "레트로 빈티지", "체크 셔츠 + 와이드 데님", "https://loremflickr.com/400/520/retro,shirt?lock=79", StyleVintage),
)

@Composable
fun StylePreferenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { StylePreferenceStore.load(context) }

    val scores by StylePreferenceStore.scores.collectAsState()
    val ratedCount by StylePreferenceStore.ratedCount.collectAsState()
    var cardIndex by remember { mutableIntStateOf(0) }
    val done = cardIndex >= prefStyleCards.size

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
                Text("취향 학습", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                Text("평가할수록 추천이 정확해져요 · 누적 ${ratedCount}회", style = MaterialTheme.typography.bodySmall, color = MediumGray)
            }
        }

        if (!done) {
            val card = prefStyleCards[cardIndex]

            // 진행도
            LinearProgressIndicator(
                progress = { cardIndex / prefStyleCards.size.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(4.dp)),
                color = Camel, trackColor = LightGray
            )

            // 코디 카드
            AnimatedContent(targetState = card, label = "styleCard") { c ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column {
                        AsyncImage(
                            model = c.imageUrl,
                            contentDescription = c.title,
                            modifier = Modifier.fillMaxWidth().height(360.dp).background(LightGray),
                            contentScale = ContentScale.Crop
                        )
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
                                Text(c.desc, style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(c.style, color = White, style = MaterialTheme.typography.labelMedium) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = c.accent),
                                border = null
                            )
                        }
                    }
                }
            }

            // 좋아요 / 싫어요
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilledIconButton(
                    onClick = { StylePreferenceStore.rate(context, card.style, like = false); cardIndex++ },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = LightGray)
                ) { Icon(Icons.Filled.Close, contentDescription = "싫어요", tint = MediumGray) }

                FilledIconButton(
                    onClick = { StylePreferenceStore.rate(context, card.style, like = true); cardIndex++ },
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Camel)
                ) { Icon(Icons.Filled.Favorite, contentDescription = "좋아요", tint = White) }
            }
        } else {
            // 평가 완료 → 결과 요약
            Card(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CamelLight)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("학습 완료!", style = MaterialTheme.typography.titleMedium, color = BrownDark, fontWeight = FontWeight.Bold)
                    Text(
                        "지금까지의 평가로 보면 \"${StylePreferenceStore.topStyle()}\" 스타일을 가장 선호하시네요.",
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

        // 실시간 선호도 순위
        Text(
            "내 스타일 선호도",
            style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        val maxScore = (scores.values.maxOrNull() ?: 0).coerceAtLeast(1)
        StylePreferenceStore.styles.sortedByDescending { scores[it] ?: 0 }.forEach { style ->
            val score = scores[style] ?: 0
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(style, style = MaterialTheme.typography.bodyMedium, color = CharcoalBlack, modifier = Modifier.width(56.dp))
                LinearProgressIndicator(
                    progress = { (score.coerceAtLeast(0) / maxScore.toFloat()) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Camel, trackColor = LightGray
                )
                Text("$score", style = MaterialTheme.typography.bodySmall, color = MediumGray, modifier = Modifier.width(28.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
