package com.prehmus.selli.data.google

import android.content.Intent
import com.prehmus.selli.domain.model.CalendarAuthRequiredException

class GoogleRecoverableAuthException(
    val recoveryIntent: Intent,
    message: String,
    cause: Throwable,
) : CalendarAuthRequiredException(message, cause)
