package com.example.ridesharing_rider

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.ridesharing_rider.Model.DriverGeoModel
import com.example.ridesharing_rider.Model.EventBus.*
import com.example.ridesharing_rider.Model.TripPlanModel
import com.example.ridesharing_rider.Remote.IGoogleAPI
import com.example.ridesharing_rider.Remote.RetrofitClient
import com.example.ridesharing_rider.databinding.ActivityRequestDriverBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.maps.android.ui.IconGenerator
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.lang.StringBuilder
import kotlin.random.Random
import kotlinx.android.synthetic.main.activity_request_driver.*
import kotlinx.android.synthetic.main.layout_confirm_pickup.*
import kotlinx.android.synthetic.main.layout_confirm_uber.*
import kotlinx.android.synthetic.main.layout_driver_info.*
import kotlinx.android.synthetic.main.layout_finding_your_driver.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject

class RequestDriverActivity : AppCompatActivity(), OnMapReadyCallback {

  private var driverOldPosition: String = ""
  private var handler: Handler? = null
  private var v = 0f
  private var lat = 0.0
  private var lng = 0.0
  private var index = 0
  private var next = 0
  private var start: LatLng? = null
  private var end: LatLng? = null

  // Spinning animation
  var animator: ValueAnimator? = null
  private val DESIRED_NUM_OF_SPINS = 5
  private val DESIRED_SECONDS_PER_ONE_FULL_360_SPIN = 40

  // Effect
  var lastUserCircle: Circle? = null
  var duration = 1000
  var lastPulseAnimator: ValueAnimator? = null

  private lateinit var mMap: GoogleMap
  private lateinit var txt_origin: TextView

  private var selectedPlaceEvent: SelectedPlaceEvent? = null

  private lateinit var mapFragment: SupportMapFragment

  // Routes
  private val compositeDisposable = CompositeDisposable()
  private lateinit var iGoogleAPI: IGoogleAPI
  private var blackPolyLine: Polyline? = null
  private var greyPolyline: Polyline? = null
  private var polylineOptions: PolylineOptions? = null
  private var blackPolylineOptions: PolylineOptions? = null
  private var polylineList: ArrayList<LatLng>? = null
  private var originMarker: Marker? = null
  private var destinationMarker: Marker? = null

  private var lastDriverCall: DriverGeoModel? = null

  override fun onStart() {
    if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    super.onStart()
  }

