package com.dragon.agent.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dragon.agent.DragonAgentApp
import com.dragon.agent.R
import com.dragon.agent.data.local.SettingsManager
import com.dragon.agent.gateway.Gateway
import com.dragon.agent.gateway.GatewayConfig
import com.dragon.agent.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Gateway 前台服务
 * 用于在后台持续运行 Gateway，接收飞书等渠道的消息
 */
@AndroidEntryPoint
class GatewayService : Service() {

    @Inject
    lateinit var gateway: Gateway

    @Inject
    lateinit var settingsManager: SettingsManager

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val notificationId = 1001
    private val channelId = "dragon_agent_gateway"

    companion object {
        const val ACTION_START = "com.dragon.agent.action.START_GATEWAY"
        const val ACTION_STOP = "com.dragon.agent.action.STOP_GATEWAY"

        /**
         * 启动 Gateway 服务
         */
        fun start(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止 Gateway 服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, GatewayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(notificationId, createNotification("正在启动..."))
                startGateway()
            }
            ACTION_STOP -> {
                stopGateway()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY  // 进程被杀死后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopGateway()
        serviceScope.cancel()
    }

    /**
     * 启动 Gateway
     */
    private fun startGateway() {
        serviceScope.launch {
            try {
                // 从设置获取配置
                val settings = settingsManager.settings.first()

                val config = GatewayConfig(
                    enabled = true,
                    port = 8080,  // 默认端口
                    authToken = settings.apiKey,
                    allowExternalAccess = false
                )

                // 启动 Gateway
                gateway.start(config)

                // 更新通知
                val status = gateway.getStatus()
                updateNotification("运行中 - ${status.activeChannel}")
            } catch (e: Exception) {
                updateNotification("启动失败: ${e.message}")
            }
        }
    }

    /**
     * 停止 Gateway
     */
    private fun stopGateway() {
        serviceScope.launch {
            gateway.stop()
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "DragonAgent 网关",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台运行，接收消息"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GatewayService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("DragonAgent 🦞")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 更新通知
     */
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }
}
