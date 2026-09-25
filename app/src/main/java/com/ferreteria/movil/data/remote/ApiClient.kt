package com.ferreteria.movil.data.remote

import android.content.Context
import com.ferreteria.movil.data.local.TokenManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente singleton para la construcción y configuración de Retrofit y OkHttp.
 */
object ApiClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var currentBaseUrl: String? = null

    private fun buildGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(Double::class.java, DoubleTypeAdapter())
            .registerTypeAdapter(java.lang.Double::class.java, DoubleTypeAdapter())
            .setLenient()
            .create()
    }

    private fun buildOkHttpClient(context: Context): OkHttpClient {
        val tokenManager = TokenManager.getInstance(context)

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = AuthInterceptor(tokenManager)

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun getRetrofit(context: Context): Retrofit {
        val tokenManager = TokenManager.getInstance(context)
        val baseUrl = tokenManager.getBaseUrl()

        // Si la URL base cambió o aún no existe la instancia, creamos un nuevo Retrofit
        if (retrofit == null || currentBaseUrl != baseUrl) {
            synchronized(this) {
                if (retrofit == null || currentBaseUrl != baseUrl) {
                    currentBaseUrl = baseUrl
                    retrofit = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(buildOkHttpClient(context))
                        .addConverterFactory(GsonConverterFactory.create(buildGson()))
                        .build()
                }
            }
        }
        return retrofit!!
    }

    fun getApiService(context: Context): ApiService {
        return getRetrofit(context).create(ApiService::class.java)
    }

    /**
     * Invalida la instancia en caso de actualización dinámica de URL base.
     */
    fun resetInstance() {
        synchronized(this) {
            retrofit = null
            currentBaseUrl = null
        }
    }
}
