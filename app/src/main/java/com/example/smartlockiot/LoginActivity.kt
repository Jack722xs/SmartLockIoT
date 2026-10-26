package com.example.smartlockiot

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartlockiot.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (auth.currentUser != null) {
            goDashboard()
            return
        }
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val email = b.etEmail.text.toString().trim()
        val pass = b.etPassword.text.toString()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Correo electrónico inválido"); return
        }
        if (pass.length < 6) {
            showError("La contraseña debe tener al menos 6 caracteres"); return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = auth.signInWithEmailAndPassword(email, pass).awaitTimeout(15000)
                val uid = res.user?.uid ?: throw IllegalStateException("Sesión inválida")
                if (Constants.REQUIRE_ADMIN_DOC) {
                    val doc = FirebaseFirestore.getInstance()
                        .collection(Constants.COL_ADMINS).document(uid).get().awaitTimeout(15000)
                    if (!doc.exists()) {
                        auth.signOut()
                        showError("La cuenta no tiene rol Administrador")
                        return@launch
                    }
                }
                goDashboard()
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                showError("Credenciales inválidas")
            } catch (e: FirebaseAuthInvalidUserException) {
                showError("Usuario no existe o deshabilitado")
            } catch (e: FirebaseNetworkException) {
                showError("Sin conexión a Internet")
            } catch (e: Exception) {
                auth.signOut()
                showError("Error de autenticación: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        b.progress.visibility = if (loading) android.view.View.VISIBLE else android.view.View.GONE
        b.btnLogin.isEnabled = !loading
        if (loading) b.tvError.visibility = android.view.View.GONE
    }

    private fun showError(msg: String) {
        b.tvError.text = msg
        b.tvError.visibility = android.view.View.VISIBLE
    }

    private fun goDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
