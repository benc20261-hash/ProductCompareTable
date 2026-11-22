package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.PictureItem
import java.io.File
import com.example.model.resolveImageModel
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppSpacing

/**
 * AppBadge: Safe, single-line pill badge that NEVER wraps vertically.
 * Prevents horizontal squeezing and runaway vertical card expansion.
 */
@Composable
fun AppBadge(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.PrimaryBrandLight,
    textColor: Color = AppColors.PrimaryBrand,
    borderColor: Color? = null,
    fontSize: TextUnit = 11.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    horizontalPadding: Dp = 7.dp,
    verticalPadding: Dp = 2.dp,
    cornerRadius: Dp = AppSpacing.badgeCornerRadius,
    icon: @Composable (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        border = borderColor?.let { BorderStroke(1.dp, it) },
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = text,
                color = textColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * AppCardHeader: Standard card header layout ensuring:
 * 1. Title/subtitle Column always receives Modifier.weight(1f) when action/badge is present.
 * 2. Badges and subtitles wrap gracefully beneath the title, never getting squeezed into 1-character columns.
 * 3. Prevents multi-line vertical text wrapping and rogue white gaps across all screen sizes.
 */
@Composable
fun AppCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeColor: Color = Color(0xFF15803D),
    badgeBackground: Color = Color(0xFFF0FDF4),
    badgeBorder: Color? = Color(0xFFBBF7D0),
    icon: @Composable (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (icon != null) {
            icon()
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AppColors.HeadingText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (badgeText != null) {
                    AppBadge(
                        text = badgeText,
                        textColor = badgeColor,
                        backgroundColor = badgeBackground,
                        borderColor = badgeBorder,
                        fontSize = 10.sp
                    )
                }
            }

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = AppColors.SubtitleText,
                    lineHeight = 16.sp
                )
            }
        }

        if (action != null) {
            action()
        }
    }
}

/**
 * AppSummaryCard: Standard layout for summary and status bars.
 * Keeps stats on top with secondary actions (like Reset),
 * and places prominent navigation actions in a dedicated full-width row below.
 * Completely eliminates the "squeezed action button" bug that created the 180dp tall black pillar.
 */
