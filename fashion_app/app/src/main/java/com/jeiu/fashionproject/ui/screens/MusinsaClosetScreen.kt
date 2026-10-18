package com.jeiu.fashionproject.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.TryOnRepository
import com.jeiu.fashionproject.data.TryOnBatch
import com.jeiu.fashionproject.data.TryOnPlan
import com.jeiu.fashionproject.data.TryOnGarment
import com.jeiu.fashionproject.data.TryOnFailure
import kotlinx.coroutines.TimeoutCancellationException
import com.jeiu.fashionproject.ui.viewmodel.PhotoTransferConsent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.jeiu.fashionproject.data.advice
import com.jeiu.fashionproject.data.isRainy
import com.jeiu.fashionproject.ui.viewmodel.WeatherUiState
import com.jeiu.fashionproject.ui.viewmodel.WeatherViewModel
import com.jeiu.fashionproject.ui.components.OutfitPhoto
import com.jeiu.fashionproject.ui.components.OutfitPhotoImage
import com.jeiu.fashionproject.ui.theme.*

// 데모 옷장: 무신사 실제 판매 랭킹(2026년 10월 13일, 20~24세, 전체 랭킹)에서 카테고리별 상위 상품으로 채운 샘플 옷장.
// - 마이페이지에서 고른 성별에 따라 여성 / 남성 랭킹을 따로 보여준다 (선택 안 하면 둘 다).
// - 사진·이름·가격은 무신사 상품 그대로이고, 누르면 해당 무신사 상품 페이지로 바로 이동한다.
// - 사용자가 직접 추가한 옷은 image에 갤러리 Uri 문자열이 들어가고 url은 없다.
private data class ClothingItem(
    val name: String,
    val category: String,
    val color: Color,
    val image: Any,
    val gender: Gender? = null,
    val isDemo: Boolean = false,
    val brand: String = "",
    val price: Int? = null,
    val url: String? = null
)

private const val MSS = "https://image.msscdn.net/thumbnails/images"
private const val RANKING_DATE = "10월 13일"

private fun musinsa(
    id: String, brand: String, name: String, price: Int, category: String, imagePath: String, gender: Gender
) = ClothingItem(
    name = name,
    category = category,
    color = Color(0xFFF4F1EC),
    image = "$MSS/$imagePath",
    gender = gender,
    isDemo = true,
    brand = brand,
    price = price,
    url = "https://www.musinsa.com/products/$id"
)

