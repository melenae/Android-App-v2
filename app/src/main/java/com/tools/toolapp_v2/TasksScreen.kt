package com.tools.toolapp_v2

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val cardWidthRow = 240.dp  // 1.5 × 160 — вторая карточка в строке не помещается целиком
private val spacingBetweenCards = 4.dp
private val minCardHeight = 48.dp

private fun fitStatusFontSizeSp(
    text: String,
    availableDp: Float,
    preferredSp: Float = 20f,
    minSp: Float = 11f
): Float {
    if (availableDp <= 0f) return minSp
    var size = preferredSp
    // Эвристика: средняя ширина символа ~0.56 от размера шрифта.
    while (size > minSp && text.length * size * 0.56f > availableDp) {
        size -= 1f
    }
    return size.coerceAtLeast(minSp)
}

private fun TextStyle.copyWithFontSizeDeltaSp(deltaSp: Float): TextStyle {
    val fs = fontSize
    if (fs == TextUnit.Unspecified) return this
    val newFontSize = (fs.value - deltaSp).coerceAtLeast(1f).sp

    val lh = lineHeight
    val newLineHeight = if (lh == TextUnit.Unspecified) lh else (lh.value - deltaSp).coerceAtLeast(1f).sp

    return copy(fontSize = newFontSize, lineHeight = newLineHeight)
}

