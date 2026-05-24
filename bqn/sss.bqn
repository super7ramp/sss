RemoveClausesWithLiteral ← (>¬∘∊¨) / ⊢
RemoveNegatedLiteral     ← (-⊸≠/⊢)¨
SortByClauseSize         ← (⍋≢¨) ⊏ ⊢
Propagate                ← SortByClauseSize ⊣ RemoveNegatedLiteral RemoveClausesWithLiteral

Solve ← {
  0 = ≠𝕩 ? ⟨⟨⟩⟩ ;
  0 = ≠⊑𝕩 ? ⟨⟩ ;
  literal          ← ⊑⊑𝕩
  leftPropagation  ← {𝕩 ∾ literal} ¨ (𝕊 literal Propagate 𝕩)
  rightPropagation ← {𝕩 ∾ (-literal)} ¨ (𝕊 (-literal) Propagate 𝕩)
  leftPropagation ∾ rightPropagation
}

•Show "Example: Trivial clauses"
•Show "Input: (1 or 2) and ((not 2) or 3)"
•Show Solve ⟨1‿2,¯2‿3⟩
