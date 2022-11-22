package com.example.weatherapp

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.work.*
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.workers.RequestWeatherWorker
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private var city = "szczecin"
    private lateinit var mSharedPreferences: SharedPreferences
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        loadSavedPreferences()
        binding.buttonGetWeather.setOnClickListener {
            requestWeather()
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
            binding.textWeatherVal.text = weather
            binding.textWeatherDescVal.text = weatherDesc
            binding.textWeatherTempVal.text = temperature
            binding.textWeatherFeelTempVal.text = feelsLike
            binding.textWeatherMinTempVal.text = minTemp
            binding.textWeatherMaxTempVal.text = maxTemp
        }
    }

    private fun requestWeather() {
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
