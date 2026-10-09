package com.datafloat.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** One row per local calendar day (yyyy-MM-dd) with total mobile bytes. Keeps history beyond Android's own retention. */
@Entity(tableName = "daily_usage")
data class DailyUsage(@PrimaryKey val date: String, val bytes: Long)

@Dao
abstract class DailyUsageDao {
    @Query("SELECT * FROM daily_usage ORDER BY date DESC LIMIT :n")
    abstract fun recent(n: Int): Flow<List<DailyUsage>>

    @Query("SELECT bytes FROM daily_usage WHERE date = :d")
    abstract suspend fun bytesFor(d: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(row: DailyUsage)

    /** Daily totals only ever grow, so never overwrite with a smaller number. */
    @Transaction
    open suspend fun upsertMax(date: String, bytes: Long) {
        val old = bytesFor(date) ?: 0L
        if (bytes > old) upsert(DailyUsage(date, bytes))
    }
}

@Database(entities = [DailyUsage::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): DailyUsageDao

    companion object {
        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "datafloat.db").build()
    }
}
