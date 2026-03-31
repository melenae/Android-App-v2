package com.tools.toolapp_v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Раньше включала экспериментальную вёрстку; сейчас [IssueFormScreen] сам даёт две колонки в ландшафте.
 * Оставлен как алиас для совместимости (можно заменить вызовы на [IssueFormScreen]).
 */
@Composable
fun IssueFormLandscapeExperimentScreen(
    issue: Issues,
    onDismiss: () -> Unit,
    onSaveIssue: (Issues) -> Unit,
    onUpdateIssue: (Issues) -> Unit = {},
    currentUser: Users,
    allIssues: List<Issues>,
    permittedProjects: List<Projects>,
    allUsers: List<Users>,
    allCompanies: List<Companies>,
    allRoadMaps: List<RoadMap> = emptyList(),
    modifier: Modifier = Modifier
) {
    IssueFormScreen(
        issue = issue,
        onDismiss = onDismiss,
        onSaveIssue = onSaveIssue,
        onUpdateIssue = onUpdateIssue,
        currentUser = currentUser,
        allIssues = allIssues,
        permittedProjects = permittedProjects,
        allUsers = allUsers,
        allCompanies = allCompanies,
        allRoadMaps = allRoadMaps,
        modifier = modifier
    )
}
