# Propagates a unit clause
# ? literal clauses
Propagate ← (
  ⟜(▽¬⊸≡◇˜∊) # literal RemoveClausesWithLiteral ? literal clauses
  ⍚▽⊸≠¯      # RemoveNegatedLiteral ? literal clauses
  ⊏⍏⊸⍚⧻      # SortByClauseSize ? clauses
)

# A recursive SAT solver
# ? clauses
Solve ← |1 ⍣(
  ⍩{[]} °0 ⧻
| ⍩[] °0 ◇⧻⊢
| ⊂∩⍚˜⊂∩⤚(Solve Propagate)⊃(◇⊢⊸⊢)(¯◇⊢⊸⊢)
)

&s "Example: Trivial clauses"
&s "Input: (1 or 2) and ((not 2) or 3)"
Solve {1_2 ¯2_3}
