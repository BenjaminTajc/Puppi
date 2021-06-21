package com.example.puppi

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.room.*
import com.jakewharton.threetenabp.AndroidThreeTen
import kotlinx.coroutines.*
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.*


class PuppiDBService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("dbService", "DB service started")
        db = EventDatabase(this)
        AndroidThreeTen.init(this)
        return super.onStartCommand(intent, flags, startId)
    }

    private lateinit var db: EventDatabase


    inner class LocalBinder : Binder() {
        val service: PuppiDBService
            get() = this@PuppiDBService
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveEvent(type: Int) = runBlocking {
        launch {
            db.eventDao().insertAll(Event(type))
        }
    }
}



// Database entity definitions:

@Entity(tableName = "events")

data class Event(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "event_type") var eventType: Int = 0,
    @ColumnInfo(name = "date") var date: OffsetDateTime? = null
) {
   @RequiresApi(Build.VERSION_CODES.O)
   constructor(event_type: Int): this(0, event_type, OffsetDateTime.now())
}

class Converters{
    @RequiresApi(Build.VERSION_CODES.O)
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    fun toOffsetDateTime(value: String?): OffsetDateTime? {
        return value?.let{
            return formatter.parse(value, OffsetDateTime::from)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    fun fromOffsetDateTime(date: OffsetDateTime?): String? {
        return date?.format(formatter)
    }
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun getAll(): List<Event>

    @Query("SELECT * FROM events WHERE event_type LIKE :value")
    fun findByType(value: Int): List<Event>

    @Insert
    fun insertAll(vararg event: Event)

    @Delete
    fun delete(event: Event)
}

@Database(entities = [Event::class], version = 1)
@TypeConverters(Converters::class)
abstract class EventDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile private var instance: EventDatabase? = null
        private val LOCK = Any()

        operator fun invoke(context: Context)= instance ?: synchronized(LOCK){
            instance ?: buildDatabase(context).also { instance = it}
        }

        private fun buildDatabase(context: Context) = Room.databaseBuilder(
            context,
            EventDatabase::class.java, "events.db"
        )
                .build()
    }
}
