package spans.example.context

import io.opentracing.util.GlobalTracer
import io.opentracing.{Scope, Tracer}

/**
 * A [[ContextCaptor]] implementation that leverages the extensions added to the standard [[io.opentracing.ScopeManager]]
 * interface by [[ReferenceCountingScopeManager]] to propagate the current span between threads.
 */
class ContinuationSpanContextCaptor(tracer: => Tracer = GlobalTracer.get()) extends ContextCaptor {

    override def capture(): Restore = new Restore {
        private val continuation =
            tracer.scopeManager().asInstanceOf[ReferenceCountingScopeManager].capture()

        private var activatedScope: Option[Scope] = _

        override def restore(): Unit = {
            activatedScope = Option(continuation.activate())
        }

        override def cleanup(): Unit = {
            activatedScope.foreach(_.close())
        }
    }
}
