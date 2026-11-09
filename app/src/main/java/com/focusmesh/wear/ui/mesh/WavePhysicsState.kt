package com.focusmesh.wear.ui.mesh

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

// Maintains a discrete 2D lattice of vertical displacements and velocities to simulate a taut full-screen surface.
// Touching or dragging injects a Gaussian impulse, while starting or pausing the timer triggers a radial ring wave.
class WavePhysicsState(
    val gridSize: Int = 17
) {
    private val currentHeight = FloatArray(gridSize * gridSize)
    private val velocity = FloatArray(gridSize * gridSize)
    private val nextHeight = FloatArray(gridSize * gridSize)

    // Injects a smooth radial displacement centered at normalized screen coordinates in the range [-1.0, 1.0].
    fun applyTouchImpulse(normX: Float, normY: Float, strength: Float = 0.65f) {
        for (row in 0 until gridSize) {
            val gy = -1.0f + (2.0f * row) / (gridSize - 1)
            for (col in 0 until gridSize) {
                val gx = -1.0f + (2.0f * col) / (gridSize - 1)
                val dx = gx - normX
                val dy = gy - normY
                val distSq = dx * dx + dy * dy
                val impulse = strength * exp(-distSq * 7.5f)
                val idx = row * gridSize + col
                velocity[idx] = (velocity[idx] + impulse).coerceIn(-1.6f, 1.6f)
            }
        }
    }

    // Triggers a concentric ring shockwave from the center of the watch face when the timer starts or pauses.
    fun triggerStateTransitionWave(intensity: Float = 0.85f) {
        for (row in 0 until gridSize) {
            val gy = -1.0f + (2.0f * row) / (gridSize - 1)
            for (col in 0 until gridSize) {
                val gx = -1.0f + (2.0f * col) / (gridSize - 1)
                val r = sqrt(gx * gx + gy * gy)
                val ringWave = intensity * cos(r * 4.5f) * exp(-r * 2.2f)
                val idx = row * gridSize + col
                velocity[idx] = (velocity[idx] + ringWave).coerceIn(-1.6f, 1.6f)
            }
        }
    }

    // Advances the discrete Laplacian spring-damper simulation by one frame step.
    fun stepSimulation() {
        val stiffness = 0.20f
        val restoringForce = 0.055f
        val damping = 0.92f

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val idx = row * gridSize + col
                val center = currentHeight[idx]

                var neighborSum = 0.0f
                var neighborCount = 0
                if (row > 0) {
                    neighborSum += currentHeight[(row - 1) * gridSize + col]
                    neighborCount++
                }
                if (row < gridSize - 1) {
                    neighborSum += currentHeight[(row + 1) * gridSize + col]
                    neighborCount++
                }
                if (col > 0) {
                    neighborSum += currentHeight[row * gridSize + (col - 1)]
                    neighborCount++
                }
                if (col < gridSize - 1) {
                    neighborSum += currentHeight[row * gridSize + (col + 1)]
                    neighborCount++
                }

                val laplacian = (neighborSum / neighborCount.coerceAtLeast(1)) - center
                val accel = (stiffness * laplacian) - (restoringForce * center)
                val updatedVel = (velocity[idx] + accel) * damping
                velocity[idx] = updatedVel
                nextHeight[idx] = (center + updatedVel).coerceIn(-1.3f, 1.3f)
            }
        }

        System.arraycopy(nextHeight, 0, currentHeight, 0, currentHeight.size)
    }

    // Returns the current displacement at a specific lattice vertex.
    fun displacementAt(row: Int, col: Int): Float {
        return currentHeight[row * gridSize + col]
    }

    // Clears all active ripples when entering ambient or sleep mode.
    fun clear() {
        currentHeight.fill(0.0f)
        velocity.fill(0.0f)
        nextHeight.fill(0.0f)
    }
}
