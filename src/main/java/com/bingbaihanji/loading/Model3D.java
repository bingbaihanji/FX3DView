package com.bingbaihanji.loading;

import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.paint.Material;

import java.util.*;

/**
 * 加载器无关的3D模型数据表示类（纯数据类，不管理场景图Group）
 * <p>
 * 职责：存储模型名称→MeshView/材质的映射关系及统计信息。
 * 场景图的组装（将 MeshView 添加到 Group）由调用方（如 DragDropHandler）负责。
 * </p>
 *
 * @author bingbaihanji
 */
public class Model3D {

    /**
     * 材质映射表：材质名称 → 材质对象
     */
    private final Map<String, Material> materials = new HashMap<>();

    /**
     * 网格视图映射表：网格名称 → 网格视图节点
     */
    private final Map<String, Node> meshViews = new HashMap<>();

    /**
     * 获取所有网格名称的集合
     *
     * @return 网格名称的不重复集合
     */
    public final Set<String> getMeshNames() {
        // 返回不可修改的视图，防止外部修改内部集合
        return Collections.unmodifiableSet(meshViews.keySet());
    }

    /**
     * 添加网格视图到模型中
     *
     * @param key  网格的唯一标识符
     * @param view 网格视图节点
     * @throws IllegalArgumentException 如果key为null或view为null
     */
    public final void addMeshView(String key, Node view) {
        if (key == null) {
            throw new IllegalArgumentException("Mesh key cannot be null");
        }
        if (view == null) {
            throw new IllegalArgumentException("Mesh view cannot be null");
        }
        meshViews.put(key, view);
    }

    /**
     * 根据名称获取特定的网格视图
     * 可通过 getMeshNames() 方法获取所有可用的网格名称
     *
     * @param key 网格名称
     * @return 对应的网格视图，如果不存在则返回null
     */
    public final Node getMeshView(String key) {
        return meshViews.get(key);
    }

    /**
     * 获取模型中包含的所有网格视图
     *
     * @return 所有网格视图的列表（不可修改的副本）
     */
    public final List<Node> getMeshViews() {
        return new ArrayList<>(meshViews.values());
    }

    /**
     * 添加材质到模型中
     *
     * @param key      材质的唯一标识符
     * @param material 材质对象
     * @throws IllegalArgumentException 如果key为null或material为null
     */
    public final void addMaterial(String key, Material material) {
        // 参数校验
        if (key == null) {
            throw new IllegalArgumentException("Material key cannot be null");
        }
        if (material == null) {
            throw new IllegalArgumentException("Material cannot be null");
        }

        materials.put(key, material);
    }

    /**
     * 获取所有材质名称的集合
     *
     * @return 材质名称的不重复集合
     */
    public final Set<String> getMaterialNames() {
        return Collections.unmodifiableSet(materials.keySet());
    }

    /**
     * 根据名称获取特定的材质
     *
     * @param key 材质名称
     * @return 对应的材质对象，如果不存在则返回null
     */
    public final Material getMaterial(String key) {
        return materials.get(key);
    }

    /**
     * 获取模型中包含的所有材质
     *
     * @return 所有材质的列表（不可修改的副本）
     */
    public final List<Material> getMaterials() {
        return new ArrayList<>(materials.values());
    }

    /**
     * 获取与此模型关联的动画时间线
     * 默认实现返回空的Optional，子类可以重写此方法以提供动画支持
     *
     * @return 动画时间线的Optional对象
     */
    public Optional<Timeline> getTimeline() {
        return Optional.empty();
    }

    /**
     * 从模型中移除指定的网格视图
     *
     * @param key 要移除的网格名称
     * @return 被移除的网格视图，如果不存在则返回null
     */
    public final Node removeMeshView(String key) {
        return meshViews.remove(key);
    }

    /**
     * 从模型中移除指定的材质
     *
     * @param key 要移除的材质名称
     * @return 被移除的材质对象，如果不存在则返回null
     */
    public final Material removeMaterial(String key) {
        return materials.remove(key);
    }

    /**
     * 清空模型中的所有网格视图
     */
    public final void clearMeshViews() {
        meshViews.clear();
    }

    /**
     * 清空模型中的所有材质
     */
    public final void clearMaterials() {
        materials.clear();
    }

    /**
     * 检查模型是否包含指定的网格
     *
     * @param key 网格名称
     * @return 如果包含则返回true
     */
    public final boolean containsMesh(String key) {
        return meshViews.containsKey(key);
    }

    /**
     * 检查模型是否包含指定的材质
     *
     * @param key 材质名称
     * @return 如果包含则返回true
     */
    public final boolean containsMaterial(String key) {
        return materials.containsKey(key);
    }

    /**
     * 获取网格视图的数量
     *
     * @return 网格视图的数量
     */
    public final int getMeshCount() {
        return meshViews.size();
    }

    /**
     * 获取材质的数量
     *
     * @return 材质的数量
     */
    public final int getMaterialCount() {
        return materials.size();
    }
}