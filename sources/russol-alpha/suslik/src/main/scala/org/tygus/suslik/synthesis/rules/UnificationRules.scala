package org.tygus.suslik.synthesis.rules

import org.tygus.suslik.language.CardType
import org.tygus.suslik.language.Expressions._
import org.tygus.suslik.logic.Specifications._
import org.tygus.suslik.logic._
import org.tygus.suslik.report.ProofTrace
import org.tygus.suslik.synthesis._
import org.tygus.suslik.synthesis.rules.Rules._

import scala.Function.tupled
import org.tygus.suslik.language.LocType

/**
  * The goal of unification rules is to eliminate existentials
  * via either heap unification or various forms of pure synthesis.
  *
  * @author Nadia Polikarpova, Ilya Sergey
  */

object UnificationRules extends PureLogicUtils with SepLogicUtils with RuleUtils {

  val exceptionQualifier: String = "rule-unification"


  abstract class HeapUnify extends SynthesisRule {
    override def toString: String = "Unify"

    def heapletFilter(h: Heaplet): Boolean

    // Do we have a chance to get rid of the relevant kind of heaplets by only unification and framing?
    def profilesMatch(pre: SFormula, post: SFormula, exact: Boolean): Boolean



    def apply(goal: Goal): Seq[RuleResult] = {
      val pre = goal.pre
      val post = goal.post
      if (!profilesMatch(pre.sigma, post.sigma, !goal.env.config.canLeak && goal.callGoal.isEmpty)) return Nil

//      val postCandidates = post.sigma.chunks.filter(p => p.vars.exists(goal.isExistential) && heapletFilter(p))
      val postCandidates = post.sigma.chunks.filter(p => heapletFilter(p))

      val alternatives = for {
        s <- postCandidates.take(1) // DANGER: in block phase this relies on alloc and unify discovering existential heaplets in the same order
        t <- pre.sigma.chunks
        if !s.eqModTags(t)
        sub <- t.unify(s)
        subExpr = goal.substToFormula(sub)
        newPostSigma = (post.sigma - s) ** t.copyTag(s.getTag.get)
//        (varSub, subExpr) = goal.splitSubst(sub)
//        newPostSigma = (post.sigma - s).subst(varSub) ** t.copyTag(s)
        if newPostSigma.chunks.distinct.size == newPostSigma.chunks.size // discard substitution if is produces duplicate chunks in the post
      } yield {
//        val newPost = Assertion((post.phi && subExpr).subst(varSub), newPostSigma)
//        val newCallGoal = goal.callGoal.map(_.updateSubstitution(varSub))
//        val newGoal = goal.spawnChild(post = newPost, callGoal = newCallGoal)
        val newPost = Assertion(post.phi && subExpr, newPostSigma)
        val newPre = Assertion(pre.phi, pre.sigma)
        val newGoal = goal.spawnChild(pre = newPre, post = newPost)
        val kont = UnificationProducer(t, s, sub) >> IdProducer >> ExtractHelper(goal)

        ProofTrace.current.add(ProofTrace.DerivationTrail.withSubst(goal, List(newGoal), this, sub))

        RuleResult(List(newGoal), kont, this, goal)
      }
      nubBy[RuleResult, Assertion](alternatives, sub => sub.subgoals.head.post)
//      val derivations = nubBy[RuleResult, Assertion](alternatives, sub => sub.subgoals.head.post)
//      derivations.sortBy(s => -s.subgoals.head.similarity)
    }
  }

  // Unify syntactically (i.e. only allowed to unify existentials that are top-level in a pure expression)
  abstract class SyntacticUnify extends SynthesisRule {
    override def toString: String = "Unify"

    def heapletFilter(h: Heaplet): Boolean

    // Do we have a chance to get rid of the relevant kind of heaplets by only unification and framing?
    def profilesMatch(pre: SFormula, post: SFormula, exact: Boolean): Boolean

