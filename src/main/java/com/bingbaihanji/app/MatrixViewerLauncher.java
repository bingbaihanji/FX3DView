package com.bingbaihanji.app;

import com.bingbaihanji.rotation.MatrixRotation;
import javafx.application.Application;
import lombok.extern.slf4j.Slf4j;

/**
 * 矩阵版本3D查看器启动器
 * <p>
 * 使用旋转矩阵策略的应用程序入口
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class MatrixViewerLauncher extends Fx3DViewerApp {

    public MatrixViewerLauncher() {
        super(new MatrixRotation(), "Fx3DView");
    }

    public static void main(String[] args) {
        log.info("启动矩阵版本3D查看器");
        Application.launch(MatrixViewerLauncher.class, args);
    }
}
