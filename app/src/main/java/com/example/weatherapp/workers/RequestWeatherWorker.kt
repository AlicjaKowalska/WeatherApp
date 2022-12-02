package com.example.weatherapp.workers

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.weatherapp.Weather
import java.net.HttpURLConnection

class RequestWeatherWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    override fun doWork(): Result {
        val key: String = inputData.getString("API_KEY")!!
        val city = inputData.getString("city")

        val weather = Weather()
        val data: Data = weather.getWeather(key, city.toString())

        return if (data.getInt("responseCode", 0) == HttpURLConnection.HTTP_OK)
            Result.success(data)
        else
            Result.failure(data)
    }
}
