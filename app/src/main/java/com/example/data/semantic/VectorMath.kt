package com.example.data.semantic

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Numerical and serialization engine for semantic vectors.
 *
 * Enforces strict mathematical safety:
 * - Dimensionality mismatch detection
 * - Zero-vector protection (explicit rejection or typed handling)
 * - NaN and Infinity guards
 * - Deterministic, byte-ordered serialization
 */
object VectorMath {

    const val DEFAULT_EPSILON = 1e-9f

    fun validateVector(vector: FloatArray) {
        require(vector.isNotEmpty()) { "Vector cannot be empty" }
        for (i in vector.indices) {
            val v = vector[i]
            require(!v.isNaN()) { "Vector contains NaN at index $i" }
            require(!v.isInfinite()) { "Vector contains Infinity at index $i" }
        }
    }

    fun dotProduct(u: FloatArray, v: FloatArray): Float {
        validateVector(u)
        validateVector(v)
        require(u.size == v.size) {
            "Vector dimension mismatch: ${u.size} vs ${v.size}"
        }

        var sum = 0.0
        for (i in u.indices) {
            sum += u[i].toDouble() * v[i].toDouble()
        }
        val result = sum.toFloat()
        require(!result.isNaN() && !result.isInfinite()) {
            "Dot product calculation resulted in invalid numerical value: $result"
        }
        return result
    }

    fun magnitude(v: FloatArray): Float {
        validateVector(v)
        var sumSquares = 0.0
        for (i in v.indices) {
            val value = v[i].toDouble()
            sumSquares += value * value
        }
        val mag = sqrt(sumSquares).toFloat()
        require(!mag.isNaN() && !mag.isInfinite()) {
            "Magnitude calculation resulted in invalid numerical value: $mag"
        }
        return mag
    }

    fun cosineSimilarity(u: FloatArray, v: FloatArray, epsilon: Float = DEFAULT_EPSILON): Float {
        validateVector(u)
        validateVector(v)
        require(u.size == v.size) {
            "Vector dimension mismatch for cosine similarity: ${u.size} vs ${v.size}"
        }

        val magU = magnitude(u)
        val magV = magnitude(v)

        require(magU >= epsilon) {
            "Cannot compute cosine similarity with zero or near-zero vector u (magnitude $magU < $epsilon)"
        }
        require(magV >= epsilon) {
            "Cannot compute cosine similarity with zero or near-zero vector v (magnitude $magV < $epsilon)"
        }

        val dot = dotProduct(u, v)
        val denominator = magU * magV
        val sim = (dot / denominator).coerceIn(-1.0f, 1.0f)
        return sim
    }

    fun safeCosineSimilarity(u: FloatArray, v: FloatArray, epsilon: Float = DEFAULT_EPSILON): Float? {
        return try {
            cosineSimilarity(u, v, epsilon)
        } catch (_: Exception) {
            null
        }
    }

    fun l2Normalize(v: FloatArray, epsilon: Float = DEFAULT_EPSILON): FloatArray {
        validateVector(v)
        val mag = magnitude(v)
        require(mag >= epsilon) {
            "Cannot normalize zero or near-zero vector (magnitude $mag < $epsilon)"
        }

        val normalized = FloatArray(v.size)
        val invMag = 1.0 / mag.toDouble()
        for (i in v.indices) {
            normalized[i] = (v[i].toDouble() * invMag).toFloat()
        }
        return normalized
    }

    fun mean(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty()) { "Vector list cannot be empty" }
        val dim = vectors[0].size
        for (v in vectors) {
            require(v.size == dim) { "Vector dimension mismatch in mean calculation" }
        }

        val result = FloatArray(dim)
        for (d in 0 until dim) {
            var sum = 0.0
            for (v in vectors) {
                sum += v[d].toDouble()
            }
            result[d] = (sum / vectors.size).toFloat()
        }
        return result
    }

    /** Element-wise max across multiple vectors (max-pooling for multi-frame video). */
    fun maxPool(vectors: List<FloatArray>): FloatArray {
        require(vectors.isNotEmpty()) { "Vector list cannot be empty" }
        val dim = vectors[0].size
        for (v in vectors) {
            require(v.size == dim) { "Vector dimension mismatch in maxPool calculation" }
        }
        val result = FloatArray(dim)
        for (d in 0 until dim) {
            var m = vectors[0][d]
            for (i in 1 until vectors.size) {
                if (vectors[i][d] > m) m = vectors[i][d]
            }
            result[d] = m
        }
        return result
    }

    fun serialize(vector: FloatArray, byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN): ByteArray {
        validateVector(vector)
        val byteBuffer = ByteBuffer.allocate(vector.size * 4).order(byteOrder)
        for (f in vector) {
            byteBuffer.putFloat(f)
        }
        return byteBuffer.array()
    }

    fun deserialize(
        bytes: ByteArray,
        expectedDimension: Int? = null,
        byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
    ): FloatArray {
        require(bytes.isNotEmpty()) { "Serialized byte array cannot be empty" }
        require(bytes.size % 4 == 0) {
            "Invalid byte array length (${bytes.size}). Must be a multiple of 4."
        }

        val dim = bytes.size / 4
        if (expectedDimension != null) {
            require(dim == expectedDimension) {
                "Deserialization dimension mismatch. Expected $expectedDimension (${expectedDimension * 4} bytes), got $dim (${bytes.size} bytes)"
            }
        }

        val byteBuffer = ByteBuffer.wrap(bytes).order(byteOrder)
        val vector = FloatArray(dim)
        for (i in 0 until dim) {
            vector[i] = byteBuffer.float
        }
        validateVector(vector)
        return vector
    }
}
