package com.example

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    adBlocker: AdBlocker,
    addressManager: SiteAddressManager,
    onDismiss: () -> Unit,
    onUrlSelected: (String) -> Unit,
    onClearSiteData: () -> Unit,
    onShareNetworkLogs: () -> Unit,
    onUpdateBlocklist: suspend () -> Result<Int>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isAdBlockEnabled by remember { mutableStateOf(adBlocker.isEnabled) }
    var blockedCount by remember { mutableIntStateOf(adBlocker.blockedCount.get()) }
    var rulesCount by remember { mutableIntStateOf(adBlocker.getRulesCount()) }
    var allowlistCount by remember { mutableIntStateOf(adBlocker.getAllowlistCount()) }
    var lastUpdatedTime by remember { mutableLongStateOf(adBlocker.getLastUpdatedTime()) }
    var isUpdatingList by remember { mutableStateOf(false) }

    var currentActiveUrl by remember { mutableStateOf(addressManager.currentUrl ?: "") }
    var sameSiteMode by remember { mutableStateOf(addressManager.sameSiteMode) }
    var recentUrls by remember { mutableStateOf(addressManager.getRecentUrls()) }
    var tvModeSetting by remember { mutableStateOf(addressManager.tvMode) }
    var uaModeSetting by remember { mutableStateOf(addressManager.uaMode) }

    // Dialog states
    var showChangeUrlDialog by remember { mutableStateOf(false) }
    var showEditAllowlistDialog by remember { mutableStateOf(false) }
    var showEditBlocklistDialog by remember { mutableStateOf(false) }

    // Last 50 requests
    var recentRequests by remember { mutableStateOf(NetworkLogger.getRecentRequests(50)) }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Scaffold(
            containerColor = Color(0xFF0F172A),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Ayarlar & Site Yönetimi",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (currentActiveUrl.isNotBlank()) currentActiveUrl else "Adres Tanımsız",
                                color = Color(0xFF06B6D4),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, modifier = Modifier.focusable()) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E293B)
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // SECTION 1: Site Address & Dynamic Policy
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Site Adresi & Alan Adı Politikası",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aktif Adres:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (currentActiveUrl.isNotBlank()) currentActiveUrl else "(Ayarlanmadı)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showChangeUrlDialog = true },
                                modifier = Modifier.weight(1f).focusable(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2))
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Adresi Değiştir", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = onClearSiteData,
                                modifier = Modifier.weight(1f).focusable(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF4444))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verileri Temizle", fontSize = 12.sp, color = Color(0xFFEF4444))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Dynamic Same Site Mode Options
                        Text(
                            text = "Aynı Alan Adı Kuralı (Dinamik Filtreleme):",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sameSiteMode = SameSiteMode.STRICT
                                    addressManager.sameSiteMode = SameSiteMode.STRICT
                                }
                                .padding(vertical = 4.dp)
                                .focusable()
                        ) {
                            RadioButton(
                                selected = sameSiteMode == SameSiteMode.STRICT,
                                onClick = {
                                    sameSiteMode = SameSiteMode.STRICT
                                    addressManager.sameSiteMode = SameSiteMode.STRICT
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF06B6D4))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Sıkı Mod (Varsayılan)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Yalnızca aktif alan adı ve alt alan adları (www dahil)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sameSiteMode = SameSiteMode.FLEXIBLE
                                    addressManager.sameSiteMode = SameSiteMode.FLEXIBLE
                                }
                                .padding(vertical = 4.dp)
                                .focusable()
                        ) {
                            RadioButton(
                                selected = sameSiteMode == SameSiteMode.FLEXIBLE,
                                onClick = {
                                    sameSiteMode = SameSiteMode.FLEXIBLE
                                    addressManager.sameSiteMode = SameSiteMode.FLEXIBLE
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF06B6D4))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Esnek Mod (Marka Eşleşmesi)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Aynı marka etiketini taşıyan uzantılar da dahil (örn: .now, .nl, .com)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        // Recent URLs list
                        if (recentUrls.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Son Kullanılan Adresler (Kumandayla Seçilebilir):", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            recentUrls.forEach { url ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            addressManager.currentUrl = url
                                            currentActiveUrl = url
                                            onUrlSelected(url)
                                        }
                                        .focusable(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = url,
                                            color = if (url == currentActiveUrl) Color(0xFF06B6D4) else Color(0xFFE2E8F0),
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1
                                        )
                                        if (url == currentActiveUrl) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Ad Blocker Master Toggle & Stats
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (isAdBlockEnabled) Color(0xFF06B6D4).copy(alpha = 0.2f)
                                            else Color.Gray.copy(alpha = 0.2f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (isAdBlockEnabled) Color(0xFF06B6D4) else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Reklam & Takip Engelleyici",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = if (isAdBlockEnabled) "Aktif ve Koruyor" else "Devre Dışı",
                                        color = if (isAdBlockEnabled) Color(0xFF10B981) else Color(0xFFEF4444),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Switch(
                                checked = isAdBlockEnabled,
                                onCheckedChange = { checked ->
                                    isAdBlockEnabled = checked
                                    adBlocker.isEnabled = checked
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF06B6D4),
                                    checkedTrackColor = Color(0xFF0891B2).copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color(0xFF334155)
                                ),
                                modifier = Modifier.focusable()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Counters (Blocked & Rules)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Blocked Count
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Engellenen",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = blockedCount.toString(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF06B6D4)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            adBlocker.resetBlockedCount()
                                            blockedCount = 0
                                        },
                                        modifier = Modifier.fillMaxWidth().focusable(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Sıfırla", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }

                            // Rules Count
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Aktif Kural",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                    Text(
                                        text = String.format(Locale.getDefault(), "%,d", rulesCount),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA855F7)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            if (!isUpdatingList) {
                                                coroutineScope.launch {
                                                    isUpdatingList = true
                                                    val result = onUpdateBlocklist()
                                                    isUpdatingList = false
                                                    if (result.isSuccess) {
                                                        rulesCount = adBlocker.getRulesCount()
                                                        lastUpdatedTime = System.currentTimeMillis()
                                                        Toast.makeText(context, "Filtre güncellendi (${result.getOrNull()} kural)", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Güncelleme hatası: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isUpdatingList,
                                        modifier = Modifier.fillMaxWidth().focusable(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                                    ) {
                                        if (isUpdatingList) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Güncelle", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // Editable Rules Buttons
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showEditAllowlistDialog = true },
                                modifier = Modifier.weight(1f).focusable(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("İzin Listesi (${allowlistCount})", fontSize = 11.sp, color = Color(0xFF10B981))
                            }

                            OutlinedButton(
                                onClick = { showEditBlocklistDialog = true },
                                modifier = Modifier.weight(1f).focusable(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Engel Listesi (${rulesCount})", fontSize = 11.sp, color = Color(0xFFF43F5E))
                            }
                        }

                        // Last updated time info
                        Spacer(modifier = Modifier.height(10.dp))
                        val formattedDate = remember(lastUpdatedTime) {
                            if (lastUpdatedTime > 0L) {
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(lastUpdatedTime))
                            } else "Dahili Kural Listesi"
                        }
                        Text(
                            text = "Son Güncelleme: $formattedDate",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                // SECTION 3: Recent 50 Requests (1-Click Rule Addition)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Son 50 Ağ İsteği (Kural Ekle)",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }
                            IconButton(
                                onClick = { recentRequests = NetworkLogger.getRecentRequests(50) },
                                modifier = Modifier.size(28.dp).focusable()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Host'a göre tek dokunuşla izin ver veya engelle kuralı ekleyin:",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        if (recentRequests.isEmpty()) {
                            Text("Bu oturumda henüz ağ isteği kaydedilmedi.", color = Color(0xFF64748B), fontSize = 12.sp)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                recentRequests.take(50).forEach { item ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().focusable(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (item.isBlocked) Color(0xFFEF4444).copy(alpha = 0.2f)
                                                        else Color(0xFF10B981).copy(alpha = 0.2f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (item.isBlocked) "ENGEL" else "İZİN",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (item.isBlocked) Color(0xFFEF4444) else Color(0xFF10B981)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.host, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                                Text("${item.type} • ${item.ruleOrReason}", color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                                            }

                                            // Quick Allow Button
                                            IconButton(
                                                onClick = {
                                                    val ok = adBlocker.addAllowRule(item.host)
                                                    if (ok) {
                                                        allowlistCount = adBlocker.getAllowlistCount()
                                                        Toast.makeText(context, "${item.host} izin listesine eklendi", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).focusable()
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "İzin Ver", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                            }

                                            // Quick Block Button
                                            IconButton(
                                                onClick = {
                                                    val ok = adBlocker.addBlockRule(item.host)
                                                    if (ok) {
                                                        rulesCount = adBlocker.getRulesCount()
                                                        Toast.makeText(context, "${item.host} engelleme listesine eklendi", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).focusable()
                                            ) {
                                                Icon(Icons.Default.Block, contentDescription = "Engelle", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3b: TV Modu & Görünüm (Kumanda)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "TV Modu & Görünüm",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Değişiklikler uygulama yeniden açılınca geçerli olur. TV modunda sanal imleç, masaüstü görünüm ve yatay ekran kullanılır; Cast ve PiP kapanır. Menü veya Info tuşu imleç/odak modunu değiştirir.",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("TV modu:", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OptionRow(
                            options = listOf("Otomatik" to TvMode.AUTO, "Açık" to TvMode.ON, "Kapalı" to TvMode.OFF),
                            selected = tvModeSetting,
                            onSelect = {
                                tvModeSetting = it
                                addressManager.tvMode = it
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Site görünümü:", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OptionRow(
                            options = listOf("Otomatik" to UaMode.AUTO, "Mobil" to UaMode.MOBILE, "Masaüstü" to UaMode.DESKTOP),
                            selected = uaModeSetting,
                            onSelect = {
                                uaModeSetting = it
                                addressManager.uaMode = it
                            }
                        )
                    }
                }

                // SECTION 4: Keşif Modu & Log Sharing
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Keşif Modu & İstek Günlüğü (Son 500)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Son 500 ağ isteğini metin dosyası olarak dışa aktararak hangi domainlerin player/CDN veya reklam olduğunu tespit edebilirsiniz.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onShareNetworkLogs,
                            modifier = Modifier.fillMaxWidth().focusable(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("İstek Günlüğünü Paylaş", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // DIALOG 1: Change URL Dialog
    if (showChangeUrlDialog) {
        ChangeUrlDialog(
            currentUrl = currentActiveUrl,
            addressManager = addressManager,
            onDismiss = { showChangeUrlDialog = false },
            onSave = { newUrl ->
                showChangeUrlDialog = false
                currentActiveUrl = newUrl
                recentUrls = addressManager.getRecentUrls()
                onUrlSelected(newUrl)
            }
        )
    }

    // DIALOG 2: Edit Allowlist Dialog
    if (showEditAllowlistDialog) {
        EditTextListDialog(
            title = "İzin Verilenler (Allowlist)",
            initialText = adBlocker.getAllowlistText(),
            onDismiss = { showEditAllowlistDialog = false },
            onSave = { newText ->
                adBlocker.setAllowlistText(newText)
                allowlistCount = adBlocker.getAllowlistCount()
                showEditAllowlistDialog = false
                Toast.makeText(context, "İzin listesi kaydedildi", Toast.LENGTH_SHORT).show()
            },
            onReset = {
                adBlocker.resetToDefaultLists()
                allowlistCount = adBlocker.getAllowlistCount()
                rulesCount = adBlocker.getRulesCount()
                showEditAllowlistDialog = false
                Toast.makeText(context, "Listeler varsayılana sıfırlandı", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // DIALOG 3: Edit Blocklist Dialog
    if (showEditBlocklistDialog) {
        EditTextListDialog(
            title = "Engellenenler (Blocklist)",
            initialText = adBlocker.getBlocklistText(),
            onDismiss = { showEditBlocklistDialog = false },
            onSave = { newText ->
                adBlocker.setBlocklistText(newText)
                rulesCount = adBlocker.getRulesCount()
                showEditBlocklistDialog = false
                Toast.makeText(context, "Engel listesi kaydedildi", Toast.LENGTH_SHORT).show()
            },
            onReset = {
                adBlocker.resetToDefaultLists()
                rulesCount = adBlocker.getRulesCount()
                allowlistCount = adBlocker.getAllowlistCount()
                showEditBlocklistDialog = false
                Toast.makeText(context, "Listeler varsayılana sıfırlandı", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/** Kumandayla seçilebilir (focusable) yatay seçenek satırı. */
@Composable
fun <T> OptionRow(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            if (isSelected) {
                Button(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f).focusable(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2))
                ) {
                    Text(label, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f).focusable(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(label, fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

@Composable
fun ChangeUrlDialog(
    currentUrl: String,
    addressManager: SiteAddressManager,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var inputUrl by remember { mutableStateOf(currentUrl) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text("Site Adresini Değiştir", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Görüntülemek istediğiniz sitenin web adresini girin:",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth().focusable(),
                    placeholder = { Text("https://dizilla.now", color = Color.Gray) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedIndicatorColor = Color(0xFF06B6D4)
                    )
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = Color(0xFFEF4444), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Paste button
                    OutlinedButton(
                        onClick = {
                            clipboardManager.getText()?.let {
                                inputUrl = it.text.trim()
                            }
                        },
                        modifier = Modifier.weight(1f).focusable(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yapıştır", fontSize = 11.sp)
                    }

                    // Default preset button
                    OutlinedButton(
                        onClick = { inputUrl = "https://dizilla.now" },
                        modifier = Modifier.weight(1f).focusable(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("dizilla.now", fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (inputUrl.isBlank()) {
                        errorMessage = "Lütfen bir site adresi girin"
                        return@Button
                    }
                    val normalized = addressManager.normalizeUrl(inputUrl)
                    if (!addressManager.isValidUrl(normalized)) {
                        errorMessage = "Geçersiz web adresi biçimi"
                        return@Button
                    }
                    addressManager.currentUrl = normalized
                    onSave(normalized)
                },
                modifier = Modifier.focusable(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
            ) {
                Text("Kaydet ve Aç", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusable()) {
                Text("İptal", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
fun EditTextListDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var textContent by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Her satıra bir alan adı (domain) yazın. # ile başlayan satırlar yorumdur.",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .focusable(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedIndicatorColor = Color(0xFF06B6D4)
                    )
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.focusable()
                ) {
                    Text("Sıfırla", color = Color(0xFFEF4444), fontSize = 12.sp)
                }

                Button(
                    onClick = { onSave(textContent) },
                    modifier = Modifier.focusable(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                ) {
                    Text("Kaydet", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusable()) {
                Text("Kapat", color = Color(0xFF94A3B8))
            }
        }
    )
}
