package com.owla19s.bridgefs

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinaryFileDetectorTest {
    @Test
    fun skipsBinaryByExtension() {
        val file = File.createTempFile("bridgefs-binary-", ".png")
        try {
            file.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            assertTrue(BinaryFileDetector.isBinary(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun skipsBinaryByNulByte() {
        val file = File.createTempFile("bridgefs-binary-", ".dat")
        try {
            file.writeBytes(byteArrayOf(0x41, 0x00, 0x42))
            assertTrue(BinaryFileDetector.isBinary(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun keepsUtf8Text() {
        val file = File.createTempFile("bridgefs-text-", ".txt")
        try {
            file.writeText("BridgeFS 文本内容")
            assertFalse(BinaryFileDetector.isBinary(file))
        } finally {
            file.delete()
        }
    }
}
