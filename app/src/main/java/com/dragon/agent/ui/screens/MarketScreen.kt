package com.dragon.agent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.dragon.agent.skills.market.LocalSkill
import com.dragon.agent.skills.market.MarketSkill
import com.dragon.agent.skills.market.SkillCategory
import com.dragon.agent.skills.market.SkillMarketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 技能市场 UI 状态
 */
data class MarketUiState(
    val featuredSkills: List<MarketSkill> = emptyList(),
    val searchResults: List<MarketSkill> = emptyList(),
    val categories: List<SkillCategory> = emptyList(),
    val localSkills: List<LocalSkill> = emptyList(),
    val selectedTab: Int = 0,  // 0=市场, 1=本地, 2=已安装
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val installingSkill: String? = null,
    val installSuccess: String? = null
)

/**
 * 技能市场 ViewModel
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val marketService: SkillMarketService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // 加载分类
            marketService.getCategories().onSuccess { categories ->
                _uiState.value = _uiState.value.copy(categories = categories)
            }
            
            // 加载推荐技能
            marketService.getFeaturedSkills().onSuccess { skills ->
                _uiState.value = _uiState.value.copy(featuredSkills = skills)
            }
            
            // 加载本地技能
            val localSkills = marketService.getInstalledSkills()
            _uiState.value = _uiState.value.copy(
                localSkills = localSkills,
                isLoading = false
            )
        }
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            marketService.searchSkills(query).fold(
                onSuccess = { skills ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = skills,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message,
                        isLoading = false
                    )
                }
            )
        }
    }

    fun installFromMarket(skill: MarketSkill) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                installingSkill = skill.slug,
                error = null,
                installSuccess = null
            )
            
            marketService.installFromClawHub(skill.slug).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        installingSkill = null,
                        installSuccess = "安装成功: ${skill.name}"
                    )
                    // 刷新本地技能
                    val localSkills = marketService.getInstalledSkills()
                    _uiState.value = _uiState.value.copy(localSkills = localSkills)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        installingSkill = null,
                        error = "安装失败: ${error.message}"
                    )
                }
            )
        }
    }

    fun installFromLocal(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            marketService.installFromDirectory(path).fold(
                onSuccess = {
                    val localSkills = marketService.getInstalledSkills()
                    _uiState.value = _uiState.value.copy(
                        localSkills = localSkills,
                        isLoading = false,
                        installSuccess = "本地安装成功"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "安装失败: ${error.message}"
                    )
                }
            )
        }
    }

    fun uninstall(slug: String) {
        viewModelScope.launch {
            marketService.uninstallSkill(slug).fold(
                onSuccess = {
                    val localSkills = marketService.getInstalledSkills()
                    _uiState.value = _uiState.value.copy(
                        localSkills = localSkills,
                        installSuccess = "已卸载"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "卸载失败: ${error.message}"
                    )
                }
            )
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, installSuccess = null)
    }
}

/**
 * 技能市场 Screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("技能市场") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // 搜索栏
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索技能...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.search("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true
            )

            // Tab
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("推荐") },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text("本地导入") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    text = { Text("已安装 (${uiState.localSkills.size})") },
                    icon = { Icon(Icons.Default.InstallDesktop, contentDescription = null) }
                )
            }

            // 消息提示
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
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        IconButton(onClick = { viewModel.clearMessages() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
            }

            uiState.installSuccess?.let { success ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(success, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        IconButton(onClick = { viewModel.clearMessages() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
            }

            // 内容
            when (uiState.selectedTab) {
                0 -> MarketContent(
                    skills = if (uiState.searchQuery.isNotEmpty()) 
                        uiState.searchResults else uiState.featuredSkills,
                    isLoading = uiState.isLoading,
                    installingSkill = uiState.installingSkill,
                    onInstall = { viewModel.installFromMarket(it) }
                )
                1 -> LocalImportContent(
                    onImport = { viewModel.installFromLocal(it) }
                )
                2 -> InstalledContent(
                    skills = uiState.localSkills,
                    onUninstall = { viewModel.uninstall(it) }
                )
            }
        }
    }
}

@Composable
private fun MarketContent(
    skills: List<MarketSkill>,
    isLoading: Boolean,
    installingSkill: String?,
    onInstall: (MarketSkill) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (skills.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("暂无技能")
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(skills) { skill ->
                MarketSkillCard(
                    skill = skill,
                    isInstalling = installingSkill == skill.slug,
                    onInstall = { onInstall(skill) }
                )
            }
        }
    }
}

@Composable
private fun MarketSkillCard(
    skill: MarketSkill,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Text(skill.emoji.ifEmpty { "📦" }, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(skill.name, fontWeight = FontWeight.Bold)
                        Text("v${skill.version}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                if (isInstalling) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Button(onClick = onInstall) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("安装")
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                skill.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (skill.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    skill.tags.take(3).forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row {
                Text("作者: ${skill.author}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(16.dp))
                Text("⭐ ${skill.stars}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LocalImportContent(
    onImport: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("本地技能导入")
            Spacer(Modifier.height(8.dp))
            Text(
                "将包含 SKILL.md 的文件夹路径填入\n或从文件管理器选择",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("技能目录路径") },
                placeholder = { Text("/sdcard/skills/my-skill") },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { /* TODO: 文件选择器 */ }) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("选择文件夹")
            }
        }
    }
}

@Composable
private fun InstalledContent(
    skills: List<LocalSkill>,
    onUninstall: (String) -> Unit
) {
    if (skills.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.InstallDesktop, contentDescription = null, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text("暂无已安装的技能")
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(skills) { skill ->
                InstalledSkillCard(
                    skill = skill,
                    onUninstall = { onUninstall(skill.slug) }
                )
            }
        }
    }
}

@Composable
private fun InstalledSkillCard(
    skill: LocalSkill,
    onUninstall: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.name, fontWeight = FontWeight.Bold)
                Text("v${skill.version}", style = MaterialTheme.typography.bodySmall)
                Text(
                    skill.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TextButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text("卸载")
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("卸载技能") },
            text = { Text("确定要卸载 ${skill.name} 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onUninstall()
                    showDialog = false
                }) {
                    Text("卸载")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
