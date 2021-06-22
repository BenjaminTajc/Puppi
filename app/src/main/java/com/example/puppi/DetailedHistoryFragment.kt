package com.example.puppi

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class DetailedHistoryFragment : Fragment() {

    var recyclerView: RecyclerView? = null
    var recyclerViewAdapter: DetailViewAdapter? = null
    var rowsArrayList: ArrayList<Event?>? = null

    var isLoading = false

    lateinit var dbService: PuppiDBService

    var dbServiceBound = false

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_detailed_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val srvIntent = Intent(activity, PuppiDBService::class.java)
        recyclerView = getView()?.findViewById(R.id.recyclerView)
        initAdapter()

        //initScrollListener()
        activity?.bindService(srvIntent, mServiceConnection, Service.BIND_AUTO_CREATE)
    }

    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            Log.i("dbBinderStatus", "DB service unbound from Broad History fragment")
            dbServiceBound = false
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val myBinder: PuppiDBService.LocalBinder = service as PuppiDBService.LocalBinder
            dbService = myBinder.service
            Log.i("dbBinderStatus", "DB service bound to Broad History fragment")
            dbServiceBound = true
            rowsArrayList = populateData()
            recyclerViewAdapter?.updateItemList(rowsArrayList)
        }
    }

    suspend fun getData(): List<Event> {
        return dbService.db.eventDao().findTenDays(/*OffsetDateTime.now()*/)
    }

    private fun populateData(): ArrayList<Event?>? {
        var array: ArrayList<Event?>? = null
        runBlocking {
            val job = async { getData() }
            runBlocking {
                array = ArrayList(job.await().reversed())
            }
        }
        return array
    }

    private fun initAdapter() {
        recyclerViewAdapter = DetailViewAdapter(rowsArrayList)
        recyclerView!!.adapter = recyclerViewAdapter
    }
}




class DetailViewAdapter(itemList: ArrayList<Event?>?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val VIEW_TYPE_ITEM = 0
    private val VIEW_TYPE_LOADING = 1
    private val VIEW_TYPE_EMPTY = 2

    private var mItemList: ArrayList<Event?>? = itemList

    fun updateItemList(itemList: ArrayList<Event?>?) {
        mItemList = itemList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM -> {
                val view: View =
                    LayoutInflater.from(parent.context).inflate(R.layout.item_row_detail, parent, false)
                ItemViewHolder(view)
            }
            VIEW_TYPE_LOADING -> {
                val view: View =
                    LayoutInflater.from(parent.context).inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view)
            }
            else -> {
                val view: View =
                    LayoutInflater.from(parent.context).inflate(R.layout.item_empty, parent, false)
                EmptyViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
        if (viewHolder is ItemViewHolder) {
            populateItemRows(viewHolder, position)
        } else if (viewHolder is LoadingViewHolder) {
            showLoadingView(viewHolder, position)
        } else if (viewHolder is EmptyViewHolder) {
            viewHolder.tvItem.text = Resources.getSystem().getString(R.string.history_empty)
        }
    }

    override fun getItemCount(): Int {
        return if (mItemList == null) 0 else mItemList!!.size
    }

    override fun getItemViewType(position: Int): Int {
        return if(mItemList?.get(position) == null){
            VIEW_TYPE_LOADING
        } else {
            if(mItemList?.get(position) is Event){
                VIEW_TYPE_ITEM
            } else {
                VIEW_TYPE_EMPTY
            }
        }

    }

    private class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var event_date: TextView = itemView.findViewById(R.id.event_date)
        var event_type: TextView = itemView.findViewById(R.id.event_type)
    }



    private class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

    private class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvItem: TextView = itemView.findViewById(R.id.date)
    }

    private fun showLoadingView(viewHolder: LoadingViewHolder, position: Int) {
        //ProgressBar would be displayed
    }

    private fun populateItemRows(viewHolder: ItemViewHolder, position: Int) {
        val item: Event? = mItemList?.get(position)
        if (item != null) {
            viewHolder.event_date.text = "${item.date?.dayOfWeek?.name}, ${item.date?.hour}:${item.date?.minute}"
            var typeText = ""
            var colour = 0
            when (item.eventType) {
                1 -> {
                    typeText = "BARK"
                    colour = Color.parseColor("#E60808")
                }
                2 -> {
                    typeText = "GROWL"
                    colour = Color.parseColor("#20B806")
                }
                3 -> {
                    typeText = "WHINE"
                    colour = Color.parseColor("#008EED")
                }
            }
            viewHolder.event_type.text = typeText
            viewHolder.event_type.setTextColor(colour)
        }
    }
}