package ai.opencode.platform.pty

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JNI bridge to native PTY operations.
 * Uses openpty()/forkpty() via libpty.so.
 */
class NativePTY {

    companion object {
        init {
            System.loadLibrary("pty")
        }

        /**
         * PTY poll results
         */
        const val POLL_READY = 1
        const val POLL_HANGUP = 2
        const val POLL_TIMEOUT = 0
        const val POLL_ERROR = -1
    }

    external fun nativeCreate(
        cmd: String,
        cwd: String?,
        args: Array<String>?,
        envVars: Array<String>?,
        rows: Int,
        cols: Int
    ): Int

    external fun nativeRead(fd: Int, buffer: ByteArray, offset: Int, length: Int): Int

    external fun nativeWrite(fd: Int, data: ByteArray, offset: Int, length: Int): Int

    external fun nativeSetSize(fd: Int, cols: Int, rows: Int)

    external fun nativeWaitForExit(pid: Int): Int

    external fun nativeClose(fd: Int)

    external fun nativeKill(pid: Int)

    external fun nativeGetAvailable(fd: Int): Int

    external fun nativePoll(fd: Int, timeoutMs: Int): Int

    /**
     * Create a new PTY session.
     * Returns a handle with master FD and child PID.
     */
    suspend fun create(
        command: String,
        cwd: String? = null,
        args: Array<String>? = null,
        envVars: Array<String>? = null,
        rows: Int = 24,
        cols: Int = 80
    ): PtyHandle = withContext(Dispatchers.IO) {
        val fd = nativeCreate(command, cwd, args, envVars, rows, cols)
        if (fd < 0) {
            throw PTYException("Failed to create PTY for command: $command")
        }
        PtyHandle(fd = fd)
    }

    /**
     * Read from PTY master.
     */
    suspend fun read(handle: PtyHandle, buffer: ByteArray = ByteArray(8192)): ByteArray =
        withContext(Dispatchers.IO) {
            val n = nativeRead(handle.fd, buffer, 0, buffer.size)
            if (n < 0) throw PTYException("PTY read failed")
            buffer.copyOf(n)
        }

    /**
     * Write to PTY master.
     */
    suspend fun write(handle: PtyHandle, data: ByteArray): Int = withContext(Dispatchers.IO) {
        nativeWrite(handle.fd, data, 0, data.size)
    }

    /**
     * Write string to PTY.
     */
    suspend fun write(handle: PtyHandle, text: String): Int {
        return write(handle, text.toByteArray(Charsets.UTF_8))
    }

    /**
     * Resize the PTY.
     */
    fun resize(handle: PtyHandle, cols: Int, rows: Int) {
        nativeSetSize(handle.fd, cols, rows)
    }

    /**
     * Wait for the child process to exit.
     */
    suspend fun waitForExit(handle: PtyHandle): Int = withContext(Dispatchers.IO) {
        nativeWaitForExit(0)
    }

    /**
     * Close the PTY master FD.
     */
    fun close(handle: PtyHandle) {
        nativeClose(handle.fd)
    }

    /**
     * Kill the child process.
     */
    fun kill(handle: PtyHandle, pid: Int) {
        nativeKill(pid)
    }

    /**
     * Check if data is available for reading.
     */
    fun available(handle: PtyHandle): Int {
        return nativeGetAvailable(handle.fd)
    }

    /**
     * Poll for PTY activity.
     */
    fun poll(handle: PtyHandle, timeoutMs: Int = 100): Int {
        return nativePoll(handle.fd, timeoutMs)
    }
}

data class PtyHandle(val fd: Int)

class PTYException(message: String) : Exception(message)