private val femaleDemoClothes = listOf(
    musinsa("7385464", "탄산마그네슘", "안녕자두야X탄산 앉은 꼬꼬 롱슬리브 (화이트)", 71400, "상의", "goods_img/20260921/7385464/7385464_17917794260705_500.jpg", Gender.FEMALE),
    musinsa("7167713", "포르테나", "RAW CUT BLUR GRAPHIC LONG SLEEVE T-SHIRT", 59000, "상의", "goods_img/20260826/7167713/7167713_17888412795201_500.jpg", Gender.FEMALE),
    musinsa("7361603", "포어링", "Caramel knit half top (beige)", 92000, "상의", "goods_img/20260918/7361603/7361603_17898385565481_500.png", Gender.FEMALE),
    musinsa("7045012", "아이돈워너셀", "Shift Curved Sweat Pants Gray", 79200, "하의", "goods_img/20260811/7045012/7045012_17901475708390_500.jpg", Gender.FEMALE),
    musinsa("7425873", "락케이크", "Carly Coating Bootcut Jeans - Black", 154800, "하의", "goods_img/20260930/7425873/7425873_17914447876803_500.jpg", Gender.FEMALE),
    musinsa("7204746", "페인오어플레져", "SWAN SKIRT white", 198000, "하의", "goods_img/20260831/7204746/7204746_17881575249084_500.jpg", Gender.FEMALE),
    musinsa("6604755", "드라이로즈", "머메이드 롱 스커트 (블루)", 89000, "하의", "goods_img/20260604/6604755/6604755_17809279711592_500.png", Gender.FEMALE),
    musinsa("7341329", "파타고니아", "우먼즈 피츠 로이 다운 후디", 699000, "아우터", "goods_img/20260916/7341329/7341329_17895234023335_500.jpg", Gender.FEMALE),
    musinsa("6100901", "닉켈하우스", "닉켈 26 동키 후드집업", 99500, "아우터", "goods_img/20260309/6100901/6100901_17730403884837_500.jpg", Gender.FEMALE),
    musinsa("7102485", "노스페이스", "여성 칼릭스 LT 다운 자켓 (RDS) IVORY", 338000, "아우터", "goods_img/20260819/7102485/7102485_17894506842205_500.jpg", Gender.FEMALE),
    musinsa("7351463", "포어링", "Mocha button boots (charcoal)", 193000, "신발", "goods_img/20260917/7351463/7351463_17895825620121_500.png", Gender.FEMALE),
    musinsa("7206094", "로맨틱무브", "헤비듀티 부츠 HD25-01 (홀스레더 블랙)", 293550, "신발", "prd_img/20260831/7206094/detail_7206094_17912056821881_500.jpg", Gender.FEMALE),
    musinsa("5807110", "푸마", "클럽 II 에라 스웨이드 - 블랙:알파인스노우", 89000, "신발", "goods_img/20251205/5807110/5807110_17649170857146_500.jpg", Gender.FEMALE),
    musinsa("6885777", "코치", "로렐 숄더 백 인 시그니처 캔버스", 490000, "액세서리", "goods_img/20260721/6885777/6885777_17846042397220_500.jpg", Gender.FEMALE),
    musinsa("7228227", "썬번프로젝트", "FAUX LEATHER B.B BAG, BLACK", 199000, "액세서리", "goods_img/20260902/7228227/7228227_17883318937367_500.jpg", Gender.FEMALE),
    musinsa("5160043", "살로몬", "트레일블레이저 10/20/30 (5 color)", 100000, "액세서리", "goods_img/20250530/5160043/5160043_17540258682523_500.jpg", Gender.FEMALE),
)

private val maleDemoClothes = listOf(
    musinsa("5453617", "수아레", "데일리 헨리넥 니트 - 5 COLOR", 30050, "상의", "goods_img/20250912/5453617/5453617_17593914818524_500.jpg", Gender.MALE),
    musinsa("3656717", "트래블", "디스코 피그먼트 후드티 블랙 차콜", 12000, "상의", "goods_img/20231025/3656717/3656717_16983038129580_500.jpg", Gender.MALE),
    musinsa("2149254", "무신사 스탠다드", "베이식 긴소매 티셔츠 2팩", 37790, "상의", "goods_img/20210928/2149254/2149254_17138588137724_500.jpg", Gender.MALE),
    musinsa("7325646", "미세키서울", "맨 High neck side button knit CHARCOAL", 117300, "상의", "goods_img/20260914/7325646/7325646_17908206076485_500.jpg", Gender.MALE),
    musinsa("6255589", "알리스", "[NERDKING x ALYSS] 슬릭 커브드 코튼 치노 팬츠", 59200, "하의", "goods_img/20260403/6255589/6255589_17887458796825_500.jpg", Gender.MALE),
    musinsa("1168906", "무신사 스탠다드", "와이드 히든 밴딩 슬랙스 [블랙]", 47390, "하의", "goods_img/20190927/1168906/1168906_17224121763738_500.jpg", Gender.MALE),
    musinsa("4757347", "언탭트 스튜디오", "빈티지 프론트 워싱 와이드 벌룬 데님 팬츠", 36800, "하의", "goods_img/20250204/4757347/4757347_17389245694198_500.jpg", Gender.MALE),
    musinsa("947091", "무신사 스탠다드", "후디드 스웨트 집업 [네이비]", 45900, "아우터", "goods_img/20190201/947091/947091_17217984749026_500.jpg", Gender.MALE),
    musinsa("5943431", "아디다스", "와플 클래식 트랙탑 - 원더화이트", 108990, "아우터", "goods_img/20260127/5943431/5943431_17745057757383_500.jpg", Gender.MALE),
    musinsa("5931875", "러프사이드", "Dune Parka Ash Black", 368000, "아우터", "goods_img/20260123/5931875/5931875_17874696320237_500.jpg", Gender.MALE),
    musinsa("595038", "닥터마틴", "1461 3홀 블랙 나파", 229000, "신발", "goods_img/20170728/595038/595038_4_500.jpg", Gender.MALE),
    musinsa("3721889", "야세", "홀리 홀스하이드 컴포트 더비 슈즈 블랙", 112860, "신발", "goods_img/20231118/3721889/3721889_17694113943929_500.jpg", Gender.MALE),
    musinsa("4421920", "살로몬", "XT-6 GTX - 블랙:인피니티:화이트", 295000, "신발", "goods_img/20240909/4421920/4421920_17290459891913_500.jpg", Gender.MALE),
    musinsa("7423696", "매드고트", "카바스 레더 숄더 토트백_블랙", 179000, "액세서리", "goods_img/20260930/7423696/7423696_17908154433840_500.jpg", Gender.MALE),
    musinsa("5288153", "알리스", "슬롯 벨티드 레더 호보 크로스백 [블랙]", 65450, "액세서리", "goods_img/20250801/5288153/5288153_17562587425757_500.jpg", Gender.MALE),
    musinsa("4641096", "무신사 스탠다드", "[시티 레저] 립스탑 커브드 포켓 하이킹 백팩 [블랙]", 64990, "액세서리", "goods_img/20241128/4641096/4641096_17440954417172_500.jpg", Gender.MALE),
)

