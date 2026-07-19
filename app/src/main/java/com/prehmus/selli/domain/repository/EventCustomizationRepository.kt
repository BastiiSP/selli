package com.prehmus.selli.domain.repository

import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.EventCustomization

interface EventCustomizationRepository {
    /** Ersetzt eine bestehende Customization mit demselben Target. */
    suspend fun save(customization: EventCustomization)

    suspend fun remove(target: CustomizationTarget)

    suspend fun all(): List<EventCustomization>
}

object NoOpEventCustomizationRepository : EventCustomizationRepository {
    override suspend fun save(customization: EventCustomization) = Unit

    override suspend fun remove(target: CustomizationTarget) = Unit

    override suspend fun all(): List<EventCustomization> = emptyList()
}
