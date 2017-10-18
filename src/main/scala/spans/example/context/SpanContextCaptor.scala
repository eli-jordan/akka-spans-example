package spans.example.context

import io.opentracing.util.GlobalTracer
import io.opentracing.{Scope, Span, Tracer}

/**
 * A [[ContextCaptor]] implementation that uses the standard [[io.opentracing.ScopeManager]] functionality
 * to propagate the current span between threads.
 *
 * This is used in conjunction with [[DelegatingExecutionContext]] to capture and restore the span when
 * using scala futures.
 */
class SpanContextCaptor(tracer: => Tracer = GlobalTracer.get()) extends ContextCaptor {

    override def capture(): Restore = new Restore {

        private val capturedSpan = currentSpan

        private var activeScope: Option[Scope] =_

        override def restore(): Unit = {
            activeScope = capturedSpan.map { span =>
                tracer.scopeManager().activate(span, false)
            }
        }

        override def cleanup(): Unit = activeScope.foreach(_.close())
    }

    private def currentSpan: Option[Span] = for {
        scope <- Option(tracer.scopeManager().active())
    } yield scope.span()
}
