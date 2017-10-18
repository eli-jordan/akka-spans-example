package spans.example.context

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/**
 * Allows hooking into the thread context propagation logic
 */
trait ContextCaptor {

    /**
     * This is executed on the calling thread, allowing context to be captured.
     * The returned object should contain the captured data, and allow it to be restored.
     */
    def capture(): Restore
}

/**
 * Executed on the new thread. The execution is essentially;
 *    Restore.restore()
 *    try {
 *       executeAction()
 *    } finally {
 *       Restore.cleanup()
 *    }
 */
trait Restore {

    /**
     * Executed on the new thread, in order the restore the previously captured context
     */
    def restore(): Unit

    /**
     * Cleanup
     */
    def cleanup(): Unit
}


/**
 * Defines the prepare method in such a way that the ContextStore thread context will be propagated between threads.
 * This trait allows the actual execution to be defined elsewhere. e.g. using a thread pool or an actor
 */
trait ContextPropagatingExecutionContext extends ExecutionContext {
    self =>
    override def prepare(): ExecutionContext = new ExecutionContextExecutor {

        private val captured = captors.map(_.capture())

        // delegate
        override def reportFailure(cause: Throwable): Unit = self.reportFailure(cause)

        /**
         * Delegate the execution, but wrap it a call that restores the saved context
         * @param runnable the action to execute
         */
        override def execute(runnable: Runnable): Unit = self.execute(() => {
            captured.foreach(_.restore())
            try {
                runnable.run()
            } finally {
                captured.foreach(_.cleanup())
            }
        })
    }

    def captors: List[ContextCaptor]
}
