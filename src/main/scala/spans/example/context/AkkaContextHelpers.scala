package spans.example.context

import akka.AroundReceiveHack
import akka.actor.{Actor, ActorRef, Status}
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object AkkaContextHelpers {
    val captors = List(new ContinuationSpanContextCaptor())

    /**
     * A trait that should be mixed into the messages that are exchanged between
     * actors that wish to propagate the current context. T
     */
    trait TraceSupport {
        private var captured: List[Restore] = List.empty

        /**
         * Called when a message that mixes in this trait is sent using the `>!`
         * helper operator, that will ensure that the current context is captured
         * before the message is dispatched.
         */
        def onSend(): TraceSupport = {
            val toSend = duplicate()
            toSend.captured = captors.map(_.capture())
            toSend
        }

        /**
         * Called when a message is received by an actor that mixes in the
         */
        def onReceive(): Unit = {
            captured.foreach(_.restore())
        }

        def afterReceive(): Unit = {
            captured.foreach(_.cleanup())
            captured = List.empty
        }

        def duplicate(): TraceSupport
    }

    /**
     * Defines alternatives to the actor ! ? and pipeTo operators that ensure that
     * the current context is propagated with the message.
     */
    trait ContextPropagatingActorDecorators {

        import _root_.akka.pattern.ask

        /**
         * Defines context propagating variants of tell and ask
         */
        implicit class TracedActorOps(actor: ActorRef) {
            def !>(msg: TraceSupport)(implicit sender: ActorRef = Actor.noSender): Unit = {
                actor ! msg.onSend()
            }

            /**
             * The context propagating variant of the the akka '?' operator.
             *
             * Note: This operator has a significant limitation. It will propagate the context to the actor being invoked,
             * however any callback registered on the resulting future will not be executed in the same context,
             */
            def ?>(msg: TraceSupport)(implicit sender: ActorRef = Actor.noSender, timeout: Timeout, ec: ExecutionContext): Future[Any] = {
                (actor ? msg.onSend()).map {
                    case msg: TraceSupport => {
                        msg.onReceive()
                        msg.afterReceive()
                        msg
                    }
                    case x => x
                }
            }
        }

        /**
         * Assuming the provided ExecutionContext propagates the context, provides a context propagating variant
         * of pipeTo.
         *
         * Note: This operator has a limitation in the failure case. If the future results in a failure, the context is
         * not propagated to the target actor.
         */
        implicit class TracePipe[T](val future: Future[TraceSupport])(implicit ec: ExecutionContext) {
            def tracePipe(recipient: ActorRef)(implicit sender: ActorRef = Actor.noSender): Future[TraceSupport] = {
                future andThen {
                    case Success(r) => recipient !> r
                    case Failure(f) => recipient ! Status.Failure(f)
                }
            }
        }

    }

    /**
     * A helper trait that should be mixed into any actors that want the
     * current ContextStore data propagated.
     */
    trait ContextPropagatingActor extends AroundReceiveHack with ContextPropagatingActorDecorators {
        override protected def traceBeforeReceive(receive: Receive, msg: Any): Unit = {
            msg match {
                case m: TraceSupport => m.onReceive()
                case _ =>
            }
        }

        override protected def traceAfterReceive(receive: Receive, msg: Any): Unit = {
            msg match {
                case m: TraceSupport => m.afterReceive()
                case _ =>
            }
        }
    }
}
