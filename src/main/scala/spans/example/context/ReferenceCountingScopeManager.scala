package spans.example.context

import java.util.concurrent.atomic.AtomicInteger

import io.opentracing.{Scope, ScopeManager, Span}

/**
 * A scope manager implementation the extends the API definition adding a
 * the concept of a Continuation.
 */
class ReferenceCountingScopeManager extends ScopeManager {

    /**
     * Use thread local storage to save a reference to the currently active scope
     */
    private val threadLocalStore = new ThreadLocal[ReferenceCountingScope]

    override def active(): Scope =
        threadLocalStore.get()

    override def activate(span: Span): Scope = {
        val scope = ReferenceCountingScope(span = span)
        threadLocalStore.set(scope)
        scope
    }

    override def activate(span: Span, finishSpanOnClose: Boolean): Scope =
        activate(span)

    /**
     * An extension that is equivalent to the ActiveSpan continuation concept
     */
    trait Continuation {
        def activate(): Scope
    }

    /**
     * Capture the current scope
     */
    def capture(): Continuation = new Continuation {
        private val captured: Option[ReferenceCountingScope] = Option(threadLocalStore.get())
        captured.foreach { scope =>
            scope.refCount.incrementAndGet()
        }

        override def activate(): Scope = {
            captured.map { scope =>
                ReferenceCountingScope(refCount = scope.refCount, span = scope.span)
            }.orNull
        }
     }

    case class ReferenceCountingScope(refCount: AtomicInteger = new AtomicInteger(1), span: Span) extends Scope {

        private val toRestore: ReferenceCountingScope = threadLocalStore.get()
        threadLocalStore.set(this)

        override def close(): Unit = {
            val count = refCount.decrementAndGet()
            println(s"Count: $count")
            threadLocalStore.set(toRestore)
            if (count == 0) {
                println(s"Finishing Span: $span")
                span.finish()
            }
        }
    }
}

