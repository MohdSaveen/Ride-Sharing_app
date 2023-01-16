package com.example.ridesharing_rider.Services

import android.util.Log
import com.example.ridesharing_rider.Common
import com.example.ridesharing_rider.Model.EventBus.DeclineRequestAndRemoveTripFromDriver
import com.example.ridesharing_rider.Model.EventBus.DeclineRequestFromDriver
import com.example.ridesharing_rider.Model.EventBus.DriverAcceptTripEvent
import com.example.ridesharing_rider.Model.EventBus.DriverCompleteTripEvent
import com.example.ridesharing_rider.UserUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.greenrobot.eventbus.EventBus
import kotlin.random.Random

class MyFirebaseMessagingService : FirebaseMessagingService() {

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    if (FirebaseAuth.getInstance().currentUser != null) UserUtils.updateToken(this, token)
  }
  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val data = message.data
    if (data != null) {

      if (data[Common.NOT_TITLE] != null) {
        if (data[Common.NOT_TITLE].equals(Common.REQUEST_DRIVER_DECLINE)) {

            EventBus.getDefault().postSticky(DeclineRequestFromDriver())

        }else if (data[Common.NOT_TITLE].equals(Common.REQUEST_DRIVER_ACCEPT)) {

          EventBus.getDefault().postSticky(DriverAcceptTripEvent(data[Common.TRIP_KEY]!!))

        }else if (data[Common.NOT_TITLE].equals(Common.REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP)) {

          EventBus.getDefault().postSticky(DeclineRequestAndRemoveTripFromDriver())

        }else if (data[Common.NOT_TITLE].equals(Common.RIDER_REQUEST_COMPLETE_TRIP)) {

          val tripkey = data[Common.TRIP_KEY]
          EventBus.getDefault().postSticky(DriverCompleteTripEvent(tripkey!!))

        }
        else{

          Common.showNotification(
              this, Random.nextInt(), data[Common.NOT_TITLE], data[Common.NOT_BODY], null)
        }
      }
    }
  }
}
