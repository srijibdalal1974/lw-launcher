package com.custom.pixellauncher;

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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.lang.reflect.Method;

public class MainActivity extends Activity {

    private static final String PREFS_NAME = "PixelLauncherPrefs";
    private static final String KEY_PAGES_JSON = "home_pages_json";
    private static final String KEY_PAGE_WIDGETS_JSON = "page_widgets_json";
    private static final String PREF_KEY_GRID_MODE = "pref_grid_mode";
    public static final int GRID_MODE_4X5 = 0;  // 4 columns x 5 rows (Large 58dp icons - Pixel Standard)
    public static final int GRID_MODE_5X5 = 1;  // 5 columns x 5 rows (Medium 50dp icons - Pixel Expressive)
    public static final int GRID_MODE_16X9 = 2; // 9 columns x 16 rows (Compact 34dp icons - Ultra Dense)
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
    private GridView gvApps;
    private EditText etDrawerSearch;
    private TextView tvDate;

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
    private static final int REQUEST_PICK_APPWIDGET = 102;
    private static final int REQUEST_CREATE_APPWIDGET = 103;
    private static final String PREF_GOOGLE_WIDGET_ID = "google_search_widget_id";

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

    private Runnable searchBarLongPressRunnable = null;
    private Runnable atAGlanceLongPressRunnable = null;
    private Runnable widgetLongPressRunnable = null;
    private float touchDownRawX = 0f;
    private float touchDownRawY = 0f;
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

        appWidgetManager = AppWidgetManager.getInstance(this);
        appWidgetHost = new AppWidgetHost(this, APPWIDGET_HOST_ID);
        try {
            appWidgetHost.startListening();
        } catch (Exception ignored) {}

        currentGridMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(PREF_KEY_GRID_MODE, GRID_MODE_4X5);

        initViews();
        setupWindowInsets();
        setupDrawerController();
        setupAtAGlance();
        setupSearchPill();
        setupDock();
        setupDrawer();
        setupCustomizationPanel();

        checkFirstRunRolePrompt();

        loadInstalledApps();
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        homeScreen = findViewById(R.id.home_screen);
        appDrawer = findViewById(R.id.app_drawer);
        gvApps = findViewById(R.id.gv_apps);
        etDrawerSearch = findViewById(R.id.et_drawer_search);
        tvDate = findViewById(R.id.tv_date);

        bottomDockContainer = findViewById(R.id.bottom_dock_container);

        dragOverlay = findViewById(R.id.drag_overlay);
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
                if (hsvHomePages.isAnimatingSnap()) {
                    return;
                }
                int width = getScreenWidth();
                if (width > 0 && pages.size() > 0) {
                    int activePage = Math.max(0, Math.min(pages.size() - 1, Math.round((float) scrollX / width)));
                    if (activePage != currentPage) {
                        currentPage = activePage;
                        updatePageDots(activePage);
                        if (isWindowModeActive) {
                            updateActivePageWindow(activePage);
                        }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        navBarHeight = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                    } catch (Throwable t) {
                        navBarHeight = insets.getSystemWindowInsetBottom();
                    }
                } else {
                    navBarHeight = insets.getSystemWindowInsetBottom();
                }

