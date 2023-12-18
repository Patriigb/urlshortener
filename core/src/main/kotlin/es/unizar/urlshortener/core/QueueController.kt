package es.unizar.urlshortener.core

import java.util.concurrent.BlockingQueue
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.LinkedBlockingQueue
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 *  Concurrent Java Queues Controller
 */
interface QueueController {
    fun insertarComando(nombre: String, funcion: suspend () -> Unit)

    fun takeFromQueue() : suspend () -> Unit
    
    fun producerMethod(nombre: String, funcion: suspend () -> Unit)

    fun consumerMethod()

    fun consumerMethod2()
}

/**
 * Implementation of [QueueController].
 */
class QueueControllerImpl : QueueController {
    private val cola: BlockingQueue<Pair<String, suspend () -> Unit>> = LinkedBlockingQueue()
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun insertarComando(nombre: String, funcion: suspend () -> Unit) {
        // Inserta el comando en la cola bloqueante
        log.info("Comando insertado: $nombre")
        cola.put(nombre to funcion)
    }
    
    override fun takeFromQueue() : suspend () -> Unit {
        // Obtiene el comando de la cola
        val (tag, comando) = cola.take()
        log.info("Consumidor ejecutando comando: $tag")
        return comando
    }

    @Async
    override fun producerMethod(nombre: String, funcion: suspend () -> Unit) {
        insertarComando(nombre,funcion)
    }

    override fun consumerMethod() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val command = takeFromQueue()
                command.invoke()
            }
        }
    }

    override fun consumerMethod2() {
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val command = takeFromQueue()
                command.invoke()
            }
        }
    }

    init {
        runBlocking {
            consumerMethod()
            consumerMethod2()
        }
    }
}
