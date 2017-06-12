import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
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

suspend fun add(actor: ActorJob<Msg>, n: Long) = actor.send(Add(n))

suspend fun getAndReset(actor: ActorJob<Msg>): Long {
    val reply = Channel<Long>()
    actor.send(GetAndReset(reply))
    return reply.receive()
}

val processorCount = 8

suspend fun run(numPerThread: Int) {
    print("ChMsg: ")
    val elapsed = measureTimeMillis {
        val actor = create()
        for (i in 1..processorCount) {
            for (n in 1..numPerThread) {
                add(actor, 100L)
                getAndReset(actor)
            }
        }
    }
    print("$processorCount * $numPerThread msgs => ${processorCount.toDouble() * numPerThread / (elapsed / 1000.0)} msgs/s\n")
}

fun main(args: Array<String>) = runBlocking {
    for (n in listOf(300, 3000, 30000, 300000, 3000000)) {
        run(n)
    }
}
