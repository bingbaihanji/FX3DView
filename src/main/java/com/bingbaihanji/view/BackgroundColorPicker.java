package com.bingbaihanji.view;

import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.ColorPicker;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * 背景颜色选择器对话框（Stage复用）
 */
public class BackgroundColorPicker {

    private final SubScene subScene;
    private Stage colorStage;

    public BackgroundColorPicker(SubScene subScene) {
        this.subScene = subScene;
    }

    public void show() {
        if (colorStage == null) {
            colorStage = createStage();
        }
        // 同步当前背景色到选择器
        ColorPicker cp = (ColorPicker) ((BorderPane) colorStage.getScene().getRoot()).getCenter();
        cp.setValue((Color) subScene.getFill());
        colorStage.show();
    }

    private Stage createStage() {
        ColorPicker colorPicker = new ColorPicker();
        colorPicker.setValue((Color) subScene.getFill());

        Stage stage = new Stage();
        stage.getIcons().add(new Image("/setBackgroundColor.png"));

        BorderPane colorRoot = new BorderPane(colorPicker);

        LinearGradient linearGradient = new LinearGradient(
                0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, new Color(0.98, 0.0, 0.99, 1.0)),
                new Stop(1.0, new Color(0.01, 0.86, 0.87, 1.0))
        );
        colorRoot.setBackground(new Background(new BackgroundFill(linearGradient, null, null)));

        Scene colorScene = new Scene(colorRoot, 300, 100);
        stage.setResizable(false);
        stage.setScene(colorScene);
        stage.setTitle("选择背景颜色");
        stage.initModality(Modality.APPLICATION_MODAL);

        colorPicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            subScene.setFill(newValue);
        });

        colorPicker.setOnAction(e -> stage.hide());
        return stage;
    }
}
