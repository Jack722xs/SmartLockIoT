package com.example.smartlockiot

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat

@SuppressLint("MissingPermission")
class BleLockManager(private val ctx: Context, private val onState: (String) -> Unit) {

    private val adapter: BluetoothAdapter? =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    @Volatile private var characteristic: BluetoothGattCharacteristic? = null
    @Volatile private var scanning = false

    val isConnected: Boolean get() = characteristic != null

    private fun post(msg: String) = main.post { onState(msg) }

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    private val stopRunnable = Runnable {
        if (scanning) {
            stopScan()
            post("BLE: cerradura no encontrada")
        }
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            stopScan()
            post("BLE: conectando…")
            gatt = result.device.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            post("BLE: error de escaneo ($errorCode)")
        }
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else {
                characteristic = null
                g.close()
                gatt = null
                post("BLE: desconectado")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val c = g.getService(Constants.BLE_SERVICE)?.getCharacteristic(Constants.BLE_CHAR)
            if (status == BluetoothGatt.GATT_SUCCESS && c != null) {
                characteristic = c
                post("BLE: conectado")
            } else {
                post("BLE: servicio de cerradura no encontrado")
                g.disconnect()
            }
        }
    }

    fun startScan() {
        val a = adapter
        val scanner = a?.bluetoothLeScanner
        if (a == null || !a.isEnabled || scanner == null) {
            post("BLE: Bluetooth apagado o no disponible")
            return
        }
        if (scanning) return
        scanning = true
        post("BLE: buscando cerradura…")
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(Constants.BLE_SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCb)
        main.postDelayed(stopRunnable, 10_000)
    }

    private fun stopScan() {
        main.removeCallbacks(stopRunnable)
        if (scanning) {
            scanning = false
            adapter?.bluetoothLeScanner?.stopScan(scanCb)
        }
    }

    @Suppress("DEPRECATION")
    fun send(cmd: String): Boolean {
        val g = gatt ?: return false
        val c = characteristic ?: return false
        val bytes = cmd.toByteArray()
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = bytes
            g.writeCharacteristic(c)
        }
    }

    fun disconnect() {
        stopScan()
        gatt?.disconnect()
    }

    fun release() {
        stopScan()
        characteristic = null
        gatt?.close()
        gatt = null
    }
}
