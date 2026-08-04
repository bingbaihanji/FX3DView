package com.bingbaihanji.menu;

import com.bingbaihanji.view.BackgroundColorPicker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 截图处理器:负责 SubScene 快照,剪贴板复制和文件保存
 *
 * @author bingbaihanji
 */
@Slf4j
public class ScreenshotHandler {

    /**
     * 背景颜色选择器(懒加载复用)
     */
    private BackgroundColorPicker backgroundColorPicker;

    /**
     * 注册截图菜单事件
     * <p>
     * 截取 SubScene 快照 → 复制到剪贴板 → 弹出保存对话框
     * </p>
     */
    public void screenshots(Stage primaryStage, MenuNode menuNode, SubScene subScene) {
        menuNode.getScreenshots().setOnAction(event -> {
            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setFill(Color.TRANSPARENT);
            WritableImage image = subScene.snapshot(snapshotParameters, null);

            // 复制到剪贴板
            Clipboard systemClipboard = Clipboard.getSystemClipboard();
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putImage(image);
            systemClipboard.setContent(clipboardContent);

            // 保存到文件
            BufferedImage png = SwingFXUtils.fromFXImage(image, null);
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Image File", ".png", "*.png"));
            File save = fileChooser.showSaveDialog(primaryStage);
            if (save != null) {
                try {
                    ImageIO.write(png, "png", save);
                } catch (IOException e) {
                    log.error("截图保存失败", e);
                }
            }
        });
    }

    /**
     * 注册背景颜色菜单事件
     */
    public void setBackgroundColor(MenuNode menuNode, SubScene subScene) {
        menuNode.getSetBackgroundColor().setOnAction(event -> {
            if (backgroundColorPicker == null) {
                backgroundColorPicker = new BackgroundColorPicker(subScene);
            }
            backgroundColorPicker.show();
        });
    }
}