  override fun onStop() {
    compositeDisposable.clear()
    if (EventBus.getDefault().hasSubscriberForEvent(SelectedPlaceEvent::class.java))
        EventBus.getDefault().removeStickyEvent(SelectedPlaceEvent::class.java)
    if (EventBus.getDefault().hasSubscriberForEvent(DeclineRequestFromDriver::class.java))
        EventBus.getDefault().removeStickyEvent(DeclineRequestFromDriver::class.java)
    if (EventBus.getDefault().hasSubscriberForEvent(DriverAcceptTripEvent::class.java))
        EventBus.getDefault().removeStickyEvent(DriverAcceptTripEvent::class.java)
    if (EventBus.getDefault()
        .hasSubscriberForEvent(DeclineRequestAndRemoveTripFromDriver::class.java))
        EventBus.getDefault().removeStickyEvent(DeclineRequestAndRemoveTripFromDriver::class.java)
    if (EventBus.getDefault().hasSubscriberForEvent(DriverCompleteTripEvent::class.java))
        EventBus.getDefault().removeStickyEvent(DriverCompleteTripEvent::class.java)
    EventBus.getDefault().unregister(this)
    super.onStop()
  }
  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onDriverCompleteTripEvent(event: DriverCompleteTripEvent) {
    Common.showNotification(
        this,
        Random.nextInt(),
        "Thank you",
        "Your trip" + event.tripId + "has been completed",
        null)
    finish()
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onDriverAcceptTripEvent(event: DriverAcceptTripEvent) {
    FirebaseDatabase.getInstance()
        .getReference(Common.TRIP)
        .child(event.tripId)
        .addListenerForSingleValueEvent(
            object : ValueEventListener {
              override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {

                  val tripPlanModel = snapshot.getValue(TripPlanModel::class.java)
                  mMap.clear()
                  fill_maps.visibility = View.GONE
                  if (animator != null) animator!!.end()
                  val cameraPos =
                      CameraPosition.Builder()
                          .target(mMap.cameraPosition.target)
                          .tilt(0f)
                          .zoom(mMap.cameraPosition.zoom)
                          .build()
                  mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

                  val driverLocation =
                      StringBuilder()
                          .append(tripPlanModel!!.currentLat)
                          .append(",")
                          .append(tripPlanModel.currentLng)
                          .toString()

                  compositeDisposable.add(
                      iGoogleAPI
                          .getDirections(
                              "driving",
                              "less_driving",
                              tripPlanModel.origin,
                              driverLocation,
                              getString(R.string.google_api_key))!!
                          .subscribeOn(Schedulers.io())
                          .observeOn(AndroidSchedulers.mainThread())
                          .subscribe { returnResult ->
                            var blackPolylineOptions: PolylineOptions? = null
                            var polylineList: List<LatLng?>? = null
                            var blackPolyline: Polyline? = null
                            try {
                              Log.d("driverinfo", "onDataChange: enter in try")
                              val jsonObject = JSONObject(returnResult)
                              //
                              // Toast.makeText(this,"${jsonObject.length()}",Toast.LENGTH_LONG).show()
                              val jsonArray = jsonObject.getJSONArray("routes")
                              for (i in 0 until jsonArray.length()) {
                                val route = jsonArray.getJSONObject(i)
                                val poly = route.getJSONObject("overview_polyline")
                                val polyline = poly.getString("points")
                                polylineList = Common.decodePoly(polyline)
                              }

                              blackPolylineOptions = PolylineOptions()
                              blackPolylineOptions!!.color(Color.BLACK)
                              blackPolylineOptions!!.width(5f)
                              blackPolylineOptions!!.startCap(SquareCap())
                              blackPolylineOptions!!.jointType(JointType.ROUND)
                              blackPolylineOptions!!.addAll(polylineList!!)
                              blackPolyline = mMap.addPolyline(blackPolylineOptions!!)

                              // add car icon for origin
                              val objects = jsonArray.getJSONObject(0)
                              val legs = objects.getJSONArray("legs")
                              val legsObject = legs.getJSONObject(0)

                              val time = legsObject.getJSONObject("duration")
                              val duration = time.getString("text")

                              val origin =
                                  LatLng(
                                      tripPlanModel.origin!!.split(",").get(0).toDouble(),
                                      tripPlanModel.origin!!.split(",").get(1).toDouble())
                              val destination =
                                  LatLng(tripPlanModel.currentLat, tripPlanModel.currentLng)

                              val latlngBound =
                                  LatLngBounds.builder()
                                      .include(origin)
                                      .include(destination)
                                      .build()

                              addPickupMarkerWithDuration(duration, origin)
                              addDriverMarker(destination)

                              Log.d("latlangbound", "onDataChange: we will use this as per checking `")
                              mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latlngBound, 160))
                              mMap.moveCamera(
                                  CameraUpdateFactory.zoomTo(mMap.cameraPosition.zoom - 1))

                              initDriverForMoving(event.tripId, tripPlanModel)
                              Log.d("driverinfo", "onDataChange: above glide")
                              // Load driver avatar
                              Glide.with(this@RequestDriverActivity)
                                  .load(tripPlanModel.driverInfoModel?.avatar)
                                  .into(img_driver)
                              driver_info_layout.visibility = View.VISIBLE
                              Log.d(
                                  "driverinfo",
                                  "onDataChange: ${tripPlanModel.driverInfoModel?.firstName}")
                              findViewById<TextView>(R.id.txt_driver_name).text =
                                  tripPlanModel.driverInfoModel?.firstName

                              confirm_uber_layout.visibility = View.GONE
                              confirm_pickup_layout.visibility = View.GONE
                            } catch (e: java.lang.Exception) {
                              Toast.makeText(
                                      this@RequestDriverActivity, e.message!!, Toast.LENGTH_SHORT)
                                  .show()
                            }
                          })
                } else {
                  Snackbar.make(
                          main_layout,
                          getString(R.string.trip_not_found) + event.tripId,
                          Snackbar.LENGTH_SHORT)
                      .show()
                }
              }

              override fun onCancelled(error: DatabaseError) {
                Snackbar.make(main_layout, error.message!!, Snackbar.LENGTH_SHORT).show()
              }
            })
  }

  private fun initDriverForMoving(tripId: String, tripPlanModel: TripPlanModel) {
    driverOldPosition =
        StringBuilder()
            .append(tripPlanModel.currentLat)
            .append(",")
            .append(tripPlanModel.currentLng)
            .toString()

    FirebaseDatabase.getInstance()
        .getReference(Common.TRIP)
        .child(tripId)
        .addValueEventListener(
            object : ValueEventListener {
              override fun onDataChange(snapshot: DataSnapshot) {

                val newData = snapshot.getValue(TripPlanModel::class.java)
                if (newData != null) {
                  val driverNewPosition =
                      StringBuilder()
                          .append(newData!!.currentLat)
                          .append(",")
                          .append(newData.currentLng)
                          .toString()
                  if (!driverOldPosition.equals(driverNewPosition))
                      moveMarkerAnimation(destinationMarker!!, driverOldPosition, driverNewPosition)
                }
              }

              override fun onCancelled(error: DatabaseError) {
                Snackbar.make(main_layout, error.message, Snackbar.LENGTH_SHORT).show()
              }
            })
  }

  private fun moveMarkerAnimation(marker: Marker, from: String, to: String) {

    compositeDisposable.add(
        iGoogleAPI
            .getDirections(
                "driving", "less_driving", from, to, getString(R.string.google_api_key))!!
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { returnResult ->
              try {
                val jsonObject = JSONObject(returnResult)
                //
                // Toast.makeText(this,"${jsonObject.length()}",Toast.LENGTH_LONG).show()
                val jsonArray = jsonObject.getJSONArray("routes")
                for (i in 0 until jsonArray.length()) {
                  val route = jsonArray.getJSONObject(i)
                  val poly = route.getJSONObject("overview_polyline")
                  val polyline = poly.getString("points")
                  polylineList = Common.decodePoly(polyline)
                }

                blackPolylineOptions = PolylineOptions()
                blackPolylineOptions!!.color(Color.BLACK)
                blackPolylineOptions!!.width(5f)
                blackPolylineOptions!!.startCap(SquareCap())
                blackPolylineOptions!!.jointType(JointType.ROUND)
                blackPolylineOptions!!.addAll(polylineList!!)
                blackPolyLine = mMap.addPolyline(blackPolylineOptions!!)

                // add car icon for origin
                val objects = jsonArray.getJSONObject(0)
                val legs = objects.getJSONArray("legs")
                val legsObject = legs.getJSONObject(0)

                val time = legsObject.getJSONObject("duration")
                val duration = time.getString("text")

                val bitmap = Common.createIconWithDuration(this@RequestDriverActivity, duration)
                originMarker!!.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap!!))

                // Moving
                val runnable =
                    object : Runnable {
                      override fun run() {
                        if (index < polylineList!!.size - 2) {

                          index++
                          next = index + 1
                          start = polylineList!![index]
                          end = polylineList!![next]
                        }
                        val valueAnimator = ValueAnimator.ofInt(0, 1)
                        valueAnimator.duration = 1500
                        valueAnimator.interpolator = LinearInterpolator()
                        valueAnimator.addUpdateListener { valueAnimatorNew ->
                          v = valueAnimatorNew.animatedFraction
                          lat = v * end!!.latitude + (1 - v) * start!!.latitude
                          lng = v * end!!.longitude + (1 - v) * end!!.longitude
                          val newPos = LatLng(lat, lng)
                          marker.position = newPos
                          marker.setAnchor(0.5f, 0.5f)
                          marker.rotation = Common.getBearing(start!!, newPos)
                          mMap.moveCamera(CameraUpdateFactory.newLatLng(newPos))
                        }
                        valueAnimator.start()
                        if (index < polylineList!!.size - 2) handler!!.postDelayed(this, 1500)
                      }
                    }
                handler = Handler()
                index = -1
                next = 1
                handler!!.postDelayed(runnable, 1500)
                driverOldPosition = to // set new driver position
              } catch (e: java.lang.Exception) {
                Toast.makeText(this@RequestDriverActivity, e.message!!, Toast.LENGTH_SHORT).show()
              }
            })
  }

  private fun addDriverMarker(destination: LatLng) {

    destinationMarker =
        mMap.addMarker(
            MarkerOptions()
                .position(destination)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)))
  }

  private fun addPickupMarkerWithDuration(duration: String, origin: LatLng) {
    val icon = Common.createIconWithDuration(this@RequestDriverActivity, duration)
    icon?.let {
      originMarker =
          mMap.addMarker(
              MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(icon)).position(origin))
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onDeclineReceived(event: DeclineRequestFromDriver) {

    if (lastDriverCall != null) {

      Common.driversFound.get(lastDriverCall!!.key)!!.isDecline = true
      findNearbyDriver(selectedPlaceEvent!!)
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onDeclineAndRemoveTripReceive(event: DeclineRequestAndRemoveTripFromDriver) {

    if (lastDriverCall != null) {

      if (Common.driversFound.get(lastDriverCall!!.key) != null)
          Common.driversFound.get(lastDriverCall!!.key)!!.isDecline = true
      finish()
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onSelectPlaceEvent(event: SelectedPlaceEvent) {
    selectedPlaceEvent = event
  }

  private lateinit var binding: ActivityRequestDriverBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityRequestDriverBinding.inflate(layoutInflater)
    setContentView(binding.root)

    init()

    // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)
  }

  private fun init() {

    iGoogleAPI = RetrofitClient.instance!!.create(IGoogleAPI::class.java)

    // event
    btn_confirm_uber.setOnClickListener {
      confirm_pickup_layout.visibility = View.VISIBLE
      confirm_uber_layout.visibility = View.GONE

      setDataPickup()
    }

    btn_confirm_pickup.setOnClickListener {
      if (mMap == null) return@setOnClickListener
      if (selectedPlaceEvent == null) return@setOnClickListener

      // clear map
      mMap.clear()
      // tilt
      val cameraPos =
          CameraPosition.Builder().target(selectedPlaceEvent!!.origin).tilt(45f).zoom(16f).build()
      mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPos))

      // start animation
      addMarkerPulseAnimation()
    }
  }

  private fun addMarkerPulseAnimation() {
    confirm_pickup_layout.visibility = View.GONE
    fill_maps.visibility = View.VISIBLE
    finding_your_ride_layout.visibility = View.VISIBLE

    originMarker =
        mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.defaultMarker())
                .position(selectedPlaceEvent!!.origin))

    addPulsatingEffect(selectedPlaceEvent!!)
  }

  private fun addPulsatingEffect(selectedPlaceEvent: SelectedPlaceEvent?) {
    Log.d("checking", "pulse effect method enter")

    if (lastPulseAnimator != null) lastPulseAnimator!!.cancel()
    if (lastPulseAnimator != null) lastUserCircle!!.center = selectedPlaceEvent!!.origin
    lastPulseAnimator =
        Common.ValueAnimate(
            duration,
            object : ValueAnimator.AnimatorUpdateListener {
              override fun onAnimationUpdate(animation: ValueAnimator?) {
                if (lastUserCircle != null)
                    lastUserCircle!!.radius = animation!!.animatedValue.toString().toDouble()
                else {
                  Log.d("checking", "onanimationupdate after if else")
                  lastUserCircle =
                      mMap.addCircle(
                          CircleOptions()
                              .center(selectedPlaceEvent!!.origin)
                              .radius(animation!!.animatedValue.toString().toDouble())
                              .strokeColor(Color.WHITE)
                              .fillColor(
                                  ContextCompat.getColor(
                                      this@RequestDriverActivity, R.color.map_darker)))
                }
              }
            })
    // start rotating camera
    Log.d("checking", "try to rotate camera ")
    startMapCameraSpinningAnimation(selectedPlaceEvent)
  }

  private fun startMapCameraSpinningAnimation(selectedPlaceEvent: SelectedPlaceEvent?) {

    if (animator != null) animator!!.cancel()
    animator = ValueAnimator.ofFloat(0f, (DESIRED_NUM_OF_SPINS * 360).toFloat())
    animator!!.duration =
        (DESIRED_NUM_OF_SPINS * DESIRED_SECONDS_PER_ONE_FULL_360_SPIN * 1000).toLong()
    animator!!.interpolator = LinearInterpolator()
    animator!!.startDelay = (100)
    animator!!.addUpdateListener {
      val newBearingValue = it.animatedValue as Float
      mMap.moveCamera(
          CameraUpdateFactory.newCameraPosition(
              CameraPosition.Builder()
                  .target(selectedPlaceEvent!!.origin)
                  .zoom(16f)
                  .tilt(45f)
                  .bearing(newBearingValue)
                  .build()))
    }
    animator!!.start()

    findNearbyDriver(selectedPlaceEvent)
  }

  private fun findNearbyDriver(selectedPlaceEvent: SelectedPlaceEvent?) {
    if (Common.driversFound.size > 0) {

      var min = 0f
      var foundDriver: DriverGeoModel? = null // default found driver is first driver
      val currentRiderLocation = Location("")
      currentRiderLocation.latitude = selectedPlaceEvent!!.origin!!.latitude
      currentRiderLocation.longitude = selectedPlaceEvent.origin!!.longitude

      for (key in Common.driversFound.keys) {

        Log.d("checking", "loop entered")
        val driverLocation = Location("")
        driverLocation.latitude = Common.driversFound[key]!!.geoLocation!!.latitude
        driverLocation.longitude = Common.driversFound[key]!!.geoLocation!!.longitude

        // First , init min value and found driver if first driver in list
        if (min == 0f) {
          min = driverLocation.distanceTo(currentRiderLocation)
          if (!Common.driversFound[key]!!.isDecline) {
            foundDriver = Common.driversFound[key]
            break // exit loop because we already found driver
          } else {
            continue // if we decline before , just skip and continues
          }
        } else if (driverLocation.distanceTo(currentRiderLocation) < min) {
          min = driverLocation.distanceTo(currentRiderLocation)
          if (!Common.driversFound[key]!!.isDecline) {
            foundDriver = Common.driversFound[key]
            break // exit loop because we already found driver
          } else {
            continue // if we decline before , just skip and continues
          }
        }
      }
      //      Snackbar.make(main_layout, StringBuilder("Founf Driver: ")
      //        .append(foundDriver!!.driverInfoModel!!.phoneNumber),Snackbar.LENGTH_SHORT).show()

      if (foundDriver != null) {
        UserUtils.sendRequestToDriver(
            this@RequestDriverActivity, main_layout, foundDriver, selectedPlaceEvent!!)
        lastDriverCall = foundDriver
      } else {
        Toast.makeText(this, getString(R.string.no_driver_accept), Toast.LENGTH_SHORT).show()
        lastDriverCall = null
        finish()
      }
    } else {
      Snackbar.make(main_layout, getString(R.string.drivers_not_found), Snackbar.LENGTH_SHORT)
          .show()
      lastDriverCall = null
      finish()
    }
  }

  override fun onDestroy() {
    if (animator != null) animator!!.end()
    super.onDestroy()
  }

  private fun setDataPickup() {

    txt_address_pickup.text = if (txt_origin != null) txt_origin.text else "None"
    mMap.clear()
    addPickupMarker()
  }

  private fun addPickupMarker() {
    val view = layoutInflater.inflate(R.layout.pickup_info_windows, null)

    val generator = IconGenerator(this)
    generator.setContentView(view)
    generator.setBackground(ColorDrawable(Color.TRANSPARENT))
    val icon = generator.makeIcon()

    originMarker =
        mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.origin))
  }
  override fun onMapReady(googleMap: GoogleMap) {
    mMap = googleMap

    drawPath(selectedPlaceEvent)

    try {
      val success =
          googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.uber_maps_style))
      if (!success) Log.e("Shown_Error", "style parsing error")

    } catch (e: Resources.NotFoundException) {
      Snackbar.make(mapFragment.requireView(), e.message!!, Snackbar.LENGTH_SHORT).show()
    }
  }

  private fun drawPath(selectedPlaceEvent: SelectedPlaceEvent?) {

    // Request API
    compositeDisposable.add(
        iGoogleAPI!!
            .getDirections(
                "driving",
                "less_driving",
                selectedPlaceEvent?.originString,
                selectedPlaceEvent?.destinationString,
                getString(R.string.google_api_key))!!
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe() {
              Log.d("API_RETURN", it)
              try {
                val jsonObject = JSONObject(it)
                //
                // Toast.makeText(this,"${jsonObject.length()}",Toast.LENGTH_LONG).show()
                val jsonArray = jsonObject.getJSONArray("routes")
                for (i in 0 until jsonArray.length()) {
                  val route = jsonArray.getJSONObject(i)
                  val poly = route.getJSONObject("overview_polyline")
                  val polyline = poly.getString("points")
                  polylineList = Common.decodePoly(polyline)
                }

                polylineOptions = PolylineOptions()
                polylineOptions!!.color(Color.GRAY)
                polylineOptions!!.width(12f)
                polylineOptions!!.startCap(SquareCap())
                polylineOptions!!.jointType(JointType.ROUND)
                polylineOptions!!.addAll(polylineList!!)
                greyPolyline = mMap.addPolyline(polylineOptions!!)

                blackPolylineOptions = PolylineOptions()
                blackPolylineOptions!!.color(Color.BLACK)
                blackPolylineOptions!!.width(5f)
                blackPolylineOptions!!.startCap(SquareCap())
                blackPolylineOptions!!.jointType(JointType.ROUND)
                blackPolylineOptions!!.addAll(polylineList!!)
                blackPolyLine = mMap.addPolyline(blackPolylineOptions!!)

                // Animator

                val valueAnimator = ValueAnimator.ofInt(0, 100)
                valueAnimator.duration = 1100
                valueAnimator.repeatCount = ValueAnimator.INFINITE
                valueAnimator.interpolator = LinearInterpolator()
                valueAnimator.addUpdateListener {
                  val points = greyPolyline!!.points
                  val percentValue = it.animatedValue.toString().toInt()
                  val size = points.size
                  val newpoints = (size * (percentValue / 100.0f)).toInt()
                  val p = points.subList(0, newpoints)
                  blackPolyLine!!.points = p
                }
                valueAnimator.start()

                val latlngBound =
                    LatLngBounds.builder()
                        .include(selectedPlaceEvent!!.origin)
                        .include(selectedPlaceEvent!!.destination)
                        .build()

                // add car icon for origin
                val objects = jsonArray.getJSONObject(0)
                val legs = objects.getJSONArray("legs")
                val legsObject = legs.getJSONObject(0)

                val time = legsObject.getJSONObject("duration")
                val duration = time.getString("text")

                val start_address = legsObject.getString("start_address")
                val end_address = legsObject.getString("end_address")

                addOriginMarker(duration, start_address)

                addDestinationMarker(end_address)

                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latlngBound, 160))
                mMap.moveCamera(CameraUpdateFactory.zoomTo(mMap.cameraPosition.zoom - 1))
              } catch (e: java.lang.Exception) {

                //                    Toast.makeText(this,e.message!!,Toast.LENGTH_SHORT).show()
              }
            })
  }

  private fun addDestinationMarker(endAddress: String) {

    val view = layoutInflater.inflate(R.layout.destination_info_window, null)

    val txt_destination = view.findViewById<TextView>(R.id.txt_destination)
    txt_destination.text = Common.formatAddress(endAddress)

    val generator = IconGenerator(this)
    generator.setContentView(view)
    generator.setBackground(ColorDrawable(Color.TRANSPARENT))
    val icon = generator.makeIcon()

    destinationMarker =
        mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.destination))
  }

  private fun addOriginMarker(duration: String, startAddress: String) {

    val view = layoutInflater.inflate(R.layout.origin_info_windows, null)

    val txt_time = view.findViewById<TextView>(R.id.txt_time)
    txt_origin = view.findViewById<TextView>(R.id.txt_origin)

    txt_time.text = Common.formatDuration(duration)
    txt_origin.text = Common.formatAddress(startAddress)

    val generator = IconGenerator(this)
    generator.setContentView(view)
    generator.setBackground(ColorDrawable(Color.TRANSPARENT))
    val icon = generator.makeIcon()

    originMarker =
        mMap.addMarker(
            MarkerOptions()
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .position(selectedPlaceEvent!!.origin))
  }
}
