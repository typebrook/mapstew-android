package com.example.sample.network

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Streaming

interface GithubService {

    @Streaming
    @GET("typebrook/mapstew/releases/download/daily-taiwan-pbf/taiwan-daily.osm.pbf")
    fun downloadFileWithFixedUrl(): Call<ResponseBody>
}