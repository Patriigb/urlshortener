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

    /**
     * Function to insert a command into the queue.
     * It is used by the producer.
     */
    fun insertCommand(name: String, function: suspend () -> Unit)

    /**
     * Function to take a command from the queue.
     * It is used by the consumer.
     */
    fun takeFromQueue() : suspend () -> Unit
    
    /**
     * Insert a command into the queue
     * @param name Name of the command
     * @param function Function to be executed
     */
    fun producerMethod(name: String, function: suspend () -> Unit)

    /**
     * Take a command from the queue and execute it
     */
    fun consumerMethod()

    /**
     * Take a command from the queue and execute it
     */
    fun consumerMethod2()
}

/**
 * Implementation of [QueueController].
 */
class QueueControllerImpl : QueueController {
    private val cola: BlockingQueue<Pair<String, suspend () -> Unit>> = LinkedBlockingQueue()
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun insertCommand(name: String, function: suspend () -> Unit) {
        log.info("Comando insertado: $name")
        cola.put(name to function)
    }

    override fun takeFromQueue() : suspend () -> Unit {
        val (tag, comando) = cola.take()
        log.info("Consumidor ejecutando comando: $tag")
        return comando
    }

    @Async
    override fun producerMethod(name: String, function: suspend () -> Unit) {
        insertCommand(name, function)
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
