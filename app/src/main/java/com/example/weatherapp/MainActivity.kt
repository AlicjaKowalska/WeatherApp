package com.example.weatherapp

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.os.PersistableBundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.*
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.services.LocalizationService
import com.example.weatherapp.services.HourlyWeatherService
import com.example.weatherapp.workers.RequestWeatherWorker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var airplaneModeReceiver: BroadcastReceiver
    private lateinit var mSharedPreferences: SharedPreferences
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var mService: LocalizationService
    private lateinit var binding: ActivityMainBinding
    private var isNotificationPermissionGranted = false
    private var isLocationPermissionGranted = false
    private var mBound: Boolean = false

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

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ permissions ->
            isLocationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: isLocationPermissionGranted
            isNotificationPermissionGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: isNotificationPermissionGranted
        }

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

        if (!isLocationPermissionGranted || !isNotificationPermissionGranted)
            requestPermissions()

        if (isLocationPermissionGranted) {
            enableGetWeatherButton()
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                binding.progressBar.visibility = View.INVISIBLE
                binding.textViewLocalization.text = intent.getStringExtra("cityName")
                var city = intent.getStringExtra("cityName").toString()
                requestWeather(city)
                getHourlyWeather(city)
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
            getLocalization()
        }
    }

    private fun getLocalization(){
        if(binding.textViewLocalization.text.isEmpty())
            binding.progressBar.visibility = View.VISIBLE
        Intent(this, LocalizationService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        binding.buttonGetWeather.setText(R.string.get_weather_button)
        binding.buttonGetWeather.isEnabled = true
    }

    private fun disableGetWeatherButton(stringId: Int) {
        binding.buttonGetWeather.setText(stringId)
        binding.buttonGetWeather.isEnabled = false
    }

    private fun getHourlyWeather(city: String) {
        val locationBundle = PersistableBundle()
        locationBundle.putString("API_KEY", Constants.API_KEY)
        locationBundle.putString("city", city)

        val jobScheduler = getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
        val jobInfo = JobInfo.Builder( 101,
            ComponentName(applicationContext, HourlyWeatherService::class.java))
            .setMinimumLatency(0)
            .setPersisted(true)
            .setPeriodic(60 * 60 * 1000)
            .setExtras(locationBundle)
            .build()
        jobScheduler.schedule(jobInfo)
    }

    private fun requestPermissions() {

        isLocationPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        isNotificationPermissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        val permissionRequest : MutableList<String> = ArrayList()

        if(!isLocationPermissionGranted) {
            permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if(!isNotificationPermissionGranted) {
            permissionRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if(permissionRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionRequest.toTypedArray())
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
