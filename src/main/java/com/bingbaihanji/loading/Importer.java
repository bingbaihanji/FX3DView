package com.bingbaihanji.loading;

import java.io.IOException;
import java.net.URL;

public interface Importer {

    /**
     * 加载3D文件
     *
     * @param url 需要加载的3D文件的URL
     * @return 加载的3D模型
     * @throws IOException 如果加载文件时出现问题
     */
    Model3D load(URL url) throws IOException;

    /**
     * 带进度回调的加载方法
     * <p>
     * 默认实现忽略回调,直接委托给 {@link #load(URL)}.
     * 子类可覆盖以提供真实的进度报告.
     * </p>
     *
     * @param url      模型文件 URL
     * @param callback 进度回调(可为 null)
     * @return 加载的3D模型
     * @throws IOException 如果加载文件时出现问题
     */
    default Model3D load(URL url, ProgressCallback callback) throws IOException {
        return load(url);
    }

    /**
     * 将3D文件加载为多边形网格.
     *
     * @param url 需要加载的3D文件的URL
     * @return 加载的3D多边形模型
     * @throws IOException 如果加载文件时出现问题
     */
    Model3D loadAsPoly(URL url) throws IOException;

    /**
     * 测试给定的3D文件扩展名是否受支持(例如"ma","ase","obj","fxml","dae").
     *
     * @param supportType 文件扩展名(例如"ma","ase","obj","fxml","dae")
     * @return 如果扩展名属于受支持类型,则返回true;否则返回false.
     */
    boolean isSupported(String supportType);

    /**
     * 进度回调函数式接口
     * <p>
     * 在模型解析过程中被调用,用于报告加载进度.
     * </p>
     */
    @FunctionalInterface
    interface ProgressCallback {

        /**
         * 进度更新回调
         *
         * @param progress 进度值(0.0~1.0)
         * @param status   当前状态描述(如"解析顶点...")
         */
        void onProgress(double progress, String status);
    }
}