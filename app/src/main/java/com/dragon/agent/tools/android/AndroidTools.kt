package com.dragon.agent.tools.android

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.Telephony
import android.location.LocationManager
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 系统工具基类
 */
abstract class AndroidTool(
    name: String,
    description: String
) : com.dragon.agent.tools.BaseTool(name, description) {
    
    @Inject
    @ApplicationContext
    lateinit var context: Context
    
    /**
     * 检查权限
     */
    protected fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查多个权限
     */
    protected fun hasPermissions(permissions: List<String>): Boolean {
        return permissions.all { hasPermission(it) }
    }
    
    /**
     * 格式化输出
     */
    protected fun formatList(items: List<String>): String {
        return if (items.isEmpty()) "无数据" else items.joinToString("\n")
    }
}

/**
 * 通讯录工具
 */
class ContactsTool @Inject constructor() : AndroidTool(
    name = "contacts",
    description = "读取手机通讯录联系人信息，包括姓名、电话、邮箱等"
) {
    
    override fun getDefinition() = com.dragon.agent.tools.ToolDefinition(
        name = name,
        description = description,
        parameters = com.dragon.agent.tools.ToolParameters(
            properties = mapOf(
                "action" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "操作类型: list(列表) / search(搜索)",
                    enum = listOf("list", "search")
                ),
                "query" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "搜索关键词（仅 search 时使用）"
                ),
                "limit" to com.dragon.agent.tools.ToolProperty(
                    type = "number",
                    description = "返回数量限制，默认 20"
                )
            ),
            required = listOf("action")
        )
    )
    
    override suspend fun execute(args: Map<String, Any>): com.dragon.agent.tools.ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val action = args["action"]?.toString() ?: "list"
                val query = args["query"]?.toString() ?: ""
                val limit = (args["limit"]?.toString()?.toIntOrNull() ?: 20).coerceIn(1, 100)
                
                if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                    return@withContext com.dragon.agent.tools.ToolResult(
                        false, "", "需要通讯录权限，请在设置中授权"
                    )
                }
                
                val contacts = when (action) {
                    "search" -> searchContacts(query, limit)
                    else -> listContacts(limit)
                }
                
                com.dragon.agent.tools.ToolResult(true, formatContacts(contacts))
            } catch (e: Exception) {
                com.dragon.agent.tools.ToolResult(false, "", "读取通讯录失败: ${e.message}")
            }
        }
    }
    
    private fun listContacts(limit: Int): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )
        
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
                val name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: "未知"
                val phone = getPhoneNumber(id)
                val email = getEmail(id)
                
                contacts.add(Contact(name, phone, email))
                count++
            }
        }
        return contacts
    }
    
    private fun searchContacts(query: String, limit: Int): List<Contact> {
        val contacts = mutableListOf<Contact>()
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, selection, selectionArgs,
            ContactsContract.Contacts.DISPLAY_NAME + " ASC"
        )
        
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val id = it.getString(it.getColumnIndex(ContactsContract.Contacts._ID))
                val name = it.getString(it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)) ?: "未知"
                val phone = getPhoneNumber(id)
                val email = getEmail(id)
                
                contacts.add(Contact(name, phone, email))
                count++
            }
        }
        return contacts
    }
    
    private fun getPhoneNumber(contactId: String): String {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0) ?: "无"
            }
        }
        return "无"
    }
    
    private fun getEmail(contactId: String): String {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId), null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0) ?: "无"
            }
        }
        return "无"
    }
    
    private fun formatContacts(contacts: List<Contact>): String {
        if (contacts.isEmpty()) return "通讯录为空"
        
        return "📱 通讯录 (${contacts.size} 个联系人)\n\n" + contacts.joinToString("\n\n") { c ->
            "👤 ${c.name}\n📞 ${c.phone}\n📧 ${c.email}"
        }
    }
    
    data class Contact(val name: String, val phone: String, val email: String)
}

