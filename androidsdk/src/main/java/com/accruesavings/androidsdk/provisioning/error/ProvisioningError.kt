package com.accruesavings.androidsdk.provisioning.error

/**
 * Represents an error that occurred during provisioning operations
 */
data class ProvisioningError(
    val code: String,
    val message: String,
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Convert to JSON representation for JavaScript bridge
     */
    fun toJson(): String {
        return """
        {
            "error": true,
            "code": "$code",
            "message": "$message",
            "details": ${if (details != null) "\"$details\"" else "null"},
            "timestamp": $timestamp
        }
        """.trimIndent()
    }
    
    override fun toString(): String {
        return "ProvisioningError(code='$code', message='$message', details=$details)"
    }
} 