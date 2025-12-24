package com.upgovt.ashadiary.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiClient - Retrofit client configuration
 * Singleton instance for API calls
 */
object ApiClient {
    
    // TODO: Configure actual server URL
    private const val BASE_URL = "https://ashadiary.up.gov.in/api/"
    
    // For development/testing
    private const val DEV_BASE_URL = "http://10.0.2.2:8000/api/"  // Android emulator localhost
    
    private const val CONNECT_TIMEOUT = 30L  // seconds
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L
    
    private var apiService: ASHADiaryApiService? = null
    
    /**
     * Get configured API service instance
     */
    fun getApiService(context: Context, isDevelopment: Boolean = false): ASHADiaryApiService {
        if (apiService == null) {
            synchronized(this) {
                if (apiService == null) {
                    apiService = createApiService(context, isDevelopment)
                }
            }
        }
        return apiService!!
    }
    
    /**
     * Create Retrofit API service
     */
    private fun createApiService(context: Context, isDevelopment: Boolean): ASHADiaryApiService {
        val okHttpClient = createOkHttpClient(context, isDevelopment)
        val gson = createGson()
        
        val retrofit = Retrofit.Builder()
            .baseUrl(if (isDevelopment) DEV_BASE_URL else BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        return retrofit.create(ASHADiaryApiService::class.java)
    }
    
    /**
     * Create OkHttp client with interceptors
     */
    private fun createOkHttpClient(context: Context, isDevelopment: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
        
        // Add logging interceptor in development
        if (isDevelopment) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        // Add auth token interceptor
        builder.addInterceptor(AuthInterceptor(context))
        
        // Add retry interceptor for network errors
        builder.addInterceptor(RetryInterceptor(maxRetries = 3))
        
        return builder.build()
    }
    
    /**
     * Create Gson with custom configuration
     */
    private fun createGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .serializeNulls()
            .create()
    }
    
    /**
     * Reset API service (for logout)
     */
    fun resetApiService() {
        apiService = null
    }
}

/**
 * Auth token interceptor
 */
class AuthInterceptor(private val context: Context) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val originalRequest = chain.request()
        
        // Get auth token from secure storage
        // TODO: Implement token storage
        val token = getAuthToken()
        
        val request = if (token != null && !originalRequest.url.pathSegments.contains("login")) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        
        return chain.proceed(request)
    }
    
    private fun getAuthToken(): String? {
        // TODO: Get from EncryptedSharedPreferences
        return null
    }
}

/**
 * Retry interceptor for failed requests
 */
class RetryInterceptor(private val maxRetries: Int = 3) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        var request = chain.request()
        var response: okhttp3.Response? = null
        var tryCount = 0
        
        while (tryCount < maxRetries) {
            try {
                response = chain.proceed(request)
                
                // Success or non-retryable error
                if (response.isSuccessful || !isRetryableError(response.code)) {
                    return response
                }
                
                response.close()
                tryCount++
                
                // Wait before retry (exponential backoff)
                if (tryCount < maxRetries) {
                    Thread.sleep((500L * (1 shl tryCount)).coerceAtMost(5000))
                }
                
            } catch (e: Exception) {
                if (tryCount >= maxRetries - 1) {
                    throw e
                }
                tryCount++
                Thread.sleep((500L * (1 shl tryCount)).coerceAtMost(5000))
            }
        }
        
        return response ?: throw Exception("Max retries exceeded")
    }
    
    private fun isRetryableError(code: Int): Boolean {
        return code in listOf(408, 429, 500, 502, 503, 504)
    }
}
