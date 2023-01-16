package com.example.ridesharing_driver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.ridesharing_driver.Model.DriverInfoModel
import com.google.android.gms.maps.model.LatLng
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList

object Common {

    fun showNotification(context: Context, nextInt: Int, title: String?, body: String?, intent: Intent?) {

        var pendingIntent : PendingIntent? = null
        if (intent != null)
            pendingIntent = PendingIntent.getActivity(context,nextInt,intent,PendingIntent.FLAG_UPDATE_CURRENT)
        val NOTIFICATION_CHANNEL_ID = "ride_Sharing_app"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.O)
        {
            val notificationChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID,"Saveen_123",
            NotificationManager.IMPORTANCE_HIGH)
            notificationChannel.description = "ride making app"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor= Color.RED
            notificationChannel.vibrationPattern = longArrayOf(0,1000,500,1000)
            notificationChannel.enableVibration(true)

            notificationManager.createNotificationChannel(notificationChannel)

        }
        val builder = NotificationCompat.Builder(context,NOTIFICATION_CHANNEL_ID)
        builder.setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_VIBRATE)
            .setSmallIcon(R.drawable.ic_baseline_directions_car_24)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources,R.drawable.ic_baseline_directions_car_24))
        if (pendingIntent != null)
            builder.setContentIntent(pendingIntent)
        val notification = builder.build()
        notificationManager.notify(nextInt,notification)

    }

    fun buildWelcomeMessage(): String {

        return StringBuilder("Welcome ")
            .append(currentUser!!.firstName)
            .append(" ")
            .append(currentUser!!.lastName)
            .toString()
    }

    //DECODE POLY
    fun decodePoly(encoded: String): ArrayList<LatLng>? {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val p = LatLng(
                lat.toDouble() / 1E5,
                lng.toDouble() / 1E5
            )
            poly.add(p)
        }
        return poly
    }

    fun createUniqueTripIdNumber(timeOffset: Long?): String? {

        val rd = Random()
        var current = System.currentTimeMillis()+timeOffset!!
        var unique = current+rd.nextLong()
        if (unique < 0) unique *= -1
        return unique.toString()

    }

    val RIDER_REQUEST_COMPLETE_TRIP: String = "RequestCompleteTripToRider"
    val REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP: String = "DeclineAndRemoveTrip"
    val TRIP_DESTINATION_LOCATION_REF: String= "TripDestinationLocation"
    val WAIT_TIME_IN_MIN: Int = 1
    val MIN_RANGE_PICKUP_IN_KM: Double = 0.05 //50m
    val TRIP_PICKUP_REF: String = "TripPickupLocation"
    val TRIP_KEY: String="TripKey"
    val REQUEST_DRIVER_ACCEPT:String = "Accept"
    val TRIP: String = "Trip"
    val RIDER_INFO: String = "Riders" //Same name of reference in your Firebase
    val DESTINATION_LOCATION:String = "DestinationLocation"
    val DESTINATION_LOCATION_STRING:String = "DestinationLocationString"
    val PICKUP_LOCATION_STRING:String= "PickupLocationString"
     val DRIVER_KEY:String="DriverKey"
    val REQUEST_DRIVER_DECLINE:String = "Decline"
    val RIDER_KEY:String= "RiderKey"
    val PICKUP_LOCATION:String= "PickupLocation"
    val REQUEST_DRIVER_TITLE: String = "RequestDriver"
    var currentUser: DriverInfoModel? = null
    val DRIVER_INFO_REFERENCE: String = "DriverInfo"
    val NOT_BODY: String = "body"
    val NOT_TITLE: String = "title"
    val TOKEN_REFERENCE: String="Token"
    val DRIVER_LOCATION_REFRENCE: String= "DriversLocation"
}