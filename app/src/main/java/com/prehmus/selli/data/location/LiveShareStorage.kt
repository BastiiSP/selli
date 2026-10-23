package com.prehmus.selli.data.location

import com.prehmus.selli.domain.location.LiveShare

interface LiveShareStorage {
    fun read(): LiveShare?
    fun write(share: LiveShare?)
}
