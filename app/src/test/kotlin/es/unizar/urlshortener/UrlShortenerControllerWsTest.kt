package es.unizar.urlshortener

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
            "/user/queue/csv",
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
        var msg = """{"urls": ["http://example.com/"], "generateQr": false}"""
        stompSession.send("/app/csv", msg.toByteArray())
        msg = """{"urls": ["http://www.unizar.com/"], "generateQr": true}"""
        stompSession.send("/app/csv", msg.toByteArray())
        msg = """{"urls": ["a"], "generateQr": false}"""
        stompSession.send("/app/csv", msg.toByteArray())
        latch.await()
        assertTrue(list.size >= 4)
        assertTrue(list.contains(
            """{"type":"server","body":"¡Hola! Escribe una o varias urls separadas por espacios."}"""
        ))
        assertTrue(list.contains(
            """{"type":"server","body":"http://example.com/ >>> http://127.0.0.1/f684a3c4"}"""
        ))
        val uri = "http://127.0.0.1/b8580acd"
        assertTrue(list.contains(
            """{"type":"server","body":"http://www.unizar.com/ >>> $uri >>> $uri/qr"}"""
        ))
        assertTrue(list.contains(
            """{"type":"server","body":"Ha ocurrido un error: [a] does not follow a supported schema"}"""
        ))

        Thread.sleep(5000)
        stompSession.disconnect()
    }
}
