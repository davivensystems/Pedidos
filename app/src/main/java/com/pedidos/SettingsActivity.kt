package com.pedidos

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pedidos.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.campoGrupo.setText(Prefs.getGroup(this))
        binding.checkSiempre.isChecked = Prefs.isSiempre(this)

        binding.botonGuardar.setOnClickListener {
            val grupo = binding.campoGrupo.text.toString().trim()
            if (grupo.isBlank()) {
                Toast.makeText(this, "Escribe el nombre exacto del grupo de WhatsApp", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Prefs.setGroup(this, grupo)
            Prefs.setSiempre(this, binding.checkSiempre.isChecked)
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.botonAccesibilidad.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