    def apply(goal: Goal): Seq[RuleResult] = {
      val pre = goal.pre
      val post = goal.post
      if (!profilesMatch(pre.sigma, post.sigma, goal.callGoal.isEmpty)) return Nil

      val postCandidates = post.sigma.chunks.filter(p => p.vars.exists(goal.isExistential) && heapletFilter(p))

      val alternatives = for {
        s <- postCandidates
        t <- pre.sigma.chunks
        sub <- s.unifySyntactic(t, goal.existentials)
        newPost = post.subst(sub)
        if newPost.sigma.chunks.distinct.size == newPost.sigma.chunks.size // discard substitution if is produces duplicate chunks in the post
      } yield {
        val newCallGoal = goal.callGoal.map(_.updateSubstitution(sub))
        val newGoal = goal.spawnChild(post = newPost, callGoal = newCallGoal)
        val kont = IdProducer >> ExtractHelper(goal)
        RuleResult(List(newGoal), kont, this, goal)
      }
      nubBy[RuleResult, Assertion](alternatives, sub => sub.subgoals.head.post)
    }
  }

  object HeapUnifySimple extends SyntacticUnify with PhaseDisabled

  object HeapUnifyBorrows extends HeapUnify with BorrowsPhase
  object HeapUnifyUnfolding extends HeapUnify with UnfoldingPhase

  object HeapUnifyBlock extends HeapUnify with BlockPhase

  object HeapUnifyPure extends HeapUnify with FlatPhase

  abstract class UnifyPointer extends SynthesisRule {
    override def toString: String = "UnifyLHS"

    override def apply(goal: Goal): Seq[RuleResult] = {
      val pre = goal.pre
      val post = goal.post
      val prePtss = pre.sigma.ptss
      val postPtss = post.sigma.ptss

      def lcpLen(s1: String, s2: String): Int = s1.zip(s2).takeWhile(Function.tupled(_ == _)).length

      val alternatives = for {
        PointsTo(y, oy, _) <- postPtss
        if y.vars.exists(goal.isExistential)
        t@PointsTo(x, ox, _) <- prePtss
//        if post.sigma.block_size(y) == pre.sigma.block_size(x)
        if ox == oy
        if !postPtss.exists(sameLhs(t))
      } yield (y -> x)

      alternatives.sortBy{ case (e1, e2) => -lcpLen(e1.pp, e2.pp) }.headOption match {
        case None => Nil
        case Some((y, x)) => {
          val subst = Map(y -> x)
          val subExpr = goal.substToFormula(subst)
          val newPost = Assertion(post.phi && subExpr, post.sigma)
          val newGoal = goal.spawnChild(post = newPost)
          val kont = (x, y) match {
            case (x:Var, y:Var) => SubstVarProducer(y, x) >> IdProducer >> ExtractHelper(goal)
            case _ =>  IdProducer >> ExtractHelper(goal)
          }
          List(RuleResult(List(newGoal), kont, this, goal))
        }
      }
    }
  }

  object UnifyPointerFlat extends UnifyPointer with FlatPhase with InvertibleRule

  object UnifyPointerSimple extends UnifyPointer with PhaseDisabled

  /*
    X ∈ GV(post) / GV (pre)
    Γ ; {φ ; P} ; {[l/X]ψ ; [l/X]Q} ---> S
    --------------------------------------- [subst-R]
    Γ ; {φ ; P} ; {ψ ∧ X = l; Q} ---> S
  */
  object SubstRight extends SynthesisRule with InvertibleRule {
    override def toString: String = "SubstExist"

