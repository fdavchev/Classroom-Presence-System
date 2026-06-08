package com.example.teacherapp_nfc.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // ─── IMPORTANT: Change this to your computer's IP address ───
    // Find it by opening a terminal and typing:
    //   Windows → ipconfig   (look for "IPv4 Address")
    //   Mac/Linux → ifconfig (look for "inet" on your Wi-Fi)
    // Example: "http://192.168.1.42:8000/api/"
    // Both phones must be on the SAME Wi-Fi network as your computer.
    private const val BASE_URL = "BuildConfig.BASE_URL"

    // This is where we store the login token after the teacher logs in.
    // It starts empty — nobody is logged in yet.
    private var jwtToken: String = ""

    // Call this once right after a successful login to save the token.
    fun setToken(token: String) {
        jwtToken = token
    }

    // Clear the token when the teacher logs out.
    fun clearToken() {
        jwtToken = ""
    }

    // This is the "interceptor" — it runs before EVERY network request.
    // Its job: if we have a token, attach it as a badge to the request.
    private val authInterceptor = okhttp3.Interceptor { chain ->
        val original = chain.request()
        val request = if (jwtToken.isNotBlank()) {
            original.newBuilder()
                .header("Authorization", "Bearer $jwtToken")
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    // This logs every network request to Logcat so you can debug easily.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // The actual HTTP client — think of it as the phone's internet engine.
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)   // attach badge
        .addInterceptor(loggingInterceptor) // log everything
        .build()

    // Retrofit turns our Kotlin interface into real HTTP calls.
    val nfcApiService: NfcApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NfcApiService::class.java)
    }
}