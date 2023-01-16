package com.example.ridesharing_driver.Services

import android.util.Log
import com.example.ridesharing_driver.Common
import com.example.ridesharing_driver.Model.EventBus.DriverRequestReceived
import com.example.ridesharing_driver.Utils.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService(){

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        if (FirebaseAuth.getInstance().currentUser != null)
            UserUtils.updateToken(this,token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        Log.d("asdfasdf","${data}")
        if (data[Common.NOT_TITLE].equals(Common.REQUEST_DRIVER_TITLE))
        {
            Log.d("driverRequest","enter in if else")
            val driverRequestReceived = DriverRequestReceived()
            driverRequestReceived.key = data[Common.RIDER_KEY]
            driverRequestReceived.pickupLocation=data[Common.PICKUP_LOCATION]
            driverRequestReceived.pickupLocationString= data[Common.PICKUP_LOCATION_STRING]
            driverRequestReceived.destinationLocation = data[Common.DESTINATION_LOCATION]
            driverRequestReceived.destinationLocationString = data[Common.DESTINATION_LOCATION_STRING]

          EventBus.getDefault().postSticky(driverRequestReceived)
        }
        else
        Common.showNotification(this, Random.nextInt(),
        data[Common.NOT_TITLE],
        data[Common.NOT_BODY],
        null)
    }

}