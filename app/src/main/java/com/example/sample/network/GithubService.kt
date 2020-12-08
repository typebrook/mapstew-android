package com.example.sample.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Streaming

interface GithubService {

    @Streaming
    @GET("{path}")
    suspend fun downloadFileWithFixedUrl(
        @Path("path", encoded = true) path: String
    ): Response<ResponseBody>
}