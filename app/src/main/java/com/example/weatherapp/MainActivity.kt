package com.example.weatherapp

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.services.LocalizationService

class MainActivity : AppCompatActivity() {

    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var mService: LocalizationService
    private lateinit var binding: ActivityMainBinding
    private var mBound: Boolean = false
    private val MIN_SDK_VERSION = 23

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalizationService.ServiceBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if(!checkPermissions())
            enableButton()
    }

    override fun onResume() {
        super.onResume()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                binding.progressBar.visibility = View.INVISIBLE
                binding.textViewLocalization.text = intent.getStringExtra("cityName")
            }
        }
        registerReceiver(broadcastReceiver, IntentFilter("locationUpdate"))
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    fun enableButton() {
        binding.buttonGetWeather.setOnClickListener {
            if(binding.textViewLocalization.text.isEmpty())
                binding.progressBar.visibility = View.VISIBLE
            Intent(this, LocalizationService::class.java).also { intent ->
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    private fun checkPermissions() : Boolean {
        if (Build.VERSION.SDK_INT >= MIN_SDK_VERSION && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 100){ //result requestCode to match with a requestCode given in requestPermissions(), this can be any >0 value
            if( grantResults.size == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED){
                enableButton()
            } else {
                Toast.makeText(this@MainActivity, "App requires location permission to work properly!",Toast.LENGTH_LONG).show()
                checkPermissions()
            }
        }
    }
}