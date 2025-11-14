package com.example.weather;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface GeoService {
    @GET("search?format=json&limit=1")
    Call<List<GeoResponse>> getLocation(@Query("q") String cityName);
} 