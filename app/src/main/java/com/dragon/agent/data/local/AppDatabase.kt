package com.dragon.agent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dragon.agent.data.local.dao.ConversationDao
import com.dragon.agent.data.local.dao.MessageDao
import com.dragon.agent.data.local.entity.ConversationEntity
import com.dragon.agent.data.local.entity.MessageEntity

/**
 * DragonAgent 数据库
 */
@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        const val DATABASE_NAME = "dragon_agent_db"
    }
}
