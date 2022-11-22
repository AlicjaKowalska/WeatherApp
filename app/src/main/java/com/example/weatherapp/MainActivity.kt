package com.example.weatherapp

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.services.LocalizationService
import com.example.weatherapp.workers.RequestWeatherWorker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var airplaneModeReceiver: BroadcastReceiver
    private lateinit var mSharedPreferences: SharedPreferences
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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if(!checkPermissions())
            enableGetWeatherButton()

        val isAirplaneModeEnabled: Boolean = Settings.Global.getInt(baseContext.contentResolver,
        Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        if (isAirplaneModeEnabled) {
            disableGetWeatherButton(R.string.airplane_on)
        }

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        loadSavedPreferences()
    }

    override fun onResume() {
        super.onResume()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                binding.progressBar.visibility = View.INVISIBLE
                binding.textViewLocalization.text = intent.getStringExtra("cityName")
                requestWeather(intent.getStringExtra("cityName").toString())
            }
        }
        airplaneModeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val isAirplaneModeEnabled = intent.getBooleanExtra("state", false)
                if (isAirplaneModeEnabled) {
                    disableGetWeatherButton(R.string.airplane_on)
                } else {
                    enableGetWeatherButton()
                }
            }
        }
        registerReceiver(airplaneModeReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        registerReceiver(broadcastReceiver, IntentFilter("locationUpdate"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(airplaneModeReceiver)
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

    private fun enableGetWeatherButton() {
        binding.buttonGetWeather.setOnClickListener {
            if(binding.textViewLocalization.text.isEmpty())
                binding.progressBar.visibility = View.VISIBLE
            Intent(this, LocalizationService::class.java).also { intent ->
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
        binding.buttonGetWeather.setText(R.string.get_weather_button)
        binding.buttonGetWeather.isEnabled = true
    }

    private fun disableGetWeatherButton(stringId: Int) {
        binding.buttonGetWeather.setText(stringId)
        binding.buttonGetWeather.isEnabled = false
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
                enableGetWeatherButton()
            } else {
                Toast.makeText(this@MainActivity, "App requires location permission to work properly!",Toast.LENGTH_LONG).show()
                checkPermissions()
            }
        }
    }

    private fun loadSavedPreferences() {
        val lastUpdate = mSharedPreferences.getString("lastUpdate", null)
        if (lastUpdate != null) {

            binding.textLastUpdateVal.text = lastUpdate
            val weather = mSharedPreferences.getString(Constants.WEATHER_MAIN_KEY, "")
            val weatherDesc = mSharedPreferences.getString(Constants.WEATHER_DESC_KEY, "")
            val temperature = mSharedPreferences.getString(Constants.TEMP_KEY, "")
            val feelsLike = mSharedPreferences.getString(Constants.FEELS_LIKE_KEY, "")
            val minTemp = mSharedPreferences.getString(Constants.TEMP_MIN_KEY, "")
            val maxTemp = mSharedPreferences.getString(Constants.TEMP_MAX_KEY, "")
            val localization = mSharedPreferences.getString(Constants.LOCALIZATION_KEY, "")
            binding.textWeatherVal.text = weather
            binding.textWeatherDescVal.text = weatherDesc
            binding.textWeatherTempVal.text = temperature
            binding.textWeatherFeelTempVal.text = feelsLike
            binding.textWeatherMinTempVal.text = minTemp
            binding.textWeatherMaxTempVal.text = maxTemp
            binding.textViewLocalization.text = localization
        }
    }

    private fun requestWeather(city: String) {
        val constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val data: Data = Data.Builder()
            .putString("API_KEY", Constants.API_KEY)
            .putString("city", city)
            .build()
        val workRequest: WorkRequest = OneTimeWorkRequest.Builder(RequestWeatherWorker::class.java)
            .setInputData(data)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        WorkManager.getInstance(applicationContext)
            .getWorkInfoByIdLiveData(workRequest.id)
            .observe(this@MainActivity) { workInfo: WorkInfo ->
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val editor = mSharedPreferences.edit()
                        binding.textWeatherVal.text =
                            workInfo.outputData.getString(Constants.WEATHER_MAIN_KEY)
                        editor.putString(
                            Constants.WEATHER_MAIN_KEY,
                            workInfo.outputData.getString(Constants.WEATHER_MAIN_KEY)
                        )
                        binding.textWeatherDescVal.text =
                            workInfo.outputData.getString(Constants.WEATHER_DESC_KEY)
                        editor.putString(
                            Constants.WEATHER_DESC_KEY,
                            workInfo.outputData.getString(Constants.WEATHER_DESC_KEY)
                        )
                        binding.textWeatherTempVal.text =
                            workInfo.outputData.getDouble(Constants.TEMP_KEY, 0.0).toString()
                        editor.putString(
                            Constants.TEMP_KEY,
                            workInfo.outputData.getDouble(Constants.TEMP_KEY, 0.0).toString()
                        )
                        binding.textWeatherFeelTempVal.text =
                            workInfo.outputData.getDouble(Constants.FEELS_LIKE_KEY, 0.0).toString()
                        editor.putString(
                            Constants.FEELS_LIKE_KEY,
                            workInfo.outputData.getDouble(Constants.FEELS_LIKE_KEY, 0.0).toString()
                        )
                        binding.textWeatherMinTempVal.text =
                            workInfo.outputData.getDouble(Constants.TEMP_MIN_KEY, 0.0).toString()
                        editor.putString(
                            Constants.TEMP_MIN_KEY,
                            workInfo.outputData.getDouble(Constants.TEMP_MIN_KEY, 0.0).toString()
                        )
                        binding.textWeatherMaxTempVal.text =
                            workInfo.outputData.getDouble("tempMax", 0.0).toString()
                        editor.putString(
                            Constants.TEMP_MAX_KEY,
                            workInfo.outputData.getDouble(Constants.TEMP_MAX_KEY, 0.0).toString()
                        )
                        editor.putString(
                            Constants.LOCALIZATION_KEY,
                            city
                        )
                        val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                        val current = LocalDateTime.now().format(formatter)
                        binding.textLastUpdateVal.text = current
                        editor.putString("lastUpdate", current)
                        editor.apply()
                    }
                    WorkInfo.State.FAILED -> {
                        Toast.makeText(
                            this@MainActivity,
                            "Failed to refresh weather",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                    }
                }
            }
    }
}
