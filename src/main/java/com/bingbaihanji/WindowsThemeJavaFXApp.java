package com.bingbaihanji;

import com.sun.jna.*;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.W32APIOptions;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public abstract class WindowsThemeJavaFXApp extends Application {

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
    private static final int DWMWCP_ROUNDSMALL = 3;

    /**
     * 用于存储每个 Stage 当前是否暗色
     */
    private final Map<Stage, Boolean> darkState = new HashMap<>();

    /**
     * 获取某 Stage 是否暗色
     */
    protected boolean isDark(Stage stage) {
        return darkState.getOrDefault(stage, false);
    }

    /**
     * 切换深浅色
     */
    protected void toggleDarkTitleBar(Stage stage) {
        boolean oldState = isDark(stage);
        boolean newState = !oldState;

        darkState.put(stage, newState);
        applyDarkTitleBarAsync(stage, newState);
    }

    @Override
    public final void start(Stage primaryStage) throws Exception {
        applyDarkTitleBarAsync(primaryStage, true);
        startWindowsThemeUI(primaryStage);
    }

    // 替换默认的 start() 方法，并添加了 applyDarkTitleBar()
    protected abstract void startWindowsThemeUI(Stage primaryStage);

    /**
     * 显式设置深浅色
     */
    protected void applyDarkTitleBarAsync(Stage stage, boolean dark) {
        darkState.put(stage, dark);
        Platform.runLater(() -> applyDarkTitleBar(stage, dark));
    }

    protected void applyDarkTitleBar(Stage stage, boolean dark) {
        String os = System.getProperty("os.name");
        if (os == null || !os.toLowerCase().contains("win")) {
            return;
        }

        WinDef.HWND hwnd = getWindowHandle(stage);
        if (hwnd == null) {
            return;
        }

        int mode = dark ? 1 : 0;
        Memory pDark = new Memory(4);
        pDark.setInt(0, mode);

        try {
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, pDark, 4);
        } catch (Throwable ignored) {
        }

        Memory cornerPtr = new Memory(4);
        cornerPtr.setInt(0, DWMWCP_ROUNDSMALL);
        try {
            Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, cornerPtr, 4);
        } catch (Throwable ignored) {
        }
    }

    protected WinDef.HWND getWindowHandle(Stage stage) {
        try {
            Class<?> stageHelperCls = Class.forName("com.sun.javafx.stage.StageHelper");
            Method getPeerMethod = stageHelperCls.getMethod("getPeer", Window.class);
            Object peer = getPeerMethod.invoke(null, stage);

            if (peer != null) {
                try {
                    Method getRawHandle = peer.getClass().getMethod("getRawHandle");
                    Object raw = getRawHandle.invoke(peer);

                    if (raw instanceof Long) {
                        return new WinDef.HWND(new Pointer((Long) raw));
                    }
                    if (raw instanceof Integer) {
                        return new WinDef.HWND(new Pointer(Integer.toUnsignedLong((Integer) raw)));
                    }
                } catch (NoSuchMethodException ignore) {
                }
            }
        } catch (Throwable ignored) {
        }

        String title = stage.getTitle();
        if (title == null || title.isEmpty()) {
            return null;
        }

        for (int i = 0; i < 50; i++) {
            WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, new WString(title).toString());
            if (hwnd != null && Pointer.nativeValue(hwnd.getPointer()) != 0L) {
                return hwnd;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return null;
    }

    public interface Dwmapi extends Library {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class, W32APIOptions.DEFAULT_OPTIONS);

        int DwmSetWindowAttribute(WinDef.HWND hwnd, int attr, Pointer value, int size);
    }
}
