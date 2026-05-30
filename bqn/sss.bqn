RemoveClausesWithLiteral ← (>¬∘∊¨) / ⊢
RemoveNegatedLiteral     ← (-⊸≠/⊢)¨
SortByClauseSize         ← (⍋≢¨) ⊏ ⊢
# Propagates a unit clause (𝕨) to simplify the problem (𝕩)
Propagate                ← SortByClauseSize ⊣ RemoveNegatedLiteral RemoveClausesWithLiteral

# A recursive SAT solver
Solve ← {
  0 = ≠𝕩 ? ⟨⟨⟩⟩ ;
  0 = ≠⊑𝕩 ? ⟨⟩ ;
  literal          ← ⊑⊑𝕩
  (∾ ○ (⊢∾¨ ⊢ 𝕊 Propagate⟜𝕩)) ⟜- ⊑⊑𝕩
}

•Show "Example: Trivial clauses"
•Show "Input: (1 or 2) and ((not 2) or 3)"
•Show Solve ⟨1‿2,¯2‿3⟩
