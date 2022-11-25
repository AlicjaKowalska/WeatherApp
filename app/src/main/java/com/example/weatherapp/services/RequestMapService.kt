package com.example.weatherapp.services

import android.app.Activity
import android.app.IntentService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import com.example.weatherapp.Constants
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class RequestMapService(name: String = "RequestMapIntentService"): IntentService(name) {
    private val responseIntent: Intent = Intent(Constants.MAP_IMAGE_KEY)
    private var result = Activity.RESULT_CANCELED
    override fun onHandleIntent(intent: Intent?) {
        val key = intent?.getStringExtra("API_KEY")
        val url = URL("https://tile.openweathermap.org/map/pressure_new/1/0/0.png?appid=$key")

        val connection = url.openConnection() as HttpURLConnection
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val bitmap: Bitmap = BitmapFactory.decodeStream(connection.inputStream)
            val byteArray = bitmapToByteArray(bitmap)
            result = Activity.RESULT_OK
            responseIntent.putExtra(Constants.MAP_IMAGE_KEY, byteArray)
            responseIntent.putExtra(Constants.RESULT_KEY, result)
        } else {
            Toast.makeText(this@RequestMapService, "response code: " + connection.responseCode, Toast.LENGTH_LONG).show()
            Log.e("RetrieveMapService", "response code: " + connection.responseCode)
        }

        responseIntent.putExtra("result", result)
        sendBroadcast(responseIntent)
    }

    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray:ByteArray = stream.toByteArray()
        bitmap.recycle()
        return byteArray
    }
}