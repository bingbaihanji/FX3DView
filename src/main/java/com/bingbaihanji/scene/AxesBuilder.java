package com.bingbaihanji.scene;

import com.bingbaihanji.world.GroupTransform;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;

/**
 * 坐标轴构建器
 * <p>
 * 负责创建和配置3D坐标轴（X/Y/Z轴）
 * </p>
 *
 * @author bingbaihanji
 */
public final class AxesBuilder {

    /**
     * 坐标轴长度
     */
    private static final double AXIS_LENGTH = 500;

    /**
     * 坐标轴粗细
     */
    private static final double AXIS_THICKNESS = 0.1;

    /**
     * 私有构造函数，防止实例化
     */
    private AxesBuilder() {
        throw new AssertionError("工具类不应该被实例化");
    }

    /**
     * 构建坐标轴并添加到指定的组中
     *
     * @param axisGroup 坐标轴组
     */
    public static void buildAxes(GroupTransform axisGroup) {
        axisGroup.getChildren().clear();

        // X轴 - 红色
        PhongMaterial redMaterial = new PhongMaterial(Color.ORANGERED);
        Box xAxis = new Box(AXIS_LENGTH, AXIS_THICKNESS, AXIS_THICKNESS);
        xAxis.setMaterial(redMaterial);

        // Y轴 - 绿色
        PhongMaterial greenMaterial = new PhongMaterial(Color.LIME);
        Box yAxis = new Box(AXIS_THICKNESS, AXIS_LENGTH, AXIS_THICKNESS);
        yAxis.setMaterial(greenMaterial);

        // Z轴 - 蓝色
        PhongMaterial blueMaterial = new PhongMaterial(Color.DODGERBLUE);
        Box zAxis = new Box(AXIS_THICKNESS, AXIS_THICKNESS, AXIS_LENGTH);
        zAxis.setMaterial(blueMaterial);

        // 添加到坐标轴组
        axisGroup.getChildren().addAll(xAxis, yAxis, zAxis);
        axisGroup.setVisible(false); // 默认隐藏
    }
}
