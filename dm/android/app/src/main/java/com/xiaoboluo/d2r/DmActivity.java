// 安卓外壳：一个全屏横屏的 SurfaceView，原生层（libdm.so，dm/android/jni_android.c）跑宿主与游戏。
// 对应 iOS 的 main.m（启动链）与 front_ios.m（视图、触屏、生命周期）；本类只做系统对接，游戏逻辑与界面都在原生层。
// Android shell: a full-screen landscape SurfaceView; the native layer (libdm.so, dm/android/jni_android.c) runs the host and the game.
// Counterpart of iOS's main.m (startup chain) and front_ios.m (view, touch, lifecycle); this class only does system integration, game logic and UI are all in the native layer.
package com.xiaoboluo.d2r;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.system.ErrnoException;
import android.system.Os;
import android.text.Editable;
import android.text.InputType;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.inputmethod.BaseInputConnection;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Button;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class DmActivity extends Activity implements SurfaceHolder.Callback, DisplayManager.DisplayListener {
    private boolean original133;
    static native void nativePad(long buttons, int lx, int ly, int rx, int ry);
    private final GamepadInput gamepad = new GamepadInput(DmActivity::nativePad);
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (started && inputDialog == null && gameFrame.getParent() != null && gamepad.key(event)) return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onGenericMotionEvent(android.view.MotionEvent event) {
        if (started && inputDialog == null && gameFrame.getParent() != null && gamepad.motion(event)) return true;
        return super.onGenericMotionEvent(event);
    }
    static native int nativeStart(String filesDir, String extDir, String romfsSrc, String lang, String runtimeDir);
    static native void nativeSurface(Object surface, int w, int h);
    static native void nativeRefresh(float hz);
    static native void nativeTouch(int n, int[] ids, float[] xs, float[] ys);
    static native void nativeFocus(int ev);
    static native void nativeTextResult(long id, boolean accepted, String text);
    // 越肩镜头的体感转向（CamSensor）：开哪种传感器（0 = 不开），每次转动的弧度
    // Sensor steering of the over-the-shoulder camera (CamSensor): which sensor to listen to (0 = none), each turn in radians
    static native int nativeCamSensor();
    static native void nativeCamTurn(float rad);
    // 镜头档（nativeStart 之后才准）与「此刻在局里、镜头在出图」（CamButtons）
    // The camera mode (valid only after nativeStart) and "in a game right now with the camera drawing" (CamButtons)
    static native int nativeCamMode();
    static native int nativeCamActive();
    // 画面外的怪（CamThreats）：每条 5 个数，返回条数
    // Monsters off the picture (CamThreats): 5 numbers each, returns the count
    static native int nativeCamThreats(float[] out);
    // 小地图上的朝向箭头（CamThreats）：4 个数，返回 1 = 该画
    // Heading arrow on the minimap (CamThreats): 4 numbers, returns 1 = draw it
    static native int nativeCamHeading(float[] out);

    private static WeakReference<DmActivity> current = new WeakReference<>(null);
    private static volatile int bootFailure;
    private AlertDialog inputDialog;

    private SurfaceView view;
    private FrameLayout gameFrame;
    private TouchOverlay touchOverlay;
    private boolean started;
    private boolean libraryLoaded;
    private boolean resumed;
    private boolean pickingFolder;
    private boolean folderPendingRestart;
    private static final int PICK_GAME_FOLDER = 40;
    private DisplayManager displays;
    private CamSensor camSensor;
    private CamButtons camButtons;
    private CamThreats camThreats;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 调试包：am start … --ez dmArtDump true 只把触屏控件底图写到 files/dm/artdump/ 就退出（dm/android/tools/art_compare.sh 用）
        // Debug builds: am start … --ez dmArtDump true only writes the touch control art to files/dm/artdump/ and exits (used by dm/android/tools/art_compare.sh)
        if (BuildConfig.DEBUG && getIntent().getBooleanExtra("dmArtDump", false)) {
            try { new TouchOverlay(this, null, null).dumpArt(new File(getFilesDir(), "dm/artdump")); } catch (IOException e) { android.util.Log.e("dm", "art dump: " + e); }
            finish(); return;
        }
        current = new WeakReference<>(this);
        displays = getSystemService(DisplayManager.class);
        if (displays != null) displays.registerDisplayListener(this, null);
        camSensor = new CamSensor(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        view = new SurfaceView(this);
        view.getHolder().addCallback(this);
        gameFrame = new FrameLayout(this);
        gameFrame.addView(view, new FrameLayout.LayoutParams(-1, -1));
        setContentView(gameFrame);
        immersive();
    }

    private void immersive() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) { c.hide(WindowInsets.Type.systemBars()); c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }
    }

    // 资源在玩家放的位置读（缺省 Download/S2X/D2R Mobile/data），要「所有文件访问」权限；没有就引导去系统设置页，回来再启动
    // Resources are read where the player put them (default Download/S2X/D2R Mobile/data), which needs "all files access"; without it, guide the player to the system settings page and start after returning
    private boolean ensureFileAccess() {
        if (Environment.isExternalStorageManager()) return true;
        TextView t = new TextView(this);
        t.setText(R.string.dmAndroidPermission);
        t.setTextSize(20); t.setPadding(64, 64, 64, 64);
        t.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))));
        setContentView(t);
        return false;
    }

    // 系统文件夹选择器只接受本地卷；原生层需要可 seek / mmap 的真实路径。
    // The system picker accepts local volumes; the native host needs real paths for seek and mmap.
    private void pickGameFolder() {
        pickingFolder = true;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        String previous = getPreferences(MODE_PRIVATE).getString("game_tree_uri", null);
        if (previous != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(previous));
        else {
            StorageManager storage = getSystemService(StorageManager.class);
            if (storage != null) for (StorageVolume volume : storage.getStorageVolumes()) {
                if (volume.isRemovable() && volume.getDirectory() != null && volume.getUuid() != null) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", volume.getUuid() + ":"));
                    break;
                }
            }
        }
        try { startActivityForResult(intent, PICK_GAME_FOLDER); }
        catch (android.content.ActivityNotFoundException e) { pickingFolder = false; showStartError(getString(R.string.dmAndroidFolderUnsupported)); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_GAME_FOLDER) return;
        pickingFolder = false;
        if (result != RESULT_OK || data == null || data.getData() == null) { showFolderScreen(getString(R.string.dmAndroidChooseFolderHint)); return; }
        Uri uri = data.getData();
        try {
            if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) throw new IOException("Non-local provider");
            String[] id = DocumentsContract.getTreeDocumentId(uri).split(":", 2);
            if (id.length != 2) throw new IOException("Invalid document ID");
            StorageManager storage = getSystemService(StorageManager.class);
            File volumeRoot = null;
            if (storage != null) for (StorageVolume volume : storage.getStorageVolumes()) {
                if (("primary".equals(id[0]) && volume.isPrimary()) || id[0].equalsIgnoreCase(volume.getUuid())) volumeRoot = volume.getDirectory();
            }
            if (volumeRoot == null) throw new IOException("Unavailable volume");
            File folder = GamePaths.treePath(volumeRoot, id[1]);
            if (GamePaths.dataDirectory(folder) == null) { showFolderScreen(getString(R.string.dmAndroidFolderInvalid)); return; }
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getPreferences(MODE_PRIVATE).edit().putString("game_folder", folder.getAbsolutePath()).putString("game_tree_uri", uri.toString()).apply();
            if (libraryLoaded) { folderPendingRestart = true; showFolderScreen(getString(R.string.dmAndroidFolderRestart)); }
            else startHost();
        } catch (IOException | IllegalArgumentException | SecurityException e) { showFolderScreen(getString(R.string.dmAndroidFolderUnsupported)); }
    }

    private void showFolderScreen(String message) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(64, 48, 64, 48);
        TextView text = new TextView(this); text.setTextSize(20); text.setText(message); panel.addView(text);
        String selected = getPreferences(MODE_PRIVATE).getString("game_folder", null);
        if (selected != null) { TextView path = new TextView(this); path.setText(getString(R.string.dmAndroidSelectedFolder, selected)); path.setTextIsSelectable(true); panel.addView(path); }
        Button choose = new Button(this); choose.setText(R.string.dmAndroidChooseFolder); choose.setOnClickListener(v -> pickGameFolder()); panel.addView(choose);
        if (selected != null) {
            Button forget = new Button(this); forget.setText(R.string.dmAndroidForgetFolder);
            forget.setOnClickListener(v -> {
                String uri = getPreferences(MODE_PRIVATE).getString("game_tree_uri", null);
                if (uri != null) try { getContentResolver().releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (SecurityException ignored) { }
                getPreferences(MODE_PRIVATE).edit().remove("game_folder").remove("game_tree_uri").apply();
                folderPendingRestart = libraryLoaded;
                showFolderScreen(getString(folderPendingRestart ? R.string.dmAndroidFolderRestart : R.string.dmAndroidChooseFolderHint));
            }); panel.addView(forget);
        }
        if (!folderPendingRestart && selected != null) {
            Button retry = new Button(this); retry.setText(R.string.dmAndroidRetry); retry.setOnClickListener(v -> startHost()); panel.addView(retry);
        }
        setContentView(panel);
    }

    @Override public boolean onKeyUp(int code, android.view.KeyEvent event) {
        if (code == android.view.KeyEvent.KEYCODE_MENU) {
            new AlertDialog.Builder(this).setItems(new String[] {getString(R.string.dmAndroidChooseFolder)}, (dialog, which) -> pickGameFolder()).show();
            return true;
        }
        return super.onKeyUp(code, event);
    }

    private static String readText(File f) {
        try { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); } catch (IOException e) { return null; }
    }

    private static String packProduct(String info) {
        if (info != null) for (String line : info.split("\n")) if (line.startsWith("PRODUCT=")) return line.substring(8).trim();
        return null;
    }

    private void startHost() {
        if (bootFailure != 0) { showBootFailure(); return; }
        if (started || pickingFolder || folderPendingRestart || !resumed || !ensureFileAccess()) return;
        String selected = getPreferences(MODE_PRIVATE).getString("game_folder", null);
        if (selected == null) { showFolderScreen(getString(R.string.dmAndroidChooseFolderHint)); return; }
        File chosen = new File(selected), dataFolder = GamePaths.dataDirectory(chosen);
        if (dataFolder == null) { showFolderScreen(getString(R.string.dmAndroidFolderInvalid)); return; }
        File external = getExternalFilesDir(null);
        File runtime = GamePaths.runtimeDirectory(chosen, new File(getFilesDir(), "dm"), external == null ? null : new File(external, "dm"));
        if (runtime == null) { showFolderScreen(getString(R.string.dmAndroidPreparedPackRequired)); return; }
        String info = readText(new File(runtime, "pack_info.txt"));
        if (info != null && !BuildConfig.DM_PRODUCT.equals(packProduct(info))) { showFolderScreen(getString(R.string.dmAndroidImportVersion, packProduct(info))); return; }
        String profile = null;
        if (info != null) for (String line : info.split("\n")) if (line.startsWith("PROFILE=")) profile = line.substring(8).trim();
        if (profile != null && !profile.equals("enhanced134") && !profile.equals("original133")) {
            showStartError(getString(R.string.dmAndroidStartFailed, -11)); return;
        }
        original133 = "original133".equals(profile);
        if (gameFrame.getParent() == null) setContentView(gameFrame);
        if (!libraryLoaded) {
            // 原生构造函数与 JNI 共用选定目录，不导入另一份。
            // Native constructors and JNI share the selected runtime without importing another copy.
            try {
                if (original133) Os.setenv("DM_ORIGINAL_133", "1", true); else Os.unsetenv("DM_ORIGINAL_133");
                Os.setenv("DM_DERIVED", new File(runtime, "derived.tsv").getAbsolutePath(), true);
            } catch (ErrnoException e) { showStartError(getString(R.string.dmAndroidStorage)); return; }
            try { System.loadLibrary("dm"); libraryLoaded = true; }
            catch (UnsatisfiedLinkError error) { showStartError(getString(R.string.dmAndroidLibrary)); return; }
        }
        if (!original133 && touchOverlay == null) {
            int[] gameIds = {R.string.dmAndroidAttack, R.string.dmAndroidSkill1, R.string.dmAndroidSkill2, R.string.dmAndroidSkill3, R.string.dmAndroidSkill4,
                R.string.dmAndroidPortal, R.string.dmAndroidPotion1, R.string.dmAndroidPotion2, R.string.dmAndroidPotion3, R.string.dmAndroidPotion4,
                R.string.dmAndroidMenu, R.string.dmAndroidMap, R.string.dmAndroidMove, R.string.dmAndroidSwap, R.string.dmAndroidDifficulty};
            int[] navIds = {R.string.dmConfirm, R.string.dmAndroidBack, R.string.dmAndroidAction, R.string.dmAndroidBind, R.string.dmAndroidNext};
            String[] gameLabels = new String[gameIds.length], navLabels = new String[navIds.length];
            for (int i = 0; i < gameIds.length; i++) gameLabels[i] = getString(gameIds[i]);
            for (int i = 0; i < navIds.length; i++) navLabels[i] = getString(navIds[i]);
            touchOverlay = new TouchOverlay(this, gameLabels, navLabels);
            touchOverlay.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.displayCutout());
                touchOverlay.setSafeInsets(safe.left, safe.right, safe.bottom);
                return insets;
            });
            gameFrame.addView(touchOverlay, new FrameLayout.LayoutParams(-1, -1));
            touchOverlay.requestApplyInsets();
        }
        if (!view.getHolder().getSurface().isValid() || view.getWidth() <= 0 || view.getHeight() <= 0) return;
        File ext = getExternalFilesDir(null);
        if (ext == null) { showStartError(getString(R.string.dmAndroidStorage)); return; }
        nativeSurface(view.getHolder().getSurface(), view.getWidth(), view.getHeight());
        updateRefresh();
        // 资源直接从记住的本地或 SD 卡目录读取。
        // Read resources directly from the remembered local or SD-card directory.
        String src = dataFolder.getAbsolutePath();
        int result = nativeStart(getFilesDir().getAbsolutePath(), ext.getAbsolutePath(), src, Locale.getDefault().toLanguageTag(), runtime.getAbsolutePath());
        if (result < 0) {
            int resource = switch (result) {
                case -2 -> R.string.dmAndroidMissingData;
                case -3 -> R.string.dmAndroidMissingModules;
                case -4 -> R.string.dmAndroidMissingShaders;
                case -5 -> R.string.dmAndroidMissingConfig;
                case -9 -> R.string.dmAndroidMissingOverlay;
                case -10 -> R.string.dmAndroidInvalidOverlay;
                default -> 0;
            };
            showStartError(resource == 0 ? getString(R.string.dmAndroidStartFailed, result) : getString(resource)); return;
        }
        started = true;
        if (original133) { nativeFocus(3); return; }
        touchOverlay.setInputEnabled(true);
        if (!original133 && camSensor != null) camSensor.start(nativeCamSensor());
        // 越肩镜头的左右转按钮：盖在触屏层上面，只收按钮上的触摸。镜头可以在局中从「自定义」页打开（dm_cam.c dm_cam_mode），所以总是加上：
        // 镜头没在出图时按钮不画、触摸全部漏给下面的触屏层（nativeCamActive，每帧问一次）
        // Turn buttons of the over-the-shoulder camera: above the touch layer, taking only touches on the buttons. The camera can be turned on mid-game from
        // the Custom page (dm_cam.c dm_cam_mode), so they are always added: while the camera is not drawing the buttons are not drawn and every touch falls
        // through to the touch layer below (nativeCamActive, asked every frame)
        // 画面外的怪的箭头与地图上的朝向箭头放在按钮下面一层：只画不收触摸，镜头关着时什么都不画
        // The off-picture monster arrows and the map heading arrow go one layer below the buttons: drawing only, no touches, nothing drawn while the camera is off
        if (camButtons == null) {
            camThreats = new CamThreats(this); gameFrame.addView(camThreats, new FrameLayout.LayoutParams(-1, -1));
            camButtons = new CamButtons(this); gameFrame.addView(camButtons, new FrameLayout.LayoutParams(-1, -1)); }
        if (camButtons != null) camButtons.start();
        if (camThreats != null) camThreats.start();
        nativeFocus(3);
    }

    private void showStartError(String message) {
        showFolderScreen(message);
    }

    // 原生线程只投递主线程工作，不持有 Activity，也不直接操作视图。
    // Native threads only post UI work; they neither retain the Activity nor touch views.
    private static void showNativeFailure(int code) {
        bootFailure = code;
        DmActivity activity = current.get();
        if (activity != null) activity.runOnUiThread(() -> {
            if (!activity.isDestroyed()) activity.showBootFailure();
        });
    }

    private void showBootFailure() {
        releaseTouches();
        showStartError(getString(bootFailure == -6 ? R.string.dmAndroidGraphicsFailed : R.string.dmAndroidHostStopped));
    }

    private static void showNativeInput(long id, String title, String sub, String guide, String initial,
                                        int maxChars, int nameRule, int numericMax) {
        DmActivity activity = current.get();
        if (activity == null) { nativeTextResult(id, false, ""); return; }
        activity.runOnUiThread(() -> activity.openInput(id, title, sub, guide, initial, maxChars, nameRule != 0, numericMax));
    }

    private void openInput(long id, String title, String sub, String guide, String initial,
                           int maxChars, boolean nameRule, int numericMax) {
        if (!resumed || isFinishing() || isDestroyed() || inputDialog != null) { nativeTextResult(id, false, ""); return; }
        releaseTouches();
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(numericMax >= 0 ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(nameRule ? getString(R.string.dmNameBlankRandom) : guide);
        input.setText(TextInputRules.normalize(initial, maxChars, nameRule, numericMax));
        input.setSelection(input.length());
        String message = sub + (sub.isEmpty() || guide.isEmpty() ? "" : "\n") + guide;
        if (nameRule) message += (message.isEmpty() ? "" : "\n") + getString(R.string.dmNameRule);
        if (numericMax >= 0) message = getString(R.string.dmAndroidQuantityRange, numericMax);
        if (title.isEmpty()) title = getString(nameRule ? R.string.dmNameTitle : numericMax >= 0 ? R.string.dmQtyTitle : R.string.dmInputTitle);
        final boolean[] finished = {false};
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setMessage(message).setView(input)
            .setPositiveButton(R.string.dmConfirm, null).setNegativeButton(R.string.dmCancel, (d, which) -> {})
            .create();
        inputDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (inputDialog == dialog) inputDialog = null;
            if (!finished[0]) { finished[0] = true; nativeTextResult(id, false, ""); }
            if (resumed) immersive();
        });
        dialog.setOnShowListener(d -> {
            Runnable check = () -> {
                boolean composing = BaseInputConnection.getComposingSpanStart(input.getText()) >= 0;
                int error = nameRule ? TextInputRules.nameError(input.getText().toString()) : 0;
                input.setError(composing ? getString(R.string.dmNamePickIme) : error == 1 ? getString(R.string.dmNameTooShort) : error == 2 ? getString(R.string.dmNameEdge) : null);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!composing && error == 0);
            };
            Runnable settle = () -> {
                Editable text = input.getText();
                if (BaseInputConnection.getComposingSpanStart(text) < 0) {
                    String normalized = TextInputRules.normalize(text.toString(), maxChars, nameRule, numericMax);
                    if (!normalized.contentEquals(text)) { input.setText(normalized); input.setSelection(input.length()); return; }
                }
                check.run();
            };
            // 输入法结束组字（点候选、按完成）时常常只去掉组字标记、文字不变：TextWatcher 收不到，「确定」一直灰、提示停在「先选完字」。
            // 组字状态一变就和文字变化一样处理（规整 + 重判）：SpanWatcher 挂在文字上，Editable.Factory 让每次 setText 换上的新文字也挂着它。
            // When the IME finishes composing (picking a candidate, Done) it often only removes the composing span and keeps the text: TextWatcher is not
            // called, so OK stays disabled with "finish choosing characters". A change of composing state is handled like a text change (normalize + check):
            // a SpanWatcher sits on the text, and an Editable.Factory attaches it to the new text after every setText.
            SpanWatcher composingWatch = new SpanWatcher() {
                boolean composing = BaseInputConnection.getComposingSpanStart(input.getText()) >= 0;
                private void changed(Spannable text) {
                    boolean now = BaseInputConnection.getComposingSpanStart(text) >= 0;
                    if (now != composing) { composing = now; input.post(settle); }
                }
                public void onSpanAdded(Spannable text, Object what, int start, int end) { changed(text); }
                public void onSpanRemoved(Spannable text, Object what, int start, int end) { changed(text); }
                public void onSpanChanged(Spannable text, Object what, int ostart, int oend, int nstart, int nend) { changed(text); }
            };
            input.setEditableFactory(new Editable.Factory() {
                @Override public Editable newEditable(CharSequence source) {
                    Editable e = super.newEditable(source);
                    e.setSpan(composingWatch, 0, e.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    return e;
                }
            });
            input.getText().setSpan(composingWatch, 0, input.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                public void afterTextChanged(Editable text) { settle.run(); }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                finished[0] = true;
                nativeTextResult(id, true, input.getText().toString());
                dialog.dismiss();
            });
            check.run();
            input.requestFocus();
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void releaseTouches() {
        if (libraryLoaded) gamepad.reset();
        if (touchOverlay != null) touchOverlay.cancelTouches();
        if (started) nativeTouch(0, new int[0], new float[0], new float[0]);
    }

    private void updateRefresh() {
        if (libraryLoaded) nativeRefresh(view.getDisplay() == null ? 0 : view.getDisplay().getRefreshRate());
    }
    @Override public void onDisplayAdded(int id) { updateRefresh(); }
    @Override public void onDisplayRemoved(int id) { updateRefresh(); }
    @Override public void onDisplayChanged(int id) { updateRefresh(); }
    @Override protected void onResume() { super.onResume(); resumed = true; immersive(); startHost(); updateRefresh(); if (started) { nativeFocus(3); if (touchOverlay != null && bootFailure == 0) touchOverlay.setInputEnabled(true); if (!original133 && camSensor != null) camSensor.start(nativeCamSensor()); if (camButtons != null) camButtons.start(); if (camThreats != null) camThreats.start(); } }
    @Override protected void onPause() { resumed = false; if (camSensor != null) camSensor.stop(); if (camButtons != null) camButtons.stop(); if (camThreats != null) camThreats.stop(); if (inputDialog != null) inputDialog.dismiss(); releaseTouches(); if (touchOverlay != null) touchOverlay.setInputEnabled(false); if (started) nativeFocus(0); super.onPause(); }
    @Override protected void onStart() { super.onStart(); if (started) nativeFocus(2); }
    @Override protected void onStop() { if (started) nativeFocus(1); com.xiaoboluo.d2r.widget.HeroWidgetProvider.requestUpdate(this); super.onStop(); }
    @Override protected void onDestroy() { if (displays != null) displays.unregisterDisplayListener(this); if (inputDialog != null) inputDialog.dismiss(); if (current.get() == this) current.clear(); super.onDestroy(); }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) immersive(); else releaseTouches(); }

    @Override public void surfaceCreated(SurfaceHolder h) {}
    @Override public void surfaceChanged(SurfaceHolder h, int format, int w, int ht) {
        if (libraryLoaded) nativeSurface(h.getSurface(), w, ht);
        updateRefresh();
        startHost();
    }
    @Override public void surfaceDestroyed(SurfaceHolder h) { releaseTouches(); if (libraryLoaded) nativeSurface(null, 0, 0); }

}
