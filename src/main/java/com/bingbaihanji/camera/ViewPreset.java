package com.bingbaihanji.camera;

/**
 * 预设视角枚举
 */
public enum ViewPreset {
    FRONT(0, 0),
    BACK(0, 180),
    LEFT(0, -90),
    RIGHT(0, 90),
    TOP(-90, 0),
    BOTTOM(90, 0);

    public final double xAngle;

    public final double yAngle;

    ViewPreset(double xAngle, double yAngle) {
        this.xAngle = xAngle;
        this.yAngle = yAngle;
    }
}
