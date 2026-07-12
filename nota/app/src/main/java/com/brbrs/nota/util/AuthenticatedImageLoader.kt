package com.brbrs.nota.util

import android.content.Context
import coil.ImageLoader
import com.brbrs.nota.auth.NotaSession
import okhttp3.OkHttpClient

fun buildAuthenticatedImageLoader(
    context: Context,
    session: NotaSession,
    httpClient: OkHttpClient,
): ImageLoader {
    val authedClient = httpClient.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", session.basicAuthHeader())
                    .build()
            )
        }
        .build()

    return ImageLoader.Builder(context)
        .okHttpClient(authedClient)
        .build()
}
