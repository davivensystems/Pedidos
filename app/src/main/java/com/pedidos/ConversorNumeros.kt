package com.pedidos

/**
 * Convierte números dictados en palabras ("dos", "doce", "treinta y cinco")
 * a dígitos ("2", "12", "35") dentro de una frase, para que las cantidades
 * de los pedidos salgan siempre en número.
 */
object ConversorNumeros {

    private val numerosBase = mapOf(
        "cero" to 0,
        "un" to 1, "uno" to 1, "una" to 1,
        "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        "diez" to 10, "once" to 11, "doce" to 12, "trece" to 13,
        "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "dieciséis" to 16,
        "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20,
        "veintiuno" to 21, "veintiun" to 21, "veintiún" to 21, "veintiuna" to 21,
        "veintidos" to 22, "veintidós" to 22,
        "veintitres" to 23, "veintitrés" to 23,
        "veinticuatro" to 24, "veinticinco" to 25,
        "veintiseis" to 26, "veintiséis" to 26,
        "veintisiete" to 27, "veintiocho" to 28, "veintinueve" to 29,
        "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
        "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
        "cien" to 100, "ciento" to 100
    )

    fun convertir(fraseOriginal: String): String {
        // 1) Sustituye cada palabra-número suelta por su dígito, respetando el resto del texto
        val tokens = fraseOriginal.split(Regex("(?<=\\s)|(?=\\s)"))
        var resultado = tokens.joinToString("") { token ->
            val limpio = token.trim().lowercase()
            val numero = numerosBase[limpio]
            if (numero != null) numero.toString() else token
        }

        // 2) Combina decenas + "y" + unidad (ej: "30 y 2" -> "32"), típico de "treinta y dos"
        val regexCompuesto = Regex("\\b(30|40|50|60|70|80|90)\\s+y\\s+(\\d)\\b")
        resultado = regexCompuesto.replace(resultado) { match ->
            val decena = match.groupValues[1].toInt()
            val unidad = match.groupValues[2].toInt()
            (decena + unidad).toString()
        }

        return resultado
    }
}
