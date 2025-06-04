package com.example.mobile_app.data;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Provides Retrofit instances for network APIs.
 */
public class NetworkModule {
    public static Retrofit provideRetrofit(String baseUrl) {
        OkHttpClient client = new OkHttpClient.Builder().build();
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
    }
}
