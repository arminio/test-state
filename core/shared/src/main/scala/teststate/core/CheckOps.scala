package teststate.core

import acyclic.file
import teststate.data._
import teststate.typeclass._
import Profunctor.ToOps._
import Types._

trait CheckOps[C[_, _, _]] {
  def map_OS[O, S, E, X, Y](c: C[O, S, E])(f: OS[X, Y] => OS[O, S]): C[X, Y, E]

  def pmapO[O, S, E, X](c: C[O, S, E])(f: X => E Or O): C[X, S, E]
}

object CheckOps {

  final class Ops[C[_, _, _], O, S, E](c: C[O, S, E])(implicit tc: CheckOps[C]) {

    @inline def map_OS[X, Y](f: OS[X, Y] => OS[O, S]): C[X, Y, E] =
      tc.map_OS(c)(f)

    def mapOS[X, Y](o: X => O, s: Y => S): C[X, Y, E] =
      map_OS(_.map(o, s))

    def mapO[X](f: X => O): C[X, S, E] =
      map_OS(_ mapO f)

    def mapS[X](f: X => S): C[O, X, E] =
      map_OS(_ mapS f)

    def pmapO[X](f: X => E Or O): C[X, S, E] =
      tc.pmapO(c)(f)
  }

  @inline implicit def NameFnOsExt[O, S](n: NameFn[OS[O, S]]): NameFnOsExt[O, S] = new NameFnOsExt(n.fn)
  class NameFnOsExt[O, S](private val _n: Option[OS[O, S]] => Name) extends AnyVal {
    def pmapO[X](f: X => Any Or O): NameFn[OS[X, S]] =
      NameFn(_n).comap(_ mapOe f)
  }

  @inline implicit class EitherOsExt[E, O, S](private val e: E Or OS[O, S]) extends AnyVal {
    def pmapO[X](f: O => E Or X): E Or OS[X, S] =
      e flatMap(_ mapOE f)
  }

  @inline implicit class OsToTriFnExt[O, S, E, A](private val fn: OS[O, S] => Tri[E, A]) extends AnyVal {
    def pmapO[X](f: X => E Or O): OS[X, S] => Tri[E, A] =
      _.mapOE(f).toTriFlatMap(fn)
  }

  abstract class SingleCheck[D[_, _] : Profunctor] extends CheckOps[CheckShape1[D]#T] {
    final type C[O, S, E] = CheckShape1[D]#T[O, S, E]

    override def map_OS[O, S, E, X, Y](c: C[O, S, E])(f: OS[X, Y] => OS[O, S]): C[X, Y, E] =
      c lmap f
  }

  trait Implicits {

    implicit val checkOpsInstanceForPoint: CheckOps[CheckShape1[Point]#T] =
      new SingleCheck[Point] {
        override def pmapO[O, S, E, X](c: C[O, S, E])(f: X => E Or O): C[X, S, E] =
          Point(c.name pmapO f, c.test pmapO f)
      }

    implicit val checkOpsInstanceForAround: CheckOps[CheckShape1[Around]#T] =
      new SingleCheck[Around] {
        override def pmapO[O, S, E, X](c: C[O, S, E])(f: X => E Or O): C[X, S, E] =
          c match {
            case Around.Point(p, w) => Around.Point(p pmapO f, w)
            case Around.Delta(n, d) =>
              val t = d.test
              Around.Delta(n pmapO f,
                Around.DeltaA(
                  d.before pmapO f,
                  (xs, a: d.A) => xs.mapOE(f).test(t(_, a)).leftOption))
          }
      }

    implicit val checkOpsInstanceForInvariant: CheckOps[CheckShape1[Invariant]#T] =
      new SingleCheck[Invariant] {
        override def pmapO[O, S, E, X](c: C[O, S, E])(f: X => E Or O): C[X, S, E] =
          c match {
            case Invariant.Point (p) => Invariant.Point (p pmapO f)
            case Invariant.Around(p) => Invariant.Around(p pmapO f)
          }
      }

    private def checkOpsInstanceForChecks[C[_, _]](implicit sub: CheckOps[CheckShape1[C]#T]): CheckOps[CheckShape[C, ?, ?, ?]] =
      new CheckOps[CheckShape[C, ?, ?, ?]] {
        import Sack._

        override def map_OS[O, S, E, X, Y](c: CheckShape[C, O, S, E])(f: OS[X, Y] => OS[O, S]): CheckShape[C, X, Y, E] =
          c.dimap(_ map f, _ map_OS f)

        override def pmapO[O, S, E, X](m: CheckShape[C, O, S, E])(f: X => E Or O): CheckShape[C, X, S, E] =
          m match {
            case Value(c)        => Value(c pmapO f)
            case Product(cs)     => Product(cs map (pmapO(_)(f)))
            case CoProduct(n, p) => CoProduct(n.cmap(_ pmapO f), i => pmapO(p(i pmapO f))(f))
          }
      }

    implicit val checkOpsInstanceForPoints     = checkOpsInstanceForChecks[Point    ]
    implicit val checkOpsInstanceForArounds    = checkOpsInstanceForChecks[Around   ]
    implicit val checkOpsInstanceForInvariants = checkOpsInstanceForChecks[Invariant]

    implicit def pointsToCheckOps    [O, S, E](c: Points    [O, S, E]): Ops[Points    , O, S, E] = new Ops(c)
    implicit def aroundsToCheckOps   [O, S, E](c: Arounds   [O, S, E]): Ops[Arounds   , O, S, E] = new Ops(c)
    implicit def invariantsToCheckOps[O, S, E](c: Invariants[O, S, E]): Ops[Invariants, O, S, E] = new Ops(c)

    implicit def checkToCheckOps[C[_, _], O, S, E](c: C[OS[O, S], E])(implicit tc: CheckOps[CheckShape1[C]#T]): Ops[CheckShape1[C]#T, O, S, E] =
      new Ops[CheckShape1[C]#T, O, S, E](c)
  }

// // ↓ works but IntelliJ highlights everything red as usual so ↑ for a nicer experience
//  trait ToOps {
//     implicit def toCheckOps[C[_, _, _], O, S, E](c: C[O, S, E])(implicit tc: CheckOps[C]): Ops[C, O, S, E] =
//       new Ops[C, O, S, E](c)(tc)
//  }
}

