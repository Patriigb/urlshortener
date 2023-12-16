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

data class ServerMessage(
    val type: String? = null,
    val body: String? = null
)

data class WsData(
    val urls: List<String>,
    val generateQr: Boolean
)

class RemoteAddressHandshakeInterceptor : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        // Obtener la dirección remota del cliente desde la solicitud y almacenarla como un atributo de la sesión
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

    @MessageMapping("/csv")
    fun fastBulk(data: WsData, accessor: SimpMessageHeaderAccessor) {

        val uris = data.urls
        val checkQr = data.generateQr
        val localAddr = accessor.sessionAttributes?.get("localAddr") as InetAddress
        val port = accessor.sessionAttributes?.get("port").toString()
        val username = "user${accessor.sessionId}"
        log.info("Mensaje recibido de $username")

        for (uri in uris) {
            val (originalUri, shortenedUri, errorMessage) = urlShortenerControllerImpl.shortUrl(
                uri, null, null, true, localAddr, port
            )
            var msg: String
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
                    } else {
                        "$originalUri >>> $shortenedUri"
                    }
                }

                msg = result.getOrElse {
                    "Ha ocurrido un error: ${it.message}"
                }
            } else {
                msg = "Ha ocurrido un error: $errorMessage"
            }
            messagingTemplate.convertAndSend("/queue/csv-$username", ServerMessage("server", msg))
        }
    }

    @SubscribeMapping("/queue/csv")
    fun handleSubscribeEvent(): ServerMessage {
        log.info("Suscripcion")
        return ServerMessage("server", "¡Hola! Escribe una o varias urls separadas por espacios.")
    }
}

