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
     * 将3D文件加载为多边形网格。
     *
     * @param url 需要加载的3D文件的URL
     * @return 加载的3D多边形模型
     * @throws IOException 如果加载文件时出现问题
     */
    Model3D loadAsPoly(URL url) throws IOException;

    /**
     * 测试给定的3D文件扩展名是否受支持（例如“ma”，“ase”，“obj”，“fxml”，“dae”）。
     *
     * @param supportType 文件扩展名（例如“ma”，“ase”，“obj”，“fxml”，“dae”）
     * @return 如果扩展名属于受支持类型，则返回true；否则返回false。
     */
    boolean isSupported(String supportType);
}