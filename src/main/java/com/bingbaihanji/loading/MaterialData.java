package com.bingbaihanji.loading;

import javafx.scene.paint.Material;

/**
 * A wrapper around JavaFX Material that provides metadata.
 */
public record MaterialData(String name, Material material) {

}