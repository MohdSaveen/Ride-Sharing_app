package com.example.ridesharing_rider

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import com.example.ridesharing_rider.Common.DESTINATION_LOCATION
import com.example.ridesharing_rider.Common.DESTINATION_LOCATION_STRING
import com.example.ridesharing_rider.Common.NOT_BODY
import com.example.ridesharing_rider.Common.NOT_TITLE
import com.example.ridesharing_rider.Common.PICKUP_LOCATION
import com.example.ridesharing_rider.Common.PICKUP_LOCATION_STRING
import com.example.ridesharing_rider.Common.REQUEST_DRIVER_TITLE
import com.example.ridesharing_rider.Common.RIDER_INFO_REFERENCE
import com.example.ridesharing_rider.Common.RIDER_KEY
import com.example.ridesharing_rider.Common.TOKEN_REFERENCE
import com.example.ridesharing_rider.Model.DriverGeoModel
import com.example.ridesharing_rider.Model.EventBus.SelectedPlaceEvent
import com.example.ridesharing_rider.Model.FCMSendData
import com.example.ridesharing_rider.Model.TokenModel
import com.example.ridesharing_rider.Remote.IFCMService
import com.example.ridesharing_rider.Remote.RetrofitFCMClient
import com.google.android.gms.common.internal.service.Common
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.lang.StringBuilder

object UserUtils {

    fun updateUser(
        view: View?,
        updateData:Map<String,Any>
    ){
        FirebaseDatabase.getInstance()
            .getReference(RIDER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener {
                Snackbar.make(view!!,it.message!!, Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!,"Update information Success", Snackbar.LENGTH_SHORT).show()
            }
    }

    fun updateToken(context: Context, token: String) {

        val tokenModel= TokenModel()
        tokenModel.token= token

        FirebaseDatabase.getInstance()
            .getReference(TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { e -> Toast.makeText(context,e.message, Toast.LENGTH_SHORT).show() }
            .addOnSuccessListener {
                Toast.makeText(context,"get token updated",Toast.LENGTH_SHORT).show()
            }
    }

    fun sendRequestToDriver(context: Context, mainLayout: RelativeLayout?, foundDriver: DriverGeoModel?, selectedPlaceEvent: SelectedPlaceEvent) {

        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        //get token
        FirebaseDatabase.getInstance()
            .getReference(TOKEN_REFERENCE)
            .child(foundDriver!!.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener{

                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d("token" ,"${TokenModel::token}")
                    if (snapshot.exists())
                    {

                        val tokenModel = snapshot.getValue(TokenModel::class.java)

                        val notificationData = HashMap<String, String>()
                        notificationData.put(NOT_TITLE,REQUEST_DRIVER_TITLE)
                        notificationData.put(NOT_BODY,"This message represent for Request Driver action")
                        notificationData.put(RIDER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)

                        notificationData.put(PICKUP_LOCATION_STRING,selectedPlaceEvent.originString)
                        notificationData.put(PICKUP_LOCATION,StringBuilder()
                            .append(selectedPlaceEvent.origin.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.origin.longitude)
                            .toString())

                        notificationData.put(DESTINATION_LOCATION_STRING,selectedPlaceEvent.address)
                        notificationData.put(DESTINATION_LOCATION,StringBuilder()
                            .append(selectedPlaceEvent.destination.latitude)
                            .append(",")
                            .append(selectedPlaceEvent.destination.longitude)
                            .toString())

                        val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)
                        Log.d("qwerty","$tokenModel.token")

                        compositeDisposable.add(

                            ifcmService.sendNotification(fcmSendData)
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                            .subscribe ({ fcmResponse ->
                                if (fcmResponse.success == 0) {
                                        compositeDisposable.clear()
                                        Snackbar.make(mainLayout!!,context.getString(R.string.send_request_driver_failed),Snackbar.LENGTH_SHORT).show()
                                    }
                            },
                            {t: Throwable? ->

                                compositeDisposable.clear()
                                Snackbar.make(mainLayout!!,t!!.message!!,Snackbar.LENGTH_SHORT).show()

                            }))
                    }
                    else
                    {
                        Snackbar.make(mainLayout!!,context.getString(R.string.token_not_found),Snackbar.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(mainLayout!!,error.message,Snackbar.LENGTH_SHORT).show()
                }

            })
    }

//    private fun sendNotification(context: Context, messageBody: String) {
//        val intent = Intent(this, MainActivity::class.java)
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//        val pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, intent,
//            PendingIntent.FLAG_IMMUTABLE)
//
//        val channelId = getString(R.string.default_notification_channel_id)
//        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
//        val notificationBuilder = NotificationCompat.Builder(this, channelId)
//            .setSmallIcon(R.drawable.ic_stat_ic_notification)
//            .setContentTitle(getString(R.string.fcm_message))
//            .setContentText(messageBody)
//            .setAutoCancel(true)
//            .setSound(defaultSoundUri)
//            .setContentIntent(pendingIntent)
//
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//
//        // Since android Oreo notification channel is needed.
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(channelId,
//                "Channel human readable title",
//                NotificationManager.IMPORTANCE_DEFAULT)
//            notificationManager.createNotificationChannel(channel)
//        }
//
//        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
//    }


}