package org.scalasteward.core.nurture

import cats.data.{NonEmptyList, StateT}
import cats.effect.{Async, IO}
import cats.syntax.option._
import org.scalacheck.{Arbitrary, Gen}
import org.scalasteward.core.data.ProcessResult.{Ignored, Updated}
import org.scalasteward.core.data.Update.Single
import org.scalasteward.core.data.{GroupId, ProcessResult, Update}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class NurtureAlgTest extends AnyFunSuite with Matchers with ScalaCheckPropertyChecks {

  implicit val updateArbitrary: Arbitrary[Update] = Arbitrary(for {
    groupId <- Gen.alphaStr.map(GroupId.apply)
    artifactId <- Gen.alphaStr
    currentVersion <- Gen.alphaStr
    newerVersion <- Gen.alphaStr
  } yield Single(groupId, artifactId, currentVersion, NonEmptyList.one(newerVersion)))

  test("processUpdates with No Limiting") {
    forAll { updates: List[Update] =>
      NurtureAlg
        .processUpdates(
          updates,
          _ => StateT[IO, Int, ProcessResult](actionAcc => IO.pure(actionAcc + 1 -> Ignored)),
          None
        )
        .runS(0)
        .unsafeRunSync() shouldBe updates.size
    }
  }

  test("processUpdates with Limiting should process all updates up to the limit") {
    forAll { updates: Set[Update] =>
      val (ignorableUpdates, appliableUpdates) = updates.toList.splitAt(updates.size / 2)

      implicit val stateAsync = Async.catsStateTAsync[IO, Int]
      val f: Update => StateT[IO, Int, ProcessResult] = update =>
        StateT[IO, Int, ProcessResult](
          actionAcc =>
            IO.pure(actionAcc + 1 -> (if (ignorableUpdates.contains(update)) Ignored else Updated))
        )
      NurtureAlg
        .processUpdates(ignorableUpdates ++ appliableUpdates, f, appliableUpdates.size.some)
        .runS(0)
        .unsafeRunSync() shouldBe updates.size
    }
  }
}
