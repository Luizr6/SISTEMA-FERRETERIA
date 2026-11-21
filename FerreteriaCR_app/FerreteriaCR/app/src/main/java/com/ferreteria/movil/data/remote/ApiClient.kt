package com.ferreteria.movil.data.remote

import android.content.Context
import com.ferreteria.movil.BuildConfig
import com.ferreteria.movil.data.local.TokenManager
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Agrega "Authorization: Bearer <access>" a cada petición (menos login y refresh).
 * También envía "ngrok-skip-browser-warning" para que ngrok no responda con su página de aviso.
 */
class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder().header("ngrok-skip-browser-warning", "true")
        val path = original.url.encodedPath
        val token = tokenManager.getToken()
        if (!token.isNullOrBlank() && !path.contains("/auth/login") && !path.contains("/auth/refresh")) {
            builder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(builder.build())
    }
}

/**
 * Retrofit + OkHttp (un solo cliente para toda la app):
 *  - AuthInterceptor: token JWT.
 *  - TokenAuthenticator: renueva el token al recibir 401 (el vendedor no vuelve a escribir su clave).
 *  - RetryInterceptor: reintenta los GET si la señal es débil.
 */
object ApiClient {

    @Volatile
    private var api: ApiService? = null

    @Volatile
    private var urlActual: String? = null

    fun get(context: Context): ApiService {
        val tokenManager = TokenManager.getInstance(context)
        val baseUrl = tokenManager.getBaseUrl()
        api?.let { if (urlActual == baseUrl) return it }
        synchronized(this) {
            api?.let { if (urlActual == baseUrl) return it }
            val log = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                redactHeader("Authorization")
            }
            val cliente = OkHttpClient.Builder()
                .addInterceptor(RetryInterceptor())
                .addInterceptor(AuthInterceptor(tokenManager))
                .addInterceptor(log)
                .authenticator(TokenAuthenticator(tokenManager))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)   // el asistente IA puede tardar unos segundos
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
            val gson = GsonBuilder()
                .registerTypeAdapter(Double::class.java, DoubleTypeAdapter())
                .registerTypeAdapter(java.lang.Double::class.java, DoubleTypeAdapter())
                .create()
            val nuevo = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(cliente)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(ApiService::class.java)
            api = nuevo
            urlActual = baseUrl
            return nuevo
        }
    }
}
