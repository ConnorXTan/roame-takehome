package travel.roame.takehome.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.File
import kotlinx.coroutines.delay

/** How GET /flights behaves. Everything except [NORMAL] exists to exercise client failure handling. */
enum class Mode { NORMAL, EMPTY, ERROR, MALFORMED, SLOW }

fun Application.flightsApi(fixture: ByteArray, mode: Mode) {
    routing {
        get("/flights") {
            when (mode) {
                Mode.NORMAL -> call.respondBytes(fixture, ContentType.Application.Json)
                Mode.EMPTY -> call.respondText("""{"flights":[]}""", ContentType.Application.Json)
                Mode.ERROR -> call.respondText("simulated outage", status = HttpStatusCode.InternalServerError)
                Mode.MALFORMED -> call.respondText("""{"flights":[{"id":""", ContentType.Application.Json)
                Mode.SLOW -> {
                    delay(4_000)
                    call.respondBytes(fixture, ContentType.Application.Json)
                }
            }
        }
    }
}

fun main() {
    val mode = Mode.valueOf((System.getenv("FLIGHTS_MODE") ?: "normal").uppercase())
    val port = (System.getenv("PORT") ?: "8080").toInt()
    val fixture = File("fixtures/flights.json").readBytes()
    println("Flight API on http://0.0.0.0:$port/flights (mode=${mode.name.lowercase()})")
    embeddedServer(CIO, port = port, host = "0.0.0.0") { flightsApi(fixture, mode) }.start(wait = true)
}
