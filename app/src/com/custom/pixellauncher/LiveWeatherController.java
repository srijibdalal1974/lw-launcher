package com.custom.pixellauncher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Fetches, caches, and formats live current weather for At a Glance and resolves Pixel Weather launch intents.
 */
public class LiveWeatherController {

    public interface Listener {
        void onWeatherUpdated();
    }

    private static final String TAG = "LiveWeather";
    private static final String WEATHER_PREFS = "live_weather_cache";
    private static final String KEY_TEMP_C = "temp_c";
    private static final String KEY_CODE = "weather_code";
    private static final String KEY_IS_DAY = "is_day";
    private static final String KEY_UPDATED_MS = "updated_ms";
    private static final String KEY_LAT = "cached_lat";
    private static final String KEY_LON = "cached_lon";
    private static final String KEY_LATLON_PRECISE = "cached_latlon_precise";
    private static final String KEY_LATLON_MS = "cached_latlon_ms";

    private static final String PIXEL_WEATHER_PKG = "com.google.android.apps.weather";
    private static final long REFRESH_INTERVAL_MS = 20L * 60L * 1000L;
    private static final long STALE_LOCATION_MS = 2L * 60L * 60L * 1000L;
    private static final String USER_AGENT = "LWLauncher/3.0 (Android)";

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Listener listener;

    private float tempC = Float.NaN;
    private int weatherCode = -1;
    private boolean isDay = true;
    private long updatedMs = 0L;
    private volatile boolean isFetching = false;
    private volatile boolean pendingForceRefresh = false;
    private volatile boolean pendingPreciseLocationOnly = false;
    private volatile boolean isShutdown = false;
    private volatile boolean suspectedNetworkPolicyBlock = false;
    private int consecutiveFailures = 0;
    private ConnectivityManager registeredCm = null;
    private ConnectivityManager.NetworkCallback networkCallback = null;

    private final Runnable scheduledNetworkRefresh = new Runnable() {
        @Override
        public void run() {
            if (!isShutdown) {
                refreshIfNeeded(!hasData());
            }
        }
    };

    private final Runnable scheduledRetryRefresh = new Runnable() {
        @Override
        public void run() {
            if (!isShutdown) {
                refreshIfNeeded(true);
            }
        }
    };

    public LiveWeatherController(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        loadCached();
        registerNetworkObserver();
    }

