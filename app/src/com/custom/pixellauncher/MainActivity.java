package com.custom.pixellauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.app.WallpaperManager;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "PixelLauncherPrefs";
    private static final String KEY_PAGES_JSON = "home_pages_json";
    private static final String KEY_PAGE_WIDGETS_JSON = "page_widgets_json";
    private static final String PREF_KEY_GRID_MODE = "pref_grid_mode";
    public static final int GRID_MODE_4X5 = 0;  // 4 columns x 5 rows (Standard 60dp icons - Pixel Standard)
    public static final int GRID_MODE_5X5 = 1;  // 5 columns x 5 rows (Medium 54dp icons - Pixel Expressive)
    public static final int GRID_MODE_16X9 = 2; // 9 columns x 16 rows (Compact 36dp icons - Ultra Dense)
    private int currentGridMode = GRID_MODE_4X5;

    private int getGridColumns() {
        if (currentGridMode == GRID_MODE_16X9) return 9;
        if (currentGridMode == GRID_MODE_5X5) return 5;
        return 4;
    }

    private int getGridRows() {
        if (currentGridMode == GRID_MODE_16X9) return 16;
        return 5;
    }

    private int getPageSlots() {
        return getGridColumns() * getGridRows();
    }

    private List<LauncherItem> createEmptyPage() {
        int slots = getPageSlots();
        List<LauncherItem> page = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            page.add(null);
        }
        return page;
    }

    private boolean isPageEmpty(List<LauncherItem> page) {
        if (page == null || page.isEmpty()) return true;
        for (LauncherItem item : page) {
            if (item != null) return false;
        }
        return true;
    }

    private boolean isPageEmpty(int pageIdx) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return true;
        List<LauncherItem> page = pages.get(pageIdx);
        if (page != null) {
            for (LauncherItem item : page) {
                if (item != null) return false;
            }
        }
        List<PlacedWidget> widgets = getPlacedWidgetsForPage(pageIdx);
        if (widgets != null && !widgets.isEmpty()) {
            for (PlacedWidget w : widgets) {
                try {
                    if (appWidgetManager != null && appWidgetManager.getAppWidgetInfo(w.id) != null) {
                        return false;
                    }
                } catch (Exception ignored) {}
            }
        }
        if (isAtAGlanceVisible() && getAtAGlancePage() == pageIdx) return false;
        return true;
    }

    private void shiftWidgetPagesDown(int removedPageIdx) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGE_WIDGETS_JSON, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                JSONArray newArr = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    int p = obj.optInt("page", -1);
                    int id = obj.optInt("id", -1);
                    if (p == removedPageIdx) {
                        try {
                            if (appWidgetHost != null) appWidgetHost.deleteAppWidgetId(id);
                        } catch (Exception ignored) {}
                    } else if (p > removedPageIdx) {
                        obj.put("page", p - 1);
                        newArr.put(obj);
                    } else {
                        newArr.put(obj);
                    }
                }
                prefs.edit().putString(KEY_PAGE_WIDGETS_JSON, newArr.toString()).apply();
            } catch (Exception ignored) {}
        }
        int glancePage = getAtAGlancePage();
        if (glancePage == removedPageIdx) {
            prefs.edit().putInt(PREF_KEY_AT_A_GLANCE_PAGE, Math.max(0, removedPageIdx - 1)).apply();
        } else if (glancePage > removedPageIdx) {
            prefs.edit().putInt(PREF_KEY_AT_A_GLANCE_PAGE, glancePage - 1).apply();
        }
    }

    private static class PlacedWidget {
        int id;
        int page;
        int col;
        int row;
        int spanCols;
        int spanRows;
    }

    private static class WidgetDragTag {
        PlacedWidget pw;
        String label;
        WidgetDragTag(PlacedWidget pw, String label) {
            this.pw = pw;
            this.label = label;
        }
    }

    private int findFirstEmptySlot(int pageIdx) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return -1;
        List<LauncherItem> list = pages.get(pageIdx);
        int cols = getGridColumns();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) {
                int col = i % cols;
                int row = i / cols;
                if (!isCellOccupiedByOverlay(pageIdx, col, row, -1, false)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findFirstEmptySlotInList(List<LauncherItem> list) {
        if (list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null) return i;
        }
        return -1;
    }

    private View rootLayout;
    private View homeScreen;
    private LinearLayout appDrawer;
    private View drawerDragHandle;
    private View drawerSearchBar;
    private FrameLayout drawerGoogleWidgetContainer;
    private AppWidgetHostView drawerSearchWidgetHostView;
    private ImageView ivDrawerSearchIcon;
    private ImageView btnCloseDrawer;
    private GridView gvApps;
    private DrawerFastScrollbarView drawerScrollbar;
    private EditText etDrawerSearch;
    private TextView tvDate;
    private boolean isDrawerVisiblyOpenForBars = false;

    private SmoothHomePager hsvHomePages;
    private LinearLayout llHomePages;
    private LinearLayout layoutPageDots;

    private final List<List<LauncherItem>> pages = new ArrayList<>();
    private final List<GridView> pageGridViews = new ArrayList<>();
    private final List<HomeItemAdapter> pageAdapters = new ArrayList<>();
    private final List<FrameLayout> pageGridOverlays = new ArrayList<>();
    private final List<View> pageGhostPreviews = new ArrayList<>();
    private final Map<Integer, View> pageGlanceViews = new HashMap<>();
    private final Map<Integer, List<View>> pageWidgetWrappers = new HashMap<>();
    private int currentPage = 0;

    private static final String KEY_DOCK_JSON = "dock_apps_json";
    private final List<AppInfo> dockApps = new ArrayList<>(5);
    private final FrameLayout[] dockSlots = new FrameLayout[5];
    private final ImageView[] dockIcons = new ImageView[5];

    private static final int APPWIDGET_HOST_ID = 2048;
    private static final int REQUEST_BIND_APPWIDGET = 7777;
    private static final int REQUEST_BIND_CUSTOM_APPWIDGET = 7778;
    private static final int REQUEST_BIND_DRAWER_APPWIDGET = 7779;
    private static final int REQUEST_LAUNCH_LENS = 7780;
    private static final int REQUEST_PICK_APPWIDGET = 102;
    private static final int REQUEST_CREATE_APPWIDGET = 103;
    private static final int REQUEST_COARSE_LOCATION = 1104;
    private static final String PREF_KEY_WEATHER_LOC_PROMPTED = "pref_weather_loc_prompted";
    private static final String PREF_KEY_DATA_SAVER_PROMPTED = "pref_weather_data_saver_prompted";
    private LiveWeatherController liveWeatherController;
    private DisplayManager.DisplayListener displayListener;
    private LinearLayout llDrawerHeaderOverlay;
    private LinearLayout llDrawerPredictions;
    private TextView tvDrawerAllAppsHeader;
    private TextView tvDrawerTypeaheadHint;
    private ScrollView svDrawerSearchResults;
    private LinearLayout llDrawerSearchResultsContent;
    private ImageView btnDrawerMic;
    private ImageView btnDrawerLens;
    private int drawerHeaderHeightPx = 0;
    private int drawerSearchSeq = 0;
    private Runnable pendingWebSuggestRunnable;
    private String lastDrawerSearchQuery = "";
    private final List<String> lastDrawerSuggestions = new ArrayList<>();
    private ValueAnimator drawerSearchMarginAnimator;
    private static final String PREF_KEY_APP_LAUNCH_HISTORY = "pref_app_launch_history";
    private static final String PREF_GOOGLE_WIDGET_ID = "google_search_widget_id";
    private static final String PREF_DRAWER_GOOGLE_WIDGET_ID = "drawer_google_search_widget_id";
    private static final String PREF_KEY_DRAWER_USE_GOOGLE_WIDGET = "pref_drawer_use_google_widget";

    private static final int SPECIAL_ITEM_NONE = 0;
    private static final int SPECIAL_ITEM_SEARCH_BAR = 1;
    private static final int SPECIAL_ITEM_CALENDAR = 2;
    private static final int SPECIAL_ITEM_WIDGET = 3;
    private int draggedSpecialItem = SPECIAL_ITEM_NONE;
    private int draggedWidgetId = -1;
    private int draggedWidgetSourcePage = -1;
    private int draggedWidgetSpanCols = 2;
    private int draggedWidgetSpanRows = 2;
    private String draggedWidgetLabel = "";
    private static final int DRAG_SOURCE_DOCK = -2;

    private static final String PREF_KEY_SHOW_SEARCH_BAR = "pref_show_search_bar";
    private static final String PREF_KEY_SHOW_AT_A_GLANCE = "pref_show_at_a_glance";
    private static final String PREF_KEY_SEARCH_POS_TOP = "pref_search_bar_pos_top";
    private static final String PREF_KEY_AT_A_GLANCE_PAGE = "pref_at_a_glance_page";
    private static final String PREF_KEY_AT_A_GLANCE_ROW = "pref_at_a_glance_row";
    private static final String PREF_KEY_AT_A_GLANCE_COL = "pref_at_a_glance_col";
    private static final String PREF_KEY_V206_MIGRATED = "pref_v206_migrated_grid";
    private static final String PREF_KEY_V303_COMPACT_GLANCE_MIGRATED = "pref_v303_compact_glance_migrated";

    private static final long LONG_PRESS_ICON_HOLD_MS = 560L;
    private static final long LONG_PRESS_DRAWER_HOLD_MS = 620L;
    private static final long LONG_PRESS_SPECIAL_HOLD_MS = 680L;
    private static final long LONG_PRESS_WIDGET_HOLD_MS = 720L;
    private static final float DRAG_MOVE_THRESHOLD_ICON_DP = 28f;
    private static final float DRAG_MOVE_THRESHOLD_SPECIAL_DP = 34f;
    private static final float EDGE_SCROLL_MARGIN_DP = 28f;
    private static final long EDGE_SCROLL_HOLD_MS = 620L;
    private static final long EDGE_SCROLL_COOLDOWN_MS = 700L;
    private static final long NEW_PAGE_EDGE_COOLDOWN_MS = 1200L;

    private Runnable searchBarLongPressRunnable = null;
    private Runnable atAGlanceLongPressRunnable = null;
    private Runnable widgetLongPressRunnable = null;
    private Runnable pendingDragHoldRunnable = null;
    private float touchDownRawX = 0f;
    private float touchDownRawY = 0f;
    private long touchDownTimeMs = 0L;
    private int touchDownSeq = 0;
    private boolean touchMovedBeyondStillSlop = false;
    private boolean touchMovedBeyondTapSlop = false;
    private long lastSwipeEndTimeMs = 0L;
    private boolean isMultiTouchGesture = false;
    private boolean isPrimaryPointerDown = false;
    private int longPressTouchSlopPx = 14;
    private int tapMoveSlopPx = 21;
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private AppWidgetHostView searchWidgetHostView;

    private AppAdapter appAdapter;
    private final List<AppInfo> appList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppDrawerController drawerController;

    // Navigation bar insets & bottom dock
    private LinearLayout bottomDockContainer;
    private int currentNavBarHeight = 0;
    private int currentSysGestureEdgePx = 0;
    private float lastDrawerOpenFraction = 0f;
    private boolean isEdgeExclusionSuspendedForDrawer = false;
    private boolean pendingDrawerCollapseOnStop = false;
    private PopupWindow pendingFolderLaunchPopup = null;
    private LaunchTransitionView launchTransitionView;
    private static final long WARM_LAUNCH_WINDOW_MS = 30L * 60L * 1000L;
    private final Map<String, Long> recentlyLaunchedPackages = new HashMap<>();

    // Touch & Drag state (Lawnchair DragLayer)
    private FrameLayout dragOverlay;
    private ImageView ivFloatingDrag;
    private LinearLayout llDropTargetsBar;
    private LinearLayout dropTargetRemove;
    private LinearLayout dropTargetUninstall;
    private static final int DROP_ZONE_NONE = 0;
    private static final int DROP_ZONE_REMOVE = 1;
    private static final int DROP_ZONE_UNINSTALL = 2;
    private int activeDropZone = DROP_ZONE_NONE;
    private boolean isDragActive = false;
    private boolean dragMoved = false;
    private boolean dragStartedFromDrawer = false;
    private PopupWindow pendingFolderDragPopup = null;
    private View pendingFolderDragDialogView = null;
    private FolderInfo pendingDragFolder = null;
    private int pendingDragFolderPage = -1;
    private int pendingDragFolderItemIdx = -1;
    private int pendingDragFolderAppPos = -1;
    private float lastTouchRawX = 0f;
    private float lastTouchRawY = 0f;
    private float dragStartX = 0f;
    private float dragStartY = 0f;
    private LauncherItem draggedItem = null;
    private int dragSourcePage = -1;
    private int dragSourcePosition = -1;
    private View dragSourceView = null;
    private View currentHoverView = null;
    private long lastEdgeScrollTime = 0L;
    private long edgeHoldStartTime = 0L;
    private int pendingEdgeDirection = 0;

    // Android 17 Customization Panel & Drag Window Mode
    private FrameLayout customizationPanelOverlay;
    private LinearLayout panelCustomizationCard;
    private View cardWallpaperCurrent;
    private ImageView ivWallpaperThumbCurrent;
    private View cardWallpaperTheme1;
    private View cardWallpaperTheme2;
    private View cardWallpaperTheme3;
    private LinearLayout rowActionWallpaper;
    private LinearLayout rowActionWidgets;
    private LinearLayout rowActionApps;
    private LinearLayout rowActionSettings;

    private View dragBlurOverlay;
    private HorizontalScrollView hsvDragPageChips;
    private LinearLayout llDragPageChips;
    private final List<TextView> dragChipViews = new ArrayList<>();
    private TextView dragChipDotsView;
    private final List<TextView> pageCardBadges = new ArrayList<>();
    private final List<FrameLayout> pageContainers = new ArrayList<>();
    private boolean isWindowModeActive = false;
    private float windowScaleX = 0.94f;
    private float windowScaleY = 0.94f;
    private float lastDragTouchX = 0f;
    private Object onBackInvokedCallbackProxy = null;

    // Android 17 Grouped Widgets state
    private TextView tvBatteryDeviceName;
    private TextView tvBatteryStatus;
    private TextView tvBatteryPct;
    private ImageView ivBatteryBolt;
    private boolean isBatteryReceiverRegistered = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            int pct = (int) ((level / (float) scale) * 100);
            boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                  status == BatteryManager.BATTERY_STATUS_FULL);

            if (tvBatteryPct != null) tvBatteryPct.setText(pct + "%");
            if (tvBatteryStatus != null) tvBatteryStatus.setText(isCharging ? "Charging" : "In use");
            if (ivBatteryBolt != null) ivBatteryBolt.setVisibility(isCharging ? View.VISIBLE : View.GONE);
        }
    };

    // Live package install/update/uninstall sync state
    private final ExecutorService appLoaderExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger appLoadSeq = new AtomicInteger(0);
    private final Map<String, AppInfo> appInfoCache = new HashMap<>();
    private volatile String loadedAppsSignature = "";
    private boolean hasLoadedInitialWorkspace = false;
    private Runnable pendingAppRefreshRunnable = null;
    private boolean isPackageReceiverRegistered = false;
    private LauncherApps launcherAppsService = null;
    private LauncherApps.Callback launcherAppsCallback = null;

    private final BroadcastReceiver packageChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return;
            }
            if ((Intent.ACTION_PACKAGE_REMOVED.equals(action) || Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(action))
                    && intent.getData() != null) {
                recentlyLaunchedPackages.remove(intent.getData().getSchemeSpecificPart());
            }
            scheduleInstalledAppsRefresh(false, 180L);
        }
    };

    // Helper ArrayList to avoid typing issues
    private static class ListHelper {
        static List<HomeItemAdapter> newAdapterList() {
            return new ArrayList<>();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);

        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        setContentView(R.layout.activity_launcher);
        longPressTouchSlopPx = Math.max(8, ViewConfiguration.get(this).getScaledTouchSlop());
        tapMoveSlopPx = Math.max(longPressTouchSlopPx, Math.round(12f * getResources().getDisplayMetrics().density));

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
        try {
            appWidgetHost.startListening();
        } catch (Exception ignored) {}

        currentGridMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_KEY_GRID_MODE, GRID_MODE_4X5);
        DisplayRefreshHelper.init(this);
        DisplayRefreshHelper.applyWindowRefreshPreference(this, false);
        displayListener = DisplayRefreshHelper.registerDisplayListener(this, new DisplayRefreshHelper.RefreshChangeListener() {
            @Override
            public void onRefreshRateChanged(float effectiveHz, float maxSupportedHz) {
                applyRefreshAdaptedScrollPhysics();
            }
        });

        initViews();
        setupWindowInsets();
        setupDrawerController();
        setupAtAGlance();
        setupSearchPill();
        setupDock();
        setupDrawer();
        setupCustomizationPanel();
        registerPredictiveBackCallback();

        checkFirstRunRolePrompt();

        registerPackageListeners();
        loadInstalledApps(false);

        liveWeatherController = new LiveWeatherController(this, new LiveWeatherController.Listener() {
            @Override
            public void onWeatherUpdated() {
                refreshGlanceDateText();
            }
        });
        liveWeatherController.refreshIfNeeded(true);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                maybeRequestWeatherLocationPermission();
            }
        }, 700L);
    }

    private void maybeRequestWeatherLocationPermission() {
        if (liveWeatherController == null) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!liveWeatherController.hasLocationPermission()
                && !prefs.getBoolean(PREF_KEY_WEATHER_LOC_PROMPTED, false)) {
            prefs.edit().putBoolean(PREF_KEY_WEATHER_LOC_PROMPTED, true).apply();
            try {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);
                return;
            } catch (Exception ignored) {}
        }
        maybePromptBackgroundDataExemption();
    }

    private void maybePromptBackgroundDataExemption() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed() || liveWeatherController == null) {
                    return;
                }
                if (!liveWeatherController.isNetworkPolicyLikelyBlocked()) {
                    return;
                }
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                if (prefs.getBoolean(PREF_KEY_DATA_SAVER_PROMPTED, false)) {
                    return;
                }
                prefs.edit().putBoolean(PREF_KEY_DATA_SAVER_PROMPTED, true).apply();
                try {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Allow Wi-Fi & Mobile Data")
                            .setMessage("Android is blocking network access for LW Launcher. Turn on Allow network access, Wi-Fi data, Mobile data, and Background data in App Data Usage settings for live At a Glance weather.")
                            .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    LiveWeatherController.openAppNetworkSettings(MainActivity.this);
                                }
                            })
                            .setNegativeButton("Not now", null)
                            .show();
                } catch (Exception ignored) {}
            }
        }, 3500L);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_COARSE_LOCATION && liveWeatherController != null) {
            if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                liveWeatherController.refreshIfNeeded(true);
            }
            maybePromptBackgroundDataExemption();
        }
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        homeScreen = findViewById(R.id.home_screen);
        appDrawer = findViewById(R.id.app_drawer);
        drawerDragHandle = findViewById(R.id.drawer_drag_handle);
        drawerSearchBar = findViewById(R.id.drawer_search_bar);
        drawerGoogleWidgetContainer = findViewById(R.id.drawer_google_widget_container);
        ivDrawerSearchIcon = findViewById(R.id.iv_drawer_search_icon);
        tvDrawerTypeaheadHint = findViewById(R.id.tv_drawer_typeahead_hint);
        svDrawerSearchResults = findViewById(R.id.sv_drawer_search_results);
        llDrawerSearchResultsContent = findViewById(R.id.ll_drawer_search_results_content);
        btnDrawerMic = findViewById(R.id.btn_drawer_mic);
        btnDrawerLens = findViewById(R.id.btn_drawer_lens);
        btnCloseDrawer = findViewById(R.id.btn_close_drawer);
        gvApps = findViewById(R.id.gv_apps);
        llDrawerHeaderOverlay = findViewById(R.id.ll_drawer_header_overlay);
        llDrawerPredictions = findViewById(R.id.ll_drawer_predictions);
        tvDrawerAllAppsHeader = findViewById(R.id.tv_drawer_all_apps_header);
        drawerScrollbar = findViewById(R.id.drawer_scrollbar);
        etDrawerSearch = findViewById(R.id.et_drawer_search);
        tvDate = findViewById(R.id.tv_date);

        if (llDrawerHeaderOverlay != null) {
            drawerHeaderHeightPx = Math.round(134f * getResources().getDisplayMetrics().density);
            llDrawerHeaderOverlay.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int h = bottom - top;
                    if (h > 0 && h != drawerHeaderHeightPx && v.getVisibility() == View.VISIBLE) {
                        drawerHeaderHeightPx = h;
                        updateDrawerGridPadding();
                    }
                }
            });
        }
        applyRefreshAdaptedScrollPhysics();
        applySystemTheme(getResources().getConfiguration());

        bottomDockContainer = findViewById(R.id.bottom_dock_container);

        dragOverlay = findViewById(R.id.drag_overlay);
        launchTransitionView = findViewById(R.id.launch_transition_view);
        ivFloatingDrag = findViewById(R.id.iv_floating_drag);
        llDropTargetsBar = findViewById(R.id.ll_drop_targets_bar);
        dropTargetRemove = findViewById(R.id.drop_target_remove);
        dropTargetUninstall = findViewById(R.id.drop_target_uninstall);

        hsvHomePages = findViewById(R.id.hsv_home_pages);
        llHomePages = findViewById(R.id.ll_home_pages);
        layoutPageDots = findViewById(R.id.layout_page_dots);

        hsvHomePages.setClipChildren(false);
        hsvHomePages.setClipToPadding(false);
        llHomePages.setClipChildren(false);
        llHomePages.setClipToPadding(false);

        hsvHomePages.setPagingMetricsProvider(new SmoothHomePager.PagingMetricsProvider() {
            @Override
            public int getPageWidth() {
                return getScreenWidth();
            }

            @Override
            public int getPageCount() {
                return pages.size();
            }
        });

        hsvHomePages.setOnPageSnapListener(new SmoothHomePager.OnPageSnapListener() {
            @Override
            public void onSnapToPage(int targetPage, float velocityPxPerSec) {
                scrollToPageWithVelocity(targetPage, velocityPxPerSec);
            }
        });

        hsvHomePages.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                int width = getScreenWidth();
                if (width <= 0 || pages.isEmpty()) return;

                float pageProgress = (float) scrollX / width;
                applyPageScrollEffects(pageProgress);
                updatePageDotsContinuous(pageProgress);

                if (hsvHomePages.isAnimatingSnap()) {
                    return;
                }
                int activePage = Math.max(0, Math.min(pages.size() - 1, Math.round(pageProgress)));
                if (activePage != currentPage) {
                    currentPage = activePage;
                    updatePageDots(activePage);
                    if (isWindowModeActive) {
                        updateActivePageWindow(activePage);
                    }
                }
            }
        });
    }

    private int getScreenWidth() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.widthPixels > 0 ? dm.widthPixels : 1080;
    }

    private int getScreenHeight() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.heightPixels > 0 ? dm.heightPixels : 2400;
    }

    private void setupWindowInsets() {
        if (rootLayout == null) return;

        currentNavBarHeight = getNavigationBarHeight();
        applyNavBarInsets(currentNavBarHeight);

        rootLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int navBarHeight = 0;
                int statusBarTop = 0;
                int gestureEdgePx = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                        statusBarTop = insets.getInsets(WindowInsets.Type.statusBars()).top;
                        android.graphics.Insets sysGesture = insets.getInsets(WindowInsets.Type.systemGestures());
                        gestureEdgePx = Math.max(sysGesture.left, sysGesture.right);
                    } catch (Throwable t) {
                        navBarHeight = insets.getSystemWindowInsetBottom();
                        statusBarTop = insets.getSystemWindowInsetTop();
                    }
                } else {
                    navBarHeight = insets.getSystemWindowInsetBottom();
                    statusBarTop = insets.getSystemWindowInsetTop();
                    try {
                        android.graphics.Insets sysGesture = insets.getSystemGestureInsets();
                        gestureEdgePx = Math.max(sysGesture.left, sysGesture.right);
                    } catch (Throwable ignored) {}
                }

                if (statusBarTop > 0 && homeScreen != null) {
                    float d = getResources().getDisplayMetrics().density;
                    int safeTop = Math.max(statusBarTop, Math.round(40f * d));
                    if (homeScreen.getPaddingTop() != safeTop) {
                        homeScreen.setPadding(
                                homeScreen.getPaddingLeft(),
                                safeTop,
                                homeScreen.getPaddingRight(),
                                homeScreen.getPaddingBottom()
                        );
                    }
                }

                if (gestureEdgePx > 0) {
                    currentSysGestureEdgePx = gestureEdgePx;
                }
                if (navBarHeight > 0 || currentNavBarHeight != navBarHeight) {
                    currentNavBarHeight = navBarHeight;
                    applyNavBarInsets(currentNavBarHeight);
                }
                updateHomeSystemGestureExclusion();
                return insets;
            }
        });
        rootLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                    updateHomeSystemGestureExclusion();
                }
            }
        });
        rootLayout.requestApplyInsets();
    }

    private void updateHomeSystemGestureExclusion() {
        if (rootLayout == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        int w = rootLayout.getWidth();
        int h = rootLayout.getHeight();
        if (w <= 0 || h <= 0) return;

        boolean drawerOpen = isEdgeExclusionSuspendedForDrawer
                || isDrawerVisiblyOpenForBars
                || (appDrawer != null && appDrawer.getVisibility() == View.VISIBLE && lastDrawerOpenFraction > 0.02f);
        boolean panelOpen = (customizationPanelOverlay != null && customizationPanelOverlay.getVisibility() == View.VISIBLE);

        if (drawerOpen || panelOpen) {
            rootLayout.setSystemGestureExclusionRects(Collections.<Rect>emptyList());
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int edgeW = Math.max((int) (44f * density), currentSysGestureEdgePx + (int) (8f * density));
        // Respect Android's 200dp per-edge cap, placed over the primary Home thumb swipe zone
        int maxExclH = Math.min((int) (198f * density), Math.max(1, h - (int) (140f * density)));
        int minTop = (int) (96f * density);
        int maxBottom = Math.max(minTop + 1, h - Math.max(currentNavBarHeight, (int) (48f * density)));
        int top = Math.max(minTop, Math.round(h * 0.54f - maxExclH / 2f));
        int bottom = Math.min(maxBottom, top + maxExclH);
        if (bottom <= top) {
            rootLayout.setSystemGestureExclusionRects(Collections.<Rect>emptyList());
            return;
        }

        rootLayout.setSystemGestureExclusionRects(Arrays.asList(
                new Rect(0, top, edgeW, bottom),
                new Rect(Math.max(0, w - edgeW), top, w, bottom)
        ));
    }

    private int getNavigationBarHeight() {
        int navBarHeight = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            navBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        return navBarHeight;
    }

    private void applyRefreshAdaptedScrollPhysics() {
        if (gvApps != null) {
            gvApps.setFriction(DisplayRefreshHelper.getAdaptedScrollFriction());
        }
    }

    private void updateDrawerGridPadding() {
        if (gvApps == null) return;
        float rawDensity = getResources().getDisplayMetrics().density;
        int safeBottom = Math.max(0, currentNavBarHeight);
        boolean showHeader = (etDrawerSearch == null || etDrawerSearch.length() == 0)
                && llDrawerPredictions != null
                && llDrawerPredictions.getChildCount() > 0;
        int headerPad = drawerHeaderHeightPx > 0 ? drawerHeaderHeightPx : Math.round(134f * rawDensity);
        int topPad = showHeader ? headerPad : Math.round(6f * rawDensity);
        int leftPad = Math.round(12f * rawDensity);
        int rightPad = Math.round(16f * rawDensity);
        int botPad = safeBottom + Math.round(28f * rawDensity);
        if (gvApps.getPaddingTop() != topPad || gvApps.getPaddingBottom() != botPad
                || gvApps.getPaddingLeft() != leftPad || gvApps.getPaddingRight() != rightPad) {
            gvApps.setPadding(leftPad, topPad, rightPad, botPad);
            gvApps.setClipToPadding(false);
        }
    }

    private void applyNavBarInsets(int navBarHeight) {
        final float rawDensity = getResources().getDisplayMetrics().density;
        final int safeBottom = (navBarHeight > 0) ? navBarHeight : 0;

        // Lift bottom dock container above 3-button / gesture navigation bar
        if (bottomDockContainer != null) {
            int sidePad = Math.round(14f * rawDensity);
            bottomDockContainer.setPadding(
                sidePad,
                0,
                sidePad,
                safeBottom + Math.round(10f * rawDensity)
            );
        }

        updateDrawerGridPadding();
        if (drawerScrollbar != null) {
            drawerScrollbar.setBottomInsetPx(safeBottom + Math.round(18 * rawDensity));
        }

        // Re-adjust grid heights across home pages
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                recalculateHomeGridHeights();
            }
        });
    }

    private void recalculateHomeGridHeights() {
        final int density = (int) getResources().getDisplayMetrics().density;
        for (int i = 0; i < pageGridViews.size() && i < pageAdapters.size(); i++) {
            final GridView gv = pageGridViews.get(i);
            final HomeItemAdapter adapter = pageAdapters.get(i);
            if (gv != null && adapter != null) {
                int gvH = gv.getHeight() - gv.getPaddingTop() - gv.getPaddingBottom();
                int rows = getGridRows();
                int vSpacing = (int) ((currentGridMode == GRID_MODE_16X9 ? 2 : 8) * density);
                int availH = gvH - ((rows - 1) * vSpacing);
                if (availH > 0) {
                    adapter.setCellHeight(availH / rows);
                }
            }
        }
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                renderAllSavedWidgets();
            }
        });
    }

    private int calculateCellHeight() {
        int screenH = getScreenHeight();
        float density = getResources().getDisplayMetrics().density;
        int navH = currentNavBarHeight > 0 ? currentNavBarHeight : getNavigationBarHeight();
        int glanceH = isAtAGlanceVisible() ? (int) (68 * density) : 0;
        int usedH = (int) ((44 + 144 + 16 + 24) * density) + glanceH + navH;
        int availH = screenH - usedH;
        int rows = getGridRows();
        int cellH = availH / rows;
        int minH = (rows > 10) ? (int) (36 * density) : (int) (84 * density);
        if (cellH < minH) {
            cellH = minH;
        }
        return cellH;
    }

    private void setupPagesLayout() {
        llHomePages.removeAllViews();
        pageGridViews.clear();
        pageAdapters.clear();
        pageContainers.clear();
        pageCardBadges.clear();
        pageGridOverlays.clear();
        pageGhostPreviews.clear();
        pageGlanceViews.clear();
        pageWidgetWrappers.clear();

        if (pages.isEmpty()) {
            pages.add(createEmptyPage());
        }

        int width = getScreenWidth();
        final int density = (int) getResources().getDisplayMetrics().density;
        final int estimatedCellH = calculateCellHeight();

        for (int i = 0; i < pages.size(); i++) {
            final int pageIdx = i;

            final FrameLayout pageContainer = new FrameLayout(this);
            LinearLayout.LayoutParams lpContainer = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT);
            pageContainer.setLayoutParams(lpContainer);
            pageContainer.setPadding(8 * density, 2 * density, 8 * density, 4 * density);
            pageContainer.setClipToPadding(false);
            pageContainer.setClipChildren(false);

            final LinearLayout pageColumn = new LinearLayout(this);
            pageColumn.setOrientation(LinearLayout.VERTICAL);
            pageColumn.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pageColumn.setClipChildren(false);
            pageColumn.setClipToPadding(false);

            if (isAtAGlanceVisible() && getAtAGlancePage() == pageIdx) {
                LinearLayout glanceView = createPageGlanceView();
                LinearLayout.LayoutParams lpGlance = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                glanceView.setLayoutParams(lpGlance);
                pageColumn.addView(glanceView);
                pageGlanceViews.put(pageIdx, glanceView);
            }

            final FrameLayout pageGridCanvas = new FrameLayout(this);
            LinearLayout.LayoutParams lpCanvas = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
            pageGridCanvas.setLayoutParams(lpCanvas);
            pageGridCanvas.setClipChildren(false);
            pageGridCanvas.setClipToPadding(false);

            final GridView gv = new GridView(this);
            FrameLayout.LayoutParams lpGv = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            gv.setLayoutParams(lpGv);
            gv.setNumColumns(getGridColumns());
            gv.setVerticalSpacing((int) ((currentGridMode == GRID_MODE_16X9 ? 2 : 8) * density));
            gv.setHorizontalSpacing((int) ((currentGridMode == GRID_MODE_16X9 ? 2 : 4) * density));
            gv.setPadding(4 * density, 2 * density, 4 * density, 2 * density);
            gv.setClipToPadding(false);
            gv.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            gv.setGravity(Gravity.CENTER);
            gv.setSelector(new ColorDrawable(Color.TRANSPARENT));
            gv.setDrawSelectorOnTop(false);
            gv.setOverScrollMode(View.OVER_SCROLL_NEVER);
            gv.setVerticalScrollBarEnabled(false);

            // Prevent GridView from scrolling vertically internally
            gv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return event.getActionMasked() == MotionEvent.ACTION_MOVE;
                }
            });

            final HomeItemAdapter adapter = new HomeItemAdapter(this, pages.get(pageIdx), estimatedCellH);
            adapter.setGridDensity(getGridColumns(), getGridRows());
            gv.setAdapter(adapter);

            final FrameLayout widgetOverlay = new FrameLayout(this);
            widgetOverlay.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            widgetOverlay.setClipChildren(false);
            widgetOverlay.setClipToPadding(false);

            final View ghostPreview = new View(this);
            ghostPreview.setBackgroundResource(R.drawable.drag_card_active_bg);
            ghostPreview.setAlpha(0.75f);
            ghostPreview.setElevation(4f * density);
            ghostPreview.setVisibility(View.GONE);
            ghostPreview.setLayoutParams(new FrameLayout.LayoutParams(0, 0));

            gv.post(new Runnable() {
                @Override
                public void run() {
                    int gvH = gv.getHeight() - gv.getPaddingTop() - gv.getPaddingBottom();
                    int rows = getGridRows();
                    int vSpacing = (int) ((currentGridMode == GRID_MODE_16X9 ? 2 : 8) * density);
                    int availH = gvH - ((rows - 1) * vSpacing);
                    if (availH > 0) {
                        int exactH = availH / rows;
                        adapter.setCellHeight(exactH);
                    }
                    renderWidgetsForPage(pageIdx);
                }
            });

            pageGridViews.add(gv);
            pageAdapters.add(adapter);
            pageContainers.add(pageContainer);
            pageGridOverlays.add(widgetOverlay);
            pageGhostPreviews.add(ghostPreview);

            gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (!isGestureTapEligible()) return;
                    if (pageIdx < pageAdapters.size()) {
                        LauncherItem item = pageAdapters.get(pageIdx).getItem(position);
                        if (item != null) {
                            if (item.type == LauncherItem.TYPE_FOLDER) {
                                showFolderPopup(view, item.folderInfo, pageIdx, position);
                            } else if (item.appInfo != null) {
                                View iconView = view != null ? view.findViewById(R.id.iv_app_icon) : null;
                                launchApp(item.appInfo, iconView != null ? iconView : view);
                            }
                        }
                    }
                }
            });

            gv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, final View view, final int position, long id) {
                    boolean drawerOpen = (drawerController != null && drawerController.isVisibleOrAnimating());
                    if (drawerOpen || !canStartGestureDragHold()) {
                        return false;
                    }
                    if (pageIdx < pages.size() && position < pages.get(pageIdx).size()) {
                        final LauncherItem item = pages.get(pageIdx).get(position);
                        if (item != null) {
                            scheduleDeferredDragHold(new Runnable() {
                                @Override
                                public void run() {
                                    if (drawerController == null || !drawerController.isVisibleOrAnimating()) {
                                        startDragItem(view, item, pageIdx, position);
                                    }
                                }
                            }, LONG_PRESS_ICON_HOLD_MS);
                            return true;
                        } else {
                            scheduleDeferredDragHold(new Runnable() {
                                @Override
                                public void run() {
                                    if (!isDragActive && !isWindowModeActive
                                            && (drawerController == null || !drawerController.isVisibleOrAnimating())) {
                                        dispatchWorkspaceTouchCancel();
                                        showCustomizationPanel();
                                    }
                                }
                            }, LONG_PRESS_ICON_HOLD_MS);
                            return true;
                        }
                    }
                    return false;
                }
            });

            final TextView tvBadge = new TextView(this);
            FrameLayout.LayoutParams lpBadge = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpBadge.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lpBadge.topMargin = 12 * density;
            tvBadge.setLayoutParams(lpBadge);
            tvBadge.setText("Page " + (pageIdx + 1));
            tvBadge.setTextColor(Color.WHITE);
            tvBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tvBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tvBadge.setBackgroundResource(R.drawable.page_card_badge_bg);
            tvBadge.setPadding(14 * density, 4 * density, 14 * density, 4 * density);
            tvBadge.setVisibility(isWindowModeActive ? View.VISIBLE : View.GONE);
            pageCardBadges.add(tvBadge);

            pageGridCanvas.addView(gv);
            pageGridCanvas.addView(widgetOverlay);
            pageColumn.addView(pageGridCanvas);
            pageContainer.addView(pageColumn);
            pageContainer.addView(tvBadge);
            llHomePages.addView(pageContainer);
        }

        renderAllSavedWidgets();
        updatePageDots(currentPage);
    }

    private void refreshAllPages() {
        refreshGlanceDateText();
        for (HomeItemAdapter adapter : pageAdapters) {
            adapter.notifyDataSetChanged();
        }
        renderAllSavedWidgets();
        updatePageDots(currentPage);
    }

    private int addNewPage() {
        pages.add(createEmptyPage());
        setupPagesLayout();
        return pages.size() - 1;
    }

    private void pruneEmptyTrailingPages() {
        boolean changed = false;
        for (int i = pages.size() - 1; i > 0; i--) {
            if (isPageEmpty(i)) {
                pages.remove(i);
                shiftWidgetPagesDown(i);
                changed = true;
            }
        }
        if (pages.isEmpty()) {
            pages.add(createEmptyPage());
            changed = true;
        }
        if (changed) {
            if (currentPage >= pages.size()) {
                currentPage = pages.size() - 1;
            }
            savePages();
            setupPagesLayout();
            scrollToPage(currentPage);
        }
    }

    private void triggerPageSwitchVibration() {
        try {
            if (rootLayout != null) {
                rootLayout.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(35);
                }
            }
        } catch (Exception ignored) {}
    }

    private void scrollToPage(int pageIdx) {
        scrollToPageWithVelocity(pageIdx, 0f);
    }

    private void scrollToPageWithVelocity(int pageIdx, float velocityPxPerSec) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return;
        boolean pageChanged = (currentPage != pageIdx);
        currentPage = pageIdx;
        if (hsvHomePages != null) {
            hsvHomePages.smoothScrollToPage(pageIdx, velocityPxPerSec);
        }

        updatePageDots(pageIdx);
        if (isWindowModeActive) {
            updateActivePageWindow(pageIdx);
        }
        if (isDragActive) {
            updateDragPageChips(pageIdx);
            if (pageChanged) {
                triggerPageSwitchVibration();
            }
        }
    }

    private void updatePageDots(int activePage) {
        if (layoutPageDots == null) return;
        int count = pages.size();
        if (count <= 1) {
            if (layoutPageDots.getVisibility() != View.GONE) {
                layoutPageDots.setVisibility(View.GONE);
            }
            return;
        }
        if (layoutPageDots.getVisibility() != View.VISIBLE) {
            layoutPageDots.setVisibility(View.VISIBLE);
        }

        float density = getResources().getDisplayMetrics().density;
        int inactiveW = Math.max(4, Math.round(5f * density));
        int activeW = Math.max(10, Math.round(16f * density));
        int dotH = Math.max(4, Math.round(5f * density));
        int dotMargin = Math.max(2, Math.round(3f * density));

        if (layoutPageDots.getChildCount() != count) {
            layoutPageDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                final int pageIdx = i;
                View dot = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        i == activePage ? activeW : inactiveW, dotH);
                lp.setMargins(dotMargin, 0, dotMargin, 0);
                dot.setLayoutParams(lp);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.RECTANGLE);
                bg.setCornerRadius(dotH * 0.5f);
                bg.setColor(Color.WHITE);
                dot.setBackground(bg);
                dot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scrollToPage(pageIdx);
                    }
                });
                layoutPageDots.addView(dot);
            }
        }
        updatePageDotsContinuous((float) activePage);
    }

    private void applyPageScrollEffects(float pageProgress) {
        if (isWindowModeActive || pageContainers.size() <= 1) return;
        boolean settled = Math.abs(pageProgress - Math.round(pageProgress)) < 0.005f;
        for (int i = 0; i < pageContainers.size(); i++) {
            FrameLayout page = pageContainers.get(i);
            if (page == null) continue;
            if (settled) {
                page.setScaleX(1.0f);
                page.setScaleY(1.0f);
                page.setAlpha(1.0f);
            } else {
                float dist = Math.min(1.0f, Math.abs(i - pageProgress));
                float scale = 1.0f - (0.032f * dist);
                float alpha = 1.0f - (0.14f * dist);
                page.setScaleX(scale);
                page.setScaleY(scale);
                page.setAlpha(alpha);
            }
        }
    }

    private void updatePageDotsContinuous(float pageProgress) {
        if (layoutPageDots == null || layoutPageDots.getVisibility() != View.VISIBLE) return;
        float density = getResources().getDisplayMetrics().density;
        int inactiveW = Math.max(4, Math.round(5f * density));
        int activeW = Math.max(10, Math.round(16f * density));
        int count = layoutPageDots.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = layoutPageDots.getChildAt(i);
            if (child == null) continue;
            float proximity = Math.max(0f, 1.0f - Math.abs(i - pageProgress));
            int targetW = Math.round(inactiveW + (activeW - inactiveW) * proximity);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp != null && lp.width != targetW) {
                lp.width = targetW;
                child.setLayoutParams(lp);
            }
            child.setAlpha(0.45f + (0.55f * proximity));
        }
    }

    private void updateDragPageChips(final int activePage) {
        if (llDragPageChips == null) return;
        llDragPageChips.removeAllViews();
        dragChipViews.clear();

        final int density = (int) getResources().getDisplayMetrics().density;
        int chipSize = 36 * density;
        int margin = 4 * density;

        for (int i = 0; i < pages.size(); i++) {
            final int pageIdx = i;
            final TextView tvChip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(chipSize, chipSize);
            lp.setMargins(margin, 0, margin, 0);
            tvChip.setLayoutParams(lp);
            tvChip.setGravity(Gravity.CENTER);
            tvChip.setText(String.valueOf(i + 1));
            tvChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

            if (i == activePage) {
                tvChip.setBackgroundResource(R.drawable.page_chip_active_bg);
                tvChip.setTextColor(Color.parseColor("#0A192F"));
                tvChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                tvChip.setScaleX(1.1f);
                tvChip.setScaleY(1.1f);
            } else {
                tvChip.setBackgroundResource(R.drawable.page_chip_inactive_bg);
                tvChip.setTextColor(Color.WHITE);
                tvChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                tvChip.setScaleX(1.0f);
                tvChip.setScaleY(1.0f);
            }

            tvChip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollToPage(pageIdx);
                }
            });

            dragChipViews.add(tvChip);
            llDragPageChips.addView(tvChip);
        }

        // Add the "...." button (exact request: "1 2 3 4 5....")
        dragChipDotsView = new TextView(this);
        LinearLayout.LayoutParams lpDots = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, chipSize);
        lpDots.setMargins(margin, 0, margin, 0);
        dragChipDotsView.setLayoutParams(lpDots);
        dragChipDotsView.setPadding(10 * density, 0, 10 * density, 0);
        dragChipDotsView.setGravity(Gravity.CENTER);
        dragChipDotsView.setText("....");
        dragChipDotsView.setTextColor(Color.parseColor("#A8C7FA"));
        dragChipDotsView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        dragChipDotsView.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        dragChipDotsView.setBackgroundResource(R.drawable.page_chip_inactive_bg);
        dragChipDotsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (pages.size() < 7) {
                    int np = addNewPage();
                    scrollToPage(np);
                }
            }
        });
        llDragPageChips.addView(dragChipDotsView);
    }

    private void startDragItem(View view, LauncherItem item, int pageIdx, int position) {
        if (isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
        dragStartedFromDrawer = false;
        clearPendingFolderDragState();
        activeDropZone = DROP_ZONE_NONE;
        edgeHoldStartTime = 0L;
        pendingEdgeDirection = 0;
        dragStartX = lastTouchRawX;
        dragStartY = lastTouchRawY;
        lastDragTouchX = lastTouchRawX;
        dragSourcePage = pageIdx;
        dragSourcePosition = position;
        dragSourceView = view;
        draggedItem = item;
        draggedSpecialItem = SPECIAL_ITEM_NONE;

        Log.d("PixelDrag", "startDragItem: item=" + item.getLabel() + " page=" + pageIdx + " pos=" + position);

        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        dispatchWorkspaceTouchCancel();
        resetIconScaleTarget(view);
    }

    private void startDragFromDrawer(View view, AppInfo app) {
        if (app == null || isDragActive) return;
        startDragItem(view, new LauncherItem(app), -1, -1);
        dragStartedFromDrawer = true;
    }

    private void startDragFromFolderPopup(View view, AppInfo app, FolderInfo folder,
                                          int pageIdx, int itemIdx, int appPos,
                                          PopupWindow popupWindow, View dialogView) {
        if (app == null || isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
        dragStartedFromDrawer = false;
        pendingFolderDragPopup = popupWindow;
        pendingFolderDragDialogView = dialogView;
        pendingDragFolder = folder;
        pendingDragFolderPage = pageIdx;
        pendingDragFolderItemIdx = itemIdx;
        pendingDragFolderAppPos = appPos;
        activeDropZone = DROP_ZONE_NONE;
        edgeHoldStartTime = 0L;
        pendingEdgeDirection = 0;
        dragStartX = lastTouchRawX;
        dragStartY = lastTouchRawY;
        lastDragTouchX = lastTouchRawX;
        dragSourcePage = -1;
        dragSourcePosition = -1;
        dragSourceView = view;
        draggedItem = new LauncherItem(app);
        draggedSpecialItem = SPECIAL_ITEM_NONE;

        Log.d("PixelDrag", "startDragFromFolderPopup: item=" + app.label + " folder=" + (folder != null ? folder.name : ""));

        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        resetIconScaleTarget(view);
    }

    private void clearPendingFolderDragState() {
        pendingFolderDragPopup = null;
        pendingFolderDragDialogView = null;
        pendingDragFolder = null;
        pendingDragFolderPage = -1;
        pendingDragFolderItemIdx = -1;
        pendingDragFolderAppPos = -1;
    }

    private void commitPendingFolderDragRemoval() {
        if (pendingFolderDragDialogView != null) {
            pendingFolderDragDialogView.animate().cancel();
            pendingFolderDragDialogView.setAlpha(0f);
        }
        if (pendingFolderDragPopup != null) {
            try {
                pendingFolderDragPopup.setElevation(0f);
            } catch (Throwable ignored) {}
        }
        if (pendingDragFolder != null) {
            AppInfo removedApp = (draggedItem != null) ? draggedItem.appInfo : null;
            if (pendingDragFolderAppPos >= 0 && pendingDragFolderAppPos < pendingDragFolder.apps.size()) {
                AppInfo candidate = pendingDragFolder.apps.get(pendingDragFolderAppPos);
                if (removedApp == null || (candidate != null && removedApp.packageName.equals(candidate.packageName))) {
                    pendingDragFolder.apps.remove(pendingDragFolderAppPos);
                } else {
                    pendingDragFolder.apps.remove(removedApp);
                }
            } else if (removedApp != null) {
                pendingDragFolder.apps.remove(removedApp);
            }
            if (pendingDragFolderPage >= 0 && pendingDragFolderPage < pages.size()
                    && pendingDragFolderItemIdx >= 0 && pendingDragFolderItemIdx < pages.get(pendingDragFolderPage).size()) {
                if (pendingDragFolder.apps.size() == 1) {
                    pages.get(pendingDragFolderPage).set(pendingDragFolderItemIdx, new LauncherItem(pendingDragFolder.apps.get(0)));
                } else if (pendingDragFolder.apps.isEmpty()) {
                    pages.get(pendingDragFolderPage).set(pendingDragFolderItemIdx, null);
                }
            }
            cleanupFoldersOnAllPages();
            savePages();
            for (HomeItemAdapter adapter : pageAdapters) {
                adapter.notifyDataSetChanged();
            }
            pendingDragFolder = null;
            pendingDragFolderPage = -1;
            pendingDragFolderItemIdx = -1;
            pendingDragFolderAppPos = -1;
        }
    }

    private void resetIconScaleTarget(View view) {
        if (view == null) return;
        View iconTarget = view.findViewById(R.id.iv_app_icon);
        View target = (iconTarget != null) ? iconTarget : view;
        target.animate().cancel();
        target.setScaleX(1.0f);
        target.setScaleY(1.0f);
        view.setPressed(false);
    }

    private void dispatchWorkspaceTouchCancel() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancelEv = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, lastTouchRawX, lastTouchRawY, 0);
        try {
            MainActivity.super.dispatchTouchEvent(cancelEv);
        } catch (Throwable ignored) {}
        cancelEv.recycle();
    }

    private void cancelUnmovedDrag() {
        if (!isDragActive || dragMoved) return;
        isDragActive = false;
        dragStartedFromDrawer = false;
        draggedSpecialItem = SPECIAL_ITEM_NONE;
        draggedWidgetId = -1;
        draggedWidgetSourcePage = -1;
        if (dragSourceView != null) {
            dragSourceView.setAlpha(1.0f);
            resetIconScaleTarget(dragSourceView);
            dragSourceView = null;
        }
        draggedItem = null;
        clearPendingFolderDragState();
    }

    private void startDragSpecialItem(int type, String label, View view) {
        if (isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
        dragStartedFromDrawer = false;
        clearPendingFolderDragState();
        activeDropZone = DROP_ZONE_NONE;
        edgeHoldStartTime = 0L;
        pendingEdgeDirection = 0;
        dragStartX = lastTouchRawX;
        dragStartY = lastTouchRawY;
        lastDragTouchX = lastTouchRawX;
        dragSourcePage = -99;
        dragSourcePosition = -99;
        dragSourceView = view;
        draggedItem = null;
        draggedSpecialItem = type;

        Log.d("PixelDrag", "startDragSpecialItem: type=" + type + " label=" + label);

        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        dispatchWorkspaceTouchCancel();
    }

    private void startDragWidget(int sourcePage, int widgetId, String label, int spanCols, int spanRows, View view) {
        if (isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
        dragStartedFromDrawer = false;
        clearPendingFolderDragState();
        activeDropZone = DROP_ZONE_NONE;
        edgeHoldStartTime = 0L;
        pendingEdgeDirection = 0;
        dragStartX = lastTouchRawX;
        dragStartY = lastTouchRawY;
        lastDragTouchX = lastTouchRawX;
        dragSourcePage = sourcePage;
        dragSourcePosition = -1;
        dragSourceView = view;
        draggedItem = null;
        draggedSpecialItem = SPECIAL_ITEM_WIDGET;
        draggedWidgetId = widgetId;
        draggedWidgetSourcePage = sourcePage;
        draggedWidgetSpanCols = spanCols;
        draggedWidgetSpanRows = spanRows;
        draggedWidgetLabel = (label != null) ? label : "Widget";

        Log.d("PixelDrag", "startDragWidget: id=" + widgetId + " page=" + sourcePage + " label=" + label);

        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        dispatchWorkspaceTouchCancel();
    }

    private void startDragDockApp(int slot, AppInfo app, View view) {
        if (isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
        dragStartedFromDrawer = false;
        clearPendingFolderDragState();
        activeDropZone = DROP_ZONE_NONE;
        edgeHoldStartTime = 0L;
        pendingEdgeDirection = 0;
        dragStartX = lastTouchRawX;
        dragStartY = lastTouchRawY;
        lastDragTouchX = lastTouchRawX;
        dragSourcePage = DRAG_SOURCE_DOCK;
        dragSourcePosition = slot;
        dragSourceView = view;
        draggedItem = new LauncherItem(app);
        draggedSpecialItem = SPECIAL_ITEM_NONE;

        Log.d("PixelDrag", "startDragDockApp: slot=" + slot + " app=" + app.label);

        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        dispatchWorkspaceTouchCancel();
        resetIconScaleTarget(view);
    }

    private boolean isTouchInsideView(View v, float rawX, float rawY) {
        return isTouchInsideView(v, rawX, rawY, 6f);
    }

    private boolean isTouchInsideView(View v, float rawX, float rawY, float slopDp) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        int slop = (int) (slopDp * getResources().getDisplayMetrics().density);
        return (rawX >= loc[0] - slop && rawX <= loc[0] + v.getWidth() + slop &&
                rawY >= loc[1] - slop && rawY <= loc[1] + v.getHeight() + slop);
    }

    private boolean isDropTargetHit(View v, float rawX, float rawY, float density) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        return isTouchInsideView(v, rawX, rawY, 10f)
                || isTouchInsideView(v, rawX, rawY - (28f * density), 6f);
    }

    private void highlightDropTarget(View v, int bgRes) {
        if (v != null) {
            v.setBackgroundResource(bgRes);
            if (v == dropTargetUninstall) {
                updateDropUninstallContrast(true);
            }
            v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
        }
    }

    private void unhighlightDropTarget(View v, int bgRes) {
        if (v != null) {
            v.setBackgroundResource(bgRes);
            if (v == dropTargetUninstall) {
                updateDropUninstallContrast(false);
            }
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private void updateDropUninstallContrast(boolean highlighted) {
        if (dropTargetUninstall == null) return;
        int fgColor = highlighted ? Color.WHITE : Color.parseColor("#FF5252");
        ImageView iv = dropTargetUninstall.findViewById(R.id.iv_drop_uninstall_icon);
        TextView tv = dropTargetUninstall.findViewById(R.id.tv_drop_uninstall_label);
        if (iv != null) iv.setColorFilter(fgColor);
        if (tv != null) tv.setTextColor(fgColor);
    }

    private void handleDragTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        float density = getResources().getDisplayMetrics().density;
        int iconSize = (int) (64 * density);
        int halfSize = iconSize / 2;
        int offsetY = (int) (36 * density);

        switch (action) {
            case MotionEvent.ACTION_POINTER_DOWN:
                if (!dragMoved) {
                    isMultiTouchGesture = true;
                    cancelUnmovedDrag();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - dragStartX;
                float dy = ev.getRawY() - dragStartY;
                float dist = (float) Math.hypot(dx, dy);
                float moveThresholdPx = ((draggedSpecialItem != SPECIAL_ITEM_NONE)
                        ? DRAG_MOVE_THRESHOLD_SPECIAL_DP
                        : DRAG_MOVE_THRESHOLD_ICON_DP) * density;

                if (!dragMoved && dist > moveThresholdPx) {
                    dragMoved = true;
                    lastDragTouchX = ev.getRawX();

                    if (dragStartedFromDrawer) {
                        closeAppDrawer();
                    }
                    if (pendingDragFolder != null || pendingFolderDragPopup != null) {
                        commitPendingFolderDragRemoval();
                    } else if (dragSourceView != null) {
                        dragSourceView.setAlpha(0.25f);
                    }

                    if (ivFloatingDrag != null) {
                        if (draggedSpecialItem == SPECIAL_ITEM_SEARCH_BAR) {
                            ivFloatingDrag.setImageResource(R.drawable.ic_google_g);
                        } else if (draggedSpecialItem == SPECIAL_ITEM_CALENDAR) {
                            ivFloatingDrag.setImageResource(R.drawable.ic_calendar);
                        } else if (draggedSpecialItem == SPECIAL_ITEM_WIDGET) {
                            ivFloatingDrag.setImageResource(R.drawable.ic_widgets);
                        } else if (draggedItem != null) {
                            ivFloatingDrag.setImageDrawable(draggedItem.getIcon(this));
                        }
                        ViewGroup.LayoutParams lp = ivFloatingDrag.getLayoutParams();
                        lp.width = iconSize;
                        lp.height = iconSize;
                        ivFloatingDrag.setLayoutParams(lp);

                        ivFloatingDrag.setX(ev.getRawX() - halfSize);
                        ivFloatingDrag.setY(ev.getRawY() - halfSize - offsetY);
                        ivFloatingDrag.setScaleX(1.0f);
                        ivFloatingDrag.setScaleY(1.0f);
                        ivFloatingDrag.setAlpha(0.88f);
                        ivFloatingDrag.setRotation(0f);
                        ivFloatingDrag.animate()
                                .scaleX(1.16f)
                                .scaleY(1.16f)
                                .alpha(1.0f)
                                .setDuration(140)
                                .setInterpolator(new OvershootInterpolator(1.45f))
                                .start();
                    }

                    boolean canUninstall = (draggedSpecialItem == SPECIAL_ITEM_NONE)
                            && draggedItem != null
                            && draggedItem.type == LauncherItem.TYPE_APP
                            && draggedItem.appInfo != null
                            && isAppUninstallable(draggedItem.appInfo.packageName);
                    boolean canRemove = (draggedSpecialItem != SPECIAL_ITEM_NONE) || !dragStartedFromDrawer;

                    if (dropTargetRemove != null) {
                        dropTargetRemove.setVisibility(canRemove ? View.VISIBLE : View.GONE);
                        unhighlightDropTarget(dropTargetRemove, R.drawable.drop_target_bg);
                    }
                    if (dropTargetUninstall != null) {
                        dropTargetUninstall.setVisibility(canUninstall ? View.VISIBLE : View.GONE);
                        unhighlightDropTarget(dropTargetUninstall, R.drawable.drop_target_bg);
                    }

                    if (dragOverlay != null) {
                        dragOverlay.setVisibility(View.VISIBLE);
                    }

                    if (llDropTargetsBar != null) {
                        if (canRemove || canUninstall) {
                            llDropTargetsBar.setVisibility(View.VISIBLE);
                            llDropTargetsBar.setAlpha(0f);
                            llDropTargetsBar.setTranslationY(-30f * density);
                            llDropTargetsBar.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
                        } else {
                            llDropTargetsBar.setVisibility(View.GONE);
                        }
                    }

                    enterPageWindowMode();
                }

                if (dragMoved) {
                    if (ivFloatingDrag != null) {
                        ivFloatingDrag.setX(ev.getRawX() - halfSize);
                        ivFloatingDrag.setY(ev.getRawY() - halfSize - offsetY);

                        // Organic drag tilt physics based on drag velocity
                        float deltaX = ev.getRawX() - lastDragTouchX;
                        lastDragTouchX = ev.getRawX();
                        float targetTilt = Math.max(-14f, Math.min(14f, deltaX * 1.8f));
                        float currentRot = ivFloatingDrag.getRotation();
                        float newRot = currentRot + (targetTilt - currentRot) * 0.35f;
                        ivFloatingDrag.setRotation(newRot);
                    }

                    boolean isOverRemove = isDropTargetHit(dropTargetRemove, ev.getRawX(), ev.getRawY(), density);
                    boolean isOverUninstall = isDropTargetHit(dropTargetUninstall, ev.getRawX(), ev.getRawY(), density);

                    if (isOverRemove) {
                        if (activeDropZone != DROP_ZONE_REMOVE) {
                            activeDropZone = DROP_ZONE_REMOVE;
                            highlightDropTarget(dropTargetRemove, R.drawable.drop_target_active_remove);
                            unhighlightDropTarget(dropTargetUninstall, R.drawable.drop_target_bg);
                            clearHoverHighlight();
                            dropTargetRemove.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    } else if (isOverUninstall) {
                        if (activeDropZone != DROP_ZONE_UNINSTALL) {
                            activeDropZone = DROP_ZONE_UNINSTALL;
                            highlightDropTarget(dropTargetUninstall, R.drawable.drop_target_active_red);
                            unhighlightDropTarget(dropTargetRemove, R.drawable.drop_target_bg);
                            clearHoverHighlight();
                            dropTargetUninstall.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    } else {
                        if (activeDropZone != DROP_ZONE_NONE) {
                            activeDropZone = DROP_ZONE_NONE;
                            unhighlightDropTarget(dropTargetRemove, R.drawable.drop_target_bg);
                            unhighlightDropTarget(dropTargetUninstall, R.drawable.drop_target_bg);
                        }

                        // Intentional, deliberate edge scrolling so icons dragged to outer columns don't flip pages accidentally
                        long now = System.currentTimeMillis();
                        int screenW = getScreenWidth();
                        float edgeThreshold = EDGE_SCROLL_MARGIN_DP * density;

                        int currentEdgeDir = 0;
                        if (ev.getRawX() > screenW - edgeThreshold) {
                            currentEdgeDir = 1;
                        } else if (ev.getRawX() < edgeThreshold) {
                            currentEdgeDir = -1;
                        }

                        if (currentEdgeDir != 0) {
                            if (pendingEdgeDirection != currentEdgeDir) {
                                pendingEdgeDirection = currentEdgeDir;
                                edgeHoldStartTime = now;
                            } else {
                                if (now - edgeHoldStartTime >= EDGE_SCROLL_HOLD_MS && now - lastEdgeScrollTime > EDGE_SCROLL_COOLDOWN_MS) {
                                    if (pendingEdgeDirection == 1) {
                                        if (currentPage < pages.size() - 1) {
                                            scrollToPage(currentPage + 1);
                                            lastEdgeScrollTime = now;
                                            edgeHoldStartTime = now;
                                        } else if (pages.size() < 7 && now - lastEdgeScrollTime > NEW_PAGE_EDGE_COOLDOWN_MS) {
                                            int newPageIdx = addNewPage();
                                            scrollToPage(newPageIdx);
                                            lastEdgeScrollTime = now;
                                            edgeHoldStartTime = now;
                                        }
                                    } else if (pendingEdgeDirection == -1) {
                                        if (currentPage > 0) {
                                            scrollToPage(currentPage - 1);
                                            lastEdgeScrollTime = now;
                                            edgeHoldStartTime = now;
                                        }
                                    }
                                }
                            }
                        } else {
                            pendingEdgeDirection = 0;
                            edgeHoldStartTime = 0L;
                        }

                        // Fast, snappy page chip hovering (180ms debounce)
                        int hoveredChipIdx = -1;
                        for (int c = 0; c < dragChipViews.size(); c++) {
                            TextView chip = dragChipViews.get(c);
                            if (isTouchInsideView(chip, ev.getRawX(), ev.getRawY(), 8f)) {
                                hoveredChipIdx = c;
                                break;
                            }
                        }

                        if (hoveredChipIdx >= 0 && hoveredChipIdx < pages.size()) {
                            if (hoveredChipIdx != currentPage && now - lastEdgeScrollTime > 180) {
                                scrollToPage(hoveredChipIdx);
                                lastEdgeScrollTime = now;
                            }
                        } else if (dragChipDotsView != null && isTouchInsideView(dragChipDotsView, ev.getRawX(), ev.getRawY(), 8f)) {
                            if (now - lastEdgeScrollTime > 400 && pages.size() < 7) {
                                int np = addNewPage();
                                scrollToPage(np);
                                lastEdgeScrollTime = now;
                            }
                        }

                        detectAndHighlightHoverTarget(ev.getRawX(), ev.getRawY());
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endDrag(ev.getRawX(), ev.getRawY());
                break;
        }
    }

    private int findItemPositionAt(GridView gv, int localX, int localY, int itemCount) {
        if (gv == null || gv.getWidth() <= 0 || gv.getHeight() <= 0) {
            return AdapterView.INVALID_POSITION;
        }

        // 1. Try standard pointToPosition
        int pos = gv.pointToPosition(localX, localY);
        if (pos >= 0 && pos < itemCount) {
            return pos;
        }

        // 2. Proximity search: find child view whose center is closest to (localX, localY)
        int bestPos = AdapterView.INVALID_POSITION;
        float bestDistSq = Float.MAX_VALUE;

        for (int i = 0; i < gv.getChildCount(); i++) {
            View child = gv.getChildAt(i);
            if (child != null) {
                float childCenterX = child.getLeft() + child.getWidth() / 2f;
                float childCenterY = child.getTop() + child.getHeight() / 2f;
                float dx = localX - childCenterX;
                float dy = localY - childCenterY;
                float distSq = dx * dx + dy * dy;

                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    int candidatePos = gv.getFirstVisiblePosition() + i;
                    if (candidatePos < itemCount) {
                        bestPos = candidatePos;
                    }
                }
            }
        }
        if (bestPos != AdapterView.INVALID_POSITION) {
            return bestPos;
        }

        // 3. Dynamic cols x rows mapping based on active grid mode (16*9 or 4*5)
        int innerW = gv.getWidth() - gv.getPaddingLeft() - gv.getPaddingRight();
        int innerH = gv.getHeight() - gv.getPaddingTop() - gv.getPaddingBottom();
        if (innerW <= 0 || innerH <= 0) return 0;

        int adjX = Math.max(0, Math.min(innerW - 1, localX - gv.getPaddingLeft()));
        int adjY = Math.max(0, Math.min(innerH - 1, localY - gv.getPaddingTop()));

        int cols = getGridColumns();
        int rows = getGridRows();
        int col = Math.max(0, Math.min(cols - 1, (adjX * cols) / innerW));
        int row = Math.max(0, Math.min(rows - 1, (adjY * rows) / innerH));
        int slot = row * cols + col;
        if (slot >= 0 && slot < itemCount) {
            return slot;
        }
        return 0;
    }

    private int[] computeDropGridOrigin(GridView gv, float rawX, float rawY, int spanCols, int spanRows) {
        int cols = getGridColumns();
        int rows = getGridRows();
        if (gv == null || gv.getWidth() <= 0 || gv.getHeight() <= 0) {
            return new int[]{0, 0};
        }
        float scaleX = isWindowModeActive ? windowScaleX : 1.0f;
        float scaleY = isWindowModeActive ? windowScaleY : 1.0f;
        int localX = getLocalX(gv, rawX, scaleX);
        int localY = getLocalY(gv, rawY, scaleY);

        int padL = gv.getPaddingLeft();
        int padT = gv.getPaddingTop();
        int innerW = Math.max(1, gv.getWidth() - padL - gv.getPaddingRight());
        int innerH = Math.max(1, gv.getHeight() - padT - gv.getPaddingBottom());

        float cellW = (float) innerW / cols;
        float cellH = (float) innerH / rows;

        int desiredCol = Math.round((localX - padL) / cellW - spanCols / 2.0f);
        int desiredRow = Math.round((localY - padT) / cellH - spanRows / 2.0f);
        desiredCol = Math.max(0, Math.min(cols - spanCols, desiredCol));
        desiredRow = Math.max(0, Math.min(rows - spanRows, desiredRow));
        return new int[]{desiredCol, desiredRow};
    }

    private int[] computeOverlayCellBounds(GridView gv, int col, int row, int spanCols, int spanRows) {
        int cols = getGridColumns();
        int rows = getGridRows();
        float density = getResources().getDisplayMetrics().density;
        int padL = gv.getPaddingLeft();
        int padT = gv.getPaddingTop();
        int innerW = Math.max(1, gv.getWidth() - padL - gv.getPaddingRight());
        int innerH = Math.max(1, gv.getHeight() - padT - gv.getPaddingBottom());
        int hSpacing = (int) ((currentGridMode == GRID_MODE_16X9 ? 2 : 4) * density);
        int vSpacing = (int) ((currentGridMode == GRID_MODE_16X9 ? 2 : 8) * density);

        float cellW = (innerW - (cols - 1) * hSpacing) / (float) cols;
        float cellH = (innerH - (rows - 1) * vSpacing) / (float) rows;

        int left = padL + Math.round(col * (cellW + hSpacing));
        int top = padT + Math.round(row * (cellH + vSpacing));
        int width = Math.max(1, Math.round(spanCols * cellW + (spanCols - 1) * hSpacing));
        int height = Math.max(1, Math.round(spanRows * cellH + (spanRows - 1) * vSpacing));
        return new int[]{left, top, width, height};
    }

    private void hideAllGhostPreviews() {
        for (View ghost : pageGhostPreviews) {
            if (ghost != null) {
                ghost.setVisibility(View.GONE);
            }
        }
    }

    private void updateOverlayGhostPreview(float rawX, float rawY, int spanCols, int spanRows, int ignoreWidgetId, boolean ignoreGlance) {
        hideAllGhostPreviews();
        if (currentPage < 0 || currentPage >= pageGridViews.size() || currentPage >= pageGhostPreviews.size()) return;
        GridView gv = pageGridViews.get(currentPage);
        View ghost = pageGhostPreviews.get(currentPage);
        if (gv == null || ghost == null || gv.getWidth() <= 0 || gv.getHeight() <= 0) return;

        int[] desired = computeDropGridOrigin(gv, rawX, rawY, spanCols, spanRows);
        int[] best = findBestOverlaySlot(currentPage, desired[0], desired[1], spanCols, spanRows, ignoreWidgetId, ignoreGlance);
        int[] bounds = computeOverlayCellBounds(gv, best[0], best[1], spanCols, spanRows);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(bounds[2], bounds[3]);
        lp.leftMargin = bounds[0];
        lp.topMargin = bounds[1];
        ghost.setLayoutParams(lp);
        ghost.setVisibility(View.VISIBLE);
    }

    private void detectAndHighlightHoverTarget(float rawX, float rawY) {
        if (draggedSpecialItem == SPECIAL_ITEM_SEARCH_BAR) {
            clearHoverHighlight();
            hideAllGhostPreviews();
            return;
        }
        if (draggedSpecialItem == SPECIAL_ITEM_CALENDAR) {
            clearHoverHighlight();
            hideAllGhostPreviews();
            if (currentPage >= 0 && currentPage < pageGhostPreviews.size() && currentPage != getAtAGlancePage()) {
                View ghost = pageGhostPreviews.get(currentPage);
                if (ghost != null) {
                    float density = getResources().getDisplayMetrics().density;
                    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * density));
                    lp.leftMargin = (int) (8 * density);
                    lp.rightMargin = (int) (8 * density);
                    lp.topMargin = (int) (4 * density);
                    ghost.setLayoutParams(lp);
                    ghost.setVisibility(View.VISIBLE);
                }
            }
            return;
        }
        if (draggedSpecialItem == SPECIAL_ITEM_WIDGET) {
            clearHoverHighlight();
            updateOverlayGhostPreview(rawX, rawY, draggedWidgetSpanCols, draggedWidgetSpanRows, draggedWidgetId, false);
            return;
        }

        hideAllGhostPreviews();
        if (currentPage < 0 || currentPage >= pageGridViews.size()) return;
        GridView gv = pageGridViews.get(currentPage);
        if (gv == null) return;

        float scaleX = isWindowModeActive ? windowScaleX : 1.0f;
        float scaleY = isWindowModeActive ? windowScaleY : 1.0f;
        int localX = getLocalX(gv, rawX, scaleX);
        int localY = getLocalY(gv, rawY, scaleY);

        List<LauncherItem> currPageList = pages.get(currentPage);
        int pos = findItemPositionAt(gv, localX, localY, currPageList.size());

        if (pos >= 0 && pos < currPageList.size()) {
            int cols = getGridColumns();
            boolean occupiedByWidget = isCellOccupiedByOverlay(currentPage, pos % cols, pos / cols, -1, false);
            boolean isSameItem = (currentPage == dragSourcePage && pos == dragSourcePosition);
            if (!isSameItem && !occupiedByWidget) {
                int childIndex = pos - gv.getFirstVisiblePosition();
                View targetChild = gv.getChildAt(childIndex);
                if (targetChild != null) {
                    if (targetChild != currentHoverView) {
                        clearHoverHighlight();
                        currentHoverView = targetChild;
                        LauncherItem targetItem = currPageList.get(pos);
                        if (targetItem != null) {
                            currentHoverView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(140).setInterpolator(new OvershootInterpolator(1.6f)).start();
                        } else {
                            currentHoverView.setBackgroundResource(R.drawable.drop_target_bg);
                            currentHoverView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(130).setInterpolator(new DecelerateInterpolator(1.5f)).start();
                        }
                    }
                    return;
                }
            }
        }
        clearHoverHighlight();
    }

    private void clearHoverHighlight() {
        if (currentHoverView != null) {
            currentHoverView.setBackground(null);
            currentHoverView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            currentHoverView = null;
        }
    }

    private void endDrag(float rawX, float rawY) {
        clearHoverHighlight();
        hideAllGhostPreviews();
        isPrimaryPointerDown = false;
        cancelPendingLongPressRunnables();

        float dropDensity = getResources().getDisplayMetrics().density;
        boolean dropBarVisible = (llDropTargetsBar != null && llDropTargetsBar.getVisibility() == View.VISIBLE);
        boolean hitRemoveDrop = dropBarVisible && (activeDropZone == DROP_ZONE_REMOVE
                || isDropTargetHit(dropTargetRemove, rawX, rawY, dropDensity));
        boolean hitUninstallDrop = dropBarVisible && (activeDropZone == DROP_ZONE_UNINSTALL
                || isDropTargetHit(dropTargetUninstall, rawX, rawY, dropDensity));
        activeDropZone = DROP_ZONE_NONE;

        if (ivFloatingDrag != null) {
            ivFloatingDrag.animate().rotation(0f).scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
        }

        if (llDropTargetsBar != null) {
            llDropTargetsBar.setVisibility(View.GONE);
        }
        if (dropTargetRemove != null) {
            dropTargetRemove.setVisibility(View.VISIBLE);
            unhighlightDropTarget(dropTargetRemove, R.drawable.drop_target_bg);
        }
        if (dropTargetUninstall != null) {
            dropTargetUninstall.setVisibility(View.VISIBLE);
            unhighlightDropTarget(dropTargetUninstall, R.drawable.drop_target_bg);
        }

        if (dragSourceView != null) {
            dragSourceView.setAlpha(1.0f);
            resetIconScaleTarget(dragSourceView);
        }
        if (dragOverlay != null) {
            dragOverlay.setVisibility(View.GONE);
        }

        isDragActive = false;
        edgeHoldStartTime = 0L;
        pendingEdgeDirection = 0;

        boolean fromDrawer = dragStartedFromDrawer;
        dragStartedFromDrawer = false;

        if (!dragMoved) {
            boolean wasPendingFolderPopup = (pendingFolderDragPopup != null || pendingDragFolder != null);
            clearPendingFolderDragState();
            Log.d("PixelDrag", "endDrag without move: no accidental reorder or drop");
            if (draggedSpecialItem != SPECIAL_ITEM_NONE) {
                draggedSpecialItem = SPECIAL_ITEM_NONE;
                draggedWidgetId = -1;
                draggedWidgetSourcePage = -1;
                if (dragSourceView != null) dragSourceView.setAlpha(1.0f);
                return;
            }
            if (wasPendingFolderPopup) {
                return;
            }
            if (fromDrawer && draggedItem != null && draggedItem.type == LauncherItem.TYPE_APP && draggedItem.appInfo != null) {
                View anchor = (dragSourceView != null && dragSourceView.isAttachedToWindow()) ? dragSourceView : rootLayout;
                showPixelPopup(anchor, draggedItem.appInfo, -1, -1);
                return;
            }
            if (dragSourcePage == -1 && draggedItem != null) {
                addItemToCurrentPage(draggedItem);
            }
            if (dragSourcePage == DRAG_SOURCE_DOCK) {
                if (dragSourceView != null) dragSourceView.setAlpha(1.0f);
                if (draggedItem != null && draggedItem.appInfo != null) {
                    showDockOptionsPopup(dragSourceView, dragSourcePosition, draggedItem.appInfo);
                }
                return;
            }
            if (draggedItem != null) {
                if (draggedItem.type == LauncherItem.TYPE_APP && draggedItem.appInfo != null) {
                    showPixelPopup(dragSourceView, draggedItem.appInfo, dragSourcePage, dragSourcePosition);
                } else if (draggedItem.type == LauncherItem.TYPE_FOLDER && draggedItem.folderInfo != null) {
                    showFolderOptionsPopup(dragSourceView, draggedItem.folderInfo, dragSourcePage, dragSourcePosition);
                }
            }
            return;
        }

        if (pendingFolderDragPopup != null) {
            PopupWindow popupToDismiss = pendingFolderDragPopup;
            clearPendingFolderDragState();
            try {
                popupToDismiss.dismiss();
            } catch (Exception ignored) {}
        } else {
            clearPendingFolderDragState();
        }

        float dragScaleX = isWindowModeActive ? windowScaleX : 1.0f;
        float dragScaleY = isWindowModeActive ? windowScaleY : 1.0f;
        exitPageWindowMode();

        // Check if dropped onto page chips
        int droppedChipIdx = -1;
        for (int c = 0; c < dragChipViews.size(); c++) {
            if (isTouchInsideView(dragChipViews.get(c), rawX, rawY, 8f)) {
                droppedChipIdx = c;
                break;
            }
        }
        int targetPage = (droppedChipIdx >= 0 && droppedChipIdx < pages.size()) ? droppedChipIdx : currentPage;

        if (draggedSpecialItem != SPECIAL_ITEM_NONE) {
            int specialType = draggedSpecialItem;
            int widgetId = draggedWidgetId;
            int widgetSrcPage = draggedWidgetSourcePage;
            int spanCols = draggedWidgetSpanCols;
            int spanRows = draggedWidgetSpanRows;
            String wLabel = draggedWidgetLabel;
            draggedSpecialItem = SPECIAL_ITEM_NONE;
            draggedWidgetId = -1;
            draggedWidgetSourcePage = -1;

            float screenH = getScreenHeight();

            if (specialType == SPECIAL_ITEM_SEARCH_BAR) {
                if (hitRemoveDrop) {
                    setSearchBarVisible(false);
                    Toast.makeText(this, "Removed Google Search Bar", Toast.LENGTH_SHORT).show();
                } else if (rawY < screenH * 0.5f) {
                    setSearchBarTop(true);
                    Toast.makeText(this, "Moved Google Search Bar Up", Toast.LENGTH_SHORT).show();
                } else {
                    setSearchBarTop(false);
                    Toast.makeText(this, "Moved Google Search Bar Down", Toast.LENGTH_SHORT).show();
                }
                return;
            } else if (specialType == SPECIAL_ITEM_CALENDAR) {
                if (hitRemoveDrop) {
                    setAtAGlanceVisible(false);
                    Toast.makeText(this, "Removed Calendar from Home screen", Toast.LENGTH_SHORT).show();
                } else {
                    setAtAGlancePlacement(targetPage, 0, 0);
                    Toast.makeText(this, "Placed At a Glance on Page " + (targetPage + 1), Toast.LENGTH_SHORT).show();
                }
                return;
            } else if (specialType == SPECIAL_ITEM_WIDGET) {
                if (hitRemoveDrop) {
                    removeWidgetFromPage(widgetSrcPage, widgetId);
                    Toast.makeText(this, "Removed " + wLabel, Toast.LENGTH_SHORT).show();
                } else {
                    GridView targetGv = (targetPage >= 0 && targetPage < pageGridViews.size()) ? pageGridViews.get(targetPage) : null;
                    int[] desired = (droppedChipIdx >= 0 || targetGv == null)
                            ? new int[]{0, 0}
                            : computeDropGridOrigin(targetGv, rawX, rawY, spanCols, spanRows);
                    int[] best = findBestOverlaySlot(targetPage, desired[0], desired[1], spanCols, spanRows, widgetId, false);
                    pushAppsOutOfOverlayRect(targetPage, best[0], best[1], spanCols, spanRows);
                    updateWidgetPlacement(widgetId, targetPage, best[0], best[1], spanCols, spanRows);
                    pruneEmptyTrailingPages();
                    savePages();
                    refreshAllPages();
                    if (droppedChipIdx >= 0) scrollToPage(targetPage);
                    Toast.makeText(this, "Placed " + wLabel + " on Page " + (targetPage + 1) + " (Row " + (best[1] + 1) + ")", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }

        if (draggedItem == null) return;

        // Check if dropped onto Remove target (does not uninstall the app)
        if (hitRemoveDrop) {
            if (dragSourcePage == DRAG_SOURCE_DOCK) {
                setDockApp(dragSourcePosition, null);
                Toast.makeText(this, "Removed " + draggedItem.getLabel() + " from Dock", Toast.LENGTH_SHORT).show();
            } else if (dragSourcePage >= 0 && dragSourcePosition >= 0 && dragSourcePage < pages.size()) {
                pages.get(dragSourcePage).set(dragSourcePosition, null);
                pruneEmptyTrailingPages();
                savePages();
                refreshAllPages();
                Toast.makeText(this, "Removed " + draggedItem.getLabel() + " from Home screen", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, draggedItem.getLabel() + " is not on Home screen", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Check if dropped onto Uninstall target: prompt system uninstaller first; only remove shortcuts when Android confirms removal
        if (hitUninstallDrop) {
            if (draggedItem.type == LauncherItem.TYPE_APP && draggedItem.appInfo != null) {
                requestAppUninstall(draggedItem.appInfo);
            } else if (draggedItem.type == LauncherItem.TYPE_FOLDER) {
                Toast.makeText(this, "Cannot uninstall a folder", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Check if dropped onto dock
        View dockContainer = findViewById(R.id.dock_icons);
        if (dockContainer != null && isTouchInsideView(dockContainer, rawX, rawY, 8f)) {
            if (draggedItem.type == LauncherItem.TYPE_APP && draggedItem.appInfo != null) {
                int[] dockLoc = new int[2];
                dockContainer.getLocationOnScreen(dockLoc);
                float relX = rawX - dockLoc[0];
                int slot = (int) (relX / (dockContainer.getWidth() / 5.0f));
                slot = Math.max(0, Math.min(4, slot));

                AppInfo existingDockApp = (slot < dockApps.size()) ? dockApps.get(slot) : null;

                if (dragSourcePage == DRAG_SOURCE_DOCK) {
                    int srcSlot = dragSourcePosition;
                    if (srcSlot >= 0 && srcSlot < 5 && srcSlot != slot) {
                        dockApps.set(srcSlot, existingDockApp);
                        dockApps.set(slot, draggedItem.appInfo);
                        saveDockApps();
                        renderDock();
                        Toast.makeText(this, "Reordered dock", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    if (dragSourcePage >= 0 && dragSourcePosition >= 0 && dragSourcePage < pages.size()) {
                        pages.get(dragSourcePage).set(dragSourcePosition, existingDockApp != null ? new LauncherItem(existingDockApp) : null);
                        savePages();
                        refreshAllPages();
                    }
                    setDockApp(slot, draggedItem.appInfo);
                    Toast.makeText(this, "Placed " + draggedItem.getLabel() + " in Dock slot " + (slot + 1), Toast.LENGTH_SHORT).show();
                }
            } else if (draggedItem.type == LauncherItem.TYPE_FOLDER) {
                Toast.makeText(this, "Dock slots only support apps", Toast.LENGTH_SHORT).show();
            }
            pruneEmptyTrailingPages();
            savePages();
            refreshAllPages();
            return;
        }

        // Check if dropped onto page chips
        if (droppedChipIdx >= 0 && droppedChipIdx < pages.size()) {
            int targetSlot = findFirstEmptySlot(droppedChipIdx);
            if (targetSlot != -1) {
                if (dragSourcePage == DRAG_SOURCE_DOCK) {
                    setDockApp(dragSourcePosition, null);
                } else if (dragSourcePage >= 0 && dragSourcePosition >= 0 && dragSourcePage < pages.size()) {
                    pages.get(dragSourcePage).set(dragSourcePosition, null);
                }
                pages.get(droppedChipIdx).set(targetSlot, draggedItem);
                pruneEmptyTrailingPages();
                savePages();
                refreshAllPages();
                scrollToPage(droppedChipIdx);
                Toast.makeText(this, "Moved " + draggedItem.getLabel() + " to Page " + (droppedChipIdx + 1), Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (dragChipDotsView != null && isTouchInsideView(dragChipDotsView, rawX, rawY, 8f)) {
            int np = addNewPage();
            if (dragSourcePage == DRAG_SOURCE_DOCK) {
                setDockApp(dragSourcePosition, null);
            } else if (dragSourcePage >= 0 && dragSourcePosition >= 0 && dragSourcePage < pages.size()) {
                pages.get(dragSourcePage).set(dragSourcePosition, null);
            }
            pages.get(np).set(0, draggedItem);
            pruneEmptyTrailingPages();
            savePages();
            refreshAllPages();
            scrollToPage(np);
            Toast.makeText(this, "Moved " + draggedItem.getLabel() + " to new Page " + (np + 1), Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPage < 0 || currentPage >= pageGridViews.size()) return;
        GridView gv = pageGridViews.get(currentPage);
        if (gv == null) return;

        int localX = getLocalX(gv, rawX, dragScaleX);
        int localY = getLocalY(gv, rawY, dragScaleY);

        List<LauncherItem> currPageList = pages.get(currentPage);
        int dropPos = findItemPositionAt(gv, localX, localY, currPageList.size());
        int gridCols = getGridColumns();

        if (dropPos < 0 || dropPos >= currPageList.size()
                || isCellOccupiedByOverlay(currentPage, dropPos % gridCols, dropPos / gridCols, -1, false)) {
            dropPos = findFirstEmptySlot(currentPage);
            if (dropPos == -1) {
                int np = addNewPage();
                currentPage = np;
                dropPos = findFirstEmptySlot(currentPage);
                if (dropPos == -1) dropPos = 0;
                currPageList = pages.get(currentPage);
            }
        }

        LauncherItem targetItem = currPageList.get(dropPos);
        boolean isSame = (currentPage == dragSourcePage && dropPos == dragSourcePosition);

        Log.d("PixelDrag", "endDrag drop: dropPos=" + dropPos + " page=" + currentPage + " item=" + draggedItem.getLabel());

        if (isSame) {
            pruneEmptyTrailingPages();
            savePages();
            refreshAllPages();
            return;
        }

        if (targetItem != null) {
            if (dragSourcePage == DRAG_SOURCE_DOCK) {
                setDockApp(dragSourcePosition, null);
            }
            if (targetItem.type == LauncherItem.TYPE_FOLDER) {
                addAppToFolder(currentPage, dropPos, targetItem, draggedItem);
                return;
            } else if (targetItem.type == LauncherItem.TYPE_APP && draggedItem.type == LauncherItem.TYPE_APP) {
                createFolderWithItems(currentPage, dropPos, targetItem, draggedItem);
                return;
            }
        }

        // Dropped on empty slot
        if (dragSourcePage == DRAG_SOURCE_DOCK) {
            setDockApp(dragSourcePosition, null);
        } else if (dragSourcePage >= 0 && dragSourcePage < pages.size()) {
            List<LauncherItem> srcList = pages.get(dragSourcePage);
            if (dragSourcePosition >= 0 && dragSourcePosition < srcList.size()) {
                srcList.set(dragSourcePosition, null);
            }
        }

        if (draggedItem.type == LauncherItem.TYPE_APP && draggedItem.appInfo != null) {
            removeAppFromAllPages(draggedItem.appInfo.packageName);
        } else if (draggedItem.type == LauncherItem.TYPE_FOLDER && draggedItem.folderInfo != null) {
            removeFolderFromAllPages(draggedItem.folderInfo.id);
        }

        currPageList.set(dropPos, draggedItem);
        pruneEmptyTrailingPages();
        savePages();
        refreshAllPages();

        int row = (dropPos / getGridColumns()) + 1;
        int col = (dropPos % getGridColumns()) + 1;
        Toast.makeText(this, "Placed " + draggedItem.getLabel() + " (Row " + row + ", Col " + col + ")", Toast.LENGTH_SHORT).show();
    }

    private void cleanupFoldersOnAllPages() {
        for (int p = 0; p < pages.size(); p++) {
            List<LauncherItem> list = pages.get(p);
            for (int i = 0; i < list.size(); i++) {
                LauncherItem item = list.get(i);
                if (item != null && item.type == LauncherItem.TYPE_FOLDER && item.folderInfo != null) {
                    if (item.folderInfo.apps.isEmpty()) {
                        list.set(i, null);
                    } else if (item.folderInfo.apps.size() == 1) {
                        list.set(i, new LauncherItem(item.folderInfo.apps.get(0)));
                    }
                }
            }
        }
    }

    private void removeAppFromAllPages(String packageName) {
        if (packageName == null) return;
        for (int p = 0; p < pages.size(); p++) {
            List<LauncherItem> list = pages.get(p);
            for (int i = 0; i < list.size(); i++) {
                LauncherItem item = list.get(i);
                if (item != null) {
                    if (item.type == LauncherItem.TYPE_APP && item.appInfo != null &&
                            packageName.equals(item.appInfo.packageName)) {
                        list.set(i, null);
                    } else if (item.type == LauncherItem.TYPE_FOLDER && item.folderInfo != null) {
                        for (int a = item.folderInfo.apps.size() - 1; a >= 0; a--) {
                            if (packageName.equals(item.folderInfo.apps.get(a).packageName)) {
                                item.folderInfo.apps.remove(a);
                            }
                        }
                    }
                }
            }
        }
        cleanupFoldersOnAllPages();
    }

    private void removeFolderFromAllPages(String folderId) {
        if (folderId == null) return;
        for (int p = 0; p < pages.size(); p++) {
            List<LauncherItem> list = pages.get(p);
            for (int i = 0; i < list.size(); i++) {
                LauncherItem item = list.get(i);
                if (item != null && item.type == LauncherItem.TYPE_FOLDER && item.folderInfo != null &&
                        folderId.equals(item.folderInfo.id)) {
                    list.set(i, null);
                }
            }
        }
    }

    private void createFolderWithItems(int pageIdx, int dropPos, LauncherItem targetItem, LauncherItem draggedItem) {
        if (targetItem == null || draggedItem == null) return;
        if (targetItem.appInfo == null || draggedItem.appInfo == null) return;
        if (targetItem.appInfo.packageName.equals(draggedItem.appInfo.packageName)) return;

        FolderInfo folder = new FolderInfo(UUID.randomUUID().toString(), "Folder");
        folder.apps.add(targetItem.appInfo);
        folder.apps.add(draggedItem.appInfo);

        if (dragSourcePage >= 0 && dragSourcePage < pages.size()) {
            List<LauncherItem> srcList = pages.get(dragSourcePage);
            if (dragSourcePosition >= 0 && dragSourcePosition < srcList.size()) {
                srcList.set(dragSourcePosition, null);
            }
        }

        // Remove both items from all pages to guarantee ZERO DUPLICATES
        removeAppFromAllPages(draggedItem.appInfo.packageName);
        removeAppFromAllPages(targetItem.appInfo.packageName);

        // Place new folder at dropPos
        if (pageIdx >= 0 && pageIdx < pages.size()) {
            List<LauncherItem> pageList = pages.get(pageIdx);
            if (dropPos >= 0 && dropPos < pageList.size()) {
                pageList.set(dropPos, new LauncherItem(folder));
            }
        }

        pruneEmptyTrailingPages();
        savePages();
        refreshAllPages();
        Toast.makeText(this, "Created folder with " + targetItem.getLabel() + " & " + draggedItem.getLabel(), Toast.LENGTH_SHORT).show();
    }

    private void addAppToFolder(int pageIdx, int dropPos, LauncherItem folderItem, LauncherItem draggedItem) {
        if (folderItem == null || folderItem.folderInfo == null || draggedItem == null || draggedItem.appInfo == null) return;

        boolean exists = false;
        for (AppInfo a : folderItem.folderInfo.apps) {
            if (a.packageName.equals(draggedItem.appInfo.packageName)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            folderItem.folderInfo.apps.add(draggedItem.appInfo);
        }

        if (dragSourcePage >= 0 && dragSourcePage < pages.size()) {
            List<LauncherItem> srcList = pages.get(dragSourcePage);
            if (dragSourcePosition >= 0 && dragSourcePosition < srcList.size()) {
                srcList.set(dragSourcePosition, null);
            }
        }

        // Remove from all home screen pages so NO duplicate remains
        removeAppFromAllPages(draggedItem.appInfo.packageName);

        pruneEmptyTrailingPages();
        savePages();
        refreshAllPages();
        Toast.makeText(this, "Added to " + folderItem.folderInfo.name, Toast.LENGTH_SHORT).show();
    }

    private void removeItemFromPage(int pageIdx, int itemIdx) {
        if (pageIdx >= 0 && pageIdx < pages.size()) {
            List<LauncherItem> list = pages.get(pageIdx);
            if (itemIdx >= 0 && itemIdx < list.size()) {
                LauncherItem removed = list.get(itemIdx);
                list.set(itemIdx, null);
                pruneEmptyTrailingPages();
                savePages();
                refreshAllPages();
                if (removed != null) {
                    Toast.makeText(this, "Removed " + removed.getLabel(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void addItemToCurrentPage(LauncherItem item) {
        if (item.appInfo != null) {
            removeAppFromAllPages(item.appInfo.packageName);
        }
        if (currentPage >= pages.size()) {
            currentPage = pages.size() - 1;
        }
        int emptySlot = findFirstEmptySlot(currentPage);
        if (emptySlot == -1) {
            int newPage = addNewPage();
            pages.get(newPage).set(0, item);
            currentPage = newPage;
        } else {
            pages.get(currentPage).set(emptySlot, item);
        }
        savePages();
        refreshAllPages();
        scrollToPage(currentPage);
        Toast.makeText(this, "Added to Page " + (currentPage + 1), Toast.LENGTH_SHORT).show();
    }

    private void moveAppToPage(AppInfo app, int fromPage, int fromIdx, int toPage) {
        if (fromPage >= 0 && fromPage < pages.size() && fromIdx >= 0 && fromIdx < pages.get(fromPage).size()) {
            pages.get(fromPage).set(fromIdx, null);
        }
        removeAppFromAllPages(app.packageName);
        if (toPage >= pages.size()) {
            addNewPage();
            toPage = pages.size() - 1;
        }
        int slot = findFirstEmptySlot(toPage);
        if (slot == -1) {
            int newPage = addNewPage();
            toPage = newPage;
            slot = 0;
        }
        pages.get(toPage).set(slot, new LauncherItem(app));
        pruneEmptyTrailingPages();
        savePages();
        refreshAllPages();
        scrollToPage(toPage);
        Toast.makeText(this, "Moved " + app.label + " to Page " + (toPage + 1), Toast.LENGTH_SHORT).show();
    }

    private void preparePixelPopupWindow(PopupWindow popupWindow, float elevationPx) {
        if (popupWindow == null) return;
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(elevationPx);
        popupWindow.setAnimationStyle(0);
        DisplayRefreshHelper.requestInteractiveBoost(this, true);
    }

    private void animateAnchoredPopupIn(View popupView, View anchor, int popupX, int popupY, int popupW) {
        float pivotFracX = 0.5f;
        float pivotFracY = 0.88f;
        float startOffsetYDp = 10f;
        if (anchor != null && popupW > 0) {
            int[] pos = new int[2];
            anchor.getLocationOnScreen(pos);
            float anchorCenterX = pos[0] + anchor.getWidth() * 0.5f;
            float anchorCenterY = pos[1] + anchor.getHeight() * 0.5f;
            pivotFracX = Math.max(0.12f, Math.min(0.88f, (anchorCenterX - popupX) / (float) popupW));
            boolean opensBelow = popupY >= anchorCenterY;
            pivotFracY = opensBelow ? 0.08f : 0.92f;
            startOffsetYDp = opensBelow ? -10f : 10f;
        }
        UiMotion.animatePopIn(popupView, pivotFracX, pivotFracY, startOffsetYDp);
    }

    private void showPixelPopup(final View anchor, final AppInfo app, final int pageIndex, final int itemIndex) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View popupView = inflater.inflate(R.layout.popup_app_menu, null);

        final PopupWindow popupWindow = new PopupWindow(popupView,
                (int) (230 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        preparePixelPopupWindow(popupWindow, 24f);

        // App Info
        popupView.findViewById(R.id.popup_item_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + app.packageName));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open app settings", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Dynamic Page Chips
        LinearLayout llChips = popupView.findViewById(R.id.popup_ll_page_chips);
        llChips.removeAllViews();
        int density = (int) getResources().getDisplayMetrics().density;

        final boolean isNight = LauncherTheme.isNightMode(this);
        for (int p = 0; p < pages.size(); p++) {
            final int targetPage = p;
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(32 * density, 32 * density);
            lp.setMargins(3 * density, 0, 3 * density, 0);
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            chip.setText(String.valueOf(p + 1));
            chip.setTextColor(pageIndex == targetPage ? Color.WHITE : LauncherTheme.textPrimary(isNight));
            chip.setTextSize(13);
            chip.setBackgroundResource(pageIndex == targetPage ? R.drawable.chip_page_active_bg : R.drawable.chip_page_bg);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    moveAppToPage(app, pageIndex, itemIndex, targetPage);
                }
            });
            llChips.addView(chip);
        }

        // Chip [+] to add to a new page
        if (pages.size() < 7) {
            TextView addChip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(32 * density, 32 * density);
            lp.setMargins(3 * density, 0, 3 * density, 0);
            addChip.setLayoutParams(lp);
            addChip.setGravity(Gravity.CENTER);
            addChip.setText("+");
            addChip.setTextColor(LauncherTheme.textPrimary(isNight));
            addChip.setTextSize(14);
            addChip.setBackgroundResource(R.drawable.chip_page_bg);
            addChip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    int newPage = addNewPage();
                    moveAppToPage(app, pageIndex, itemIndex, newPage);
                }
            });
            llChips.addView(addChip);
        }

        // Drag & Drop / Folder mode
        popupView.findViewById(R.id.popup_item_drag).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                if (pageIndex < 0) {
                    closeAppDrawer();
                }
                ClipData.Item clipItem = new ClipData.Item(app.packageName + "::" + app.activityName);
                ClipData dragData = new ClipData(app.label, new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, clipItem);
                View anchorView = anchor != null ? anchor : rootLayout;
                View.DragShadowBuilder shadow = new View.DragShadowBuilder(anchorView);
                LauncherItem itemToDrag = (pageIndex >= 0 && itemIndex >= 0 && itemIndex < pages.get(pageIndex).size())
                        ? pages.get(pageIndex).get(itemIndex)
                        : new LauncherItem(app);
                anchorView.startDragAndDrop(dragData, shadow, itemToDrag, 0);
                Toast.makeText(MainActivity.this, "Drop onto another app to make a folder, or on any page", Toast.LENGTH_SHORT).show();
            }
        });

        // Pin / Remove
        TextView tvPin = popupView.findViewById(R.id.popup_tv_pin_label);
        ImageView ivPin = popupView.findViewById(R.id.popup_iv_pin_icon);
        if (pageIndex >= 0) {
            tvPin.setText("Remove from Home");
            ivPin.setImageResource(R.drawable.ic_remove);
            popupView.findViewById(R.id.popup_item_pin).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    removeItemFromPage(pageIndex, itemIndex);
                }
            });
        } else {
            tvPin.setText("Add to Home");
            ivPin.setImageResource(R.drawable.ic_pin);
            popupView.findViewById(R.id.popup_item_pin).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    addItemToCurrentPage(new LauncherItem(app));
                    closeAppDrawer();
                }
            });
        }

        // Uninstall (hidden for system apps to match Pixel Launcher)
        View uninstallRow = popupView.findViewById(R.id.popup_item_uninstall);
        if (uninstallRow != null) {
            if (isAppUninstallable(app.packageName)) {
                uninstallRow.setVisibility(View.VISIBLE);
                uninstallRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        popupWindow.dismiss();
                        requestAppUninstall(app);
                    }
                });
            } else {
                uninstallRow.setVisibility(View.GONE);
            }
        }

        int anchorX = (int) lastTouchRawX;
        int anchorY = (int) lastTouchRawY;
        if (anchor != null) {
            int[] screenPos = new int[2];
            anchor.getLocationOnScreen(screenPos);
            anchorX = screenPos[0];
            anchorY = screenPos[1];
        }
        int popupW = (int) (230 * density);

        int x = Math.max(20, Math.min(anchorX, getScreenWidth() - popupW - 20));
        int y = Math.max(100, anchorY - (int) (160 * density));
        if (y < 120 && anchor != null) {
            y = anchorY + anchor.getHeight() + 10;
        }

        popupWindow.showAtLocation(rootLayout, Gravity.NO_GRAVITY, x, y);
        animateAnchoredPopupIn(popupView, anchor, x, y, popupW);
    }

    private boolean isAppUninstallable(String packageName) {
        if (packageName == null || packageName.isEmpty() || packageName.equals(getPackageName())) {
            return false;
        }
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(packageName, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void requestAppUninstall(AppInfo app) {
        if (app == null || app.packageName == null || app.packageName.isEmpty()) return;
        if (!isAppUninstallable(app.packageName)) {
            Toast.makeText(this, app.label + " is a system app and cannot be uninstalled", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri pkgUri = Uri.fromParts("package", app.packageName, null);
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, pkgUri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        } catch (Exception firstErr) {
            try {
                Intent fallback = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, pkgUri);
                fallback.putExtra(Intent.EXTRA_RETURN_RESULT, false);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(fallback);
            } catch (Exception e) {
                Toast.makeText(this, "Could not start uninstallation", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showFolderOptionsPopup(final View anchor, final FolderInfo folder, final int pageIndex, final int itemIndex) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View popupView = inflater.inflate(R.layout.popup_folder_options, null);

        final PopupWindow popupWindow = new PopupWindow(popupView,
                (int) (230 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        preparePixelPopupWindow(popupWindow, 24f);

        // Dynamic Page Chips
        LinearLayout llChips = popupView.findViewById(R.id.folder_ll_page_chips);
        llChips.removeAllViews();
        int density = (int) getResources().getDisplayMetrics().density;

        final boolean isNight = LauncherTheme.isNightMode(this);
        for (int p = 0; p < pages.size(); p++) {
            final int targetPage = p;
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(32 * density, 32 * density);
            lp.setMargins(3 * density, 0, 3 * density, 0);
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            chip.setText(String.valueOf(p + 1));
            chip.setTextColor(pageIndex == targetPage ? Color.WHITE : LauncherTheme.textPrimary(isNight));
            chip.setTextSize(13);
            chip.setBackgroundResource(pageIndex == targetPage ? R.drawable.chip_page_active_bg : R.drawable.chip_page_bg);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    if (pageIndex != targetPage) {
                        LauncherItem item = pages.get(pageIndex).get(itemIndex);
                        pages.get(pageIndex).set(itemIndex, null);
                        int destPage = targetPage;
                        int slot = findFirstEmptySlot(destPage);
                        if (slot == -1) {
                            int np = addNewPage();
                            pages.get(np).set(0, item);
                            destPage = np;
                        } else {
                            pages.get(destPage).set(slot, item);
                        }
                        pruneEmptyTrailingPages();
                        savePages();
                        refreshAllPages();
                        scrollToPage(destPage);
                        Toast.makeText(MainActivity.this, "Moved " + folder.name + " to Page " + (destPage + 1), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            llChips.addView(chip);
        }

        // Dissolve folder
        popupView.findViewById(R.id.folder_item_dissolve).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                pages.get(pageIndex).set(itemIndex, null);
                for (AppInfo a : folder.apps) {
                    int slot = findFirstEmptySlot(pageIndex);
                    if (slot == -1) {
                        int np = addNewPage();
                        pages.get(np).set(0, new LauncherItem(a));
                    } else {
                        pages.get(pageIndex).set(slot, new LauncherItem(a));
                    }
                }
                pruneEmptyTrailingPages();
                savePages();
                refreshAllPages();
                Toast.makeText(MainActivity.this, "Dissolved folder " + folder.name, Toast.LENGTH_SHORT).show();
            }
        });

        // Remove folder
        popupView.findViewById(R.id.folder_item_remove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                removeItemFromPage(pageIndex, itemIndex);
            }
        });

        int anchorX = (int) lastTouchRawX;
        int anchorY = (int) lastTouchRawY;
        if (anchor != null) {
            int[] screenPos = new int[2];
            anchor.getLocationOnScreen(screenPos);
            anchorX = screenPos[0];
            anchorY = screenPos[1];
        }
        int popupW = (int) (230 * density);

        int x = Math.max(20, Math.min(anchorX, getScreenWidth() - popupW - 20));
        int y = Math.max(100, anchorY - (int) (140 * density));
        if (y < 120 && anchor != null) {
            y = anchorY + anchor.getHeight() + 10;
        }

        popupWindow.showAtLocation(rootLayout, Gravity.NO_GRAVITY, x, y);
        animateAnchoredPopupIn(popupView, anchor, x, y, popupW);
    }

    private void showFolderPopup(final View anchorView, final FolderInfo folder, final int pageIdx, final int itemIdx) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View dialogView = inflater.inflate(R.layout.popup_folder_view, null);

        final EditText etTitle = dialogView.findViewById(R.id.et_folder_title);
        etTitle.setText(folder.name);

        GridView gv = dialogView.findViewById(R.id.gv_folder_apps);
        final AppAdapter adapter = new AppAdapter(this, folder.apps, 56, 12.0f);
        gv.setAdapter(adapter);

        float density = getResources().getDisplayMetrics().density;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;

        int popupWidth = (int) (310 * density);
        dialogView.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int popupHeight = dialogView.getMeasuredHeight();

        final PopupWindow popupWindow = new PopupWindow(dialogView,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        preparePixelPopupWindow(popupWindow, 28f * density);

        // Position folder directly near the folder icon (Android 17 style)
        int targetX;
        int targetY;
        float pivotX;
        float pivotY;

        int margin = (int) (14 * density);
        int topMargin = (int) (48 * density);
        int bottomMargin = (int) (70 * density);

        if (anchorView != null) {
            int[] loc = new int[2];
            anchorView.getLocationOnScreen(loc);
            float iconCenterX = loc[0] + anchorView.getWidth() / 2f;
            float iconCenterY = loc[1] + anchorView.getHeight() / 2f;

            targetX = (int) (iconCenterX - popupWidth / 2f);
            if (targetX < margin) targetX = margin;
            if (targetX + popupWidth > screenWidth - margin) {
                targetX = screenWidth - margin - popupWidth;
            }

            // Center vertically over folder icon, clamped within screen margins
            targetY = (int) (iconCenterY - popupHeight / 2f);
            if (targetY < topMargin) {
                targetY = topMargin;
            }
            if (targetY + popupHeight > screenHeight - bottomMargin) {
                targetY = screenHeight - bottomMargin - popupHeight;
            }

            // Morph pivot originates exactly from folder icon center
            pivotX = iconCenterX - targetX;
            pivotY = iconCenterY - targetY;
        } else {
            targetX = (screenWidth - popupWidth) / 2;
            targetY = (screenHeight - popupHeight) / 2;
            pivotX = popupWidth / 2f;
            pivotY = popupHeight / 2f;
        }

        dialogView.setPivotX(pivotX);
        dialogView.setPivotY(pivotY);
        dialogView.setScaleX(0.18f);
        dialogView.setScaleY(0.18f);
        dialogView.setAlpha(0f);

        gv.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                lastTouchRawX = event.getRawX();
                lastTouchRawY = event.getRawY();
                if (isDragActive) {
                    handleDragTouchEvent(event);
                    return true;
                }
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touchDownSeq++;
                    touchDownTimeMs = SystemClock.uptimeMillis();
                    touchDownRawX = event.getRawX();
                    touchDownRawY = event.getRawY();
                    touchMovedBeyondStillSlop = false;
                    touchMovedBeyondTapSlop = false;
                    isMultiTouchGesture = false;
                    isPrimaryPointerDown = true;
                    cancelPendingLongPressRunnables();
                } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    isMultiTouchGesture = true;
                    touchMovedBeyondTapSlop = true;
                    cancelPendingLongPressRunnables();
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float moveDist = (float) Math.hypot(event.getRawX() - touchDownRawX, event.getRawY() - touchDownRawY);
                    if (moveDist > longPressTouchSlopPx) {
                        touchMovedBeyondStillSlop = true;
                        cancelPendingLongPressRunnables();
                    }
                    if (moveDist > tapMoveSlopPx) {
                        touchMovedBeyondTapSlop = true;
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (action == MotionEvent.ACTION_UP
                            && Math.hypot(event.getRawX() - touchDownRawX, event.getRawY() - touchDownRawY) > tapMoveSlopPx) {
                        touchMovedBeyondTapSlop = true;
                    }
                    if (touchMovedBeyondTapSlop || isMultiTouchGesture) {
                        lastSwipeEndTimeMs = SystemClock.uptimeMillis();
                    }
                    isPrimaryPointerDown = false;
                    cancelPendingLongPressRunnables();
                }
                return false;
            }
        });

        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                cancelPendingLongPressRunnables();
                if (pendingFolderDragPopup == popupWindow) {
                    clearPendingFolderDragState();
                }
            }
        });

        gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isGestureTapEligible()) return;
                AppInfo app = adapter.getItem(position);
                if (app != null) {
                    View iconView = view != null ? view.findViewById(R.id.iv_app_icon) : null;
                    pendingFolderLaunchPopup = popupWindow;
                    dialogView.animate().alpha(0f).setDuration(90L).start();
                    launchApp(app, iconView != null ? iconView : view);
                    mainHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (pendingFolderLaunchPopup == popupWindow) {
                                pendingFolderLaunchPopup = null;
                                try {
                                    if (popupWindow.isShowing()) {
                                        popupWindow.dismiss();
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }, 650L);
                }
            }
        });

        gv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, final View view, final int position, long id) {
                if (!canStartGestureDragHold()) {
                    return false;
                }
                final AppInfo app = adapter.getItem(position);
                if (app == null) return false;
                scheduleDeferredDragHold(new Runnable() {
                    @Override
                    public void run() {
                        if (popupWindow.isShowing()) {
                            startDragFromFolderPopup(view, app, folder, pageIdx, itemIdx, position, popupWindow, dialogView);
                        }
                    }
                }, LONG_PRESS_DRAWER_HOLD_MS);
                return true;
            }
        });

        etTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                folder.name = s.toString();
                savePages();
                if (pageIdx < pageAdapters.size()) {
                    pageAdapters.get(pageIdx).notifyDataSetChanged();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        popupWindow.showAtLocation(rootLayout, Gravity.NO_GRAVITY, targetX, targetY);
        UiMotion.animateFolderPopIn(dialogView, pivotX, pivotY);
    }

    private void savePages() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            JSONArray pagesArr = new JSONArray();
            for (int p = 0; p < pages.size(); p++) {
                JSONArray pageArr = new JSONArray();
                List<LauncherItem> pList = pages.get(p);
                for (int slot = 0; slot < pList.size(); slot++) {
                    LauncherItem item = pList.get(slot);
                    if (item == null) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("slot", slot);
                    obj.put("type", item.type);
                    if (item.type == LauncherItem.TYPE_APP && item.appInfo != null) {
                        obj.put("pkg", item.appInfo.packageName);
                        obj.put("act", item.appInfo.activityName);
                    } else if (item.type == LauncherItem.TYPE_FOLDER && item.folderInfo != null) {
                        obj.put("id", item.folderInfo.id);
                        obj.put("name", item.folderInfo.name);
                        JSONArray appsArr = new JSONArray();
                        for (AppInfo a : item.folderInfo.apps) {
                            JSONObject appObj = new JSONObject();
                            appObj.put("pkg", a.packageName);
                            appObj.put("act", a.activityName);
                            appsArr.put(appObj);
                        }
                        obj.put("apps", appsArr);
                    }
                    pageArr.put(obj);
                }
                pagesArr.put(pageArr);
            }
            prefs.edit().putString(KEY_PAGES_JSON, pagesArr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadPages() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGES_JSON, null);

        pages.clear();

        if (json != null && !json.isEmpty()) {
            try {
                JSONArray pagesArr = new JSONArray(json);
                for (int p = 0; p < pagesArr.length(); p++) {
                    JSONArray pageArr = pagesArr.getJSONArray(p);
                    List<LauncherItem> pageList = createEmptyPage();
                    for (int i = 0; i < pageArr.length(); i++) {
                        JSONObject obj = pageArr.getJSONObject(i);
                        int slot = obj.optInt("slot", i);
                        if (slot < 0 || slot >= getPageSlots()) {
                            slot = findFirstEmptySlotInList(pageList);
                            if (slot == -1) continue;
                        }
                        int type = obj.optInt("type", LauncherItem.TYPE_APP);
                        if (type == LauncherItem.TYPE_APP) {
                            String pkg = obj.optString("pkg");
                            String act = obj.optString("act");
                            AppInfo app = findApp(pkg, act);
                            if (app != null) {
                                pageList.set(slot, new LauncherItem(app));
                            }
                        } else if (type == LauncherItem.TYPE_FOLDER) {
                            FolderInfo folder = new FolderInfo(obj.optString("id"), obj.optString("name"));
                            JSONArray appsArr = obj.optJSONArray("apps");
                            if (appsArr != null) {
                                for (int a = 0; a < appsArr.length(); a++) {
                                    JSONObject aObj = appsArr.getJSONObject(a);
                                    AppInfo app = findApp(aObj.optString("pkg"), aObj.optString("act"));
                                    if (app != null) {
                                        folder.apps.add(app);
                                    }
                                }
                            }
                            if (folder.apps.size() > 1) {
                                pageList.set(slot, new LauncherItem(folder));
                            } else if (folder.apps.size() == 1) {
                                pageList.set(slot, new LauncherItem(folder.apps.get(0)));
                            }
                        }
                    }
                    if (!isPageEmpty(pageList) || pages.isEmpty()) {
                        pages.add(pageList);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (pages.isEmpty()) {
            List<LauncherItem> page0 = createEmptyPage();
            String[] defaultApps = new String[]{
                    "com.android.chrome",
                    "com.google.android.youtube",
                    "app.revanced.android.youtube",
                    "com.google.android.apps.photos",
                    "com.google.android.gm",
                    "com.android.vending",
                    "com.whatsapp",
                    "com.android.settings"
            };

            int slotIdx = 0;
            int maxSlots = getPageSlots();
            for (String pkg : defaultApps) {
                AppInfo app = findAppByPkg(pkg);
                if (app != null && slotIdx < maxSlots) {
                    page0.set(slotIdx++, new LauncherItem(app));
                }
            }

            FolderInfo googleFolder = new FolderInfo(UUID.randomUUID().toString(), "Google");
            String[] gApps = new String[]{"com.google.android.gm", "com.google.android.apps.docs", "com.google.android.apps.maps"};
            for (String gPkg : gApps) {
                AppInfo a = findAppByPkg(gPkg);
                if (a != null) {
                    googleFolder.apps.add(a);
                }
            }
            if (googleFolder.apps.size() >= 2 && slotIdx < maxSlots) {
                page0.set(slotIdx++, new LauncherItem(googleFolder));
            }

            pages.add(page0);
            savePages();
        }

        cleanupFoldersOnAllPages();
        migrateToUnifiedGridIfNeeded();
        migrateCompactAtAGlanceIfNeeded();
        pruneEmptyTrailingPages();
        if (pages.isEmpty()) {
            pages.add(createEmptyPage());
        }
        savePages();
        setupPagesLayout();
    }

    private AppInfo findApp(String pkg, String act) {
        for (AppInfo a : appList) {
            if (a.packageName.equals(pkg) && a.activityName.equals(act)) {
                return a;
            }
        }
        return findAppByPkg(pkg);
    }

    private AppInfo findAppByPkg(String pkg) {
        for (AppInfo a : appList) {
            if (a.packageName.equals(pkg)) {
                return a;
            }
        }
        return null;
    }

    private void setupDrawerController() {
        drawerController = new AppDrawerController(
                this,
                homeScreen,
                appDrawer,
                gvApps,
                etDrawerSearch,
                new AppDrawerController.Host() {
                    @Override
                    public boolean canStartOpeningDrawer() {
                        return !isDragActive
                                && !isWindowModeActive
                                && (customizationPanelOverlay == null || customizationPanelOverlay.getVisibility() != View.VISIBLE)
                                && (hsvHomePages == null || !hsvHomePages.isPaging());
                    }

                    @Override
                    public boolean isTouchInScrollbar(float rawX, float rawY) {
                        return drawerScrollbar != null && drawerScrollbar.isInTouchZone(rawX, rawY);
                    }

                    @Override
                    public void onDrawerOpenFractionChanged(float openFraction) {
                        View activeDrawerCapsule = getActiveDrawerSearchView();
                        if (activeDrawerCapsule != null) {
                            float inv = 1f - openFraction;
                            float density = getResources().getDisplayMetrics().density;
                            activeDrawerCapsule.setTranslationY(inv * 14f * density);
                            float barScale = 0.96f + 0.04f * openFraction;
                            activeDrawerCapsule.setScaleX(barScale);
                            activeDrawerCapsule.setScaleY(barScale);
                        }
                        lastDrawerOpenFraction = openFraction;
                        float blurDensity = getResources().getDisplayMetrics().density;
                        UiMotion.setWindowBackgroundBlurRadius(getWindow(), Math.round(30f * blurDensity * openFraction));
                        boolean suspendForDrawer = openFraction > 0.02f;
                        if (isEdgeExclusionSuspendedForDrawer != suspendForDrawer) {
                            isEdgeExclusionSuspendedForDrawer = suspendForDrawer;
                            updateHomeSystemGestureExclusion();
                        }
                        boolean visiblyOpen = openFraction >= 0.45f;
                        if (isDrawerVisiblyOpenForBars != visiblyOpen) {
                            isDrawerVisiblyOpenForBars = visiblyOpen;
                            LauncherTheme.updateSystemBarsForDrawer(
                                    getWindow(),
                                    LauncherTheme.isNightMode(MainActivity.this),
                                    isDrawerVisiblyOpenForBars
                            );
                        }
                    }

                    @Override
                    public void onDrawerDragStarted() {
                        cancelPendingLongPressRunnables();
                        if (hsvHomePages != null) {
                            hsvHomePages.setDragBlocked(true);
                        }
                    }

                    @Override
                    public void onDrawerDragEnded() {
                        if (hsvHomePages != null) {
                            hsvHomePages.setDragBlocked(false);
                        }
                    }

                    @Override
                    public void dispatchCancelToChildren(MotionEvent cancelEvent) {
                        MainActivity.super.dispatchTouchEvent(cancelEvent);
                    }

                    @Override
                    public void hideKeyboard() {
                        MainActivity.this.hideKeyboard(etDrawerSearch);
                    }

                    @Override
                    public int getScreenHeight() {
                        return MainActivity.this.getScreenHeight();
                    }
                }
        );
    }

    private void cancelPendingDragHoldRunnable() {
        if (pendingDragHoldRunnable != null) {
            mainHandler.removeCallbacks(pendingDragHoldRunnable);
            pendingDragHoldRunnable = null;
        }
    }

    private void cancelPendingLongPressRunnables() {
        if (searchBarLongPressRunnable != null) {
            mainHandler.removeCallbacks(searchBarLongPressRunnable);
            searchBarLongPressRunnable = null;
        }
        if (atAGlanceLongPressRunnable != null) {
            mainHandler.removeCallbacks(atAGlanceLongPressRunnable);
            atAGlanceLongPressRunnable = null;
        }
        if (widgetLongPressRunnable != null) {
            mainHandler.removeCallbacks(widgetLongPressRunnable);
            widgetLongPressRunnable = null;
        }
        cancelPendingDragHoldRunnable();
    }

    private boolean isGestureTapEligible() {
        if (isDragActive
                || (hsvHomePages != null && hsvHomePages.isPaging())
                || (drawerController != null && drawerController.isDragging())
                || (drawerScrollbar != null && drawerScrollbar.isDragging())) {
            return false;
        }
        if (touchMovedBeyondTapSlop || isMultiTouchGesture) {
            if (isPrimaryPointerDown || (SystemClock.uptimeMillis() - lastSwipeEndTimeMs) < 350L) {
                return false;
            }
        }
        return true;
    }

    private boolean canStartGestureDragHold() {
        return !isDragActive
                && !isWindowModeActive
                && isPrimaryPointerDown
                && !touchMovedBeyondStillSlop
                && !isMultiTouchGesture
                && (hsvHomePages == null || !hsvHomePages.isPaging())
                && (drawerController == null || !drawerController.isDragging())
                && (drawerScrollbar == null || !drawerScrollbar.isDragging());
    }

    private void scheduleDeferredDragHold(final Runnable triggerAction, long requiredHoldMs) {
        if (!canStartGestureDragHold()) return;
        cancelPendingDragHoldRunnable();
        final int expectedSeq = touchDownSeq;
        long elapsed = Math.max(0L, SystemClock.uptimeMillis() - touchDownTimeMs);
        long remaining = Math.max(0L, requiredHoldMs - elapsed);
        pendingDragHoldRunnable = new Runnable() {
            @Override
            public void run() {
                pendingDragHoldRunnable = null;
                if (expectedSeq == touchDownSeq && canStartGestureDragHold()) {
                    triggerAction.run();
                }
            }
        };
        mainHandler.postDelayed(pendingDragHoldRunnable, remaining);
    }

    private void handleDeferredViewLongClick(final Runnable dragStarter, boolean alreadyScheduled, long requiredHoldMs) {
        boolean drawerOpen = (drawerController != null && drawerController.isVisibleOrAnimating());
        if (drawerOpen || !canStartGestureDragHold()) return;
        if (alreadyScheduled) {
            // Let the dispatchTouchEvent timer complete the full intentional hold duration
            return;
        }
        scheduleDeferredDragHold(dragStarter, requiredHoldMs);
    }

    private void updateTouchGestureState(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownSeq++;
                touchDownTimeMs = SystemClock.uptimeMillis();
                touchDownRawX = ev.getRawX();
                touchDownRawY = ev.getRawY();
                touchMovedBeyondStillSlop = false;
                touchMovedBeyondTapSlop = false;
                isMultiTouchGesture = false;
                isPrimaryPointerDown = true;
                cancelPendingLongPressRunnables();
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                isMultiTouchGesture = true;
                touchMovedBeyondTapSlop = true;
                cancelPendingLongPressRunnables();
                break;

            case MotionEvent.ACTION_MOVE:
                float moveDist = (float) Math.hypot(ev.getRawX() - touchDownRawX, ev.getRawY() - touchDownRawY);
                if (moveDist > longPressTouchSlopPx) {
                    touchMovedBeyondStillSlop = true;
                }
                if (moveDist > tapMoveSlopPx) {
                    touchMovedBeyondTapSlop = true;
                }
                boolean controllerDragging = (hsvHomePages != null && hsvHomePages.isPaging())
                        || (drawerController != null && drawerController.isDragging())
                        || (drawerScrollbar != null && drawerScrollbar.isDragging());
                if (controllerDragging) {
                    touchMovedBeyondTapSlop = true;
                }
                if (controllerDragging || touchMovedBeyondStillSlop) {
                    cancelPendingLongPressRunnables();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (ev.getActionMasked() == MotionEvent.ACTION_UP
                        && Math.hypot(ev.getRawX() - touchDownRawX, ev.getRawY() - touchDownRawY) > tapMoveSlopPx) {
                    touchMovedBeyondTapSlop = true;
                }
                if (touchMovedBeyondTapSlop || isMultiTouchGesture
                        || (hsvHomePages != null && hsvHomePages.isPaging())
                        || (drawerController != null && drawerController.isDragging())
                        || (drawerScrollbar != null && drawerScrollbar.isDragging())) {
                    lastSwipeEndTimeMs = SystemClock.uptimeMillis();
                }
                isPrimaryPointerDown = false;
                cancelPendingLongPressRunnables();
                break;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouchRawX = ev.getRawX();
        lastTouchRawY = ev.getRawY();

        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            DisplayRefreshHelper.requestInteractiveBoost(this, true);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            DisplayRefreshHelper.requestInteractiveBoost(this, false);
        }

        if (isDragActive) {
            handleDragTouchEvent(ev);
            return true;
        }

        updateTouchGestureState(ev);

        if (drawerController != null && drawerController.onDispatchTouchEvent(ev)) {
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            boolean drawerClosed = (drawerController == null || !drawerController.isVisibleOrAnimating());
            boolean panelClosed = (customizationPanelOverlay == null || customizationPanelOverlay.getVisibility() != View.VISIBLE);
            if (drawerClosed && panelClosed && !isWindowModeActive) {
                final int downSeq = touchDownSeq;
                // 1. Long press detector for Original Google Search Bar (require deliberate stillness + hold)
                final View topSearchContainer = findViewById(R.id.search_bar_top_container);
                final View bottomSearchContainer = findViewById(R.id.search_bar_container);
                final View activeSearchContainer = (topSearchContainer != null && topSearchContainer.getVisibility() == View.VISIBLE)
                        ? topSearchContainer
                        : ((bottomSearchContainer != null && bottomSearchContainer.getVisibility() == View.VISIBLE) ? bottomSearchContainer : null);

                if (activeSearchContainer != null && isTouchInsideView(activeSearchContainer, ev.getRawX(), ev.getRawY(), 0f)) {
                    searchBarLongPressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            searchBarLongPressRunnable = null;
                            if (downSeq == touchDownSeq && canStartGestureDragHold()
                                    && (drawerController == null || !drawerController.isVisibleOrAnimating())
                                    && activeSearchContainer.getVisibility() == View.VISIBLE) {
                                startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", activeSearchContainer);
                            }
                        }
                    };
                    mainHandler.postDelayed(searchBarLongPressRunnable, LONG_PRESS_SPECIAL_HOLD_MS);
                }

                // 2. Long press detector for Calendar on current page
                final View currGlanceView = pageGlanceViews.get(currentPage);
                if (currGlanceView != null && currGlanceView.getVisibility() == View.VISIBLE
                        && isTouchInsideView(currGlanceView, ev.getRawX(), ev.getRawY(), 0f)) {
                    atAGlanceLongPressRunnable = new Runnable() {
                        @Override
                        public void run() {
                            atAGlanceLongPressRunnable = null;
                            if (downSeq == touchDownSeq && canStartGestureDragHold()
                                    && (drawerController == null || !drawerController.isVisibleOrAnimating())
                                    && currGlanceView.isAttachedToWindow()) {
                                startDragSpecialItem(SPECIAL_ITEM_CALENDAR, "At a Glance (Calendar)", currGlanceView);
                            }
                        }
                    };
                    mainHandler.postDelayed(atAGlanceLongPressRunnable, LONG_PRESS_SPECIAL_HOLD_MS);
                }

                // 3. Long press detector for placed AppWidgets on current page
                List<View> currWidgets = pageWidgetWrappers.get(currentPage);
                if (currWidgets != null) {
                    for (final View wWrapper : currWidgets) {
                        if (wWrapper != null && wWrapper.getVisibility() == View.VISIBLE
                                && isTouchInsideView(wWrapper, ev.getRawX(), ev.getRawY(), 0f)) {
                            Object tagObj = wWrapper.getTag();
                            if (tagObj instanceof WidgetDragTag) {
                                final WidgetDragTag dTag = (WidgetDragTag) tagObj;
                                widgetLongPressRunnable = new Runnable() {
                                    @Override
                                    public void run() {
                                        widgetLongPressRunnable = null;
                                        if (downSeq == touchDownSeq && canStartGestureDragHold()
                                                && (drawerController == null || !drawerController.isVisibleOrAnimating())
                                                && wWrapper.isAttachedToWindow()) {
                                            startDragWidget(dTag.pw.page, dTag.pw.id, dTag.label, dTag.pw.spanCols, dTag.pw.spanRows, wWrapper);
                                        }
                                    }
                                };
                                mainHandler.postDelayed(widgetLongPressRunnable, LONG_PRESS_WIDGET_HOLD_MS);
                            }
                            break;
                        }
                    }
                }
            }
        }

        return super.dispatchTouchEvent(ev);
    }

    private void setupAtAGlance() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        if (tvDate != null) {
            tvDate.setText(currentDate);
        }
        refreshGlanceDateText();

        View atAGlance = findViewById(R.id.at_a_glance);
        if (atAGlance != null) {
            atAGlance.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    openCalendar();
                }
            });
            atAGlance.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(final View v) {
                    handleDeferredViewLongClick(new Runnable() {
                        @Override
                        public void run() {
                            startDragSpecialItem(SPECIAL_ITEM_CALENDAR, "At a Glance (Calendar)", v);
                        }
                    }, atAGlanceLongPressRunnable != null, LONG_PRESS_SPECIAL_HOLD_MS);
                    return true;
                }
            });
        }
        updateAtAGlanceVisibility();
    }

    private void openCalendar() {
        try {
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            builder.appendPath(Long.toString(System.currentTimeMillis()));
            Intent intent = new Intent(Intent.ACTION_VIEW, builder.build());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.calendar");
                if (intent != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Calendar app not found", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        }
    }

    private void setupCustomizationPanel() {
        customizationPanelOverlay = findViewById(R.id.customization_panel_overlay);
        panelCustomizationCard = findViewById(R.id.panel_customization_card);
        cardWallpaperCurrent = findViewById(R.id.card_wallpaper_current);
        ivWallpaperThumbCurrent = findViewById(R.id.iv_wallpaper_thumb_current);
        cardWallpaperTheme1 = findViewById(R.id.card_wallpaper_theme1);
        cardWallpaperTheme2 = findViewById(R.id.card_wallpaper_theme2);
        cardWallpaperTheme3 = findViewById(R.id.card_wallpaper_theme3);
        rowActionWallpaper = findViewById(R.id.row_action_wallpaper);
        rowActionWidgets = findViewById(R.id.row_action_widgets);
        rowActionApps = findViewById(R.id.row_action_apps);
        rowActionSettings = findViewById(R.id.row_action_settings);
        dragBlurOverlay = findViewById(R.id.drag_blur_overlay);
        hsvDragPageChips = findViewById(R.id.hsv_drag_page_chips);
        llDragPageChips = findViewById(R.id.ll_drag_page_chips);

        if (customizationPanelOverlay != null) {
            customizationPanelOverlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideCustomizationPanel();
                }
            });
        }

        View.OnClickListener wpClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideCustomizationPanel();
                openWallpaperAndStyle();
            }
        };

        if (cardWallpaperCurrent != null) {
            cardWallpaperCurrent.setClipToOutline(true);
            cardWallpaperCurrent.setOnClickListener(wpClickListener);
        }
        if (cardWallpaperTheme1 != null) {
            cardWallpaperTheme1.setClipToOutline(true);
            cardWallpaperTheme1.setOnClickListener(wpClickListener);
        }
        if (cardWallpaperTheme2 != null) {
            cardWallpaperTheme2.setClipToOutline(true);
            cardWallpaperTheme2.setOnClickListener(wpClickListener);
        }
        if (cardWallpaperTheme3 != null) {
            cardWallpaperTheme3.setClipToOutline(true);
            cardWallpaperTheme3.setOnClickListener(wpClickListener);
        }
        if (rowActionWallpaper != null) rowActionWallpaper.setOnClickListener(wpClickListener);

        if (rowActionWidgets != null) {
            rowActionWidgets.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideCustomizationPanel();
                    showGroupedWidgetPicker();
                }
            });
        }

        if (rowActionApps != null) {
            rowActionApps.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideCustomizationPanel();
                    openAppDrawer();
                }
            });
        }

        if (rowActionSettings != null) {
            rowActionSettings.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideCustomizationPanel();
                    showModernHomeSettings();
                }
            });
        }
    }

    private void showCustomizationPanel() {
        if (isDragActive || isWindowModeActive || customizationPanelOverlay == null) return;
        if (appDrawer != null && appDrawer.getVisibility() == View.VISIBLE) return;

        boolean loadedWp = false;
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            Drawable wp = wm.getDrawable();
            if (wp != null && ivWallpaperThumbCurrent != null) {
                ivWallpaperThumbCurrent.setImageDrawable(wp);
                loadedWp = true;
            }
        } catch (Throwable ignored) {}
        if (!loadedWp && ivWallpaperThumbCurrent != null) {
            ivWallpaperThumbCurrent.setImageResource(R.drawable.wallpaper_card_2_bg);
        }

        final float startOffsetY = 80f * getResources().getDisplayMetrics().density;
        Object oldAnim = customizationPanelOverlay.getTag();
        if (oldAnim instanceof ValueAnimator) {
            ((ValueAnimator) oldAnim).cancel();
        }
        DisplayRefreshHelper.requestInteractiveBoost(this, true);
        customizationPanelOverlay.setVisibility(View.VISIBLE);
        updateHomeSystemGestureExclusion();
        customizationPanelOverlay.setAlpha(0f);
        if (panelCustomizationCard != null) {
            panelCustomizationCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            panelCustomizationCard.setTranslationY(startOffsetY);
            DisplayRefreshHelper.setFrameContentVelocity(panelCustomizationCard, 2800f);
        }

        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(DisplayRefreshHelper.scaleDurationMs(220L));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!pacer.shouldRender(animation)) return;
                float raw = (float) animation.getAnimatedValue();
                float ease = UiMotion.EMPHASIZED_DECEL.getInterpolation(raw);
                if (customizationPanelOverlay != null) {
                    customizationPanelOverlay.setAlpha(Math.min(1f, raw * 1.4f));
                }
                if (panelCustomizationCard != null) {
                    panelCustomizationCard.setTranslationY(startOffsetY * (1f - ease));
                }
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (customizationPanelOverlay != null) {
                    customizationPanelOverlay.setTag(null);
                    customizationPanelOverlay.setAlpha(1f);
                }
                if (panelCustomizationCard != null) {
                    panelCustomizationCard.setLayerType(View.LAYER_TYPE_NONE, null);
                    panelCustomizationCard.setTranslationY(0f);
                }
                DisplayRefreshHelper.requestInteractiveBoost(MainActivity.this, false);
            }
        });
        customizationPanelOverlay.setTag(va);
        va.start();
    }

    private void hideCustomizationPanel() {
        if (customizationPanelOverlay == null || customizationPanelOverlay.getVisibility() != View.VISIBLE) return;

        Object oldAnim = customizationPanelOverlay.getTag();
        if (oldAnim instanceof ValueAnimator) {
            ((ValueAnimator) oldAnim).cancel();
        }
        DisplayRefreshHelper.requestInteractiveBoost(this, true);
        final float targetOffsetY = 80f * getResources().getDisplayMetrics().density;
        final float startAlpha = customizationPanelOverlay.getAlpha();
        final float startY = panelCustomizationCard != null ? panelCustomizationCard.getTranslationY() : 0f;
        if (panelCustomizationCard != null) {
            panelCustomizationCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            DisplayRefreshHelper.setFrameContentVelocity(panelCustomizationCard, 2800f);
        }
        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(DisplayRefreshHelper.scaleDurationMs(180L));
        va.setInterpolator(new AccelerateInterpolator(1.4f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!pacer.shouldRender(animation)) return;
                float t = (float) animation.getAnimatedValue();
                if (customizationPanelOverlay != null) {
                    customizationPanelOverlay.setAlpha(startAlpha * (1f - t));
                }
                if (panelCustomizationCard != null) {
                    panelCustomizationCard.setTranslationY(startY + (targetOffsetY - startY) * t);
                }
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (panelCustomizationCard != null) {
                    panelCustomizationCard.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (!canceled) {
                        panelCustomizationCard.setTranslationY(0f);
                    }
                }
                if (customizationPanelOverlay != null) {
                    customizationPanelOverlay.setTag(null);
                    if (!canceled) {
                        customizationPanelOverlay.setVisibility(View.GONE);
                        customizationPanelOverlay.setAlpha(1f);
                        updateHomeSystemGestureExclusion();
                    }
                }
                DisplayRefreshHelper.requestInteractiveBoost(MainActivity.this, false);
            }
        });
        customizationPanelOverlay.setTag(va);
        va.start();
    }

    private void openWallpaperAndStyle() {
        try {
            Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
            startActivity(Intent.createChooser(intent, "Wallpaper and style"));
        } catch (Exception e) {
            try {
                Intent fallback = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.wallpaper");
                if (fallback != null) startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void openWidgetsPicker() {
        try {
            int appWidgetId = appWidgetHost.allocateAppWidgetId();
            Intent pickIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
            pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open widget picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static final int REQUEST_CODE_ROLE_HOME = 1099;

    public boolean isDefaultHome() {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                return getPackageName().equals(resolveInfo.activityInfo.packageName);
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void requestDefaultHomeRole() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.app.role.RoleManager roleManager = (android.app.role.RoleManager) getSystemService(Context.ROLE_SERVICE);
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                        Intent roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME);
                        startActivityForResult(roleIntent, REQUEST_CODE_ROLE_HOME);
                        return;
                    } else {
                        Toast.makeText(this, "LW Launcher is already your default launcher!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void checkFirstRunRolePrompt() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean prompted = prefs.getBoolean("pref_prompted_default_home", false);
            if (!prompted && !isDefaultHome()) {
                prefs.edit().putBoolean("pref_prompted_default_home", true).apply();
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing() && !isDefaultHome()) {
                            requestDefaultHomeRole();
                        }
                    }
                }, 1000);
            }
        } catch (Exception ignored) {}
    }

    private void showHomeSettingsDialog() {
        showModernHomeSettings();
    }

    private void showModernHomeSettings() {
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_home_settings, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(sheetView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setWindowAnimations(0);
        }
        DisplayRefreshHelper.requestInteractiveBoost(this, true);
        dialog.show();
        DisplayRefreshHelper.applyWindowRefreshPreference(dialog.getWindow(), MainActivity.this, true);
        UiMotion.animateSheetSlideIn(sheetView);

        View btnClose = dialog.findViewById(R.id.btn_close_settings);
        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        // Default Launcher
        TextView tvStatus = dialog.findViewById(R.id.tv_default_home_status);
        TextView btnDefault = dialog.findViewById(R.id.btn_action_default_home);
        View cardDefault = dialog.findViewById(R.id.card_default_launcher);
        boolean isDef = isDefaultHome();
        if (tvStatus != null) {
            tvStatus.setText(isDef ? "Active as Default Launcher" : "Not default launcher");
        }
        final boolean isNight = LauncherTheme.isNightMode(this);
        if (btnDefault != null) {
            btnDefault.setText(isDef ? "Active" : "Set Default");
            btnDefault.setBackgroundResource(isDef ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
            btnDefault.setTextColor(isDef ? Color.WHITE : LauncherTheme.textPrimary(isNight));
        }
        View.OnClickListener defaultClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestDefaultHomeRole();
            }
        };
        if (cardDefault != null) cardDefault.setOnClickListener(defaultClick);
        if (btnDefault != null) btnDefault.setOnClickListener(defaultClick);

        // Switches
        Switch swSearchBar = dialog.findViewById(R.id.sw_toggle_search_bar);
        if (swSearchBar != null) {
            swSearchBar.setChecked(isSearchBarVisible());
            swSearchBar.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setSearchBarVisible(isChecked);
                }
            });
        }

        Switch swAtAGlance = dialog.findViewById(R.id.sw_toggle_at_a_glance);
        if (swAtAGlance != null) {
            swAtAGlance.setChecked(isAtAGlanceVisible());
            swAtAGlance.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setAtAGlanceVisible(isChecked);
                }
            });
        }

        Switch swDrawerGoogleWidget = dialog.findViewById(R.id.sw_toggle_drawer_google_widget);
        if (swDrawerGoogleWidget != null) {
            swDrawerGoogleWidget.setChecked(isDrawerGoogleWidgetEnabled());
            swDrawerGoogleWidget.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setDrawerGoogleWidgetEnabled(isChecked);
                }
            });
        }

        // Grid Mode Pills
        final TextView btn4x5 = dialog.findViewById(R.id.btn_grid_mode_4x5);
        final TextView btn5x5 = dialog.findViewById(R.id.btn_grid_mode_5x5);
        final TextView btn16x9 = dialog.findViewById(R.id.btn_grid_mode_16x9);

        final Runnable updateGridPills = new Runnable() {
            @Override
            public void run() {
                int inactiveText = LauncherTheme.textPrimary(isNight);
                if (btn4x5 != null) {
                    boolean active = currentGridMode == GRID_MODE_4X5;
                    btn4x5.setBackgroundResource(active ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
                    btn4x5.setTextColor(active ? Color.WHITE : inactiveText);
                }
                if (btn5x5 != null) {
                    boolean active = currentGridMode == GRID_MODE_5X5;
                    btn5x5.setBackgroundResource(active ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
                    btn5x5.setTextColor(active ? Color.WHITE : inactiveText);
                }
                if (btn16x9 != null) {
                    boolean active = currentGridMode == GRID_MODE_16X9;
                    btn16x9.setBackgroundResource(active ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
                    btn16x9.setTextColor(active ? Color.WHITE : inactiveText);
                }
            }
        };
        updateGridPills.run();

        if (btn4x5 != null) {
            btn4x5.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setGridMode(GRID_MODE_4X5);
                    updateGridPills.run();
                }
            });
        }
        if (btn5x5 != null) {
            btn5x5.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setGridMode(GRID_MODE_5X5);
                    updateGridPills.run();
                }
            });
        }
        if (btn16x9 != null) {
            btn16x9.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setGridMode(GRID_MODE_16X9);
                    updateGridPills.run();
                }
            });
        }

        // Animation frame rate auto-adapts to maximum display refresh rate (switcher hidden cleanly)
        View cardRefreshRate = dialog.findViewById(R.id.card_refresh_rate);
        if (cardRefreshRate != null) {
            cardRefreshRate.setVisibility(View.GONE);
        }
        DisplayRefreshHelper.applyWindowRefreshPreference(dialog.getWindow(), MainActivity.this, true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface di) {
                DisplayRefreshHelper.applyWindowRefreshPreference(MainActivity.this, false);
            }
        });

        // System Settings
        View cardSystem = dialog.findViewById(R.id.card_system_settings);
        if (cardSystem != null) {
            cardSystem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void showWidgetMenu() {
        showGroupedWidgetPicker();
    }

    private void showGroupedWidgetPicker() {
        if (appWidgetManager == null) return;
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_widget_picker, null);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(sheetView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setGravity(Gravity.BOTTOM);
            dialog.getWindow().setWindowAnimations(0);
        }
        DisplayRefreshHelper.requestInteractiveBoost(this, true);
        dialog.show();
        DisplayRefreshHelper.applyWindowRefreshPreference(dialog.getWindow(), MainActivity.this, true);
        UiMotion.animateSheetSlideIn(sheetView);

        View btnClose = dialog.findViewById(R.id.btn_close_widget_picker);
        if (btnClose != null) {
            btnClose.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
        }

        View btnAddSearchBar = dialog.findViewById(R.id.btn_add_search_bar_widget);
        if (btnAddSearchBar != null) {
            btnAddSearchBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    setSearchBarVisible(true);
                    Toast.makeText(MainActivity.this, "Google Search Bar added", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnAddCalendar = dialog.findViewById(R.id.btn_add_calendar_widget);
        if (btnAddCalendar != null) {
            btnAddCalendar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    setAtAGlancePlacement(currentPage, 0, 0);
                    Toast.makeText(MainActivity.this, "Added At a Glance to Page " + (currentPage + 1), Toast.LENGTH_SHORT).show();
                }
            });
        }

        final LinearLayout llAccordion = dialog.findViewById(R.id.ll_widgets_apps_accordion);
        final EditText etSearch = dialog.findViewById(R.id.et_widget_picker_search);

        if (llAccordion == null) return;

        final PackageManager pm = getPackageManager();
        List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        if (providers == null) providers = new ArrayList<>();

        Map<String, List<AppWidgetProviderInfo>> grouped = new HashMap<>();
        for (AppWidgetProviderInfo info : providers) {
            if (info == null || info.provider == null) continue;
            String pkg = info.provider.getPackageName();
            if (!grouped.containsKey(pkg)) {
                grouped.put(pkg, new ArrayList<AppWidgetProviderInfo>());
            }
            grouped.get(pkg).add(info);
        }

        List<String> sortedPkgs = new ArrayList<>(grouped.keySet());
        Collections.sort(sortedPkgs, new Comparator<String>() {
            @Override
            public int compare(String p1, String p2) {
                String l1 = getAppLabel(pm, p1);
                String l2 = getAppLabel(pm, p2);
                return l1.compareToIgnoreCase(l2);
            }
        });

        final List<View> appGroupViews = new ArrayList<>();
        LayoutInflater inflater = LayoutInflater.from(this);
        float density = getResources().getDisplayMetrics().density;

        for (final String pkg : sortedPkgs) {
            final List<AppWidgetProviderInfo> widgetList = grouped.get(pkg);
            if (widgetList == null || widgetList.isEmpty()) continue;

            final View groupView = inflater.inflate(R.layout.item_widget_app_group, llAccordion, false);
            ImageView ivIcon = groupView.findViewById(R.id.iv_app_group_icon);
            TextView tvName = groupView.findViewById(R.id.tv_app_group_name);
            TextView tvCount = groupView.findViewById(R.id.tv_app_widget_count);
            final ImageView ivChevron = groupView.findViewById(R.id.iv_app_group_chevron);
            final LinearLayout llWidgetsList = groupView.findViewById(R.id.ll_app_widgets_list);
            View rowHeader = groupView.findViewById(R.id.row_app_header);

            String appLabel = getAppLabel(pm, pkg);
            tvName.setText(appLabel);
            groupView.setTag(appLabel.toLowerCase(Locale.getDefault()));

            Drawable appIcon = null;
            try {
                appIcon = pm.getApplicationIcon(pkg);
            } catch (Exception ignored) {}
            if (appIcon != null) {
                ivIcon.setImageDrawable(appIcon);
            } else {
                ivIcon.setImageResource(R.drawable.ic_widgets);
            }

            tvCount.setText(widgetList.size() + (widgetList.size() == 1 ? " widget" : " widgets"));

            rowHeader.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean isExpanded = llWidgetsList.getVisibility() == View.VISIBLE;
                    if (isExpanded) {
                        llWidgetsList.setVisibility(View.GONE);
                        ivChevron.animate().rotation(0f).setDuration(180).start();
                    } else {
                        llWidgetsList.setVisibility(View.VISIBLE);
                        ivChevron.animate().rotation(180f).setDuration(180).start();
                    }
                }
            });

            for (final AppWidgetProviderInfo wInfo : widgetList) {
                View entryView = inflater.inflate(R.layout.item_widget_entry, llWidgetsList, false);
                ImageView ivPreview = entryView.findViewById(R.id.iv_widget_entry_preview);
                TextView tvLabel = entryView.findViewById(R.id.tv_widget_entry_label);
                TextView tvDims = entryView.findViewById(R.id.tv_widget_entry_dimensions);
                View btnAdd = entryView.findViewById(R.id.btn_widget_entry_add);

                String wLabel = (wInfo.label != null && !wInfo.label.isEmpty()) ? wInfo.label : appLabel;
                tvLabel.setText(wLabel);

                int spanX = Math.max(1, Math.min(5, (int) Math.ceil(wInfo.minWidth / (density > 0 ? density : 1) / 70.0)));
                int spanY = Math.max(1, Math.min(4, (int) Math.ceil(wInfo.minHeight / (density > 0 ? density : 1) / 70.0)));
                tvDims.setText(spanX + " × " + spanY);

                if (appIcon != null) {
                    ivPreview.setImageDrawable(appIcon);
                }

                View.OnClickListener onPickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        pickSpecificWidget(wInfo);
                    }
                };
                entryView.setOnClickListener(onPickListener);
                if (btnAdd != null) {
                    btnAdd.setOnClickListener(onPickListener);
                }

                llWidgetsList.addView(entryView);
            }

            llAccordion.addView(groupView);
            appGroupViews.add(groupView);
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String q = s == null ? "" : s.toString().trim().toLowerCase(Locale.getDefault());
                    for (View g : appGroupViews) {
                        String tag = (String) g.getTag();
                        if (tag == null || q.isEmpty() || tag.contains(q)) {
                            g.setVisibility(View.VISIBLE);
                        } else {
                            g.setVisibility(View.GONE);
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void pickSpecificWidget(AppWidgetProviderInfo info) {
        if (info == null || info.provider == null) return;
        try {
            int appWidgetId = appWidgetHost.allocateAppWidgetId();
            boolean allowed = appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider);
            if (allowed) {
                configureOrAddWidget(appWidgetId);
            } else {
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                startActivityForResult(intent, REQUEST_BIND_CUSTOM_APPWIDGET);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not add widget: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getAppLabel(PackageManager pm, String packageName) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null) return label.toString();
        } catch (Exception ignored) {}
        return packageName;
    }

    private boolean isSearchBarVisible() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_KEY_SHOW_SEARCH_BAR, true);
    }

    private void setSearchBarVisible(boolean visible) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_KEY_SHOW_SEARCH_BAR, visible).apply();
        updateSearchBarVisibility();
    }

    private boolean isSearchBarTop() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_KEY_SEARCH_POS_TOP, false);
    }

    private void setSearchBarTop(boolean top) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_KEY_SEARCH_POS_TOP, top)
                .putBoolean(PREF_KEY_SHOW_SEARCH_BAR, true)
                .apply();
        updateSearchBarVisibility();
    }

    private void updateSearchBarVisibility() {
        attachOriginalSearchWidgetToActiveContainer();
    }

    private void attachOriginalSearchWidgetToActiveContainer() {
        FrameLayout topContainer = findViewById(R.id.search_bar_top_container);
        FrameLayout bottomContainer = findViewById(R.id.search_bar_container);
        if (topContainer == null && bottomContainer == null) return;

        boolean visible = isSearchBarVisible();
        boolean topPos = isSearchBarTop();

        if (!visible) {
            if (topContainer != null) topContainer.setVisibility(View.GONE);
            if (bottomContainer != null) bottomContainer.setVisibility(View.GONE);
            recalculateHomeGridHeights();
            return;
        }

        FrameLayout target = topPos ? topContainer : bottomContainer;
        FrameLayout other = topPos ? bottomContainer : topContainer;
        if (other != null) other.setVisibility(View.GONE);
        if (target == null) return;

        if (searchWidgetHostView != null) {
            View fallbackPill = findViewById(R.id.pixel_search_bar);
            if (fallbackPill != null) {
                fallbackPill.setVisibility(View.GONE);
            }
            target.setClipChildren(false);
            target.setClipToPadding(false);
            if (searchWidgetHostView.getParent() instanceof ViewGroup && searchWidgetHostView.getParent() != target) {
                ((ViewGroup) searchWidgetHostView.getParent()).removeView(searchWidgetHostView);
            }
            if (searchWidgetHostView.getParent() == null) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_VERTICAL
                );
                searchWidgetHostView.setLayoutParams(lp);
                target.addView(searchWidgetHostView);
            }
            searchWidgetHostView.setClipToPadding(false);
            updateGoogleWidgetSizing(searchWidgetHostView, topPos ? 32f : 40f, 56, 64);
        } else if (topPos && bottomContainer != null && topContainer != null) {
            View fallbackPill = findViewById(R.id.pixel_search_bar);
            if (fallbackPill != null && fallbackPill.getParent() != topContainer) {
                ((ViewGroup) fallbackPill.getParent()).removeView(fallbackPill);
                topContainer.addView(fallbackPill);
            }
        } else if (!topPos && bottomContainer != null) {
            View fallbackPill = findViewById(R.id.pixel_search_bar);
            if (fallbackPill != null && fallbackPill.getParent() != bottomContainer) {
                ((ViewGroup) fallbackPill.getParent()).removeView(fallbackPill);
                bottomContainer.addView(fallbackPill);
            }
        }

        target.setVisibility(View.VISIBLE);
        recalculateHomeGridHeights();
    }

    private boolean isAtAGlanceVisible() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_KEY_SHOW_AT_A_GLANCE, true);
    }

    private void setAtAGlanceVisible(boolean visible) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_KEY_SHOW_AT_A_GLANCE, visible).apply();
        updateAtAGlanceVisibility();
        final int savedPage = Math.max(0, Math.min(pages.size() - 1, currentPage));
        setupPagesLayout();
        refreshAllPages();
        restorePageAfterRebuild(savedPage);
    }

    private void restorePageAfterRebuild(final int targetPage) {
        if (pages.isEmpty()) return;
        final int clamped = Math.max(0, Math.min(pages.size() - 1, targetPage));
        currentPage = clamped;
        updatePageDots(clamped);
        if (hsvHomePages != null) {
            hsvHomePages.post(new Runnable() {
                @Override
                public void run() {
                    int w = getScreenWidth();
                    if (hsvHomePages != null && w > 0) {
                        hsvHomePages.scrollTo(clamped * w, 0);
                    }
                    updatePageDots(clamped);
                }
            });
        }
    }

    private int getAtAGlancePage() {
        int p = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_KEY_AT_A_GLANCE_PAGE, 0);
        return Math.max(0, p);
    }

    private int getAtAGlanceCol() {
        return Math.max(0, getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_KEY_AT_A_GLANCE_COL, 0));
    }

    private int getAtAGlanceRow() {
        return Math.max(0, getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_KEY_AT_A_GLANCE_ROW, 0));
    }

    private int getGlanceSpanCols() {
        int cols = getGridColumns();
        if (cols >= 9) return 5;
        return Math.min(3, cols);
    }

    private int getGlanceSpanRows() {
        int rows = getGridRows();
        return (rows >= 10) ? 2 : 1;
    }

    private void setAtAGlancePlacement(int page, int col, int row) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(PREF_KEY_SHOW_AT_A_GLANCE, true)
                .putInt(PREF_KEY_AT_A_GLANCE_PAGE, page)
                .putInt(PREF_KEY_AT_A_GLANCE_COL, col)
                .putInt(PREF_KEY_AT_A_GLANCE_ROW, row)
                .apply();
        updateAtAGlanceVisibility();
        pruneEmptyTrailingPages();
        savePages();
        setupPagesLayout();
        refreshAllPages();
        restorePageAfterRebuild(page);
    }

    private void updateAtAGlanceVisibility() {
        // Legacy header stays gone; Calendar now lives directly on the unified home page grid
        View atAGlance = findViewById(R.id.at_a_glance);
        if (atAGlance != null) {
            atAGlance.setVisibility(View.GONE);
        }
    }

    private void migrateToUnifiedGridIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_KEY_V206_MIGRATED, false)) {
            return;
        }

        // 1. Migrate Search Bar strictly to Up or Down (never middle or custom page widget)
        int oldSearchPos = prefs.getInt("pref_search_bar_pos", 2);
        int oldSearchPage = prefs.getInt("pref_search_bar_page", -1);
        boolean wasTop = (oldSearchPage >= 0 && oldSearchPos == 0);
        prefs.edit()
                .putBoolean(PREF_KEY_SEARCH_POS_TOP, wasTop)
                .remove("pref_search_bar_page")
                .remove("pref_search_bar_pos")
                .apply();

        // 2. Ensure Calendar (At a Glance) header page index is valid; header sits cleanly above GridView
        int oldGlancePage = prefs.getInt(PREF_KEY_AT_A_GLANCE_PAGE, -1);
        int gPage = (oldGlancePage >= 0 && oldGlancePage < pages.size()) ? oldGlancePage : 0;
        prefs.edit()
                .putInt(PREF_KEY_AT_A_GLANCE_PAGE, gPage)
                .putInt(PREF_KEY_AT_A_GLANCE_COL, 0)
                .putInt(PREF_KEY_AT_A_GLANCE_ROW, 0)
                .remove("pref_at_a_glance_pos")
                .apply();

        prefs.edit().putBoolean(PREF_KEY_V206_MIGRATED, true).apply();
        savePages();
        updateSearchBarVisibility();
        updateAtAGlanceVisibility();
    }

    private void migrateCompactAtAGlanceIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(PREF_KEY_V303_COMPACT_GLANCE_MIGRATED, false)) {
            return;
        }
        int gPage = getAtAGlancePage();
        if (isAtAGlanceVisible() && getAtAGlanceRow() == 0 && gPage >= 0 && gPage < pages.size()) {
            List<LauncherItem> pageList = pages.get(gPage);
            int cols = getGridColumns();
            int totalSlots = pageList.size();
            boolean row0Empty = true;
            for (int c = 0; c < Math.min(cols, totalSlots); c++) {
                if (pageList.get(c) != null) {
                    row0Empty = false;
                    break;
                }
            }
            List<PlacedWidget> widgets = getPlacedWidgetsForPage(gPage);
            boolean row0HasWidget = false;
            boolean hasLowerWidget = false;
            for (PlacedWidget w : widgets) {
                if (w.row == 0) {
                    row0HasWidget = true;
                } else {
                    hasLowerWidget = true;
                }
            }
            boolean hasLowerApps = false;
            for (int s = cols; s < totalSlots; s++) {
                if (pageList.get(s) != null) {
                    hasLowerApps = true;
                    break;
                }
            }
            if (row0Empty && !row0HasWidget && (hasLowerApps || hasLowerWidget) && totalSlots > cols) {
                for (int s = 0; s < totalSlots - cols; s++) {
                    pageList.set(s, pageList.get(s + cols));
                }
                for (int s = Math.max(0, totalSlots - cols); s < totalSlots; s++) {
                    pageList.set(s, null);
                }
                for (PlacedWidget w : widgets) {
                    if (w.row > 0) {
                        updateWidgetPlacement(w.id, gPage, w.col, w.row - 1, w.spanCols, w.spanRows);
                    }
                }
                savePages();
            }
        }
        prefs.edit().putBoolean(PREF_KEY_V303_COMPACT_GLANCE_MIGRATED, true).apply();
    }

    private void refreshGlanceDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        String formatted = sdf.format(new Date());
        int wIcon = (liveWeatherController != null)
                ? liveWeatherController.getIconResId()
                : R.drawable.ic_glance_partly_cloudy_day;
        String wText = (liveWeatherController != null)
                ? liveWeatherController.getFormattedTemp()
                : "Weather";

        for (View v : pageGlanceViews.values()) {
            if (v == null) continue;
            View tv = v.findViewWithTag("glance_date_text");
            if (tv instanceof TextView) {
                ((TextView) tv).setText(formatted);
            }
            View ivW = v.findViewWithTag("glance_weather_icon");
            if (ivW instanceof ImageView) {
                ((ImageView) ivW).setImageResource(wIcon);
            }
            View tvW = v.findViewWithTag("glance_weather_text");
            if (tvW instanceof TextView) {
                ((TextView) tvW).setText(wText);
            }
        }
    }

    private LinearLayout createPageGlanceView() {
        float density = getResources().getDisplayMetrics().density;
        boolean dense = (currentGridMode == GRID_MODE_16X9);
        final LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.TOP | Gravity.START);
        int padStart = Math.round(18 * density);
        int padEnd = Math.round(16 * density);
        int padTop = Math.round((dense ? 6 : 13) * density);
        int padBottom = Math.round((dense ? 5 : 10) * density);
        wrapper.setPadding(padStart, padTop, padEnd, padBottom);

        View.OnLongClickListener glanceLongClickListener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                handleDeferredViewLongClick(new Runnable() {
                    @Override
                    public void run() {
                        startDragSpecialItem(SPECIAL_ITEM_CALENDAR, "At a Glance", wrapper);
                    }
                }, atAGlanceLongPressRunnable != null, LONG_PRESS_SPECIAL_HOLD_MS);
                return true;
            }
        };

        TextView tvD = new TextView(this);
        tvD.setTag("glance_date_text");
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
        tvD.setText(sdf.format(new Date()));
        tvD.setTextSize(TypedValue.COMPLEX_UNIT_SP, dense ? 16 : 19);
        tvD.setTextColor(Color.WHITE);
        tvD.setIncludeFontPadding(false);
        tvD.setLetterSpacing(-0.01f);
        tvD.setMaxLines(1);
        tvD.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvD.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tvD.setShadowLayer(3f, 0f, 1.5f, Color.parseColor("#88000000"));
        tvD.setPadding(0, 0, Math.round(14 * density), Math.round(2 * density));
        tvD.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tvD.setClickable(true);
        tvD.setFocusable(true);
        tvD.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isGestureTapEligible()) return;
                openCalendar();
            }
        });
        tvD.setOnLongClickListener(glanceLongClickListener);
        wrapper.addView(tvD);

        LinearLayout weatherRow = new LinearLayout(this);
        weatherRow.setOrientation(LinearLayout.HORIZONTAL);
        weatherRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpWeatherRow = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpWeatherRow.topMargin = Math.round((dense ? 2 : 3) * density);
        weatherRow.setLayoutParams(lpWeatherRow);
        weatherRow.setPadding(0, Math.round(2 * density), Math.round(16 * density), Math.round(2 * density));
        weatherRow.setClickable(true);
        weatherRow.setFocusable(true);

        ImageView ivWeather = new ImageView(this);
        ivWeather.setTag("glance_weather_icon");
        int iconSize = Math.round((dense ? 14 : 18) * density);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(iconSize, iconSize);
        lpIcon.setMarginEnd(Math.round((dense ? 5 : 6) * density));
        ivWeather.setLayoutParams(lpIcon);
        ivWeather.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivWeather.setImageResource(liveWeatherController != null
                ? liveWeatherController.getIconResId()
                : R.drawable.ic_glance_partly_cloudy_day);
        weatherRow.addView(ivWeather);

        TextView tvWeather = new TextView(this);
        tvWeather.setTag("glance_weather_text");
        tvWeather.setText(liveWeatherController != null
                ? liveWeatherController.getFormattedTemp()
                : "Weather");
        tvWeather.setTextSize(TypedValue.COMPLEX_UNIT_SP, dense ? 11.5f : 14f);
        tvWeather.setTextColor(Color.parseColor("#F2FFFFFF"));
        tvWeather.setIncludeFontPadding(false);
        tvWeather.setMaxLines(1);
        tvWeather.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvWeather.setShadowLayer(2.5f, 0f, 1f, Color.parseColor("#88000000"));
        weatherRow.addView(tvWeather);

        weatherRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isGestureTapEligible()) return;
                launchWeather();
            }
        });
        weatherRow.setOnLongClickListener(glanceLongClickListener);
        wrapper.addView(weatherRow);

        wrapper.setOnLongClickListener(glanceLongClickListener);

        return wrapper;
    }

    private void setGridMode(int mode) {
        currentGridMode = mode;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(PREF_KEY_GRID_MODE, currentGridMode).apply();
        loadPages();
        setupPagesLayout();
        refreshAllPages();
        String name = (mode == GRID_MODE_4X5) ? "4 x 5 (Standard 60dp)" : ((mode == GRID_MODE_5X5) ? "5 x 5 (Medium 54dp)" : "16 x 9 (Dense 36dp)");
        Toast.makeText(this, "Grid set to " + name, Toast.LENGTH_SHORT).show();
    }

    private void configureOrAddWidget(int appWidgetId) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (info == null) return;
        if (info.configure != null) {
            try {
                appWidgetHost.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, REQUEST_CREATE_APPWIDGET, null);
            } catch (Exception e) {
                completeAddWidget(appWidgetId);
            }
        } else {
            completeAddWidget(appWidgetId);
        }
    }

    private int[] computeWidgetSpan(AppWidgetProviderInfo info) {
        float density = getResources().getDisplayMetrics().density;
        if (density <= 0f) density = 1f;
        int cols = getGridColumns();
        int rows = getGridRows();

        int minWDp = (info != null) ? Math.round(info.minWidth / density) : 140;
        int minHDp = (info != null) ? Math.round(info.minHeight / density) : 110;
        int spanC = Math.max(1, Math.min(4, (minWDp + 30) / 70));
        int spanR = Math.max(1, Math.min(4, (minHDp + 30) / 70));

        if (cols >= 9) {
            spanC = Math.min(cols, spanC * 2);
            spanR = Math.min(rows, spanR * 2);
        } else {
            spanC = Math.min(cols, spanC);
            spanR = Math.min(rows, spanR);
        }
        return new int[]{spanC, spanR};
    }

    private void completeAddWidget(int appWidgetId) {
        AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(appWidgetId);
        int[] span = computeWidgetSpan(info);
        int targetPage = currentPage;
        int[] pos = findFirstFreeSpotAvoidingAll(targetPage, span[0], span[1], -1, false);
        if (pos[0] < 0) {
            // Try currentPage pushing apps if necessary
            pos = findBestOverlaySlot(targetPage, 0, 0, span[0], span[1], -1, false);
        }
        pushAppsOutOfOverlayRect(targetPage, pos[0], pos[1], span[0], span[1]);
        savePlacedWidget(targetPage, appWidgetId, pos[0], pos[1], span[0], span[1]);
        pruneEmptyTrailingPages();
        savePages();
        renderAllSavedWidgets();
        refreshAllPages();
        Toast.makeText(this, "Widget added to Page " + (targetPage + 1), Toast.LENGTH_SHORT).show();
    }

    private List<PlacedWidget> getPlacedWidgetsForPage(int pageIdx) {
        List<PlacedWidget> list = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGE_WIDGETS_JSON, null);
        int cols = getGridColumns();
        int rows = getGridRows();
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (obj.optInt("page", -1) == pageIdx) {
                        PlacedWidget pw = new PlacedWidget();
                        pw.id = obj.optInt("id");
                        pw.page = pageIdx;
                        int defaultSpanC = 2;
                        int defaultSpanR = 2;
                        try {
                            AppWidgetProviderInfo info = (appWidgetManager != null) ? appWidgetManager.getAppWidgetInfo(pw.id) : null;
                            if (info != null) {
                                int[] s = computeWidgetSpan(info);
                                defaultSpanC = s[0];
                                defaultSpanR = s[1];
                            }
                        } catch (Exception ignored) {}
                        pw.spanCols = Math.max(1, Math.min(cols, obj.optInt("spanCols", defaultSpanC)));
                        pw.spanRows = Math.max(1, Math.min(rows, obj.optInt("spanRows", defaultSpanR)));
                        pw.col = Math.max(0, Math.min(cols - pw.spanCols, obj.optInt("col", 0)));
                        pw.row = Math.max(0, Math.min(rows - pw.spanRows, obj.optInt("row", Math.min(rows - pw.spanRows, 1))));
                        list.add(pw);
                    }
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    private void savePlacedWidget(int pageIdx, int widgetId, int col, int row, int spanCols, int spanRows) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGE_WIDGETS_JSON, null);
        JSONArray arr = new JSONArray();
        try {
            if (json != null) arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("page", pageIdx);
            obj.put("id", widgetId);
            obj.put("col", col);
            obj.put("row", row);
            obj.put("spanCols", spanCols);
            obj.put("spanRows", spanRows);
            arr.put(obj);
            prefs.edit().putString(KEY_PAGE_WIDGETS_JSON, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void updateWidgetPlacement(int widgetId, int targetPage, int col, int row, int spanCols, int spanRows) {
        deleteWidgetById(widgetId);
        savePlacedWidget(targetPage, widgetId, col, row, spanCols, spanRows);
    }

    private void deleteWidgetById(int widgetId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGE_WIDGETS_JSON, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                JSONArray newArr = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (obj.optInt("id", -1) != widgetId) {
                        newArr.put(obj);
                    }
                }
                prefs.edit().putString(KEY_PAGE_WIDGETS_JSON, newArr.toString()).apply();
            } catch (Exception ignored) {}
        }
    }

    private boolean isCellOccupiedByOverlay(int pageIdx, int col, int row, int ignoreWidgetId, boolean ignoreGlance) {
        List<PlacedWidget> widgets = getPlacedWidgetsForPage(pageIdx);
        for (PlacedWidget w : widgets) {
            if (w.id == ignoreWidgetId) continue;
            if (col >= w.col && col < w.col + w.spanCols && row >= w.row && row < w.row + w.spanRows) {
                return true;
            }
        }
        return false;
    }

    private boolean isRectOccupiedByOverlay(int pageIdx, int col, int row, int spanCols, int spanRows, int ignoreWidgetId, boolean ignoreGlance) {
        for (int r = row; r < row + spanRows; r++) {
            for (int c = col; c < col + spanCols; c++) {
                if (isCellOccupiedByOverlay(pageIdx, c, r, ignoreWidgetId, ignoreGlance)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int[] findFirstFreeSpotAvoidingAll(int pageIdx, int spanCols, int spanRows, int ignoreWidgetId, boolean ignoreGlance) {
        int cols = getGridColumns();
        int rows = getGridRows();
        List<LauncherItem> pageList = (pageIdx >= 0 && pageIdx < pages.size()) ? pages.get(pageIdx) : null;
        for (int r = 0; r <= rows - spanRows; r++) {
            for (int c = 0; c <= cols - spanCols; c++) {
                if (isRectOccupiedByOverlay(pageIdx, c, r, spanCols, spanRows, ignoreWidgetId, ignoreGlance)) {
                    continue;
                }
                boolean hasApp = false;
                if (pageList != null) {
                    for (int rr = r; rr < r + spanRows && !hasApp; rr++) {
                        for (int cc = c; cc < c + spanCols; cc++) {
                            int slot = rr * cols + cc;
                            if (slot < pageList.size() && pageList.get(slot) != null) {
                                hasApp = true;
                                break;
                            }
                        }
                    }
                }
                if (!hasApp) {
                    return new int[]{c, r};
                }
            }
        }
        return new int[]{-1, -1};
    }

    private int[] findBestOverlaySlot(int pageIdx, int desiredCol, int desiredRow, int spanCols, int spanRows, int ignoreWidgetId, boolean ignoreGlance) {
        int cols = getGridColumns();
        int rows = getGridRows();
        int c0 = Math.max(0, Math.min(cols - spanCols, desiredCol));
        int r0 = Math.max(0, Math.min(rows - spanRows, desiredRow));
        if (!isRectOccupiedByOverlay(pageIdx, c0, r0, spanCols, spanRows, ignoreWidgetId, ignoreGlance)) {
            return new int[]{c0, r0};
        }
        int bestC = c0;
        int bestR = r0;
        int bestDistSq = Integer.MAX_VALUE;
        for (int r = 0; r <= rows - spanRows; r++) {
            for (int c = 0; c <= cols - spanCols; c++) {
                if (!isRectOccupiedByOverlay(pageIdx, c, r, spanCols, spanRows, ignoreWidgetId, ignoreGlance)) {
                    int distSq = (c - c0) * (c - c0) + (r - r0) * (r - r0);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        bestC = c;
                        bestR = r;
                    }
                }
            }
        }
        return new int[]{bestC, bestR};
    }

    private void pushAppsOutOfOverlayRect(int pageIdx, int col, int row, int spanCols, int spanRows) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return;
        List<LauncherItem> pageList = pages.get(pageIdx);
        int cols = getGridColumns();
        List<LauncherItem> displaced = new ArrayList<>();
        for (int r = row; r < row + spanRows; r++) {
            for (int c = col; c < col + spanCols; c++) {
                int slot = r * cols + c;
                if (slot >= 0 && slot < pageList.size() && pageList.get(slot) != null) {
                    displaced.add(pageList.get(slot));
                    pageList.set(slot, null);
                }
            }
        }
        for (LauncherItem item : displaced) {
            int freeSlot = -1;
            for (int s = 0; s < pageList.size(); s++) {
                if (pageList.get(s) == null) {
                    int cc = s % cols;
                    int rr = s / cols;
                    boolean inNewRect = (cc >= col && cc < col + spanCols && rr >= row && rr < row + spanRows);
                    if (!inNewRect && !isCellOccupiedByOverlay(pageIdx, cc, rr, -1, false)) {
                        freeSlot = s;
                        break;
                    }
                }
            }
            if (freeSlot != -1) {
                pageList.set(freeSlot, item);
            } else {
                int np = addNewPage();
                pages.get(np).set(0, item);
            }
        }
        if (!displaced.isEmpty()) {
            savePages();
        }
    }

    private void renderWidgetsForPage(final int pageIdx) {
        if (pageIdx < 0 || pageIdx >= pageGridOverlays.size() || pageIdx >= pageGridViews.size()) return;
        FrameLayout overlay = pageGridOverlays.get(pageIdx);
        GridView gv = pageGridViews.get(pageIdx);
        if (overlay == null || gv == null) return;

        View ghost = (pageIdx < pageGhostPreviews.size()) ? pageGhostPreviews.get(pageIdx) : null;
        overlay.removeAllViews();
        List<View> wrappers = new ArrayList<>();
        pageWidgetWrappers.put(pageIdx, wrappers);

        if (gv.getWidth() <= 0 || gv.getHeight() <= 0) {
            if (ghost != null) overlay.addView(ghost);
            return;
        }

        float density = getResources().getDisplayMetrics().density;

        // Android AppWidgets rendered at their exact (col, row, spanCols, spanRows) grid cells
        List<PlacedWidget> widgets = getPlacedWidgetsForPage(pageIdx);
        for (final PlacedWidget pw : widgets) {
            try {
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(pw.id);
                if (info == null) continue;

                AppWidgetHostView hostView = appWidgetHost.createView(this, pw.id, info);
                hostView.setAppWidget(pw.id, info);

                int[] bounds = computeOverlayCellBounds(gv, pw.col, pw.row, pw.spanCols, pw.spanRows);
                int wPx = bounds[2];
                int hPx = bounds[3];
                int wDp = Math.max(40, Math.round(wPx / density));
                int hDp = Math.max(40, Math.round(hPx / density));

                Bundle options = new Bundle();
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp);
                options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp);
                hostView.updateAppWidgetSize(options, wDp, hDp, wDp, hDp);

                final FrameLayout wrapper = new FrameLayout(this);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wPx, hPx);
                lp.leftMargin = bounds[0];
                lp.topMargin = bounds[1];
                wrapper.setLayoutParams(lp);

                FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                hostLp.gravity = Gravity.CENTER;
                hostView.setLayoutParams(hostLp);
                wrapper.addView(hostView);

                final String label = (info.label != null && !info.label.isEmpty()) ? info.label : "Widget";
                wrapper.setTag(new WidgetDragTag(pw, label));
                wrappers.add(wrapper);

                final View.OnLongClickListener widgetDragListener = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        handleDeferredViewLongClick(new Runnable() {
                            @Override
                            public void run() {
                                startDragWidget(pageIdx, pw.id, label, pw.spanCols, pw.spanRows, wrapper);
                            }
                        }, widgetLongPressRunnable != null, LONG_PRESS_WIDGET_HOLD_MS);
                        return true;
                    }
                };

                // Subtle sleek drag handle at the top-right of the widget
                final ImageView ivDragHandle = new ImageView(this);
                FrameLayout.LayoutParams lpDrag = new FrameLayout.LayoutParams((int) (26 * density), (int) (26 * density));
                lpDrag.gravity = Gravity.TOP | Gravity.END;
                lpDrag.topMargin = (int) (4 * density);
                lpDrag.rightMargin = (int) (4 * density);
                ivDragHandle.setLayoutParams(lpDrag);
                ivDragHandle.setImageResource(R.drawable.ic_move);
                ivDragHandle.setBackgroundResource(R.drawable.page_chip_inactive_bg);
                ivDragHandle.setPadding((int) (5 * density), (int) (5 * density), (int) (5 * density), (int) (5 * density));
                ivDragHandle.setElevation(14f);
                ivDragHandle.setContentDescription("Drag or move widget");

                ivDragHandle.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            lastTouchRawX = event.getRawX();
                            lastTouchRawY = event.getRawY();
                        }
                        return false;
                    }
                });

                ivDragHandle.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showWidgetRemoveDialog(pageIdx, pw.id, label);
                    }
                });

                ivDragHandle.setOnLongClickListener(widgetDragListener);
                hostView.setOnLongClickListener(widgetDragListener);
                wrapper.setOnLongClickListener(widgetDragListener);

                wrapper.addView(ivDragHandle);
                overlay.addView(wrapper);
            } catch (Exception ignored) {}
        }

        if (ghost != null) {
            overlay.addView(ghost);
        }
    }

    private void renderAllSavedWidgets() {
        for (int i = 0; i < pageGridOverlays.size(); i++) {
            renderWidgetsForPage(i);
        }
    }

    private void showWidgetRemoveDialog(final int pageIdx, final int widgetId, String widgetLabel) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle(widgetLabel);
        b.setMessage("Remove this widget from Page " + (pageIdx + 1) + "?");
        b.setPositiveButton("Remove", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                removeWidgetFromPage(pageIdx, widgetId);
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void removeWidgetFromPage(int pageIdx, int widgetId) {
        try {
            appWidgetHost.deleteAppWidgetId(widgetId);
        } catch (Exception ignored) {}
        deleteWidgetById(widgetId);
        renderAllSavedWidgets();
        pruneEmptyTrailingPages();
        savePages();
        refreshAllPages();
        Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show();
    }

    private void enterPageWindowMode() {
        isWindowModeActive = true;
        float density = getResources().getDisplayMetrics().density;
        windowScaleX = 0.94f;
        windowScaleY = 0.94f;

        if (dragBlurOverlay != null) {
            dragBlurOverlay.setVisibility(View.GONE);
        }

        if (hsvDragPageChips != null) {
            hsvDragPageChips.setVisibility(View.GONE);
        }

        for (TextView badge : pageCardBadges) {
            badge.setVisibility(View.GONE);
        }

        applyBackgroundBlur(false);

        for (int i = 0; i < pageContainers.size(); i++) {
            FrameLayout container = pageContainers.get(i);
            boolean isActive = (i == currentPage);
            container.setBackgroundResource(isActive ? R.drawable.drag_card_active_bg : R.drawable.drag_card_inactive_bg);
            float targetScale = isActive ? 0.94f : 0.88f;
            float targetAlpha = isActive ? 1.0f : 0.80f;
            container.setElevation((isActive ? 10f : 2f) * density);
            container.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
        }
    }

    private void updateActivePageWindow(int activeIndex) {
        if (!isWindowModeActive) return;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < pageContainers.size(); i++) {
            FrameLayout container = pageContainers.get(i);
            boolean isActive = (i == activeIndex);
            container.setBackgroundResource(isActive ? R.drawable.drag_card_active_bg : R.drawable.drag_card_inactive_bg);
            float targetScale = isActive ? 0.94f : 0.88f;
            float targetAlpha = isActive ? 1.0f : 0.80f;
            container.setElevation((isActive ? 10f : 2f) * density);
            container.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(targetAlpha)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    private void exitPageWindowMode() {
        if (!isWindowModeActive) return;
        isWindowModeActive = false;
        windowScaleX = 1.0f;
        windowScaleY = 1.0f;
        applyBackgroundBlur(false);

        if (dragBlurOverlay != null) {
            dragBlurOverlay.setVisibility(View.GONE);
        }

        if (hsvDragPageChips != null) {
            hsvDragPageChips.setVisibility(View.GONE);
        }

        for (final TextView badge : pageCardBadges) {
            badge.setVisibility(View.GONE);
        }

        for (int i = 0; i < pageContainers.size(); i++) {
            final FrameLayout container = pageContainers.get(i);
            container.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            container.setBackground(null);
                            container.setElevation(0f);
                        }
                    })
                    .start();
        }
    }

    private void applyBackgroundBlur(boolean enable) {
        // Disabled: Pixel Launcher keeps workspace unblurred during icon drag
        if (homeScreen != null && Build.VERSION.SDK_INT >= 31) {
            try {
                Method setRenderEffectMethod = View.class.getMethod("setRenderEffect", Class.forName("android.graphics.RenderEffect"));
                setRenderEffectMethod.invoke(homeScreen, (Object) null);
            } catch (Throwable ignored) {}
        }
    }

    private int getLocalX(View v, float rawX, float scaleX) {
        if (v == null) return (int) rawX;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float centerX = loc[0] + (v.getWidth() * scaleX) / 2f;
        return (int) ((rawX - centerX) / scaleX + v.getWidth() / 2f);
    }

    private int getLocalY(View v, float rawY, float scaleY) {
        if (v == null) return (int) rawY;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float centerY = loc[1] + (v.getHeight() * scaleY) / 2f;
        return (int) ((rawY - centerY) / scaleY + v.getHeight() / 2f);
    }

    private void setupGroupedWidgets(View v) {
        if (v == null) return;
        View widgetWeather = v.findViewById(R.id.widget_weather);
        View btnGeminiAsk = v.findViewById(R.id.btn_gemini_ask);
        View btnGeminiVideo = v.findViewById(R.id.btn_gemini_video);
        View btnGeminiLive = v.findViewById(R.id.btn_gemini_live);
        View widgetBattery = v.findViewById(R.id.widget_battery);

        tvBatteryDeviceName = v.findViewById(R.id.tv_battery_device_name);
        tvBatteryStatus = v.findViewById(R.id.tv_battery_status);
        tvBatteryPct = v.findViewById(R.id.tv_battery_pct);
        ivBatteryBolt = v.findViewById(R.id.iv_battery_bolt);

        if (tvBatteryDeviceName != null) {
            tvBatteryDeviceName.setText(Build.MODEL);
        }

        if (widgetWeather != null) {
            widgetWeather.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchWeather();
                }
            });
        }

        if (btnGeminiAsk != null) {
            btnGeminiAsk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchGemini();
                }
            });
        }

        if (btnGeminiVideo != null) {
            btnGeminiVideo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchGeminiVideo();
                }
            });
        }

        if (btnGeminiLive != null) {
            btnGeminiLive.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchGeminiLive();
                }
            });
        }

        if (widgetBattery != null) {
            widgetBattery.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchBatterySettings();
                }
            });
        }

        registerBatteryMonitor();
    }

    private void registerBatteryMonitor() {
        if (!isBatteryReceiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(batteryReceiver, filter);
                if (batteryStatus != null) {
                    batteryReceiver.onReceive(this, batteryStatus);
                }
                isBatteryReceiverRegistered = true;
            } catch (Exception ignored) {}
        }
    }

    private void launchWeather() {
        LiveWeatherController.openPixelWeather(this);
    }

    private void launchGemini() {
        try {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = getPackageManager().getLaunchIntentForPackage("com.google.android.apps.bard");
                if (fallback != null) startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void launchGeminiVideo() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void launchGeminiLive() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VOICE_COMMAND);
                fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void launchBatterySettings() {
        try {
            Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
                fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    private void setupSearchPill() {
        setupFallbackSearchPill();
        setupOriginalGoogleSearchWidget();
    }

    private void setupFallbackSearchPill() {
        View searchBar = findViewById(R.id.pixel_search_bar);
        if (searchBar != null) {
            searchBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGoogleWebSearch();
                }
            });
            searchBar.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(final View v) {
                    handleDeferredViewLongClick(new Runnable() {
                        @Override
                        public void run() {
                            startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", v);
                        }
                    }, searchBarLongPressRunnable != null, LONG_PRESS_SPECIAL_HOLD_MS);
                    return true;
                }
            });
        }

        View searchContainer = findViewById(R.id.search_bar_container);
        if (searchContainer != null) {
            searchContainer.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(final View v) {
                    handleDeferredViewLongClick(new Runnable() {
                        @Override
                        public void run() {
                            startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", v);
                        }
                    }, searchBarLongPressRunnable != null, LONG_PRESS_SPECIAL_HOLD_MS);
                    return true;
                }
            });
        }

        View btnGoogle = findViewById(R.id.btn_google_g);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGoogleApp();
                }
            });
        }

        View btnAiMode = findViewById(R.id.btn_ai_mode);
        if (btnAiMode != null) {
            btnAiMode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGoogleWebSearch();
                }
            });
        }

        View btnMic = findViewById(R.id.btn_voice_search);
        if (btnMic != null) {
            btnMic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGoogleVoiceSearch();
                }
            });
        }

        View btnLens = findViewById(R.id.btn_lens);
        if (btnLens != null) {
            btnLens.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openGoogleLens();
                }
            });
        }
    }

    private ComponentName findGoogleSearchWidgetProvider() {
        if (appWidgetManager == null) return null;
        String[] candidates = new String[]{
                "com.google.android.apps.gsa.staticplugins.searchwidget.GoogleSearchWidgetProvider",
                "com.google.android.googlequicksearchbox.SearchWidgetProvider",
                "com.google.android.apps.gsa.staticplugins.searchwidget.PremiumSearchWidgetProvider"
        };
        try {
            List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
            for (String cls : candidates) {
                ComponentName cn = new ComponentName("com.google.android.googlequicksearchbox", cls);
                for (AppWidgetProviderInfo info : providers) {
                    if (info.provider != null && info.provider.equals(cn)) {
                        return cn;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private AppWidgetHostView obtainGoogleSearchWidgetHostView(
            String prefKeyWidgetId,
            int bindRequestCode,
            boolean promptPermissionIfNeeded) {
        if (appWidgetManager == null || appWidgetHost == null) return null;
        ComponentName selectedProvider = findGoogleSearchWidgetProvider();
        if (selectedProvider == null) {
            Log.d("PixelLauncher", "Google Search Widget provider not found, keeping fallback");
            return null;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int widgetId = prefs.getInt(prefKeyWidgetId, AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetProviderInfo widgetInfo = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                ? appWidgetManager.getAppWidgetInfo(widgetId)
                : null;

        if (widgetInfo == null) {
            try {
                widgetId = appWidgetHost.allocateAppWidgetId();
                boolean bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, selectedProvider);
                if (!bound) {
                    if (promptPermissionIfNeeded) {
                        Intent bindIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                        bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                        bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, selectedProvider);
                        startActivityForResult(bindIntent, bindRequestCode);
                    } else {
                        try { appWidgetHost.deleteAppWidgetId(widgetId); } catch (Exception ignored) {}
                    }
                    return null;
                }
                prefs.edit().putInt(prefKeyWidgetId, widgetId).apply();
                widgetInfo = appWidgetManager.getAppWidgetInfo(widgetId);
            } catch (Exception e) {
                Log.e("PixelLauncher", "Error allocating/binding widget: " + e.getMessage());
                return null;
            }
        }

        if (widgetInfo == null) return null;
        try {
            AppWidgetHostView hostView = appWidgetHost.createView(this, widgetId, widgetInfo);
            hostView.setAppWidget(widgetId, widgetInfo);
            hostView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL
            ));
            hostView.setClipToPadding(false);
            return hostView;
        } catch (Exception e) {
            Log.e("PixelLauncher", "Error creating hostView: " + e.getMessage());
            return null;
        }
    }

    private void updateGoogleWidgetSizing(AppWidgetHostView hostView, float sideInsetDp, int minHeightDp, int maxHeightDp) {
        if (hostView == null) return;
        float density = getResources().getDisplayMetrics().density;
        int wDp = Math.max(200, Math.round((getScreenWidth() / density) - sideInsetDp));
        Bundle opts = new Bundle();
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp);
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp);
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeightDp);
        opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeightDp);
        hostView.updateAppWidgetSize(opts, wDp, minHeightDp, wDp, maxHeightDp);
    }

    private boolean isDrawerGoogleWidgetEnabled() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_KEY_DRAWER_USE_GOOGLE_WIDGET, false);
    }

    private void setDrawerGoogleWidgetEnabled(boolean enabled) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_DRAWER_USE_GOOGLE_WIDGET, enabled)
                .apply();
        syncDrawerSearchCapsuleMode(enabled);
    }

    private View getActiveDrawerSearchView() {
        if (drawerGoogleWidgetContainer != null
                && drawerGoogleWidgetContainer.getVisibility() == View.VISIBLE) {
            return drawerGoogleWidgetContainer;
        }
        return drawerSearchBar;
    }

    private void syncDrawerSearchCapsuleMode(boolean promptPermissionIfNeeded) {
        boolean useOriginalWidget = isDrawerGoogleWidgetEnabled();
        if (!useOriginalWidget || drawerGoogleWidgetContainer == null) {
            if (drawerGoogleWidgetContainer != null) {
                drawerGoogleWidgetContainer.setVisibility(View.GONE);
            }
            if (drawerSearchBar != null) {
                drawerSearchBar.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (drawerSearchWidgetHostView == null) {
            drawerSearchWidgetHostView = obtainGoogleSearchWidgetHostView(
                    PREF_DRAWER_GOOGLE_WIDGET_ID,
                    REQUEST_BIND_DRAWER_APPWIDGET,
                    promptPermissionIfNeeded
            );
        }
        if (drawerSearchWidgetHostView == null) {
            drawerGoogleWidgetContainer.setVisibility(View.GONE);
            if (drawerSearchBar != null) drawerSearchBar.setVisibility(View.VISIBLE);
            return;
        }

        if (drawerSearchWidgetHostView.getParent() instanceof ViewGroup
                && drawerSearchWidgetHostView.getParent() != drawerGoogleWidgetContainer) {
            ((ViewGroup) drawerSearchWidgetHostView.getParent()).removeView(drawerSearchWidgetHostView);
        }
        if (drawerSearchWidgetHostView.getParent() == null) {
            drawerGoogleWidgetContainer.addView(drawerSearchWidgetHostView);
        }
        updateGoogleWidgetSizing(drawerSearchWidgetHostView, 32f, 54, 62);
        hideKeyboard(etDrawerSearch);
        if (etDrawerSearch != null && etDrawerSearch.length() > 0) {
            etDrawerSearch.setText("");
        }
        if (drawerSearchBar != null) {
            drawerSearchBar.setVisibility(View.GONE);
        }
        drawerGoogleWidgetContainer.setVisibility(View.VISIBLE);
        if (drawerController == null || !drawerController.isVisibleOrAnimating()) {
            drawerGoogleWidgetContainer.setTranslationY(0f);
            drawerGoogleWidgetContainer.setScaleX(1f);
            drawerGoogleWidgetContainer.setScaleY(1f);
        }
    }

    private void setupOriginalGoogleSearchWidget() {
        updateSearchBarVisibility();
        final FrameLayout container = findViewById(R.id.search_bar_container);
        if (container == null) return;

        AppWidgetHostView newHostView = obtainGoogleSearchWidgetHostView(
                PREF_GOOGLE_WIDGET_ID,
                REQUEST_BIND_APPWIDGET,
                true
        );
        if (newHostView == null) return;

        if (searchWidgetHostView != null && searchWidgetHostView.getParent() instanceof ViewGroup) {
            ((ViewGroup) searchWidgetHostView.getParent()).removeView(searchWidgetHostView);
        }
        searchWidgetHostView = newHostView;
        searchWidgetHostView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(final View v) {
                final View activeParent = (v.getParent() instanceof View) ? (View) v.getParent() : container;
                handleDeferredViewLongClick(new Runnable() {
                    @Override
                    public void run() {
                        startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", activeParent);
                    }
                }, searchBarLongPressRunnable != null, LONG_PRESS_SPECIAL_HOLD_MS);
                return true;
            }
        });

        View fallbackPill = findViewById(R.id.pixel_search_bar);
        if (fallbackPill != null) {
            fallbackPill.setVisibility(View.GONE);
        }
        attachOriginalSearchWidgetToActiveContainer();
        Log.d("PixelLauncher", "Original Google search bar widget attached successfully!");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
                if (widgetId != -1) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(PREF_GOOGLE_WIDGET_ID, widgetId).apply();
                }
                setupOriginalGoogleSearchWidget();
            }
        } else if (requestCode == REQUEST_BIND_DRAWER_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
                if (widgetId != -1) {
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(PREF_DRAWER_GOOGLE_WIDGET_ID, widgetId).apply();
                }
                syncDrawerSearchCapsuleMode(false);
            } else if (resultCode == RESULT_CANCELED) {
                setDrawerGoogleWidgetEnabled(false);
            }
        } else if (requestCode == REQUEST_LAUNCH_LENS) {
            // Handled via startActivityForResult so LensExportedActivity receives non-empty callingPackage.
        } else if (requestCode == REQUEST_PICK_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
                if (widgetId != -1) {
                    configureOrAddWidget(widgetId);
                }
            } else if (resultCode == RESULT_CANCELED) {
                if (data != null) {
                    int widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                    if (widgetId != -1) {
                        try {
                            appWidgetHost.deleteAppWidgetId(widgetId);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
                if (widgetId != -1) {
                    completeAddWidget(widgetId);
                }
            } else if (resultCode == RESULT_CANCELED) {
                if (data != null) {
                    int widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                    if (widgetId != -1) {
                        try {
                            appWidgetHost.deleteAppWidgetId(widgetId);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } else if (requestCode == REQUEST_BIND_CUSTOM_APPWIDGET) {
            if (resultCode == RESULT_OK) {
                int widgetId = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
                if (widgetId != -1) {
                    configureOrAddWidget(widgetId);
                }
            } else if (resultCode == RESULT_CANCELED) {
                if (data != null) {
                    int widgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                    if (widgetId != -1) {
                        try {
                            appWidgetHost.deleteAppWidgetId(widgetId);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    private void openGoogleWebSearch() {
        openGoogleWebSearch("");
    }

    private void openGoogleWebSearch(String query) {
        String cleanQuery = query != null ? query.trim() : "";
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.setPackage("com.google.android.googlequicksearchbox");
            intent.putExtra(SearchManager.QUERY, cleanQuery);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                String url = cleanQuery.isEmpty()
                        ? "https://www.google.com"
                        : "https://www.google.com/search?q=" + Uri.encode(cleanQuery);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open Google Search", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGoogleApp() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.google.android.googlequicksearchbox");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launch);
                return;
            }
        } catch (Exception ignored) {}
        try {
            Intent cmp = new Intent(Intent.ACTION_MAIN);
            cmp.addCategory(Intent.CATEGORY_LAUNCHER);
            cmp.setComponent(new ComponentName("com.google.android.googlequicksearchbox",
                    "com.google.android.googlequicksearchbox.SearchActivity"));
            cmp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(cmp);
            return;
        } catch (Exception ignored) {}
        openGoogleWebSearch("");
    }

    private void openGoogleVoiceSearch() {
        String[] actions = new String[]{
                "android.intent.action.VOICE_ASSIST",
                "com.google.android.googlequicksearchbox.action.CLASSIC_GSA_VOICE_SEARCH",
                "android.speech.action.WEB_SEARCH",
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        };
        for (String act : actions) {
            try {
                Intent intent = new Intent(act);
                intent.setPackage("com.google.android.googlequicksearchbox");
                if (RecognizerIntent.ACTION_RECOGNIZE_SPEECH.equals(act)) {
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                }
                if (intent.resolveActivity(getPackageManager()) == null) continue;
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                return;
            } catch (Exception ignored) {}
        }
        try {
            Intent generic = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            generic.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(generic);
        } catch (Exception ex) {
            Toast.makeText(this, "Google Voice Search not available", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean tryStartLensForResult(Intent intent) {
        if (intent == null || intent.resolveActivity(getPackageManager()) == null) return false;
        try {
            // LensExportedActivity rejects launches whose getCallingPackage() is null (FLAG_ACTIVITY_NEW_TASK).
            startActivityForResult(intent, REQUEST_LAUNCH_LENS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openGoogleLens() {
        try {
            Intent deeplink = new Intent(Intent.ACTION_VIEW, Uri.parse("https://search.google/ways-to-search/lens/try"));
            deeplink.setPackage("com.google.android.googlequicksearchbox");
            if (deeplink.resolveActivity(getPackageManager()) != null) {
                deeplink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(deeplink);
                return;
            }
        } catch (Exception ignored) {}

        Intent lensUri = new Intent(Intent.ACTION_VIEW, Uri.parse("google://lens"));
        lensUri.setPackage("com.google.android.googlequicksearchbox");
        if (tryStartLensForResult(lensUri)) return;

        String[] lensActivities = new String[]{
                "com.google.android.apps.search.lens.LensExportedActivity",
                "com.google.android.apps.lens.MainActivity"
        };
        for (String cls : lensActivities) {
            Intent cmp = new Intent();
            cmp.setComponent(new ComponentName("com.google.android.googlequicksearchbox", cls));
            if (tryStartLensForResult(cmp)) return;
        }

        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens");
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, "Google Lens is not installed", Toast.LENGTH_SHORT).show();
    }

    private void setupDock() {
        dockSlots[0] = findViewById(R.id.dock_slot_0);
        dockSlots[1] = findViewById(R.id.dock_slot_1);
        dockSlots[2] = findViewById(R.id.dock_slot_2);
        dockSlots[3] = findViewById(R.id.dock_slot_3);
        dockSlots[4] = findViewById(R.id.dock_slot_4);

        dockIcons[0] = findViewById(R.id.dock_iv_0);
        dockIcons[1] = findViewById(R.id.dock_iv_1);
        dockIcons[2] = findViewById(R.id.dock_iv_2);
        dockIcons[3] = findViewById(R.id.dock_iv_3);
        dockIcons[4] = findViewById(R.id.dock_iv_4);

        for (int i = 0; i < 5; i++) {
            final int slot = i;
            final ImageView iv = dockIcons[i];
            if (iv == null) continue;

            iv.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        lastTouchRawX = event.getRawX();
                        lastTouchRawY = event.getRawY();
                    }
                    if (slot < dockApps.size() && dockApps.get(slot) != null) {
                        UiMotion.handleIconPressMotion(v, event);
                    }
                    return false;
                }
            });

            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    AppInfo app = (slot < dockApps.size()) ? dockApps.get(slot) : null;
                    if (app != null) {
                        launchApp(app, v);
                    } else {
                        Toast.makeText(MainActivity.this, "Drag an app here to place on dock", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(final View v) {
                    boolean drawerOpen = (drawerController != null && drawerController.isVisibleOrAnimating());
                    if (drawerOpen || !canStartGestureDragHold()) {
                        return false;
                    }
                    final AppInfo app = (slot < dockApps.size()) ? dockApps.get(slot) : null;
                    if (app != null) {
                        scheduleDeferredDragHold(new Runnable() {
                            @Override
                            public void run() {
                                if (drawerController == null || !drawerController.isVisibleOrAnimating()) {
                                    startDragDockApp(slot, app, v);
                                }
                            }
                        }, LONG_PRESS_ICON_HOLD_MS);
                        return true;
                    }
                    return false;
                }
            });
        }

        renderDock();
    }

    private void setDockApp(int slot, AppInfo app) {
        while (dockApps.size() <= slot) {
            dockApps.add(null);
        }
        dockApps.set(slot, app);
        saveDockApps();
        renderDock();
    }

    private void renderDock() {
        for (int i = 0; i < 5; i++) {
            ImageView iv = dockIcons[i];
            if (iv == null) continue;

            AppInfo app = (i < dockApps.size()) ? dockApps.get(i) : null;
            if (app != null) {
                Drawable d = app.icon;
                if (d == null) {
                    try {
                        d = getPackageManager().getApplicationIcon(app.packageName);
                    } catch (Exception ignored) {}
                }
                if (d != null) {
                    iv.setImageDrawable(d);
                }
                iv.setBackground(null);
                iv.setAlpha(1.0f);
                iv.setContentDescription(app.label);
            } else {
                iv.setImageDrawable(null);
                iv.setBackgroundResource(R.drawable.page_chip_inactive_bg);
                iv.setAlpha(0.35f);
                iv.setContentDescription("Empty dock slot");
            }
        }
    }

    private void saveDockApps() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < 5; i++) {
                JSONObject obj = new JSONObject();
                if (i < dockApps.size() && dockApps.get(i) != null) {
                    AppInfo a = dockApps.get(i);
                    obj.put("pkg", a.packageName);
                    obj.put("act", a.activityName);
                    obj.put("label", a.label);
                }
                arr.put(obj);
            }
            prefs.edit().putString(KEY_DOCK_JSON, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadDockApps() {
        dockApps.clear();
        for (int i = 0; i < 5; i++) {
            dockApps.add(null);
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_DOCK_JSON, null);
        boolean hasSaved = false;
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length() && i < 5; i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String pkg = obj.optString("pkg", null);
                    String act = obj.optString("act", null);
                    if (pkg != null && !pkg.isEmpty()) {
                        AppInfo a = findApp(pkg, act);
                        if (a == null) {
                            try {
                                PackageManager pm = getPackageManager();
                                Intent launch = pm.getLaunchIntentForPackage(pkg);
                                if (launch != null) {
                                    String label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                                    Drawable icon = pm.getApplicationIcon(pkg);
                                    String actName = launch.getComponent() != null ? launch.getComponent().getClassName() : "";
                                    a = new AppInfo(label, pkg, actName, icon);
                                }
                            } catch (Exception ignored) {}
                        }
                        if (a != null) {
                            dockApps.set(i, a);
                            hasSaved = true;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!hasSaved) {
            AppInfo phone = findAppForIntent(new Intent(Intent.ACTION_DIAL));
            AppInfo msg = findAppForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING));
            if (msg == null) {
                msg = findAppForIntent(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")));
            }
            AppInfo browser = findAppForIntent(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")));
            if (browser == null) {
                browser = findAppByPkg("com.android.chrome");
            }
            AppInfo extra = null;
            String[] preferred = new String[]{
                    "app.revanced.android.youtube",
                    "com.google.android.youtube",
                    "com.google.android.gm",
                    "com.whatsapp",
                    "com.android.settings"
            };
            for (String p : preferred) {
                extra = findAppByPkg(p);
                if (extra != null) break;
            }
            AppInfo camera = findAppForIntent(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));

            dockApps.set(0, phone);
            dockApps.set(1, msg);
            dockApps.set(2, browser);
            dockApps.set(3, extra);
            dockApps.set(4, camera);
            saveDockApps();
        }
    }

    private AppInfo findAppForIntent(Intent intent) {
        try {
            PackageManager pm = getPackageManager();
            ResolveInfo ri = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                String act = ri.activityInfo.name;
                AppInfo matched = findApp(pkg, act);
                if (matched != null) return matched;
                String label = ri.loadLabel(pm).toString();
                Drawable icon = ri.loadIcon(pm);
                return new AppInfo(label, pkg, act, icon);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void showDockOptionsPopup(final View anchor, final int slot, final AppInfo currentApp) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View popupView = inflater.inflate(R.layout.popup_dock_menu, null);

        final PopupWindow popupWindow = new PopupWindow(popupView,
                (int) (236 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        preparePixelPopupWindow(popupWindow, 24f);

        TextView tvTitle = popupView.findViewById(R.id.dock_popup_title);
        if (tvTitle != null) {
            tvTitle.setText(currentApp != null ? currentApp.label : "Empty Slot (" + (slot + 1) + ")");
        }

        // Change App
        popupView.findViewById(R.id.dock_item_change).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                showAppPickerDialog(slot);
            }
        });

        // Drag to Move
        View vDrag = popupView.findViewById(R.id.dock_item_drag);
        if (currentApp != null) {
            vDrag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    startDragItem(anchor, new LauncherItem(currentApp), -1, -1);
                }
            });
        } else {
            vDrag.setVisibility(View.GONE);
        }

        // App Info
        View vInfo = popupView.findViewById(R.id.dock_item_info);
        if (currentApp != null) {
            vInfo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + currentApp.packageName));
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not open app settings", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            vInfo.setVisibility(View.GONE);
        }

        // Remove from Dock
        View vRemove = popupView.findViewById(R.id.dock_item_remove);
        if (currentApp != null) {
            vRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    popupWindow.dismiss();
                    setDockApp(slot, null);
                    Toast.makeText(MainActivity.this, "Removed " + currentApp.label + " from Dock", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            vRemove.setVisibility(View.GONE);
        }

        // Uninstall (only for user-installed / removable apps)
        View vUninstall = popupView.findViewById(R.id.dock_item_uninstall);
        boolean dockCanUninstall = (currentApp != null && isAppUninstallable(currentApp.packageName));
        if (vUninstall != null) {
            if (dockCanUninstall) {
                vUninstall.setVisibility(View.VISIBLE);
                vUninstall.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        popupWindow.dismiss();
                        requestAppUninstall(currentApp);
                    }
                });
            } else {
                vUninstall.setVisibility(View.GONE);
            }
        }

        int[] screenPos = new int[2];
        anchor.getLocationOnScreen(screenPos);
        int density = (int) getResources().getDisplayMetrics().density;
        int popupW = (int) (236 * density);
        int x = screenPos[0] + (anchor.getWidth() / 2) - (popupW / 2);
        x = Math.max(20, Math.min(x, getScreenWidth() - popupW - 20));
        int liftDp = dockCanUninstall ? 255 : 210;
        int y = screenPos[1] - (int) (liftDp * density);
        if (y < 60) {
            y = screenPos[1] - (int) (160 * density);
        }

        popupWindow.showAtLocation(rootLayout, Gravity.NO_GRAVITY, x, y);
        animateAnchoredPopupIn(popupView, anchor, x, y, popupW);
    }

    private void showAppPickerDialog(final int slot) {
        if (appList.isEmpty()) {
            Toast.makeText(this, "Apps are still loading...", Toast.LENGTH_SHORT).show();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_app_picker, null);

        TextView tvTitle = dialogView.findViewById(R.id.picker_title);
        tvTitle.setText("Choose Dock App (" + (slot + 1) + ")");

        EditText etSearch = dialogView.findViewById(R.id.picker_search);
        GridView gv = dialogView.findViewById(R.id.picker_grid);

        final AppAdapter pickerAdapter = new AppAdapter(this, appList, 50, 11.5f);
        gv.setAdapter(pickerAdapter);

        int width = (int) (320 * getResources().getDisplayMetrics().density);
        int height = (int) (460 * getResources().getDisplayMetrics().density);
        final PopupWindow pickerPopup = new PopupWindow(dialogView, width, height, true);
        preparePixelPopupWindow(pickerPopup, 28f);

        gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo selected = pickerAdapter.getItem(position);
                if (selected != null) {
                    pickerPopup.dismiss();
                    setDockApp(slot, selected);
                    Toast.makeText(MainActivity.this, "Set " + selected.label + " to Dock slot " + (slot + 1), Toast.LENGTH_SHORT).show();
                }
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                pickerAdapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        pickerPopup.showAtLocation(rootLayout, Gravity.CENTER, 0, 0);
        UiMotion.animatePopIn(dialogView, 0.5f, 0.5f, 12f);
    }

    private void setupDrawer() {
        appAdapter = new AppAdapter(this, appList);
        gvApps.setAdapter(appAdapter);
        applyRefreshAdaptedScrollPhysics();
        if (drawerScrollbar != null && gvApps != null) {
            drawerScrollbar.attachToGridView(gvApps, appAdapter);
            drawerScrollbar.setOnExternalScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {}

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    syncDrawerHeaderScroll();
                }
            });
        }

        if (btnDrawerMic != null) {
            btnDrawerMic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    hideKeyboard(etDrawerSearch);
                    openGoogleVoiceSearch();
                }
            });
        }
        if (btnDrawerLens != null) {
            btnDrawerLens.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    hideKeyboard(etDrawerSearch);
                    openGoogleLens();
                }
            });
        }
        if (drawerSearchBar != null) {
            drawerSearchBar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    focusDrawerSearchInput();
                }
            });
        }
        if (ivDrawerSearchIcon != null) {
            ivDrawerSearchIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    hideKeyboard(etDrawerSearch);
                    openGoogleApp();
                }
            });
        }
        syncDrawerSearchCapsuleMode(false);

        gvApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isGestureTapEligible()) return;
                AppInfo app = appAdapter.getItem(position);
                if (app != null) {
                    View iconView = view != null ? view.findViewById(R.id.iv_app_icon) : null;
                    launchApp(app, iconView != null ? iconView : view);
                }
            }
        });

        gvApps.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, final View view, int position, long id) {
                if (!canStartGestureDragHold()) return false;
                final AppInfo app = appAdapter.getItem(position);
                if (app == null) return false;
                scheduleDeferredDragHold(new Runnable() {
                    @Override
                    public void run() {
                        if (drawerController != null && drawerController.isVisibleOrAnimating() && !drawerController.isDragging()) {
                            startDragFromDrawer(view, app);
                        }
                    }
                }, LONG_PRESS_DRAWER_HOLD_MS);
                return true;
            }
        });

        etDrawerSearch.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                boolean expanded = hasFocus || (etDrawerSearch != null && etDrawerSearch.length() > 0);
                animateDrawerSearchBarExpand(expanded);
            }
        });

        etDrawerSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handleDrawerSearchQueryChanged(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etDrawerSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_DONE) {
                    return submitDrawerSearch();
                }
                return false;
            }
        });

        etDrawerSearch.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                    return submitDrawerSearch();
                }
                return false;
            }
        });

        if (btnCloseDrawer != null) {
            btnCloseDrawer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (etDrawerSearch != null && etDrawerSearch.length() > 0) {
                        etDrawerSearch.setText("");
                        focusDrawerSearchInput();
                    } else {
                        closeAppDrawer();
                    }
                }
            });
        }
    }

    private void focusDrawerSearchInput() {
        if (etDrawerSearch == null) return;
        etDrawerSearch.requestFocus();
        if (etDrawerSearch.getText() != null) {
            etDrawerSearch.setSelection(etDrawerSearch.getText().length());
        }
        showKeyboard(etDrawerSearch);
    }

    private void animateDrawerSearchBarExpand(boolean expanded) {
        if (drawerSearchBar == null) return;
        ViewGroup.LayoutParams rawLp = drawerSearchBar.getLayoutParams();
        if (!(rawLp instanceof ViewGroup.MarginLayoutParams)) return;
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) rawLp;
        float density = getResources().getDisplayMetrics().density;
        final int targetMargin = Math.round((expanded ? 10f : 22f) * density);
        final int startMargin = lp.leftMargin;
        if (drawerSearchMarginAnimator != null) {
            drawerSearchMarginAnimator.cancel();
            drawerSearchMarginAnimator = null;
        }
        if (startMargin == targetMargin) return;
        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        drawerSearchMarginAnimator = ValueAnimator.ofInt(startMargin, targetMargin);
        drawerSearchMarginAnimator.setDuration(DisplayRefreshHelper.scaleDurationMs(160L));
        drawerSearchMarginAnimator.setInterpolator(new DecelerateInterpolator(1.8f));
        drawerSearchMarginAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (drawerSearchBar == null) return;
                if (!pacer.shouldRender(animation)) return;
                int m = (Integer) animation.getAnimatedValue();
                lp.leftMargin = m;
                lp.rightMargin = m;
                lp.setMarginStart(m);
                lp.setMarginEnd(m);
                drawerSearchBar.setLayoutParams(lp);
            }
        });
        drawerSearchMarginAnimator.start();
    }

    private boolean submitDrawerSearch() {
        String q = etDrawerSearch != null ? etDrawerSearch.getText().toString().trim() : "";
        if (q.isEmpty()) return false;
        hideKeyboard(etDrawerSearch);
        List<AppInfo> matches = AppAdapter.rankMatches(appList, q);
        if (!matches.isEmpty()) {
            launchApp(matches.get(0), null);
            return true;
        }
        openGoogleWebSearch(q);
        return true;
    }

    private void handleDrawerSearchQueryChanged(CharSequence s) {
        final int seq = ++drawerSearchSeq;
        if (pendingWebSuggestRunnable != null) {
            mainHandler.removeCallbacks(pendingWebSuggestRunnable);
            pendingWebSuggestRunnable = null;
        }

        String raw = s != null ? s.toString() : "";
        final String trimmed = raw.trim();
        boolean hasQuery = !trimmed.isEmpty();
        lastDrawerSearchQuery = trimmed;

        if (btnDrawerMic != null) {
            btnDrawerMic.setVisibility(hasQuery ? View.GONE : View.VISIBLE);
        }
        if (btnDrawerLens != null) {
            btnDrawerLens.setVisibility(hasQuery ? View.GONE : View.VISIBLE);
        }
        if (btnCloseDrawer != null) {
            btnCloseDrawer.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
            btnCloseDrawer.setImageResource(R.drawable.ic_close);
            btnCloseDrawer.setContentDescription("Clear search");
        }

        boolean focusedOrHasText = hasQuery || (etDrawerSearch != null && etDrawerSearch.hasFocus());
        animateDrawerSearchBarExpand(focusedOrHasText);

        if (!hasQuery) {
            lastDrawerSuggestions.clear();
            if (tvDrawerTypeaheadHint != null) {
                tvDrawerTypeaheadHint.setText("");
                tvDrawerTypeaheadHint.setVisibility(View.GONE);
            }
            if (svDrawerSearchResults != null) {
                svDrawerSearchResults.setVisibility(View.GONE);
            }
            if (gvApps != null) {
                gvApps.setVisibility(View.VISIBLE);
            }
            if (drawerScrollbar != null) {
                drawerScrollbar.setVisibility(View.VISIBLE);
            }
            if (appAdapter != null) {
                appAdapter.filterSync("");
            }
            if (drawerScrollbar != null) {
                drawerScrollbar.notifyListChanged();
            }
            updateDrawerGridPadding();
            syncDrawerHeaderScroll();
            return;
        }

        if (gvApps != null) gvApps.setVisibility(View.GONE);
        if (llDrawerHeaderOverlay != null) llDrawerHeaderOverlay.setVisibility(View.GONE);
        if (drawerScrollbar != null) drawerScrollbar.setVisibility(View.GONE);
        if (svDrawerSearchResults != null) {
            svDrawerSearchResults.setVisibility(View.VISIBLE);
            svDrawerSearchResults.scrollTo(0, 0);
        }

        final List<AppInfo> matches = AppAdapter.rankMatches(appList, trimmed);
        if (appAdapter != null) {
            appAdapter.filterSync(trimmed);
        }
        updateDrawerTypeaheadHint(raw, matches);
        renderDrawerSearchResults(trimmed, matches, Collections.<String>emptyList());
        scheduleLiveWebSuggestions(trimmed, seq, matches);
    }

    private void updateDrawerTypeaheadHint(String rawInput, List<AppInfo> matches) {
        if (tvDrawerTypeaheadHint == null) return;
        if (rawInput == null || rawInput.isEmpty() || matches == null || matches.isEmpty()) {
            tvDrawerTypeaheadHint.setText("");
            tvDrawerTypeaheadHint.setVisibility(View.GONE);
            return;
        }
        String label = matches.get(0).label;
        if (label == null || label.length() <= rawInput.length()) {
            tvDrawerTypeaheadHint.setText("");
            tvDrawerTypeaheadHint.setVisibility(View.GONE);
            return;
        }
        if (!label.toLowerCase(Locale.ROOT).startsWith(rawInput.toLowerCase(Locale.ROOT))) {
            tvDrawerTypeaheadHint.setText("");
            tvDrawerTypeaheadHint.setVisibility(View.GONE);
            return;
        }

        boolean isNight = LauncherTheme.isNightMode(this);
        String completion = label.substring(rawInput.length());
        if (Character.isLowerCase(rawInput.charAt(0))) {
            completion = completion.toLowerCase(Locale.getDefault());
        }
        String full = rawInput + completion + " - open";
        SpannableString span = new SpannableString(full);
        int prefixLen = rawInput.length();
        int completionEnd = prefixLen + completion.length();
        span.setSpan(new ForegroundColorSpan(Color.TRANSPARENT), 0, prefixLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int hintColor = LauncherTheme.textSecondary(isNight);
        span.setSpan(new ForegroundColorSpan(hintColor), prefixLen, full.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int highlightBg = isNight ? 0x28FFFFFF : 0x1A000000;
        span.setSpan(new BackgroundColorSpan(highlightBg), prefixLen, completionEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvDrawerTypeaheadHint.setText(span);
        tvDrawerTypeaheadHint.setVisibility(View.VISIBLE);
    }

    private void scheduleLiveWebSuggestions(final String query, final int seq, final List<AppInfo> matches) {
        pendingWebSuggestRunnable = new Runnable() {
            @Override
            public void run() {
                appLoaderExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != drawerSearchSeq) return;
                        final List<String> fetched = fetchGoogleSuggestSync(query);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (seq != drawerSearchSeq) return;
                                if (svDrawerSearchResults == null || svDrawerSearchResults.getVisibility() != View.VISIBLE) return;
                                lastDrawerSuggestions.clear();
                                lastDrawerSuggestions.addAll(fetched);
                                renderDrawerSearchResults(query, matches, fetched);
                            }
                        });
                    }
                });
            }
        };
        mainHandler.postDelayed(pendingWebSuggestRunnable, 110L);
    }

    private List<String> fetchGoogleSuggestSync(String query) {
        List<String> out = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" + Uri.encode(query));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                JSONArray root = new JSONArray(sb.toString());
                JSONArray arr = root.optJSONArray(1);
                if (arr != null) {
                    for (int i = 0; i < arr.length() && out.size() < 4; i++) {
                        String s = arr.optString(i, "").trim();
                        if (s.isEmpty()) continue;
                        boolean dup = false;
                        for (String existing : out) {
                            if (existing.equalsIgnoreCase(s)) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) out.add(s);
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    private void renderDrawerSearchResults(final String query, List<AppInfo> matches, List<String> suggestions) {
        if (llDrawerSearchResultsContent == null) return;
        llDrawerSearchResultsContent.removeAllViews();
        if (query == null || query.isEmpty()) return;

        boolean isNight = LauncherTheme.isNightMode(this);
        float density = getResources().getDisplayMetrics().density;
        int primaryText = LauncherTheme.textPrimary(isNight);
        int secondaryIcon = LauncherTheme.iconSecondary(isNight);

        if (matches != null && !matches.isEmpty()) {
            int maxApps = Math.min(8, matches.size());
            for (int start = 0; start < maxApps; start += 4) {
                int end = Math.min(start + 4, maxApps);
                llDrawerSearchResultsContent.addView(
                        createSearchMatchedAppsRow(matches, start, end, start == 0, isNight, density, primaryText));
            }
            View appSpacer = new View(this);
            appSpacer.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.round(10f * density)));
            llDrawerSearchResultsContent.addView(appSpacer);
        }

        llDrawerSearchResultsContent.addView(
                createSearchSectionHeader("Web search", density, primaryText, secondaryIcon, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        hideKeyboard(etDrawerSearch);
                        openGoogleWebSearch(query);
                    }
                }));

        List<String> rows = new ArrayList<>();
        if (suggestions != null && !suggestions.isEmpty()) {
            rows.addAll(suggestions);
        } else {
            rows.add(query);
        }
        int totalRows = rows.size();
        for (int i = 0; i < totalRows; i++) {
            llDrawerSearchResultsContent.addView(
                    createSearchSuggestionRow(rows.get(i), i, totalRows, isNight, density, primaryText, secondaryIcon));
        }

        View storeSpacer = new View(this);
        storeSpacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(12f * density)));
        llDrawerSearchResultsContent.addView(storeSpacer);

        llDrawerSearchResultsContent.addView(
                createSearchSectionHeader("Play Store", density, primaryText, secondaryIcon, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openPlayStoreSearch(query);
                    }
                }));
        llDrawerSearchResultsContent.addView(
                createPlayStoreSearchRow(query, isNight, density, primaryText, secondaryIcon));
    }

    private LinearLayout createSearchMatchedAppsRow(
            List<AppInfo> matches,
            int start,
            int end,
            boolean highlightFirst,
            boolean isNight,
            float density,
            int primaryText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(4f);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(0, Math.round(4f * density), 0, Math.round(4f * density));

        LayoutInflater inflater = LayoutInflater.from(this);
        int iconPx = Math.round(54f * density);
        int padH = Math.round(4f * density);
        int padV = Math.round(10f * density);

        for (int i = 0; i < 4; i++) {
            int idx = start + i;
            if (idx >= end) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                row.addView(spacer);
                continue;
            }
            final AppInfo app = matches.get(idx);
            final View slotView = inflater.inflate(R.layout.item_app_grid, row, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            slotView.setLayoutParams(lp);
            slotView.setPadding(padH, padV, padH, padV);
            if (highlightFirst && i == 0) {
                int hlFill = isNight ? 0x26FFFFFF : 0x1E000000;
                slotView.setBackground(LauncherTheme.makeRoundedRectDrawable(
                        hlFill, Color.TRANSPARENT, 20f * density, 0));
            }
            final ImageView iv = slotView.findViewById(R.id.iv_app_icon);
            TextView tv = slotView.findViewById(R.id.tv_app_label);
            if (iv != null) {
                ViewGroup.LayoutParams ilp = iv.getLayoutParams();
                if (ilp != null) {
                    ilp.width = iconPx;
                    ilp.height = iconPx;
                    iv.setLayoutParams(ilp);
                }
                if (app.icon != null) iv.setImageDrawable(app.icon);
                UiMotion.attachIconTouchSpring(slotView, iv);
            }
            if (tv != null) {
                tv.setText(app.label);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
                tv.setTextColor(primaryText);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
            }
            slotView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    hideKeyboard(etDrawerSearch);
                    launchApp(app, iv != null ? iv : slotView);
                }
            });
            slotView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (!canStartGestureDragHold()) return false;
                    scheduleDeferredDragHold(new Runnable() {
                        @Override
                        public void run() {
                            if (drawerController != null && drawerController.isVisibleOrAnimating() && !drawerController.isDragging()) {
                                hideKeyboard(etDrawerSearch);
                                startDragFromDrawer(slotView, app);
                            }
                        }
                    }, LONG_PRESS_DRAWER_HOLD_MS);
                    return true;
                }
            });
            row.addView(slotView);
        }
        return row;
    }

    private LinearLayout createSearchSectionHeader(
            String title,
            float density,
            int primaryText,
            int secondaryIcon,
            View.OnClickListener listener) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(42f * density)));
        header.setPadding(Math.round(8f * density), 0, Math.round(8f * density), 0);
        header.setBackgroundResource(R.drawable.popup_item_bg);
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnClickListener(listener);

        TextView tv = new TextView(this);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tlp);
        tv.setText(title);
        tv.setTextColor(primaryText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(tv);

        ImageView chevron = new ImageView(this);
        int cSize = Math.round(18f * density);
        chevron.setLayoutParams(new LinearLayout.LayoutParams(cSize, cSize));
        chevron.setImageResource(R.drawable.ic_arrow_up);
        chevron.setRotation(90f);
        chevron.setColorFilter(secondaryIcon);
        header.addView(chevron);
        return header;
    }

    private LinearLayout createSearchSuggestionRow(
            final String suggestionText,
            int index,
            int totalCount,
            boolean isNight,
            float density,
            int primaryText,
            int secondaryIcon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(54f * density));
        if (index < totalCount - 1) {
            rowLp.bottomMargin = Math.round(2f * density);
        }
        row.setLayoutParams(rowLp);
        row.setPadding(Math.round(14f * density), 0, Math.round(12f * density), 0);

        float topR = (index == 0) ? 20f * density : 6f * density;
        float botR = (index == totalCount - 1) ? 20f * density : 6f * density;
        int cardFill = isNight ? 0x22FFFFFF : 0xCCFFFFFF;
        row.setBackground(LauncherTheme.makeSegmentedRowDrawable(cardFill, Color.TRANSPARENT, topR, botR, 0));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard(etDrawerSearch);
                openGoogleWebSearch(suggestionText);
            }
        });

        FrameLayout badge = new FrameLayout(this);
        int badgeSize = Math.round(32f * density);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        blp.rightMargin = Math.round(14f * density);
        badge.setLayoutParams(blp);
        badge.setBackground(LauncherTheme.makeRoundedRectDrawable(
                isNight ? 0x2C000000 : 0x12000000, Color.TRANSPARENT, badgeSize * 0.5f, 0));

        ImageView icon = new ImageView(this);
        int iconSize = Math.round(16f * density);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        icon.setLayoutParams(ilp);
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(secondaryIcon);
        badge.addView(icon);
        row.addView(badge);

        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tv.setText(suggestionText);
        tv.setTextColor(primaryText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tv);

        ImageView fillBtn = new ImageView(this);
        int fillSize = Math.round(34f * density);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(fillSize, fillSize);
        flp.leftMargin = Math.round(4f * density);
        fillBtn.setLayoutParams(flp);
        int pad = Math.round(8f * density);
        fillBtn.setPadding(pad, pad, pad, pad);
        fillBtn.setImageResource(R.drawable.ic_arrow_up);
        fillBtn.setRotation(-45f);
        fillBtn.setColorFilter(secondaryIcon);
        fillBtn.setBackgroundResource(R.drawable.popup_item_bg);
        fillBtn.setClickable(true);
        fillBtn.setFocusable(true);
        fillBtn.setContentDescription("Fill search query");
        fillBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (etDrawerSearch != null) {
                    etDrawerSearch.setText(suggestionText);
                    etDrawerSearch.setSelection(suggestionText.length());
                }
            }
        });
        row.addView(fillBtn);
        return row;
    }

    private LinearLayout createPlayStoreSearchRow(
            final String query,
            boolean isNight,
            float density,
            int primaryText,
            int secondaryIcon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(52f * density)));
        row.setPadding(Math.round(14f * density), 0, Math.round(14f * density), 0);
        int cardFill = isNight ? 0x22FFFFFF : 0xCCFFFFFF;
        row.setBackground(LauncherTheme.makeSegmentedRowDrawable(
                cardFill, Color.TRANSPARENT, 20f * density, 20f * density, 0));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPlayStoreSearch(query);
            }
        });

        FrameLayout badge = new FrameLayout(this);
        int badgeSize = Math.round(32f * density);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(badgeSize, badgeSize);
        blp.rightMargin = Math.round(14f * density);
        badge.setLayoutParams(blp);
        badge.setBackground(LauncherTheme.makeRoundedRectDrawable(
                isNight ? 0x2C000000 : 0x12000000, Color.TRANSPARENT, badgeSize * 0.5f, 0));

        ImageView icon = new ImageView(this);
        int iconSize = Math.round(16f * density);
        icon.setLayoutParams(new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));
        icon.setImageResource(R.drawable.ic_search);
        icon.setColorFilter(secondaryIcon);
        badge.addView(icon);
        row.addView(badge);

        TextView tv = new TextView(this);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tv.setText("Search \"" + query + "\" in Play Store");
        tv.setTextColor(primaryText);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(tv);
        return row;
    }

    private void openPlayStoreSearch(String query) {
        hideKeyboard(etDrawerSearch);
        String encoded = Uri.encode(query != null ? query.trim() : "");
        try {
            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + encoded + "&c=apps"));
            market.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(market);
        } catch (Exception e) {
            try {
                Intent web = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/search?q=" + encoded + "&c=apps"));
                web.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(web);
            } catch (Exception ignored) {
                Toast.makeText(this, "Play Store not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void syncDrawerHeaderScroll() {
        if (llDrawerHeaderOverlay == null || gvApps == null) return;
        boolean hasQuery = (etDrawerSearch != null && etDrawerSearch.length() > 0);
        if (hasQuery || llDrawerPredictions == null || llDrawerPredictions.getChildCount() == 0) {
            llDrawerHeaderOverlay.setVisibility(View.GONE);
            return;
        }
        if (llDrawerHeaderOverlay.getVisibility() != View.VISIBLE) {
            llDrawerHeaderOverlay.setVisibility(View.VISIBLE);
        }
        int headerH = drawerHeaderHeightPx > 0 ? drawerHeaderHeightPx : llDrawerHeaderOverlay.getHeight();
        if (headerH <= 0) return;

        float transY;
        if (gvApps.getChildCount() == 0) {
            transY = 0f;
        } else if (gvApps.getFirstVisiblePosition() == 0) {
            View first = gvApps.getChildAt(0);
            int topPad = gvApps.getPaddingTop();
            transY = Math.min(0f, (float) (first.getTop() - topPad));
        } else {
            transY = -headerH;
        }
        llDrawerHeaderOverlay.setTranslationY(transY);
        float fadeDist = Math.max(1f, headerH * 0.78f);
        llDrawerHeaderOverlay.setAlpha(Math.max(0f, Math.min(1f, 1f + (transY / fadeDist))));
    }

    private void recordAppLaunchForPredictions(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            JSONObject obj = new JSONObject(prefs.getString(PREF_KEY_APP_LAUNCH_HISTORY, "{}"));
            JSONObject entry = obj.optJSONObject(packageName);
            if (entry == null) entry = new JSONObject();
            int count = entry.optInt("c", 0) + 1;
            entry.put("c", count);
            entry.put("t", System.currentTimeMillis());
            obj.put(packageName, entry);
            prefs.edit().putString(PREF_KEY_APP_LAUNCH_HISTORY, obj.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void refreshDrawerPredictions() {
        if (llDrawerPredictions == null) return;
        llDrawerPredictions.removeAllViews();
        if (appList.isEmpty()) {
            updateDrawerGridPadding();
            syncDrawerHeaderScroll();
            return;
        }

        final Map<String, Double> scores = new HashMap<>();
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            JSONObject obj = new JSONObject(prefs.getString(PREF_KEY_APP_LAUNCH_HISTORY, "{}"));
            long now = System.currentTimeMillis();
            for (AppInfo app : appList) {
                JSONObject e = obj.optJSONObject(app.packageName);
                if (e != null) {
                    int cnt = e.optInt("c", 0);
                    long t = e.optLong("t", 0L);
                    double hoursAgo = Math.max(0.1, (now - t) / 3600000.0);
                    double score = cnt * 2.5 + Math.max(0.0, 24.0 - hoursAgo * 0.8);
                    scores.put(app.packageName, score);
                }
            }
        } catch (Exception ignored) {}

        final String[] fallbackOrder = new String[]{
                "com.whatsapp", "com.facebook.katana", "com.apple.android.music", "com.spotify.music",
                "com.google.android.youtube", "com.android.chrome", "com.google.android.apps.photos", "com.android.settings"
        };

        List<AppInfo> candidates = new ArrayList<>();
        List<String> addedPkgs = new ArrayList<>();
        List<AppInfo> sorted = new ArrayList<>(appList);
        Collections.sort(sorted, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                Double sa = scores.get(a.packageName);
                Double sb = scores.get(b.packageName);
                double va = sa != null ? sa : 0.0;
                double vb = sb != null ? sb : 0.0;
                if (vb != va) return Double.compare(vb, va);
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        for (AppInfo a : sorted) {
            if (scores.containsKey(a.packageName) && !addedPkgs.contains(a.packageName)) {
                candidates.add(a);
                addedPkgs.add(a.packageName);
                if (candidates.size() >= 4) break;
            }
        }
        if (candidates.size() < 4) {
            for (String pkg : fallbackOrder) {
                AppInfo match = findAppByPkg(pkg);
                if (match != null && !addedPkgs.contains(match.packageName)) {
                    candidates.add(match);
                    addedPkgs.add(match.packageName);
                    if (candidates.size() >= 4) break;
                }
            }
        }
        for (AppInfo a : appList) {
            if (candidates.size() >= 4) break;
            if (!addedPkgs.contains(a.packageName)) {
                candidates.add(a);
                addedPkgs.add(a.packageName);
            }
        }

        boolean isNight = LauncherTheme.isNightMode(this);
        float density = getResources().getDisplayMetrics().density;
        int iconPx = Math.round(60f * density);
        int padPx = Math.round(4f * density);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final AppInfo app : candidates) {
            final View slotView = inflater.inflate(R.layout.item_app_grid, llDrawerPredictions, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            slotView.setLayoutParams(lp);
            slotView.setPadding(padPx, padPx, padPx, padPx);

            final ImageView iv = slotView.findViewById(R.id.iv_app_icon);
            TextView tv = slotView.findViewById(R.id.tv_app_label);
            if (iv != null) {
                ViewGroup.LayoutParams ilp = iv.getLayoutParams();
                if (ilp != null) {
                    ilp.width = iconPx;
                    ilp.height = iconPx;
                    iv.setLayoutParams(ilp);
                }
                if (app.icon != null) iv.setImageDrawable(app.icon);
                UiMotion.attachIconTouchSpring(slotView, iv);
            }
            if (tv != null) {
                tv.setText(app.label);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                tv.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
                tv.setTextColor(LauncherTheme.textPrimary(isNight));
            }
            slotView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!isGestureTapEligible()) return;
                    launchApp(app, iv != null ? iv : slotView);
                }
            });
            slotView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (!canStartGestureDragHold()) return false;
                    scheduleDeferredDragHold(new Runnable() {
                        @Override
                        public void run() {
                            if (drawerController != null && drawerController.isVisibleOrAnimating() && !drawerController.isDragging()) {
                                startDragFromDrawer(slotView, app);
                            }
                        }
                    }, LONG_PRESS_DRAWER_HOLD_MS);
                    return true;
                }
            });
            llDrawerPredictions.addView(slotView);
        }

        if (llDrawerHeaderOverlay != null) {
            llDrawerHeaderOverlay.post(new Runnable() {
                @Override
                public void run() {
                    if (llDrawerHeaderOverlay != null && llDrawerHeaderOverlay.getHeight() > 0) {
                        drawerHeaderHeightPx = llDrawerHeaderOverlay.getHeight();
                    }
                    updateDrawerGridPadding();
                    syncDrawerHeaderScroll();
                }
            });
        }
    }

    private void launchApp(AppInfo app) {
        launchApp(app, null);
    }

    private void launchApp(final AppInfo app, final View sourceView) {
        if (app == null) return;
        if (launchTransitionView != null && launchTransitionView.isAnimating()) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(app.packageName, app.activityName));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        final boolean fromDrawer = (drawerController != null && drawerController.isVisibleOrAnimating());
        pendingDrawerCollapseOnStop = fromDrawer;

        Long prevLaunchTime = recentlyLaunchedPackages.get(app.packageName);
        final boolean isWarmLaunch = (prevLaunchTime != null && (SystemClock.uptimeMillis() - prevLaunchTime) < WARM_LAUNCH_WINDOW_MS);

        Rect startBounds = UiMotion.measureScreenBounds(sourceView);
        if (startBounds != null && launchTransitionView != null) {
            View bgTarget = fromDrawer ? appDrawer : homeScreen;
            Drawable iconDrawable = (app.icon != null)
                    ? app.icon
                    : ((sourceView instanceof ImageView) ? ((ImageView) sourceView).getDrawable() : null);
            ComponentName launchComponent = new ComponentName(app.packageName, app.activityName);

            launchTransitionView.playLaunchExpansion(startBounds, launchComponent, iconDrawable, app.splashAccentColor, sourceView, bgTarget, isWarmLaunch, new Runnable() {
                @Override
                public void run() {
                    try {
                        UiMotion.launchWithClipReveal(MainActivity.this, intent, sourceView, true);
                        recentlyLaunchedPackages.put(app.packageName, SystemClock.uptimeMillis());
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                recordAppLaunchForPredictions(app.packageName);
                                refreshDrawerPredictions();
                            }
                        }, 620L);
                    } catch (Exception e) {
                        pendingDrawerCollapseOnStop = false;
                        pendingFolderLaunchPopup = null;
                        if (launchTransitionView != null) {
                            launchTransitionView.resetImmediately();
                        }
                        Toast.makeText(MainActivity.this, "Could not open " + app.label, Toast.LENGTH_SHORT).show();
                    }
                }
            });
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (launchTransitionView != null && launchTransitionView.getVisibility() == View.VISIBLE && hasWindowFocus()) {
                        launchTransitionView.resetImmediately();
                        pendingDrawerCollapseOnStop = false;
                    }
                }
            }, 900L);
            return;
        }

        try {
            UiMotion.launchWithClipReveal(this, intent, sourceView);
            recentlyLaunchedPackages.put(app.packageName, SystemClock.uptimeMillis());
            recordAppLaunchForPredictions(app.packageName);
            refreshDrawerPredictions();
        } catch (Exception e) {
            pendingDrawerCollapseOnStop = false;
            pendingFolderLaunchPopup = null;
            Toast.makeText(this, "Could not open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void registerPackageListeners() {
        if (!isPackageReceiverRegistered) {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_PACKAGE_ADDED);
                filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
                filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
                filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
                filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
                filter.addDataScheme("package");
                if (Build.VERSION.SDK_INT >= 33) {
                    // Context.RECEIVER_EXPORTED = 2 (compiled against API 30 jar)
                    Method regMethod = Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
                    regMethod.invoke(this, packageChangeReceiver, filter, 2);
                } else {
                    registerReceiver(packageChangeReceiver, filter);
                }
                isPackageReceiverRegistered = true;
            } catch (Throwable t) {
                try {
                    IntentFilter fallback = new IntentFilter();
                    fallback.addAction(Intent.ACTION_PACKAGE_ADDED);
                    fallback.addAction(Intent.ACTION_PACKAGE_REMOVED);
                    fallback.addAction(Intent.ACTION_PACKAGE_CHANGED);
                    fallback.addAction(Intent.ACTION_PACKAGE_REPLACED);
                    fallback.addDataScheme("package");
                    registerReceiver(packageChangeReceiver, fallback);
                    isPackageReceiverRegistered = true;
                } catch (Throwable ignored) {}
            }
        }

        if (launcherAppsCallback == null) {
            try {
                launcherAppsService = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherAppsService != null) {
                    launcherAppsCallback = new LauncherApps.Callback() {
                        @Override
                        public void onPackageRemoved(String packageName, UserHandle user) {
                            scheduleInstalledAppsRefresh(false, 160L);
                        }

                        @Override
                        public void onPackageAdded(String packageName, UserHandle user) {
                            scheduleInstalledAppsRefresh(false, 160L);
                        }

                        @Override
                        public void onPackageChanged(String packageName, UserHandle user) {
                            scheduleInstalledAppsRefresh(false, 160L);
                        }

                        @Override
                        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                            scheduleInstalledAppsRefresh(false, 160L);
                        }

                        @Override
                        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {
                            if (!replacing) {
                                scheduleInstalledAppsRefresh(false, 160L);
                            }
                        }

                        @Override
                        public void onPackagesSuspended(String[] packageNames, UserHandle user) {
                            scheduleInstalledAppsRefresh(false, 160L);
                        }

                        @Override
                        public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
                            scheduleInstalledAppsRefresh(false, 160L);
                        }
                    };
                    launcherAppsService.registerCallback(launcherAppsCallback, mainHandler);
                }
            } catch (Throwable t) {
                Log.w("PixelLauncher", "LauncherApps callback registration failed: " + t.getMessage());
            }
        }
    }

    private void unregisterPackageListeners() {
        if (pendingAppRefreshRunnable != null) {
            mainHandler.removeCallbacks(pendingAppRefreshRunnable);
            pendingAppRefreshRunnable = null;
        }
        if (isPackageReceiverRegistered) {
            try {
                unregisterReceiver(packageChangeReceiver);
            } catch (Throwable ignored) {}
            isPackageReceiverRegistered = false;
        }
        if (launcherAppsService != null && launcherAppsCallback != null) {
            try {
                launcherAppsService.unregisterCallback(launcherAppsCallback);
            } catch (Throwable ignored) {}
            launcherAppsCallback = null;
        }
    }

    private void scheduleInstalledAppsRefresh(final boolean onlyIfChanged, long delayMs) {
        if (pendingAppRefreshRunnable != null) {
            mainHandler.removeCallbacks(pendingAppRefreshRunnable);
        }
        pendingAppRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                pendingAppRefreshRunnable = null;
                loadInstalledApps(onlyIfChanged);
            }
        };
        mainHandler.postDelayed(pendingAppRefreshRunnable, Math.max(0L, delayMs));
    }

    private void loadInstalledApps(final boolean onlyIfChanged) {
        final int requestSeq = appLoadSeq.incrementAndGet();
        appLoaderExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (requestSeq != appLoadSeq.get()) return;

                PackageManager pm = getPackageManager();
                LauncherApps la = launcherAppsService;
                if (la == null) {
                    try {
                        la = (LauncherApps) getSystemService(Context.LAUNCHER_APPS_SERVICE);
                    } catch (Throwable ignored) {}
                }

                // Collect launchable components from both LauncherApps and PackageManager
                final class CandidateApp {
                    final String pkg;
                    final String act;
                    final String sourceDir;
                    final LauncherActivityInfo laInfo;
                    final ResolveInfo riInfo;

                    CandidateApp(String pkg, String act, String sourceDir, LauncherActivityInfo laInfo, ResolveInfo riInfo) {
                        this.pkg = pkg;
                        this.act = act;
                        this.sourceDir = sourceDir != null ? sourceDir : "";
                        this.laInfo = laInfo;
                        this.riInfo = riInfo;
                    }
                }

                Map<String, CandidateApp> unique = new LinkedHashMap<>();
                String selfPkg = getPackageName();
                UserHandle primaryUser = Process.myUserHandle();

                if (la != null) {
                    try {
                        List<LauncherActivityInfo> laList = la.getActivityList(null, primaryUser);
                        if (laList != null) {
                            for (LauncherActivityInfo info : laList) {
                                ComponentName cn = info.getComponentName();
                                if (cn == null) continue;
                                String pkg = cn.getPackageName();
                                if (selfPkg.equals(pkg)) continue;
                                String act = cn.getClassName();
                                ApplicationInfo ai = info.getApplicationInfo();
                                String srcDir = (ai != null && ai.sourceDir != null) ? ai.sourceDir : "";
                                unique.put(pkg + "/" + act, new CandidateApp(pkg, act, srcDir, info, null));
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                try {
                    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);
                    if (activities != null) {
                        for (ResolveInfo ri : activities) {
                            if (ri.activityInfo == null) continue;
                            String pkg = ri.activityInfo.packageName;
                            if (selfPkg.equals(pkg)) continue;
                            String act = ri.activityInfo.name;
                            String compKey = pkg + "/" + act;
                            if (!unique.containsKey(compKey)) {
                                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                                String srcDir = (ai != null && ai.sourceDir != null) ? ai.sourceDir : "";
                                unique.put(compKey, new CandidateApp(pkg, act, srcDir, null, ri));
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                List<String> sigKeys = new ArrayList<>(unique.size());
                for (CandidateApp c : unique.values()) {
                    sigKeys.add(c.pkg + "/" + c.act + "|" + c.sourceDir);
                }
                Collections.sort(sigKeys);
                StringBuilder sigBuilder = new StringBuilder(sigKeys.size() * 48);
                for (String s : sigKeys) {
                    sigBuilder.append(s).append('\n');
                }
                final String signature = sigBuilder.toString();

                if (onlyIfChanged && hasLoadedInitialWorkspace && signature.equals(loadedAppsSignature)) {
                    return;
                }

                final List<AppInfo> apps = new ArrayList<>(unique.size());
                for (CandidateApp c : unique.values()) {
                    if (requestSeq != appLoadSeq.get()) return;
                    try {
                        String label = null;
                        if (c.laInfo != null) {
                            CharSequence cs = c.laInfo.getLabel();
                            if (cs != null) label = cs.toString();
                        } else if (c.riInfo != null) {
                            CharSequence cs = c.riInfo.loadLabel(pm);
                            if (cs != null) label = cs.toString();
                        }
                        if (label == null || label.isEmpty()) label = c.pkg;

                        String cacheKey = c.pkg + "/" + c.act + "|" + c.sourceDir + "|" + label;
                        AppInfo cached = appInfoCache.get(cacheKey);
                        if (cached != null) {
                            apps.add(cached);
                            continue;
                        }

                        Drawable rawIcon = null;
                        if (c.laInfo != null) {
                            rawIcon = c.laInfo.getBadgedIcon(0);
                        } else if (c.riInfo != null) {
                            rawIcon = c.riInfo.loadIcon(pm);
                        }
                        int splashTint = IconNormalizer.extractSplashTintColor(rawIcon);
                        Drawable normIcon = IconNormalizer.normalizeToPixelCircle(MainActivity.this, rawIcon);
                        AppInfo entry = new AppInfo(label, c.pkg, c.act, normIcon, splashTint);
                        appInfoCache.put(cacheKey, entry);
                        apps.add(entry);
                    } catch (Throwable ignored) {}
                }

                Collections.sort(apps);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (requestSeq != appLoadSeq.get()) return;
                        loadedAppsSignature = signature;
                        appList.clear();
                        appList.addAll(apps);
                        if (appAdapter != null) {
                            appAdapter.updateList(apps);
                            if (etDrawerSearch != null && etDrawerSearch.length() > 0) {
                                handleDrawerSearchQueryChanged(etDrawerSearch.getText());
                            }
                        }
                        refreshDrawerPredictions();
                        if (drawerScrollbar != null) {
                            drawerScrollbar.notifyListChanged();
                        }
                        if (!hasLoadedInitialWorkspace) {
                            hasLoadedInitialWorkspace = true;
                            loadPages();
                            loadDockApps();
                            renderDock();
                            int rasterPx = Math.max(144, Math.round(108f * getResources().getDisplayMetrics().density));
                            java.util.List<AppInfo> warmupApps = new java.util.ArrayList<>(dockApps);
                            if (!pages.isEmpty() && pages.get(0) != null) {
                                for (LauncherItem it : pages.get(0)) {
                                    if (it != null && it.appInfo != null) {
                                        warmupApps.add(it.appInfo);
                                    }
                                }
                            }
                            IconNormalizer.prewarmLaunchLayers(MainActivity.this, warmupApps, rasterPx);
                        } else {
                            reconcileWorkspaceWithApps(apps);
                        }
                    }
                });
            }
        });
    }

    private boolean isPackageStillInstalled(PackageManager pm, String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void reconcileWorkspaceWithApps(List<AppInfo> freshApps) {
        if (isDragActive || isWindowModeActive) {
            scheduleInstalledAppsRefresh(false, 400L);
            return;
        }
        Map<String, AppInfo> byComp = new HashMap<>(freshApps.size());
        Map<String, AppInfo> byPkg = new HashMap<>(freshApps.size());
        for (AppInfo a : freshApps) {
            byComp.put(a.packageName + "/" + a.activityName, a);
            if (!byPkg.containsKey(a.packageName)) {
                byPkg.put(a.packageName, a);
            }
        }

        PackageManager pm = getPackageManager();
        boolean pagesRemoved = false;
        int prevPageCount = pages.size();

        for (int p = 0; p < pages.size(); p++) {
            List<LauncherItem> page = pages.get(p);
            for (int i = 0; i < page.size(); i++) {
                LauncherItem item = page.get(i);
                if (item == null) continue;
                if (item.type == LauncherItem.TYPE_APP && item.appInfo != null) {
                    AppInfo fresh = byComp.get(item.appInfo.packageName + "/" + item.appInfo.activityName);
                    if (fresh == null) fresh = byPkg.get(item.appInfo.packageName);
                    if (fresh != null) {
                        page.set(i, new LauncherItem(fresh));
                    } else if (!isPackageStillInstalled(pm, item.appInfo.packageName)) {
                        page.set(i, null);
                        pagesRemoved = true;
                    }
                } else if (item.type == LauncherItem.TYPE_FOLDER && item.folderInfo != null) {
                    for (int aIdx = item.folderInfo.apps.size() - 1; aIdx >= 0; aIdx--) {
                        AppInfo sub = item.folderInfo.apps.get(aIdx);
                        if (sub == null) continue;
                        AppInfo fresh = byComp.get(sub.packageName + "/" + sub.activityName);
                        if (fresh == null) fresh = byPkg.get(sub.packageName);
                        if (fresh != null) {
                            item.folderInfo.apps.set(aIdx, fresh);
                        } else if (!isPackageStillInstalled(pm, sub.packageName)) {
                            item.folderInfo.apps.remove(aIdx);
                            pagesRemoved = true;
                        }
                    }
                }
            }
        }

        cleanupFoldersOnAllPages();
        if (pagesRemoved) {
            pruneEmptyTrailingPages();
            savePages();
        }
        if (pages.size() != prevPageCount) {
            setupPagesLayout();
        } else {
            for (HomeItemAdapter adapter : pageAdapters) {
                adapter.notifyDataSetChanged();
            }
        }

        boolean dockRemoved = false;
        for (int d = 0; d < dockApps.size(); d++) {
            AppInfo dApp = dockApps.get(d);
            if (dApp == null) continue;
            AppInfo fresh = byComp.get(dApp.packageName + "/" + dApp.activityName);
            if (fresh == null) fresh = byPkg.get(dApp.packageName);
            if (fresh != null) {
                dockApps.set(d, fresh);
            } else if (!isPackageStillInstalled(pm, dApp.packageName)) {
                dockApps.set(d, null);
                dockRemoved = true;
            }
        }
        if (dockRemoved) {
            saveDockApps();
        }
        renderDock();
    }

    private void openAppDrawer() {
        syncDrawerHeaderScroll();
        if (drawerController != null) {
            drawerController.open();
        }
    }

    private void closeAppDrawer() {
        if (drawerController != null) {
            drawerController.close();
        }
    }

    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (customizationPanelOverlay != null && customizationPanelOverlay.getVisibility() == View.VISIBLE) {
                hideCustomizationPanel();
            }
            if (drawerController != null && drawerController.isVisibleOrAnimating()) {
                closeAppDrawer();
            }
        }
    }

    private void registerPredictiveBackCallback() {
        if (Build.VERSION.SDK_INT < 33 || onBackInvokedCallbackProxy != null) return;
        try {
            Class<?> dispatcherCls = Class.forName("android.window.OnBackInvokedDispatcher");
            Class<?> callbackCls = Class.forName("android.window.OnBackInvokedCallback");
            Method getDispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher");
            Object dispatcher = getDispatcher.invoke(this);
            if (dispatcher == null) return;

            final Object proxy = Proxy.newProxyInstance(
                    callbackCls.getClassLoader(),
                    new Class<?>[]{callbackCls},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object p, Method method, Object[] args) {
                            String name = method.getName();
                            if ("onBackInvoked".equals(name)) {
                                if (Looper.myLooper() == Looper.getMainLooper()) {
                                    handleBackNavigation();
                                } else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            handleBackNavigation();
                                        }
                                    });
                                }
                                return null;
                            }
                            if ("equals".equals(name)) {
                                return p == (args != null && args.length > 0 ? args[0] : null);
                            }
                            if ("hashCode".equals(name)) {
                                return System.identityHashCode(p);
                            }
                            if ("toString".equals(name)) {
                                return "LWLauncherBackCallback";
                            }
                            return null;
                        }
                    }
            );
            Method registerMethod = dispatcherCls.getMethod("registerOnBackInvokedCallback", int.class, callbackCls);
            registerMethod.invoke(dispatcher, 0, proxy);
            onBackInvokedCallbackProxy = proxy;
        } catch (Throwable t) {
            Log.w("PixelLauncher", "Failed to register OnBackInvokedCallback: " + t.getMessage());
        }
    }

    private void unregisterPredictiveBackCallback() {
        if (Build.VERSION.SDK_INT < 33 || onBackInvokedCallbackProxy == null) return;
        try {
            Class<?> dispatcherCls = Class.forName("android.window.OnBackInvokedDispatcher");
            Class<?> callbackCls = Class.forName("android.window.OnBackInvokedCallback");
            Method getDispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher");
            Object dispatcher = getDispatcher.invoke(this);
            if (dispatcher != null) {
                Method unregisterMethod = dispatcherCls.getMethod("unregisterOnBackInvokedCallback", callbackCls);
                unregisterMethod.invoke(dispatcher, onBackInvokedCallbackProxy);
            }
        } catch (Throwable ignored) {}
        onBackInvokedCallbackProxy = null;
    }

    private void handleBackNavigation() {
        if (isDragActive) {
            if (!dragMoved) {
                cancelUnmovedDrag();
            } else {
                endDrag(dragStartX, dragStartY);
            }
            return;
        }
        if (customizationPanelOverlay != null && customizationPanelOverlay.getVisibility() == View.VISIBLE) {
            hideCustomizationPanel();
            return;
        }
        if (drawerController != null && drawerController.isVisibleOrAnimating()) {
            if (etDrawerSearch != null && (etDrawerSearch.length() > 0 || etDrawerSearch.hasFocus())) {
                hideKeyboard(etDrawerSearch);
                etDrawerSearch.clearFocus();
                if (etDrawerSearch.length() > 0) {
                    etDrawerSearch.setText("");
                }
                return;
            }
            closeAppDrawer();
            return;
        }
        if (currentPage > 0) {
            scrollToPage(0);
        }
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void applySystemTheme(Configuration config) {
        boolean isNight = LauncherTheme.isNightMode(config);
        LauncherTheme.applyAppDrawerTheme(
                this,
                isNight,
                appDrawer,
                drawerDragHandle,
                drawerSearchBar,
                ivDrawerSearchIcon,
                etDrawerSearch,
                btnCloseDrawer,
                drawerScrollbar
        );
        LauncherTheme.applyHomeFallbackSearchTheme(this, isNight, findViewById(R.id.pixel_search_bar));
        LauncherTheme.applyCustomizationPanelTheme(this, isNight, panelCustomizationCard);
        if (btnDrawerMic != null) btnDrawerMic.clearColorFilter();
        if (btnDrawerLens != null) btnDrawerLens.clearColorFilter();
        if (tvDrawerAllAppsHeader != null) tvDrawerAllAppsHeader.setTextColor(LauncherTheme.textPrimary(isNight));
        if (llDrawerPredictions != null) {
            int predColor = LauncherTheme.textPrimary(isNight);
            for (int i = 0; i < llDrawerPredictions.getChildCount(); i++) {
                View slot = llDrawerPredictions.getChildAt(i);
                TextView tv = slot != null ? (TextView) slot.findViewById(R.id.tv_app_label) : null;
                if (tv != null) tv.setTextColor(predColor);
            }
        }
        if (svDrawerSearchResults != null
                && svDrawerSearchResults.getVisibility() == View.VISIBLE
                && !lastDrawerSearchQuery.isEmpty()) {
            List<AppInfo> matches = AppAdapter.rankMatches(appList, lastDrawerSearchQuery);
            String raw = etDrawerSearch != null && etDrawerSearch.getText() != null
                    ? etDrawerSearch.getText().toString()
                    : lastDrawerSearchQuery;
            updateDrawerTypeaheadHint(raw, matches);
            renderDrawerSearchResults(lastDrawerSearchQuery, matches, lastDrawerSuggestions);
        }
        if (appAdapter != null) {
            appAdapter.notifyDataSetChanged();
        }
        LauncherTheme.updateSystemBarsForDrawer(getWindow(), isNight, isDrawerVisiblyOpenForBars);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemTheme(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pendingDrawerCollapseOnStop = false;
        DisplayRefreshHelper.refreshCapabilities(this);
        DisplayRefreshHelper.applyWindowRefreshPreference(this, false);
        applyRefreshAdaptedScrollPhysics();
        if (pendingFolderLaunchPopup != null) {
            PopupWindow p = pendingFolderLaunchPopup;
            pendingFolderLaunchPopup = null;
            try {
                if (p.isShowing()) p.dismiss();
            } catch (Exception ignored) {}
        }
        if (launchTransitionView != null) {
            launchTransitionView.resetImmediately();
        }
        applySystemTheme(getResources().getConfiguration());
        setupAtAGlance();
        if (liveWeatherController != null) {
            liveWeatherController.refreshIfNeeded(!liveWeatherController.hasData());
        }
        if (rootLayout != null) {
            rootLayout.requestApplyInsets();
        }
        if (hasLoadedInitialWorkspace) {
            scheduleInstalledAppsRefresh(true, 100L);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (appWidgetHost != null) {
            try {
                appWidgetHost.startListening();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        isPrimaryPointerDown = false;
        cancelPendingLongPressRunnables();
        if (isDragActive && !dragMoved) {
            cancelUnmovedDrag();
        }
        if (pendingFolderLaunchPopup != null) {
            PopupWindow p = pendingFolderLaunchPopup;
            pendingFolderLaunchPopup = null;
            try {
                if (p.isShowing()) p.dismiss();
            } catch (Exception ignored) {}
        }
        if (pendingDrawerCollapseOnStop && drawerController != null) {
            pendingDrawerCollapseOnStop = false;
            drawerController.collapseImmediately();
        }
        if (launchTransitionView != null) {
            launchTransitionView.resetImmediately();
        }
        if (appWidgetHost != null) {
            try {
                appWidgetHost.stopListening();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        unregisterPredictiveBackCallback();
        unregisterPackageListeners();
        DisplayRefreshHelper.unregisterDisplayListener(this, displayListener);
        displayListener = null;
        if (pendingWebSuggestRunnable != null) {
            mainHandler.removeCallbacks(pendingWebSuggestRunnable);
            pendingWebSuggestRunnable = null;
        }
        if (drawerSearchMarginAnimator != null) {
            drawerSearchMarginAnimator.cancel();
            drawerSearchMarginAnimator = null;
        }
        if (liveWeatherController != null) {
            liveWeatherController.shutdown();
        }
        try {
            appLoaderExecutor.shutdownNow();
        } catch (Throwable ignored) {}
        super.onDestroy();
        if (isBatteryReceiverRegistered) {
            try {
                unregisterReceiver(batteryReceiver);
                isBatteryReceiverRegistered = false;
            } catch (Exception ignored) {}
        }
        if (appWidgetHost != null) {
            try {
                appWidgetHost.stopListening();
            } catch (Exception ignored) {}
        }
    }
}
