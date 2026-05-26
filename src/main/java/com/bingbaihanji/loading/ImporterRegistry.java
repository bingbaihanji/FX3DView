package com.bingbaihanji.loading;

import java.util.*;
import java.util.function.Supplier;

/**
 * 导入器注册表
 * <p>
 * 管理文件扩展名到 Importer 工厂的映射。
 * 使用 {@link Supplier} 工厂模式，每次 getImporter() 返回新实例，
 * 避免多线程共享可变状态。添加新文件格式支持只需实现 {@link Importer} 并注册即可。
 * </p>
 */
public class ImporterRegistry {

    private final Map<String, Supplier<Importer>> importers = new LinkedHashMap<>();

    /**
     * 注册一个导入器工厂
     *
     * @param extension 文件扩展名（不含点号，例如 "obj"），大小写不敏感
     * @param factory   导入器工厂（例如 {@code ObjImporter::new}）
     */
    public void register(String extension, Supplier<Importer> factory) {
        Objects.requireNonNull(extension, "扩展名不能为空");
        Objects.requireNonNull(factory, "导入器工厂不能为空");
        importers.put(extension.toLowerCase(Locale.ROOT), factory);
    }

    /**
     * 根据扩展名创建新的导入器实例
     *
     * @param extension 文件扩展名（不含点号）
     * @return 导入器实例，如果没有注册则返回 null
     */
    public Importer getImporter(String extension) {
        if (extension == null) return null;
        Supplier<Importer> supplier = importers.get(extension.toLowerCase(Locale.ROOT));
        return supplier != null ? supplier.get() : null;
    }

    /**
     * 判断指定的扩展名是否受支持
     *
     * @param extension 文件扩展名
     * @return 如果存在对应的导入器则返回 true
     */
    public boolean isExtensionSupported(String extension) {
        return extension != null && importers.containsKey(extension.toLowerCase(Locale.ROOT));
    }

    /**
     * 获取所有已注册的扩展名
     *
     * @return 不可修改的扩展名集合
     */
    public Set<String> getSupportedExtensions() {
        return Collections.unmodifiableSet(importers.keySet());
    }
}
