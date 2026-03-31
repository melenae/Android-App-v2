package com.tools.toolapp_v2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun fitLandscapeStatusFontSizeSp(
    text: String,
    availableDp: Float,
    preferredSp: Float = 16f,
    minSp: Float = 10f
): Float {
    if (availableDp <= 0f) return minSp
    var size = preferredSp
    while (size > minSp && text.length * size * 0.56f > availableDp) {
        size -= 1f
    }
    return size.coerceAtLeast(minSp)
}

@Composable
fun TasksLandscapeScreen(
    issues: List<Issues>,
    currentUser: Users? = null,
    companies: List<Companies> = emptyList(),
    onIssueClick: (Issues) -> Unit = {},
    compactCards: Boolean = false,
    modifier: Modifier = Modifier
) {
    val boardStatuses = IssueStatuses.boardStatuses
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, lineColor, RoundedCornerShape(6.dp))
    ) {
        val headerTextHeight = 20.dp // Эвристика для оценки высоты заголовка.
        val dividerWidth = 1.dp
        val columnWidth = (maxWidth - dividerWidth * (boardStatuses.size - 1)) / boardStatuses.size

        // В ландшафте высоту карточек не ограничиваем: каждая карточка должна показать все 4 строки.
        // Поэтому не рассчитываем принудительный cardHeight — оставляем естественную высоту content.
        val effectiveCompact = compactCards
        val headerVerticalPadding = if (effectiveCompact) 4.dp else 8.dp
        val listPadding = if (effectiveCompact) 1.dp else 2.dp
        val listSpacing = if (effectiveCompact) 1.dp else 2.dp

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            boardStatuses.forEachIndexed { index, status ->
                val statusIssues = issues.filter { it.status == status }
                Column(
                    modifier = Modifier
                        .width(columnWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = headerVerticalPadding)
                    ) {
                        val statusTitle = "${status.displayName} (${statusIssues.size})"
                        val fittedSp = fitLandscapeStatusFontSizeSp(
                            text = statusTitle,
                            availableDp = columnWidth.value - 14f,
                            preferredSp = 16f,
                            minSp = 10f
                        )
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = fittedSp.sp),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = lineColor)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(listPadding),
                        verticalArrangement = Arrangement.spacedBy(listSpacing)
                    ) {
                        items(
                            items = statusIssues,
                            key = { it.id }
                        ) { issue ->
                            IssueCard(
                                issue = issue,
                                currentUser = currentUser,
                                companies = companies,
                                onClick = { onIssueClick(issue) },
                                compact = effectiveCompact,
                            modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (index < boardStatuses.lastIndex) {
                    androidx.compose.material3.VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = dividerWidth,
                        color = lineColor
                    )
                }
            }
        }
    }
}