@Composable
fun IssueCard(
    issue: Issues,
    currentUser: Users?,
    companies: List<Companies> = emptyList(),
    onClick: () -> Unit = {},
    compact: Boolean = false,
    showContent: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasUnreadForCurrentUser = currentUser != null && (
        (currentUser.id == issue.user?.id && issue.newCommentForUser) ||
        (currentUser.id == issue.applicant?.id && issue.newCommentForApplicant)
    )

    val isTaskForCurrentUser = currentUser != null && currentUser.id == issue.user?.id
    val titleColor = if (isTaskForCurrentUser) Color.Blue else MaterialTheme.colorScheme.onSurface
    val isApplicantForCurrentUser = currentUser != null && currentUser.id == issue.applicant?.id

    // На реальных телефонах карточки иногда обрезаются — уменьшаем плотность.
    val deltaSp = if (compact) 2f else 1f
    val labelSmall = MaterialTheme.typography.labelSmall.copyWithFontSizeDeltaSp(deltaSp = deltaSp)
    // Наименование задачи: фиксированный размер (как в горизонтальном), независимо от фильтров.
    val titleDeltaSp = 1f
    val titleSmall = MaterialTheme.typography.titleSmall.copyWithFontSizeDeltaSp(deltaSp = titleDeltaSp)
    val bodySmall = MaterialTheme.typography.bodySmall.copyWithFontSizeDeltaSp(deltaSp = deltaSp)
    val projectBoldStyle = labelSmall.copy(fontWeight = FontWeight.Bold)

    Card(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = 1.dp, horizontal = 4.dp)
            .then(
                if (hasUnreadForCurrentUser)
                    Modifier.border(2.dp, Color.Red, RoundedCornerShape(6.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
        Column(
            modifier = Modifier
                .padding(if (compact) 4.dp else 5.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
            // Распределяем "лишнюю" высоту между всеми видимыми элементами карточки,
            // чтобы не было перекоса между 3-й и 4-й строкой.
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            val companyName = (companies
                .firstOrNull { it.project?.id == issue.project?.id }
                ?.name ?: "").trim()
            val topGap = if (compact) 2.dp else 4.dp
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val projectLineText = projectLineWithOptionalRoadMap(issue.project, issue.roadMap)
                if (companyName.isEmpty()) {
                    Text(
                        text = projectLineText,
                        style = projectBoldStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val companyMax = maxWidth * 0.4f
                    val projectW = (maxWidth - companyMax - topGap).coerceAtLeast(48.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(topGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = projectLineText,
                            style = projectBoldStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(projectW)
                        )
                        Text(
                            text = companyName,
                            style = labelSmall,
                            color = Color.Blue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = companyMax)
                        )
                    }
                }
            }
            Text(
                text = issue.name,
                style = titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = titleColor
            )
            if (showContent && issue.content.isNotBlank()) {
                Text(
                    text = issue.content,
                    style = bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val labelReserve = 52.dp
                val nameColW = ((maxWidth - labelReserve) / 2).coerceAtLeast(36.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "для: ",
                            style = labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = issue.user?.displayName ?: "—",
                            style = labelSmall,
                        color = Color.Red,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(nameColW)
                    )
                    Text(
                        text = " от: ",
                            style = labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = issue.applicant?.displayName ?: "—",
                            style = labelSmall,
                        color = if (isApplicantForCurrentUser) Color.Red else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(nameColW)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBlock(
    status: IssueStatuses,
    issues: List<Issues>,
    currentUser: Users? = null,
    companies: List<Companies> = emptyList(),
    onIssueClick: (Issues) -> Unit = {},
    compact: Boolean = false,
    showContent: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, lineColor, RoundedCornerShape(6.dp))
    ) {
        // Если высоты недостаточно — включаем плотный режим.
        // Это нужно, чтобы 4-я строка текста не обрезалась при "узких" по высоте статус-блоках.
        val cardHeightNonCompact =
            ((maxHeight - 8.dp - spacingBetweenCards).value / 2f).dp
        val effectiveCompact = compact || cardHeightNonCompact < (minCardHeight + 4.dp)

        // Высота карточки: заполняем дорожку (2 карточки + отступ между ними)
        val verticalPadding = if (effectiveCompact) 2.dp else 8.dp // LazyRow padding
        val spacingBetweenCardsLocal = if (effectiveCompact) 2.dp else spacingBetweenCards
        val minHeight = if (effectiveCompact) minCardHeight - 6.dp else minCardHeight
        val cardHeight = maxOf(
            ((maxHeight - verticalPadding - spacingBetweenCardsLocal).value / 2f).dp,
            minHeight
        )
        val blockMaxWidth = maxWidth
        val cardSpacing = spacingBetweenCardsLocal
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
        // Колонка с картинкой статуса. Ширина достаточная, чтобы повёрнутая картинка/текст не сжимались.
        BoxWithConstraints(
            modifier = Modifier
                .width(68.dp)
                .fillMaxHeight()
                .padding(horizontal = 1.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPortrait) {
                val statusTitle = "${status.displayName} (${issues.size})"
                val fittedSp = fitStatusFontSizeSp(
                    text = statusTitle,
                    availableDp = (maxHeight - 8.dp).value,
                    preferredSp = 20f,
                    minSp = 11f
                )
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = fittedSp.sp),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        // Даем тексту измериться по собственной ширине до поворота,
                        // иначе он режется шириной левой колонки.
                        .wrapContentWidth(unbounded = true)
                        .rotate(-90f)
                )
            } else {
                Text(
                    text = status.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = lineColor
        )
        // Горизонтальный скролл: по 2 карточки по вертикали, высота карточек заполняет дорожку
        val pairs = issues.chunked(2)
        val statusColW = 68.dp
        val dividerW = 1.dp
        val listW = (blockMaxWidth - statusColW - dividerW).coerceAtLeast(0.dp)
        LazyRow(
            modifier = Modifier
                .width(listW)
                .fillMaxHeight()
                .padding(if (compact) 1.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
            contentPadding = PaddingValues(end = 32.dp)
        ) {
            items(
                items = pairs,
                key = { pair -> pair.map { it.id }.joinToString(",") }
            ) { pair ->
                Column(
                    modifier = Modifier
                        .width(cardWidthRow)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(cardSpacing)
                ) {
                    pair.forEach { issue ->
                        IssueCard(
                            issue = issue,
                            currentUser = currentUser,
                            companies = companies,
                            onClick = { onIssueClick(issue) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight),
                            // При недостатке места дополнительно "плотним" карточку.
                            compact = effectiveCompact,
                            showContent = showContent
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
fun TasksScreen(
    issues: List<Issues>,
    currentUser: Users? = null,
    companies: List<Companies> = emptyList(),
    onIssueClick: (Issues) -> Unit = {},
    filtersExpanded: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val compactCards = !filtersExpanded
    if (!isPortrait) {
        TasksLandscapeScreen(
            issues = issues,
            currentUser = currentUser,
            companies = companies,
            onIssueClick = onIssueClick,
            compactCards = compactCards,
            modifier = modifier
        )
        return
    }

    val boardStatuses = IssueStatuses.boardStatuses
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val nRows = boardStatuses.size
    val showIssueContent = !isPortrait || !filtersExpanded
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, lineColor, RoundedCornerShape(6.dp))
    ) {
        val dividerThickness = 1.dp
        val dividersTotal = dividerThickness * (nRows - 1).coerceAtLeast(0)
        val rowH = ((maxHeight - dividersTotal) / nRows).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            boardStatuses.forEachIndexed { index, status ->
                val list = issues.filter { it.status == status }
                StatusBlock(
                    status = status,
                    issues = list,
                    currentUser = currentUser,
                    companies = companies,
                    onIssueClick = onIssueClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowH),
                    compact = compactCards,
                    showContent = showIssueContent
                )
                if (index < boardStatuses.lastIndex) {
                    HorizontalDivider(
                        thickness = dividerThickness,
                        color = lineColor
                    )
                }
            }
        }
    }
}
