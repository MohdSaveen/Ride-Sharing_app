package com.example.ridesharing_driver.Utils

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.example.ridesharing_driver.Common
import com.example.ridesharing_driver.Common.DRIVER_KEY
import com.example.ridesharing_driver.Common.NOT_BODY
import com.example.ridesharing_driver.Common.NOT_TITLE
import com.example.ridesharing_driver.Common.PICKUP_LOCATION
import com.example.ridesharing_driver.Common.REQUEST_DRIVER_ACCEPT
import com.example.ridesharing_driver.Common.REQUEST_DRIVER_DECLINE
import com.example.ridesharing_driver.Common.REQUEST_DRIVER_TITLE
import com.example.ridesharing_driver.Common.RIDER_KEY
import com.example.ridesharing_driver.Common.TOKEN_REFERENCE
import com.example.ridesharing_driver.Common.TRIP_KEY
import com.example.ridesharing_driver.Model.EventBus.NotifyRiderEvent
import com.example.ridesharing_driver.Model.FCMSendData
import com.example.ridesharing_driver.Model.TokenModel
import com.example.ridesharing_driver.R
import com.example.ridesharing_driver.Remote.IFCMService
import com.example.ridesharing_driver.Remote.RetrofitFCMClient
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap

object UserUtils {

    fun updateUser(
        view: View?,
        updateData:Map<String,Any>
        ){
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .updateChildren(updateData)
            .addOnFailureListener {
                Snackbar.make(view!!,it.message!!,Snackbar.LENGTH_LONG).show()
            }.addOnSuccessListener {
                Snackbar.make(view!!,"Update information Success",Snackbar.LENGTH_SHORT).show()
            }
    }

