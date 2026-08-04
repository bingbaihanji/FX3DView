package com.bingbaihanji.core;

/**
 * 组件生命周期接口
 * <p>
 * 管理需要显式初始化和清理的资源(AnimationTimer,Thread,事件处理器等).
 * start() 和 stop() 提供默认空实现,dispose() 必须实现.
 * </p>
 */
public interface Lifecycle {

    /**
     * 初始化并启动组件.可重复调用
     */
    default void start() {
    }

    /**
     * 暂停组件运行,允许通过 start() 恢复
     */
    default void stop() {
    }

    /**
     * 永久释放资源,调用后组件不再可用
     */
    void dispose();
}