/**
 * 短信工具
 */
class SmsTool @Inject constructor() : AndroidTool(
    name = "sms",
    description = "读取手机短信记录"
) {
    
    override fun getDefinition() = com.dragon.agent.tools.ToolDefinition(
        name = name,
        description = description,
        parameters = com.dragon.agent.tools.ToolParameters(
            properties = mapOf(
                "action" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "操作类型: inbox(收件箱) / sent(已发送)",
                    enum = listOf("inbox", "sent")
                ),
                "limit" to com.dragon.agent.tools.ToolProperty(
                    type = "number",
                    description = "返回数量限制，默认 10"
                )
            ),
            required = listOf("action")
        )
    )
    
    override suspend fun execute(args: Map<String, Any>): com.dragon.agent.tools.ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val action = args["action"]?.toString() ?: "inbox"
                val limit = (args["limit"]?.toString()?.toIntOrNull() ?: 10).coerceIn(1, 50)
                
                if (!hasPermission(Manifest.permission.READ_SMS)) {
                    return@withContext com.dragon.agent.tools.ToolResult(
                        false, "", "需要短信权限，请在设置中授权"
                    )
                }
                
                val messages = getSms(action, limit)
                com.dragon.agent.tools.ToolResult(true, formatMessages(messages))
            } catch (e: Exception) {
                com.dragon.agent.tools.ToolResult(false, "", "读取短信失败: ${e.message}")
            }
        }
    }
    
    private fun getSms(type: String, limit: Int): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        
        val uri = if (type == "sent") {
            Telephony.Sms.Sent.CONTENT_URI
        } else {
            Telephony.Sms.Inbox.CONTENT_URI
        }
        
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            null, null,
            "${Telephony.Sms.DATE} DESC"
        )
        
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val address = it.getString(0) ?: "未知"
                val body = it.getString(1) ?: ""
                val date = it.getLong(2)
                
                messages.add(SmsMessage(address, body, date))
                count++
            }
        }
        return messages
    }
    
    private fun formatMessages(messages: List<SmsMessage>): String {
        if (messages.isEmpty()) return "无短信记录"
        
        return "💬 短信记录 (${messages.size} 条)\n\n" + messages.joinToString("\n\n") { m ->
            "📱 ${m.address}\n📝 ${m.body.take(100)}${if (m.body.length > 100) "..." else ""}\n🕐 ${formatDate(m.date)}"
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
    
    data class SmsMessage(val address: String, val body: String, val date: Long)
}

/**
 * 通话记录工具
 */
class CallLogTool @Inject constructor() : AndroidTool(
    name = "call_log",
    description = "读取手机通话记录"
) {
    
    override fun getDefinition() = com.dragon.agent.tools.ToolDefinition(
        name = name,
        description = description,
        parameters = com.dragon.agent.tools.ToolParameters(
            properties = mapOf(
                "type" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "通话类型: all(全部) / incoming(来电) / outgoing(去电) / missed(未接)",
                    enum = listOf("all", "incoming", "outgoing", "missed")
                ),
                "limit" to com.dragon.agent.tools.ToolProperty(
                    type = "number",
                    description = "返回数量限制，默认 10"
                )
            ),
            required = listOf("type")
        )
    )
    
    override suspend fun execute(args: Map<String, Any>): com.dragon.agent.tools.ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val type = args["type"]?.toString() ?: "all"
                val limit = (args["limit"]?.toString()?.toIntOrNull() ?: 10).coerceIn(1, 50)
                
                if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
                    return@withContext com.dragon.agent.tools.ToolResult(
                        false, "", "需要通话记录权限，请在设置中授权"
                    )
                }
                
                val calls = getCallLog(type, limit)
                com.dragon.agent.tools.ToolResult(true, formatCalls(calls))
            } catch (e: Exception) {
                com.dragon.agent.tools.ToolResult(false, "", "读取通话记录失败: ${e.message}")
            }
        }
    }
    
    private fun getCallLog(type: String, limit: Int): List<CallRecord> {
        val calls = mutableListOf<CallRecord>()
        
        val selection = when (type) {
            "incoming" -> "${android.provider.CallLog.Calls.TYPE} = ?"
            "outgoing" -> "${android.provider.CallLog.Calls.TYPE} = ?"
            "missed" -> "${android.provider.CallLog.Calls.TYPE} = ?"
            else -> null
        }
        
        val selectionArgs = when (type) {
            "incoming" -> arrayOf(android.provider.CallLog.Calls.INCOMING_TYPE.toString())
            "outgoing" -> arrayOf(android.provider.CallLog.Calls.OUTGOING_TYPE.toString())
            "missed" -> arrayOf(android.provider.CallLog.Calls.MISSED_TYPE.toString())
            else -> null
        }
        
        val cursor = context.contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.DURATION,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DATE
            ),
            selection, selectionArgs,
            "${android.provider.CallLog.Calls.DATE} DESC"
        )
        
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val number = it.getString(0) ?: "未知"
                val duration = it.getLong(1)
                val callType = it.getInt(2)
                val date = it.getLong(3)
                
                calls.add(CallRecord(
                    number = number,
                    duration = duration,
                    type = when (callType) {
                        android.provider.CallLog.Calls.INCOMING_TYPE -> "来电"
                        android.provider.CallLog.Calls.OUTGOING_TYPE -> "去电"
                        android.provider.CallLog.Calls.MISSED_TYPE -> "未接"
                        else -> "未知"
                    },
                    date = date
                ))
                count++
            }
        }
        return calls
    }
    
    private fun formatCalls(calls: List<CallRecord>): String {
        if (calls.isEmpty()) return "无通话记录"
        
        return "📞 通话记录 (${calls.size} 条)\n\n" + calls.joinToString("\n\n") { c ->
            "📱 ${c.number}\n⏱️ 时长: ${formatDuration(c.duration)}\n🏷️ ${c.type}\n🕐 ${formatDate(c.date)}"
        }
    }
    
    private fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) "${minutes}分${secs}秒" else "${secs}秒"
    }
    
    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
    
    data class CallRecord(val number: String, val duration: Long, val type: String, val date: Long)
}

