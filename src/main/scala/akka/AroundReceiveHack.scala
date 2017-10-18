package akka

import akka.actor.Actor

/**
 * Must be in the akk package in order to override aroundReceive.
 * This allows injecting code before/after an actors receive function
 * in order to instrument its behaviour.
 */
trait AroundReceiveHack extends Actor {

    override protected[akka] def aroundReceive(receive: Receive, msg: Any): Unit = {
        traceBeforeReceive(receive, msg)
        super.aroundReceive(receive, msg)
        traceAfterReceive(receive, msg)
    }

    protected def traceBeforeReceive(receive: Receive, msg: Any): Unit = {}
    protected def traceAfterReceive(receive: Receive, msg: Any): Unit = {}
}