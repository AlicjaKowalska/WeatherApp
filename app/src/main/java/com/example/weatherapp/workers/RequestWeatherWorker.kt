package com.example.weatherapp.workers

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RequestWeatherWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {
    private var responseCode: Int = 0
    override fun doWork(): Result {
        val key: String = inputData.getString("API_KEY")!!
        val city = inputData.getString("city")
        val url = URL("https://api.openweathermap.org/data/2.5/weather?q=$city&appid=$key")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            responseCode = connection.responseCode
            val input = connection.inputStream.bufferedReader().use {
                it.readText()
            }
            val jsonObject = parseStringToJson(input) ?: return Result.failure()
            val weatherData = buildWeatherData(jsonObject)
            return Result.success(weatherData)

        } else {
            val data: Data = Data.Builder()
                .putInt("responseCode", connection.responseCode)
                .build()
            return Result.failure(data)
        }
    }

    private fun parseStringToJson(json: String): JSONObject? {
        var jsonObject: JSONObject? = null
        try {
            jsonObject = JSONObject(json)
        } catch (e: JSONException) {
            Log.e("RequestWeatherWorker", e.toString())
        }
        return jsonObject
    }

    private fun kelvinToCelsius(temp: Double): Double {
        return temp - 275.15
    }

    private fun buildWeatherData(jsonObject: JSONObject): Data {
        val weatherArray = jsonObject.getJSONArray("weather")
        val weatherObject = weatherArray.getJSONObject(0)
        val mainObject = jsonObject.getJSONObject("main")
        val windObject = jsonObject.getJSONObject("wind")
        return Data.Builder()
            .putString("weatherMain", weatherObject.getString("main"))
            .putString("weatherDescription", weatherObject.getString("description"))
            .putDouble("temp", kelvinToCelsius(mainObject.getDouble("temp")))
            .putDouble("feelsLike", kelvinToCelsius(mainObject.getDouble("feels_like")))
            .putDouble("tempMin", kelvinToCelsius(mainObject.getDouble("temp_min")))
            .putDouble("tempMax", kelvinToCelsius(mainObject.getDouble("temp_max")))
            .putInt("pressure", mainObject.getInt("pressure"))
            .putDouble("windSpeed", windObject.getDouble("speed"))
            .build()
    }
}