/**
 * 应用列表工具
 */
class AppsTool @Inject constructor() : AndroidTool(
    name = "apps",
    description = "获取手机已安装的应用列表"
) {
    
    override fun getDefinition() = com.dragon.agent.tools.ToolDefinition(
        name = name,
        description = description,
        parameters = com.dragon.agent.tools.ToolParameters(
            properties = mapOf(
                "filter" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "筛选类型: all(全部) / user(用户应用) / system(系统应用)",
                    enum = listOf("all", "user", "system")
                ),
                "query" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "搜索应用名称"
                ),
                "limit" to com.dragon.agent.tools.ToolProperty(
                    type = "number",
                    description = "返回数量限制，默认 20"
                )
            ),
            required = listOf("filter")
        )
    )
    
    override suspend fun execute(args: Map<String, Any>): com.dragon.agent.tools.ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val filter = args["filter"]?.toString() ?: "all"
                val query = args["query"]?.toString() ?: ""
                val limit = (args["limit"]?.toString()?.toIntOrNull() ?: 20).coerceIn(1, 100)
                
                val apps = getApps(filter, query, limit)
                com.dragon.agent.tools.ToolResult(true, formatApps(apps))
            } catch (e: Exception) {
                com.dragon.agent.tools.ToolResult(false, "", "获取应用列表失败: ${e.message}")
            }
        }
    }
    
    private fun getApps(filter: String, query: String, limit: Int): List<AppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppInfo>()
        
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        for (info in resolveInfos) {
            val packageName = info.activityInfo.packageName
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val isUserApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
            
            // 过滤
            if (filter == "user" && !isUserApp) continue
            if (filter == "system" && isUserApp) continue
            
            // 搜索
            val appName = pm.getApplicationLabel(appInfo).toString()
            if (query.isNotEmpty() && !appName.contains(query, ignoreCase = true)) continue
            
            apps.add(AppInfo(
                name = appName,
                packageName = packageName,
                version = pm.getPackageInfo(packageName, 0).versionName ?: "未知",
                isUserApp = isUserApp
            ))
            
            if (apps.size >= limit) break
        }
        
        return apps
    }
    
    private fun formatApps(apps: List<AppInfo>): String {
        if (apps.isEmpty()) return "未找到应用"
        
        val userApps = apps.filter { it.isUserApp }
        val systemApps = apps.filter { !it.isUserApp }
        
        return buildString {
            append("📦 应用列表 (共 ${apps.size} 个)\n\n")
            
            if (userApps.isNotEmpty()) {
                append("👤 用户应用 (${userApps.size}):\n")
                userApps.take(10).forEach { app ->
                    append("  • ${app.name} (${app.version})\n")
                }
                if (userApps.size > 10) append("  ... 等${userApps.size}个\n")
                append("\n")
            }
            
            if (systemApps.isNotEmpty()) {
                append("⚙️ 系统应用 (${systemApps.size}):\n")
                systemApps.take(5).forEach { app ->
                    append("  • ${app.name}\n")
                }
                if (systemApps.size > 5) append("  ... 等${systemApps.size}个\n")
            }
        }
    }
    
    data class AppInfo(val name: String, val packageName: String, val version: String, val isUserApp: Boolean)
}

