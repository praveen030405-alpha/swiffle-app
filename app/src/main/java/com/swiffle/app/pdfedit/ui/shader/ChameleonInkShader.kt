package com.swiffle.app.pdfedit.ui.shader

import android.graphics.Color as AndroidColor
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * AGSL Physical Ink RuntimeShader:
 * Modulates live typography with physical ink properties:
 * - Sub-pixel morphological dilation/erosion
 * - Scanner optical point-spread function (edge softness)
 * - Subtractive ink pigment blending
 */
object ChameleonInkShader {

    private const val AGSL_SOURCE = """
        uniform shader image;
        uniform float subPixelThickness;
        uniform float inkBleedAmount;
        uniform float scannerSoftness;
        uniform vec3 sampledInkColor;
        uniform float paperGrainVariance;

        vec4 main(float2 fragCoord) {
            vec4 src = image.eval(fragCoord);
            if (src.a <= 0.002) {
                return src;
            }

            // Morphological thickness modulation
            float alpha = clamp(src.a * (1.0 + subPixelThickness * 0.35), 0.0, 1.0);

            // Optical scanner softness transition
            if (scannerSoftness > 0.08) {
                float edgeSoft = smoothstep(0.02, 0.98 - (scannerSoftness * 0.15), alpha);
                alpha = mix(alpha, edgeSoft, clamp(scannerSoftness * 0.45, 0.0, 1.0));
            }

            // Micro-grain variance on high zoom
            if (paperGrainVariance > 0.01) {
                float noise = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
                alpha = clamp(alpha + (noise - 0.5) * paperGrainVariance * 0.08, 0.0, 1.0);
            }

            // Subtractive chromaticity
            vec3 finalRgb = sampledInkColor * alpha;
            return vec4(finalRgb, alpha);
        }
    """

    fun createInkRenderEffect(
        subPixelThickness: Float = 0f,
        inkBleedAmount: Float = 0f,
        scannerSoftness: Float = 0.3f,
        inkColor: Color = Color(0xFF1E1E1E),
        paperGrainVariance: Float = 0.02f
    ): androidx.compose.ui.graphics.RenderEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return try {
                val shader = RuntimeShader(AGSL_SOURCE)
                shader.setFloatUniform("subPixelThickness", subPixelThickness.coerceIn(-1.0f, 1.0f))
                shader.setFloatUniform("inkBleedAmount", inkBleedAmount.coerceIn(0.0f, 1.0f))
                shader.setFloatUniform("scannerSoftness", scannerSoftness.coerceIn(0.0f, 2.5f))
                shader.setFloatUniform(
                    "sampledInkColor",
                    inkColor.red,
                    inkColor.green,
                    inkColor.blue
                )
                shader.setFloatUniform("paperGrainVariance", paperGrainVariance.coerceIn(0.0f, 0.1f))

                val renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "image")
                renderEffect.asComposeRenderEffect()
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }
}

