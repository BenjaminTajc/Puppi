package com.example.puppi

import android.Manifest
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.*


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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            Log.i("BleServiceScan", "Ble scan start unsuccessful, isLocationPermissionGranted: $isLocationPermissionGranted")
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
        bluetoothGatt?.disconnect()
    }

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
                    setNotify()
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
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            with(characteristic) {
                val byteArray = value
                Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()}")
                var result = byteArray.first().toInt()
            }
        }
    }

    fun ByteArray.toHexString(): String =
            joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                    separator = "\n|--",
                    prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }

    private fun setNotify() {
        val characteristic: BluetoothGattCharacteristic = bluetoothGatt?.getService(BATTERY_SERVICE)!!.getCharacteristic(BATTERY_CHAR)
        bluetoothGatt?.setCharacteristicNotification(characteristic, true )
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("BLEService", "BLE service started")
        return super.onStartCommand(intent, flags, startId)
    }

    inner class LocalBinder : Binder() {
        val service: PuppiBLEService
            get() = this@PuppiBLEService
    }

    private val mBinder: IBinder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        return mBinder
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
}