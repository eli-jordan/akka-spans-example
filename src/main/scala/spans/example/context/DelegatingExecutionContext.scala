package spans.example.context

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/**
 * Uses the thread context propagation trait to propagate the log4j context between threads and
 * delegates the actual running of the async operation to a delegate execution context.
 *
 * @param delegate the delegate execution context, used to actually run the computation
 */
class DelegatingExecutionContext(delegate: ExecutionContext, override val captors: List[ContextCaptor])
        extends ExecutionContextExecutor with ContextPropagatingExecutionContext {
    override def execute(runnable: Runnable): Unit = delegate.execute(runnable)

    override def reportFailure(cause: Throwable): Unit = delegate.reportFailure(cause)
}
