package com.example.weatherapp.services

import android.app.Service
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import java.util.*

class LocalizationService : Service(){

    private lateinit var locationListener: LocationListener
    private lateinit var locationManager: LocationManager
    private val binder: Binder = ServiceBinder()

    override fun onBind(intent: Intent): IBinder {
        Log.i("ServiceRunning", "Service is bounded")
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val geocoder = Geocoder(applicationContext, Locale.getDefault())
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val city = addresses?.get(0)?.locality

                val i = Intent("locationUpdate")
                i.putExtra("cityName",  city)
                sendBroadcast(i)
            }

            override fun onProviderDisabled(provider: String) {
                super.onProviderDisabled(provider)
                val i = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(i)
            }
        }

        locationManager = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        //noinspection MissingPermission
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, locationListener)
    }

    override fun onUnbind(intent: Intent): Boolean {
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        //noinspection MissingPermission
        locationManager.removeUpdates(locationListener)
    }

    inner class ServiceBinder : Binder() {
        fun getService() : LocalizationService = this@LocalizationService
    }
}