package com.example.yoloconedetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper


class WaypointManagerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val pathPoints = mutableListOf<LatLng>()
    private var pathPolyline: Polyline? = null
    private var sequencePolyline: Polyline? = null

    private lateinit var clearWaypointsButton: Button
    private lateinit var clearPathButton: Button

    private lateinit var undoWaypointButton: Button

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WaypointAdapter



    /* =========================
       LIFECYCLE
       ========================= */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waypoint_manager)

        mapView = findViewById(R.id.mapView)
        clearWaypointsButton = findViewById(R.id.clearWaypointsButton)
        clearPathButton = findViewById(R.id.clearPathButton)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        clearWaypointsButton.setOnClickListener {
            WaypointStore.clear()
            redrawWaypoints()
        }

        clearPathButton.setOnClickListener {
            pathPoints.clear()
            pathPolyline?.remove()
            pathPolyline = null
        }

        undoWaypointButton = findViewById(R.id.undoWaypointButton)

        undoWaypointButton.setOnClickListener {
            WaypointStore.removeLast()
            redrawWaypoints()
        }

        recyclerView = findViewById(R.id.waypointRecycler)

        adapter = WaypointAdapter {
            redrawWaypoints()
        }

        recyclerView.adapter = adapter

        val lm = LinearLayoutManager(this)
        lm.stackFromEnd = false
        lm.reverseLayout = false
        recyclerView.layoutManager = lm

        recyclerView.setHasFixedSize(true)


        //DRAG AND DROP LISTA WAYPOINTS
        val touchHelper = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    adapter.moveItem(
                        viewHolder.adapterPosition,
                        target.adapterPosition
                    )
                    return true
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) {}
            }
        )

        touchHelper.attachToRecyclerView(recyclerView)

    }

    /* =========================
       MAPA
       ========================= */

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isZoomControlsEnabled = true
        googleMap.uiSettings.isCompassEnabled = true

        googleMap.setOnMapClickListener { latLng ->
            val index = WaypointStore.waypoints.size
            WaypointStore.waypoints.add(latLng)

            adapter.notifyItemInserted(index)
            redrawWaypoints()
        }


        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }

        // 🔥 ISSO É O QUE FALTAVA
        redrawWaypoints()
    }

    private fun enableLocation() {
        googleMap.isMyLocationEnabled = true

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val pos = LatLng(location.latitude, location.longitude)

                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(pos, 18f)
                )

                updatePath(pos)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            mainLooper
        )
    }

    /* =========================
       WAYPOINTS (ÚNICO SISTEMA)
       ========================= */

    private fun redrawWaypoints() {
        googleMap.clear()
        adapter.notifyDataSetChanged()

        val points = WaypointStore.waypoints
        val selected = WaypointStore.selectedIndex

        points.forEachIndexed { index, point ->

            val isSelected = index == selected

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(point)
                    .title("Waypoint $index")
                    .icon(
                        BitmapDescriptorFactory.defaultMarker(
                            if (isSelected)
                                BitmapDescriptorFactory.HUE_AZURE
                            else
                                BitmapDescriptorFactory.HUE_RED
                        )
                    )
            )

            marker?.tag = index

        }

        if (points.size >= 2) {
            googleMap.addPolyline(
                PolylineOptions()
                    .addAll(points + points.first())
                    .color(Color.RED)
                    .width(4f)
                    .pattern(listOf(Dot(), Gap(12f)))
            )
        }
    }


    /* =========================
       RASTRO (DEBUG)
       ========================= */

    private fun updatePath(currentLocation: LatLng) {
        pathPoints.add(currentLocation)

        if (pathPolyline == null) {
            pathPolyline = googleMap.addPolyline(
                PolylineOptions()
                    .addAll(pathPoints)
                    .color(Color.BLUE)
                    .width(5f)
            )
        } else {
            pathPolyline?.points = pathPoints
        }
    }

    /* =========================
       MAPVIEW
       ========================= */

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
