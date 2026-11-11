package com.featurestic.loader.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.featurestic.loader.AppConfig;
import com.featurestic.loader.R;
import com.featurestic.loader.auth.PhpAuthManager;
import com.featurestic.loader.sdk.FeaturesticSdkHelper;
import com.featurestic.sdk.FeaturesticBox;

/**
 * Apple iOS 18 Glassmorphic Virtual Box Dashboard
 * - 1-Button Smart Workflow:
 *     [Setup Game] -> Copies/mounts OBB into /SdCard/Android/obb/<pkg> + Clones APK -> Morphs into [Start Game]
 *     [Start Game] -> Launches Game inside FeaturesticBox with 120 FPS + Clean ID + Plugin .so
 */
public class MainActivity extends Activity {

    private static final int STATE_NEEDS_SETUP = 0;
    private static final int STATE_SETTING_UP  = 1;
    private static final int STATE_READY_START = 2;

    private String selectedPackage = AppConfig.PKG_BGMI;
    private int currentButtonState = STATE_NEEDS_SETUP;

    private TextView tvLicenseBadge;
    private TextView btnLogout;

    // Variant Pills
    private TextView pillBgmi;
    private TextView pillGlobal;
    private TextView pillKr;
    private TextView pillVn;

    // Game Info Views
    private ImageView ivGameIcon;
    private TextView tvGameName;
    private TextView tvGamePackage;
    private TextView tvHostStatus;

    // Status Rows
    private View dotCloneStatus;
    private TextView tvCloneStatusValue;
    private View dotObbStatus;
    private TextView tvObbStatusValue;
    private View dotEngineStatus;
    private TextView tvEngineStatusValue;

    // Progress Bar Section
    private ProgressBar progressSetupBar;
    private TextView tvSetupStepDetail;

    // Single Smart Action Button ("Setup Game" -> "Start Game")
    private LinearLayout btnSmartAction;
    private ProgressBar progressSmartButton;
    private TextView tvSmartButtonTitle;
    private TextView tvSmartButtonSubtitle;
    private TextView btnResyncObb;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean hasPromptedPermissionsOnce = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupEdgeToEdgeWindow();
        setContentView(R.layout.activity_main);

        bindViews();
        detectDefaultInstalledGame();
        setupListeners();
        refreshGameStateUI(false);

        View topBar = findViewById(R.id.topHeaderBar);
        View variants = findViewById(R.id.variantScrollContainer);
        View heroCard = findViewById(R.id.gameHeroCard);
        View envCard = findViewById(R.id.envChecklistCard);
        View dock = findViewById(R.id.bottomActionDock);
        AppleAnimationHelper.playEntranceAnimation(topBar, variants, heroCard, envCard, dock);

