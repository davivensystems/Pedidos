package com.pedidos

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Este servicio abre WhatsApp, busca el grupo configurado, escribe el pedido
 * y pulsa "Enviar" automáticamente, sin que la persona tenga que tocar nada más.
 *
 * IMPORTANTE: usa identificadores internos de WhatsApp que no son oficiales ni
 * están garantizados a largo plazo. Si en el futuro no encuentra algún botón,
 * deja el mensaje ya escrito en el chat correcto para que solo haga falta
 * pulsar "Enviar" a mano como último recurso.
 */
class OrderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PedidosAccesibilidad"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"

        @Volatile var instance: OrderAccessibilityService? = null

        @Volatile private var pendingGroup: String? = null
        @Volatile private var pendingMessage: String? = null

        fun isEnabled(context: Context): Boolean = instance != null

        /** Llamado desde MainActivity cuando el pedido dictado está completo. */
        fun sendOrder(context: Context, groupName: String, message: String) {
            pendingGroup = groupName
            pendingMessage = message

            val launchIntent = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
            if (launchIntent == null) {
                Log.e(TAG, "WhatsApp no está instalado en este dispositivo")
                return
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)

            instance?.beginAutomation()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var state = State.IDLE
    private var attempts = 0

    private enum class State {
        IDLE, ABRIR_BUSQUEDA, ESCRIBIR_BUSQUEDA, ABRIR_CHAT, ESCRIBIR_MENSAJE, PULSAR_ENVIAR
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // Usamos sondeo (polling) en vez de depender solo de eventos, porque
    // WhatsApp no siempre notifica sus cambios de pantalla de forma fiable.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun beginAutomation() {
        attempts = 0
        state = State.ABRIR_BUSQUEDA
        handler.postDelayed({ step() }, 2000)
    }

    private fun step() {
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != WHATSAPP_PACKAGE) {
            retryOrGiveUp("Esperando a que WhatsApp esté en primer plano")
            return
        }

        when (state) {
            State.ABRIR_BUSQUEDA -> {
                val boton = findByDesc(root, listOf("Buscar", "Search")) ?: findByViewId(root, "menuitem_search")
                if (boton != null && clickNode(boton)) {
                    avanzar(State.ESCRIBIR_BUSQUEDA, 900)
                } else {
                    retryOrGiveUp("Buscando el icono de búsqueda")
                }
            }

            State.ESCRIBIR_BUSQUEDA -> {
                val campo = findFirstEditText(root)
                val grupo = pendingGroup ?: ""
                if (campo != null && setText(campo, grupo)) {
                    avanzar(State.ABRIR_CHAT, 1100)
                } else {
                    retryOrGiveUp("Escribiendo el nombre del grupo")
                }
            }

            State.ABRIR_CHAT -> {
                val resultado = findFirstClickableWithText(root, pendingGroup ?: "")
                if (resultado != null && clickNode(resultado)) {
                    avanzar(State.ESCRIBIR_MENSAJE, 1300)
                } else {
                    retryOrGiveUp("Abriendo la conversación del grupo")
                }
            }

            State.ESCRIBIR_MENSAJE -> {
                val campoMensaje = findByViewId(root, "entry") ?: findFirstEditText(root)
                val mensaje = pendingMessage ?: ""
                if (campoMensaje != null && setText(campoMensaje, mensaje)) {
                    avanzar(State.PULSAR_ENVIAR, 900)
                } else {
                    retryOrGiveUp("Escribiendo el pedido en el chat")
                }
            }

            State.PULSAR_ENVIAR -> {
                val boton = findByViewId(root, "send")
                    ?: findByDesc(root, listOf("Enviar", "Send"))
                    ?: findUltimoBotonImagenClicable(root)
                if (boton != null && clickNode(boton)) {
                    Log.i(TAG, "Pedido enviado automáticamente")
                } else {
                    Log.w(TAG, "No se encontró el botón de enviar; el mensaje queda escrito para enviarlo a mano")
                }
                finish()
            }

            State.IDLE -> { /* nada que hacer */ }
        }
    }

    private fun avanzar(siguiente: State, esperaMs: Long) {
        state = siguiente
        attempts = 0
        handler.postDelayed({ step() }, esperaMs)
    }

    private fun retryOrGiveUp(motivo: String) {
        attempts++
        if (attempts > 16) {
            Log.w(TAG, "Se abandona el paso automático: $motivo")
            finish()
        } else {
            handler.postDelayed({ step() }, 700)
        }
    }

    private fun finish() {
        state = State.IDLE
        pendingGroup = null
        pendingMessage = null
    }

    // ---------- Utilidades de búsqueda en el árbol de accesibilidad ----------

    private fun findByViewId(root: AccessibilityNodeInfo, idSuffix: String): AccessibilityNodeInfo? {
        return root.findAccessibilityNodeInfosByViewId("$WHATSAPP_PACKAGE:id/$idSuffix").firstOrNull()
    }

    private fun findByDesc(root: AccessibilityNodeInfo, opciones: List<String>): AccessibilityNodeInfo? {
        return buscarRecursivo(root) { nodo ->
            val desc = nodo.contentDescription?.toString()
            desc != null && opciones.any { desc.equals(it, ignoreCase = true) }
        }
    }

    private fun findFirstEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return buscarRecursivo(root) { it.className == "android.widget.EditText" }
    }

    private fun findFirstClickableWithText(root: AccessibilityNodeInfo, texto: String): AccessibilityNodeInfo? {
        if (texto.isBlank()) return null
        val directo = buscarRecursivo(root) { nodo ->
            val t = nodo.text?.toString()
            nodo.isClickable && t != null && t.contains(texto, ignoreCase = true)
        }
        if (directo != null) return directo

        val conTexto = buscarRecursivo(root) { nodo ->
            val t = nodo.text?.toString()
            t != null && t.contains(texto, ignoreCase = true)
        }
        return conTexto?.let { encontrarAncestroClicable(it) }
    }

    private fun encontrarAncestroClicable(nodo: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var actual: AccessibilityNodeInfo? = nodo
        while (actual != null) {
            if (actual.isClickable) return actual
            actual = actual.parent
        }
        return null
    }

    private fun buscarRecursivo(
        nodo: AccessibilityNodeInfo,
        condicion: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (condicion(nodo)) return nodo
        for (i in 0 until nodo.childCount) {
            val hijo = nodo.getChild(i) ?: continue
            val encontrado = buscarRecursivo(hijo, condicion)
            if (encontrado != null) return encontrado
        }
        return null
    }

    /**
     * Último recurso para encontrar el botón de enviar: en la pantalla de un chat,
     * suele ser el último botón/icono clicable del árbol (normalmente abajo a la derecha).
     */
    private fun findUltimoBotonImagenClicable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var ultimo: AccessibilityNodeInfo? = null
        fun recorrer(nodo: AccessibilityNodeInfo) {
            val esBotonImagen = nodo.isClickable &&
                (nodo.className == "android.widget.ImageButton" || nodo.className == "android.widget.ImageView")
            if (esBotonImagen) ultimo = nodo
            for (i in 0 until nodo.childCount) {
                nodo.getChild(i)?.let { recorrer(it) }
            }
        }
        recorrer(root)
        return ultimo
    }

    private fun clickNode(nodo: AccessibilityNodeInfo): Boolean {
        return nodo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun setText(nodo: AccessibilityNodeInfo, texto: String): Boolean {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto)
        return nodo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
