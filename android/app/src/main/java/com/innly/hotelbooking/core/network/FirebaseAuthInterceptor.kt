package com.innly.hotelbooking.core.network

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class FirebaseAuthInterceptor @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val currentUser = firebaseAuth.currentUser
        val token = currentUser?.let { user ->
            runCatching { Tasks.await(user.getIdToken(false)).token }.getOrNull()
        }

        val request = chain.request().newBuilder().apply {
            token?.let { header("Authorization", "Bearer $it") }
        }.build()

        return chain.proceed(request)
    }
}
