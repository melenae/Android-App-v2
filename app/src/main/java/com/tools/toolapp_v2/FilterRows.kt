package com.tools.toolapp_v2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun filterDropdownValueStyle() =
    MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp)

/**
 * Заголовок «Отборы» с раскрытием/сворачиванием панели фильтров ([content]).
 */
@Composable
fun CollapsibleFiltersSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Отборы",
    showHeader: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Свернуть отборы" else "Развернуть отборы",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * Фильтры: "Для меня / От меня / Все" (радио) + "Проекты" + опционально "График" (см. [TaskProjectRoadMapFiltersBar]).
 * Для Задач/Стикеров с графиком предпочтительно [TaskProjectRoadMapFiltersBar]. Календарь — [CalendarFiltersBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskAndProjectFilterRow(
    showTaskFilter: Boolean = true,
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    permittedProjects: List<Projects>,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onProjectChange: (Projects?) -> Unit,
    taskCounts: Map<TaskFilter, Int> = emptyMap(),
    projectCounts: Map<String, Int> = emptyMap(),
    roadMapOptions: List<RoadMap> = emptyList(),
    selectedRoadMap: RoadMap? = null,
    onRoadMapChange: ((RoadMap?) -> Unit)? = null,
    roadMapCounts: Map<String, Int> = emptyMap(),
    showRoadMapFilter: Boolean = false,
    placeRoadMapOnSecondRow: Boolean = true,
    /** Вкладка «Графики»: «Для меня / Все» по User — в одной строке с проектом (не совмещать с showTaskFilter). */
    showRoadMapUserScopeFilter: Boolean = false,
    roadMapUserScope: RoadMapUserScopeFilter = RoadMapUserScopeFilter.ALL,
    roadMapUserScopeCounts: Map<RoadMapUserScopeFilter, Int> = emptyMap(),
    onRoadMapUserScopeChange: (RoadMapUserScopeFilter) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var projectMenuExpanded by remember { mutableStateOf(false) }
    var roadMapMenuExpanded by remember { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val rowGap = 6.dp
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val firstRowLeadFilter = showTaskFilter || showRoadMapUserScopeFilter
            val firstRowCount = when {
                !showRoadMapFilter -> if (firstRowLeadFilter) 2 else 1
                placeRoadMapOnSecondRow -> if (firstRowLeadFilter) 2 else 1
                else -> if (firstRowLeadFilter) 3 else 2
            }
            val totalGap = rowGap * (firstRowCount - 1).coerceAtLeast(0)
            val usable = maxWidth - totalGap
            /** Два радио «Для меня/Все» на «Графиках» — уже, чем три радио у «Задач». */
            val compactRoadMapLead = showRoadMapUserScopeFilter && !showTaskFilter
            val isRoadInFirstRow = showRoadMapFilter && !placeRoadMapOnSecondRow
            val scrollState = rememberScrollState()

            var wTask = when {
                !firstRowLeadFilter -> 0.dp
                firstRowCount == 3 ->
                    if (compactRoadMapLead) usable * 0.22f else usable * 0.36f
                else ->
                    if (compactRoadMapLead) usable * 0.36f else usable * 0.55f
            }
            var wProject = when (firstRowCount) {
                3 ->
                    if (compactRoadMapLead) usable * 0.32f else usable * 0.30f
                2 -> if (firstRowLeadFilter) usable - wTask else usable * 0.5f
                else -> usable
            }
            var wRoad = when {
                showRoadMapFilter && !placeRoadMapOnSecondRow && firstRowCount == 3 ->
                    usable - wTask - wProject
                showRoadMapFilter && !placeRoadMapOnSecondRow && firstRowCount == 2 && !firstRowLeadFilter ->
                    usable - wProject
                else -> 0.dp
            }

            // Если переносим "График" в первую строку, то на узких экранах
            // может не хватать ширины. Делаем горизонтальный скролл и
            // гарантируем минимальные ширины для блоков, чтобы dropdown было читаемо.
            if (isRoadInFirstRow) {
                // Минимальная ширина для блока радио "Для меня/От меня/Все",
                // чтобы названия не обрезались (особенно "Для меня").
                val wTaskMin = 190.dp
                val wProjectMin = 170.dp
                val wRoadMin = 170.dp
                if (firstRowCount == 3) {
                    wTask = wTask.coerceAtLeast(wTaskMin)
                    wProject = wProject.coerceAtLeast(wProjectMin)
                    wRoad = wRoad.coerceAtLeast(wRoadMin)
                } else if (firstRowCount == 2) {
                    wProject = wProject.coerceAtLeast(wProjectMin)
                    wRoad = wRoad.coerceAtLeast(wRoadMin)
                }
            }
            Row(
                modifier = Modifier
                    .then(if (isRoadInFirstRow) Modifier.horizontalScroll(scrollState) else Modifier.fillMaxWidth())
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(rowGap),
                verticalAlignment = Alignment.Top
            ) {
            if (showTaskFilter) {
                Box(
                    modifier = Modifier
                        .width(wTask)
                        .fillMaxHeight()
                        .border(1.dp, lineColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 2.dp, vertical = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        listOf(
                            TaskFilter.MY to "Для меня",
                            TaskFilter.FROM_ME to "От меня",
                            TaskFilter.ALL to "Все"
                        ).forEach { (filter, label) ->
                            val countSuffix = taskCounts[filter]?.let { " ($it)" } ?: ""
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onTaskFilterChange(filter) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    // Для единообразия: число в скобках на новой строке.
                                    text = label + taskCounts[filter]?.let { "\n($it)" }.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Clip,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                RadioButton(
                                    selected = taskFilter == filter,
                                    onClick = { onTaskFilterChange(filter) },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            } else if (showRoadMapUserScopeFilter) {
                Box(
                    modifier = Modifier
                        .width(wTask)
                        .fillMaxHeight()
                        .border(1.dp, lineColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 2.dp, vertical = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        listOf(
                            RoadMapUserScopeFilter.FOR_ME to "Для меня",
                            RoadMapUserScopeFilter.ALL to "Все"
                        ).forEach { (filter, label) ->
                            val countSuffix = roadMapUserScopeCounts[filter]?.let { " ($it)" } ?: ""
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onRoadMapUserScopeChange(filter) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    // Для единообразия: число в скобках на новой строке.
                                    text = label + roadMapUserScopeCounts[filter]?.let { "\n($it)" }.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = TextOverflow.Clip,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                RadioButton(
                                    selected = roadMapUserScope == filter,
                                    onClick = { onRoadMapUserScopeChange(filter) },
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(wProject)
                    .border(1.dp, lineColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Проекты",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 0.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = projectMenuExpanded,
                        onExpandedChange = { projectMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (selectedProject == null) {
                                "Все (${projectCounts[""] ?: 0})"
                            } else {
                                "${selectedProject.name} (${projectCounts[selectedProject.id] ?: 0})"
                            },
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = projectMenuExpanded,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            textStyle = filterDropdownValueStyle()
                        )
                        DropdownMenu(
                            expanded = projectMenuExpanded,
                            onDismissRequest = { projectMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Все (${projectCounts[""] ?: 0})") },
                                onClick = {
                                    onProjectChange(null)
                                    projectMenuExpanded = false
                                }
                            )
                            permittedProjects.forEach { project ->
                                DropdownMenuItem(
                                    text = { Text("${project.name} (${projectCounts[project.id] ?: 0})") },
                                    onClick = {
                                        onProjectChange(project)
                                        projectMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (showRoadMapFilter && !placeRoadMapOnSecondRow && !(firstRowCount == 2 && firstRowLeadFilter)) {
                Box(
                    modifier = Modifier
                        .width(wRoad)
                        .border(1.dp, lineColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "График",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 0.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = roadMapMenuExpanded,
                            onExpandedChange = { roadMapMenuExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (selectedRoadMap == null) {
                                    "Все (${roadMapCounts[""] ?: 0})"
                                } else {
                                    "${selectedRoadMap.name} (${roadMapCounts[selectedRoadMap.id] ?: 0})"
                                },
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = roadMapMenuExpanded,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                textStyle = filterDropdownValueStyle()
                            )
                            DropdownMenu(
                                expanded = roadMapMenuExpanded,
                                onDismissRequest = { roadMapMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все (${roadMapCounts[""] ?: 0})") },
                                    onClick = {
                                        onRoadMapChange?.invoke(null)
                                        roadMapMenuExpanded = false
                                    }
                                )
                                roadMapOptions.forEach { roadMap ->
                                    DropdownMenuItem(
                                        text = { Text("${roadMap.name} (${roadMapCounts[roadMap.id] ?: 0})") },
                                        onClick = {
                                            onRoadMapChange?.invoke(roadMap)
                                            roadMapMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }

        if (showRoadMapFilter && placeRoadMapOnSecondRow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, lineColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "График",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 0.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = roadMapMenuExpanded,
                        onExpandedChange = { roadMapMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = if (selectedRoadMap == null) {
                                "Все (${roadMapCounts[""] ?: 0})"
                            } else {
                                "${selectedRoadMap.name} (${roadMapCounts[selectedRoadMap.id] ?: 0})"
                            },
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = roadMapMenuExpanded,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            textStyle = filterDropdownValueStyle()
                        )
                        DropdownMenu(
                            expanded = roadMapMenuExpanded,
                            onDismissRequest = { roadMapMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Все (${roadMapCounts[""] ?: 0})") },
                                onClick = {
                                    onRoadMapChange?.invoke(null)
                                    roadMapMenuExpanded = false
                                }
                            )
                            roadMapOptions.forEach { roadMap ->
                                DropdownMenuItem(
                                    text = { Text("${roadMap.name} (${roadMapCounts[roadMap.id] ?: 0})") },
                                    onClick = {
                                        onRoadMapChange?.invoke(roadMap)
                                        roadMapMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Единая панель фильтров для вкладок «Задачи» и «Стикеры»: тип + проект + график (с подсчётами).
 * [placeRoadMapOnSecondRow] — портрет: график на второй строке; ландшафт: в одной строке с проектом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskProjectRoadMapFiltersBar(
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    permittedProjects: List<Projects>,
    taskCounts: Map<TaskFilter, Int>,
    projectCounts: Map<String, Int>,
    roadMapCounts: Map<String, Int>,
    roadMapOptions: List<RoadMap>,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onProjectChange: (Projects?) -> Unit,
    onRoadMapChange: (RoadMap?) -> Unit,
    placeRoadMapOnSecondRow: Boolean,
    modifier: Modifier = Modifier
) {
    TaskAndProjectFilterRow(
        showTaskFilter = true,
        taskFilter = taskFilter,
        selectedProject = selectedProject,
        permittedProjects = permittedProjects,
        onTaskFilterChange = onTaskFilterChange,
        onProjectChange = onProjectChange,
        taskCounts = taskCounts,
        projectCounts = projectCounts,
        roadMapOptions = roadMapOptions,
        selectedRoadMap = selectedRoadMap,
        onRoadMapChange = onRoadMapChange,
        roadMapCounts = roadMapCounts,
        showRoadMapFilter = true,
        placeRoadMapOnSecondRow = placeRoadMapOnSecondRow,
        modifier = modifier
    )
}

/**
 * Только «Проекты» и «График» (без «Для меня / От меня / Все») — для вкладки «Графики» и аналогичных списков.
 * Та же вёрстка, что у [TaskProjectRoadMapFiltersBar], через [TaskAndProjectFilterRow].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectRoadMapFiltersBar(
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    permittedProjects: List<Projects>,
    projectCounts: Map<String, Int>,
    roadMapCounts: Map<String, Int>,
    roadMapOptions: List<RoadMap>,
    onProjectChange: (Projects?) -> Unit,
    onRoadMapChange: (RoadMap?) -> Unit,
    placeRoadMapOnSecondRow: Boolean,
    modifier: Modifier = Modifier
) {
    TaskAndProjectFilterRow(
        showTaskFilter = false,
        taskFilter = TaskFilter.ALL,
        selectedProject = selectedProject,
        permittedProjects = permittedProjects,
        onTaskFilterChange = {},
        onProjectChange = onProjectChange,
        taskCounts = emptyMap(),
        projectCounts = projectCounts,
        roadMapOptions = roadMapOptions,
        selectedRoadMap = selectedRoadMap,
        onRoadMapChange = onRoadMapChange,
        roadMapCounts = roadMapCounts,
        showRoadMapFilter = true,
        placeRoadMapOnSecondRow = placeRoadMapOnSecondRow,
        modifier = modifier
    )
}

/**
 * Вкладка «Графики»: «Для меня / Все» в первой ячейке строки, далее «Проекты» и «График» (как у Задач).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadMapTabFiltersBar(
    userScope: RoadMapUserScopeFilter,
    userScopeCounts: Map<RoadMapUserScopeFilter, Int>,
    onUserScopeChange: (RoadMapUserScopeFilter) -> Unit,
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    permittedProjects: List<Projects>,
    projectCounts: Map<String, Int>,
    roadMapCounts: Map<String, Int>,
    roadMapOptions: List<RoadMap>,
    onProjectChange: (Projects?) -> Unit,
    onRoadMapChange: (RoadMap?) -> Unit,
    placeRoadMapOnSecondRow: Boolean,
    modifier: Modifier = Modifier
) {
    TaskAndProjectFilterRow(
        showTaskFilter = false,
        showRoadMapUserScopeFilter = true,
        roadMapUserScope = userScope,
        roadMapUserScopeCounts = userScopeCounts,
        onRoadMapUserScopeChange = onUserScopeChange,
        taskFilter = TaskFilter.ALL,
        selectedProject = selectedProject,
        permittedProjects = permittedProjects,
        onTaskFilterChange = {},
        onProjectChange = onProjectChange,
        taskCounts = emptyMap(),
        projectCounts = projectCounts,
        roadMapOptions = roadMapOptions,
        selectedRoadMap = selectedRoadMap,
        onRoadMapChange = onRoadMapChange,
        roadMapCounts = roadMapCounts,
        showRoadMapFilter = true,
        placeRoadMapOnSecondRow = placeRoadMapOnSecondRow,
        modifier = modifier
    )
}

/**
 * Календарь: как «Задачи» — первая строка «Для меня / От меня / Все» + «Проекты»;
 * вторая — «Тип» (слева) и «График» (справа), выпадающие списки со счётчиками.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarFiltersBar(
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    selectedRoadMap: RoadMap?,
    selectedType: String?,
    permittedProjects: List<Projects>,
    taskCounts: Map<TaskFilter, Int>,
    projectCounts: Map<String, Int>,
    roadMapCounts: Map<String, Int>,
    typeCounts: Map<String, Int>,
    roadMapOptions: List<RoadMap>,
    typeOptions: List<String>,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onProjectChange: (Projects?) -> Unit,
    onRoadMapChange: (RoadMap?) -> Unit,
    onTypeChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var roadMapMenuExpanded by remember { mutableStateOf(false) }
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TaskAndProjectFilterRow(
            showTaskFilter = true,
            taskFilter = taskFilter,
            selectedProject = selectedProject,
            permittedProjects = permittedProjects,
            onTaskFilterChange = onTaskFilterChange,
            onProjectChange = onProjectChange,
            taskCounts = taskCounts,
            projectCounts = projectCounts,
            roadMapOptions = emptyList(),
            selectedRoadMap = null,
            onRoadMapChange = null,
            roadMapCounts = emptyMap(),
            showRoadMapFilter = false,
            placeRoadMapOnSecondRow = false,
            modifier = Modifier.fillMaxWidth()
        )
        val rowGap = 6.dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            val usable = maxWidth - rowGap
            val wHalf = usable / 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(wHalf)
                        .border(1.dp, lineColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Тип",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 0.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = typeMenuExpanded,
                            onExpandedChange = { typeMenuExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (selectedType == null) {
                                    "Все (${typeCounts[""] ?: 0})"
                                } else {
                                    "$selectedType (${typeCounts[selectedType] ?: 0})"
                                },
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = typeMenuExpanded,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                textStyle = filterDropdownValueStyle()
                            )
                            DropdownMenu(
                                expanded = typeMenuExpanded,
                                onDismissRequest = { typeMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все (${typeCounts[""] ?: 0})") },
                                    onClick = {
                                        onTypeChange(null)
                                        typeMenuExpanded = false
                                    }
                                )
                                typeOptions.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text("$type (${typeCounts[type] ?: 0})") },
                                        onClick = {
                                            onTypeChange(type)
                                            typeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(wHalf)
                        .border(1.dp, lineColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "График",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 0.dp)
                        )
                        ExposedDropdownMenuBox(
                            expanded = roadMapMenuExpanded,
                            onExpandedChange = { roadMapMenuExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = if (selectedRoadMap == null) {
                                    "Все (${roadMapCounts[""] ?: 0})"
                                } else {
                                    "${selectedRoadMap.name} (${roadMapCounts[selectedRoadMap.id] ?: 0})"
                                },
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = roadMapMenuExpanded,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                textStyle = filterDropdownValueStyle()
                            )
                            DropdownMenu(
                                expanded = roadMapMenuExpanded,
                                onDismissRequest = { roadMapMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все (${roadMapCounts[""] ?: 0})") },
                                    onClick = {
                                        onRoadMapChange(null)
                                        roadMapMenuExpanded = false
                                    }
                                )
                                roadMapOptions.forEach { roadMap ->
                                    DropdownMenuItem(
                                        text = { Text("${roadMap.name} (${roadMapCounts[roadMap.id] ?: 0})") },
                                        onClick = {
                                            onRoadMapChange(roadMap)
                                            roadMapMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
