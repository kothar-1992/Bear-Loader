package com.bearmod.loader.network

import android.content.Context
import com.bearmod.loader.config.KeyAuthConfig
import com.bearmod.loader.data.api.KeyAuthApiService
import com.bearmod.loader.data.api.OTAApiService
import com.bearmod.loader.data.repository.AuthRepository
import com.bearmod.loader.data.repository.OTARepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory class for creating network components
 */
object NetworkFactory {
    
    // KeyAuth API base URL from config
    private val BASE_URL = KeyAuthConfig.API_BASE_URL
    
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun createOTAOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // 5 minutes for large file downloads
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    
    private fun createRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun createOTARetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.github.com/") // Base URL for OTA, actual URLs will be dynamic
            .client(createOTAOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private fun createApiService(): KeyAuthApiService {
        return createRetrofit().create(KeyAuthApiService::class.java)
    }

    private fun createOTAApiService(): OTAApiService {
        return createOTARetrofit().create(OTAApiService::class.java)
    }

    fun createKeyAuthRepository(context: Context): AuthRepository {
        return AuthRepository(createApiService(), context)
    }

    fun createOTARepository(context: Context): OTARepository {
        return OTARepository(context, createOTAApiService())
    }
}
