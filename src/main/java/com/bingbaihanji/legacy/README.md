# Legacy Code (遗留代码)

此包保留重构前的原始代码，仅供参考和对比学习。

## 文件说明

- `Fx3DViewOfQuaternion.java.exclude` - 四元数版本（重构前，已排除编译）
- `Fx3DViewOfMatrix.java.exclude` - 矩阵版本（重构前，已排除编译）

**注意**：这些文件使用`.exclude`扩展名，不会被Maven编译。它们的package声明已更新为`com.bingbaihanji.legacy`
，但由于引用了整个项目的结构，保留原样仅作为参考。

## 新版本代码位置

重构后的代码采用了模块化设计，主要位于以下包中：

### 应用入口

- 四元数启动器：`com.bingbaihanji.app.QuaternionViewerLauncher`
- 矩阵启动器：`com.bingbaihanji.app.MatrixViewerLauncher`
- 统一应用入口：`com.bingbaihanji.app.Fx3DViewerApp`

### 核心组件

- 旋转策略：`com.bingbaihanji.rotation.*`
- 相机系统：`com.bingbaihanji.camera.*`
- 场景管理：`com.bingbaihanji.scene.*`
- 交互处理：`com.bingbaihanji.interaction.*`
- 动画系统：`com.bingbaihanji.animation.*`
- UI布局：`com.bingbaihanji.ui.*`

## 重构优势

1. **单一职责**：每个类只负责一个明确的功能
2. **代码复用**：两个版本共享95%代码，仅旋转策略不同
3. **易于扩展**：添加新旋转算法只需实现RotationStrategy接口
4. **教学友好**：清晰的组件职责便于学生理解
5. **保持简单**：没有过度抽象，仅3层架构

## 运行新版本

```bash
# 四元数版本
java com.bingbaihanji.app.QuaternionViewerLauncher

# 矩阵版本
java com.bingbaihanji.app.MatrixViewerLauncher
```

## 注意事项

⚠️ 此包中的代码已不再维护，所有新功能和修复都将在新架构中进行。
