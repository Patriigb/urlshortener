package es.unizar.urlshortener.core

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job


interface QueueController {
    fun insertarComando(funcion: suspend () -> Unit)

    fun takeFromQueue() : suspend () -> Unit
    
    fun producerMethod(funcion: suspend () -> Unit)

    fun consumerMethod()
}

@EnableScheduling
@Component
class QueueControllerImpl : QueueController {
    val cola: BlockingQueue<suspend () -> Unit> = LinkedBlockingQueue()

    override fun insertarComando(funcion: suspend () -> Unit) {
        // Inserta el comando en la cola bloqueante
        cola.put(funcion)
        println("Comando insertado: $funcion")
    }

    
    override fun takeFromQueue() : suspend () -> Unit {
        // var comando: suspend () -> Unit? = null
        // try {
           // while (true) {
                // Bloquea hasta que haya un elemento en la cola
        val comando = cola.take()
        println("Consumidor ejecutando comando: $comando")

                // Aquí puedes agregar la lógica para procesar el comando según tus necesidades
                // }
        // } catch (e: InterruptedException) {
        //         Thread.currentThread().interrupt()
        // }
        return comando
    }

    @Async
    override fun producerMethod(funcion: suspend () -> Unit) {
        insertarComando(funcion)
    }

    @Scheduled(fixedRate = 100)
    override fun consumerMethod() { GlobalScope.launch {
        // Consumer logic
        val comando = takeFromQueue()
        // Procesa el comando según tus necesidades
        if (comando != null) {
            // Procesa el comando según tus necesidades
            comando.invoke()
        }
    }
    }
}
