import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.ActorJob
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.runBlocking
import kotlin.system.measureTimeMillis

sealed class Msg
class Add(val n: Long) : Msg()
class GetAndReset(val reply: SendChannel<Long>) : Msg()

fun create() = actor<Msg>(CommonPool) {
    var state = 0L
    for (msg in channel) {
        when (msg) {
            is Add -> state += msg.n
            is GetAndReset -> {
                val was = state
                state = 0L
                msg.reply.send(was)
            }
        }
    }
}

suspend fun getAndReset(actor: ActorJob<Msg>): Long {
    val reply = Channel<Long>()
    actor.send(GetAndReset(reply))
    return reply.receive()
}

suspend fun run(numPerThread: Int) {
    val processorCount = Runtime.getRuntime().availableProcessors()

    val elapsed = measureTimeMillis {
        val actor = create()
        (1..processorCount).map {
            async(CommonPool) {
                for (n in 1..numPerThread) {
                    actor.send(Add(100L))
                }
                getAndReset(actor)
            }
        }.forEach { it.await() }
    }
    print("$processorCount * $numPerThread msgs => ${processorCount.toDouble() * numPerThread / (elapsed / 1000.0)} msgs/s\n")
}

fun main(args: Array<String>) = runBlocking {
    for (n in listOf(300, 3_000, 30_000, 300_000, 3_000_000)) {
        run(n)
    }
}
