package com.example.weather;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "weather_prefs";
    private static final String PREF_NOTIFICATION = "show_notification";
    private static final String CHANNEL_ID = "weather_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String TAG = "MainActivity";

    private static final String[] POPULAR_CITIES = new String[] {
        "New York", "London", "Paris", "Tokyo", "Sydney", "Mumbai", "Beijing", "Moscow", "Cairo", "Rio de Janeiro",
        "Los Angeles", "Chicago", "Toronto", "Berlin", "Madrid", "Rome", "Istanbul", "Dubai", "Singapore", "Hong Kong",
        "Bangkok", "Seoul", "Johannesburg", "Mexico City", "Buenos Aires", "Jakarta", "Lagos", "Nairobi", "Cape Town", "Hyderabad"
    };

    private AutoCompleteTextView autoCompleteCity;
    private TextView textCityName, textTemperatureEmoji, textWindspeedCard, textUpdateTimeCard;
    private View cardWeather;
    private SharedPreferences prefs;
    private NotificationManagerCompat notificationManager;
    private ArrayAdapter<String> cityAdapter;
    private List<String> citySuggestions = new ArrayList<>();
    private TextInputLayout textInputLayout;
    private com.google.android.material.button.MaterialButton buttonGetWeather;

    private Retrofit geoRetrofit, weatherRetrofit;
    private GeoService geoService;
    private WeatherService weatherService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        autoCompleteCity = findViewById(R.id.autoCompleteCity);
        textCityName = findViewById(R.id.textCityName);
        textTemperatureEmoji = findViewById(R.id.textTemperatureEmoji);
        textWindspeedCard = findViewById(R.id.textWindspeedCard);
        textUpdateTimeCard = findViewById(R.id.textUpdateTimeCard);
        cardWeather = findViewById(R.id.cardWeather);
        textInputLayout = findViewById(R.id.textInputLayout);
        buttonGetWeather = findViewById(R.id.buttonGetWeather);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        notificationManager = NotificationManagerCompat.from(this);
        createNotificationChannel();

        // Setup Retrofit
        OkHttpClient geoClient = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request().newBuilder()
                            .header("User-Agent", BuildConfig.NOMINATIM_USER_AGENT)
                            .header("Referer", BuildConfig.NOMINATIM_REFERER)
                            .header("Accept-Language", Locale.getDefault().toLanguageTag())
                            .build();
                    return chain.proceed(request);
                })
                .build();

        geoRetrofit = new Retrofit.Builder()
                .baseUrl("https://nominatim.openstreetmap.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(geoClient)
                .build();
        geoService = geoRetrofit.create(GeoService.class);

        weatherRetrofit = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherService = weatherRetrofit.create(WeatherService.class);

        // Setup city autocomplete with predefined suggestions
        cityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, POPULAR_CITIES);
        autoCompleteCity.setAdapter(cityAdapter);
        autoCompleteCity.setThreshold(1);
        autoCompleteCity.setTextColor(getColorFromAttr(android.R.attr.textColorPrimary));
        autoCompleteCity.setDropDownBackgroundResource(android.R.color.background_light);
        autoCompleteCity.setOnItemClickListener((parent, view, position, id) -> {
            String city = cityAdapter.getItem(position);
            if (city != null && city.length() > 2 && !city.equalsIgnoreCase("ok") && !city.equalsIgnoreCase("hi")) {
                fetchCoordinatesAndWeather(city);
            }
        });
        autoCompleteCity.setOnEditorActionListener((v, actionId, event) -> {
            String city = autoCompleteCity.getText().toString().trim();
            if (!city.isEmpty() && city.length() > 2 && !city.equalsIgnoreCase("ok") && !city.equalsIgnoreCase("hi")) {
                fetchCoordinatesAndWeather(city);
                return true;
            } else {
                showError("Please enter a valid city name");
            }
            return false;
        });

        buttonGetWeather.setOnClickListener(v -> {
            String city = autoCompleteCity.getText().toString().trim();
            if (!city.isEmpty() && city.length() > 2 && !city.equalsIgnoreCase("ok") && !city.equalsIgnoreCase("hi")) {
                fetchCoordinatesAndWeather(city);
            } else {
                showError("Please enter a valid city name");
            }
        });

        cardWeather.setVisibility(View.GONE);
    }

    private void fetchCitySuggestions(String query) {
        // Use Nominatim for suggestions
        geoService.getLocation(query).enqueue(new Callback<List<GeoResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<GeoResponse>> call, @NonNull Response<List<GeoResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    citySuggestions.clear();
                    for (GeoResponse geo : response.body()) {
                        citySuggestions.add(geo.display_name);
                    }
                    cityAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<GeoResponse>> call, @NonNull Throwable t) {
                // Ignore autocomplete errors
            }
        });
    }

    private void fetchCoordinatesAndWeather(String cityName) {
        if (!isNetworkAvailable()) {
            showError("Network unavailable");
            return;
        }
        geoService.getLocation(cityName).enqueue(new Callback<List<GeoResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<GeoResponse>> call, @NonNull Response<List<GeoResponse>> response) {
                if (!response.isSuccessful()) {
                    handleGeoError(response);
                    return;
                }
                if (response.body() != null && !response.body().isEmpty()) {
                    GeoResponse geo = response.body().get(0);
                    double lat = Double.parseDouble(geo.lat);
                    double lon = Double.parseDouble(geo.lon);
                    fetchWeather(cityName, lat, lon);
                } else {
                    showError("City not found");
                }
            }
            @Override
            public void onFailure(@NonNull Call<List<GeoResponse>> call, @NonNull Throwable t) {
                Log.e(TAG, "Geo lookup failed", t);
                showError("Failed to fetch location");
            }
        });
    }

    private void fetchWeather(String cityName, double lat, double lon) {
        weatherService.getWeather(lat, lon).enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().current_weather != null) {
                    WeatherResponse.CurrentWeather w = response.body().current_weather;
                    // Show card and set values
                    String emoji = getWeatherEmoji(w.temperature);
                    textCityName.setText(cityName);
                    textTemperatureEmoji.setText(emoji + " " + String.format(Locale.getDefault(), "%.1f°C", w.temperature));
                    textWindspeedCard.setText(String.format(Locale.getDefault(), "Windspeed: %.1f m/s", w.windspeed));
                    textUpdateTimeCard.setText(String.format(Locale.getDefault(), "Updated: %s", w.time));
                    cardWeather.setAlpha(0f);
                    cardWeather.setVisibility(View.VISIBLE);
                    cardWeather.animate().alpha(1f).setDuration(400).start();
                    // Scroll to card
                    cardWeather.post(() -> cardWeather.getParent().requestChildFocus(cardWeather, cardWeather));
                    // Always show notification (or remove this line if you want to disable notifications entirely)
                    showWeatherNotification(cityName, w.temperature);
                } else {
                    showError("Weather not found");
                }
            }
            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "Weather lookup failed", t);
                showError("Failed to fetch weather");
            }
        });
    }

    private void showWeatherNotification(String city, double temp) {
        String content;
        int iconRes;
        if (temp < 10) {
            content = "❄️ Cold: " + String.format(Locale.getDefault(), "%.1f°C", temp);
            iconRes = R.drawable.ic_cold;
        } else if (temp > 35) {
            content = "🔥 Hot: " + String.format(Locale.getDefault(), "%.1f°C", temp);
            iconRes = R.drawable.ic_hot;
        } else {
            content = "☁️ " + String.format(Locale.getDefault(), "%.1f°C", temp);
            iconRes = R.drawable.ic_neutral;
        }
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconRes)
                .setContentTitle(city)
                .setContentText(content)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Weather Channel";
            String description = "Shows persistent weather notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showError(String message) {
        cardWeather.setVisibility(View.GONE);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        }
        return false;
    }

    private boolean isPopularCity(String city) {
        for (String c : POPULAR_CITIES) {
            if (c.equalsIgnoreCase(city.trim())) return true;
        }
        return false;
    }

    private int getColorFromAttr(int attr) {
        // Helper for theme-based color
        int[] attrs = new int[]{attr};
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }

    private String getWeatherEmoji(double temp) {
        if (temp < 10) return "❄️";
        if (temp > 35) return "🔥";
        if (temp > 28) return "☀️";
        if (temp > 18) return "🌤️";
        if (temp > 10) return "🌧️";
        return "☁️";
    }

    private void handleGeoError(Response<?> response) {
        int code = response.code();
        String message;
        if (code == 403) {
            message = "Location lookup blocked. Update the Nominatim User-Agent/Referer.";
        } else {
            message = "Location lookup failed (" + code + ")";
        }
        Log.e(TAG, "Geo lookup error: code=" + code + ", message=" + response.message());
        showError(message);
    }
}