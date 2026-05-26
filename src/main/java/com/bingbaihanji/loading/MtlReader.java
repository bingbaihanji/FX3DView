package com.bingbaihanji.loading;

import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.BiConsumer;

import static java.util.Map.entry;

/**
 * MTL 材质文件解析器 用于解析 OBJ 3D模型文件关联的材质定义文件
 * Reader for OBJ file MTL material files
 * .
 */
public class MtlReader {

    private static final Logger log = LoggerFactory.getLogger(MtlReader.class);

    // // MTL 文件格式关键字与对应解析器的映射
    // mtl format spec: http://paulbourke.net/dataformats/mtl/
    private static final Map<String, BiConsumer<String, MtlReader>> PARSERS = Map.ofEntries(

            // 材质定义
            entry("newmtl ", (l, m) -> m.parseNewMaterial(l)),

            // 材质颜色和光照属性
            // Material color and illumination
            entry("Ka ", (l, m) -> m.parseIgnore("环境光反射率 (Ka)")),
            entry("Kd ", (l, m) -> m.parseDiffuseReflectivity(l)),
            entry("Ks ", (l, m) -> m.parseSpecularReflectivity(l)),
            entry("Ns ", (l, m) -> m.parseSpecularExponent(l)),
            entry("Tf ", (l, m) -> m.parseIgnore("透射滤镜 (Tf)")),
            entry("illum ", (l, m) -> m.parseIgnore("光照模型 (illum)")),
            entry("d ", (l, m) -> m.parseIgnore("溶解度 (d)")),
            entry("Tr ", (l, m) -> m.parseIgnore("透明度(Tr)")),
            entry("sharpness ", (l, m) -> m.parseIgnore("锐度 (sharpness)")),
            entry("Ni ", (l, m) -> m.parseIgnore("光学密度 (Ni)")),

            // 材质纹理贴图
            // Material texture map
            entry("map_Ka ", (l, m) -> m.parseIgnore("环境反射率图 (map_Ka)")),
            entry("map_Kd ", (l, m) -> m.parseDiffuseReflectivityMap(l)), // 纹理贴图
            entry("map_Ks ", (l, m) -> m.parseSpecularReflectivityMap(l)),
            entry("map_Ns ", (l, m) -> m.parseIgnore("高光指数贴图 (map_Ns)")),
            entry("map_d ", (l, m) -> m.parseIgnore("溶解贴图 (map_d)")),
            entry("disp ", (l, m) -> m.parseIgnore("置换贴图 (disp)")),
            entry("decal ", (l, m) -> m.parseIgnore("贴花模板贴图 (decal)")),
            entry("bump ", (l, m) -> m.parseBumpMap(l)),
            entry("refl ", (l, m) -> m.parseIgnore("反射贴图 (refl)")),
            entry("map_aat ", (l, m) -> m.parseIgnore("抗锯齿 (map_aat)")));

    // 基础URL路径，用于构建纹理图片的完整路径
    private final String baseUrl;
    // 存储解析后的材质：材质名称 -> 材质对象
    private final Map<String, Material> materials = new HashMap<>();
    // 记录当前材质已解析的属性，避免重复解析
    private final Set<String> readProperties = new HashSet<>(PARSERS.size() - 1);
    // 当前正在解析的材质
    private PhongMaterial currentMaterial;

