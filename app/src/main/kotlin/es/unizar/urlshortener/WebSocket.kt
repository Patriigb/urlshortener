@file:Suppress("LongParameterList", "TooGenericExceptionCaught")
package es.unizar.urlshortener

import es.unizar.urlshortener.core.QueueController
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.CreateQrUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.stereotype.Controller
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.server.HandshakeInterceptor
import es.unizar.urlshortener.core.usecases.CreateShortUrlUseCase
import es.unizar.urlshortener.infrastructure.delivery.UrlShortenerControllerImpl
import java.net.InetAddress

/**
 * Data class to represent a message sent from the server to the client.
 * @property type Type of the message.
 * @property body Body of the message.
 */
data class ServerMessage(
    val type: String? = null,
    val body: String? = null
)

/**
 * Data class to represent a message sent from the client to the server.
 * @property urls List of urls to be shortened.
 * @property generateQr Whether to generate a QR code for each shortened url.
 */
data class WsData(
    val urls: List<String>,
    val generateQr: Boolean
)

/**
 * Interceptor to add the remote address, port and session id to the 
 * attributes of the handshake.
 */
class RemoteAddressHandshakeInterceptor : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        log.info("Guardando datos...")
        attributes["localAddr"] = request.localAddress.address
        attributes["port"] = request.localAddress.port
        val servletRequest = request as ServletServerHttpRequest
        attributes["sessionId"] = servletRequest.servletRequest.session.id
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        log.info("WebSocket handshake completed successfully.")
    }
}

/**
 * Configuration class for the WebSocket.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue")
        config.setApplicationDestinationPrefixes("/app","/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/api/fast-bulk")
            .withSockJS()
            .setInterceptors(RemoteAddressHandshakeInterceptor())
    }
}

/**
 * Controller for the WebSocket.
 */
@Controller
class WebSocket(
    val createShortUrlUseCase: CreateShortUrlUseCase,
    val queueController: QueueController,
    val createQrUseCase: CreateQrUseCase,
    val urlShortenerControllerImpl: UrlShortenerControllerImpl
) {
    @Autowired
    private lateinit var messagingTemplate: SimpMessagingTemplate

    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Handler for the WebSocket.
     * @param data Data sent from the client.
     * @param accessor Accessor to the headers of the message.
     */
    @MessageMapping("/csv")
    fun fastBulk(data: WsData, accessor: SimpMessageHeaderAccessor) {

        val uris = data.urls
        val checkQr = data.generateQr
        val localAddr = accessor.sessionAttributes?.get("localAddr") as InetAddress
        val port = accessor.sessionAttributes?.get("port").toString()
        val username = "user${accessor.sessionId}"
        log.info("Mensaje recibido de $username")

        // It is possible to receive one or more urls
        for (uri in uris) {
            var msg: String
            val (originalUri, shortenedUri, errorMessage) = 
                urlShortenerControllerImpl.shortUrl(uri, null, null, true, localAddr, port)
            if (errorMessage.isBlank()) {
                val result = runCatching {
                    val shortUrl = createShortUrlUseCase.create(
                        url = originalUri,
                        data = ShortUrlProperties(
                            ip = localAddr.toString(),
                            qr = checkQr
                        )
                    )

                    if (checkQr) {
                        val urlQr = "$shortenedUri/qr"
                        queueController.producerMethod("generateQRCode") {
                            createQrUseCase.generateQRCode(urlQr, shortUrl)
                        }
                        "$originalUri >>> $shortenedUri >>> $urlQr"
                    } else { "$originalUri >>> $shortenedUri" }
                }
                msg = result.getOrElse { "Ha ocurrido un error: ${it.message}" }

            } else { msg = "Ha ocurrido un error: $errorMessage" }

            // Send short url to the client
            messagingTemplate.convertAndSend("/queue/csv-$username", ServerMessage("server", msg))
        }
    }

    /**
     * Handler for the subscription to the WebSocket.
     */
    @SubscribeMapping("/queue/csv")
    fun handleSubscribeEvent(): ServerMessage {
        log.info("Suscripcion nueva")
        return ServerMessage("server", "¡Hola! Escribe una o varias urls separadas por espacios.")
    }
}

