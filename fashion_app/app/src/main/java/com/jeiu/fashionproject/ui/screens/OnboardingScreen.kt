package com.jeiu.fashionproject.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeiu.fashionproject.R
import com.jeiu.fashionproject.data.ProfileStore
import com.jeiu.fashionproject.ui.components.PreferredStyleChips
import com.jeiu.fashionproject.recommend.PreferredStyles
import com.jeiu.fashionproject.ui.theme.*

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    // 예전에 고른 스타일이 있으면 미리 선택해 둔다
    val selected = remember { mutableStateListOf<String>().apply { addAll(ProfileStore.preferredStyles(context)) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
            .verticalScroll(rememberScrollState())
    ) {
        // 헤더 이미지 영역
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(CreamDark)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 왼쪽 큰 이미지
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.85f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Image(
                        painter = painterResource(R.drawable.onboarding_main),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // 오른쪽 이미지 2개
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = painterResource(R.drawable.onboarding_top),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = painterResource(R.drawable.onboarding_bottom),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Camel)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("STYLE AI", color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "나에게 잘 맞는\n스타일을 찾아보세요",
                    style = MaterialTheme.typography.headlineLarge,
                    color = CharcoalBlack,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "체형 정보와 취향을 바탕으로 오늘의 코디를 추천해드려요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediumGray
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "선호하는 스타일을 선택하세요",
                    style = MaterialTheme.typography.titleMedium,
                    color = CharcoalBlack,
                    fontWeight = FontWeight.SemiBold
                )
                PreferredStyleChips(selected) { style ->
                    val next = PreferredStyles.toggleSelection(selected.toList(), style)
                    selected.clear()
                    selected.addAll(next)
                }
                Text("고른 스타일은 규칙 기반 코디 추천에 반영해요. 나중에 바꿀 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MediumGray)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    // 고른 스타일을 폰 + 서버 DB에 저장 (선택 해제도 저장)
                    ProfileStore.savePreferredStyles(context, selected.toList())
                    onStart()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
            ) {
                Text("시작하기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            // Returning users may enter without committing a new style draft or touching backup state.
            OutlinedButton(onClick = onStart,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("continueLocalGuest"),
                shape = RoundedCornerShape(12.dp)) {
                Text("기록을 유지하고 바로 이용")
            }
            Text("로그인 없이 이용할 수 있어요. 위에서 바꾼 스타일은 저장하지 않아요.",
                style = MaterialTheme.typography.bodySmall, color = MediumGray)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
