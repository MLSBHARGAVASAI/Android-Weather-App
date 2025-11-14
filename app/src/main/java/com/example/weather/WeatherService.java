package com.example.weather;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherService {
    @GET("v1/forecast?current_weather=true")
    Call<WeatherResponse> getWeather(@Query("latitude") double lat, @Query("longitude") double lon);
} 