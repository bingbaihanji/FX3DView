package com.bingbaihanji.animation;

import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.core.Lifecycle;
import com.bingbaihanji.interaction.InteractionConfig;
import com.bingbaihanji.view.ViewingAxes;
import javafx.animation.AnimationTimer;
import lombok.extern.slf4j.Slf4j;

/**
 * 自动旋转动画
 * <p>
 * 提供连续的自动旋转功能，绕Y轴旋转
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class AutoRotationAnimation implements Lifecycle {

    private final CameraSystem cameraSystem;
    private final ViewingAxes viewingAxes;
    private final AnimationTimer timer;
    private volatile boolean isRunning = false;
    /**
     * 上一帧时间戳（纳秒），用于计算真实帧间隔
     */
    private volatile long lastNow = 0;

    /**
     * 构造函数
     *
     * @param cameraSystem 相机系统
     * @param viewingAxes  辅助轴视图
     */
    public AutoRotationAnimation(CameraSystem cameraSystem, ViewingAxes viewingAxes) {
        this.cameraSystem = cameraSystem;
        this.viewingAxes = viewingAxes;
        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNow == 0) {
                    lastNow = now;
                    return;
                }
                // 基于实际帧间隔计算旋转角度，适配不同刷新率（60/120/144Hz）
                double deltaSeconds = (now - lastNow) / 1_000_000_000.0;
                lastNow = now;
                double angleRad = -Math.toRadians(InteractionConfig.AUTO_ROTATION_SPEED * deltaSeconds);
                cameraSystem.getRotationStrategy().applyAutoRotation(angleRad);
                viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());
            }
        };
    }

    @Override
    public void start() {
        if (!isRunning) {
            isRunning = true;
            lastNow = 0;
            timer.start();
        }
    }

    /**
     * 切换自动旋转状态
     */
    public void toggle() {
        isRunning = !isRunning;
        if (isRunning) {
            lastNow = 0; // 重置时间戳，避免首帧出现累积延迟
            timer.start();
            log.info("自动旋转已开启");
        } else {
            timer.stop();
            log.info("自动旋转已关闭");
        }
    }

    @Override
    public void dispose() {
        stop();
    }

    /**
     * 停止自动旋转
     */
    @Override
    public void stop() {
        if (isRunning) {
            timer.stop();
            isRunning = false;
        }
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return isRunning;
    }
}
