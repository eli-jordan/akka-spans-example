## Akka Spans Example

This repo provides some examples of using the `ScopeManager` API in `opentracing:0.31.0-RC1`

There are two main example:

1. [Using Scala Futures](https://github.com/eli-jordan/akka-spans-example/blob/master/src/main/scala/spans/example/FuturesExample.scala) &ndash; This shows how the current API can be used with scala futures, by using a custom `ExecutionContext`

2. [Using Akka Actors](https://github.com/eli-jordan/akka-spans-example/blob/master/src/main/scala/spans/example/AkkaExample.scala) &ndash; This example demonstrates that it is very difficult to determine when to finish a span when one-way asynchronous messaging is used with akka, and that using reference counting solves this in an elegant manner.