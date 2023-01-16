package com.example.ridesharing_rider.Remote

import com.example.ridesharing_rider.Model.FCMResponse
import com.example.ridesharing_rider.Model.FCMSendData
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface IFCMService {

    @Headers(
        "Content-Type:application/json",
        "Authorization:key=AAAAiRRbpw4:APA91bEJISim7YdVcxFraEdZ5JKuAVHDWmzx5s3-z8Nv8E7TU15VJtV8ashukEXw2oHFGU6z9H2uyHvoB1Vxbq6fR4XcBsl-n6DEBaMAr5I38eYZV2rhv-A7W_bhTCVssp7e7VxPC6xu"
    )

    @POST("fcm/send")
    fun sendNotification(@Body body: FCMSendData?) : Observable<FCMResponse>
}