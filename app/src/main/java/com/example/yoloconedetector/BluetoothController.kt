package com.example.yoloconedetector

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.OutputStream
import java.util.UUID
import java.util.Locale

class BluetoothController(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val SPP_UUID: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connect(deviceName: String): Boolean {

        if (adapter == null) {
            Log.e("BT", "BluetoothAdapter null")
            return false
        }

        // Android 12+
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.e("BT", "BLUETOOTH_CONNECT not granted")
                return false
            }
        }

        val device: BluetoothDevice = adapter.bondedDevices.firstOrNull {
            it.name == deviceName
        } ?: run {
            Log.e("BT", "Device not found: $deviceName")
            return false
        }

        try {
            Log.i("BT", "Opening RFCOMM socket...")
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)

            adapter.cancelDiscovery()
            socket!!.connect()   // 🔥 A LINHA QUE FALTAVA

            outputStream = socket!!.outputStream

            Log.i("BT", "Bluetooth CONNECTED to ${device.name}")
            return true

        } catch (e: Exception) {
            Log.e("BT", "Connection failed", e)
            close()
            return false
        }
    }

    fun send(steering: Float, throttle: Float) {

        val msg =
            String.format(
                Locale.US,
                "S:%.3f;T:%.3f\n",
                steering,
                throttle
            )


        try {
            outputStream?.write(msg.toByteArray())
            outputStream?.flush()
            Log.i("BT_SEND", msg)

        } catch (e: Exception) {
            Log.e("BT_SEND", "Write failed", e)
        }
    }

    fun close() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
    }
}
