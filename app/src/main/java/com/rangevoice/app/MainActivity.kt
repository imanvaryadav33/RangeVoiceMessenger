package com.rangevoice.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rangevoice.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: BluetoothAdapter
    private lateinit var prefs: SharedPreferences
    private var pairedDevices: List<BluetoothDevice> = emptyList()
    private var serviceRunning = false

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            loadPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth permissions ke bina app kaam nahi karegi", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("range_voice_prefs", MODE_PRIVATE)
        val btManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        adapter = btManager.adapter

        binding.etMessage.setText(prefs.getString("my_message", ""))

        binding.btnRefreshDevices.setOnClickListener { requestPermissionsAndLoad() }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnToggleService.setOnClickListener { toggleService() }

        requestPermissionsAndLoad()
        updateStatus()
    }

    private fun requestPermissionsAndLoad() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            loadPairedDevices()
        }
    }

    private fun loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) return

        pairedDevices = adapter.bondedDevices?.toList() ?: emptyList()
        val names = pairedDevices.map { "${it.name} (${it.address})" }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        binding.spinnerDevices.adapter = spinnerAdapter

        val savedAddress = prefs.getString("peer_address", null)
        val savedIndex = pairedDevices.indexOfFirst { it.address == savedAddress }
        if (savedIndex >= 0) binding.spinnerDevices.setSelection(savedIndex)

        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "Koi paired device nahi mila. Pehle Bluetooth Settings me dusra phone pair karo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveSettings() {
        val message = binding.etMessage.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Pehle message likho", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedIndex = binding.spinnerDevices.selectedItemPosition
        if (selectedIndex < 0 || selectedIndex >= pairedDevices.size) {
            Toast.makeText(this, "Peer device select karo", Toast.LENGTH_SHORT).show()
            return
        }
        val peerDevice = pairedDevices[selectedIndex]
        prefs.edit()
            .putString("my_message", message)
            .putString("peer_address", peerDevice.address)
            .apply()
        Toast.makeText(this, "Save ho gaya!", Toast.LENGTH_SHORT).show()
    }

    private fun toggleService() {
        val intent = Intent(this, RangeVoiceService::class.java)
        if (!serviceRunning) {
            if (prefs.getString("my_message", null).isNullOrBlank() || prefs.getString("peer_address", null) == null) {
                Toast.makeText(this, "Pehle message aur device save karo", Toast.LENGTH_LONG).show()
                return
            }
            ContextCompat.startForegroundService(this, intent)
            serviceRunning = true
        } else {
            stopService(intent)
            serviceRunning = false
        }
        updateStatus()
    }

    private fun updateStatus() {
        binding.tvStatus.text = if (serviceRunning)
            "Status: Service chal raha hai (background me range check ho raha hai)"
        else
            "Status: Service band hai"
        binding.btnToggleService.text = if (serviceRunning) "Service Band Karo" else "Service Start Karo"
    }
}
