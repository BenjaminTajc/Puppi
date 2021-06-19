package com.example.puppi


//import android.animation.Animator
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.animation.AnimationUtils
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton

//mrbit?
//import kotlinx.android.synthetic.main.activity_main.*



private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : AppCompatActivity(), PuppiBLEService.LiveCallBack {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        Intent(this, PuppiDBService::class.java).also { intent ->
            startService(intent)
        }
        Intent(this, PuppiBLEService::class.java).also { intent ->
            startService(intent)
        }

        requestLocationPermission()
        promptEnableBluetooth()

        val bleButton = findViewById<FloatingActionButton>(R.id.bleButton)

        bleButton.setOnClickListener {
            if(serviceBound) {
                Log.i("ScanButtonStatus", "Scan button pressed")
                if(!bleService.isConnected){
                    if(!bleService.isScanning){
                        Log.i("ScanButtonStatus", "Calling Ble scan")
                        bleService.startBleScan()
                    } else {
                        Log.i("ScanButtonStatus", "Stopping Ble scan")
                        bleService.stopBleScan()
                    }
                } else {
                    bleService.disconnect()
                }
            }
        }

        val historyButton = findViewById<FloatingActionButton>(R.id.historyButton)

        historyButton.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.lefttoright)
            historyButton.startAnimation(animation)

            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)



        }
    }

    private lateinit var bleService: PuppiBLEService

    var serviceBound: Boolean = false

    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    override fun onResume() {
        super.onResume()
        if(!bluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, PuppiBLEService::class.java)
        //startService(intent)
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            bleService.registerCallBack(null)
            unbindService(mServiceConnection)
            serviceBound = false
        }
    }

    private fun promptEnableBluetooth() {
        if(!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }

    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            val alert = AlertDialog.Builder(this)
            alert.setTitle("Location permission required")
            alert.setMessage(
                "Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices."
            )
            alert.setPositiveButton(android.R.string.ok) { _, _ ->
                requestPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            alert.show()
        }
    }

    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                }
            }
        }
    }

    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            Log.i("BleBinderStatus", "Ble service unbound")
            serviceBound = false
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val myBinder: PuppiBLEService.LocalBinder = service as PuppiBLEService.LocalBinder
            bleService = myBinder.service
            Log.i("BleBinderStatus", "Ble service bound")
            bleService.registerCallBack(this@MainActivity)
            serviceBound = true
        }
    }

    override fun getResult(result: Int) {
        TODO("Not yet implemented")
        // result is the value received from BLE
        // this function gets called every time the value is sent
    }
}