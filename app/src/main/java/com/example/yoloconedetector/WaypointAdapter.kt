package com.example.yoloconedetector

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng

class WaypointAdapter(
    private val onDataChanged: () -> Unit
) : RecyclerView.Adapter<WaypointAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val indexText: TextView = view.findViewById(R.id.waypointIndex)
        private val coordsText: TextView = view.findViewById(R.id.waypointCoords)

        fun bind(point: LatLng, index: Int, selected: Boolean) {
            indexText.text = "#$index"
            coordsText.text =
                "%.6f, %.6f".format(point.latitude, point.longitude)

            itemView.setBackgroundColor(
                if (selected)
                    0x33FFFFFF.toInt()   // destaque suave
                else
                    Color.TRANSPARENT
            )

            itemView.setOnClickListener {
                WaypointStore.select(index)
                notifyDataSetChanged()
                onDataChanged()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_waypoint, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = WaypointStore.waypoints.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val point = WaypointStore.waypoints[position]
        val selected = WaypointStore.selectedIndex == position
        holder.bind(point, position, selected)
    }

    fun moveItem(from: Int, to: Int) {
        val list = WaypointStore.waypoints
        val item = list.removeAt(from)
        list.add(to, item)
        notifyItemMoved(from, to)
        onDataChanged()
    }
}
