@file:Suppress("MatchingDeclarationName", "WildcardImport")

package es.unizar.urlshortener

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.concurrent.CountDownLatch
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UrlShortenerControllerWsTest {
    @LocalServerPort
    private val port: Int = 0

    @Test
    fun websocketTest() {
        val latch = CountDownLatch(4)
        val list = mutableListOf<String>()
        val stompClient = WebSocketStompClient(SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient()))))
        val stompSession: StompSession = stompClient.connectAsync(
            "ws://localhost:$port/api/fast-bulk",
            object : StompSessionHandlerAdapter() {
            }
        ).get()
        stompSession.subscribe(
            "/topic/csv",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type {
                    return ByteArray::class.java
                }

                override fun handleFrame(headers: StompHeaders, payload: Any?) {
                    if (payload is ByteArray) {
                        list.add(String(payload))
                        latch.countDown()
                    }
                }
            }
        )
        val msg = """{"urls": ["http://example.com/"], "generateQr": false}"""
        stompSession.send("/topic/csv", msg.toByteArray())
        latch.await()
        assertTrue(list.size >= 4)
        // Mensaje al suscribirse
        assertTrue(list.contains("""{"type":"server","body":"¡Hola! Escribe las urls separadas por espacios."}"""))
        assertTrue(list.contains(
            """{"type":"server","body":"http://example.com/ >>> http://localhost:8080/f684a3c4"}"""
        ))
    }
}
