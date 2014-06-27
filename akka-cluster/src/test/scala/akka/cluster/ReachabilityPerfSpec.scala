/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.cluster

import org.scalatest.WordSpec
import org.scalatest.Matchers
import akka.actor.Address

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ReachabilityPerfSpec extends WordSpec with Matchers {

  val nodesSize = sys.props.get("akka.cluster.ReachabilityPerfSpec.nodesSize").getOrElse("250").toInt
  val iterations = sys.props.get("akka.cluster.ReachabilityPerfSpec.iterations").getOrElse("10000").toInt

  val address = Address("akka.tcp", "sys", "a", 2552)
  val node = Address("akka.tcp", "sys", "a", 2552)

  def createReachabilityOfSize(base: Reachability, size: Int): Reachability =
    (base /: (1 to size)) {
      case (r, i) ⇒
        val observer = UniqueAddress(address.copy(host = Some("node-" + i)), i)
        val j = if (i == size) 1 else i + 1
        val subject = UniqueAddress(address.copy(host = Some("node-" + j)), j)
        r.unreachable(observer, subject).reachable(observer, subject)
    }

  def addUnreachable(base: Reachability, count: Int): Reachability = {
    val observers = base.allObservers.take(count)
    val subjects = Stream.continually(base.allObservers).flatten.iterator
    (base /: observers) {
      case (r, o) ⇒
        (r /: (1 to 5)) { case (r, _) ⇒ r.unreachable(o, subjects.next()) }
    }
  }

  val reachability1 = createReachabilityOfSize(Reachability.empty, nodesSize)
  val reachability2 = createReachabilityOfSize(reachability1, nodesSize)
  val reachability3 = addUnreachable(reachability1, nodesSize / 2)
  val allowed = reachability1.allObservers

  def checkThunkFor(r1: Reachability, r2: Reachability, thunk: (Reachability, Reachability) ⇒ Unit, times: Int): Unit = {
    for (i ← 1 to times) {
      thunk(Reachability(r1.records, r1.versions), Reachability(r2.records, r2.versions))
    }
  }

  def checkThunkFor(r1: Reachability, thunk: Reachability ⇒ Unit, times: Int): Unit = {
    for (i ← 1 to times) {
      thunk(Reachability(r1.records, r1.versions))
    }
  }

  def merge(expectedRecords: Int)(r1: Reachability, r2: Reachability): Unit = {
    r1.merge(allowed, r2).records.size should be(expectedRecords)
  }

  def checkStatus(r1: Reachability): Unit = {
    val record = r1.records.head
    r1.status(record.observer, record.subject) should be(record.status)
  }

  def checkAggregatedStatus(r1: Reachability): Unit = {
    val record = r1.records.head
    r1.status(record.subject) should be(record.status)
  }

  def allUnreachableOrTerminated(r1: Reachability): Unit = {
    val record = r1.records.head
    r1.allUnreachableOrTerminated.isEmpty should be(false)
  }

  def allUnreachable(r1: Reachability): Unit = {
    val record = r1.records.head
    r1.allUnreachable.isEmpty should be(false)
  }

  def recordsFrom(r1: Reachability): Unit = {
    r1.allObservers.foreach { o ⇒
      r1.recordsFrom(o) should not be be(null)
    }
  }

  s"Reachability of size $nodesSize" must {

    s"do a warm up run, $iterations times" in {
      checkThunkFor(reachability1, reachability2, merge(0), iterations)
    }

    s"merge with same versions, $iterations times" in {
      checkThunkFor(reachability1, reachability1, merge(0), iterations)
    }

    s"merge with all older versions, $iterations times" in {
      checkThunkFor(reachability2, reachability1, merge(0), iterations)
    }

    s"merge with all newer versions, $iterations times" in {
      checkThunkFor(reachability1, reachability2, merge(0), iterations)
    }

    s"merge with half nodes unreachable, $iterations times" in {
      checkThunkFor(reachability1, reachability3, merge(5 * nodesSize / 2), iterations)
    }

    s"merge with half nodes unreachable opposite $iterations times" in {
      checkThunkFor(reachability3, reachability1, merge(5 * nodesSize / 2), iterations)
    }

    s"check status with half nodes unreachable, $iterations times" in {
      checkThunkFor(reachability3, checkStatus, iterations)
    }

    s"check aggregated reachability status with half nodes unreachable, $iterations times" in {
      checkThunkFor(reachability3, checkAggregatedStatus, iterations)
    }

    s"get allUnreachableOrTerminated with half nodes unreachable, $iterations times" in {
      checkThunkFor(reachability3, allUnreachableOrTerminated, iterations)
    }

    s"get allUnreachable with half nodes unreachable, $iterations times" in {
      checkThunkFor(reachability3, allUnreachable, iterations)
    }

    s"get recordsFrom with half nodes unreachable, $iterations times" in {
      checkThunkFor(reachability3, recordsFrom, iterations)
    }
  }
}