    private void loadCached() {
        SharedPreferences sp = appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE);
        if (sp.contains(KEY_TEMP_C)) {
            tempC = sp.getFloat(KEY_TEMP_C, Float.NaN);
        }
        weatherCode = sp.getInt(KEY_CODE, -1);
        isDay = sp.getBoolean(KEY_IS_DAY, true);
        updatedMs = sp.getLong(KEY_UPDATED_MS, 0L);
    }

    public boolean hasData() {
        return !Float.isNaN(tempC) && tempC > -120f && tempC < 90f && weatherCode >= 0;
    }

    public String getFormattedTemp() {
        if (!hasData()) {
            return "Weather";
        }
        boolean useFahrenheit = "US".equalsIgnoreCase(Locale.getDefault().getCountry());
        int displayTemp = useFahrenheit ? Math.round(tempC * 9f / 5f + 32f) : Math.round(tempC);
        return displayTemp + (useFahrenheit ? "\u00B0F" : "\u00B0C");
    }

    public int getIconResId() {
        if (!hasData()) {
            return R.drawable.ic_glance_partly_cloudy_day;
        }
        if (weatherCode == 0) {
            return isDay ? R.drawable.ic_glance_sun : R.drawable.ic_glance_moon;
        }
        if (weatherCode == 1 || weatherCode == 2) {
            return isDay ? R.drawable.ic_glance_partly_cloudy_day : R.drawable.ic_glance_partly_cloudy_night;
        }
        if (weatherCode == 3) {
            return R.drawable.ic_glance_cloudy;
        }
        if (weatherCode == 45 || weatherCode == 48) {
            return R.drawable.ic_glance_fog;
        }
        if ((weatherCode >= 51 && weatherCode <= 67) || (weatherCode >= 80 && weatherCode <= 82)) {
            return R.drawable.ic_glance_rain;
        }
        if ((weatherCode >= 71 && weatherCode <= 77) || weatherCode == 85 || weatherCode == 86) {
            return R.drawable.ic_glance_snow;
        }
        if (weatherCode >= 95 && weatherCode <= 99) {
            return R.drawable.ic_glance_storm;
        }
        return isDay ? R.drawable.ic_glance_partly_cloudy_day : R.drawable.ic_glance_partly_cloudy_night;
    }

    public boolean hasLocationPermission() {
        return appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public void refreshIfNeeded(boolean force) {
        if (isShutdown) return;
        long now = System.currentTimeMillis();
        if (!force && hasData() && (now - updatedMs) < REFRESH_INTERVAL_MS) {
            return;
        }
        if (isFetching) {
            if (force) {
                pendingForceRefresh = true;
            }
            return;
        }
        if (!isNetworkConnected()) {
            Log.w(TAG, "No active Wi-Fi or mobile network; waiting for connectivity");
            return;
        }
        mainHandler.removeCallbacks(scheduledRetryRefresh);
        startFetchTask();
    }

    private void startFetchTask() {
        if (isShutdown) return;
        isFetching = true;
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    boolean success = false;
                    try {
                        success = fetchWeatherSync();
                    } finally {
                        isFetching = false;
                        if (isShutdown) return;
                        if (!success) {
                            scheduleTransientRetry();
                        } else {
                            suspectedNetworkPolicyBlock = false;
                            consecutiveFailures = 0;
                            mainHandler.removeCallbacks(scheduledRetryRefresh);
                        }
                        if (pendingForceRefresh) {
                            pendingForceRefresh = false;
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    refreshIfNeeded(true);
                                }
                            });
                        }
                    }
                }
            });
        } catch (Exception e) {
            isFetching = false;
            Log.w(TAG, "Failed to submit weather task", e);
        }
    }

    private void scheduleTransientRetry() {
        if (isShutdown || !isNetworkConnected()) return;
        if (consecutiveFailures >= 3) return;
        long delayMs = consecutiveFailures == 0 ? 3000L : (consecutiveFailures == 1 ? 10000L : 25000L);
        consecutiveFailures++;
        mainHandler.removeCallbacks(scheduledRetryRefresh);
        mainHandler.postDelayed(scheduledRetryRefresh, delayMs);
    }

    private void registerNetworkObserver() {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            registeredCm = cm;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    scheduleSyncOnNetworkReady();
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        scheduleSyncOnNetworkReady();
                    }
                }
            };
            NetworkRequest req = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(req, networkCallback);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to register NetworkCallback", t);
        }
    }

    private void scheduleSyncOnNetworkReady() {
        if (isShutdown) return;
        consecutiveFailures = 0;
        mainHandler.removeCallbacks(scheduledNetworkRefresh);
        // Short delay lets DHCP/DNS finish settling right after Wi-Fi or Mobile Data toggles on.
        mainHandler.postDelayed(scheduledNetworkRefresh, 800L);
    }

    private boolean isNetworkConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (hasInternetTransport(cm, cm.getActiveNetwork())) {
                return true;
            }
            for (Network n : cm.getAllNetworks()) {
                if (hasInternetTransport(cm, n)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean hasInternetTransport(ConnectivityManager cm, Network net) {
        if (cm == null || net == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(net);
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false;
        }
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    public boolean isBackgroundDataRestricted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            return cm != null && cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isNetworkPolicyLikelyBlocked() {
        return !hasData() && (isBackgroundDataRestricted() || suspectedNetworkPolicyBlock);
    }

    public static void openAppNetworkSettings(Context ctx) {
        if (ctx == null) return;
        Uri pkgUri = Uri.parse("package:" + ctx.getPackageName());
        String[] actions = new String[]{
                android.provider.Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        };
        for (String act : actions) {
            try {
                Intent intent = new Intent(act, pkgUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.resolveActivity(ctx.getPackageManager()) != null) {
                    ctx.startActivity(intent);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private boolean fetchWeatherSync() {
        double[] coords = resolveBestLocation();
        if (coords == null) {
            Log.w(TAG, "Could not resolve any coordinates for weather");
            return false;
        }
        Log.i(TAG, String.format(Locale.US, "Fetching weather for lat=%.4f lon=%.4f", coords[0], coords[1]));
        if (fetchFromOpenMeteo(coords[0], coords[1])) {
            return true;
        }
        return fetchFromWttrFallback(coords[0], coords[1]);
    }

    private boolean fetchFromOpenMeteo(double lat, double lon) {
        String url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day&timezone=auto",
                lat,
                lon
        );
        String body = httpGet(url, 4500, 5000);
        if (body == null) return false;
        try {
            JSONObject root = new JSONObject(body);
            JSONObject current = root.optJSONObject("current");
            if (current == null || !current.has("temperature_2m")) return false;
            float nextTempC = (float) current.getDouble("temperature_2m");
            int nextCode = current.optInt("weather_code", 0);
            boolean nextDay = current.optInt("is_day", 1) == 1;
            applyFetchedWeather(nextTempC, nextCode, nextDay, "Open-Meteo");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Open-Meteo parse error", e);
            return false;
        }
    }

    private boolean fetchFromWttrFallback(double lat, double lon) {
        String url = String.format(Locale.US, "https://wttr.in/%.3f,%.3f?format=j1", lat, lon);
        String body = httpGet(url, 4500, 6000);
        if (body == null) return false;
        try {
            JSONObject root = new JSONObject(body);
            JSONArray cc = root.optJSONArray("current_condition");
            if (cc == null || cc.length() == 0) return false;
            JSONObject cur = cc.getJSONObject(0);
            float nextTempC = Float.parseFloat(cur.getString("temp_C"));
            int wttrCode = Integer.parseInt(cur.optString("weatherCode", "113"));
            int wmo = mapWttrToWmo(wttrCode);
            applyFetchedWeather(nextTempC, wmo, true, "wttr.in");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "wttr.in parse error", e);
            return false;
        }
    }

    private int mapWttrToWmo(int wttr) {
        if (wttr == 113) return 0;
        if (wttr == 116) return 2;
        if (wttr == 119 || wttr == 122) return 3;
        if (wttr == 143 || wttr == 248 || wttr == 260) return 45;
        if (wttr >= 386) return 95;
        if (wttr >= 320 && wttr <= 377) return 71;
        if (wttr >= 176) return 61;
        return 1;
    }

    private void applyFetchedWeather(final float nextTempC, final int nextCode, final boolean nextDay, String source) {
        final long now = System.currentTimeMillis();
        Log.i(TAG, "Weather updated from " + source + ": tempC=" + nextTempC + " code=" + nextCode + " isDay=" + nextDay);
        SharedPreferences sp = appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE);
        sp.edit()
                .putFloat(KEY_TEMP_C, nextTempC)
                .putInt(KEY_CODE, nextCode)
                .putBoolean(KEY_IS_DAY, nextDay)
                .putLong(KEY_UPDATED_MS, now)
                .apply();

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                tempC = nextTempC;
                weatherCode = nextCode;
                isDay = nextDay;
                updatedMs = now;
                if (listener != null) {
                    listener.onWeatherUpdated();
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private double[] resolveBestLocation() {
        Location bestLoc = null;
        if (hasLocationPermission()) {
            try {
                LocationManager lm = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) {
                    String[] providers = new String[]{
                            "fused",
                            LocationManager.NETWORK_PROVIDER,
                            LocationManager.PASSIVE_PROVIDER,
                            LocationManager.GPS_PROVIDER
                    };
                    for (String p : providers) {
                        try {
                            Location l = lm.getLastKnownLocation(p);
                            if (l != null && (bestLoc == null || l.getTime() > bestLoc.getTime())) {
                                bestLoc = l;
                            }
                        } catch (Exception ignored) {}
                    }
                    long ageMs = (bestLoc != null) ? (System.currentTimeMillis() - bestLoc.getTime()) : Long.MAX_VALUE;
                    if ((bestLoc == null || ageMs > STALE_LOCATION_MS) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            String activeProv = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                                    ? LocationManager.NETWORK_PROVIDER
                                    : (lm.isProviderEnabled("fused") ? "fused" : null);
                            if (activeProv != null && !pendingPreciseLocationOnly) {
                                pendingPreciseLocationOnly = true;
                                lm.getCurrentLocation(activeProv, null, executor, new Consumer<Location>() {
                                    @Override
                                    public void accept(Location freshLoc) {
                                        pendingPreciseLocationOnly = false;
                                        if (freshLoc != null) {
                                            saveCoords(freshLoc.getLatitude(), freshLoc.getLongitude(), true);
                                            refreshIfNeeded(true);
                                        }
                                    }
                                });
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Location lookup failed", e);
            }
        }

        if (bestLoc != null) {
            saveCoords(bestLoc.getLatitude(), bestLoc.getLongitude(), true);
            return new double[]{bestLoc.getLatitude(), bestLoc.getLongitude()};
        }

        double[] ipCoords = fetchIpCoordinates();
        if (ipCoords != null) {
            saveCoords(ipCoords[0], ipCoords[1], false);
            return ipCoords;
        }

        SharedPreferences sp = appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE);
        if (sp.contains(KEY_LAT) && sp.contains(KEY_LON)) {
            return new double[]{sp.getFloat(KEY_LAT, 0f), sp.getFloat(KEY_LON, 0f)};
        }
        return null;
    }

    private void saveCoords(double lat, double lon, boolean precise) {
        SharedPreferences sp = appContext.getSharedPreferences(WEATHER_PREFS, Context.MODE_PRIVATE);
        if (!precise && sp.getBoolean(KEY_LATLON_PRECISE, false) && sp.contains(KEY_LAT)) {
            return;
        }
        sp.edit()
                .putFloat(KEY_LAT, (float) lat)
                .putFloat(KEY_LON, (float) lon)
                .putBoolean(KEY_LATLON_PRECISE, precise)
                .putLong(KEY_LATLON_MS, System.currentTimeMillis())
                .apply();
    }

    private double[] fetchIpCoordinates() {
        String ipInfo = httpGet("https://ipinfo.io/json", 3500, 4000);
        if (ipInfo != null) {
            try {
                JSONObject o = new JSONObject(ipInfo);
                String loc = o.optString("loc", "");
                if (loc.contains(",")) {
                    String[] parts = loc.split(",");
                    return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
                }
            } catch (Exception ignored) {}
        }
        String geoJson = httpGet("https://get.geojs.io/v1/ip/geo.json", 3500, 4000);
        if (geoJson != null) {
            try {
                JSONObject o = new JSONObject(geoJson);
                if (o.has("latitude") && o.has("longitude")) {
                    return new double[]{
                            Double.parseDouble(o.getString("latitude")),
                            Double.parseDouble(o.getString("longitude"))
                    };
                }
            } catch (Exception ignored) {}
        }
        String ipWho = httpGet("https://ipwho.is/", 3500, 4000);
        if (ipWho != null) {
            try {
                JSONObject o = new JSONObject(ipWho);
                if (o.optBoolean("success", false) && o.has("latitude") && o.has("longitude")) {
                    return new double[]{o.getDouble("latitude"), o.getDouble("longitude")};
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String httpGet(String urlStr, int connectMs, int readMs) {
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectMs);
            conn.setReadTimeout(readMs);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "HTTP " + code + " for " + urlStr);
                return null;
            }
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            String msg = e.getMessage();
            if (isNetworkConnected() && (msg == null
                    || msg.contains("EPERM")
                    || msg.contains("Permission denied")
                    || msg.contains("Operation not permitted")
                    || e instanceof java.net.SocketException
                    || e instanceof java.net.UnknownHostException)) {
                suspectedNetworkPolicyBlock = true;
            }
            Log.w(TAG, "HTTP error for " + urlStr + ": " + e.getClass().getSimpleName() + ": " + msg);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void shutdown() {
        isShutdown = true;
        listener = null;
        mainHandler.removeCallbacks(scheduledNetworkRefresh);
        mainHandler.removeCallbacks(scheduledRetryRefresh);
        if (registeredCm != null && networkCallback != null) {
            try {
                registeredCm.unregisterNetworkCallback(networkCallback);
            } catch (Throwable ignored) {}
            networkCallback = null;
        }
        executor.shutdownNow();
    }

    public static void openPixelWeather(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        String[] candidatePkgs = new String[]{
                PIXEL_WEATHER_PKG,
                "com.motorola.timeweatherwidget",
                "com.sec.android.daemonapp"
        };
        for (String pkg : candidatePkgs) {
            try {
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(launch);
                    return;
                }
            } catch (Exception ignored) {}
        }
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"));
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(web);
        } catch (Exception ignored) {}
    }
}
