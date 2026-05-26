package com.bingbaihanji.app;

import com.bingbaihanji.rotation.QuaternionRotation;
import javafx.application.Application;
import lombok.extern.slf4j.Slf4j;

/**
 * 四元数版本3D查看器启动器
 * <p>
 * 使用四元数旋转策略的应用程序入口
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class QuaternionViewerLauncher extends Fx3DViewerApp {

    public QuaternionViewerLauncher() {
        super(new QuaternionRotation(), "Fx3DView");
    }

    public static void main(String[] args) {
        log.info("启动四元数版本3D查看器");
        Application.launch(QuaternionViewerLauncher.class, args);
    }
}
