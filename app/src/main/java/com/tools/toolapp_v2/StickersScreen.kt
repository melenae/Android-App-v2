package com.tools.toolapp_v2

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private fun userKey(u: Users): String =
    userDisplayId(
        u.displayName,
        u.email.takeIf { it.isNotBlank() } ?: u.login
    )

@Composable
fun StickersScreen(
    notes: List<Notes>,
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    permittedProjects: List<Projects>,
    taskCounts: Map<TaskFilter, Int>,
    projectCounts: Map<String, Int>,
    roadMapCounts: Map<String, Int>,
    roadMapOptions: List<RoadMap>,
    currentUser: Users,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onProjectChange: (Projects?) -> Unit,
    onRoadMapChange: (RoadMap?) -> Unit,
    onNoteClick: (Notes) -> Unit,
    showEmbeddedFilters: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val listPadding = PaddingValues(8.dp)
    val listSpacing = 6.dp
    Column(modifier = modifier.fillMaxSize()) {
        if (showEmbeddedFilters) {
            TaskProjectRoadMapFiltersBar(
                taskFilter = taskFilter,
                selectedProject = selectedProject,
                selectedRoadMap = selectedRoadMap,
                permittedProjects = permittedProjects,
                taskCounts = taskCounts,
                projectCounts = projectCounts,
                roadMapCounts = roadMapCounts,
                roadMapOptions = roadMapOptions,
                onTaskFilterChange = onTaskFilterChange,
                onProjectChange = onProjectChange,
                onRoadMapChange = onRoadMapChange,
                placeRoadMapOnSecondRow = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLandscape) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding,
                    horizontalArrangement = Arrangement.spacedBy(listSpacing),
                    verticalArrangement = Arrangement.spacedBy(listSpacing)
                ) {
                    lazyGridItems(items = notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            currentUser = currentUser,
                            onClick = { onNoteClick(note) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listPadding,
                    verticalArrangement = Arrangement.spacedBy(listSpacing)
                ) {
                    lazyColumnItems(notes) { note ->
                        NoteCard(
                            note = note,
                            currentUser = currentUser,
                            onClick = { onNoteClick(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Notes,
    currentUser: Users,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isResponsibleByUser = note.user?.let { userKey(it) == userKey(currentUser) } == true
    val isApplicantByUser = note.applicant?.let { userKey(it) == userKey(currentUser) } == true
    val projectBoldStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = projectLineWithOptionalRoadMap(note.project, note.roadMap).ifBlank { "—" },
                style = projectBoldStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = note.content.ifEmpty() { "—" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (isResponsibleByUser) Color.Blue else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "для:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = note.user?.displayName ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isResponsibleByUser) Color.Red else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "от:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = note.applicant?.displayName ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isApplicantByUser) Color.Red else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
