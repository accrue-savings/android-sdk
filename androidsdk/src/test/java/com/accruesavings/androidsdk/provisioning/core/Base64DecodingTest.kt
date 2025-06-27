package com.accruesavings.androidsdk.provisioning.core

import java.util.Base64
import org.junit.Test
import org.junit.Assert.*

/**
 * Test class for Base64 decoding functionality used in push provisioning
 * This test verifies that the Base64 decoding approach is correct
 */
class Base64DecodingTest {
    
    @Test
    fun testBase64Decoding() {
        // Test data: "Hello World" encoded in Base64
        val testString = "Hello World"
        val base64Encoded = Base64.getEncoder().encodeToString(testString.toByteArray())
        
        // Decode using the same approach as in PushProvisioningService
        val decodedBytes = Base64.getDecoder().decode(base64Encoded)
        val decodedString = String(decodedBytes)
        
        // Verify the result
        assertEquals("Decoded string should match original", testString, decodedString)
        assertArrayEquals("Decoded bytes should match original", testString.toByteArray(), decodedBytes)
    }
    
    @Test
    fun testEmptyBase64String() {
        val decodedBytes = Base64.getDecoder().decode("")
        assertEquals("Empty string should return empty byte array", 0, decodedBytes.size)
    }
    
    @Test
    fun testInvalidBase64String() {
        // Test with invalid Base64 string
        val invalidBase64 = "InvalidBase64String!!!"
        
        try {
            val decodedBytes = Base64.getDecoder().decode(invalidBase64)
            // If we get here, the method should handle the exception gracefully
            assertNotNull("Should return some result", decodedBytes)
        } catch (e: Exception) {
            // It's also acceptable for Base64.decode to throw an exception for invalid input
            assertTrue("Exception should be related to invalid Base64", 
                e.message?.contains("Base64") == true || e is IllegalArgumentException)
        }
    }
    
    @Test
    fun testMarqetaStyleBase64Decoding() {
        // Simulate what Marqeta might send - a more complex string
        val originalData = "This is test payment card data from Marqeta"
        val base64Encoded = Base64.getEncoder().encodeToString(originalData.toByteArray())
        
        val decodedBytes = Base64.getDecoder().decode(base64Encoded)
        val decodedString = String(decodedBytes)
        
        assertEquals("Marqeta-style data should decode correctly", originalData, decodedString)
    }
    
    @Test
    fun testComplexPaymentData() {
        // Simulate complex payment card data that might come from Marqeta
        val complexData = """
            {
                "cardNumber": "4111111111111111",
                "expiryMonth": "12",
                "expiryYear": "2025",
                "cvv": "123",
                "cardholderName": "John Doe"
            }
        """.trimIndent()
        
        val base64Encoded = Base64.getEncoder().encodeToString(complexData.toByteArray())
        val decodedBytes = Base64.getDecoder().decode(base64Encoded)
        val decodedString = String(decodedBytes)
        
        assertEquals("Complex payment data should decode correctly", complexData, decodedString)
    }
    
    @Test
    fun testBinaryData() {
        // Test with binary data that might be in the OPC
        val binaryData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val base64Encoded = Base64.getEncoder().encodeToString(binaryData)
        val decodedBytes = Base64.getDecoder().decode(base64Encoded)
        
        assertArrayEquals("Binary data should decode correctly", binaryData, decodedBytes)
    }
    
    @Test
    fun testBase64WithPadding() {
        // Test Base64 strings with padding characters
        val testData = "Test data with padding"
        val base64Encoded = Base64.getEncoder().encodeToString(testData.toByteArray())
        
        // Ensure the encoded string has proper padding
        assertTrue("Base64 string should have proper padding", 
            base64Encoded.endsWith("=") || base64Encoded.length % 4 == 0)
        
        val decodedBytes = Base64.getDecoder().decode(base64Encoded)
        val decodedString = String(decodedBytes)
        
        assertEquals("Padded Base64 should decode correctly", testData, decodedString)
    }
    
    @Test
    fun testMarqetaOpcDecoding() {
        // Test with a realistic OPC (Opaque Payment Card) data that Marqeta might send
        val opcData = "eyJjYXJkTnVtYmVyIjoiNDExMTExMTExMTExMTExIiwiaXNzdWVyIjoiVmlzYSIsIm5ldHdvcmsiOiJWaXNhIn0="
        
        val decodedBytes = Base64.getDecoder().decode(opcData)
        val decodedString = String(decodedBytes)
        
        // This should decode to a JSON string
        assertTrue("Decoded OPC should contain card data", decodedString.contains("cardNumber"))
        assertTrue("Decoded OPC should contain issuer info", decodedString.contains("issuer"))
        assertTrue("Decoded OPC should contain network info", decodedString.contains("network"))
    }
} 