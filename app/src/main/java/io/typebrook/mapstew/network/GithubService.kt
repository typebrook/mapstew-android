package io.typebrook.mapstew.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

interface GithubService {

    @Streaming
    @GET("{path}")
    suspend fun downloadFileWithFixedUrl(
        @Path("path", encoded = true) path: String
    ): Response<ResponseBody>

    @GET("mapstew/styles/rudymap.json")
    suspend fun getMapboxStyle(): Response<ResponseBody>

    companion object {
        fun basicService(): GithubService = Retrofit.Builder()
            .baseUrl("https://github.com/")
            .build()
            .create(GithubService::class.java)

        fun mapstewService(): GithubService = Retrofit.Builder()
            .baseUrl("https://typebrook.github.io/")
            .build()
            .create(GithubService::class.java)
    }
}