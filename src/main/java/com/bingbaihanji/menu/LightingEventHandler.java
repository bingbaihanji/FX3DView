package com.bingbaihanji.menu;

import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;

/**
 * 环境光事件处理器：负责环境光的开关切换
 * <p>
 * 保存创建的 AmbientLight 引用，关闭时仅移除自己添加的光源，
 * 避免误删场景中其他代码添加的光源。
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class LightingEventHandler {

    /**
     * 当前环境光引用
     */
    private AmbientLight ambientLight = null;
    /**
     * 环境光是否开启
     */
    private boolean isLightOn = false;

    /**
     * 切换环境光开关
     */
    public void setupLighting(Group group) {
        if (!isLightOn) {
            ambientLight = new AmbientLight(Color.WHITE);
            ambientLight.getScope().addAll(group);
            group.getChildren().add(ambientLight);
            isLightOn = true;
        } else {
            if (ambientLight != null) {
                group.getChildren().remove(ambientLight);
                ambientLight = null;
            }
            isLightOn = false;
        }
    }
}
