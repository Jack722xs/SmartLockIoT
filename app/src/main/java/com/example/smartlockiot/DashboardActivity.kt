package com.example.smartlockiot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartlockiot.databinding.ActivityDashboardBinding
import com.example.smartlockiot.model.AccessLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var b: ActivityDashboardBinding
    private lateinit var store: SecureStore
    private lateinit var ble: BleLockManager
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val adapter = LogAdapter()

    private var logsReg: ListenerRegistration? = null
    private var doorReg: ListenerRegistration? = null
    private var firstLogSnapshot = true
    private var suppress = false
    private var lastLocked = true

    private val blePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (ble.hasPermissions()) ble.startScan() else toast("Permisos Bluetooth denegados")
        }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = auth.currentUser
        if (user == null) {
            goLogin(); return
        }
        b = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(b.root)

        NotificationHelper.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

        store = SecureStore(this)
        ble = BleLockManager(this) { msg ->
            b.tvBleStatus.text = msg
            b.btnBle.text = if (ble.isConnected) "Desconectar BLE" else "Conectar BLE"
        }

        b.tvUser.text = "Admin: ${user.email}"
        b.rvLogs.layoutManager = LinearLayoutManager(this)
        b.rvLogs.adapter = adapter
        b.etEndpoint.setText(store.endpoint)
        b.etToken.setText(store.token)

        b.btnLogout.setOnClickListener {
            ble.release()
            auth.signOut()
            goLogin()
        }
        b.switchDoor.setOnCheckedChangeListener { _, checked ->
            if (!suppress) sendCommand(unlock = checked)
        }
        b.btnSaveConfig.setOnClickListener { saveConfig() }
        b.btnBle.setOnClickListener { toggleBle() }
        b.btnRegister.setOnClickListener { registerUid() }
        b.btnRevoke.setOnClickListener { revokeUid() }

        applyDoorState(true)
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser == null) return
        firstLogSnapshot = true
        listenDoor()
        listenLogs()
    }

    override fun onStop() {
        super.onStop()
        logsReg?.remove(); logsReg = null
        doorReg?.remove(); doorReg = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::ble.isInitialized) ble.release()
    }

    private fun listenDoor() {
        doorReg = db.collection(Constants.COL_DOOR).document(Constants.DOC_DOOR_STATE)
            .addSnapshotListener { s, e ->
                if (e != null || s == null) return@addSnapshotListener
                lastLocked = s.getBoolean("locked") ?: true
                applyDoorState(lastLocked)
            }
    }

    private fun listenLogs() {
        logsReg = db.collection(Constants.COL_LOGS)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, e ->
                if (e != null || snap == null) {
                    toast("Error leyendo registros: ${e?.message}")
                    return@addSnapshotListener
                }
                adapter.submitList(snap.toObjects(AccessLog::class.java))
                b.tvEmpty.visibility =
                    if (snap.isEmpty) android.view.View.VISIBLE else android.view.View.GONE
                if (!firstLogSnapshot) {
                    for (c in snap.documentChanges) {
                        if (c.type == DocumentChange.Type.ADDED && !c.document.metadata.hasPendingWrites()) {
                            val l = c.document.toObject(AccessLog::class.java)
                            if (l.status.equals(Constants.STATUS_SUCCESS, true)) {
                                NotificationHelper.show(this, "Apertura exitosa", "Acceso autorizado UID ${l.userId}")
                            } else {
                                NotificationHelper.show(this, "Apertura no exitosa", "Intento denegado UID ${l.userId}")
                            }
                        }
                    }
                }
                firstLogSnapshot = false
            }
    }

    private fun applyDoorState(locked: Boolean) {
        val color = ContextCompat.getColor(this, if (locked) R.color.red else R.color.green)
        b.viewDoorLight.backgroundTintList = ColorStateList.valueOf(color)
        b.tvDoorStatus.text = if (locked) "Puerta cerrada / Bloqueada" else "Puerta abierta / Desbloqueada"
        b.tvDoorStatus.setTextColor(color)
        suppress = true
        b.switchDoor.isChecked = !locked
        suppress = false
    }

    private fun sendCommand(unlock: Boolean) {
        val action = if (unlock) "unlock" else "lock"
        b.switchDoor.isEnabled = false
        lifecycleScope.launch {
            val res = mutableListOf<String>()
            try {
                db.collection(Constants.COL_DOOR).document(Constants.DOC_DOOR_STATE).set(
                    mapOf(
                        "locked" to !unlock,
                        "updatedBy" to (auth.currentUser?.email ?: ""),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "pendingCommand" to action
                    )
                ).awaitTimeout()
                res += "Nube OK"
            } catch (e: Exception) {
                res += "Nube falló"
            }
            val url = store.endpoint
            if (url.isNotBlank()) {
                HttpDoorClient.send(url, store.token, action)
                    .onSuccess { res += "HTTPS OK" }
                    .onFailure { res += "HTTPS: ${it.message}" }
            }
            if (ble.isConnected) {
                res += if (ble.send(if (unlock) "UNLOCK" else "LOCK")) "BLE OK" else "BLE falló"
            }
            b.switchDoor.isEnabled = true
            applyDoorState(lastLocked)
            toast(res.joinToString(" | "))
        }
    }

    private fun saveConfig() {
        val url = b.etEndpoint.text.toString().trim()
        if (url.isNotEmpty() && !url.startsWith("https://")) {
            b.etEndpoint.error = "Debe iniciar con https://"
            return
        }
        store.endpoint = url
        store.token = b.etToken.text.toString().trim()
        toast("Configuración guardada (cifrada)")
    }

    private fun toggleBle() {
        if (ble.isConnected) {
            ble.disconnect()
        } else if (ble.hasPermissions()) {
            ble.startScan()
        } else {
            blePermLauncher.launch(ble.requiredPermissions())
        }
    }

    private fun readUid(): String? {
        val uid = b.etUid.text.toString().uppercase().replace(Regex("[\\s:-]"), "")
        if (!Regex("^[0-9A-F]{8,20}$").matches(uid)) {
            b.etUid.error = "UID hexadecimal de 8 a 20 caracteres"
            return null
        }
        return uid
    }

    private fun registerUid() {
        val uid = readUid() ?: return
        lifecycleScope.launch {
            try {
                db.collection(Constants.COL_UIDS).document(uid).set(
                    mapOf(
                        "uid" to uid,
                        "active" to true,
                        "addedBy" to (auth.currentUser?.email ?: ""),
                        "addedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitTimeout()
                toast("UID $uid registrado")
                b.etUid.text?.clear()
            } catch (e: Exception) {
                toast("No se pudo registrar: ${e.message}")
            }
        }
    }

    private fun revokeUid() {
        val uid = readUid() ?: return
        lifecycleScope.launch {
            try {
                db.collection(Constants.COL_UIDS).document(uid).delete().awaitTimeout()
                toast("UID $uid revocado")
                b.etUid.text?.clear()
            } catch (e: Exception) {
                toast("No se pudo revocar: ${e.message}")
            }
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
