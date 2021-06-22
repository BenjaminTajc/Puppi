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

    private var exampleData = arrayOf(Event(1, OffsetDateTime.parse("2021-06-20T10:15:30+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(2, OffsetDateTime.parse("2021-06-20T10:15:32+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(3, OffsetDateTime.parse("2021-06-20T10:16:36+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(1, OffsetDateTime.parse("2021-06-20T10:20:50+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(2, OffsetDateTime.parse("2021-06-20T10:23:55+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(2, OffsetDateTime.parse("2021-06-20T11:16:20+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(1, OffsetDateTime.parse("2021-06-20T11:40:40+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(1, OffsetDateTime.parse("2021-06-20T12:30:20+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(1, OffsetDateTime.parse("2021-06-19T10:15:30+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(2, OffsetDateTime.parse("2021-06-19T11:20:32+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(3, OffsetDateTime.parse("2021-06-19T12:15:36+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(1, OffsetDateTime.parse("2021-06-19T13:15:50+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(2, OffsetDateTime.parse("2021-06-19T13:40:55+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(2, OffsetDateTime.parse("2021-06-19T14:15:20+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(1, OffsetDateTime.parse("2021-06-19T15:16:40+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),
        Event(1, OffsetDateTime.parse("2021-06-19T15:50:20+02:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME)),)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("dbService", "DB service started")
        db = EventDatabase(this)
        AndroidThreeTen.init(this)
        prePopulate()
        return super.onStartCommand(intent, flags, startId)
    }

    lateinit var db: EventDatabase


    inner class LocalBinder : Binder() {
        val service: PuppiDBService
            get() = this@PuppiDBService
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        Log.i("dbBinderStatus", "Service bound")
        return mBinder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveEvent(type: Int) = runBlocking {
        launch {
            Log.i("dbService", "Saving event")
            db.eventDao().insertAll(Event(type))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun prePopulate() = runBlocking {
        launch {
            Log.i("dbService", "Saving event")
            for(event in exampleData){
                db.eventDao().insertAll(event)
            }
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
    constructor(event_type: Int, date: OffsetDateTime): this(0, event_type, date)
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

    @Query("SELECT * FROM events ORDER BY datetime(date)")
    suspend fun findTenDays(/*targetDate: OffsetDateTime*/): List<Event>

    @Insert
    suspend fun insertAll(vararg event: Event)

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
