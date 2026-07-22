package com.pedidos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pedidos.databinding.ActivityMainBinding

/**
 * Flujo de uso para el padre:
 * 1. Pulsa el botón grande.
 * 2. Dice el nombre del cliente (ej: "Pedidos para mañana"). Hace una pausa.
 * 3. Dice cada producto uno a uno (ej: "2 salchichón"), con una pequeña pausa entre cada uno.
 * 4. Cuando termina, dice "enviar" y el pedido se manda solo al grupo configurado.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var recognizer: SpeechRecognizer? = null
    private var escuchando = false
    private var clienteCapturado: String? = null
    private val productos = mutableListOf<String>()

    private val permisoMicRequestCode = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.botonAjustes.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.botonHablar.setOnClickListener {
            if (!escuchando) iniciarPedido() else cancelarPedido()
        }

        actualizarEstadoTexto("Pulsa el botón y di: el cliente, luego cada producto, y termina diciendo ENVIAR")
    }

    private fun tienePermisoMic(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun iniciarPedido() {
        val grupo = Prefs.getGroup(this)
        if (grupo.isBlank()) {
            Toast.makeText(this, "Primero configura el grupo de WhatsApp en Ajustes", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }

        if (!tienePermisoMic()) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), permisoMicRequestCode)
            return
        }

        clienteCapturado = null
        productos.clear()
        escuchando = true
        binding.botonHablar.text = "ESCUCHANDO...\n(pulsa para cancelar)"
        actualizarEstadoTexto("Di el nombre del cliente")

        iniciarReconocimiento()
    }

    private fun iniciarReconocimiento() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val texto = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    ?: ""
                procesarFrase(texto)
            }

            override fun onError(error: Int) {
                // Ante silencios o pequeños fallos, simplemente volvemos a escuchar.
                if (escuchando) {
                    iniciarReconocimiento()
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer?.startListening(intent)
    }

    private fun procesarFrase(fraseOriginal: String) {
        if (!escuchando) return
        if (fraseOriginal.isBlank()) {
            iniciarReconocimiento()
            return
        }

        val frase = fraseOriginal.trim()
        val contieneEnviar = Regex("\\benviar\\b", RegexOption.IGNORE_CASE).containsMatchIn(frase)

        if (contieneEnviar) {
            val restante = frase.replace(Regex("\\benviar\\b", RegexOption.IGNORE_CASE), "").trim()
            if (restante.isNotBlank()) {
                agregarFrase(restante)
            }
            finalizarYEnviar()
            return
        }

        agregarFrase(frase)
        iniciarReconocimiento()
    }

    private fun agregarFrase(fraseOriginal: String) {
        val frase = ConversorNumeros.convertir(fraseOriginal)

        if (clienteCapturado == null) {
            clienteCapturado = frase
            actualizarEstadoTexto("Cliente: $frase\n\nAhora di el primer producto")
        } else {
            // Si en la misma frase ha dicho varios productos seguidos
            // (ej: "2 cajas de longanizas y 1 de lomo"), los separamos en líneas distintas.
            productos.addAll(dividirProductos(frase))
            actualizarEstadoTexto(
                "Cliente: $clienteCapturado\n\nProductos:\n${formatearProductos()}\n\nDi el siguiente producto o di ENVIAR"
            )
        }
    }

    /** Separa varios productos dichos en la misma frase, detectando " y " seguido de un número. */
    private fun dividirProductos(frase: String): List<String> {
        return frase.split(Regex("\\s+y\\s+(?=\\d)"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun formatearProductos(): String {
        return productos.joinToString("\n") { "- $it" }
    }

    private fun finalizarYEnviar() {
        escuchando = false
        binding.botonHablar.text = "PULSA PARA HABLAR"

        val cliente = clienteCapturado
        if (cliente.isNullOrBlank() || productos.isEmpty()) {
            actualizarEstadoTexto("No se ha capturado un pedido completo. Vuelve a intentarlo.")
            return
        }

        val mensaje = buildString {
            append(cliente)
            append("\n\n")
            append(formatearProductos())
        }

        actualizarEstadoTexto("Enviando pedido a WhatsApp...\n\n$mensaje")

        val grupo = Prefs.getGroup(this)

        if (OrderAccessibilityService.isEnabled(this)) {
            OrderAccessibilityService.sendOrder(this, grupo, mensaje)
        } else {
            Toast.makeText(
                this,
                "Activa el servicio de accesibilidad en Ajustes para el envío 100% automático",
                Toast.LENGTH_LONG
            ).show()
            enviarPorCompartirManual(mensaje)
        }
    }

    private fun enviarPorCompartirManual(mensaje: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, mensaje)
            setPackage("com.whatsapp")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelarPedido() {
        escuchando = false
        recognizer?.cancel()
        binding.botonHablar.text = "PULSA PARA HABLAR"
        actualizarEstadoTexto("Pedido cancelado. Pulsa el botón para empezar de nuevo.")
    }

    private fun actualizarEstadoTexto(texto: String) {
        binding.textoEstado.text = texto
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permisoMicRequestCode &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarPedido()
        } else {
            Toast.makeText(this, "Se necesita permiso de micrófono para dictar el pedido", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.destroy()
    }
}
