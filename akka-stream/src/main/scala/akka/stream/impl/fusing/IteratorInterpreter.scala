/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.stream.impl.fusing

import akka.stream.stage._

/**
 * INTERNAL API
 */
private[akka] object IteratorInterpreter {
  final case class IteratorUpstream[T](input: Iterator[T]) extends PushPullStage[T, T] {
    private var hasNext = input.hasNext

    override def onPush(elem: T, ctx: Context[T]): Directive =
      throw new UnsupportedOperationException("IteratorUpstream operates as a source, it cannot be pushed")

    override def onPull(ctx: Context[T]): Directive = {
      if (!hasNext) ctx.finish()
      else {
        val elem = input.next()
        hasNext = input.hasNext
        if (!hasNext) ctx.pushAndFinish(elem)
        else ctx.push(elem)
      }

    }

    // don't let toString consume the iterator
    override def toString: String = "IteratorUpstream"
  }

  final case class IteratorDownstream[T]() extends BoundaryStage with Iterator[T] {
    private var done = false
    private var nextElem: T = _
    private var needsPull = true
    private var lastError: Throwable = null

    override def onPush(elem: Any, ctx: BoundaryContext): Directive = {
      nextElem = elem.asInstanceOf[T]
      needsPull = false
      ctx.exit()
    }

    override def onPull(ctx: BoundaryContext): Directive =
      throw new UnsupportedOperationException("IteratorDownstream operates as a sink, it cannot be pulled")

    override def onUpstreamFinish(ctx: BoundaryContext): TerminationDirective = {
      done = true
      ctx.finish()
    }

    override def onUpstreamFailure(cause: Throwable, ctx: BoundaryContext): TerminationDirective = {
      done = true
      lastError = cause
      ctx.finish()
    }

    private def pullIfNeeded(): Unit = {
      if (needsPull) {
        enter().pull() // will eventually result in a finish, or an onPush which exits
      }
    }

    override def hasNext: Boolean = {
      if (!done) pullIfNeeded()
      !(done && needsPull) || (lastError ne null)
    }

    override def next(): T = {
      if (lastError ne null) {
        val e = lastError
        lastError = null
        throw e
      } else if (!hasNext)
        Iterator.empty.next()
      else {
        needsPull = true
        nextElem
      }
    }

    // don't let toString consume the iterator
    override def toString: String = "IteratorDownstream"

  }
}

/**
 * INTERNAL API
 */
private[akka] class IteratorInterpreter[I, O](val input: Iterator[I], val ops: Seq[PushPullStage[_, _]]) {
  import akka.stream.impl.fusing.IteratorInterpreter._

  private val upstream = IteratorUpstream(input)
  private val downstream = IteratorDownstream[O]()
  private val interpreter = new OneBoundedInterpreter(upstream +: ops.asInstanceOf[Seq[Stage[_, _]]] :+ downstream)
  interpreter.init()

  def iterator: Iterator[O] = downstream
}
