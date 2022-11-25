package com.example.weatherapp.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Configuration
import androidx.work.Data
import com.example.weatherapp.Constants
import com.example.weatherapp.R
import com.example.weatherapp.Weather
import kotlin.math.roundToInt

class HourlyWeatherService : JobService(){

    private var jobCancelled = false

    override fun onStartJob(params: JobParameters): Boolean {
        doBackgroundWork(params)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        jobCancelled = true
        return true
    }

    private fun doBackgroundWork(params: JobParameters) {
        val runnable = Runnable {
            val key = params.extras.getString("API_KEY")!!
            val city = params.extras.getString("city").toString()

            val weather = Weather()
            val data: Data = weather.getWeather(key, city)

            val weatherType = data.getString(Constants.WEATHER_MAIN_KEY).toString()
            val temp = data.getDouble(Constants.TEMP_KEY, 0.0).roundToInt()
            val feelsLike = data.getDouble(Constants.FEELS_LIKE_KEY, 0.0).roundToInt()

            sendNotification(city, weatherType, temp, feelsLike)

            jobFinished(params, false)
        }
        val thread = Thread(runnable)
        thread.start()
    }

    private fun sendNotification(city: String, weatherType: String, temp: Int, feelsLike: Int) {
        val builder = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.CHANNEL_ID, "Hourly Weather",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager : NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            builder.setSmallIcon(R.drawable.small_icon)
                .setContentTitle(city)
                .setContentText("Weather: " + weatherType + "\n" +
                                "Temperature: " + temp + ", " +
                                "Feels like: " + feelsLike)
        } else {
            builder.setSmallIcon(R.drawable.small_icon)
                .setContentTitle(city)
                .setContentText("Weather: " + weatherType + "\n" +
                        "Temperature: " + temp + " " +
                        "Feels like: " + feelsLike)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }
        val notificationManagerCompat = NotificationManagerCompat.from(this)
        notificationManagerCompat.notify(1, builder.build())
    }

    init {
        Configuration.Builder().setJobSchedulerJobIdRange(0, 1000).build()
    }
}