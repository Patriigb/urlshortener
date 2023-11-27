package es.unizar.urlshortener.core

import java.util.concurrent.BlockingQueue
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job


interface QueueController {
    fun insertarComando(nombre: String, funcion: suspend () -> Unit)

    fun takeFromQueue() : suspend () -> Unit
    
    fun producerMethod(nombre: String, funcion: suspend () -> Unit)

    fun consumerMethod()

    fun consumerMethod2()
}

@EnableScheduling
@Component
class QueueControllerImpl : QueueController {
    val cola: BlockingQueue<Pair<String, suspend () -> Unit>> = LinkedBlockingQueue()

    override fun insertarComando(nombre: String, funcion: suspend () -> Unit) {
        // Inserta el comando en la cola bloqueante
        println("Comando insertado: $nombre")
        cola.put(nombre to funcion)
    }

    
    override fun takeFromQueue() : suspend () -> Unit {
        // var comando: suspend () -> Unit? = null
        // try {
           // while (true) {
                // Bloquea hasta que haya un elemento en la cola
        val (tag, comando) = cola.take()
        println("Consumidor ejecutando comando: $tag")

                // Aquí puedes agregar la lógica para procesar el comando según tus necesidades
                // }
        // } catch (e: InterruptedException) {
        //         Thread.currentThread().interrupt()
        // }
        return comando
    }

    @Async
    override fun producerMethod(nombre: String, funcion: suspend () -> Unit) {
        insertarComando(nombre,funcion)
    }

    @Scheduled(fixedRate = 100)
    override fun consumerMethod() {
        CoroutineScope(Dispatchers.IO).launch {
            val command = takeFromQueue()
            command.invoke()
        }
    }

    @Scheduled(fixedRate = 101)
    override fun consumerMethod2() {
        CoroutineScope(Dispatchers.IO).launch {
            val command = takeFromQueue()
            command.invoke()
        }
    }
}
