package spans.example

import java.util.Random

import akka.actor.{ActorSystem, Props}
import io.opentracing.mock.MockTracer
import io.opentracing.util.GlobalTracer
import spans.example.context.AkkaContextHelpers.ContextPropagatingActorDecorators
import spans.example.context.ReferenceCountingScopeManager

import scala.concurrent.Await
import scala.concurrent.duration._

object ManyMessagesAkkaExample extends ContextPropagatingActorDecorators {

    import spans.example.context.AkkaContextHelpers._

    // define a tracer that uses the reference counting scope manager
    val tracer = new MockTracer(new ReferenceCountingScopeManager)
    GlobalTracer.register(tracer)

    case class Message(value: String) extends TraceSupport {
        override def duplicate(): TraceSupport = copy()
    }

    implicit val actorSystem = ActorSystem()

    lazy val actor1Ref = actorSystem.actorOf(Props(new Actor1), "Actor1")
    lazy val actor2Ref = actorSystem.actorOf(Props(new Actor2), "Actor2")
    lazy val actor3Ref = actorSystem.actorOf(Props(new Actor3), "Actor3")

    class Actor1 extends ContextPropagatingActor {
        override def receive: Receive = {
            case Message(value) => {
                randomSleep()
                printSpan("Actor1")
                if (value.length < 100) {
                    actor2Ref !> Message(value + " -> actor2")
                    actor3Ref !> Message(value + " -> actor3")
                } else {
                    println(value)
                }
            }
        }
    }

    class Actor2 extends ContextPropagatingActor {
        override def receive: Receive = {
            case Message(value) => {
                randomSleep()
                printSpan("Actor2")
                if (value.length < 100) {
                    actor3Ref !> Message(value + " -> actor3")
                    actor1Ref !> Message(value + " -> actor1")
                } else {
                    println(value)
                }
            }
        }
    }

    class Actor3 extends ContextPropagatingActor {
        override def receive: Receive = {
            case Message(value) => {
                randomSleep()
                printSpan("Actor3")
                if (value.length() < 100) {
                    actor1Ref !> Message(value + " -> actor1")
                    actor2Ref !> Message(value + " -> actor2")
                } else {
                    println(value)
                }
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
        val scope = tracer.buildSpan("manyMessagesExample").startActive(false)
        actor1Ref !> Message("start")
        scope.close()

        Thread.sleep(10000)

        println("Finished Spans: " + tracer.finishedSpans())
        Await.result(actorSystem.terminate(), 1.second)
    }
}
