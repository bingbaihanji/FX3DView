package com.bingbaihanji.menu;

/**
 * 着色模式枚举
 *
 * @author bingbaihanji
 */
public enum ShadingMode {
    /**
     * 贴图模式（原始材质）
     */
    TEXTURED,
    /**
     * 纯色模式（灰白色）
     */
    SOLID,
    /**
     * 线框模式
     */
    WIREFRAME,
    /**
     * 叠加模式（线框叠加在实体上）
     */
    OVERLAY,
    /**
     * 法线颜色模式（法线方向→颜色）
     */
    NORMAL_COLOR
}