        // Request Notification / Media / Mic permissions on launch
        FeaturesticSdkHelper.requestRuntimePermissionsOnStart(this);
        checkEssentialPermissionsOnOpen();
    }

    private void checkEssentialPermissionsOnOpen() {
        if (hasPromptedPermissionsOnce) return;
        hasPromptedPermissionsOnce = true;

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                if (!FeaturesticSdkHelper.hasAllFilesPermission(MainActivity.this)) {
                    Toast.makeText(MainActivity.this,
                            "Please allow 'All Files Access' for Virtual OBB & Data Sync (/SdCard/Android/obb)",
                            Toast.LENGTH_LONG).show();
                    FeaturesticSdkHelper.requestAllFilesPermission(MainActivity.this);
                } else if (!FeaturesticSdkHelper.hasOverlayPermission(MainActivity.this)) {
                    Toast.makeText(MainActivity.this,
                            "Please allow 'Display over other apps' (Floating Permission)",
                            Toast.LENGTH_LONG).show();
                    FeaturesticSdkHelper.requestOverlayPermission(MainActivity.this);
                }
            }
        }, 450);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentButtonState != STATE_SETTING_UP) {
            refreshGameStateUI(false);
        }
    }

    private void bindViews() {
        tvLicenseBadge = findViewById(R.id.tvLicenseBadge);
        btnLogout = findViewById(R.id.btnLogout);

        pillBgmi = findViewById(R.id.pillBgmi);
        pillGlobal = findViewById(R.id.pillGlobal);
        pillKr = findViewById(R.id.pillKr);
        pillVn = findViewById(R.id.pillVn);

        ivGameIcon = findViewById(R.id.ivGameIcon);
        tvGameName = findViewById(R.id.tvGameName);
        tvGamePackage = findViewById(R.id.tvGamePackage);
        tvHostStatus = findViewById(R.id.tvHostStatus);

        dotCloneStatus = findViewById(R.id.dotCloneStatus);
        tvCloneStatusValue = findViewById(R.id.tvCloneStatusValue);
        dotObbStatus = findViewById(R.id.dotObbStatus);
        tvObbStatusValue = findViewById(R.id.tvObbStatusValue);
        dotEngineStatus = findViewById(R.id.dotEngineStatus);
        tvEngineStatusValue = findViewById(R.id.tvEngineStatusValue);

        progressSetupBar = findViewById(R.id.progressSetupBar);
        tvSetupStepDetail = findViewById(R.id.tvSetupStepDetail);

        btnSmartAction = findViewById(R.id.btnSmartAction);
        progressSmartButton = findViewById(R.id.progressSmartButton);
        tvSmartButtonTitle = findViewById(R.id.tvSmartButtonTitle);
        tvSmartButtonSubtitle = findViewById(R.id.tvSmartButtonSubtitle);
        btnResyncObb = findViewById(R.id.btnResyncObb);

        String savedExp = PhpAuthManager.getSavedExpiry(this);
        tvLicenseBadge.setText("KEY ACTIVE • " + savedExp);

        AppleAnimationHelper.attachSpringPress(btnSmartAction);
        AppleAnimationHelper.attachSpringPress(btnLogout);
        AppleAnimationHelper.attachSpringPress(btnResyncObb);
    }

    private void setupListeners() {
        View.OnClickListener pillListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentButtonState == STATE_SETTING_UP) return;
                int id = v.getId();
                if (id == R.id.pillBgmi) selectedPackage = AppConfig.PKG_BGMI;
                else if (id == R.id.pillGlobal) selectedPackage = AppConfig.PKG_GLOBAL;
                else if (id == R.id.pillKr) selectedPackage = AppConfig.PKG_KR;
                else if (id == R.id.pillVn) selectedPackage = AppConfig.PKG_VN;
                updateVariantPills();
                refreshGameStateUI(true);
            }
        };
        pillBgmi.setOnClickListener(pillListener);
        pillGlobal.setOnClickListener(pillListener);
        pillKr.setOnClickListener(pillListener);
        pillVn.setOnClickListener(pillListener);

        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PhpAuthManager.clearSession(MainActivity.this);
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });

        btnSmartAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentButtonState == STATE_SETTING_UP) {
                    return;
                }
                if (!FeaturesticSdkHelper.hasAllFilesPermission(MainActivity.this)) {
                    Toast.makeText(MainActivity.this,
                            "Grant All Files Access first so OBB can be synced to /SdCard/Android/obb",
                            Toast.LENGTH_LONG).show();
                    FeaturesticSdkHelper.requestAllFilesPermission(MainActivity.this);
                    return;
                }
                if (currentButtonState == STATE_NEEDS_SETUP) {
                    startVirtualSetupFlow();
                } else if (currentButtonState == STATE_READY_START) {
                    launchGameInVirtualBox();
                }
            }
        });

        btnResyncObb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentButtonState == STATE_SETTING_UP) return;
                FeaturesticSdkHelper.markGameSetupState(MainActivity.this, selectedPackage, false);
                refreshGameStateUI(true);
                startVirtualSetupFlow();
            }
        });
    }

    private void detectDefaultInstalledGame() {
        String[] candidates = {
                AppConfig.PKG_BGMI,
                AppConfig.PKG_GLOBAL,
                AppConfig.PKG_KR,
                AppConfig.PKG_VN
        };
        for (String pkg : candidates) {
            if (FeaturesticSdkHelper.isInstalledOnHost(this, pkg)) {
                selectedPackage = pkg;
                break;
            }
        }
        updateVariantPills();
    }

    private void updateVariantPills() {
        stylePill(pillBgmi, AppConfig.PKG_BGMI.equals(selectedPackage));
        stylePill(pillGlobal, AppConfig.PKG_GLOBAL.equals(selectedPackage));
        stylePill(pillKr, AppConfig.PKG_KR.equals(selectedPackage));
        stylePill(pillVn, AppConfig.PKG_VN.equals(selectedPackage));
    }

    private void stylePill(TextView pill, boolean active) {
        if (active) {
            pill.setBackgroundResource(R.drawable.bg_apple_segment_selected);
            pill.setTextColor(Color.WHITE);
        } else {
            pill.setBackgroundResource(R.drawable.bg_apple_segment_unselected);
            pill.setTextColor(Color.parseColor("#8E8E93"));
        }
    }

    private void refreshGameStateUI(boolean animateButton) {
        PackageManager pm = getPackageManager();
        PackageInfo pi = FeaturesticSdkHelper.findHostGameInfo(this, selectedPackage);
        boolean installedOnHost = (pi != null);
        String versionLabel = "";
        String appTitle = getFriendlyTitle(selectedPackage);

        if (pi != null) {
            versionLabel = "v" + (pi.versionName != null ? pi.versionName : String.valueOf(pi.versionCode));
            try {
                ApplicationInfo ai = pi.applicationInfo;
                if (ai != null) {
                    CharSequence label = pm.getApplicationLabel(ai);
                    if (label != null && label.length() > 0) {
                        appTitle = label.toString();
                    }
                    Drawable icon = pm.getApplicationIcon(ai);
                    if (icon != null) {
                        ivGameIcon.setImageDrawable(icon);
                    }
                }
            } catch (Throwable ignored) {
                ivGameIcon.setImageResource(R.drawable.ic_apple_cube);
            }
        } else {
            ivGameIcon.setImageResource(R.drawable.ic_apple_cube);
        }

        tvGameName.setText(appTitle);
        tvGamePackage.setText(selectedPackage + (versionLabel.isEmpty() ? "" : " • " + versionLabel));

        if (installedOnHost) {
            tvHostStatus.setText("Installed on Device");
            tvHostStatus.setTextColor(Color.parseColor("#30D158"));
            tvHostStatus.setBackgroundResource(R.drawable.bg_apple_pill_success);
        } else {
            tvHostStatus.setText("Host APK Not Found");
            tvHostStatus.setTextColor(Color.parseColor("#FF9F0A"));
            tvHostStatus.setBackgroundResource(R.drawable.bg_apple_pill_warning);
        }

        boolean setupDone = FeaturesticSdkHelper.isGameSetupReady(this, selectedPackage);
        boolean hasVirtObb = FeaturesticSdkHelper.hasVirtualObbFiles(selectedPackage);
        boolean hasHostObb = FeaturesticSdkHelper.hasHostObbFiles(this, selectedPackage);

        if (setupDone) {
            currentButtonState = STATE_READY_START;

            dotCloneStatus.setBackgroundResource(R.drawable.dot_apple_green);
            tvCloneStatusValue.setText("Cloned in Virtual Box");
            tvCloneStatusValue.setTextColor(Color.parseColor("#30D158"));

            dotObbStatus.setBackgroundResource(R.drawable.dot_apple_green);
            tvObbStatusValue.setText(hasVirtObb ? "Synced in /SdCard/Android/obb" : "Direct Host OBB Mount Ready");
            tvObbStatusValue.setTextColor(Color.parseColor("#30D158"));

            dotEngineStatus.setBackgroundResource(R.drawable.dot_apple_blue);
            tvEngineStatusValue.setText("120 FPS • Clean ID Ready");
            tvEngineStatusValue.setTextColor(Color.parseColor("#64D2FF"));

            btnSmartAction.setBackgroundResource(R.drawable.bg_apple_btn_start_game);
            tvSmartButtonTitle.setText(AppConfig.BTN_START_TITLE);
            tvSmartButtonSubtitle.setText(AppConfig.BTN_START_SUBTITLE);
            progressSmartButton.setVisibility(View.GONE);
            progressSetupBar.setVisibility(View.GONE);
            tvSetupStepDetail.setVisibility(View.GONE);
            btnResyncObb.setVisibility(View.VISIBLE);
        } else {
            currentButtonState = STATE_NEEDS_SETUP;

            dotCloneStatus.setBackgroundResource(R.drawable.dot_apple_amber);
            tvCloneStatusValue.setText("Pending Virtual Clone");
            tvCloneStatusValue.setTextColor(Color.parseColor("#FF9F0A"));

            dotObbStatus.setBackgroundResource(R.drawable.dot_apple_amber);
            tvObbStatusValue.setText(hasHostObb ? "Host OBB Found • Tap Setup" : "Ready for OBB Sync");
            tvObbStatusValue.setTextColor(Color.parseColor("#FF9F0A"));

            dotEngineStatus.setBackgroundResource(R.drawable.dot_apple_blue);
            tvEngineStatusValue.setText("120 FPS • Clean ID Standby");
            tvEngineStatusValue.setTextColor(Color.parseColor("#64D2FF"));

            btnSmartAction.setBackgroundResource(R.drawable.bg_apple_btn_setup);
            tvSmartButtonTitle.setText(AppConfig.BTN_SETUP_TITLE);
            tvSmartButtonSubtitle.setText(AppConfig.BTN_SETUP_SUBTITLE);
            progressSmartButton.setVisibility(View.GONE);
            progressSetupBar.setVisibility(View.GONE);
            tvSetupStepDetail.setVisibility(View.GONE);
            btnResyncObb.setVisibility(View.GONE);
        }

        if (animateButton) {
            AppleAnimationHelper.playMorphPop(btnSmartAction);
        }
    }

    /**
     * Executes the complete Virtual Box Setup (APK Clone + OBB Copy/Mount into /SdCard/Android/obb + Engine Prep)
     * and then converts the button into "Start Game".
     */
    private void startVirtualSetupFlow() {
        if (!FeaturesticSdkHelper.isInstalledOnHost(this, selectedPackage)) {
            Toast.makeText(this,
                    getFriendlyTitle(selectedPackage) + " (" + selectedPackage + ") is not installed on your phone! Please install the game first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        currentButtonState = STATE_SETTING_UP;
        btnSmartAction.setBackgroundResource(R.drawable.bg_apple_btn_progress);
        progressSmartButton.setVisibility(View.VISIBLE);
        progressSetupBar.setVisibility(View.VISIBLE);
        tvSetupStepDetail.setVisibility(View.VISIBLE);
        btnResyncObb.setVisibility(View.GONE);

        updateSetupProgress(10, "Step 1/3 • Initializing Virtual Container...",
                "Preparing /SdCard/Android/obb/" + selectedPackage);

        final String targetPkg = selectedPackage;
        FeaturesticSdkHelper.setupGameEnvironment(this, targetPkg, new FeaturesticBox.SetupCallback() {
            @Override
            public void onProgress(int percent, String statusTitle, String statusDetail) {
                updateSetupProgress(percent, statusTitle, statusDetail);
            }

            @Override
            public void onComplete(boolean success, String message) {
                refreshGameStateUI(true);
                Toast.makeText(MainActivity.this,
                        "Setup complete! Tap Start Game to launch.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSetupProgress(int percent, String title, String detail) {
        tvSmartButtonTitle.setText("Setting Up... " + percent + "%");
        tvSmartButtonSubtitle.setText(title);
        progressSetupBar.setProgress(percent);
        tvSetupStepDetail.setText(detail);
    }

    /**
     * Launches the game inside FeaturesticBox when the user taps "Start Game"
     */
    private void launchGameInVirtualBox() {
        if (!FeaturesticSdkHelper.isInstalledOnHost(this, selectedPackage)) {
            Toast.makeText(this,
                    "Please install " + getFriendlyTitle(selectedPackage) + " (" + selectedPackage + ") on your device first.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!FeaturesticSdkHelper.hasVirtualObbFiles(selectedPackage)) {
            Toast.makeText(this,
                    "Syncing OBB into /SdCard/Android/obb/" + selectedPackage + " first...",
                    Toast.LENGTH_SHORT).show();
            startVirtualSetupFlow();
            return;
        }

        tvSmartButtonTitle.setText("Launching Game...");
        tvSmartButtonSubtitle.setText("Starting Featurestic Virtual Engine");
        progressSmartButton.setVisibility(View.VISIBLE);

        boolean launched = FeaturesticSdkHelper.launchVirtualGame(this, selectedPackage);

        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                progressSmartButton.setVisibility(View.GONE);
                tvSmartButtonTitle.setText(AppConfig.BTN_START_TITLE);
                tvSmartButtonSubtitle.setText(AppConfig.BTN_START_SUBTITLE);
            }
        }, 1800);

        if (!launched) {
            Toast.makeText(this, "Initializing virtual container, please tap Start Game again.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFriendlyTitle(String pkg) {
        if (AppConfig.PKG_BGMI.equals(pkg)) return "Battlegrounds Mobile India";
        if (AppConfig.PKG_GLOBAL.equals(pkg)) return "PUBG Mobile Global";
        if (AppConfig.PKG_KR.equals(pkg)) return "PUBG Mobile Korea";
        if (AppConfig.PKG_VN.equals(pkg)) return "PUBG Mobile Vietnam";
        if (AppConfig.PKG_TW.equals(pkg)) return "PUBG Mobile Taiwan";
        return pkg;
    }

    private void setupEdgeToEdgeWindow() {
        Window window = getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.parseColor("#07090E"));
            window.setNavigationBarColor(Color.parseColor("#07090E"));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
    }
}