    def apply(goal: Goal): Seq[RuleResult] = {
      val p2 = goal.post.phi
      val s2 = goal.post.sigma

      // Can e be substituted with d?
      def canSubst(e: Var, d: Expr) = {
        // e must be an existential var:
        goal.isExistential(e) &&
        // e must not occur in d:
        !d.vars.contains(e) &&
        // if it's a program-level existential, then all vars in d must be program-level
        (!goal.isProgramLevelExistential(e) || d.vars.subsetOf(goal.programVars.toSet))
      }

      def extractSides(l: Expr, r: Expr): Option[(Var, Expr)] = (l, r) match {
        case (l@Var(_), r) if canSubst(l, r) => Some(l, r)
        case (r, l@Var(_)) if canSubst(l, r) => Some(l, r)
        case (AlwaysExistsVar(l), r) if canSubst(l, r) => Some(l, r)
        case (r, AlwaysExistsVar(l)) if canSubst(l, r) => Some(l, r)
        case (Named(l, _), r) if canSubst(l, r) => Some(l, r)
        case (r, Named(l, _)) if canSubst(l, r) => Some(l, r)
        case _ => None
      }
      def equalitySides(e: Expr): Option[(Var, (Expr, Expr))] = extractEquality(e).flatMap(tupled(extractSides)).map(m => (m._1 -> (m._2, e)))

      val cs = p2.conjuncts.toSet.flatMap(equalitySides).toMap
      if (cs.size == 0) Nil
      else {
          var sigmaOld: Subst = Map.empty
          var sigma = cs.map(tpl => {
            val subst = tpl._2._1.vars.filter(v => goal.existentials(v)).map(v => v -> AlwaysExistsVar(v)).toMap
            tpl._1 -> tpl._2._1.subst(subst)
          }).toMap
          while (sigma != sigmaOld) {
            sigmaOld = sigma
            sigma = sigmaOld.map(tpl => (tpl._1, tpl._2.subst(sigmaOld)))
          }
          val _p2 = (p2 - PFormula(cs.map(_._2._2).toSet)).subst(sigma)
          val _s2 = s2.subst(sigma)
          val newCallGoal = goal.callGoal.map(_.updateSubstitution(sigma))
          val newPre = goal.pre.subst(sigma)
          val newGoal = goal.spawnChild(pre = newPre, post = Assertion(_p2, _s2), callGoal = newCallGoal)
          // The SubstMapProducer(sigma) here might contain OnExpiries which we don't want, so we filter them out
          val noExistsVars = p2.collect[NoExists](_.isInstanceOf[NoExists]).flatMap(_.vars)
          val subst = sigma.filter(s => 
            noExistsVars(s._1) || s._1.getType(goal.gamma).get == LocType
          )
          val kont = (if (subst.size > 0) SubstMapProducer(subst) else IdProducer) >> ExtractHelper(goal)
          ProofTrace.current.add(ProofTrace.DerivationTrail.withSubst(goal, Seq(newGoal), this, sigma))
          List(RuleResult(List(newGoal), kont, this, goal))
      }
    }
  }

  /*
      Γ ; {φ ; x.f -> l * P} ; {ψ ∧ Y = l ; x.f -> Y * Q} ---> S
      ---------------------------------------------------------- [pick]
      Γ ; {φ ; x.f -> l * P} ; {ψ ; x.f -> Y * Q} ---> S
   */

  object Pick extends SynthesisRule {
    override def toString: String = "Pick"

    def apply(goal: Goal): Seq[RuleResult] = {
      val constants = List(IntConst(0), SetLiteral(List()), eTrue, eFalse) ++ goal.post.constants

      val exCandidates = // goal.existentials
       if (goal.post.sigma.isEmp) goal.existentials else goal.existentials.intersect(goal.post.sigma.vars)

      def uniCandidates(ex: Var): Set[Var] = {
        if (!goal.isProgramLevelExistential(ex) && goal.post.sigma.isEmp) goal.allUniversals.intersect(goal.pre.vars ++ goal.post.vars)
        else goal.programVars.toSet
//        goal.allUniversals.intersect(goal.pre.vars ++ goal.post.vars)
      }
      if (!exCandidates.forall(ex => goal.getType(ex) != LocType))
        println(s"At goal ${goal.rulesApplied} have exCandidates (${exCandidates}) with ty LocType (gamma:\n${goal.gamma}")
      assert(exCandidates.forall(ex => goal.getType(ex) != LocType))
      for {
        ex <- least(exCandidates) // since all existentials must go, no point trying them in different order
        v <- toSorted(uniCandidates(ex)) ++ constants
        // Don't pick for Locs (fields) - unification will do this
        if goal.getType(ex) == v.getType(goal.gamma).get
        sigma = Map(ex -> v)
        newPre = goal.pre.subst(sigma)
        newPost = goal.post.subst(sigma)
        newCallGoal = goal.callGoal.map(_.updateSubstitution(sigma))
        newGoal = goal.spawnChild(pre = newPre, post = newPost, callGoal = newCallGoal)
        kont = SubstProducer(ex, v) >> IdProducer >> ExtractHelper(goal)
      } yield {
        ProofTrace.current.add(ProofTrace.DerivationTrail.withSubst(
          goal, Seq(newGoal), this, sigma))
        RuleResult(List(newGoal), kont, this, goal)
      }
    }
  }

