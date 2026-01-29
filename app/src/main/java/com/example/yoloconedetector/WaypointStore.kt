package com.example.yoloconedetector

import com.google.android.gms.maps.model.LatLng

object WaypointStore {

    val waypoints: MutableList<LatLng> = mutableListOf()

    var selectedIndex: Int? = null

    fun add(point: LatLng) {
        waypoints.add(point)
        selectedIndex = waypoints.lastIndex
    }

    fun select(index: Int?) {
        selectedIndex = index
    }

    fun removeLast() {
        if (waypoints.isNotEmpty()) {
            waypoints.removeAt(waypoints.lastIndex)
            if (selectedIndex == waypoints.size) {
                selectedIndex = waypoints.lastIndex.takeIf { it >= 0 }
            }
        }
    }

    fun clear() {
        waypoints.clear()
        selectedIndex = null
    }
}

