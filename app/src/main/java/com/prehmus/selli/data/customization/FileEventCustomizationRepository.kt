package com.prehmus.selli.data.customization

import android.content.Context
import com.prehmus.selli.domain.model.CustomizationTarget
import com.prehmus.selli.domain.model.EventCustomization
import com.prehmus.selli.domain.repository.EventCustomizationRepository
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FileEventCustomizationRepository(
    directory: File,
    fileName: String = DEFAULT_FILE_NAME,
    private val codec: EventCustomizationCodec = EventCustomizationCodec(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EventCustomizationRepository {
    private val file = File(directory, fileName)
    private val mutex = Mutex()

    constructor(
        context: Context,
        codec: EventCustomizationCodec = EventCustomizationCodec(),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        directory = context.applicationContext.filesDir,
        codec = codec,
        ioDispatcher = ioDispatcher,
    )

    override suspend fun save(customization: EventCustomization): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = readFromDisk().toMutableList()
            val existingIndex = current.indexOfFirst { it.target == customization.target }
            if (existingIndex >= 0) {
                current[existingIndex] = customization
            } else {
                current += customization
            }
            writeAtomically(current)
        }
    }

    override suspend fun remove(target: CustomizationTarget): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val current = readFromDisk()
            val retained = current.filterNot { it.target == target }
            if (retained.size != current.size) {
                writeAtomically(retained)
            }
        }
    }

    override suspend fun all(): List<EventCustomization> = withContext(ioDispatcher) {
        mutex.withLock { readFromDisk() }
    }

    private fun readFromDisk(): List<EventCustomization> =
        if (file.isFile) codec.decode(file.readText(Charsets.UTF_8)) else emptyList()

    private fun writeAtomically(customizations: List<EventCustomization>) {
        val parent = requireNotNull(file.parentFile) { "Customization file needs a parent directory." }
        check(parent.isDirectory || parent.mkdirs()) {
            "Could not create customization directory '${parent.absolutePath}'."
        }

        val temporaryFile = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            FileOutputStream(temporaryFile).use { output ->
                output.write(codec.encode(customizations).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            moveReplacing(temporaryFile, file)
        } finally {
            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "event_customizations.json"
    }
}
