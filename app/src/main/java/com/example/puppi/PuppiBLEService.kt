package com.example.puppi

import android.Manifest
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.timerTask


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class PuppiBLEService : Service() {

    private val BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val BATTERY_CHAR = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    var isConnected: Boolean = false

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    var isScanning = false

    private val scanResults = mutableListOf<ScanResult>()

    var bluetoothGatt: BluetoothGatt? = null

    private lateinit var dbService: PuppiDBService

    var dbServiceBound: Boolean = false

    var activityBound: Boolean = false

    private var liveCallback: LiveCallBack? = null

    private var notifWorking: Boolean = false

    interface LiveCallBack {
        fun getResult(result: Int)
    }

    fun registerCallBack(callBack: LiveCallBack?) {
        liveCallback = callBack
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    val filter: ScanFilter = ScanFilter.Builder().setDeviceName(
        "PuppiListenerInterface"
    ).build()

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun startBleScan() {
        Log.i("BleServiceScan", "Ble scan called")
        if (!isLocationPermissionGranted) {
            Log.i(
                "BleServiceScan",
                "Ble scan start unsuccessful, isLocationPermissionGranted: $isLocationPermissionGranted"
            )
            return
        }
        else {
            Log.i("BleServiceScan", "Ble scan started")
            scanResults.clear()
            bleScanner.startScan(List(1) { filter }, scanSettings, scanCallback)
            isScanning = true
        }
    }

    fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
        Log.i("BleServiceScan", "Ble scan stopped")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result.device) {
                Log.i(
                    "ScanCallback",
                    "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address"
                )
                scanResults.add(result)
                checkAndConnect(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }

    private fun checkAndConnect(result: ScanResult){
        if(result.device.name == "PuppiListenerInterface"){
            stopBleScan()
            result.device.connectGatt(this, false, gattCallback)
        }
    }

    fun disconnect(){
        if(this::job.isInitialized){
            if(job.isActive){
                job.cancel()
            }
        }
        bluetoothGatt?.disconnect()
        isConnected = false
    }

    private var valPrevious: Int? = 0

    private val gattCallback = object: BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val deviceAddress = gatt?.device?.address

            if(status == BluetoothGatt.GATT_SUCCESS){
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    isConnected = true
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt?.close()
                }
            } else {
                Log.w(
                    "BluetoothGattCallback",
                    "Error $status encountered for $deviceAddress! Disconnecting..."
                )
                gatt?.close()
            }


        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(
                    "BluetoothGattCallback",
                    "Discovered ${services.size} services for ${device.address}"
                )
                printGattTable()

            }
            setNotify(gatt)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if(!notifWorking){
                notifWorking = true
            }
            with(characteristic) {
                processResult(this.value.first().toInt())
            }
        }

        @RequiresApi(Build.VERSION_CODES.O)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if(status == BluetoothGatt.GATT_SUCCESS) {
                with(characteristic){
                    val result = this?.value?.first()?.toInt()
                    Log.i("BLEService", "Characteristic read value: $result, prev: $valPrevious, raw: ${this?.value?.toHexString()}")
                    if(valPrevious != result){
                        processResult(result)
                    }
                    valPrevious = result
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i("BLEService", "Descriptor written successfully. Waiting for callback")
            } else {
                Log.e("BLEService", "Writing to descriptor failed")
            }
            checkNotif()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun processResult(result: Int?) {
        Log.i("BluetoothGattCallback", "Read characteristic value: $result")
        if(activityBound){
            if (result != null) {
                liveCallback?.getResult(result)
            }
        }
        if(dbServiceBound){
            if(result != 0){
                if (result != null) {
                    dbService.saveEvent(result)
                }
            }
        }
    }

    private lateinit var job: Job

    private fun checkNotif() {
        Timer().schedule(timerTask {
            Log.i("BLEService", "Checking notif: $notifWorking")
            if(!notifWorking) {
                Log.i("BLEService", "Notif not working")
                val btLevelChar = bluetoothGatt?.getService(BATTERY_SERVICE)
                    ?.getCharacteristic(BATTERY_CHAR)
                job = GlobalScope.launch(Dispatchers.Default) {
                    while (job.isActive){
                        //Log.i("BLEService", "Loop initiated")
                        Timer().schedule(timerTask {
                            bluetoothGatt?.readCharacteristic(btLevelChar)
                        }, 300)
                    }
                }
            }
        }, 2000)
    }

    private fun ByteArray.toHexString(): String =
            joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(
                "printGattTable",
                "No service and characteristic available, call discoverServices() first?"
            )
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i(
                "printGattTable",
                "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }

    private fun setNotify(gatt: BluetoothGatt) {
        val characteristic: BluetoothGattCharacteristic =
            gatt.
            getService(BATTERY_SERVICE)!!.
            getCharacteristic(BATTERY_CHAR)
        if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == true) {
            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if(bluetoothGatt?.writeDescriptor(descriptor) == true){
                Log.i("BLEService", "Writing to descriptor")
            } else {
                Log.e("BLEService", "Characteristic notifications enabling failed")
            }
        } else {
            Log.e("BLEService", "Characteristic notifications enabling failed")
        }
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("BLEService", "BLE service started")
        val srvIntent = Intent(this, PuppiDBService::class.java)
        bindService(srvIntent, mServiceConnection, BIND_AUTO_CREATE)
        return super.onStartCommand(intent, flags, startId)
    }

    inner class LocalBinder : Binder() {
        val service: PuppiBLEService
            get() = this@PuppiBLEService
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        activityBound = true
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        activityBound = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isScanning){
            stopBleScan()
        }

        if(isConnected){
            disconnect()
        }
    }

    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            Log.i("dbBinderStatus", "DB service unbound")
            dbServiceBound = false
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val myBinder: PuppiDBService.LocalBinder = service as PuppiDBService.LocalBinder
            dbService = myBinder.service
            Log.i("dbBinderStatus", "DB service bound")
            dbServiceBound = true
        }
    }
}