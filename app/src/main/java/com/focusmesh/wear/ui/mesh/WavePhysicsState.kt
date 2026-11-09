package com.focusmesh.wear.ui.mesh

import kotlin.math.exp
import kotlin.math.sqrt

// Maintains a discrete 2D lattice of vertical displacements and velocities to simulate a taut sheet.
// When the user touches or drags on the watch face, a Gaussian impulse is injected into the lattice
// and propagates outward using a damped wave equation.
class WavePhysicsState(
    val gridSize: Int = 13
) {
    private val currentHeight = FloatArray(gridSize * gridSize)
    private val velocity = FloatArray(gridSize * gridSize)
    private val nextHeight = FloatArray(gridSize * gridSize)

    // Injects a smooth radial displacement centered at normalized coordinates in the range [-1.0, 1.0].
    fun applyTouchImpulse(normX: Float, normY: Float, strength: Float = 0.65f) {
        for (row in 0 until gridSize) {
            val gy = -1.0f + (2.0f * row) / (gridSize - 1)
            for (col in 0 until gridSize) {
                val gx = -1.0f + (2.0f * col) / (gridSize - 1)
                val dx = gx - normX
                val dy = gy - normY
                val distSq = dx * dx + dy * dy
                val impulse = strength * exp(-distSq * 9.5f)
                val idx = row * gridSize + col
                velocity[idx] = (velocity[idx] + impulse).coerceIn(-1.5f, 1.5f)
            }
        }
    }

    // Advances the discrete Laplacian spring-damper simulation by one frame step.
    fun stepSimulation() {
        val stiffness = 0.18f
        val restoringForce = 0.06f
        val damping = 0.91f

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
                nextHeight[idx] = (center + updatedVel).coerceIn(-1.2f, 1.2f)
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