private fun demoClothesFor(gender: Gender?): List<ClothingItem> = when (gender) {
    Gender.FEMALE -> femaleDemoClothes
    Gender.MALE -> maleDemoClothes
    null -> femaleDemoClothes + maleDemoClothes
}

/**
 * 무신사 랭킹 옷장 (손유리) — 10/13 무신사 판매 랭킹 상품으로 만든 성별 맞춤 데모 옷장.
 * 오늘 날씨 코디 조합과 FASHN 가상 피팅("입어보기")을 바로 해볼 수 있다.
 * 옷장 탭(WardrobeScreen, 내 옷 저장소)과는 별도 화면이며, 옷장 탭 상단 카드에서 들어온다.
 */
@Composable
fun MusinsaClosetScreen(
    onBack: () -> Unit,
    gender: Gender? = null,
    weatherViewModel: WeatherViewModel? = null,
    bodyPhotoUri: Uri? = null
) {
    val categories = listOf("전체", "상의", "하의", "아우터", "신발", "액세서리")
    var selectedCategory by remember { mutableStateOf("전체") }
    val favoriteSet = remember { mutableStateListOf<String>() }
    val customClothes = remember { mutableStateListOf<ClothingItem>() }
    val allDisplayed = demoClothesFor(gender) + customClothes
    val context = LocalContext.current
    val weatherState = weatherViewModel?.state?.collectAsState()?.value ?: WeatherUiState.Error("")
    LaunchedEffect(Unit) { weatherViewModel?.fetchWeather() }
    var showTodayOutfit by remember { mutableStateOf(false) }
    var tryOnPieces by remember { mutableStateOf<List<ClothingItem>?>(null) }
    val openItem: (ClothingItem) -> Unit = { item ->
        item.url?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var newItemName by remember { mutableStateOf("") }
    var newItemCategory by remember { mutableStateOf("상의") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pendingUri = it; newItemName = ""; showAddDialog = true }
    }

    val filtered = if (selectedCategory == "전체") allDisplayed else allDisplayed.filter { it.category == selectedCategory }

    tryOnPieces?.let { pieces ->
        TryOnView(
            pieces = pieces,
            defaultPhoto = bodyPhotoUri,
            onBack = { tryOnPieces = null },
            onOpenItem = openItem
        )
        return
    }

    if (showTodayOutfit) {
        TodayOutfitView(
            pool = allDisplayed,
            weatherState = weatherState,
            gender = gender,
            onBack = { showTodayOutfit = false },
            onOpenItem = openItem,
            onTryOn = { items -> tryOnPieces = items }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
                }
                Text("무신사 랭킹 옷장", style = MaterialTheme.typography.headlineSmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            }
            Text("${allDisplayed.size}개", style = MaterialTheme.typography.bodyMedium, color = MediumGray)
        }

        Text(
            when (gender) {
                Gender.FEMALE -> "$RANKING_DATE 무신사 판매 랭킹 (여성 20~24세) 기준 데모 옷장 · 누르면 상품 페이지로 이동해요"
                Gender.MALE -> "$RANKING_DATE 무신사 판매 랭킹 (남성 20~24세) 기준 데모 옷장 · 누르면 상품 페이지로 이동해요"
                null -> "$RANKING_DATE 무신사 판매 랭킹 (20~24세) 기준 데모 옷장 · 마이페이지에서 성별을 고르면 맞춤으로 바뀌어요"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MediumGray,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // 카테고리 탭
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedCategory),
            containerColor = Cream,
            contentColor = Camel,
            edgePadding = 20.dp,
            indicator = {},
            divider = {}
        ) {
            categories.forEach { cat ->
                val isSelected = cat == selectedCategory
                Tab(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Camel else CreamDark)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        cat,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) White else MediumGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 의류 그리드
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filtered) { item ->
                val isFav = item.name in favoriteSet
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 데모 옷은 누르면 해당 무신사 상품 페이지로 바로 이동
                        .clickable(enabled = item.url != null) { openItem(item) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .background(item.color)
                        ) {
                            OutfitPhotoImage(
                                photo = OutfitPhoto(item.image),
                                contentDescription = item.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = {
                                    if (isFav) favoriteSet.remove(item.name) else favoriteSet.add(item.name)
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "즐겨찾기",
                                    tint = if (isFav) Camel else White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(White.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(item.category, style = MaterialTheme.typography.labelSmall, color = Brown)
                            }
                        }
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (item.brand.isNotBlank()) {
                                Text(item.brand, style = MaterialTheme.typography.labelSmall, color = MediumGray, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = CharcoalBlack,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            item.price?.let {
                                Text("%,d원".format(it), style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.url != null) {
                                    Text("무신사에서 보기 ›", style = MaterialTheme.typography.labelSmall, color = Camel)
                                }
                                // 미설정이어도 진입해 서비스 안내를 볼 수 있다. 실제 생성은 별도 동의/설정 검사 후 실행한다.
                                if (TryOnRepository.categoryFor(item.category) != null) {
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        "입어보기",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BrownDark,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CamelLight)
                                            .clickable { tryOnPieces = listOf(item) }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 하단 버튼
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(White)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Camel)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("의류 추가", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    // 내 옷장 옷으로 오늘 날씨에 맞는 코디를 만들어 보여준다
                    onClick = { showTodayOutfit = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
                ) {
                    Text("코디 추천받기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showAddDialog) {
        val catOptions = listOf("상의", "하의", "아우터", "신발", "액세서리")
        AlertDialog(
            onDismissRequest = { showAddDialog = false; pendingUri = null },
            title = { Text("의류 추가", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        label = { Text("이름") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Camel, focusedLabelColor = Camel)
                    )
                    Text("카테고리", style = MaterialTheme.typography.labelLarge, color = CharcoalBlack)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        catOptions.forEach { cat ->
                            FilterChip(
                                selected = newItemCategory == cat,
                                onClick = { newItemCategory = cat },
                                label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Camel,
                                    selectedLabelColor = White
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingUri?.let { uri ->
                        customClothes.add(ClothingItem(newItemName.ifBlank { "새 의류" }, newItemCategory, Color(0xFFEDE3D5), uri.toString()))
                    }
                    showAddDialog = false; pendingUri = null
                }) { Text("추가", color = Camel) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; pendingUri = null }) { Text("취소", color = MediumGray) }
            }
        )
    }

}

// ─────────────────────────────────────────────────────────────────────────────
// 오늘의 코디: 내 옷장 옷(데모 + 직접 추가)으로 현재 날씨에 맞는 조합을 만든다.
// 옷 이름의 키워드로 보온 정도(0 얇음 · 1 보통 · 2 두꺼움)를 정하고,
// 기온에 맞는 목표 보온도에 가까운 옷부터 골라 아우터·상의·하의·신발·가방 세트를 만든다.
// ─────────────────────────────────────────────────────────────────────────────

private val heavyKeywords = listOf("다운", "패딩", "파카", "parka", "플리스", "fleece", "코트", "피츠 로이", "폴라")
private val warmTopKeywords = listOf("니트", "knit", "후드", "hood", "스웨트", "맨투맨", "가디건")
private val longSleeveKeywords = listOf("롱슬리브", "긴소매", "긴팔", "long sleeve")
private val bootKeywords = listOf("부츠", "boots", "워터프루프", "gtx", "고어텍스")

private fun String.hasAny(words: List<String>) = words.any { contains(it, ignoreCase = true) }

private fun warmthOf(item: ClothingItem): Int = when (item.category) {
    "아우터" -> if (item.name.hasAny(heavyKeywords)) 2 else 1
    "상의" -> when {
        item.name.hasAny(warmTopKeywords) -> 2
        item.name.hasAny(longSleeveKeywords) -> 1
        else -> 0
    }
    "하의" -> when {
        item.name.hasAny(listOf("플리스", "fleece", "기모", "코팅")) -> 2
        item.name.hasAny(listOf("스커트", "skirt", "반바지", "쇼츠")) -> 0
        else -> 1
    }
    else -> 1
}

/** 기온별 목표: 아우터 보온도(null = 아우터 없이), 상의 보온도, 한 줄 설명 */
private data class OutfitTarget(val outer: Int?, val top: Int, val bottom: Int, val reason: String)

private fun targetFor(temp: Int, rainy: Boolean): OutfitTarget = when {
    temp < 10 -> OutfitTarget(2, 2, 2, "추운 날이라 두꺼운 아우터와 따뜻한 상의를 골랐어요")
    temp < 17 -> OutfitTarget(1, 2, 1, "쌀쌀해서 니트·후드에 가벼운 아우터를 더했어요")
    temp < 23 -> OutfitTarget(1, 1, 1, "선선한 날이라 긴소매에 얇은 아우터를 걸쳤어요")
    else -> OutfitTarget(null, 0, 0, "따뜻한 날이라 아우터 없이 가볍게 입었어요")
}.let { if (rainy) it.copy(reason = it.reason + " · 비 소식이 있어 신발은 부츠로") else it }

private data class TodayOutfit(val items: List<ClothingItem>, val reason: String)

private fun buildOutfits(
    pool: List<ClothingItem>, temp: Int, rainy: Boolean, gender: Gender?, seed: Int
): List<TodayOutfit> {
    val target = targetFor(temp, rainy)
    // 성별을 안 골랐으면 여성 / 남성 옷장을 번갈아 조합한다 (직접 추가한 옷은 어느 쪽에나 들어감)
    val genders: List<Gender?> = if (gender != null) listOf(gender) else listOf(Gender.FEMALE, Gender.MALE)

    fun ranked(items: List<ClothingItem>, category: String, want: Int?): List<ClothingItem> {
        val inCat = items.filter { it.category == category }
        return when {
            category == "신발" && rainy -> inCat.sortedByDescending { it.name.hasAny(bootKeywords) }
            want == null -> inCat
            else -> inCat.sortedBy { kotlin.math.abs(warmthOf(it) - want) }
        }
    }

    fun pick(list: List<ClothingItem>, index: Int): ClothingItem? {
        if (list.isEmpty()) return null
        // 목표에 가까운 앞쪽 후보(최대 3개) 안에서 돌려가며 고른다
        val window = minOf(list.size, 3)
        return list[index.mod(window)]
    }

    return (0 until 3).mapNotNull { i ->
        val g = genders[i % genders.size]
        val mine = pool.filter { it.gender == null || it.gender == g }
        val k = seed + i
        val outer = if (target.outer != null) pick(ranked(mine, "아우터", target.outer), k) else null
        val top = pick(ranked(mine, "상의", target.top), k + i)
        val bottom = pick(ranked(mine, "하의", target.bottom), k * 2 + i)
        val shoes = pick(ranked(mine, "신발", null), k)
        val bag = pick(ranked(mine, "액세서리", null), k + 1)
        val items = listOfNotNull(outer, top, bottom, shoes, bag)
        if (top == null && bottom == null) null else TodayOutfit(items, target.reason)
    }.distinctBy { o -> o.items.map { it.name } }
}

@Composable
private fun TodayOutfitView(
    pool: List<ClothingItem>,
    weatherState: WeatherUiState,
    gender: Gender?,
    onBack: () -> Unit,
    onOpenItem: (ClothingItem) -> Unit,
    onTryOn: (List<ClothingItem>) -> Unit
) {
    BackHandler(onBack = onBack)
    var seed by remember { mutableStateOf(0) }
    val info = (weatherState as? WeatherUiState.Success)?.info
    val temp = info?.temp ?: 15
    val rainy = info?.isRainy() == true
    val outfits = remember(pool, temp, rainy, gender, seed) { buildOutfits(pool, temp, rainy, gender, seed) }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("오늘의 코디", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 날씨 요약
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrownDark)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    when (weatherState) {
                        is WeatherUiState.Success -> "지금 ${info!!.temp}°C · ${info.condition}"
                        is WeatherUiState.Loading -> "날씨를 불러오는 중이에요"
                        is WeatherUiState.Error -> "날씨를 못 불러와서 15°C 기준으로 골랐어요"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
                info?.let { Text(it.advice(), style = MaterialTheme.typography.bodyMedium, color = CamelLight) }
                Text(
                    "내 옷장 옷 ${pool.size}개 중에서 골랐어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = CamelLight
                )
            }

            if (outfits.isEmpty()) {
                Text(
                    "옷장에 상의나 하의가 없어서 코디를 만들 수 없어요. 옷을 먼저 추가해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MediumGray
                )
            }

            outfits.forEachIndexed { index, outfit ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("코디 ${index + 1}", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                            val total = outfit.items.mapNotNull { it.price }.sum()
                            if (total > 0) {
                                Spacer(Modifier.weight(1f))
                                Text("총 %,d원".format(total), style = MaterialTheme.typography.bodySmall, color = MediumGray)
                            }
                        }
                        Text(outfit.reason, style = MaterialTheme.typography.bodySmall, color = Brown)
                        outfit.items.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { item ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(enabled = item.url != null) { onOpenItem(item) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .background(item.color)
                                        ) {
                                            OutfitPhotoImage(
                                                photo = OutfitPhoto(item.image),
                                                contentDescription = item.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(6.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(White.copy(alpha = 0.85f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(item.category, style = MaterialTheme.typography.labelSmall, color = Brown)
                                            }
                                        }
                                        Text(
                                            item.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CharcoalBlack,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        item.price?.let {
                                            Text("%,d원".format(it), style = MaterialTheme.typography.labelSmall, color = MediumGray)
                                        }
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        val pieces = tryOnPiecesOf(outfit.items)
                        if (pieces.isNotEmpty() && TryOnRepository.hasKey()) {
                            OutlinedButton(
                                onClick = { onTryOn(pieces + outfit.items.filter { it !in pieces }) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrownDark)
                            ) {
                                Text("이 코디 AI 가상피팅 해보기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().background(White).padding(16.dp)) {
            Button(
                onClick = { seed++ },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
            ) {
                Text("다른 조합 보기", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI 가상피팅: 내 전신 사진에 고른 옷(상의/아우터 → 하의 순서)을 FASHN API로 입혀 본다.
// 결과 아래에 각 옷의 무신사 구매 버튼을 둬서 "입어보고 → 바로 구매" 흐름을 만든다.
// ─────────────────────────────────────────────────────────────────────────────

/** 앱을 켜 둔 동안 고른 전신 사진을 기억한다 (탭을 옮겨도 유지). */
private object TryOnPhotoHolder { var uri: Uri? = null }

/** 코디 세트에서 가상피팅에 쓸 옷만 고른다: 상의(아우터가 있으면 아우터) 1벌 + 하의 1벌 */
private fun tryOnPiecesOf(items: List<ClothingItem>): List<ClothingItem> {
    val upper = items.firstOrNull { it.category == "아우터" } ?: items.firstOrNull { it.category == "상의" }
    val lower = items.firstOrNull { it.category == "하의" }
    return listOfNotNull(upper, lower)
}

@Composable
private fun TryOnView(
    pieces: List<ClothingItem>,
    defaultPhoto: Uri?,
    onBack: () -> Unit,
    onOpenItem: (ClothingItem) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var personUri by remember { mutableStateOf(TryOnPhotoHolder.uri ?: defaultPhoto) }
    var progress by remember { mutableStateOf<String?>(null) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    val transferConsent = remember { PhotoTransferConsent() }
    var showTransferConsent by remember { mutableStateOf(false) }
    val wearable = tryOnPiecesOf(pieces)
    var savingResult by remember { mutableStateOf(false) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { target ->
        val result = resultUrl
        if (target != null && result != null && !savingResult) {
            savingResult = true
            scope.launch {
                try {
                    TryOnRepository.saveResult(context, result, target)
                    saveMessage = "AI 결과 이미지를 저장했어요."
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (_: Exception) { saveMessage = "이미지를 저장하지 못했어요. 다시 시도해주세요."
                } finally { savingResult = false }
            }
        }
    }


    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { transferConsent.cancel(); personUri = it; TryOnPhotoHolder.uri = it; resultUrl = null; errorText = null }
    }

    fun start() {
        val person = personUri ?: return
        if (progress != null || wearable.isEmpty() || !TryOnRepository.hasKey() || !transferConsent.consume(person.toString())) return
        progress = "사진 준비 중…"
        scope.launch {
            errorText = null; resultUrl = null; showOriginal = false
            try {
                progress = "사진 준비 중…"
                val input = TryOnRepository.uriToDataUri(context, person)
                val plan = TryOnPlan.build(wearable.map { TryOnGarment(it.name, it.name, it.category, it.image.toString()) })
                resultUrl = TryOnBatch.run(plan, input, generate = { current, item ->
                    val garment = if (TryOnBatch.isResultUrl(item.image)) item.image
                        else TryOnRepository.uriToDataUri(context, Uri.parse(item.image))
                    TryOnRepository.tryOn(current, garment, TryOnRepository.categoryFor(item.category)!!)
                }, progress = { index, total -> progress = "AI가 옷을 입혀주고 있어요… ($index/$total)" })
            } catch (_: TimeoutCancellationException) {
                errorText = "AI 피팅이 오래 걸려요. 서버 작업·차감 여부를 확인한 뒤 재시도해주세요."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                errorText = if (e is TryOnFailure) e.message else "AI 피팅을 완료하지 못했어요. 사진과 네트워크를 확인해주세요."
            } finally {
                progress = null
            }
        }
    }

    if (showTransferConsent) {
        AlertDialog(onDismissRequest = { transferConsent.cancel(); showTransferConsent = false },
            title = { Text("외부 서비스에 사진을 전송할까요?") },
            text = { Text("가상피팅을 위해 선택한 전신 사진과 의류 이미지를 FASHN(api.fashn.ai)으로 전송해요. ${wearable.size}벌을 순서대로 생성하며 최대 ${wearable.size}회 생성 요청해요. 중간 결과도 다시 전송하며 재시도는 추가 요청이에요. 현재 요금·크레딧 정책을 확인해주세요. 얼굴·체형·세부 디자인이나 실제 제품 핏은 보장하지 않아요. 체형분석 동의와 별개이고, 거부해도 체형분석과 기본 추천은 사용할 수 있어요. 전송 후 처리·보관은 외부 서비스 정책을 따라요.") },
            confirmButton = { TextButton(onClick = {
                val photo = personUri
                showTransferConsent = false
                if (photo != null && transferConsent.confirm(photo.toString())) start()
            }) { Text("확인하고 전송") } },
            dismissButton = { TextButton(onClick = { transferConsent.cancel(); showTransferConsent = false }) { Text("전송하지 않기") } })
    }

    Column(modifier = Modifier.fillMaxSize().background(Cream)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로", tint = CharcoalBlack)
            }
            Text("AI 가상피팅", style = MaterialTheme.typography.titleLarge, color = CharcoalBlack, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 큰 미리보기: 결과가 있으면 결과, 없으면 내 사진
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CreamDark)
                    .clickable(enabled = progress == null) { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                val shown: Any? = if (resultUrl != null && !showOriginal) resultUrl else personUri
                if (shown != null) {
                    AsyncImage(
                        model = shown,
                        contentDescription = "가상피팅 미리보기",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Brown, modifier = Modifier.size(36.dp))
                        Text("전신 사진을 골라 주세요", style = MaterialTheme.typography.bodyMedium, color = Brown)
                        Text("정면 · 머리부터 발끝까지 보이는 사진이 좋아요", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                    }
                }
                if (progress != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = White)
                        Spacer(Modifier.height(12.dp))
                        Text(progress!!, color = White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (resultUrl != null && progress == null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showOriginal) White else Camel)
                            .clickable { showOriginal = !showOriginal }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            if (showOriginal) "원본 사진 · 눌러서 결과 보기" else "피팅 결과 · 눌러서 원본 보기",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showOriginal) Brown else White
                        )
                    }
                }
            }

            if (personUri != null && progress == null) {
                Text(
                    "사진을 누르면 다른 전신 사진으로 바꿀 수 있어요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MediumGray
                )
            }
            if (!TryOnRepository.hasKey()) {
                Text(
                    "AI 피팅 서비스가 아직 연결되지 않았어요. 체형분석과 기본 추천은 계속 사용할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB3261E)
                )
            }
            errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB3261E)) }

            if (resultUrl != null && progress == null) {
                Text("AI 피팅이 완료됐어요! ✨", color = Brown)
                OutlinedButton(enabled = !savingResult, onClick = { savePicker.launch("ai-fitting-${System.currentTimeMillis()}.jpg") }) {
                    Text(if (savingResult) "저장 중…" else "결과 이미지 저장")
                }
            }
            saveMessage?.let { Text(it, color = Brown) }

            // 입어볼 옷 + 구매 버튼
            Text("입어볼 옷", style = MaterialTheme.typography.titleMedium, color = CharcoalBlack, fontWeight = FontWeight.Bold)
            pieces.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(White)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(item.color)
                    ) {
                        OutfitPhotoImage(
                            photo = OutfitPhoto(item.image),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        if (item.brand.isNotBlank()) {
                            Text(item.brand, style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        }
                        Text(item.name, style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.price?.let {
                            Text("%,d원".format(it), style = MaterialTheme.typography.bodySmall, color = CharcoalBlack, fontWeight = FontWeight.Bold)
                        }
                        if (TryOnRepository.categoryFor(item.category) == null) {
                            Text("신발·가방은 가상피팅이 안 돼요", style = MaterialTheme.typography.labelSmall, color = MediumGray)
                        }
                    }
                    if (item.url != null) {
                        Button(
                            onClick = { onOpenItem(item) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrownDark, contentColor = White)
                        ) {
                            Text("구매하기", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text(
                "FASHN AI 가상피팅 · 최대 ${wearable.size}회 생성 요청 · 현재 서비스 요금·크레딧 정책 적용",
                style = MaterialTheme.typography.labelSmall,
                color = MediumGray
            )
            Spacer(Modifier.height(8.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().background(White).padding(16.dp)) {
            Button(
                onClick = {
                    val photo = personUri
                    if (photo == null) picker.launch("image/*") else {
                        transferConsent.request(photo.toString())
                        showTransferConsent = true
                    }
                },
                enabled = progress == null && !savingResult && wearable.isNotEmpty() && TryOnRepository.hasKey(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Camel, contentColor = White)
            ) {
                Text(
                    when {
                        personUri == null -> "전신 사진 고르기"
                        resultUrl != null -> "다시 입혀보기"
                        else -> "가상피팅 시작 (${wearable.size}벌)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