    fun updateToken(context: Context, token: String) {

        val tokenModel= TokenModel()
        tokenModel.token= token

        FirebaseDatabase.getInstance()
            .getReference(Common.TOKEN_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .setValue(tokenModel)
            .addOnFailureListener { e -> Toast.makeText(context,e.message,Toast.LENGTH_SHORT).show() }
            .addOnSuccessListener {  }
    }

    fun sendDeclineRequest(view: View, activity: Activity, key: String) {

        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        //get token
        FirebaseDatabase.getInstance()
            .getReference(TOKEN_REFERENCE)
            .child(key)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists())
                    {

                        val tokenModel = snapshot.getValue(TokenModel::class.java)

                        val notificationData:MutableMap<String,String> = HashMap()
                        notificationData.put(NOT_TITLE,REQUEST_DRIVER_DECLINE)
                        notificationData.put(NOT_BODY,"This message represent for decline action from Driver")
                        notificationData.put(DRIVER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)

                        val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                        compositeDisposable.add(
                            ifcmService.sendNotification(fcmSendData)!!
                                .subscribeOn(Schedulers.newThread())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe ({fcmResponse ->
                                    if (fcmResponse!!.success ==0)
                                    {
                                        compositeDisposable.clear()
                                        Snackbar.make(view,activity.getString(R.string.decline_failed),Snackbar.LENGTH_SHORT).show()
                                    }
                                    else
                                    {
                                        Snackbar.make(view,activity.getString(R.string.decline_success),Snackbar.LENGTH_LONG).show()
                                    }
                                },
                                    {t: Throwable? ->

                                        compositeDisposable.clear()!!
                                        Snackbar.make(view,t!!.message!!,Snackbar.LENGTH_SHORT).show()

                                    }))
                    }
                    else
                    {
                        compositeDisposable.clear()
                        Snackbar.make(view,activity.getString(R.string.token_not_found),Snackbar.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(view,error.message,Snackbar.LENGTH_SHORT).show()
                }

            })

    }

    fun sendAcceptRequestToRider(view: View?, requireContext: Context, key: String, tripNumberId: String?) {

        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        //get token
        FirebaseDatabase.getInstance()
            .getReference(TOKEN_REFERENCE)
            .child(key)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists())
                    {

                        val tokenModel = snapshot.getValue(TokenModel::class.java)

                        val notificationData:MutableMap<String,String> = HashMap()
                        notificationData.put(NOT_TITLE,REQUEST_DRIVER_ACCEPT)
                        notificationData.put(NOT_BODY,"This message represent for decline action from Driver")
                        notificationData.put(DRIVER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)
                        notificationData.put(Common.TRIP_KEY,tripNumberId!!)

                        val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                        compositeDisposable.add(
                            ifcmService.sendNotification(fcmSendData)!!
                                .subscribeOn(Schedulers.newThread())
                                ?.observeOn(AndroidSchedulers.mainThread())
                                !!.subscribe ({fcmResponse ->
                                    if (fcmResponse!!.success ==0)
                                    {
                                        compositeDisposable.clear()
                                        Snackbar.make(view!!,requireContext.getString(R.string.accept_failed),Snackbar.LENGTH_SHORT).show()
                                    }
                                },
                                    {t: Throwable? ->

                                        compositeDisposable.clear()!!
                                        Snackbar.make(view!!,t!!.message!!,Snackbar.LENGTH_SHORT).show()

                                    }))
                    }
                    else
                    {
                        compositeDisposable.clear()
                        Snackbar.make(view!!,requireContext.getString(R.string.token_not_found),Snackbar.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(view!!,error.message,Snackbar.LENGTH_SHORT).show()
                }

            })


    }

    fun sendNotifyToRider(context: Context, view: View, key: String?) {

        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        //get token
        FirebaseDatabase.getInstance()
            .getReference(TOKEN_REFERENCE)
            .child(key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists())
                    {

                        val tokenModel = snapshot.getValue(TokenModel::class.java)

                        val notificationData:MutableMap<String,String> = HashMap()
                        notificationData.put(NOT_TITLE,context.getString(R.string.driver_arrived))
                        notificationData.put(NOT_BODY,context.getString(R.string.your_driver_arrived))
                        notificationData.put(DRIVER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)
                        notificationData.put(Common.RIDER_KEY,key!!)

                        val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                        compositeDisposable.add(
                            ifcmService.sendNotification(fcmSendData)!!
                                .subscribeOn(Schedulers.newThread())
                                ?.observeOn(AndroidSchedulers.mainThread())
                            !!.subscribe ({fcmResponse ->
                                    if (fcmResponse!!.success ==0)
                                    {
                                        compositeDisposable.clear()
                                        Snackbar.make(view!!,context.getString(R.string.accept_failed),Snackbar.LENGTH_SHORT).show()
                                    }
                                    else
                                        EventBus.getDefault().postSticky(NotifyRiderEvent())
                                },
                                    {t: Throwable? ->

                                        compositeDisposable.clear()!!
                                        Snackbar.make(view!!,t!!.message!!,Snackbar.LENGTH_SHORT).show()

                                    }))
                    }
                    else
                    {
                        compositeDisposable.clear()
                        Snackbar.make(view!!,context.getString(R.string.token_not_found),Snackbar.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Snackbar.make(view!!,error.message,Snackbar.LENGTH_SHORT).show()
                }

            })

    }

    fun sendDeclineAndRemoveTripRequest(view: View, activity: FragmentActivity, key: String, tripNumberId: String?) {

        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)

        FirebaseDatabase.getInstance().getReference(Common.TRIP)
            .child(tripNumberId!!)
            .removeValue()
            .addOnFailureListener { e ->
                Snackbar.make(view,e.message!!,Snackbar.LENGTH_SHORT).show()
            }.addOnSuccessListener {
                //After remove success , we will send notification to Rider
                //get token
                FirebaseDatabase.getInstance()
                    .getReference(TOKEN_REFERENCE)
                    .child(key)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists())
                            {

                                val tokenModel = snapshot.getValue(TokenModel::class.java)

                                val notificationData:MutableMap<String,String> = HashMap()
                                notificationData.put(NOT_TITLE,Common.REQUEST_DRIVER_DECLINE_AND_REMOVE_TRIP)
                                notificationData.put(NOT_BODY,"This message represent for decline action from Driver")
                                notificationData.put(DRIVER_KEY,FirebaseAuth.getInstance().currentUser!!.uid)

                                val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                                compositeDisposable.add(
                                    ifcmService.sendNotification(fcmSendData)!!
                                        .subscribeOn(Schedulers.newThread())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe ({fcmResponse ->
                                            if (fcmResponse!!.success ==0)
                                            {
                                                compositeDisposable.clear()
                                                Snackbar.make(view,activity.getString(R.string.decline_failed),Snackbar.LENGTH_SHORT).show()
                                            }
                                            else
                                            {
                                                Snackbar.make(view,activity.getString(R.string.decline_success),Snackbar.LENGTH_LONG).show()
                                            }
                                        },
                                            {t: Throwable? ->

                                                compositeDisposable.clear()!!
                                                Snackbar.make(view,t!!.message!!,Snackbar.LENGTH_SHORT).show()

                                            }))
                            }
                            else
                            {
                                compositeDisposable.clear()
                                Snackbar.make(view,activity.getString(R.string.token_not_found),Snackbar.LENGTH_SHORT).show()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(view,error.message,Snackbar.LENGTH_SHORT).show()
                        }

                    })

            }
    }

    fun sendCompleteTripRider(requireView: View, context: Context, key:String?,tripNumberId: String) {


        val compositeDisposable = CompositeDisposable()
        val ifcmService = RetrofitFCMClient.instance!!.create(IFCMService::class.java)


                //After remove success , we will send notification to Rider
                //get token
                FirebaseDatabase.getInstance()
                    .getReference(TOKEN_REFERENCE)
                    .child(key!!)
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.exists())
                            {

                                val tokenModel = snapshot.getValue(TokenModel::class.java)

                                val notificationData:MutableMap<String,String> = HashMap()
                                notificationData.put(NOT_TITLE,Common.RIDER_REQUEST_COMPLETE_TRIP)
                                notificationData.put(NOT_BODY,"This message represent for request complete Trip to Rider")
                                notificationData.put(TRIP_KEY,tripNumberId!!)

                                val fcmSendData = FCMSendData(tokenModel!!.token,notificationData)

                                compositeDisposable.add(
                                    ifcmService.sendNotification(fcmSendData)!!
                                        .subscribeOn(Schedulers.newThread())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe ({fcmResponse ->
                                            if (fcmResponse!!.success ==0)
                                            {
                                                compositeDisposable.clear()
                                                Snackbar.make(requireView,context.getString(R.string.complete_trip_failed),Snackbar.LENGTH_SHORT).show()
                                            }
                                            else
                                            {
                                                Snackbar.make(requireView,context.getString(R.string.complete_trip_success),Snackbar.LENGTH_LONG).show()
                                            }
                                        },
                                            {t: Throwable? ->

                                                compositeDisposable.clear()!!
                                                Snackbar.make(requireView,t!!.message!!,Snackbar.LENGTH_SHORT).show()

                                            }))
                            }
                            else
                            {
                                compositeDisposable.clear()
                                Snackbar.make(requireView,context.getString(R.string.token_not_found),Snackbar.LENGTH_SHORT).show()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(requireView,error.message,Snackbar.LENGTH_SHORT).show()
                        }

                    })


    }

}