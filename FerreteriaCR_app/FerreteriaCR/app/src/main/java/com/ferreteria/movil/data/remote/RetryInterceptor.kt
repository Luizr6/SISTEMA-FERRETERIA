package com.ferreteria.movil.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * RNF08 (resiliencia móvil): reintenta con espera exponencial (0.5 s, 1 s, 2 s) cuando
 * la señal es débil y la petición falla por red.
 *
 * Solo reintenta GET. Un POST (por ejemplo crear un pedido) NO se reintenta solo, para no
 * duplicar pedidos: lo decide el vendedor y PedidoRepository verifica antes si ya llegó.
 */
class RetryInterceptor(
    private val maxIntentos: Int = 3,
    private val esperaInicialMs: Long = 500
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)

        var espera = esperaInicialMs
        var ultimoError: IOException? = null
        for (intento in 1..maxIntentos) {
            try {
                return chain.proceed(request)
            } catch (e: IOException) {
                ultimoError = e
                if (intento < maxIntentos) {
                    try {
                        Thread.sleep(espera)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                    espera *= 2
                }
            }
        }
        throw ultimoError ?: IOException("Sin conexión")
    }
}