                if (navBarHeight > 0 || currentNavBarHeight != navBarHeight) {
                    currentNavBarHeight = navBarHeight;
                    applyNavBarInsets(currentNavBarHeight);
                }
                return insets;
            }
        });
        rootLayout.requestApplyInsets();
    }

    private int getNavigationBarHeight() {
        int navBarHeight = 0;
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            navBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        return navBarHeight;
    }

    private void applyNavBarInsets(int navBarHeight) {
        final int density = (int) getResources().getDisplayMetrics().density;
        final int safeBottom = (navBarHeight > 0) ? navBarHeight : 0;

        // Lift bottom dock container above 3-button navigation bar
        if (bottomDockContainer != null) {
            bottomDockContainer.setPadding(
                16 * density,
                0,
                16 * density,
                safeBottom + (14 * density)
            );
        }

        // Lift app drawer grid so the last row (e.g. YT Music) scrolls fully into clear view
        if (gvApps != null) {
            gvApps.setPadding(
                4 * density,
                4 * density,
                4 * density,
                safeBottom + (28 * density)
            );
            gvApps.setClipToPadding(false);
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
        int usedH = (int) ((76 + 132 + 16 + 14) * density) + navH;
        int availH = screenH - usedH;
        int rows = getGridRows();
        int cellH = availH / rows;
        int minH = (rows > 10) ? (int) (38 * density) : (int) (84 * density);
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
            pageContainer.setPadding(8 * density, 4 * density, 8 * density, 4 * density);
            pageContainer.setClipToPadding(false);
            pageContainer.setClipChildren(false);

            final FrameLayout pageGridCanvas = new FrameLayout(this);
            pageGridCanvas.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
                    if (pageIdx < pageAdapters.size()) {
                        LauncherItem item = pageAdapters.get(pageIdx).getItem(position);
                        if (item != null) {
                            if (item.type == LauncherItem.TYPE_FOLDER) {
                                showFolderPopup(view, item.folderInfo, pageIdx, position);
                            } else if (item.appInfo != null) {
                                launchApp(item.appInfo);
                            }
                        }
                    }
                }
            });

            gv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    if (isDragActive || isWindowModeActive || (hsvHomePages != null && hsvHomePages.isPaging())) {
                        return true;
                    }
                    if (pageIdx < pages.size() && position < pages.get(pageIdx).size()) {
                        LauncherItem item = pages.get(pageIdx).get(position);
                        if (item != null) {
                            startDragItem(view, item, pageIdx, position);
                            return true;
                        } else {
                            if (!isDragActive && !isWindowModeActive) {
                                showCustomizationPanel();
                            }
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
            pageContainer.addView(pageGridCanvas);
            pageContainer.addView(tvBadge);
            llHomePages.addView(pageContainer);
        }

        renderAllSavedWidgets();
        updatePageDots(currentPage);
    }

    private void refreshAllPages() {
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

        int density = Math.max(1, (int) getResources().getDisplayMetrics().density);
        if (layoutPageDots.getChildCount() != count) {
            layoutPageDots.removeAllViews();
            int dotBoxSize = 8 * density;
            for (int i = 0; i < count; i++) {
                final int pageIdx = i;
                ImageView dot = new ImageView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotBoxSize, dotBoxSize);
                lp.setMargins(4 * density, 0, 4 * density, 0);
                dot.setLayoutParams(lp);
                dot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        scrollToPage(pageIdx);
                    }
                });
                layoutPageDots.addView(dot);
            }
        }

        for (int i = 0; i < count; i++) {
            View child = layoutPageDots.getChildAt(i);
            if (!(child instanceof ImageView)) continue;
            ImageView dot = (ImageView) child;
            boolean isActive = (i == activePage);
            Object tag = dot.getTag();
            if (tag instanceof Boolean && ((Boolean) tag) == isActive) {
                continue;
            }
            dot.setTag(isActive);
            dot.setImageResource(isActive ? R.drawable.dot_active : R.drawable.dot_inactive);
            float scale = isActive ? 1.0f : 0.75f;
            float alpha = isActive ? 1.0f : 0.55f;
            dot.animate().scaleX(scale).scaleY(scale).alpha(alpha).setDuration(120).start();
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

        Log.d("PixelDrag", "startDragItem: item=" + item.getLabel() + " page=" + pageIdx + " pos=" + position);

        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }

    private void startDragSpecialItem(int type, String label, View view) {
        if (isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
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
    }

    private void startDragWidget(int sourcePage, int widgetId, String label, int spanCols, int spanRows, View view) {
        if (isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
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
    }

    private void startDragDockApp(int slot, AppInfo app, View view) {
        if (isDragActive) return;
        hideCustomizationPanel();
        isDragActive = true;
        dragMoved = false;
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
    }

    private boolean isTouchInsideView(View v, float rawX, float rawY) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        int slop = (int) (14 * getResources().getDisplayMetrics().density);
        return (rawX >= loc[0] - slop && rawX <= loc[0] + v.getWidth() + slop &&
                rawY >= loc[1] - slop && rawY <= loc[1] + v.getHeight() + slop);
    }

    private void highlightDropTarget(View v, int bgRes) {
        if (v != null) {
            v.setBackgroundResource(bgRes);
            v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
        }
    }

    private void unhighlightDropTarget(View v, int bgRes) {
        if (v != null) {
            v.setBackgroundResource(bgRes);
            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
        }
    }

    private void handleDragTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        float density = getResources().getDisplayMetrics().density;
        int iconSize = (int) (64 * density);
        int halfSize = iconSize / 2;
        int offsetY = (int) (36 * density);

        switch (action) {
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - dragStartX;
                float dy = ev.getRawY() - dragStartY;
                float dist = (float) Math.hypot(dx, dy);

                if (!dragMoved && dist > 14 * density) {
                    dragMoved = true;
                    lastDragTouchX = ev.getRawX();

                    if (dragSourceView != null) {
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
                        ivFloatingDrag.setScaleX(1.15f);
                        ivFloatingDrag.setScaleY(1.15f);
                        ivFloatingDrag.setRotation(0f);
                    }

                    if (dropTargetRemove != null) {
                        dropTargetRemove.setBackgroundResource(R.drawable.drop_target_bg);
                        dropTargetRemove.setScaleX(1.0f);
                        dropTargetRemove.setScaleY(1.0f);
                    }
                    if (dropTargetUninstall != null) {
                        dropTargetUninstall.setVisibility(draggedSpecialItem == SPECIAL_ITEM_NONE ? View.VISIBLE : View.GONE);
                        dropTargetUninstall.setBackgroundResource(R.drawable.drop_target_bg);
                        dropTargetUninstall.setScaleX(1.0f);
                        dropTargetUninstall.setScaleY(1.0f);
                    }

                    if (dragOverlay != null) {
                        dragOverlay.setVisibility(View.VISIBLE);
                    }

                    if (llDropTargetsBar != null) {
                        llDropTargetsBar.setVisibility(View.VISIBLE);
                        llDropTargetsBar.setAlpha(0f);
                        llDropTargetsBar.setTranslationY(-30f * density);
                        llDropTargetsBar.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
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

                    boolean isOverRemove = isTouchInsideView(dropTargetRemove, ev.getRawX(), ev.getRawY());
                    boolean isOverUninstall = isTouchInsideView(dropTargetUninstall, ev.getRawX(), ev.getRawY());

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

                        // Intentional, refined edge scrolling (36dp margin, 500ms hold dwell)
                        long now = System.currentTimeMillis();
                        int screenW = getScreenWidth();
                        float edgeThreshold = 36 * density;

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
                                if (now - edgeHoldStartTime >= 420 && now - lastEdgeScrollTime > 550) {
                                    if (pendingEdgeDirection == 1) {
                                        if (currentPage < pages.size() - 1) {
                                            scrollToPage(currentPage + 1);
                                            lastEdgeScrollTime = now;
                                            edgeHoldStartTime = now;
                                        } else if (pages.size() < 7 && now - lastEdgeScrollTime > 1000) {
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
                            if (isTouchInsideView(chip, ev.getRawX(), ev.getRawY())) {
                                hoveredChipIdx = c;
                                break;
                            }
                        }

                        if (hoveredChipIdx >= 0 && hoveredChipIdx < pages.size()) {
                            if (hoveredChipIdx != currentPage && now - lastEdgeScrollTime > 180) {
                                scrollToPage(hoveredChipIdx);
                                lastEdgeScrollTime = now;
                            }
                        } else if (dragChipDotsView != null && isTouchInsideView(dragChipDotsView, ev.getRawX(), ev.getRawY())) {
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
            updateOverlayGhostPreview(rawX, rawY, getGlanceSpanCols(), getGlanceSpanRows(), -1, true);
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
                            currentHoverView.animate().scaleX(1.15f).scaleY(1.15f).setDuration(120).start();
                        } else {
                            currentHoverView.setBackgroundResource(R.drawable.drop_target_bg);
                            currentHoverView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start();
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

        if (ivFloatingDrag != null) {
            ivFloatingDrag.animate().rotation(0f).scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
        }

        if (llDropTargetsBar != null) {
            llDropTargetsBar.setVisibility(View.GONE);
        }
        if (dropTargetUninstall != null) {
            dropTargetUninstall.setVisibility(View.VISIBLE);
        }

        if (dragSourceView != null) {
            dragSourceView.setAlpha(1.0f);
        }
        if (dragOverlay != null) {
            dragOverlay.setVisibility(View.GONE);
        }

        isDragActive = false;
        edgeHoldStartTime = 0L;
        pendingEdgeDirection = 0;

        if (!dragMoved) {
            Log.d("PixelDrag", "endDrag without move: showing popup directly without blur");
            if (draggedSpecialItem != SPECIAL_ITEM_NONE) {
                draggedSpecialItem = SPECIAL_ITEM_NONE;
                draggedWidgetId = -1;
                draggedWidgetSourcePage = -1;
                if (dragSourceView != null) dragSourceView.setAlpha(1.0f);
                return;
            }
            if (dragSourcePage == -1 && draggedItem != null) {
                addItemToCurrentPage(draggedItem);
            }
            if (dragSourcePage == DRAG_SOURCE_DOCK) {
                if (dragSourceView != null) dragSourceView.setAlpha(1.0f);
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

        float dragScaleX = isWindowModeActive ? windowScaleX : 1.0f;
        float dragScaleY = isWindowModeActive ? windowScaleY : 1.0f;
        exitPageWindowMode();

        // Check if dropped onto page chips
        int droppedChipIdx = -1;
        for (int c = 0; c < dragChipViews.size(); c++) {
            if (isTouchInsideView(dragChipViews.get(c), rawX, rawY)) {
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

            boolean isOverRemove = (activeDropZone == DROP_ZONE_REMOVE || isTouchInsideView(dropTargetRemove, rawX, rawY));
            float screenH = getScreenHeight();

            if (specialType == SPECIAL_ITEM_SEARCH_BAR) {
                if (isOverRemove) {
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
                if (isOverRemove) {
                    setAtAGlanceVisible(false);
                    Toast.makeText(this, "Removed Calendar from Home screen", Toast.LENGTH_SHORT).show();
                } else {
                    int gSpanC = getGlanceSpanCols();
                    int gSpanR = getGlanceSpanRows();
                    GridView targetGv = (targetPage >= 0 && targetPage < pageGridViews.size()) ? pageGridViews.get(targetPage) : null;
                    int[] desired = (droppedChipIdx >= 0 || targetGv == null)
                            ? new int[]{0, 0}
                            : computeDropGridOrigin(targetGv, rawX, rawY, gSpanC, gSpanR);
                    int[] best = findBestOverlaySlot(targetPage, desired[0], desired[1], gSpanC, gSpanR, -1, true);
                    pushAppsOutOfOverlayRect(targetPage, best[0], best[1], gSpanC, gSpanR);
                    setAtAGlancePlacement(targetPage, best[0], best[1]);
                    if (droppedChipIdx >= 0) scrollToPage(targetPage);
                    Toast.makeText(this, "Placed Calendar on Page " + (targetPage + 1) + " (Row " + (best[1] + 1) + ")", Toast.LENGTH_SHORT).show();
                }
                return;
            } else if (specialType == SPECIAL_ITEM_WIDGET) {
                if (isOverRemove) {
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

        // Check if dropped onto Remove target
        if (activeDropZone == DROP_ZONE_REMOVE || isTouchInsideView(dropTargetRemove, rawX, rawY)) {
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

        // Check if dropped onto Uninstall target
        if (activeDropZone == DROP_ZONE_UNINSTALL || isTouchInsideView(dropTargetUninstall, rawX, rawY)) {
            if (dragSourcePage == DRAG_SOURCE_DOCK) {
                setDockApp(dragSourcePosition, null);
            }
            if (draggedItem.type == LauncherItem.TYPE_APP && draggedItem.appInfo != null) {
                removeAppFromAllPages(draggedItem.appInfo.packageName);
                pruneEmptyTrailingPages();
                savePages();
                refreshAllPages();
                try {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + draggedItem.appInfo.packageName));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not start uninstallation", Toast.LENGTH_SHORT).show();
                }
            } else if (draggedItem.type == LauncherItem.TYPE_FOLDER) {
                Toast.makeText(this, "Cannot uninstall a folder", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Check if dropped onto dock
        View dockContainer = findViewById(R.id.dock_icons);
        if (dockContainer != null && isTouchInsideView(dockContainer, rawX, rawY)) {
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
        } else if (dragChipDotsView != null && isTouchInsideView(dragChipDotsView, rawX, rawY)) {
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

    private void animatePopIn(final View view) {
        if (view == null) return;
        view.setScaleX(0.75f);
        view.setScaleY(0.75f);
        view.setAlpha(0f);
        view.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(220)
                .setInterpolator(new OvershootInterpolator(1.3f))
                .start();
    }

    private void showPixelPopup(final View anchor, final AppInfo app, final int pageIndex, final int itemIndex) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View popupView = inflater.inflate(R.layout.popup_app_menu, null);

        final PopupWindow popupWindow = new PopupWindow(popupView,
                (int) (230 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24f);

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

        for (int p = 0; p < pages.size(); p++) {
            final int targetPage = p;
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(32 * density, 32 * density);
            lp.setMargins(3 * density, 0, 3 * density, 0);
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            chip.setText(String.valueOf(p + 1));
            chip.setTextColor(Color.WHITE);
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
            addChip.setTextColor(Color.WHITE);
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

        // Uninstall
        popupView.findViewById(R.id.popup_item_uninstall).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.dismiss();
                try {
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + app.packageName));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not start uninstallation", Toast.LENGTH_SHORT).show();
                }
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
        int y = Math.max(100, anchorY - (int) (160 * density));
        if (y < 120 && anchor != null) {
            y = anchorY + anchor.getHeight() + 10;
        }

        popupWindow.showAtLocation(rootLayout, Gravity.NO_GRAVITY, x, y);
        animatePopIn(popupView);
    }

    private void showFolderOptionsPopup(final View anchor, final FolderInfo folder, final int pageIndex, final int itemIndex) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View popupView = inflater.inflate(R.layout.popup_folder_options, null);

        final PopupWindow popupWindow = new PopupWindow(popupView,
                (int) (230 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24f);

        // Dynamic Page Chips
        LinearLayout llChips = popupView.findViewById(R.id.folder_ll_page_chips);
        llChips.removeAllViews();
        int density = (int) getResources().getDisplayMetrics().density;

        for (int p = 0; p < pages.size(); p++) {
            final int targetPage = p;
            TextView chip = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(32 * density, 32 * density);
            lp.setMargins(3 * density, 0, 3 * density, 0);
            chip.setLayoutParams(lp);
            chip.setGravity(Gravity.CENTER);
            chip.setText(String.valueOf(p + 1));
            chip.setTextColor(Color.WHITE);
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
        animatePopIn(popupView);
    }

    private void showFolderPopup(final View anchorView, final FolderInfo folder, final int pageIdx, final int itemIdx) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View dialogView = inflater.inflate(R.layout.popup_folder_view, null);

        final EditText etTitle = dialogView.findViewById(R.id.et_folder_title);
        etTitle.setText(folder.name);

        GridView gv = dialogView.findViewById(R.id.gv_folder_apps);
        final AppAdapter adapter = new AppAdapter(this, folder.apps);
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

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(28f * density);

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

        gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = adapter.getItem(position);
                if (app != null) {
                    popupWindow.dismiss();
                    launchApp(app);
                }
            }
        });

        gv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = adapter.getItem(position);
                if (app != null) {
                    popupWindow.dismiss();
                    folder.apps.remove(position);
                    if (folder.apps.size() == 1) {
                        pages.get(pageIdx).set(itemIdx, new LauncherItem(folder.apps.get(0)));
                    } else if (folder.apps.isEmpty()) {
                        pages.get(pageIdx).set(itemIdx, null);
                    }
                    cleanupFoldersOnAllPages();
                    pruneEmptyTrailingPages();
                    savePages();
                    refreshAllPages();
                    startDragItem(null, new LauncherItem(app), -1, -1);
                }
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

        dialogView.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(240)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();
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
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouchRawX = ev.getRawX();
        lastTouchRawY = ev.getRawY();

        if (isDragActive) {
            handleDragTouchEvent(ev);
            return true;
        }

        if (drawerController != null && drawerController.onDispatchTouchEvent(ev)) {
            return true;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownRawX = ev.getRawX();
                touchDownRawY = ev.getRawY();
                cancelPendingLongPressRunnables();

                boolean drawerClosed = (drawerController == null || !drawerController.isVisibleOrAnimating());
                boolean panelClosed = (customizationPanelOverlay == null || customizationPanelOverlay.getVisibility() != View.VISIBLE);
                if (drawerClosed && panelClosed && !isWindowModeActive) {
                    // 1. Long press detector for Original Google Search Bar (either Top or Bottom container)
                    final View topSearchContainer = findViewById(R.id.search_bar_top_container);
                    final View bottomSearchContainer = findViewById(R.id.search_bar_container);
                    final View activeSearchContainer = (topSearchContainer != null && topSearchContainer.getVisibility() == View.VISIBLE)
                            ? topSearchContainer
                            : ((bottomSearchContainer != null && bottomSearchContainer.getVisibility() == View.VISIBLE) ? bottomSearchContainer : null);

                    if (activeSearchContainer != null && isTouchInsideView(activeSearchContainer, ev.getRawX(), ev.getRawY())) {
                        searchBarLongPressRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (!isDragActive && (hsvHomePages == null || !hsvHomePages.isPaging()) && activeSearchContainer.getVisibility() == View.VISIBLE) {
                                    startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", activeSearchContainer);
                                }
                            }
                        };
                        mainHandler.postDelayed(searchBarLongPressRunnable, 300);
                    }

                    // 2. Long press detector for Calendar on current page
                    final View currGlanceView = pageGlanceViews.get(currentPage);
                    if (currGlanceView != null && currGlanceView.getVisibility() == View.VISIBLE && isTouchInsideView(currGlanceView, ev.getRawX(), ev.getRawY())) {
                        atAGlanceLongPressRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (!isDragActive && (hsvHomePages == null || !hsvHomePages.isPaging()) && currGlanceView.isAttachedToWindow()) {
                                    startDragSpecialItem(SPECIAL_ITEM_CALENDAR, "At a Glance (Calendar)", currGlanceView);
                                }
                            }
                        };
                        mainHandler.postDelayed(atAGlanceLongPressRunnable, 280);
                    }

                    // 3. Long press detector for placed AppWidgets on current page (works even over clickable widget faces like Clock)
                    List<View> currWidgets = pageWidgetWrappers.get(currentPage);
                    if (currWidgets != null) {
                        for (final View wWrapper : currWidgets) {
                            if (wWrapper != null && wWrapper.getVisibility() == View.VISIBLE && isTouchInsideView(wWrapper, ev.getRawX(), ev.getRawY())) {
                                Object tagObj = wWrapper.getTag();
                                if (tagObj instanceof WidgetDragTag) {
                                    final WidgetDragTag dTag = (WidgetDragTag) tagObj;
                                    widgetLongPressRunnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!isDragActive && (hsvHomePages == null || !hsvHomePages.isPaging()) && wWrapper.isAttachedToWindow()) {
                                                startDragWidget(dTag.pw.page, dTag.pw.id, dTag.label, dTag.pw.spanCols, dTag.pw.spanRows, wWrapper);
                                            }
                                        }
                                    };
                                    mainHandler.postDelayed(widgetLongPressRunnable, 380);
                                }
                                break;
                            }
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float moveDist = (float) Math.hypot(ev.getRawX() - touchDownRawX, ev.getRawY() - touchDownRawY);
                if ((hsvHomePages != null && hsvHomePages.isPaging())
                        || (drawerController != null && drawerController.isDragging())
                        || moveDist > 14 * getResources().getDisplayMetrics().density) {
                    cancelPendingLongPressRunnables();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPendingLongPressRunnables();
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    private void setupAtAGlance() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        tvDate.setText(currentDate);

        View atAGlance = findViewById(R.id.at_a_glance);
        if (atAGlance != null) {
            atAGlance.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCalendar();
                }
            });
            atAGlance.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    startDragSpecialItem(SPECIAL_ITEM_CALENDAR, "At a Glance (Calendar)", v);
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

        if (cardWallpaperCurrent != null) cardWallpaperCurrent.setOnClickListener(wpClickListener);
        if (cardWallpaperTheme1 != null) cardWallpaperTheme1.setOnClickListener(wpClickListener);
        if (cardWallpaperTheme2 != null) cardWallpaperTheme2.setOnClickListener(wpClickListener);
        if (cardWallpaperTheme3 != null) cardWallpaperTheme3.setOnClickListener(wpClickListener);
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

        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            Drawable wp = wm.getDrawable();
            if (wp != null && ivWallpaperThumbCurrent != null) {
                ivWallpaperThumbCurrent.setImageDrawable(wp);
            }
        } catch (Exception ignored) {}

        float density = getResources().getDisplayMetrics().density;
        customizationPanelOverlay.setVisibility(View.VISIBLE);
        customizationPanelOverlay.setAlpha(0f);
        customizationPanelOverlay.animate().alpha(1f).setDuration(180).start();

        if (panelCustomizationCard != null) {
            panelCustomizationCard.setTranslationY(80f * density);
            panelCustomizationCard.animate()
                    .translationY(0f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void hideCustomizationPanel() {
        if (customizationPanelOverlay == null || customizationPanelOverlay.getVisibility() != View.VISIBLE) return;

        float density = getResources().getDisplayMetrics().density;
        if (panelCustomizationCard != null) {
            panelCustomizationCard.animate()
                    .translationY(80f * density)
                    .setDuration(180)
                    .start();
        }
        customizationPanelOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        customizationPanelOverlay.setVisibility(View.GONE);
                    }
                })
                .start();
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
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog_home_settings)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();

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
        if (btnDefault != null) {
            btnDefault.setText(isDef ? "Active" : "Set Default");
            btnDefault.setBackgroundResource(isDef ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
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

        // Grid Mode Pills
        final TextView btn4x5 = dialog.findViewById(R.id.btn_grid_mode_4x5);
        final TextView btn5x5 = dialog.findViewById(R.id.btn_grid_mode_5x5);
        final TextView btn16x9 = dialog.findViewById(R.id.btn_grid_mode_16x9);

        final Runnable updateGridPills = new Runnable() {
            @Override
            public void run() {
                if (btn4x5 != null) btn4x5.setBackgroundResource(currentGridMode == GRID_MODE_4X5 ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
                if (btn5x5 != null) btn5x5.setBackgroundResource(currentGridMode == GRID_MODE_5X5 ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
                if (btn16x9 != null) btn16x9.setBackgroundResource(currentGridMode == GRID_MODE_16X9 ? R.drawable.pill_active_bg : R.drawable.pill_inactive_bg);
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
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog_widget_picker)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();

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
                    int gSpanC = getGlanceSpanCols();
                    int gSpanR = getGlanceSpanRows();
                    int[] free = findFirstFreeSpotAvoidingAll(currentPage, gSpanC, gSpanR, -1, true);
                    pushAppsOutOfOverlayRect(currentPage, free[0], free[1], gSpanC, gSpanR);
                    setAtAGlancePlacement(currentPage, free[0], free[1]);
                    Toast.makeText(MainActivity.this, "Added Calendar to Page " + (currentPage + 1), Toast.LENGTH_SHORT).show();
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
            if (searchWidgetHostView.getParent() instanceof ViewGroup && searchWidgetHostView.getParent() != target) {
                ((ViewGroup) searchWidgetHostView.getParent()).removeView(searchWidgetHostView);
            }
            if (searchWidgetHostView.getParent() == null) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                searchWidgetHostView.setLayoutParams(lp);
                target.addView(searchWidgetHostView);
            }
            float density = getResources().getDisplayMetrics().density;
            int wDp = (int) ((getScreenWidth() - 32 * density) / density);
            Bundle opts = new Bundle();
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 52);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 58);
            searchWidgetHostView.updateAppWidgetSize(opts, wDp, 54, wDp, 58);
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
        renderAllSavedWidgets();
        refreshAllPages();
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
        renderAllSavedWidgets();
        refreshAllPages();
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

        // 2. Migrate Calendar onto page grid Row 0, shifting existing Row 0 apps down cleanly if room exists
        int oldGlancePage = prefs.getInt(PREF_KEY_AT_A_GLANCE_PAGE, -1);
        int gPage = (oldGlancePage >= 0 && oldGlancePage < pages.size()) ? oldGlancePage : 0;
        prefs.edit()
                .putInt(PREF_KEY_AT_A_GLANCE_PAGE, gPage)
                .putInt(PREF_KEY_AT_A_GLANCE_COL, 0)
                .putInt(PREF_KEY_AT_A_GLANCE_ROW, 0)
                .remove("pref_at_a_glance_pos")
                .apply();

        if (isAtAGlanceVisible() && gPage < pages.size()) {
            List<LauncherItem> pageList = pages.get(gPage);
            int cols = getGridColumns();
            int totalSlots = pageList.size();
            boolean row0HasApp = false;
            for (int c = 0; c < Math.min(cols, totalSlots); c++) {
                if (pageList.get(c) != null) {
                    row0HasApp = true;
                    break;
                }
            }
            if (row0HasApp) {
                boolean lastRowClear = true;
                for (int s = Math.max(0, totalSlots - cols); s < totalSlots; s++) {
                    if (pageList.get(s) != null) {
                        lastRowClear = false;
                        break;
                    }
                }
                if (lastRowClear && totalSlots > cols) {
                    for (int s = totalSlots - 1; s >= cols; s--) {
                        pageList.set(s, pageList.get(s - cols));
                    }
                    for (int c = 0; c < cols; c++) {
                        pageList.set(c, null);
                    }
                } else {
                    pushAppsOutOfOverlayRect(gPage, 0, 0, getGlanceSpanCols(), getGlanceSpanRows());
                }
            }
        }

        prefs.edit().putBoolean(PREF_KEY_V206_MIGRATED, true).apply();
        savePages();
        updateSearchBarVisibility();
        updateAtAGlanceVisibility();
    }

    private LinearLayout createPageGlanceView() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (14 * density);
        int padV = (int) (6 * density);
        wrapper.setPadding(padH, padV, padH, padV);

        TextView tvD = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        tvD.setText(sdf.format(new Date()));
        tvD.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentGridMode == GRID_MODE_16X9 ? 18 : 22);
        tvD.setTextColor(Color.WHITE);
        tvD.setMaxLines(1);
        tvD.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvD.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvD.setShadowLayer(4f, 1f, 2f, Color.parseColor("#99000000"));
        wrapper.addView(tvD);

        TextView tvSub = new TextView(this);
        tvSub.setText("Tap for Calendar");
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentGridMode == GRID_MODE_16X9 ? 11 : 13);
        tvSub.setTextColor(Color.parseColor("#D9FFFFFF"));
        tvSub.setMaxLines(1);
        tvSub.setShadowLayer(3f, 1f, 1f, Color.parseColor("#99000000"));
        wrapper.addView(tvSub);

        wrapper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });
        wrapper.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startDragSpecialItem(SPECIAL_ITEM_CALENDAR, "At a Glance (Calendar)", v);
                return true;
            }
        });

        return wrapper;
    }

    private void setGridMode(int mode) {
        currentGridMode = mode;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(PREF_KEY_GRID_MODE, currentGridMode).apply();
        loadPages();
        setupPagesLayout();
        refreshAllPages();
        String name = (mode == GRID_MODE_4X5) ? "4 x 5 (Large 58dp)" : ((mode == GRID_MODE_5X5) ? "5 x 5 (Medium 50dp)" : "16 x 9 (Dense 34dp)");
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
        if (!ignoreGlance && isAtAGlanceVisible() && getAtAGlancePage() == pageIdx) {
            int gCols = getGridColumns();
            int gRows = getGridRows();
            int spanC = getGlanceSpanCols();
            int spanR = getGlanceSpanRows();
            int gCol = Math.max(0, Math.min(gCols - spanC, getAtAGlanceCol()));
            int gRow = Math.max(0, Math.min(gRows - spanR, getAtAGlanceRow()));
            if (col >= gCol && col < gCol + spanC && row >= gRow && row < gRow + spanR) {
                return true;
            }
        }
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
        pageGlanceViews.remove(pageIdx);
        List<View> wrappers = new ArrayList<>();
        pageWidgetWrappers.put(pageIdx, wrappers);

        if (gv.getWidth() <= 0 || gv.getHeight() <= 0) {
            if (ghost != null) overlay.addView(ghost);
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int cols = getGridColumns();
        int rows = getGridRows();

        // 1. Calendar (At a Glance) rendered directly at its exact (col, row) grid cells
        if (isAtAGlanceVisible() && getAtAGlancePage() == pageIdx) {
            int gSpanC = getGlanceSpanCols();
            int gSpanR = getGlanceSpanRows();
            int gCol = Math.max(0, Math.min(cols - gSpanC, getAtAGlanceCol()));
            int gRow = Math.max(0, Math.min(rows - gSpanR, getAtAGlanceRow()));
            int[] bounds = computeOverlayCellBounds(gv, gCol, gRow, gSpanC, gSpanR);

            LinearLayout glanceView = createPageGlanceView();
            FrameLayout.LayoutParams lpGlance = new FrameLayout.LayoutParams(bounds[2], bounds[3]);
            lpGlance.leftMargin = bounds[0];
            lpGlance.topMargin = bounds[1];
            glanceView.setLayoutParams(lpGlance);
            overlay.addView(glanceView);
            pageGlanceViews.put(pageIdx, glanceView);
        }

        // 2. Android AppWidgets rendered at their exact (col, row, spanCols, spanRows) grid cells
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
                        startDragWidget(pageIdx, pw.id, label, pw.spanCols, pw.spanRows, wrapper);
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
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
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
                public boolean onLongClick(View v) {
                    startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", v);
                    return true;
                }
            });
        }

        View searchContainer = findViewById(R.id.search_bar_container);
        if (searchContainer != null) {
            searchContainer.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", v);
                    return true;
                }
            });
        }

        View btnGoogle = findViewById(R.id.btn_google_g);
        if (btnGoogle != null) {
            btnGoogle.setOnClickListener(new View.OnClickListener() {
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

    private void setupOriginalGoogleSearchWidget() {
        updateSearchBarVisibility();
        final FrameLayout container = findViewById(R.id.search_bar_container);
        if (container == null || appWidgetManager == null || appWidgetHost == null) return;

        // Try candidate providers for the original Google search bar
        ComponentName selectedProvider = null;
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
                        selectedProvider = cn;
                        break;
                    }
                }
                if (selectedProvider != null) break;
            }
        } catch (Exception ignored) {}

        if (selectedProvider == null) {
            Log.d("PixelLauncher", "Google Search Widget provider not found, keeping fallback");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int widgetId = prefs.getInt(PREF_GOOGLE_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        AppWidgetProviderInfo widgetInfo = null;

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetInfo = appWidgetManager.getAppWidgetInfo(widgetId);
        }

        if (widgetInfo == null) {
            try {
                widgetId = appWidgetHost.allocateAppWidgetId();
                boolean bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, selectedProvider);
                if (!bound) {
                    Intent bindIntent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                    bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                    bindIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, selectedProvider);
                    startActivityForResult(bindIntent, REQUEST_BIND_APPWIDGET);
                    return;
                }
                prefs.edit().putInt(PREF_GOOGLE_WIDGET_ID, widgetId).apply();
                widgetInfo = appWidgetManager.getAppWidgetInfo(widgetId);
            } catch (Exception e) {
                Log.e("PixelLauncher", "Error allocating/binding widget: " + e.getMessage());
                return;
            }
        }

        if (widgetInfo != null) {
            try {
                if (searchWidgetHostView != null && searchWidgetHostView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) searchWidgetHostView.getParent()).removeView(searchWidgetHostView);
                }
                searchWidgetHostView = appWidgetHost.createView(this, widgetId, widgetInfo);
                searchWidgetHostView.setAppWidget(widgetId, widgetInfo);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                searchWidgetHostView.setLayoutParams(lp);

                searchWidgetHostView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        View activeParent = (v.getParent() instanceof View) ? (View) v.getParent() : container;
                        startDragSpecialItem(SPECIAL_ITEM_SEARCH_BAR, "Google Search Bar", activeParent);
                        return true;
                    }
                });

                View fallbackPill = findViewById(R.id.pixel_search_bar);
                if (fallbackPill != null) {
                    fallbackPill.setVisibility(View.GONE);
                }
                attachOriginalSearchWidgetToActiveContainer();
                Log.d("PixelLauncher", "Original Google search bar widget attached successfully!");
            } catch (Exception e) {
                Log.e("PixelLauncher", "Error creating hostView: " + e.getMessage());
            }
        }
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
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.setPackage("com.google.android.googlequicksearchbox");
            intent.putExtra(SearchManager.QUERY, "");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
                browserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browserIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Could not open Google Search", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGoogleVoiceSearch() {
        try {
            Intent intent = new Intent("android.speech.action.WEB_SEARCH");
            intent.setPackage("com.google.android.googlequicksearchbox");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent recIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                recIntent.setPackage("com.google.android.googlequicksearchbox");
                recIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(recIntent);
            } catch (Exception ex) {
                Toast.makeText(this, "Google Voice Search not available", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openGoogleLens() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens"));
            intent.setPackage("com.google.android.googlequicksearchbox");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.ar.lens");
            if (intent != null) {
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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
                    return false;
                }
            });

            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppInfo app = (slot < dockApps.size()) ? dockApps.get(slot) : null;
                    if (app != null) {
                        launchApp(app);
                    } else {
                        Toast.makeText(MainActivity.this, "Drag an app here to place on dock", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    AppInfo app = (slot < dockApps.size()) ? dockApps.get(slot) : null;
                    if (app != null) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        startDragDockApp(slot, app, v);
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

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24f);

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

        int[] screenPos = new int[2];
        anchor.getLocationOnScreen(screenPos);
        int density = (int) getResources().getDisplayMetrics().density;
        int popupW = (int) (236 * density);
        int x = screenPos[0] + (anchor.getWidth() / 2) - (popupW / 2);
        x = Math.max(20, Math.min(x, getScreenWidth() - popupW - 20));
        int y = screenPos[1] - (int) (210 * density);
        if (y < 60) {
            y = screenPos[1] - (int) (160 * density);
        }

        popupWindow.showAtLocation(rootLayout, Gravity.NO_GRAVITY, x, y);
        animatePopIn(popupView);
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

        final AppAdapter pickerAdapter = new AppAdapter(this, appList);
        gv.setAdapter(pickerAdapter);

        int width = (int) (320 * getResources().getDisplayMetrics().density);
        int height = (int) (460 * getResources().getDisplayMetrics().density);
        final PopupWindow pickerPopup = new PopupWindow(dialogView, width, height, true);
        pickerPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pickerPopup.setElevation(28f);

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
        animatePopIn(dialogView);
    }

    private void setupDrawer() {
        appAdapter = new AppAdapter(this, appList);
        gvApps.setAdapter(appAdapter);

        gvApps.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = appAdapter.getItem(position);
                if (app != null) {
                    launchApp(app);
                }
            }
        });

        gvApps.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (drawerController != null && drawerController.isDragging()) return true;
                final AppInfo app = appAdapter.getItem(position);
                if (app == null) return false;
                closeAppDrawer();
                startDragItem(null, new LauncherItem(app), -1, -1);
                return true;
            }
        });

        etDrawerSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (appAdapter != null) {
                    appAdapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        View btnClose = findViewById(R.id.btn_close_drawer);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeAppDrawer();
            }
        });
    }

    private void launchApp(AppInfo app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(app.packageName, app.activityName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
            closeAppDrawer();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadInstalledApps() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                List<ResolveInfo> activities = pm.queryIntentActivities(mainIntent, 0);
                final List<AppInfo> apps = new ArrayList<>();

                for (ResolveInfo ri : activities) {
                    if (ri.activityInfo.packageName.equals(getPackageName())) {
                        continue;
                    }
                    String label = ri.loadLabel(pm).toString();
                    Drawable icon = IconNormalizer.normalizeToPixelCircle(MainActivity.this, ri.loadIcon(pm));
                    String packageName = ri.activityInfo.packageName;
                    String activityName = ri.activityInfo.name;
                    apps.add(new AppInfo(label, packageName, activityName, icon));
                }

                Collections.sort(apps);

                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        appList.clear();
                        appList.addAll(apps);
                        if (appAdapter != null) {
                            appAdapter.updateList(apps);
                        }
                        loadPages();
                        loadDockApps();
                        renderDock();
                    }
                });
            }
        }).start();
    }

    private void openAppDrawer() {
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

    @Override
    public void onBackPressed() {
        if (customizationPanelOverlay != null && customizationPanelOverlay.getVisibility() == View.VISIBLE) {
            hideCustomizationPanel();
            return;
        }
        if (drawerController != null && drawerController.isVisibleOrAnimating()) {
            closeAppDrawer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupAtAGlance();
        if (rootLayout != null) {
            rootLayout.requestApplyInsets();
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
        if (appWidgetHost != null) {
            try {
                appWidgetHost.stopListening();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
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
