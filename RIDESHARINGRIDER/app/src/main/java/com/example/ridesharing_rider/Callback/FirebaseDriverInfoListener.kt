package com.example.ridesharing_rider.Callback

import com.example.ridesharing_rider.Model.DriverGeoModel

interface FirebaseDriverInfoListener{

fun onDriverInfoLoadSuccess (driverGeoModel: DriverGeoModel?)
}