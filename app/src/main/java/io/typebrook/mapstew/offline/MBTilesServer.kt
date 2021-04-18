package io.typebrook.mapstew.offline

import timber.log.Timber
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.PrintStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.math.pow

/*
 * Localhost tile server with MBTilesSource
 */

object MBTilesServer : Runnable {

    const val port = 8888
    private val serverSocket: ServerSocket = ServerSocket(port)
    var isRunning = false
    val sources: MutableMap<String, MBTilesSource> = mutableMapOf()

    fun start() {
        isRunning = true
        Thread(this).start()
    }

    fun stop() {
        isRunning = false
        serverSocket.close()
    }

    override fun run() {
        try {
            while (isRunning) {
                serverSocket.accept().use { socket ->
                    Timber.d("Handling request")
                    handle(socket)
                    Timber.d("Request handled")
                }
            }
        } catch (e: Exception) {
            Timber.d(e.localizedMessage ?: "Exception while running MBTilesServer")
        } finally {
            Timber.d("request handled")
        }
    }

    @Throws
    private fun handle(socket: Socket) {

        val reader: BufferedReader = socket.getInputStream().reader().buffered()
        // Output stream that we send the response to
        val output = PrintStream(socket.getOutputStream())

        try {
            var route: String? = null

            // Read HTTP headers and parse out the route.
            do {
                val line = reader.readLine() ?: ""
                if (line.startsWith("GET")) {
                    // the format for route should be {source}/{z}/{x}/{y}
                    route = line.substringAfter("GET /").substringBefore(".")
                    break
                }
            } while (line.isNotEmpty())

            // Prepare the content to send.
            if (route == null) {
                writeServerError(output)
                return
            }

            // the source which this request target to
            val sourceId = route.substringBefore("/")
            val source = sources[sourceId]

            if (source == null) {
                writeServerError(output, "No such this source: $sourceId")
                return
            }

            val bytes = loadContent(source, route) ?: run {
                writeServerError(output)
                return
            }

            // Send out the content.
            with(output) {
                println("HTTP/1.0 200 OK")
                println("Content-Type: " + detectMimeType(source.format))
                println("Content-Length: " + bytes.size)
//                if (source.isVector) println("Content-Encoding: gzip")
                println()
                write(bytes)
                flush()
            }
            Timber.d("send $route")
        } finally {
            reader.close()
            output.close()
        }
    }

    @Throws
    private fun loadContent(source: MBTilesSource, route: String): ByteArray? = try {
        val (z, x, y) = route.split("/").subList(1, 4).map { it.toInt() }
        source.getTile(z, x, (2.0.pow(z)).toInt() - 1 - y)
    } catch (e: FileNotFoundException) {
        e.printStackTrace()
        null
    }

    private fun writeServerError(output: PrintStream, message: String? = null) {
        output.println("HTTP/1.0 500 Internal Server Error")
        if (message != null) output.println(message)
        output.flush()
        Timber.d("Internal Server Error")
    }

    private fun detectMimeType(format: String): String = when (format) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "mvt" -> "application/x-protobuf"
        "pbf" -> "application/x-protobuf"
        else -> "application/octet-stream"
    }
}