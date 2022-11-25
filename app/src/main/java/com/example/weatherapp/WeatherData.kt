package com.example.weatherapp

import android.util.Log
import androidx.work.Data
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Weather {

    private var responseCode: Int = 0

    fun getWeather(key: String, city: String) : Data {
        val url = URL(Constants.API_URL + "?q=$city&appid=$key")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connect()
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            responseCode = connection.responseCode
            val input = connection.inputStream.bufferedReader().use {
                it.readText()
            }
            val jsonObject = parseStringToJson(input)
            val weatherData = buildWeatherData(jsonObject)
            return weatherData
        } else {
            val data: Data = Data.Builder()
                .putInt("responseCode", HttpURLConnection.HTTP_NOT_FOUND)
                .build()
            return data
        }
    }
}

private fun parseStringToJson(json: String): JSONObject {
    lateinit var jsonObject: JSONObject
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
        .putString(Constants.WEATHER_MAIN_KEY, weatherObject.getString("main"))
        .putString(Constants.WEATHER_DESC_KEY, weatherObject.getString("description"))
        .putDouble(Constants.TEMP_KEY, kelvinToCelsius(mainObject.getDouble("temp")))
        .putDouble(Constants.FEELS_LIKE_KEY, kelvinToCelsius(mainObject.getDouble("feels_like")))
        .putDouble(Constants.TEMP_MIN_KEY, kelvinToCelsius(mainObject.getDouble("temp_min")))
        .putDouble(Constants.TEMP_MAX_KEY, kelvinToCelsius(mainObject.getDouble("temp_max")))
        .putInt("pressure", mainObject.getInt("pressure"))
        .putDouble("windSpeed", windObject.getDouble("speed"))
        .putInt("responseCode", HttpURLConnection.HTTP_OK)
        .build()
}