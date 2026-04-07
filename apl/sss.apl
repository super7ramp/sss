⍝ Propagates a unit clause (a literal), to simplify the problem.
propagate ← {
    (literal clauses) ← ⍵ ⋄
    clausesNotContainingLiteral ← (literal (~∊) ¨ clauses) / clauses ⋄
    clausesWithNegatedLiteralRemoved ← clausesNotContainingLiteral ~¨⊂ -literal ⋄
    indicesSortedByClauseCount ← ⍋≢¨ clausesWithNegatedLiteralRemoved ⋄
    clausesWithNegatedLiteralRemoved[indicesSortedByClauseCount]
}

⍝ A recursive SAT solver
solve ← {
    0 = ≢⍵ : ⊂⍬ ⋄
    (⊂⍬) ∊ ⍵ : ⍬ ⋄
    literal ← ⊃⊃⍵ ⋄
    negatedLiteral ← -literal ⋄
    leftPropagation ← (∇ propagate literal ⍵) ,¨ literal ⋄
    rightPropagation ← (∇ propagate negatedLiteral ⍵) ,¨ negatedLiteral ⋄
    leftPropagation , rightPropagation
}

⎕ ← 'Example: Trivial clauses'
⎕ ← 'Input: (1 or 2) and (-2 or 3)'
solve (1 2) (¯2 3)
