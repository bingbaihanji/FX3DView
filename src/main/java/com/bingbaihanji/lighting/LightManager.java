package com.bingbaihanji.lighting;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Sphere;
import javafx.stage.Stage;

/**
 * 方向光管理器:通过PointLight + 球形坐标控制光源位置
 */
public class LightManager {

    private final PointLight pointLight;

    /**
     * 标记球专属光源(scope 限定只照亮标记球,不受主灯光开关影响)
     */
    private final PointLight markerLight;

    private final Sphere lightMarker; // 可视化光源位置的小球

    private final Group world;

    private double azimuth = 45;   // 水平角(度)

    private double elevation = 30; // 仰角(度)

    private double distance = 100;

    private Color lightColor = Color.WHITE;

    private Stage controlDialog;

    public LightManager(Group world) {
        this.world = world;

        pointLight = new PointLight(lightColor);
        pointLight.setTranslateX(50);
        pointLight.setTranslateY(50);
        pointLight.setTranslateZ(-100);

        lightMarker = new Sphere(0.3);
        PhongMaterial markerMat = new PhongMaterial();
        markerMat.setDiffuseColor(Color.YELLOW);
        markerMat.setSpecularColor(Color.WHITE);
        lightMarker.setMaterial(markerMat);
        lightMarker.setVisible(false); // 默认隐藏光源标记球

        // 标记球专属光源:scope 限定只照亮标记球自身,不受场景主灯光开关影响
        markerLight = new PointLight(lightColor);
        markerLight.getScope().add(lightMarker);
        markerLight.setVisible(false); // 随标记球一起显隐

        updatePosition();
    }

    public void attachToScene() {
        world.getChildren().addAll(pointLight, lightMarker, markerLight);
    }

    public void detachFromScene() {
        world.getChildren().removeAll(pointLight, lightMarker, markerLight);
    }

    public void openControlDialog() {
        // JavaFX 单线程模型下无需同步,简单判空即可
        if (controlDialog != null && controlDialog.isShowing()) {
            controlDialog.toFront();
            return;
        }

        controlDialog = new Stage();
        controlDialog.setTitle("方向光设置");

        Label aziLabel = new Label("水平角: " + azimuth);
        Slider aziSlider = new Slider(0, 360, azimuth);
        aziSlider.setBlockIncrement(5);

        Label eleLabel = new Label("仰角: " + elevation);
        Slider eleSlider = new Slider(-90, 90, elevation);
        eleSlider.setBlockIncrement(5);

        Label distLabel = new Label("距离: " + String.format("%.0f", distance));
        Slider distSlider = new Slider(10, 500, distance);
        distSlider.setBlockIncrement(10);

        Label colorLabel = new Label("颜色:");
        ColorPicker colorPicker = new ColorPicker(lightColor);

        CheckBox showMarkerCb = new CheckBox("显示光源标记");
        showMarkerCb.setSelected(lightMarker.isVisible());

        Label sizeLabel = new Label("标记大小: " + String.format("%.1f", lightMarker.getRadius()));
        Slider sizeSlider = new Slider(0.1, 5.0, lightMarker.getRadius());
        sizeSlider.setBlockIncrement(0.1);
        sizeSlider.setDisable(!lightMarker.isVisible());

        VBox content = new VBox(10,
                aziLabel, aziSlider,
                eleLabel, eleSlider,
                distLabel, distSlider,
                colorLabel, colorPicker,
                new Separator(),
                showMarkerCb,
                sizeLabel, sizeSlider);
        content.setPadding(new Insets(15));
        content.setAlignment(Pos.CENTER_LEFT);

        Runnable update = () -> {
            azimuth = aziSlider.getValue();
            elevation = eleSlider.getValue();
            distance = distSlider.getValue();
            lightColor = colorPicker.getValue();
            aziLabel.setText("水平角: " + String.format("%.0f", azimuth));
            eleLabel.setText("仰角: " + String.format("%.0f", elevation));
            distLabel.setText("距离: " + String.format("%.0f", distance));
            updatePosition();
        };

        aziSlider.valueProperty().addListener((o, ov, nv) -> update.run());
        eleSlider.valueProperty().addListener((o, ov, nv) -> update.run());
        distSlider.valueProperty().addListener((o, ov, nv) -> update.run());
        colorPicker.valueProperty().addListener((o, ov, nv) -> update.run());

        // 光源标记球开关(同时控制标记球及其专属光源的显隐)
        showMarkerCb.selectedProperty().addListener((o, ov, visible) -> {
            lightMarker.setVisible(visible);
            markerLight.setVisible(visible);
            sizeSlider.setDisable(!visible);
        });

        // 光源标记球大小调节
        sizeSlider.valueProperty().addListener((o, ov, radius) -> {
            lightMarker.setRadius(radius.doubleValue());
            sizeLabel.setText("标记大小: " + String.format("%.1f", radius));
        });

        updateLightIntensity();

        Scene scene = new Scene(content, 320, 400);
        controlDialog.setScene(scene);
        controlDialog.show();
    }

    private void updatePosition() {
        double aziRad = Math.toRadians(azimuth);
        double eleRad = Math.toRadians(elevation);

        double x = distance * Math.cos(eleRad) * Math.sin(aziRad);
        double y = distance * Math.sin(eleRad);
        double z = -distance * Math.cos(eleRad) * Math.cos(aziRad);

        pointLight.setTranslateX(x);
        pointLight.setTranslateY(y);
        pointLight.setTranslateZ(z);

        lightMarker.setTranslateX(x);
        lightMarker.setTranslateY(y);
        lightMarker.setTranslateZ(z);

        markerLight.setTranslateX(x);
        markerLight.setTranslateY(y);
        markerLight.setTranslateZ(z);

        updateLightIntensity();
    }

    private void updateLightIntensity() {
        // 根据距离调整颜色亮度(远处更暗,近处更亮)
        double factor = Math.max(0.3, Math.min(3.0, 200.0 / distance));
        Color adjusted = lightColor.deriveColor(0, 1, factor, 1);
        pointLight.setColor(adjusted);

        // 同步更新标记球材质颜色
        if (lightMarker.getMaterial() instanceof PhongMaterial mat) {
            mat.setDiffuseColor(lightColor);
        }

        // 同步更新标记球专属光源颜色(不受距离衰减影响,保持标记球颜色鲜明)
        markerLight.setColor(lightColor);
    }

    public boolean isAttached() {
        return world.getChildren().contains(pointLight);
    }

    public void toggle() {
        if (isAttached()) {
            detachFromScene();
        } else {
            attachToScene();
        }
    }
}
