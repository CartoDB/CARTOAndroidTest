package com.carto.androidtest

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.carto.androidtest.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.squareup.moshi.Moshi
import com.squareup.picasso.Picasso
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener,
    GoogleMap.OnMyLocationButtonClickListener {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val FAKE_LOCATION_TITLE = "Fake location"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: GoogleMap
    private lateinit var retrofit: Retrofit
    private lateinit var poisApi: PoisApi
    private lateinit var poisResponse: Call<PoisResponse<Poi>>
    private val pois = mutableListOf<Poi>()
    private var poiEndMarker: Poi? = null
    private val fakeLocation = LatLng(-33.3000802,149.0913524)
    private lateinit var fakeLocationMarker: MarkerOptions
    private lateinit var locationManager: LocationManager
    private var polyLine: Polyline? = null
    private var isRouteWithFakeLocation: Boolean? = null
    private lateinit var searchPoiAdapter: SearchPoiAdapter
    private var markerStartClicked: Marker? = null
    private var markerEndClicked: Marker? = null
    private val markersMap = mutableMapOf<String, Marker>()
    private var isRegisteredReceiver = false

    private val gpsStateChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            markersMap[FAKE_LOCATION_TITLE]?.isVisible = true
            drawFakeLocationIcon()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        initializeMap()
    }

    private fun initializeViews() {
        locationManager = (this.getSystemService(Context.LOCATION_SERVICE)) as LocationManager

        binding.help.setOnClickListener {
            Snackbar.make(it, R.string.help, 8000).show()
        }

        binding.myLocation.setOnClickListener {
            if (isPermissionGranted()) {
                map.animateCamera(CameraUpdateFactory.newLatLng(fakeLocation), 500, null)
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }

        binding.directions.setOnClickListener {
            binding.poiDetail.visibility = View.GONE

            if (isRouteWithFakeLocation!!) {
                drawRoute(fakeLocation, LatLng(poiEndMarker!!.latitude, poiEndMarker!!.longitude))
                binding.poiStart.setTextColor(ContextCompat.getColor(this, R.color.black))
                binding.poiStart.typeface = Typeface.DEFAULT_BOLD
                binding.poiStart.text = getString(R.string.your_location)
                binding.poiEndTitle.text = poiEndMarker!!.title
                binding.route.visibility = View.VISIBLE
            } else {
                binding.chooseTrafficCamera.visibility = View.VISIBLE
            }

            binding.chooseRoute.visibility = View.VISIBLE
            binding.layoutSearch.visibility = View.GONE
        }

        binding.back.setOnClickListener {
            resetChooseRoute()
        }

        binding.startNavigation.setOnClickListener {
            binding.navigatingTitle.visibility = View.VISIBLE
            binding.navigatingFinish.visibility = View.VISIBLE
            binding.chooseRoute.visibility = View.GONE
            binding.route.visibility = View.GONE
        }

        binding.finish.setOnClickListener {
            finishNavigation()
        }

        binding.searchBack.setOnClickListener {
            closeSearch()
        }

        binding.searchBar.setOnClickListener {
            openSearch()
        }

        (binding.searchBar.findViewById(R.id.search_button) as ImageView).setOnClickListener {
            Toast.makeText(this, "Not implemented", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSearch() {
        if (binding.recyclerSearchPoi.adapter != null) {
            binding.layoutSearch.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
            binding.recyclerSearchPoi.visibility = View.VISIBLE
            binding.searchBack.visibility = View.VISIBLE
            binding.searchSeparator.visibility = View.VISIBLE
        }
    }

    private fun closeSearch() {
        binding.layoutSearch.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.transparent))
        binding.recyclerSearchPoi.visibility = View.GONE
        binding.searchBack.visibility = View.GONE
        binding.searchSeparator.visibility = View.GONE
    }

    private fun initializeMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.setOnMarkerClickListener(this)
        map.setOnMyLocationButtonClickListener(this)

        fakeLocationMarker = MarkerOptions()
            .title(FAKE_LOCATION_TITLE)
            .position(fakeLocation)
            .anchor(0.5f, 0.5f)
            .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bluedot))

        markersMap[FAKE_LOCATION_TITLE] = map.addMarker(fakeLocationMarker)

        if (isPermissionGranted()) {
            markersMap[FAKE_LOCATION_TITLE]?.isVisible = true
            drawFakeLocationIcon()
            registerReceiver()
        } else {
            markersMap[FAKE_LOCATION_TITLE]?.isVisible = false
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        retrofit = Retrofit.Builder()
            .baseUrl(CARTO_BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
        poisApi = retrofit.create(PoisApi::class.java)

        poisResponse = poisApi.getPois(QUERY)

        poisResponse.enqueue(object: Callback<PoisResponse<Poi>> {
            override fun onResponse(call: Call<PoisResponse<Poi>>, response: Response<PoisResponse<Poi>>) {
                if (response.isSuccessful) {
                    pois.addAll(response.body()!!.rows)
                    addMarkers()
                    searchPoiAdapter = SearchPoiAdapter { poiClicked ->
                        markersMap[poiClicked.id]?.let { marker -> onMarkerClick(marker) }
                        onBackPressed()
                    }
                    searchPoiAdapter.submitList(pois)
                    binding.recyclerSearchPoi.adapter = searchPoiAdapter
                } else {
                    Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
                }
                binding.progress.visibility = View.GONE
            }

            override fun onFailure(call: Call<PoisResponse<Poi>>, error: Throwable) {
                Toast.makeText(this@MainActivity, "Error", Toast.LENGTH_SHORT).show()
                binding.progress.visibility = View.GONE
            }
        })
    }

    private fun addMarkers() {
        val bbox = LatLngBounds.Builder()

        pois.map {
            val latLng = LatLng(it.latitude, it.longitude)
            bbox.include(latLng)
            MarkerOptions()
                .title(it.id)
                .position(latLng)
        }.forEach {
            markersMap[it.title] = map.addMarker(it)
        }

        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bbox.build(), 50.toPx()))
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        if (marker.title != FAKE_LOCATION_TITLE && binding.navigatingTitle.visibility == View.GONE) {
            val poiMarker = pois.first { it.id == marker.title }
            map.animateCamera(CameraUpdateFactory.newLatLng(
                LatLng(poiMarker.latitude, poiMarker.longitude)), 500, null)

            if (poiEndMarker == null || binding.chooseTrafficCamera.visibility == View.GONE) {
                poiEndMarker = poiMarker
                poiEndMarker?.let { poi ->
                    binding.poiTitle.text = poi.title
                    binding.poiEnd.text = poi.title
                    binding.poiDescription.text = poi.description
                    binding.poiRegion.text = poi.region
                    Picasso.get()
                        .load(poi.getImageFixed())
                        .placeholder(android.R.drawable.ic_menu_camera)
                        .error(android.R.drawable.ic_delete)
                        .into(binding.poiImage)

                    if (binding.chooseRoute.visibility == View.GONE) {
                        binding.poiDetail.visibility = View.VISIBLE
                    }

                    if (isRouteWithFakeLocation == null) {
                        isRouteWithFakeLocation = isFakeLocationEnabled()
                    }

                    if (isRouteWithFakeLocation!!) {
                        binding.startNavigation.isEnabled = true
                        binding.startNavigation.setBackgroundResource(
                            R.drawable.background_rounded_black)
                        binding.startNavigation.setTextColor(
                            ContextCompat.getColor(this, R.color.black))
                        binding.poiEndTitle.text = poiMarker.title
                        binding.markerStart.setImageResource(R.drawable.ic_location)
                        binding.timeDistance.visibility = View.VISIBLE
                        binding.directionsText.layoutParams.height =
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        val end = LatLng(poiMarker.latitude, poiMarker.longitude)
                        if (binding.chooseRoute.visibility == View.VISIBLE) {
                            drawRoute(fakeLocation, end)
                        }
                        calculateRoute(fakeLocation, end)
                    } else {
                        binding.markerStart.setImageResource(R.drawable.ic_marker)
                        binding.timeDistance.visibility = View.GONE
                        binding.directionsText.layoutParams.height = 40.toPx()
                    }

                    resetMarkers()
                    markerEndClicked = marker
                    clickMarker(markerEndClicked!!)
                }
            } else {
                if (marker.title != markerEndClicked!!.title) {
                    binding.startNavigation.isEnabled = false
                    binding.startNavigation.setBackgroundResource(R.drawable.background_rounded_grey)
                    binding.startNavigation.setTextColor(
                            ContextCompat.getColor(this, R.color.light_grey))
                    binding.poiStart.text = poiMarker.title
                    binding.poiStart.setTextColor(ContextCompat.getColor(this, R.color.black))
                    binding.poiStart.typeface = Typeface.DEFAULT_BOLD
                    binding.poiEndTitle.text = poiMarker.title
                    binding.route.visibility = View.VISIBLE
                    resetMarkers(allMarkers = false)
                    markerStartClicked = marker
                    clickMarker(markerStartClicked!!)
                    val start = LatLng(poiMarker.latitude, poiMarker.longitude)
                    val end = LatLng(poiEndMarker!!.latitude, poiEndMarker!!.longitude)
                    drawRoute(start, end)
                    calculateRoute(start, end)
                }
            }
        }

        return true
    }

    private fun calculateRoute(start: LatLng, end: LatLng) {
        val result = floatArrayOf(0f)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, result)
        val time = result.first().time()
        val distance = result.first().metersToKm(this)
        binding.time.text = time
        binding.distance.text = distance
        binding.timeDistance.text = getString(R.string.time_distance, time, distance)
    }

    private fun drawRoute(start: LatLng, end: LatLng) {
        polyLine?.remove()
        polyLine = map.addPolyline(PolylineOptions()
            .add(start)
            .add(end)
            .color(Color.MAGENTA)
            .width(5.toPx().toFloat()))
    }

    override fun onBackPressed() {
        if (binding.recyclerSearchPoi.visibility == View.VISIBLE) {
            closeSearch()
        } else if (binding.poiDetail.visibility == View.GONE &&
            binding.chooseRoute.visibility == View.GONE &&
            binding.navigatingTitle.visibility == View.GONE) {
            super.onBackPressed()
        } else if (binding.navigatingTitle.visibility == View.VISIBLE) {
            finishNavigation()
        } else if (binding.chooseRoute.visibility == View.VISIBLE) {
            resetChooseRoute()
        } else if (binding.poiDetail.visibility == View.VISIBLE) {
            polyLine?.remove()
            resetMarkers()
            binding.poiDetail.visibility = View.GONE
            poiEndMarker = null
            isRouteWithFakeLocation = null
        }
    }

    private fun resetChooseRoute() {
        polyLine?.remove()
        binding.chooseTrafficCamera.visibility = View.GONE
        binding.chooseRoute.visibility = View.GONE
        binding.route.visibility = View.GONE
        binding.layoutSearch.visibility = View.VISIBLE
        binding.poiDetail.visibility = View.VISIBLE
        binding.poiStart.setTextColor(ContextCompat.getColor(this, R.color.light_grey))
        binding.poiStart.typeface = Typeface.DEFAULT
        binding.poiStart.text = getString(R.string.choose_starting)
        resetMarkers(allMarkers = false)
    }

    private fun finishNavigation() {
        binding.navigatingTitle.visibility = View.GONE
        binding.navigatingFinish.visibility = View.GONE
        binding.chooseRoute.visibility = View.VISIBLE
        binding.route.visibility = View.VISIBLE
    }

    override fun onMyLocationButtonClick(): Boolean {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(fakeLocation, 10f))
        return true
    }

    private fun clickMarker(marker: Marker) {
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
    }

    private fun resetMarkers(allMarkers: Boolean = true) {
        if (allMarkers) {
            markerEndClicked?.setIcon(
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        }

        markerStartClicked?.setIcon(
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
    }

    private fun isPermissionGranted(): Boolean =
        ActivityCompat.checkSelfPermission(this,
            android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun isFakeLocationEnabled(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && isPermissionGranted()

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    markersMap[FAKE_LOCATION_TITLE]?.isVisible = true
                    drawFakeLocationIcon()
                    registerReceiver()
                }
            }
        }
    }

    private fun registerReceiver() {
        if (isRegisteredReceiver.not()) {
            registerReceiver(gpsStateChanged, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
            isRegisteredReceiver = true
        }
    }

    private fun drawFakeLocationIcon() {
        if (isFakeLocationEnabled()) {
            markersMap[FAKE_LOCATION_TITLE]?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_bluedot))
        } else {
            markersMap[FAKE_LOCATION_TITLE]?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_greydot))
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPermissionGranted()) {
            markersMap[FAKE_LOCATION_TITLE]?.isVisible = true
            drawFakeLocationIcon()
            if (isRegisteredReceiver.not()) registerReceiver()
        } else {
            markersMap[FAKE_LOCATION_TITLE]?.isVisible = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRegisteredReceiver) unregisterReceiver(gpsStateChanged)
    }
}