@Composable
fun AppSummaryCard(
    statsText: String,
    modifier: Modifier = Modifier,
    secondaryText: String? = null,
    onReset: (() -> Unit)? = null,
    resetLabel: String = "Reset",
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionButtonColor: Color = AppColors.HeadingText
) {
    Card(
        shape = RoundedCornerShape(AppSpacing.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        border = BorderStroke(1.dp, AppColors.CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = statsText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.HeadingText
                    )
                    if (secondaryText != null) {
                        Text("•", fontSize = 12.sp, color = AppColors.PlaceholderText)
                        Text(
                            text = secondaryText,
                            fontSize = 12.sp,
                            color = AppColors.SubtitleText
                        )
                    }
                }

                if (onReset != null) {
                    TextButton(
                        onClick = onReset,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = resetLabel,
                            fontSize = 12.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (actionButtonText != null && onActionClick != null) {
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(AppSpacing.buttonCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = actionButtonColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    ButtonText(
                        text = actionButtonText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Common preset list of quick unit options for specifications.
 */
val PRESET_QUICK_UNITS = listOf(
    "g", "kg", "inch", "cm", "mm", "year", "month", "mAh", "Hz", "$", "GB", "TB", "W", "V", "mA", "K"
)

/**
 * DashedDropzoneBox: Standard upload dropzone container with upload icon and file picker button.
 * Supports choosing from photo gallery, browsing storage/downloads (critical for BlueStacks and emulators),
 * and picking from existing app photo library.
 */
@Composable
fun DashedDropzoneBox(
    buttonText: String = "Choose pictures",
    browseFilesText: String = "Browse files / storage",
    helperText: String = "Select from photo library, downloads, or BlueStacks files",
    onPickClick: () -> Unit,
    onBrowseFilesClick: (() -> Unit)? = null,
    onAppLibraryClick: (() -> Unit)? = null,
    testTag: String = "dashed_dropzone_box"
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFAFAFA))
            .border(
                width = 1.dp,
                color = Color(0xFFCBD5E1),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 16.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(32.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPickClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag(testTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                }

                if (onBrowseFilesClick != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onBrowseFilesClick,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF1F5F9)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("${testTag}_browse_files")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(browseFilesText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                    }
                }
            }

            if (onAppLibraryClick != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEF2FF),
                    border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                    modifier = Modifier.clickable { onAppLibraryClick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Collections,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Or pick from App Photo Library",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4F46E5)
                        )
                    }
                }
            }

            Text(
                helperText,
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

/**
 * PhotoThumbnailItem: Spacious thumbnail preview with tap-to-open lightbox and clear top-end delete button.
 */
@Composable
fun PhotoThumbnailItem(
    picture: PictureItem,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveClick: (() -> Unit)? = null,
    size: Dp = 120.dp
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(12.dp))
            .clickable { onPhotoClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = resolveImageModel(context, picture.url, preferredName = picture.name),
            contentDescription = picture.name.ifBlank { "Photo" },
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        if (onRemoveClick != null) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .clickable { onRemoveClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove photo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * SpecificationFormContent: Shared specification editing component used across Product Edit and Smart Add screens.
 */
@Composable
fun SpecificationFormContent(
    specName: String,
    onSpecNameChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    details: String,
    onDetailsChange: (String) -> Unit,
    isNumeric: Boolean?, // null = Auto, true = Number, false = Text
    onIsNumericChange: (Boolean?) -> Unit,
    pictures: List<PictureItem>,
    onPickPicturesClick: () -> Unit,
    onRemovePictureClick: (PictureItem) -> Unit,
    onPictureClick: (PictureItem, Int) -> Unit,
    modifier: Modifier = Modifier,
    isSpecNameReadOnly: Boolean = false,
    onBrowseFilesClick: (() -> Unit)? = null,
    onAppLibraryClick: (() -> Unit)? = null,
    testTagPrefix: String = "spec"
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. Specification name field
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Specification name", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
            OutlinedTextField(
                value = specName,
                onValueChange = onSpecNameChange,
                readOnly = isSpecNameReadOnly,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedBorderColor = Color(0xFF0F172A),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }

        // 2. Unit (optional) field + Quick unit pills
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unit (optional)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Text("e.g. g, inch, cm", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }

            OutlinedTextField(
                value = unit,
                onValueChange = onUnitChange,
                placeholder = { Text("g, inch, cm, year, mAh...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("${testTagPrefix}_unit_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedBorderColor = Color(0xFF0F172A),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Quick unit:", fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                PRESET_QUICK_UNITS.forEach { qUnit ->
                    val isSelected = unit.equals(qUnit, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)),
                        modifier = Modifier.clickable { onUnitChange(qUnit) }
                    ) {
                        Text(
                            text = qUnit,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.White else Color(0xFF334155),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 3. Specification details (WYSIWYG Editor)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Specification details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                Text("WYSIWYG: Bold, underline, list & links", fontSize = 10.sp, color = Color(0xFF94A3B8))
            }

            HtmlWysiwygEditor(
                value = details,
                onChange = onDetailsChange,
                placeholder = "e.g. 60hour, Bluetooth, stick to phone, and ambient",
                minHeight = 100.dp,
                testTagPrefix = "${testTagPrefix}_details"
            )
        }

        // 4. Auto-Detect / Numeric Mode selector banner
        val hasNumericCandidate = remember(details) {
            val clean = htmlToPlainText(details)
            !clean.contains('\n') && !clean.contains('•') && clean.length <= 45 && Regex("[0-9]").containsMatchIn(clean)
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (isNumeric) {
                                true -> Color(0xFF4F46E5)
                                false -> Color(0xFF475569)
                                null -> Color(0xFF0F172A)
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (isNumeric) {
                                        true -> "#"
                                        false -> "Aa"
                                        null -> "⚡"
                                    },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column {
                            Text(
                                text = when (isNumeric) {
                                    true -> "Numeric Comparison (Forced #)"
                                    false -> "Text Mode (Forced Aa)"
                                    null -> "Auto-Detect Mode"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = when (isNumeric) {
                                    true -> "Parsed as numeric for matrix sorting & best-value calculation"
                                    false -> "Treated strictly as text/descriptive without numeric sorting"
                                    null -> if (hasNumericCandidate) "Auto: Candidate number detected in text" else "Auto: Descriptive text format"
                                },
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("Auto", null, "${testTagPrefix}_auto_btn"),
                        Triple("Number (#)", true, "${testTagPrefix}_num_btn"),
                        Triple("Text (Aa)", false, "${testTagPrefix}_text_btn")
                    ).forEach { (label, value, tag) ->
                        val isSelected = isNumeric == value
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF0F172A) else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onIsNumericChange(value) }
                                .testTag(tag)
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF334155)
                            )
                        }
                    }
                }
            }
        }

        // 5. Specification pictures
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Specification pictures", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))

            DashedDropzoneBox(
                buttonText = "Choose pictures",
                browseFilesText = "Browse files / storage",
                helperText = "Select from photo library, downloads, or BlueStacks files",
                onPickClick = onPickPicturesClick,
                onBrowseFilesClick = onBrowseFilesClick,
                onAppLibraryClick = onAppLibraryClick,
                testTag = "${testTagPrefix}_dropzone"
            )

            if (pictures.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    itemsIndexed(pictures) { pIdx, spPic ->
                        PhotoThumbnailItem(
                            picture = spPic,
                            onPhotoClick = { onPictureClick(spPic, pIdx) },
                            onRemoveClick = { onRemovePictureClick(spPic) },
                            size = 120.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * AppPhotoLibraryDialog: Lets user select from images already saved in the app's internal storage
 * (pictures, review_images, and existing products/specs).
 * Solves the problem in emulators like BlueStacks where system photo picker cannot find external files.
 */
@Composable
fun AppPhotoLibraryDialog(
    onDismiss: () -> Unit,
    onSelectPictures: (List<PictureItem>) -> Unit,
    allowMultiple: Boolean = true
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val localImages = remember {
        val result = mutableListOf<PictureItem>()
        val seenNames = mutableSetOf<String>()

        val dirs = listOf(
            File(context.filesDir, "pictures"),
            File(context.filesDir, "review_images")
        )

        dirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()
                    ?.filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { f ->
                        if (seenNames.add(f.name)) {
                            result.add(
                                PictureItem(
                                    id = "local_${f.name}",
                                    name = f.name,
                                    url = f.absolutePath
                                )
                            )
                        }
                    }
            }
        }
        result
    }

    val filteredImages = remember(localImages, searchQuery) {
        if (searchQuery.isBlank()) localImages
        else localImages.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Collections, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(22.dp))
                            Text("App Photo Library", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                        }
                        Text(
                            if (localImages.isNotEmpty()) "${localImages.size} photos saved in app storage"
                            else "No images saved in app storage yet",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search photos by filename...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Photos Grid
                if (filteredImages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(Icons.Default.ImageNotSupported, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(40.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "No photos matching \"$searchQuery\""
                                else "No photos found in app library yet.\nUse 'Browse files' to pick images from Downloads or BlueStacks storage.",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredImages.size) { idx ->
                            val pic = filteredImages[idx]
                            val isSelected = selectedIds.contains(pic.id)
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Color(0xFF4F46E5) else Color(0xFFCBD5E1),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        if (allowMultiple) {
                                            if (isSelected) selectedIds.remove(pic.id)
                                            else selectedIds.add(pic.id)
                                        } else {
                                            onSelectPictures(listOf(pic))
                                            onDismiss()
                                        }
                                    }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = resolveImageModel(context, pic.url, preferredName = pic.name),
                                    contentDescription = pic.name,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (allowMultiple && isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF4F46E5),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp).padding(2.dp))
                                    }
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Text(
                                        text = pic.name,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF64748B))
                    }
                    if (allowMultiple) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val chosen = filteredImages.filter { it.id in selectedIds }
                                onSelectPictures(chosen)
                                onDismiss()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Text("Attach ${if (selectedIds.isNotEmpty()) "(${selectedIds.size})" else ""}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

