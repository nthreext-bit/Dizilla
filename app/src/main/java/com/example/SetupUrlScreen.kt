package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupUrlScreen(
    addressManager: SiteAddressManager,
    onUrlConfigured: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var inputUrl by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(addressManager.sameSiteMode) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bookmarkedUrls by remember { mutableStateOf(addressManager.getBookmarkedUrls()) }
    var addressMenuExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val normalizedInput = addressManager.normalizeUrl(inputUrl)
    val isBookmarked = normalizedInput in bookmarkedUrls

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Logo & Badge
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Site Adresi Kurulumu",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Bir web adresi girin ya da yer imlerinizden seçin. Adreslerinizi kendiniz kaydedip yönetebilirsiniz:",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Input Field (Focusable for D-Pad / Kumanda)
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = {
                            inputUrl = it
                            errorMessage = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusable(),
                        placeholder = { Text("https://dizilla.now", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedIndicatorColor = Color(0xFF06B6D4),
                            unfocusedIndicatorColor = Color(0xFF334155)
                        )
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Chips (Paste & Sample)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.getText()?.let {
                                    inputUrl = it.text.trim()
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.weight(1f).focusable(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yapıştır", fontSize = 12.sp)
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { addressMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth().focusable(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "Adreslerim",
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DropdownMenu(
                                expanded = addressMenuExpanded,
                                onDismissRequest = { addressMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Dizilla.now") },
                                    onClick = {
                                        inputUrl = "https://dizilla.now"
                                        errorMessage = null
                                        addressMenuExpanded = false
                                    }
                                )
                                bookmarkedUrls.forEach { savedUrl ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                android.net.Uri.parse(savedUrl).host ?: savedUrl,
                                                maxLines = 1
                                            )
                                        },
                                        onClick = {
                                            inputUrl = savedUrl
                                            errorMessage = null
                                            addressMenuExpanded = false
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    addressManager.removeBookmark(savedUrl)
                                                    bookmarkedUrls = addressManager.getBookmarkedUrls()
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Yer imini kaldır",
                                                    tint = Color(0xFFEF4444)
                                                )
                                            }
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Başka adres gir...") },
                                    onClick = {
                                        inputUrl = ""
                                        errorMessage = null
                                        addressMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (!addressManager.isValidUrl(normalizedInput)) {
                                errorMessage = "Yer imi için geçerli bir web adresi girin"
                            } else if (isBookmarked) {
                                addressManager.removeBookmark(normalizedInput)
                                bookmarkedUrls = addressManager.getBookmarkedUrls()
                                errorMessage = null
                            } else if (addressManager.addBookmark(normalizedInput)) {
                                bookmarkedUrls = addressManager.getBookmarkedUrls()
                                errorMessage = null
                            } else {
                                errorMessage = "En fazla ${SiteAddressManager.MAX_BOOKMARKED_URLS} yer imi kaydedilebilir"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusable(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (isBookmarked) "Yer imlerinden kaldır" else "Bu adresi yer imlerine ekle",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Mode Selection
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Aynı Alan Adı Kuralı:",
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = SameSiteMode.STRICT }
                                .padding(vertical = 4.dp)
                                .focusable()
                        ) {
                            RadioButton(
                                selected = selectedMode == SameSiteMode.STRICT,
                                onClick = { selectedMode = SameSiteMode.STRICT },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF06B6D4))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Sıkı Mod (Önerilen)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("Yalnızca bu host ve alt alan adları", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = SameSiteMode.FLEXIBLE }
                                .padding(vertical = 4.dp)
                                .focusable()
                        ) {
                            RadioButton(
                                selected = selectedMode == SameSiteMode.FLEXIBLE,
                                onClick = { selectedMode = SameSiteMode.FLEXIBLE },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF06B6D4))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Esnek Mod", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("Aynı marka etiketli diğer TLD uzantıları dahil", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Confirm Button (Focusable with remote)
                    Button(
                        onClick = {
                            if (inputUrl.isBlank()) {
                                errorMessage = "Lütfen bir web adresi girin"
                                return@Button
                            }
                            val normalized = addressManager.normalizeUrl(inputUrl)
                            if (!addressManager.isValidUrl(normalized)) {
                                errorMessage = "Geçersiz web adresi biçimi"
                                return@Button
                            }

                            addressManager.sameSiteMode = selectedMode
                            addressManager.currentUrl = normalized
                            onUrlConfigured(normalized)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .focusable(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kaydet ve Aç",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
