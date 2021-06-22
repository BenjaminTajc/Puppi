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
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.concurrent.thread

//mrbit?
//import kotlinx.android.synthetic.main.activity_main.*



private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MainActivity : AppCompatActivity(), PuppiBLEService.LiveCallBack {

    private val images = arrayOf(R.drawable.doge_smile, R.drawable.doge_blink, R.drawable.doge_wink, R.drawable.doge_woof, R.drawable.doge_angery, R.drawable.doge_sad)
    private val news = arrayOf(R.drawable.woof, R.drawable.grrr, R.drawable.sad_speech_bubble, R.drawable.null_image)
    private var imgCounter: Int = 0
    private var resCurrent: Int = 0
    private var buttonClicque = false

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
        val animationBle = AnimationUtils.loadAnimation(this, R.anim.rotate)
        bleButton.setOnClickListener {
            if(serviceBound) {
                Log.i("ScanButtonStatus", "Scan button pressed")
                bleButton.setBackgroundColor(Color.parseColor("#FF03F423"))
                bleButton.startAnimation(animationBle)
                buttonClicque = true
                if(!bleService.isConnected){
                    //bleButton.setBackgroundColor(Color.parseColor("#03A9F4"))

                    Log.i("ScanButtonStatus", "Not connected to device")

                    if(!bleService.isScanning){
                        Log.i("ScanButtonStatus", "Calling Ble scan")
                        //
                        bleService.startBleScan()
                    } else {
                        Log.i("ScanButtonStatus", "Stopping Ble scan")

                        bleService.stopBleScan()

                    }

                    bleButton.setBackgroundColor(Color.parseColor("#FF03F423"))
                } else {
                    bleService.disconnect()
                    //bleButton.clearAnimation()
                    bleButton.setBackgroundColor(Color.parseColor("#03A9F4"))


                }
            }
        }


        val historyButton = findViewById<FloatingActionButton>(R.id.historyButton)

        historyButton.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this, R.anim.righttoleft)
            historyButton.startAnimation(animation)


            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)

        }

        setForSwitching()
        textForSwitching()





        thread(start = true) {
            var kaunter = 0
            var neki = images[0]
            var novica = news[3]
            var blinkBool = false
            while(true){
                when(resCurrent) {

                    //noise
                    0 ->{
                        blinkBool = true
                        novica = news[3]
                    }
                    //bark
                    1 ->{
                        neki = images[3]
                        novica = news[0]
                        blinkBool = false
                    }
                    //growl
                    2 ->{
                        neki = images[4]
                        novica = news[1]
                        blinkBool = false
                    }
                    //whine
                    3 ->{
                        neki = images[5]
                        novica = news[2]
                        blinkBool = false
                    }


                    else -> {  Log.i("BleResult", "Value not in range [0, 3]") }
                }

                kaunter = (kaunter + 1)%2
                Log.i("vajl", "tru")

                if(blinkBool){
                    neki = images[kaunter]
                }
                imageSwitcher.post({
                    imageSwitcher.setImageResource(neki)
                })
                Thread.sleep(20)
                bubbleTextSwitcher.post({
                    bubbleTextSwitcher.setImageResource(novica)
                })

                if(blinkBool) {
                    if (kaunter == 0) {
                        Thread.sleep(800)
                    } else if (kaunter == 1) {
                        Thread.sleep(100)
                    }
                }else{
                    Thread.sleep(3000)
                }

            }
        }


    }

    private fun textForSwitching(){
        textFactory()
        textAnimations()
    }

    private fun textFactory(){
        bubbleTextSwitcher.setFactory{ textImageView() }
    }

    private fun textImageView():ImageView{
        val imageView = ImageView(this)
        imageView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        imageView.scaleType = ImageView.ScaleType.FIT_XY
        imageView.setImageResource(R.drawable.doge_smile)
        return imageView
    }

    private fun textAnimations(){
        val animOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        val animIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)


        bubbleTextSwitcher.outAnimation = animOut
        bubbleTextSwitcher.inAnimation = animIn

    }

    //doge images below

    private fun setForSwitching(){
        setFactory()
        setAnimations()
    }

    private fun setFactory(){
        imageSwitcher.setFactory { getImageView() }
    }

    private fun getImageView():ImageView{
        val imageView = ImageView(this)
        imageView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        imageView.scaleType = ImageView.ScaleType.FIT_XY
        imageView.setImageResource(R.drawable.doge_smile)
        return imageView
    }

    private fun setAnimations(){
        val animOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)
        val animIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)


        imageSwitcher.outAnimation = animOut
        imageSwitcher.inAnimation = animIn

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
        bindService(intent, mServiceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            bleService.registerCallBack(null)
            unbindService(mServiceConnection)
            serviceBound = false
            buttonClicque = false
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
        // result is the value received from BLE
        // this function gets called every time the value is sent
        resCurrent = result
    }
}