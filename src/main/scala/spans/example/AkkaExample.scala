package spans.example

import java.time.Instant
import java.util.Random

import _root_.akka.actor.{ActorSystem, Props}
import io.opentracing.mock.MockTracer
import io.opentracing.util.GlobalTracer
import spans.example.context.AkkaContextHelpers.ContextPropagatingActorDecorators
import spans.example.context.ReferenceCountingScopeManager

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * This example demonstrates the difficulty determining when to finish a span, if fire-and-forget
 * messaging is used with akka actors, and how reference counting can resolve the issue.
 *
 * Given the following asynchronous message flows
 *
 * A -> tell -> B
 *   -> tell -> C
 *
 * i.e. Actor 'A' sends two messages, one to actor 'B' and one to actor 'C'
 *
 * Since this happens asynchronously, the time that 'B' and 'C' process them messages are unpredictable
 *
 * It could be
 *
 *  Time -->
 *      t1           t2            t3
 *    A send     B processes   C processes
 *
 * Or,
 *
 *  Time -->
 *      t1           t2            t3
 *    A send     C processes   B processes
 *
 * In the first case, 'C' is the final one to handle the message, and should finish the span.
 * In the second case, 'B' id the final one to handle the message, and should finish the span.
 *
 * So its not possible know ahead of time which actor should finish the span.
 * However, reference counting allows the span to be finished at the correct time in both cases.
 */
object AkkaExample extends ContextPropagatingActorDecorators {

    import spans.example.context.AkkaContextHelpers._

    // define a tracer that uses the reference counting scope manager
    val tracer = new MockTracer(new ReferenceCountingScopeManager)
    GlobalTracer.register(tracer)

    case class Message(value: String) extends TraceSupport {
        override def duplicate(): TraceSupport = copy()
    }


    implicit val actorSystem = ActorSystem()

    lazy val actorARef = actorSystem.actorOf(Props(new ActorA), "ActorA")
    lazy val actorBRef = actorSystem.actorOf(Props(new ActorB), "ActorB")
    lazy val actorCRef = actorSystem.actorOf(Props(new ActorC), "ActorC")


    class ActorA extends ContextPropagatingActor {
        override def receive: Receive = {
            case Message(_) => {
                actorBRef !> Message(" -> B")
                actorCRef !> Message(" -> C")
            }
        }
    }

    class ActorB extends ContextPropagatingActor {
        override def receive: Receive = {
            case Message(_) => {
                randomSleep(100)
                println(s"${Instant.now} - ActorB")

                // There is no way to tell whether we should finish the span here
                // since ActorC may not have finished processing yet
            }
        }
    }

    class ActorC extends ContextPropagatingActor {
        override def receive: Receive = {
            case Message(_) => {
                randomSleep(100)
                println(s"${Instant.now} ActorC")

                // There is no way to tell whether we should finish the span here
                // since ActorB may not have finished processing yet
            }
        }
    }


    def printSpan(name: String): Unit = {
        println(s"ActiveSpan($name): " + Option(tracer.scopeManager().active()))
    }

    private def randomSleep(bound: Int = 10): Unit = {
        Thread.sleep(new Random().nextInt(bound))
    }

    def main(args: Array[String]): Unit = {
        val scope = tracer.buildSpan("simpleExampleFromComments").startActive(false)
        actorARef !> Message("start")
        scope.close()

        Thread.sleep(1000)

        println("Finished Spans: " + tracer.finishedSpans())
        Await.result(actorSystem.terminate(), 1.second)
    }
}
