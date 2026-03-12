package com.tools.toolapp_v2

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tools.toolapp_v2.R
import android.content.res.Configuration

private val cardWidthRow = 240.dp  // 1.5 × 160 — вторая карточка в строке не помещается целиком
private val spacingBetweenCards = 4.dp
private val minCardHeight = 48.dp

@Composable
fun IssueCard(
    issue: Issues,
    currentUser: Users?,
    companies: List<Companies> = emptyList(),
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasUnreadForCurrentUser = currentUser != null && (
        (currentUser.id == issue.user?.id && issue.newCommentForUser) ||
        (currentUser.id == issue.applicant?.id && issue.newCommentForApplicant)
    )
    Card(
        onClick = onClick,
        modifier = modifier
            .padding(vertical = 2.dp, horizontal = 4.dp)
            .then(
                if (hasUnreadForCurrentUser)
                    Modifier.border(2.dp, Color.Red, RoundedCornerShape(6.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
        Column(modifier = Modifier.padding(6.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = issue.project?.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Blue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = companies
                        .firstOrNull { it.project?.id == issue.project?.id }
                        ?.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Blue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = issue.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (issue.content.isNotBlank()) {
                Text(
                    text = issue.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "для: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = issue.user?.displayName ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Red,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " от: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = issue.applicant?.displayName ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Green,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
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
    modifier: Modifier = Modifier
) {
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
    ) {
        // Высота карточки: заполняем дорожку (2 карточки + отступ между ними)
        val verticalPadding = 8.dp  // LazyRow padding 4+4
        val cardHeight = maxOf(
            ((maxHeight - verticalPadding - spacingBetweenCards).value / 2f).dp,
            minCardHeight
        )
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
        // Колонка с картинкой статуса. Ширина достаточная, чтобы повёрнутая картинка/текст не сжимались.
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPortrait) {
                val drawableId = when (status) {
                    IssueStatuses.NEW -> R.drawable.statusnew
                    IssueStatuses.IN_PROGRESS -> R.drawable.statusinprogress
                    IssueStatuses.AWAITING -> R.drawable.statusawaiting
                    IssueStatuses.TESTING -> R.drawable.statustesting
                    else -> null
                }
                if (drawableId != null) {
                    Image(
                        painter = painterResource(drawableId),
                        contentDescription = status.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .sizeIn(minWidth = 24.dp, minHeight = 48.dp)
                            .fillMaxHeight()
                            .rotate(-90f)
                    )
                } else {
                    Text(
                        text = status.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.rotate(-90f)
                    )
                }
            } else {
                Text(
                    text = status.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        // Горизонтальный скролл: по 2 карточки по вертикали, высота карточек заполняет дорожку
        val pairs = issues.chunked(2)
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(spacingBetweenCards),
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
                    verticalArrangement = Arrangement.spacedBy(spacingBetweenCards)
                ) {
                    pair.forEach { issue ->
                        IssueCard(
                            issue = issue,
                            currentUser = currentUser,
                            companies = companies,
                            onClick = { onIssueClick(issue) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight)
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
    modifier: Modifier = Modifier
) {
    val boardStatuses = IssueStatuses.boardStatuses
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        boardStatuses.forEach { status ->
            val list = issues.filter { it.status == status }
            StatusBlock(
                status = status,
                issues = list,
                currentUser = currentUser,
                companies = companies,
                onIssueClick = onIssueClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
