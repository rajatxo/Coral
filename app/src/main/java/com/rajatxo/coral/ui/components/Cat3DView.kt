package com.rajatxo.coral.ui.components

import android.view.Choreographer
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.utils.ModelViewer

/**
 * ★ Cat3DView — renders a 3D cat model (GLB) using Filament's ModelViewer.
 *
 * The cat sits on the right side of the greeting header.
 * Auto-plays any built-in animations from the GLB file.
 */
@Composable
fun Cat3DView(
    modifier: Modifier = Modifier,
    autoRotate: Boolean = true
) {
    AndroidView(
        factory = { ctx ->
            val surfaceView = SurfaceView(ctx)

            // ★ ModelViewer handles ALL Filament boilerplate
            val modelViewer = ModelViewer(surfaceView = surfaceView)

            // Load GLB from assets — convert ByteArray to ByteBuffer
            val buffer = ctx.assets.open("cat_figure.glb").use { input ->
                java.nio.ByteBuffer.wrap(input.readBytes())
            }
            modelViewer.loadModelGlb(buffer)
            modelViewer.transformToUnitCube()

            // Animation loop
            val choreographer = Choreographer.getInstance()
            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    modelViewer.animator?.apply {
                        if (animationCount > 0) {
                            applyAnimation(0, frameTimeNanos / 1_000_000_000f)
                            updateBoneMatrices()
                        }
                    }
                    modelViewer.render(frameTimeNanos)
                    choreographer.postFrameCallback(this)
                }
            }
            choreographer.postFrameCallback(frameCallback)

            surfaceView
        },
        modifier = modifier
    )
}