    /**
     * MtlReader 构造方法，用于从指定的 URL 加载材质文件。
     *
     * @param filename  需要读取的材质文件的名称。
     * @param parentUrl 材质文件所在的父 URL。
     */
    public MtlReader(String filename, String parentUrl) {
        // 提取父 URL 目录路径
        baseUrl = parentUrl.substring(0, parentUrl.lastIndexOf('/') + 1);
        // 构建材质文件的完整 URL
        String fileUrl = baseUrl + filename;

        // 输出读取材质信息的日志
        log.info("正在读取材质文件: {}", fileUrl);

        // 尝试打开材质文件并读取其内容
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(fileUrl).openStream(), StandardCharsets.UTF_8))) {
            reader.lines()
                    .map(String::trim) // 去除前后空格
                    .filter(l -> !l.isEmpty() && !l.startsWith("#")) // 忽略空行和注释行
                    .forEach(this::parseLine); // 逐行解析材质文件
        } catch (IOException e) {
            // 如果文件读取过程中发生异常，输出警告信息
            log.warn("无法加载材质文件: {}", fileUrl, e);
        }
    }

    /**
     * 解析单行MTL文件内容
     *
     * @param line 要解析的行内容
     */
    private void parseLine(String line) {
        for (Entry<String, BiConsumer<String, MtlReader>> parser : PARSERS.entrySet()) {
            String identifier = parser.getKey();
            if (line.startsWith(identifier)) {
                if (!"newmtl ".equals(identifier) && !readProperties.add(identifier)) {
                    log.info("{} 属性已在当前材质中解析过，忽略重复设置", identifier);
                    return;
                }
                parser.getValue().accept(line.substring(identifier.length()), this);
                return;
            }
        }
        log.info("未找到解析器处理此行: {}", line);
    }

    // 忽略不支持的属性并记录日志
    private void parseIgnore(String nameAndKey) {
        log.info("{} 属性暂不支持，已忽略", nameAndKey);
    }

    // 解析新材质定义
    private void parseNewMaterial(String materialName) {
        // 检查材质名称是否已存在
        if (materials.containsKey(materialName)) {
            log.info("材质 '{}' 已存在，忽略重复定义", materialName);
            return;
        }

        // 创建新材质并重置属性记录
        currentMaterial = new PhongMaterial();
        readProperties.clear();
        materials.put(materialName, currentMaterial);

        log.info("读取材质: {}", materialName);
    }

    // 解析漫反射颜色
    private void parseDiffuseReflectivity(String value) {
        currentMaterial.setDiffuseColor(readColor(value));
    }

    // 解析镜面反射颜色
    private void parseSpecularReflectivity(String value) {
        currentMaterial.setSpecularColor(readColor(value));
    }

    // 解析高光指数
    private void parseSpecularExponent(String value) {
        currentMaterial.setSpecularPower(Double.parseDouble(value));
    }

    // 解析漫反射贴图
    private void parseDiffuseReflectivityMap(String value) {
        currentMaterial.setDiffuseMap(loadImage(value));
    }

    // 解析镜面反射贴图
    private void parseSpecularReflectivityMap(String value) {
        currentMaterial.setSpecularMap(loadImage(value));
    }

    // 解析凹凸贴图
    private void parseBumpMap(String value) {
        currentMaterial.setBumpMap(loadImage(value));
    }

    // 从字符串中解析颜色 MTL格式：三个浮点数表示RGB颜色分量
    private Color readColor(String colorLine) {
        try {
            String[] components = colorLine.trim().split("\\s+");
            if (components.length < 3) {
                throw new IllegalArgumentException("颜色值需要3个分量");
            }

            float red = Float.parseFloat(components[0]);
            float green = Float.parseFloat(components[1]);
            float blue = Float.parseFloat(components[2]);

            // 验证颜色分量范围
            red = clampColorComponent(red);
            green = clampColorComponent(green);
            blue = clampColorComponent(blue);

            return Color.color(red, green, blue);

        } catch (Exception e) {
            log.error("颜色解析错误: {} - {}", colorLine, e.getMessage());
            return Color.WHITE; // 返回默认颜色
        }
    }

    /**
     * 限制颜色分量在0-1范围内
     */
    private float clampColorComponent(float component) {
        return Math.max(0, Math.min(1, component));
    }

    /**
     * 加载纹理图片
     */
    private Image loadImage(String filename) {
        try {
            String imageUrl = baseUrl + filename.trim();
            log.info("加载图片: {}", imageUrl);
            return new Image(imageUrl);
        } catch (Exception e) {
            log.warn("图片加载失败: {} - {}", filename, e.getMessage());
            return null; // 返回null表示加载失败
        }
    }

    /**
     * 获取解析后的所有材质
     *
     * @return 不可修改的材质映射表
     */
    public Map<String, Material> getMaterials() {
        return Collections.unmodifiableMap(materials);
    }


    /**
     * 获取指定名称的材质
     *
     * @param name 材质名称
     * @return 对应的材质对象，如果不存在返回null
     */
    public Material getMaterial(String name) {
        return materials.get(name);
    }

    /**
     * 获取所有材质名称
     *
     * @return 材质名称集合
     */
    public Set<String> getMaterialNames() {
        return Collections.unmodifiableSet(materials.keySet());
    }

    /**
     * 检查是否包含指定名称的材质
     *
     * @param name 材质名称
     * @return 如果存在返回true
     */
    public boolean containsMaterial(String name) {
        return materials.containsKey(name);
    }

    /**
     * 获取解析的材质数量
     *
     * @return 材质数量
     */
    public int getMaterialCount() {
        return materials.size();
    }
}