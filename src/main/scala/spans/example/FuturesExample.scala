package spans.example

import io.opentracing.Span
import io.opentracing.mock.{MockSpan, MockTracer}
import io.opentracing.util.GlobalTracer
import spans.example.context.{DelegatingExecutionContext, SpanContextCaptor}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * This example demonstrates that the existing ScopeManager API can be used to propagate
 * the current span when using futures.
 */
object FuturesExample {

    val mockTracer = new MockTracer()

    GlobalTracer.register(mockTracer)

    private def activeSpan: Span = GlobalTracer.get().scopeManager().active().span()

    /**
     * Setup an execution context that propagates the current span, using the ScopeManager
     */
    implicit val executionContext: ExecutionContext = new DelegatingExecutionContext(
        scala.concurrent.ExecutionContext.global,
        List(new SpanContextCaptor())
    )

    private def doA(a: String): Future[String] = {
        Future {
            println(s"doA: threadId=${Thread.currentThread().getId}, activeSpan=$activeSpan")
            activeSpan.log("doing a")

            a + "1"
        }
    }

    private def doB(b: String): Future[String] = {
        Future {
            println(s"doB: threadId=${Thread.currentThread().getId}, activeSpan=$activeSpan")
            activeSpan.log("doing b")
            b + "2"
        }
    }

    private def doC(c: String): Future[String] = {
        Future {
            println(s"doC: threadId=${Thread.currentThread().getId}, activeSpan=$activeSpan")
            activeSpan.log("doing c")
            c + "3"
        }
    }

    def main(args: Array[String]): Unit = {

        val tracer = GlobalTracer.get()

        val scope = tracer.buildSpan("futures-example").startActive(false)
        val future = for {
            a <- doA("a")
            b <- doB(a)
            c <- doC(b)
        } yield {
            println(s"Final Span ${scope.span()} logs=${scope.span().asInstanceOf[MockSpan].logEntries()}")
            scope.span().finish()
            scope.close()
            a + b + c
        }


        val result = Await.result(future, 1.second)
        println(s"Result: $result")

        println(s"Finished Spans: ${mockTracer.finishedSpans()}")
    }

}
