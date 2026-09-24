package com.photo.workspace.core.data.model

import java.util.UUID

enum class LayerType {
    TEXT,
    SHAPE,
    IMAGE,
    BRUSH,
    VECTOR_PATH,
    GROUP
}

enum class ShapeType {
    RECTANGLE,
    ROUNDED_RECT,
    CIRCLE,
    STAR,
    TRIANGLE,
    POLYGON,
    HEART,
    ARROW,
    SPEECH_BUBBLE,
    SVG_PATH
}

enum class BlendModeType {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    DIFFERENCE
}

enum class AnimationPreset {
    NONE,
    FADE_IN,
    POP_UP,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    SLIDE_UP,
    BOUNCE,
    ROTATE_IN
}

data class ElementAnimation(
    val preset: AnimationPreset = AnimationPreset.NONE,
    val durationMs: Int = 800,
    val delayMs: Int = 0,
    val isLooping: Boolean = false
)

data class TextData(
    val text: String = "Double tap to edit",
    val fontSize: Float = 42f,
    val fontColor: Long = 0xFFFFFFFF,
    val fontFamily: String = "Default",
    val letterSpacing: Float = 0f,
    val lineHeight: Float = 1.2f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val alignment: String = "CENTER", // LEFT, CENTER, RIGHT
    val isCurved: Boolean = false,
    val curveRadius: Float = 150f,
    val shadowColor: Long = 0x88000000,
    val shadowRadius: Float = 0f,
    val strokeColor: Long = 0x00000000,
    val strokeWidth: Float = 0f
)

data class ShapeData(
    val shapeType: ShapeType = ShapeType.ROUNDED_RECT,
    val fillColor: Long = 0xFF6366F1, // Indigo primary
    val strokeColor: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 0f,
    val cornerRadius: Float = 24f,
    val starPoints: Int = 5,
    val pathData: String = ""
)

data class ImageAdjustments(
    val brightness: Float = 0f,   // -100 to +100
    val contrast: Float = 0f,     // -100 to +100
    val saturation: Float = 0f,   // -100 to +100
    val hue: Float = 0f,          // -180 to +180
    val exposure: Float = 0f,     // -100 to +100
    val blurRadius: Float = 0f,   // 0 to 25
    val grayscale: Boolean = false,
    val sepia: Boolean = false,
    val invert: Boolean = false,
    val vignette: Float = 0f      // 0 to 1
)

data class ImageData(
    val imagePath: String = "",
    val base64Data: String? = null,
    val adjustments: ImageAdjustments = ImageAdjustments(),
    val aspectRatio: Float = 1f,
    // Non-destructive crop window, as fractions (0..1) of the source image.
    // (0,0,1,1) — the default — shows the full, uncropped image.
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
)

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

data class BrushStroke(
    val points: List<StrokePoint> = emptyList(),
    val strokeColor: Long = 0xFF38BDF8,
    val strokeWidth: Float = 8f,
    val isEraser: Boolean = false
)

data class BrushData(
    val strokes: List<BrushStroke> = emptyList()
)

data class PathAnchor(
    val x: Float,
    val y: Float,
    val handleInX: Float? = null,
    val handleInY: Float? = null,
    val handleOutX: Float? = null,
    val handleOutY: Float? = null
)

data class VectorPathData(
    val anchors: List<PathAnchor> = emptyList(),
    val isClosed: Boolean = true,
    val fillColor: Long = 0xFFEC4899,
    val strokeColor: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 3f
)

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Layer",
    val type: LayerType = LayerType.SHAPE,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1.0f,
    val blendMode: BlendModeType = BlendModeType.NORMAL,
    val x: Float = 100f,
    val y: Float = 100f,
    val width: Float = 300f,
    val height: Float = 200f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val textData: TextData? = null,
    val shapeData: ShapeData? = null,
    val imageData: ImageData? = null,
    val brushData: BrushData? = null,
    val vectorPathData: VectorPathData? = null,
    val animation: ElementAnimation = ElementAnimation()
)

data class ArtboardPage(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Page 1",
    val width: Int = 1080,
    val height: Int = 1080,
    val backgroundColor: Long = 0xFF181824,
    val backgroundGradientEnd: Long? = null,
    val layers: List<Layer> = emptyList()
)

data class ProjectDimensions(
    val name: String = "Square (1:1)",
    val width: Int = 1080,
    val height: Int = 1080
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Studio",
    val version: String = "1.0.0",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val author: String = "Photo Workspace Artist",
    val dimensions: ProjectDimensions = ProjectDimensions(),
    val pages: List<ArtboardPage> = listOf(ArtboardPage()),
    val activePageIndex: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)
