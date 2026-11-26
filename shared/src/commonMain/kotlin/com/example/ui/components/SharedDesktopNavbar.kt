package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SharedNavTab {
    CATALOG, COMPARE, SMART_ADD, REVIEWS, DATA_BACKUP
}

@Composable
fun SharedDesktopNavbar(
    activeTab: SharedNavTab,
    onTabSelected: (SharedNavTab) -> Unit,
    productCount: Int,
    compareCount: Int,
    reviewCount: Int,
    onAddProductClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isNarrow = maxWidth < 1050.dp
            val isUltraCompact = maxWidth < 850.dp
            val vertPadding = if (isNarrow) 5.dp else 10.dp
            val horizPadding = if (isNarrow) 12.dp else 20.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizPadding, vertical = vertPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---- Brand ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 6.dp else 10.dp),
                    modifier = Modifier.clickable { onTabSelected(SharedNavTab.CATALOG) }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.size(if (isNarrow) 30.dp else 36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Compare,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isNarrow) 18.dp else 22.dp)
                            )
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "ProductCompare",
                                fontWeight = FontWeight.Black,
                                fontSize = if (isNarrow) 14.sp else 16.sp,
                                color = Color(0xFF0F172A)
                            )
                            if (!isUltraCompact) {
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = Color(0xFFEFF6FF)
                                ) {
                                    Text(
                                        "KMP",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2563EB),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (!isNarrow) {
                            Text(
                                "Side-by-side consumer tech benchmark",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // ---- Tab Row ----
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .padding(horizontal = if (isNarrow) 4.dp else 8.dp)
                        .weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 2.dp else 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavTabItem(
                            label = "Catalog",
                            icon = Icons.Default.Home,
                            badgeCount = if (productCount > 0) productCount else null,
                            isSelected = activeTab == SharedNavTab.CATALOG,
                            isCompact = isNarrow,
                            onClick = { onTabSelected(SharedNavTab.CATALOG) }
                        )
                        NavTabItem(
                            label = "Compare",
                            icon = Icons.Default.Compare,
                            badgeCount = if (compareCount > 0) compareCount else null,
                            isSelected = activeTab == SharedNavTab.COMPARE,
                            isCompact = isNarrow,
                            onClick = { onTabSelected(SharedNavTab.COMPARE) }
                        )
                        // Smart Add — amber accent pill
                        val isSmartAdd = activeTab == SharedNavTab.SMART_ADD
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (isSmartAdd) Color(0xFF0F172A) else Color(0xFFFEF3C7),
                            shadowElevation = if (isSmartAdd) 1.dp else 0.dp,
                            modifier = Modifier.clickable { onTabSelected(SharedNavTab.SMART_ADD) }
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = if (isNarrow) 8.dp else 12.dp,
                                    vertical = if (isNarrow) 4.dp else 6.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (isSmartAdd) Color(0xFFFBBF24) else Color(0xFFD97706),
                                    modifier = Modifier.size(if (isNarrow) 13.dp else 15.dp)
                                )
                                Text(
                                    "Smart Add",
                                    fontSize = if (isNarrow) 11.sp else 12.sp,
                                    fontWeight = if (isSmartAdd) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSmartAdd) Color.White else Color(0xFF92400E)
                                )
                            }
                        }
                        NavTabItem(
                            label = "Reviews",
                            icon = Icons.Default.Star,
                            badgeCount = if (reviewCount > 0) reviewCount else null,
                            isSelected = activeTab == SharedNavTab.REVIEWS,
                            isCompact = isNarrow,
                            onClick = { onTabSelected(SharedNavTab.REVIEWS) }
                        )
                        NavTabItem(
                            label = if (isNarrow) "Backup" else "Data & Backup",
                            icon = Icons.Default.Backup,
                            isSelected = activeTab == SharedNavTab.DATA_BACKUP,
                            isCompact = isNarrow,
                            onClick = { onTabSelected(SharedNavTab.DATA_BACKUP) }
                        )
                    }
                }

                // ---- Actions ----
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 4.dp else 8.dp)
                ) {
                    Button(
                        onClick = onAddProductClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(
                            horizontal = if (isNarrow) 8.dp else 12.dp,
                            vertical = if (isNarrow) 4.dp else 6.dp
                        ),
                        modifier = Modifier.height(if (isNarrow) 32.dp else 36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(if (isNarrow) 14.dp else 16.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            if (isNarrow) "Add" else "+ Add Product",
                            fontSize = if (isNarrow) 11.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    badgeCount: Int? = null,
    isCompact: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(7.dp),
        color = if (isSelected) Color.White else Color.Transparent,
        shadowElevation = if (isSelected) 1.dp else 0.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (isCompact) 8.dp else 12.dp,
                vertical = if (isCompact) 4.dp else 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF4F46E5) else Color(0xFF64748B),
                modifier = Modifier.size(if (isCompact) 13.dp else 15.dp)
            )
            Text(
                text = label,
                fontSize = if (isCompact) 11.sp else 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B)
            )
            if (badgeCount != null) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(
                        text = "$badgeCount",
                        fontSize = if (isCompact) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
