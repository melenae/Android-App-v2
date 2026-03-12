package com.tools.toolapp_v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.unit.dp

/**
 * Одна строка фильтров: "Для меня / От меня / Все" (радио) + выпадающий список "Проект" (Все + PermittedProjects).
 * Используется на вкладках Задачи, Стикеры, Календарь.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskAndProjectFilterRow(
    taskFilter: TaskFilter,
    selectedProject: Projects?,
    permittedProjects: List<Projects>,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onProjectChange: (Projects?) -> Unit,
    modifier: Modifier = Modifier
) {
    var projectMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                TaskFilter.MY to "Для меня",
                TaskFilter.FROM_ME to "От меня",
                TaskFilter.ALL to "Все"
            ).forEach { (filter, label) ->
                Row(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clickable { onTaskFilterChange(filter) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = taskFilter == filter,
                        onClick = { onTaskFilterChange(filter) }
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = projectMenuExpanded,
            onExpandedChange = { projectMenuExpanded = it },
            modifier = Modifier.widthIn(min = 120.dp)
        ) {
            OutlinedTextField(
                value = selectedProject?.name ?: "Все",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectMenuExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            DropdownMenu(
                expanded = projectMenuExpanded,
                onDismissRequest = { projectMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Все") },
                    onClick = {
                        onProjectChange(null)
                        projectMenuExpanded = false
                    }
                )
                permittedProjects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
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
