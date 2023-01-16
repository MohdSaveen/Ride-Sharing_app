package com.example.ridesharing_rider.ui.home

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.ridesharing_rider.Callback.FirebaseDriverInfoListener
import com.example.ridesharing_rider.Callback.FirebaseFailedListener
import com.example.ridesharing_rider.Common
import com.example.ridesharing_rider.Model.AnimationModel
import com.example.ridesharing_rider.Model.DriverGeoModel
import com.example.ridesharing_rider.Model.DriverInfoModel
import com.example.ridesharing_rider.Model.EventBus.SelectedPlaceEvent
import com.example.ridesharing_rider.Model.GeoQueryModel
import com.example.ridesharing_rider.R
import com.example.ridesharing_rider.Remote.IGoogleAPI
import com.example.ridesharing_rider.Remote.RetrofitClient
import com.example.ridesharing_rider.RequestDriverActivity
import com.example.ridesharing_rider.databinding.FragmentHomeBinding
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_home.view.*
import org.greenrobot.eventbus.EventBus
import org.json.JSONObject
import java.io.IOException
import java.util.*


class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener,
    FirebaseFailedListener {

    private var isNextLaunch:Boolean = false
  private lateinit var mMap: GoogleMap
  private var _binding: FragmentHomeBinding? = null
  private lateinit var mapFragment: SupportMapFragment
  private lateinit var slidingUpPanelLayout: SlidingUpPanelLayout
  private lateinit var txt_welcome: TextView
  private lateinit var autocompleteSupportFragment: AutocompleteSupportFragment
  private  var locationRequest: LocationRequest?= null
  private  var locationCallback: LocationCallback?= null
  private  var mFusedLocationClient: FusedLocationProviderClient?= null


  // Load Driver
  var distance = 1.0
  val LIMIT_RANGE = 10.0
  var previouslocation: Location? = null
  var currentlocation: Location? = null
  var firstTime = true
  // Listener
  lateinit var iFirebaseDriverInfolistener: FirebaseDriverInfoListener
 lateinit var iFirebaseFailedListener: FirebaseFailedListener

  var cityName = ""

    val compositeDisposable = CompositeDisposable()
    lateinit var iGoogleAPI : IGoogleAPI


  // This property is only valid between onCreateView and
  // onDestroyView.
  private val binding
    get() = _binding!!

    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()

    }

    override fun onResume() {
        super.onResume()
        if (isNextLaunch)
            loadAvailableDrivers()
        else
            isNextLaunch = true
    }

  override fun onDestroy() {
    mFusedLocationClient!!.removeLocationUpdates(locationCallback!!)
    super.onDestroy()
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View {
    val homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

    _binding = FragmentHomeBinding.inflate(inflater, container, false)
    val root: View = binding.root




    mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
    mapFragment.getMapAsync(this)

      //permission check


      initViews(root)

      init()
    return root
  }

    private fun initViews(root: View) {
        Common.setWelcomeMessage(root.txt_welcome)
    }


    private fun init() {

        Places.initialize(requireContext(),getString(R.string.google_api_key))
        autocompleteSupportFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ID,
        Place.Field.ADDRESS,
        Place.Field.LAT_LNG,
        Place.Field.NAME))
        autocompleteSupportFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener{
            override fun onError(p0: Status) {
//                Snackbar.make(requireView(),""+p0.statusMessage!!,Snackbar.LENGTH_SHORT).show()
            }

            override fun onPlaceSelected(p0: Place) {
//                Snackbar.make(requireView(),""+p0.latLng!!,Snackbar.LENGTH_SHORT).show()

                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Snackbar.make(mapFragment.requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()

                    return
                }
                mFusedLocationClient!!
                    .lastLocation.addOnSuccessListener {
                        val origin = LatLng(it.latitude,it.longitude)
                        val destination = LatLng(p0.latLng!!.latitude,p0.latLng!!.longitude)

                        startActivity(Intent(requireContext(),RequestDriverActivity::class.java))
                        EventBus.getDefault().postSticky(SelectedPlaceEvent(origin,destination,p0.address))
                    }

            }

        })

      iGoogleAPI = RetrofitClient.instance!!.create(IGoogleAPI::class.java)

      iFirebaseDriverInfolistener = this
        iFirebaseFailedListener = this

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Snackbar.make(mapFragment.requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()

            return
        }

           buildLocationRequest()
        buildLocationCallback()
        updateLocation()

      loadAvailableDrivers()
  }

    private fun updateLocation() {
        if (mFusedLocationClient == null){
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {

//        Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
                return
            }
            mFusedLocationClient!!.requestLocationUpdates(
                locationRequest!!, locationCallback!!, Looper.myLooper())

        }
    }

    private fun buildLocationCallback() {
        if (locationCallback == null){
            locationCallback =
                object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        super.onLocationResult(locationResult)

                        val newPos =
                            LatLng(
                                locationResult.lastLocation!!.latitude, locationResult.lastLocation!!.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 10f))

                        // If use has change Location, calculate and Load driver again
                        if (firstTime) {
                            previouslocation = locationResult.lastLocation
                            currentlocation = locationResult.lastLocation

                            setRestrictPlacesInCountry(locationResult!!.lastLocation)
                            firstTime = false
                        } else {

                            previouslocation = currentlocation
                            currentlocation = locationResult.lastLocation
                        }
                        if (previouslocation!!.distanceTo(currentlocation) / 1000 <= LIMIT_RANGE) {
                            loadAvailableDrivers()
                        }
                    }
                }
        }
    }

    private fun buildLocationRequest() {
        if (locationRequest == null){
            locationRequest = LocationRequest()
            locationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            locationRequest!!.setFastestInterval(3000)
            locationRequest!!.interval = 3000
            locationRequest!!.setSmallestDisplacement(10f)
        }
    }

    private fun setRestrictPlacesInCountry(location: Location?) {
        try {
            val geoCoder = Geocoder(requireContext(), Locale.getDefault())
            val addressList = geoCoder.getFromLocation(location!!.latitude,location.longitude,1)
            if (addressList.size > 0)
                autocompleteSupportFragment.setCountry(addressList[0].countryCode)
        }catch (e:IOException){
            e.printStackTrace()
        }
    }

    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
