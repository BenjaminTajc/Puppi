package com.example.puppi

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.threeten.bp.OffsetDateTime
import java.io.Serializable


class BroadHistoryFragment : Fragment() {

    var recyclerView: RecyclerView? = null
    var recyclerViewAdapter: RecyclerViewAdapter? = null
    var rowsArrayList: ArrayList<DaySum?>? = null

    var isLoading = false

    lateinit var dbService: PuppiDBService

    var dbServiceBound = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(R.layout.fragment_broad_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val srvIntent = Intent(activity, PuppiDBService::class.java)
        recyclerView = getView()?.findViewById(R.id.recyclerView)
        initAdapter()

        initScrollListener()
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

    private fun populateData(): ArrayList<DaySum?>? {
        var array: ArrayList<DaySum?>? = null
        runBlocking {
            val job = async { getData() }
            runBlocking {
                array = adaptData(job.await())
            }
        }
        return array
        //Log.i("dbService", "List length: ${recyclerViewAdapter?.itemCount}")
        //return array
        //initScrollListener()
    }

    private fun adaptData(results: List<Event>): ArrayList<DaySum?>? {
        var array: ArrayList<DaySum?>? = null
        isLoading = true
        if(results.isNotEmpty()){
            Log.i("dbService", "Finished query: results found")
            var currentDate = results[0].date
            var temp = DaySum(results[0].date, 0, 0, 0)
            var i = 0
            for(res in results){
                //Log.i("dbService", "Event type from DB: ${res.eventType}")
                if(res.date!!.dayOfYear != currentDate!!.dayOfYear){
                    currentDate = res.date
                    if(array == null){
                        array = arrayListOf(temp)
                    } else {
                        array.add(temp)
                    }
                    temp = DaySum(currentDate, 0, 0, 0)
                    i++
                }
                with(temp){
                    when (res.eventType) {
                        1 -> {
                            this.r++
                        }
                        2 -> {
                            this.g++
                        }
                        3 -> {
                            this.b++
                        }
                        else -> 0
                    }
                }
            }
            return array
        } else {
            Log.i("dbService", "Finished ten days query: no results")
            return null
        }
    }

    private fun initAdapter() {
        recyclerViewAdapter = RecyclerViewAdapter(rowsArrayList)
        recyclerView!!.adapter = recyclerViewAdapter
    }

    private fun initScrollListener() {
        recyclerView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val linearLayoutManager = recyclerView.layoutManager as LinearLayoutManager?
                if (!isLoading) {
                    if (linearLayoutManager != null && linearLayoutManager.findLastCompletelyVisibleItemPosition() == rowsArrayList!!.size - 1) {
                        loadMore()
                        isLoading = true
                    }
                }
            }
        })
    }

    private fun loadMore() {
        if(rowsArrayList != null){
            rowsArrayList!!.add(null)
            recyclerViewAdapter!!.notifyItemInserted(rowsArrayList!!.size - 1)
            val handler = Handler()
            handler.postDelayed(Runnable {
                rowsArrayList!!.removeAt(rowsArrayList!!.size - 1)
                val scrollPosition = rowsArrayList!!.size
                recyclerViewAdapter!!.notifyItemRemoved(scrollPosition)
                var currentSize = scrollPosition
                val nextLimit = currentSize + 10
                while (currentSize - 1 < nextLimit) {
                    //rowsArrayList.add("Item $currentSize")
                    currentSize++
                }
                recyclerViewAdapter!!.notifyDataSetChanged()
                isLoading = false
            }, 2000)
        }

    }
}


class DaySum(var date: OffsetDateTime?, var r: Int, var g: Int, var b: Int)


class RecyclerViewAdapter(itemList: ArrayList<DaySum?>?) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val VIEW_TYPE_ITEM = 0
    private val VIEW_TYPE_LOADING = 1
    private val VIEW_TYPE_EMPTY = 2

    private var mItemList: ArrayList<DaySum?>? = itemList

    fun updateItemList(itemList: ArrayList<DaySum?>?) {
        mItemList = itemList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM -> {
                val view: View =
                    LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
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
            if(mItemList?.get(position) is DaySum){
                VIEW_TYPE_ITEM
            } else {
                VIEW_TYPE_EMPTY
            }
        }

    }

    private class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvItem: TextView = itemView.findViewById(R.id.tvItem)
    }



    private class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

    private class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var tvItem: TextView = itemView.findViewById(R.id.tvItem)
    }

    private fun showLoadingView(viewHolder: LoadingViewHolder, position: Int) {
        //ProgressBar would be displayed
    }

    private fun populateItemRows(viewHolder: ItemViewHolder, position: Int) {
        val item: DaySum = mItemList?.get(position) ?: DaySum(OffsetDateTime.now(), 0, 0, 0)
        viewHolder.tvItem.text = "${item.date?.dayOfWeek?.name}, ${item.date?.dayOfMonth}.${item.date?.monthValue}"
    }
}

