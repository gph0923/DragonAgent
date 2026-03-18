package com.dragon.agent.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 自启动接收器
 * 设备重启后自动启动 Gateway 服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 延迟启动，确保系统就绪
            context.sendBroadcast(Intent().apply {
                action = "com.dragon.agent.action.START_ON_BOOT"
                putExtra("delayed", true)
            })

            // 使用 Handler 延迟 5 秒启动
            val delayedIntent = android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                GatewayService.start(context)
            }, 5000)
        }
    }
}
