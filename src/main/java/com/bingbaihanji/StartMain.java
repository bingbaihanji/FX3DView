package com.bingbaihanji;

/**
 * 应用程序主入口
 * <p>
 * 默认启动四元数版本的3D查看器
 * 如需使用矩阵版本,请使用 MatrixViewerLauncher
 * </p>
 *
 * @author bingbaihanji
 * @date 2025-08-16 14:13:11
 */
public class StartMain {

    public static void main(String[] args) {
        // 四元数版本(推荐,无万向节锁)
        com.bingbaihanji.app.QuaternionViewerLauncher.main(args);

        // 矩阵版本(教学对比用)
        // com.bingbaihanji.app.MatrixViewerLauncher.main(args);
    }
}
