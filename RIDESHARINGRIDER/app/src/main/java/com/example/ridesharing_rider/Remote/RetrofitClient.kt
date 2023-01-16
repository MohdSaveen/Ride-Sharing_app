package com.example.ridesharing_rider.Remote

import io.reactivex.internal.fuseable.ScalarCallable
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

object RetrofitClient {

    val instance: Retrofit? = null
    get() = if (field == null)Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
        .build() else field
}