  /**
    * Special pure synthesis rule for integer existentials that are only constrained by lower bounds
    * (useful for cardinalities).
    */
  object PickCard extends SynthesisRule with InvertibleRule {
    override def toString: String = "PickCard"

    def apply(goal: Goal): Seq[RuleResult] = {
      // If e is of the form lo < v, get lo
      def getLowerBound(e: Expr, v: Var): Option[Expr] = e match {
        case BinaryExpr(OpLt, lo, v1) if v1 == v => Some(lo)
        case _ => None
      }

      // Expression that computes the maximum of all es
      def maxExpr(es: Seq[Expr]): Expr =
        es.reduceLeft((e1, e2) => IfThenElse(BinaryExpr(OpLt, e1, e2), e2, e1))

      val lower_bounded = for {
        ex <- goal.existentials.toList
        if goal.getType(ex) == CardType
        boundOpts = goal.post.phi.conjuncts.filter(_.vars.contains(ex)).map(e => getLowerBound(e, ex))
        if boundOpts.forall(_.isDefined)
      } yield (ex, boundOpts.map(_.get).toList)

      for {
        (ex, bounds) <- lower_bounded.take(1)
        sol = if (bounds.isEmpty) IntConst(0) else BinaryExpr(OpPlus, maxExpr(bounds), IntConst(1))
        sigma = Map(ex -> sol)
        newPost = goal.post.subst(sigma)
        newCallGoal = goal.callGoal.map(_.updateSubstitution(sigma))
        newGoal = goal.spawnChild(post = newPost, callGoal = newCallGoal)
        kont = SubstProducer(ex, sol) >> IdProducer >> ExtractHelper(goal)
      } yield RuleResult(List(newGoal), kont, this, goal)
    }
  }

  /*
   If there's an argument in the call of the suspended recursive call goal that hasn't been substituted yet,
   try substituting it with the companion's goal argument at the same position.
   This rule is useful when there's an argument that does not appear in the precondition (see e.g. sll/sll-init).
   This strategy is incomplete, but it mimics our previous approach of not treating companion vars as existentials at all.
  */
  object PickArg extends SynthesisRule {
    override def toString: String = "PickArg"

    def apply(goal: Goal): Seq[RuleResult] = {
      if (goal.callGoal.isEmpty || goal.existentials.nonEmpty) return Nil // no suspended call or still has existentials
      val callGoal = goal.callGoal.get

      if (callGoal.call.companion.isEmpty) return Nil // suspended call is not recursive
      val companion = goal.ancestorWithLabel(callGoal.call.companion.get).get.toFunSpec

      def isUnsubstituted(e: Expr) = e.isInstanceOf[Var] && !callGoal.freshToActual.keySet.contains(e.asInstanceOf[Var])
      val i = callGoal.call.args.indexWhere(isUnsubstituted)
      if (i < 0) return Nil // no unsubstituted arguments remain

      val arg = companion.params(i)._1
      val v = callGoal.call.args(i).asInstanceOf[Var]
      val sigma = Map(v -> arg)
      val newPost = goal.post.subst(sigma)
      val newCallGoal = goal.callGoal.map(_.updateSubstitution(sigma))
      val newGoal = goal.spawnChild(post = newPost, callGoal = newCallGoal)
      val kont = SubstVarProducer(v, arg) >> IdProducer >> ExtractHelper(goal)

      ProofTrace.current.add(ProofTrace.DerivationTrail.withSubst(
        goal, Seq(newGoal), this, sigma))

      List(RuleResult(List(newGoal), kont, this, goal))
    }
  }


}
