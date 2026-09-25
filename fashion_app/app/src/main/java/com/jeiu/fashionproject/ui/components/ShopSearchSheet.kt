package com.jeiu.fashionproject.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.*

private data class Platform(val name: String, val color: Color, val searchUrl: String)

private val platforms = listOf(
    Platform("지그재그",      Color(0xFF5B5BFF), "https://zigzag.kr/search?q="),
    Platform("에이블리",      Color(0xFFFF4E7E), "https://m.a-bly.com/goods/search?keyword="),
    Platform("무신사",        Color(0xFF1A1A1A), "https://www.musinsa.com/search/musinsa/integration?q="),
    Platform("29CM",         Color(0xFF2D2D2D), "https://www.29cm.co.kr/search?query="),
    Platform("W Concept",    Color(0xFF3D6B5E), "https://www.wconcept.co.kr/Search?keyword="),
    Platform("ZARA",         Color(0xFF000000), "https://www.zara.com/kr/ko/search?searchTerm="),
    Platform("H&M",          Color(0xFFE50010), "https://www2.hm.com/ko_kr/search-results.html?q="),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopSearchSheet(keyword: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "\"$keyword\"",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CharcoalBlack
            )
            Text(
                "어디서 찾아볼까요?",
                style = MaterialTheme.typography.bodyMedium,
                color = MediumGray
            )
            Spacer(Modifier.height(4.dp))
            platforms.forEach { platform ->
                Button(
                    onClick = {
                        val url = platform.searchUrl + Uri.encode(keyword)
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = platform.color,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        platform.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
