package com.custom.pixellauncher;

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
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
        List<Integer> widgets = getWidgetIdsForPage(pageIdx);
        if (widgets != null && !widgets.isEmpty()) {
            for (int wId : widgets) {
                try {
                    if (appWidgetManager != null && appWidgetManager.getAppWidgetInfo(wId) != null) {
                        return false;
                    }
                } catch (Exception ignored) {}
            }
        }
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
    }

    private int findFirstEmptySlot(int pageIdx) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return -1;
        return findFirstEmptySlotInList(pages.get(pageIdx));
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

    private HorizontalScrollView hsvHomePages;
    private LinearLayout llHomePages;
    private LinearLayout layoutPageDots;

    private final List<List<LauncherItem>> pages = new ArrayList<>();
    private final List<GridView> pageGridViews = new ArrayList<>();
    private final List<HomeItemAdapter> pageAdapters = new ArrayList<>();
    private final List<LinearLayout> pageWidgetContainers = new ArrayList<>();
    private int currentPage = 0;

    private static final String KEY_DOCK_JSON = "dock_apps_json";
    private final List<AppInfo> dockApps = new ArrayList<>(5);
    private final FrameLayout[] dockSlots = new FrameLayout[5];
    private final ImageView[] dockIcons = new ImageView[5];

    private static final int APPWIDGET_HOST_ID = 2048;
    private static final int REQUEST_BIND_APPWIDGET = 7777;
    private static final int REQUEST_PICK_APPWIDGET = 102;
    private static final int REQUEST_CREATE_APPWIDGET = 103;
    private static final String PREF_GOOGLE_WIDGET_ID = "google_search_widget_id";
    private AppWidgetManager appWidgetManager;
    private AppWidgetHost appWidgetHost;
    private AppWidgetHostView searchWidgetHostView;

    private AppAdapter appAdapter;
    private final List<AppInfo> appList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GestureDetector gestureDetector;
    private float startY = 0;
    private float startX = 0;
    private boolean isTrackingSwipe = false;

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
    private float windowScaleX = 0.68f;
    private float windowScaleY = 0.82f;

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
        setupGestures();
        setupAtAGlance();
        setupSearchPill();
        setupDock();
        setupDrawer();
        setupCustomizationPanel();

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

        hsvHomePages.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    snapToNearestPage();
                }
                return false;
            }
        });

        hsvHomePages.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                int width = getScreenWidth();
                if (width > 0 && pages.size() > 0) {
                    int activePage = Math.round((float) scrollX / width);
                    if (activePage >= 0 && activePage < pages.size() && activePage != currentPage) {
                        currentPage = activePage;
                        updatePageDots(activePage);
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
        pageWidgetContainers.clear();

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

            final LinearLayout pageContent = new LinearLayout(this);
            pageContent.setOrientation(LinearLayout.VERTICAL);
            pageContent.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            final LinearLayout widgetsArea = new LinearLayout(this);
            widgetsArea.setOrientation(LinearLayout.VERTICAL);
            widgetsArea.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final GridView gv = new GridView(this);
            LinearLayout.LayoutParams lpGv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
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
                    return event.getAction() == MotionEvent.ACTION_MOVE;
                }
            });

            final HomeItemAdapter adapter = new HomeItemAdapter(this, pages.get(pageIdx), estimatedCellH);
            adapter.setGridDensity(getGridColumns(), getGridRows());
            gv.setAdapter(adapter);

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
                }
            });

            pageGridViews.add(gv);
            pageAdapters.add(adapter);
            pageContainers.add(pageContainer);
            pageWidgetContainers.add(widgetsArea);

            gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (pageIdx < pageAdapters.size()) {
                        LauncherItem item = pageAdapters.get(pageIdx).getItem(position);
                        if (item != null) {
                            if (item.type == LauncherItem.TYPE_FOLDER) {
                                showFolderPopup(item.folderInfo, pageIdx, position);
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
                    if (isDragActive || isWindowModeActive) return true;
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

            pageContent.addView(widgetsArea);
            pageContent.addView(gv);
            pageContainer.addView(pageContent);
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

    private void snapToNearestPage() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int scrollX = hsvHomePages.getScrollX();
                int width = getScreenWidth();
                int targetPage = Math.round((float) scrollX / width);
                if (targetPage < 0) targetPage = 0;
                if (targetPage >= pages.size()) targetPage = pages.size() - 1;
                scrollToPage(targetPage);
            }
        }, 100);
    }

    private void scrollToPage(int pageIdx) {
        if (pageIdx < 0 || pageIdx >= pages.size()) return;
        currentPage = pageIdx;
        int targetX = pageIdx * getScreenWidth();
        hsvHomePages.smoothScrollTo(targetX, 0);
        updatePageDots(pageIdx);
        updateActivePageWindow(pageIdx);
        updateDragPageChips(pageIdx);
    }

    private void updatePageDots(int activePage) {
        layoutPageDots.removeAllViews();
        if (pages.size() <= 1) {
            layoutPageDots.setVisibility(View.GONE);
            return;
        }
        layoutPageDots.setVisibility(View.VISIBLE);
        int density = (int) getResources().getDisplayMetrics().density;

        for (int i = 0; i < pages.size(); i++) {
            final int pageIdx = i;
            ImageView dot = new ImageView(this);
            int size = (i == activePage) ? 8 * density : 6 * density;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(4 * density, 0, 4 * density, 0);
            dot.setLayoutParams(lp);
            dot.setImageResource(i == activePage ? R.drawable.dot_active : R.drawable.dot_inactive);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollToPage(pageIdx);
                }
            });
            layoutPageDots.addView(dot);
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
        dragStartX = lastTouchRawX;
        dragStartY = lastTouchRawY;
        dragSourcePage = pageIdx;
        dragSourcePosition = position;
        dragSourceView = view;
        draggedItem = item;

        Log.d("PixelDrag", "startDragItem: item=" + item.getLabel() + " page=" + pageIdx + " pos=" + position);

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

                    if (dragSourceView != null) {
                        dragSourceView.setAlpha(0.25f);
                    }

                    if (ivFloatingDrag != null && draggedItem != null) {
                        ivFloatingDrag.setImageDrawable(draggedItem.getIcon(this));
                        ViewGroup.LayoutParams lp = ivFloatingDrag.getLayoutParams();
                        lp.width = iconSize;
                        lp.height = iconSize;
                        ivFloatingDrag.setLayoutParams(lp);

                        ivFloatingDrag.setX(ev.getRawX() - halfSize);
                        ivFloatingDrag.setY(ev.getRawY() - halfSize - offsetY);
                        ivFloatingDrag.setScaleX(1.15f);
                        ivFloatingDrag.setScaleY(1.15f);
                    }

                    if (dropTargetRemove != null) {
                        dropTargetRemove.setBackgroundResource(R.drawable.drop_target_bg);
                        dropTargetRemove.setScaleX(1.0f);
                        dropTargetRemove.setScaleY(1.0f);
                    }
                    if (dropTargetUninstall != null) {
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

                        // Fast, snappy edge scrolling (220ms debounce, 85dp margin)
                        long now = System.currentTimeMillis();
                        int screenW = getScreenWidth();
                        if (now - lastEdgeScrollTime > 220) {
                            if (ev.getRawX() > screenW - (85 * density)) {
                                if (currentPage < pages.size() - 1) {
                                    scrollToPage(currentPage + 1);
                                    lastEdgeScrollTime = now;
                                } else if (pages.size() < 7) {
                                    int newPageIdx = addNewPage();
                                    scrollToPage(newPageIdx);
                                    lastEdgeScrollTime = now;
                                }
                            } else if (ev.getRawX() < (85 * density) && currentPage > 0) {
                                scrollToPage(currentPage - 1);
                                lastEdgeScrollTime = now;
                            }
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

    private void detectAndHighlightHoverTarget(float rawX, float rawY) {
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
            LauncherItem targetItem = currPageList.get(pos);
            if (targetItem != null) {
                boolean isSameItem = (currentPage == dragSourcePage && pos == dragSourcePosition);
                if (!isSameItem) {
                    int childIndex = pos - gv.getFirstVisiblePosition();
                    View targetChild = gv.getChildAt(childIndex);
                    if (targetChild != null && targetChild != currentHoverView) {
                        clearHoverHighlight();
                        currentHoverView = targetChild;
                        currentHoverView.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).start();
                    }
                    return;
                }
            }
        }
        clearHoverHighlight();
    }

    private void clearHoverHighlight() {
        if (currentHoverView != null) {
            currentHoverView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
            currentHoverView = null;
        }
    }

    private void endDrag(float rawX, float rawY) {
        clearHoverHighlight();

        if (llDropTargetsBar != null) {
            llDropTargetsBar.setVisibility(View.GONE);
        }

        if (dragSourceView != null) {
            dragSourceView.setAlpha(1.0f);
        }
        if (dragOverlay != null) {
            dragOverlay.setVisibility(View.GONE);
        }

        isDragActive = false;

        if (!dragMoved) {
            Log.d("PixelDrag", "endDrag without move: showing popup directly without blur");
            if (dragSourcePage == -1 && draggedItem != null) {
                addItemToCurrentPage(draggedItem);
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

        if (llDropTargetsBar != null) {
            llDropTargetsBar.setVisibility(View.GONE);
        }

        if (dragSourceView != null) {
            dragSourceView.setAlpha(1.0f);
        }
        if (dragOverlay != null) {
            dragOverlay.setVisibility(View.GONE);
        }

        isDragActive = false;

        if (!dragMoved) {
            Log.d("PixelDrag", "endDrag without move: showing popup");
            if (dragSourcePage == -1 && draggedItem != null) {
                addItemToCurrentPage(draggedItem);
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

        if (draggedItem == null) return;

        // Check if dropped onto Remove target (can or remove from home screen)
        if (activeDropZone == DROP_ZONE_REMOVE || isTouchInsideView(dropTargetRemove, rawX, rawY)) {
            if (dragSourcePage >= 0 && dragSourcePosition >= 0 && dragSourcePage < pages.size()) {
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

        // Check if dropped onto Uninstall target (Android 17 uninstall)
        if (activeDropZone == DROP_ZONE_UNINSTALL || isTouchInsideView(dropTargetUninstall, rawX, rawY)) {
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
                setDockApp(slot, draggedItem.appInfo);
                Toast.makeText(this, "Set " + draggedItem.getLabel() + " to Dock slot " + (slot + 1), Toast.LENGTH_SHORT).show();
            } else if (draggedItem.type == LauncherItem.TYPE_FOLDER) {
                Toast.makeText(this, "Dock slots only support apps", Toast.LENGTH_SHORT).show();
            }
            pruneEmptyTrailingPages();
            savePages();
            refreshAllPages();
            return;
        }

        // Check if dropped onto page chips (1 2 3 4 5....)
        int droppedChipIdx = -1;
        for (int c = 0; c < dragChipViews.size(); c++) {
            if (isTouchInsideView(dragChipViews.get(c), rawX, rawY)) {
                droppedChipIdx = c;
                break;
            }
        }
        if (droppedChipIdx >= 0 && droppedChipIdx < pages.size()) {
            int targetSlot = findFirstEmptySlot(droppedChipIdx);
            if (targetSlot != -1) {
                if (dragSourcePage >= 0 && dragSourcePosition >= 0 && dragSourcePage < pages.size()) {
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
            if (dragSourcePage >= 0 && dragSourcePosition >= 0 && dragSourcePage < pages.size()) {
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

        if (dropPos < 0 || dropPos >= currPageList.size()) {
            dropPos = findFirstEmptySlot(currentPage);
            if (dropPos == -1) {
                int np = addNewPage();
                currentPage = np;
                dropPos = 0;
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
            if (targetItem.type == LauncherItem.TYPE_FOLDER) {
                addAppToFolder(currentPage, dropPos, targetItem, draggedItem);
                return;
            } else if (targetItem.type == LauncherItem.TYPE_APP && draggedItem.type == LauncherItem.TYPE_APP) {
                createFolderWithItems(currentPage, dropPos, targetItem, draggedItem);
                return;
            }
        }

        // Dropped on empty slot
        if (dragSourcePage >= 0 && dragSourcePage < pages.size()) {
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

    private void showFolderPopup(final FolderInfo folder, final int pageIdx, final int itemIdx) {
        LayoutInflater inflater = LayoutInflater.from(this);
        final View dialogView = inflater.inflate(R.layout.popup_folder_view, null);

        final EditText etTitle = dialogView.findViewById(R.id.et_folder_title);
        etTitle.setText(folder.name);

        GridView gv = dialogView.findViewById(R.id.gv_folder_apps);
        final AppAdapter adapter = new AppAdapter(this, folder.apps);
        gv.setAdapter(adapter);

        final PopupWindow popupWindow = new PopupWindow(dialogView,
                (int) (310 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(28f);

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

        popupWindow.showAtLocation(rootLayout, Gravity.CENTER, 0, 0);
        animatePopIn(dialogView);
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

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 50;
            private static final int SWIPE_THRESHOLD_VELOCITY = 120;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();

                if (Math.abs(dy) > Math.abs(dx) * 1.2f && Math.abs(dy) > SWIPE_MIN_DISTANCE && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
                    if (dy < 0) {
                        if (appDrawer.getVisibility() != View.VISIBLE) {
                            openAppDrawer();
                            return true;
                        }
                    } else {
                        if (appDrawer.getVisibility() == View.VISIBLE) {
                            if (e1.getY() < 650 || gvApps.getFirstVisiblePosition() == 0) {
                                closeAppDrawer();
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouchRawX = ev.getRawX();
        lastTouchRawY = ev.getRawY();

        if (isDragActive) {
            handleDragTouchEvent(ev);
            return true;
        }

        if (gestureDetector != null && gestureDetector.onTouchEvent(ev)) {
            return true;
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getY();
                startX = ev.getX();
                isTrackingSwipe = true;
                break;

            case MotionEvent.ACTION_MOVE:
                float moveDx = ev.getX() - startX;
                float moveDy = ev.getY() - startY;
                if (Math.abs(moveDx) > Math.abs(moveDy) * 1.5f && Math.abs(moveDx) > 80) {
                    isTrackingSwipe = false;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (isTrackingSwipe) {
                    float totalDy = ev.getY() - startY;
                    float totalDx = ev.getX() - startX;
                    if (Math.abs(totalDy) > Math.abs(totalDx) * 1.3f && Math.abs(totalDy) > 70) {
                        if (totalDy < 0) {
                            if (appDrawer.getVisibility() != View.VISIBLE) {
                                openAppDrawer();
                                isTrackingSwipe = false;
                                return true;
                            }
                        } else {
                            if (appDrawer.getVisibility() == View.VISIBLE) {
                                if (startY < 650 || gvApps.getFirstVisiblePosition() == 0) {
                                    closeAppDrawer();
                                    isTrackingSwipe = false;
                                    return true;
                                }
                            }
                        }
                    }
                }
                isTrackingSwipe = false;
                break;

            case MotionEvent.ACTION_CANCEL:
                isTrackingSwipe = false;
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    private void setupAtAGlance() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM d", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        tvDate.setText(currentDate);

        View atAGlance = findViewById(R.id.at_a_glance);
        atAGlance.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCalendar();
            }
        });
        atAGlance.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!isDragActive && !isWindowModeActive) {
                    showCustomizationPanel();
                }
                return true;
            }
        });
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
                    showWidgetMenu();
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
                    showHomeSettingsDialog();
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

    private void showHomeSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Grid & Icon Size");
        String[] options = new String[]{
                "4 x 5 Pixel Standard (Large 58dp Icons)" + (currentGridMode == GRID_MODE_4X5 ? " (Active)" : ""),
                "5 x 5 Pixel Expressive (Medium 50dp Icons)" + (currentGridMode == GRID_MODE_5X5 ? " (Active)" : ""),
                "16 x 9 Ultra Dense (Compact 34dp Icons)" + (currentGridMode == GRID_MODE_16X9 ? " (Active)" : ""),
                "System Settings"
        };
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    setGridMode(GRID_MODE_4X5);
                } else if (which == 1) {
                    setGridMode(GRID_MODE_5X5);
                } else if (which == 2) {
                    setGridMode(GRID_MODE_16X9);
                } else if (which == 3) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_SETTINGS);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                }
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
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

    private void completeAddWidget(int appWidgetId) {
        saveWidgetIdForPage(currentPage, appWidgetId);
        renderWidgetsForPage(currentPage);
        Toast.makeText(this, "Widget added to Page " + (currentPage + 1), Toast.LENGTH_SHORT).show();
    }

    private List<Integer> getWidgetIdsForPage(int pageIdx) {
        List<Integer> list = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGE_WIDGETS_JSON, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (obj.optInt("page", -1) == pageIdx) {
                        list.add(obj.optInt("id"));
                    }
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    private void saveWidgetIdForPage(int pageIdx, int widgetId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGE_WIDGETS_JSON, null);
        JSONArray arr = new JSONArray();
        try {
            if (json != null) arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("page", pageIdx);
            obj.put("id", widgetId);
            arr.put(obj);
            prefs.edit().putString(KEY_PAGE_WIDGETS_JSON, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void deleteWidgetIdFromPage(int pageIdx, int widgetId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = prefs.getString(KEY_PAGE_WIDGETS_JSON, null);
        if (json != null) {
            try {
                JSONArray arr = new JSONArray(json);
                JSONArray newArr = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    if (!(obj.optInt("page", -1) == pageIdx && obj.optInt("id", -1) == widgetId)) {
                        newArr.put(obj);
                    }
                }
                prefs.edit().putString(KEY_PAGE_WIDGETS_JSON, newArr.toString()).apply();
            } catch (Exception ignored) {}
        }
    }

    private void showWidgetMenu() {
        final List<Integer> currWidgets = getWidgetIdsForPage(currentPage);
        if (currWidgets.isEmpty()) {
            openWidgetsPicker();
            return;
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Widgets (Page " + (currentPage + 1) + ")");
        String[] options = new String[]{
                "Add new widget...",
                "Remove widget from Page " + (currentPage + 1) + " (" + currWidgets.size() + " active)"
        };
        b.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    openWidgetsPicker();
                } else if (which == 1) {
                    showRemoveWidgetPicker(currentPage);
                }
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void showRemoveWidgetPicker(final int pageIdx) {
        final List<Integer> widgetIds = getWidgetIdsForPage(pageIdx);
        if (widgetIds.isEmpty()) {
            Toast.makeText(this, "No widgets on this page", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] labels = new String[widgetIds.size()];
        for (int i = 0; i < widgetIds.size(); i++) {
            AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetIds.get(i));
            labels[i] = (info != null && info.label != null && !info.label.isEmpty()) ? info.label : ("Widget #" + widgetIds.get(i));
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Select widget to remove");
        b.setItems(labels, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                removeWidgetFromPage(pageIdx, widgetIds.get(which));
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void renderWidgetsForPage(final int pageIdx) {
        if (pageIdx < 0 || pageIdx >= pageWidgetContainers.size()) return;
        LinearLayout container = pageWidgetContainers.get(pageIdx);
        if (container == null) return;
        container.removeAllViews();

        List<Integer> widgetIds = getWidgetIdsForPage(pageIdx);
        float density = getResources().getDisplayMetrics().density;

        for (final int widgetId : widgetIds) {
            try {
                AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
                if (info == null) continue;

                AppWidgetHostView hostView = appWidgetHost.createView(this, widgetId, info);
                hostView.setAppWidget(widgetId, info);

                FrameLayout wrapper = new FrameLayout(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins((int) (8 * density), (int) (4 * density), (int) (8 * density), (int) (4 * density));
                wrapper.setLayoutParams(lp);

                FrameLayout.LayoutParams hostLp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                hostView.setLayoutParams(hostLp);
                wrapper.addView(hostView);

                final String label = (info.label != null && !info.label.isEmpty()) ? info.label : "Widget";

                // Subtle sleek remove button at the top-right of the widget
                final ImageView ivRemove = new ImageView(this);
                FrameLayout.LayoutParams lpRemove = new FrameLayout.LayoutParams((int) (26 * density), (int) (26 * density));
                lpRemove.gravity = Gravity.TOP | Gravity.END;
                lpRemove.topMargin = (int) (4 * density);
                lpRemove.rightMargin = (int) (4 * density);
                ivRemove.setLayoutParams(lpRemove);
                ivRemove.setImageResource(R.drawable.ic_remove);
                ivRemove.setBackgroundResource(R.drawable.page_chip_inactive_bg);
                ivRemove.setPadding((int) (5 * density), (int) (5 * density), (int) (5 * density), (int) (5 * density));
                ivRemove.setElevation(14f);
                ivRemove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showWidgetRemoveDialog(pageIdx, widgetId, label);
                    }
                });
                wrapper.addView(ivRemove);

                View.OnLongClickListener removeListener = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        showWidgetRemoveDialog(pageIdx, widgetId, label);
                        return true;
                    }
                };

                hostView.setOnLongClickListener(removeListener);
                wrapper.setOnLongClickListener(removeListener);

                container.addView(wrapper);
            } catch (Exception ignored) {}
        }
    }

    private void renderAllSavedWidgets() {
        for (int i = 0; i < pageWidgetContainers.size(); i++) {
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
        deleteWidgetIdFromPage(pageIdx, widgetId);
        renderWidgetsForPage(pageIdx);
        pruneEmptyTrailingPages();
        savePages();
        refreshAllPages();
        Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show();
    }

    private void enterPageWindowMode() {
        isWindowModeActive = true;
        float density = getResources().getDisplayMetrics().density;

        if (dragBlurOverlay != null) {
            dragBlurOverlay.setVisibility(View.VISIBLE);
            dragBlurOverlay.setAlpha(0f);
            dragBlurOverlay.animate().alpha(1.0f).setDuration(220).start();
        }

        if (hsvDragPageChips != null) {
            updateDragPageChips(currentPage);
            hsvDragPageChips.setVisibility(View.VISIBLE);
            hsvDragPageChips.setAlpha(0f);
            hsvDragPageChips.setTranslationY(20f * density);
            hsvDragPageChips.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        for (TextView badge : pageCardBadges) {
            badge.setVisibility(View.VISIBLE);
            badge.setAlpha(0f);
            badge.animate().alpha(1f).setDuration(200).start();
        }

        applyBackgroundBlur(true);

        // Uniform 0.85x scaling for natural, beautiful page window cards
        windowScaleX = 0.85f;
        windowScaleY = 0.85f;

        for (int i = 0; i < pageContainers.size(); i++) {
            FrameLayout container = pageContainers.get(i);
            if (i == currentPage) {
                container.setBackgroundResource(R.drawable.window_page_active_bg);
            } else {
                container.setBackgroundResource(R.drawable.window_page_bg);
            }
            container.setElevation(10 * density);
            container.animate()
                    .scaleX(windowScaleX)
                    .scaleY(windowScaleY)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void updateActivePageWindow(int activeIndex) {
        if (!isWindowModeActive) return;
        for (int i = 0; i < pageContainers.size(); i++) {
            FrameLayout container = pageContainers.get(i);
            if (i == activeIndex) {
                container.setBackgroundResource(R.drawable.window_page_active_bg);
            } else {
                container.setBackgroundResource(R.drawable.window_page_bg);
            }
        }
    }

    private void exitPageWindowMode() {
        if (!isWindowModeActive) return;
        isWindowModeActive = false;
        float density = getResources().getDisplayMetrics().density;

        if (dragBlurOverlay != null) {
            dragBlurOverlay.animate().alpha(0f).setDuration(200).withEndAction(new Runnable() {
                @Override
                public void run() {
                    dragBlurOverlay.setVisibility(View.GONE);
                }
            }).start();
        }

        if (hsvDragPageChips != null) {
            hsvDragPageChips.animate()
                    .alpha(0f)
                    .translationY(20f * density)
                    .setDuration(180)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            hsvDragPageChips.setVisibility(View.GONE);
                        }
                    }).start();
        }

        for (final TextView badge : pageCardBadges) {
            badge.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
                @Override
                public void run() {
                    badge.setVisibility(View.GONE);
                }
            }).start();
        }

        applyBackgroundBlur(false);

        for (int i = 0; i < pageContainers.size(); i++) {
            final FrameLayout container = pageContainers.get(i);
            container.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
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
        if (Build.VERSION.SDK_INT >= 31 && homeScreen != null) {
            try {
                if (enable) {
                    Class<?> renderEffectClass = Class.forName("android.graphics.RenderEffect");
                    Class<?> tileModeClass = Class.forName("android.graphics.Shader$TileMode");
                    Object clampTileMode = Enum.valueOf((Class<Enum>) tileModeClass, "CLAMP");
                    Method createBlurMethod = renderEffectClass.getMethod("createBlurEffect", float.class, float.class, tileModeClass);
                    Object blurEffect = createBlurMethod.invoke(null, 25f, 25f, clampTileMode);
                    Method setRenderEffectMethod = View.class.getMethod("setRenderEffect", renderEffectClass);
                    setRenderEffectMethod.invoke(homeScreen, blurEffect);
                } else {
                    Method setRenderEffectMethod = View.class.getMethod("setRenderEffect", Class.forName("android.graphics.RenderEffect"));
                    setRenderEffectMethod.invoke(homeScreen, (Object) null);
                }
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
                if (searchWidgetHostView != null) {
                    container.removeView(searchWidgetHostView);
                }
                searchWidgetHostView = appWidgetHost.createView(this, widgetId, widgetInfo);
                searchWidgetHostView.setAppWidget(widgetId, widgetInfo);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                searchWidgetHostView.setLayoutParams(lp);

                View fallbackPill = findViewById(R.id.pixel_search_bar);
                if (fallbackPill != null) {
                    fallbackPill.setVisibility(View.GONE);
                }
                container.addView(searchWidgetHostView);
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

            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppInfo app = (slot < dockApps.size()) ? dockApps.get(slot) : null;
                    if (app != null) {
                        launchApp(app);
                    } else {
                        showAppPickerDialog(slot);
                    }
                }
            });

            iv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    AppInfo app = (slot < dockApps.size()) ? dockApps.get(slot) : null;
                    showDockOptionsPopup(v, slot, app);
                    return true;
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
                iv.setAlpha(1.0f);
                iv.setContentDescription(app.label);
            } else {
                iv.setImageResource(R.drawable.ic_pin);
                iv.setAlpha(0.25f);
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
                    Drawable icon = ri.loadIcon(pm);
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
        if (appDrawer.getVisibility() == View.VISIBLE) return;
        int screenH = getScreenHeight();

        appDrawer.setVisibility(View.VISIBLE);
        appDrawer.setTranslationY(screenH);
        appDrawer.setAlpha(1f);

        appDrawer.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();

        homeScreen.animate()
                .scaleX(0.93f)
                .scaleY(0.93f)
                .alpha(0.3f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (appDrawer.getVisibility() == View.VISIBLE) {
                            homeScreen.setVisibility(View.INVISIBLE);
                        }
                    }
                })
                .start();

        etDrawerSearch.setText("");
    }

    private void closeAppDrawer() {
        if (appDrawer.getVisibility() != View.VISIBLE) return;
        hideKeyboard(etDrawerSearch);
        int screenH = getScreenHeight();

        homeScreen.setVisibility(View.VISIBLE);
        homeScreen.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        appDrawer.animate()
                .translationY(screenH)
                .setDuration(280)
                .setInterpolator(new AccelerateInterpolator(1.3f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        appDrawer.setVisibility(View.GONE);
                        etDrawerSearch.setText("");
                    }
                })
                .start();
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
    public void onBackPressed() {
        if (customizationPanelOverlay != null && customizationPanelOverlay.getVisibility() == View.VISIBLE) {
            hideCustomizationPanel();
            return;
        }
        if (appDrawer.getVisibility() == View.VISIBLE) {
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