/**
 * 设备信息工具
 */
class DeviceInfoTool @Inject constructor() : AndroidTool(
    name = "device_info",
    description = "获取手机设备信息，如型号、系统版本、电池状态等"
) {
    
    override fun getDefinition() = com.dragon.agent.tools.ToolDefinition(
        name = name,
        description = description,
        parameters = com.dragon.agent.tools.ToolParameters()
    )
    
    override suspend fun execute(args: Map<String, Any>): com.dragon.agent.tools.ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val info = getDeviceInfo()
                com.dragon.agent.tools.ToolResult(true, info)
            } catch (e: Exception) {
                com.dragon.agent.tools.ToolResult(false, "", "获取设备信息失败: ${e.message}")
            }
        }
    }
    
    private fun getDeviceInfo(): String {
        val pm = context.packageManager
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(java.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.BATTERY_SCALE else "level", -1) ?: -1
        val scale = batteryIntent?.getIntExtra("scale", -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        
        return buildString {
            append("📱 设备信息\n\n")
            append("📋 型号: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("🖥️ 设备: ${Build.DEVICE}\n")
            append("🔧 系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("🆔 Build: ${Build.ID}\n")
            append("🔋 电池: ${if (batteryPct >= 0) "$batteryPct%" else "未知"}\n")
            append("💾 存储: ${getStorageInfo()}\n")
            append("📱 屏幕: ${getScreenInfo()}\n")
            append("🌐 网络: ${getNetworkInfo()}\n")
        }
    }
    
    private fun getStorageInfo(): String {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val total = stat.blockSizeLong * stat.blockCountLong
        val available = stat.blockSizeLong * stat.blockAvailableLong
        val used = total - available
        
        fun formatSize(bytes: Long): String {
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            return String.format("%.1f GB", gb)
        }
        
        return "已用 ${formatSize(used)} / 共 ${formatSize(total)}"
    }
    
    private fun getScreenInfo(): String {
        val dm = context.resources.displayMetrics
        return "${dm.widthPixels}x${dm.heightPixels} (${dm.densityDpi}dpi)"
    }
    
    private fun getNetworkInfo(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val network = cm?.activeNetwork
        val capabilities = cm?.getNetworkCapabilities(network)
        
        return when {
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "移动数据"
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "以太网"
            else -> "无网络"
        }
    }
    
    private fun registerReceiver(null2: Any?, filter: Intent): android.content.Intent? {
        return context.registerReceiver(null, filter)
    }
}

/**
 * 设置工具 - 打开系统设置页面
 */
class SettingsTool @Inject constructor() : AndroidTool(
    name = "open_settings",
    description = "打开手机系统设置页面"
) {
    
    override fun getDefinition() = com.dragon.agent.tools.ToolDefinition(
        name = name,
        description = description,
        parameters = com.dragon.agent.tools.ToolParameters(
            properties = mapOf(
                "page" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "设置页面: wifi / bluetooth / location / display / sound / battery / apps / security",
                    enum = listOf("wifi", "bluetooth", "location", "display", "sound", "battery", "apps", "security", "settings")
                )
            ),
            required = listOf("page")
        )
    )
    
    override suspend fun execute(args: Map<String, Any>): com.dragon.agent.tools.ToolResult {
        return withContext(Dispatchers.Main) {
            try {
                val page = args["page"]?.toString() ?: "settings"
                
                val intent = when (page) {
                    "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
                    "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
                    "sound" -> Intent(Settings.ACTION_SOUND_SETTINGS)
                    "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    "apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    "security" -> Intent(Settings.ACTION_SECURITY_SETTINGS)
                    else -> Intent(Settings.ACTION_SETTINGS)
                }
                
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                
                com.dragon.agent.tools.ToolResult(true, "已打开设置页面: $page")
            } catch (e: Exception) {
                com.dragon.agent.tools.ToolResult(false, "", "打开设置失败: ${e.message}")
            }
        }
    }
}

/**
 * 常用 Intent 工具
 */
class IntentTool @Inject constructor() : AndroidTool(
    name = "intent",
    description = "启动常见应用或执行常见操作"
) {
    
    private val knownApps = mapOf(
        "camera" to Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        "gallery" to Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI),
        "dialer" to Intent(Intent.ACTION_DIAL),
        "contacts" to Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people")),
        "sms" to Intent(Intent.ACTION_VIEW, Uri.parse("sms:"))
    )
    
    override fun getDefinition() = com.dragon.agent.tools.ToolDefinition(
        name = name,
        description = description,
        parameters = com.dragon.agent.tools.ToolParameters(
            properties = mapOf(
                "action" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "操作: camera / gallery / dialer / contacts / sms / phone / browser / maps",
                    enum = knownApps.keys.toList() + listOf("browser", "maps", "phone")
                ),
                "param" to com.dragon.agent.tools.ToolProperty(
                    type = "string",
                    description = "额外参数（如电话号码、URL等）"
                )
            ),
            required = listOf("action")
        )
    )
    
    override suspend fun execute(args: Map<String, Any>): com.dragon.agent.tools.ToolResult {
        return withContext(Dispatchers.Main) {
            try {
                val action = args["action"]?.toString() ?: return@withContext com.dragon.agent.tools.ToolResult(false, "", "缺少 action 参数")
                val param = args["param"]?.toString()
                
                val intent = when (action) {
                    "browser" -> Intent(Intent.ACTION_VIEW, Uri.parse(param ?: "https://www.baidu.com"))
                    "phone" -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${param ?: ""}"))
                    "maps" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${param ?: ""}"))
                    else -> knownApps[action]
                } ?: return@withContext com.dragon.agent.tools.ToolResult(false, "", "未知操作: $action")
                
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    com.dragon.agent.tools.ToolResult(true, "已启动: $action")
                } else {
                    com.dragon.agent.tools.ToolResult(false, "", "无法启动: $action（应用不存在）")
                }
            } catch (e: Exception) {
                com.dragon.agent.tools.ToolResult(false, "", "启动失败: ${e.message}")
            }
        }
    }
}
