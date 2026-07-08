package com.accruesavings.androidsdk

data class AccrueSignInPayload(
    val id: String,
    val referenceId: String?,
    val stableReferenceId: String?,
    val effectiveReferenceId: String?,
    val isNewUser: Boolean,
)
