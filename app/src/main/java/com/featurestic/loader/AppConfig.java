package com.featurestic.loader;

/**
 * ============================================================================
 * FEATURESTIC BASE LOADER — ALL-IN-ONE CUSTOMIZATION & BRANDING HUB
 * ============================================================================
 * Pre-configured with your ZeroX Panel URL and GitHub Release .so URL.
 */
public final class AppConfig {

    private AppConfig() {}

    // ------------------------------------------------------------------------
    // 1. LOADER BRANDING & UI TEXT
    // ------------------------------------------------------------------------
    public static final String BRAND_NAME        = "Featurestic Box";
    public static final String BRAND_BADGE       = "VIRTUAL ENGINE • ANDROID 10–16";
    public static final String LOGIN_SUBTITLE    = "Sign in with your license key to unlock the virtual container.";
    public static final String WORKSPACE_TITLE   = "Virtual Workspace";

    // Smart Morphing Button Labels (Screen 2)
    public static final String BTN_SETUP_TITLE    = "Setup Game";
    public static final String BTN_SETUP_SUBTITLE = "Clone APK & sync OBB into Virtual Box";
    public static final String BTN_START_TITLE    = "Start Game";
    public static final String BTN_START_SUBTITLE = "Launch inside Featurestic Virtual Box";

    // ------------------------------------------------------------------------
    // 2. LOADER END-USER PHP PANEL (ZORO) & SDK ANTI-PIRACY SETTINGS
    // ------------------------------------------------------------------------
    public static final String PHP_PANEL_URL    = "https://zoro.manishflash.online/connect";
    public static final String SDK_LICENSE_KEY  = "FBVIP-SMC154L690";
    public static final String PHP_GAME_TAG     = "PUBG";

    public static final boolean ALLOW_DEMO_FALLBACK_WHEN_UNCONFIGURED = false;
    public static final String MASTER_TEST_KEY = "FEATURESTIC-VIP";

    // ------------------------------------------------------------------------
    // 3. SUPPORTED GAMES & VIRTUAL ENGINE OPTIONS
    // ------------------------------------------------------------------------
    public static final String PKG_BGMI   = "com.pubg.imobile";
    public static final String PKG_GLOBAL = "com.tencent.ig";
    public static final String PKG_KR     = "com.pubg.krmobile";
    public static final String PKG_VN     = "com.vng.pubgmobile";
    public static final String PKG_TW     = "com.rekoo.pubgm";

    public static final boolean ENABLE_AUTO_120_FPS   = true;
    public static final boolean ENABLE_HIDE_ROOT      = true;
    public static final boolean ENABLE_HIDE_XPOSED    = true;
    public static final boolean ENABLE_LOW_LATENCY    = true;

    /**
     * Plugin .so URL or Local Path:
     * Set to "PANEL" to automatically load the .so configured dynamically in Featurestic Box Panel Settings!
     * OR put a direct GitHub/CDN .so link to load directly from your own repository.
     */
    public static final String PLUGIN_SO_URL_OR_PATH = "PANEL";

    /**
     * Support / Telegram Link:
     */
    public static final String SUPPORT_URL = "https://t.me/";
}
