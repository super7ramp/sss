⍝ Propagates a unit clause ⍺ (a literal), to simplify the problem ⍵ (a list of clauses).
propagate ← {
    clausesNotContainingLiteral ← (~⍺∊¨⍵)/⍵ ⋄
    clausesWithNegatedLiteralRemoved ← clausesNotContainingLiteral ~¨ -⍺ ⋄
    clausesWithNegatedLiteralRemoved[⍋≢¨ clausesWithNegatedLiteralRemoved]
}

⍝ A recursive SAT solver
solve ← {
    0 = ≢⍵ : ⊂⍬ ⋄
    (⊂⍬) ∊ ⍵ : ⍬ ⋄
    literal ← ⊃⊃⍵ ⋄
    negatedLiteral ← -literal ⋄
    leftPropagation ← (∇ literal propagate ⍵) ,¨ literal ⋄
    rightPropagation ← (∇ negatedLiteral propagate ⍵) ,¨ negatedLiteral ⋄
    leftPropagation , rightPropagation
}

⎕ ← 'Example: Trivial clauses'
⎕ ← 'Input: (1 or 2) and (-2 or 3)'
solve (1 2) (¯2 3)