//            Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
            return
        }
        mFusedLocationClient!!.lastLocation
            .addOnFailureListener { e->
                Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_SHORT).show()
            }.addOnSuccessListener {

                //load all drivers in city
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                var addressList :List<Address> = ArrayList()
                try {
                    addressList = geoCoder.getFromLocation(it.latitude,it.longitude,1)
                    if (addressList.isNotEmpty())
                    cityName= addressList[0].locality

                    if (!TextUtils.isEmpty(cityName)){
                    val driver_location_ref = FirebaseDatabase.getInstance()
                        .getReference(Common.DRIVER_LOCATION_REFRENCE)
                        .child(cityName)

                    val gf = GeoFire(driver_location_ref)
                    val geoQuery = gf.queryAtLocation(GeoLocation(it.latitude,it.longitude),distance)
                    geoQuery.removeAllListeners()

                    geoQuery.addGeoQueryEventListener(object : GeoQueryEventListener{
                        override fun onKeyEntered(key: String?, location: GeoLocation?) {
//                            Common.driversFound.add(DriverGeoModel(key!!, location!!))
                            Common.driversFound[key!!] = DriverGeoModel(key,location)
                        }

                        override fun onKeyExited(key: String?) {
                        }

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {
                        }

                        override fun onGeoQueryReady() {
                            if (distance <= LIMIT_RANGE){
                                distance++
                                loadAvailableDrivers()
                            }else
                            {
                                distance = 0.0
                                addDriverMarker()
                            }
                        }

                        override fun onGeoQueryError(error: DatabaseError?) {
                            Snackbar.make(requireView(),error!!.message!!,Snackbar.LENGTH_SHORT).show()
                        }

                    })

                    driver_location_ref.addChildEventListener(object : ChildEventListener{
                        override fun onChildAdded(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                            //have new driver
                            val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                            val geoLocation = GeoLocation(geoQueryModel!!.l!![0],geoQueryModel.l!![1]) //l= letter 'l' lower case
                            val driverGeoModel = DriverGeoModel(snapshot.key,geoLocation)
                            val newDriverLocation = Location("")
                            newDriverLocation.latitude = geoLocation.latitude
                            newDriverLocation.longitude = geoLocation.longitude
                            val newDistance= it.distanceTo(newDriverLocation)/1000 // in km
                            if (newDistance <= LIMIT_RANGE)
                                findDriverByKey(driverGeoModel)
                        }

                        override fun onChildChanged(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                        }

                        override fun onChildRemoved(snapshot: DataSnapshot) {
                        }

                        override fun onChildMoved(
                            snapshot: DataSnapshot,
                            previousChildName: String?
                        ) {
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(requireView(),error.message,Snackbar.LENGTH_SHORT).show()

                        }

                    })
                }
                else{
                        Snackbar.make(requireView(),getString(R.string.city_name_not_found),Snackbar.LENGTH_SHORT).show()
                    }
                }catch (e: IOException)
                {
                    Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    private fun addDriverMarker() {
        if (Common.driversFound.size > 0){
            Observable.fromIterable(Common.driversFound.keys)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {key :String? ->
                    findDriverByKey(Common.driversFound[key]!!)
                },
                    {
                          Snackbar.make(requireView(), it.message!!,Snackbar.LENGTH_SHORT).show()
                    })
        }else{
            Snackbar.make(requireView(), getString(R.string.drivers_not_found),Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel) {
        FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel.key!!)
            .addListenerForSingleValueEvent(object : ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()){
                        driverGeoModel.driverInfoModel = (snapshot.getValue(DriverInfoModel::class.java))
                        Common.driversFound[driverGeoModel.key!!]!!.driverInfoModel = (snapshot.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverInfolistener.onDriverInfoLoadSuccess(driverGeoModel)
                    }else{
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found)+driverGeoModel.key)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }

            })

    }

    override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  override fun onMapReady(p0: GoogleMap) {
    mMap = p0

    if (ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
        Log.d("asdfasdf", "permission call made")
        Dexter.withContext(requireContext())
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) { /* ... */
                    onMapReady(mMap)
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) { /* ... */
                    Toast.makeText(context!!,"Permission"+response.permissionName+"was denied",Toast.LENGTH_SHORT).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
                ) { /* ... */
                }
            }).check()
      return
    }

    mMap.isMyLocationEnabled = true
    mMap.uiSettings.isMyLocationButtonEnabled = true
      mMap.uiSettings.isZoomControlsEnabled = true
    mMap.setOnMapClickListener {
      mFusedLocationClient!!.lastLocation
          .addOnFailureListener {
            Toast.makeText(context, "location showing fail", Toast.LENGTH_SHORT).show()
          }
          .addOnSuccessListener {
            val userLatLang = LatLng(it.latitude, it.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLang, 10f))
          }
    }
    // Layout

    val locationButton =
        (mapFragment.requireView().findViewById<View>("1".toInt())!!.parent!! as View).findViewById<
            View>("2".toInt())
    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
    params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
    params.bottomMargin = 250 //move to see zoom control

      buildLocationRequest()
      buildLocationCallback()
      updateLocation()

    try {
      val success =
          p0.setMapStyle(
              context?.let {
                  MapStyleOptions.loadRawResourceStyle(it, R.raw.uber_maps_style) })
      if (!success) Log.e("Shown_Error", "style parsing error")
    } catch (e: Resources.NotFoundException) {
      e.message?.let { Log.e("Shown_Error_exception", it) }
    }
  }

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        // if already have marker with this key, doesn't set it again
        if (!Common.markerList.containsKey(driverGeoModel!!.key))
            mMap.addMarker(MarkerOptions()
                .position
                    (LatLng(driverGeoModel!!.geoLocation!!.latitude,driverGeoModel!!.geoLocation!!.longitude))
                .flat(true)
                .title(Common.buildName(
                    driverGeoModel.driverInfoModel!!.firstName!!
                    ,
                    driverGeoModel.driverInfoModel!!.lastName!!))
                .snippet(driverGeoModel.driverInfoModel!!.phoneNumber.toString())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))

            )?.let {
                Common.markerList.put(driverGeoModel!!.key!!,
                    it
                )
            }
        if(!TextUtils.isEmpty(cityName) )
        {
        val driverLocation = FirebaseDatabase.getInstance()
            .getReference(Common.DRIVER_LOCATION_REFRENCE)
            .child(cityName)
            .child(driverGeoModel.key!!)
            driverLocation.addValueEventListener(object:ValueEventListener{
                override fun onCancelled(p0: DatabaseError) {
                    Snackbar.make(requireView(),p0.message,Snackbar.LENGTH_SHORT).show()
                    }
                    override fun onDataChange(p0: DataSnapshot) {
                        if (!p0.hasChildren()){
                            if (Common.markerList.get(driverGeoModel!!.key!!) !=null)
                            {
                                val marker= Common.markerList.get(driverGeoModel!!.key!!)
                                marker!!.remove()
                                Common.markerList.remove(driverGeoModel!!.key!!)//Remove marker information
                                Common.driversSubscribe.remove(driverGeoModel.key!!)// Remove driver information

                                //fix error
                                //when driver decline request, they can accept again if they stop and open app again
                                if(Common.driversFound !=null && Common.driversFound[driverGeoModel.key] !=null)
                                    Common.driversFound.remove(driverGeoModel.key!!)

                                driverLocation.removeEventListener(this)
                            }
                        }
                        else{
                            if (Common.markerList.get(driverGeoModel!!.key!!) != null)
                            {
                                val geoQueryModel = p0!!. getValue(GeoQueryModel::class.java)
                                val animationModel = AnimationModel(false,geoQueryModel!!)
                                if (Common.driversSubscribe.get(driverGeoModel.key!!) != null)
                                {
                                    val marker = Common.markerList.get(driverGeoModel.key!!)
                                    val oldPosition = Common.driversSubscribe.get(driverGeoModel.key!!)

                                    val from = StringBuilder()
                                        .append(oldPosition!!.geoQueryModel!!.l?.get(0))
                                        .append(",")
                                        .append(oldPosition!!.geoQueryModel!!.l?.get(1)).toString()

                                    val to =StringBuilder()
                                        .append(animationModel.geoQueryModel.l?.get(0))
                                        .append(",")
                                        .append(animationModel.geoQueryModel!!.l?.get(1)).toString()

                                    movemarkeranimation(driverGeoModel.key!!,animationModel,marker,from,to)
                                }
                                else{
                                    Common.driversSubscribe.put(driverGeoModel.key!!,animationModel)//first location
                                }
                            }
                        }

                    }
                })
        }
    }

    private fun movemarkeranimation(key: String, newData: AnimationModel, marker: Marker?, from: String?, to: String?) {

        if (!newData.isRun){

            //Request API
            compositeDisposable.add(iGoogleAPI.getDirections("driving",
            "less_driving",
            from,to,getString(R.string.google_api_key))
                !!.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(){
                    Log.d("API_RETURN",it)
                    try {
                        val jsonObject = JSONObject(it)
                        val jsonArray = jsonObject.getJSONArray("routes")
                        for(i in 0 until jsonArray.length())
                        {
                            val route = jsonArray.getJSONObject(i)
                            val poly = route.getJSONObject("overview_polyline")
                            val polyline= poly.getString("points")
                            newData.polylineList=Common.decodePoly(polyline)
                        }
                        //moving

                        newData.index = -1
                        newData.next = 1
                        val runnable = object :Runnable{
                            override fun run() {
                                if (newData.polylineList!= null && newData.polylineList!!.size > 1)
                                {
                                    if (newData.index < newData.polylineList!!.size -2)
                                    {
                                        newData.index++
                                        newData.next = newData.index+1
                                        newData.start = newData.polylineList!![newData.index]!!
                                        newData.end = newData.polylineList!![newData.index]!!
                                    }
                                    val valueAnimator = ValueAnimator.ofInt(0,1)
                                    valueAnimator.duration = 3000
                                    valueAnimator.interpolator = LinearInterpolator()
                                    valueAnimator.addUpdateListener { value ->
                                        newData.v= value.animatedFraction
                                        newData.lat = newData.v*newData.end!!.latitude + (1-newData.v)* newData.start!!.latitude
                                        newData.lng = newData.v*newData.end!!.longitude + (1-newData.v)*newData.start!!.longitude
                                        val newPos = LatLng(newData.lat,newData.lng)
                                        marker!!.position = newPos
                                        marker!!.setAnchor(0.5f,0.5f)
                                        marker!!.rotation = Common.getBearing(newData.start!!,newPos)
                                    }
                                    valueAnimator.start()
                                    if (newData.index< newData.polylineList!!.size - 2)
                                        newData.handler!!.postDelayed(this,1500)
                                    else if(newData.index < newData.polylineList!!.size -1 )
                                    {
                                        newData.isRun = false
                                        Common.driversSubscribe.put(key,newData)//update
                                    }
                                }
                            }
                        }

                        newData.handler!!.postDelayed(runnable,1500)

                    }catch (e:java.lang.Exception)
                    {
                        Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_SHORT).show()
                    }
                }
                )
        }

    }

    override fun onFirebaseFailed(message: String) {

    }


}
