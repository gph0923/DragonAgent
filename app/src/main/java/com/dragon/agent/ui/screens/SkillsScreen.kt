package com.dragon.agent.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragon.agent.skills.InstalledSkill
import com.dragon.agent.skills.SkillSource
import com.dragon.agent.skills.SkillManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI 状态
 */
data class SkillsUiState(
    val skills: List<InstalledSkill> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0  // 0=已安装, 2=内置
)

/**
 * Skills Screen ViewModel
 */
@HiltViewModel
class SkillsViewModel @Inject constructor(
    private val skillManager: SkillManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())
    val uiState: StateFlow<SkillsUiState> = _uiState.asStateFlow()

    init {
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                skillManager.initialize()
                val skills = skillManager.skills.value
                _uiState.value = _uiState.value.copy(
                    skills = skills,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun toggleSkill(slug: String, enabled: Boolean) {
        viewModelScope.launch {
            skillManager.setSkillEnabled(slug, enabled)
            loadSkills()
        }
    }

    fun uninstallSkill(slug: String) {
        viewModelScope.launch {
            skillManager.uninstallSkill(slug)
            loadSkills()
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * Skills Screen - 技能管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMarket: () -> Unit = {},
    viewModel: SkillsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("技能管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToMarket) {
                        Icon(Icons.Default.Store, contentDescription = "技能市场")
                    }
                    IconButton(onClick = { viewModel.loadSkills() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("已安装 (${uiState.skills.count { it.source != SkillSource.BUILTIN }})") },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text("内置 (${uiState.skills.count { it.source == SkillSource.BUILTIN }})") },
                    icon = { Icon(Icons.Default.Build, contentDescription = null) }
                )
            }

            // 错误提示
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
            }

            // 内容
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val filteredSkills = when (uiState.selectedTab) {
                    0 -> uiState.skills.filter { it.source != SkillSource.BUILTIN }
                    1 -> uiState.skills.filter { it.source == SkillSource.BUILTIN }
                    else -> uiState.skills
                }

                if (filteredSkills.isEmpty()) {
                    EmptySkillsState(tab = uiState.selectedTab)
                } else {
                    SkillsList(
                        skills = filteredSkills,
                        onToggle = { slug, enabled -> viewModel.toggleSkill(slug, enabled) },
                        onUninstall = { viewModel.uninstallSkill(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySkillsState(tab: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (tab == 0) Icons.Default.Extension else Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (tab == 0) "暂无已安装的技能" else "暂无内置技能",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (tab == 0) "从技能市场安装更多技能" else "内置技能将显示在这里",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun SkillsList(
    skills: List<InstalledSkill>,
    onToggle: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(skills) { skill ->
            SkillCard(
                skill = skill,
                onToggle = { enabled -> onToggle(skill.manifest.slug, enabled) },
                onUninstall = { onUninstall(skill.manifest.slug) }
            )
        }
    }
}

@Composable
private fun SkillCard(
    skill: InstalledSkill,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    var showUninstallDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Emoji
                    Text(
                        text = skill.manifest.metadata.emoji.ifEmpty { "🔧" },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = skill.manifest.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v${skill.manifest.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // 开关
                Switch(
                    checked = skill.enabled,
                    onCheckedChange = onToggle
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 描述
            Text(
                text = skill.manifest.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 触发条件
            if (skill.manifest.triggers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "触发: ${skill.manifest.triggers.joinToString(", ") { trigger ->
                            when {
                                !trigger.keyword.isNullOrEmpty() -> trigger.keyword.toString()
                                !trigger.intent.isNullOrEmpty() -> trigger.intent
                                !trigger.regex.isNullOrEmpty() -> trigger.regex
                                else -> ""
                            }
                        }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 底部信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 来源
                AssistChip(
                    onClick = {},
                    label = { Text(skill.source.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingIcon = {
                        Icon(
                            imageVector = when (skill.source) {
                                SkillSource.BUILTIN -> Icons.Default.Build
                                SkillSource.LOCAL -> Icons.Default.Folder
                                SkillSource.CLAWHUB -> Icons.Default.Cloud
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                // 工具数量
                if (skill.manifest.tools.isNotEmpty()) {
                    Text(
                        text = "${skill.manifest.tools.size} 个工具",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 卸载按钮
                if (skill.source != SkillSource.BUILTIN) {
                    TextButton(onClick = { showUninstallDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("卸载")
                    }
                }
            }
        }
    }

    // 卸载确认对话框
    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text("卸载技能") },
            text = { Text("确定要卸载 ${skill.manifest.name} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onUninstall()
                    showUninstallDialog = false
                }) {
                    Text("卸载")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
