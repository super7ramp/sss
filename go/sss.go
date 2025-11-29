package main

import (
	"fmt"
	"slices"
)

// Literal represents a propositional logic literal.
type Literal int

func (l Literal) Negate() Literal {
	return -l
}

// Clause represents a disjunction ("or") of literals.
type Clause []Literal

func (c Clause) IsEmpty() bool {
	return len(c) == 0
}

func (c Clause) Head() Literal {
	return c[0]
}

func (c Clause) Without(literal Literal) Clause {
	var newClause Clause
	for _, currentLiteral := range c {
		if currentLiteral != literal {
			newClause = append(newClause, currentLiteral)
		}
	}
	return newClause
}

// Problem represents a conjunction ("and") of clauses.
type Problem []Clause

func (p Problem) IsEmpty() bool {
	return len(p) == 0
}

func (p Problem) Head() Clause {
	return p[0]
}

type Assignment []Literal

// Propagator defines the interface for unit propagation in a SAT solver.
type Propagator interface {
	Propagate(literal Literal, problem Problem) Problem
}

type DefaultPropagator struct{}

func (_ *DefaultPropagator) Propagate(literal Literal, problem Problem) Problem {
	negatedLiteral := literal.Negate()
	var clausesAfterPropagation Problem
	for _, clause := range problem {
		if slices.Contains(clause, literal) {
			continue
		}
		updatedClause := clause.Without(negatedLiteral)
		if updatedClause == nil {
			return []Clause{[]Literal{}}
		}
		clausesAfterPropagation = append(clausesAfterPropagation, updatedClause)
	}
	slices.SortFunc(clausesAfterPropagation, compareByClauseLength)
	return clausesAfterPropagation
}

func compareByClauseLength(a, b Clause) int {
	return len(a) - len(b)
}

// Solver defines the interface for a SAT solver.
type Solver interface {
	// TODO return an iterator
	Solve(p Problem) []Assignment
}

type DefaultSolver struct {
	propagator Propagator
}

func NewDefaultSolver() *DefaultSolver {
	return &DefaultSolver{propagator: &DefaultPropagator{}}
}

func (s *DefaultSolver) Solve(problem Problem) []Assignment {

	if problem.IsEmpty() {
		return []Assignment{{}}
	}

	headClause := problem.Head()
	if headClause.IsEmpty() {
		return []Assignment{}
	}

	literal := headClause.Head()

	literalPropagated := s.propagate(literal, problem)
	assignmentsWithLiteral := s.Solve(literalPropagated)
	for i, assignment := range assignmentsWithLiteral {
		assignmentsWithLiteral[i] = slices.Insert(assignment, 0, literal)
	}

	negatedLiteral := literal.Negate()
	negatedLiteralPropagated := s.propagate(negatedLiteral, problem)
	assignmentsWithLiteralNegated := s.Solve(negatedLiteralPropagated)
	for i, assignment := range assignmentsWithLiteralNegated {
		assignmentsWithLiteralNegated[i] = slices.Insert(assignment, 0, negatedLiteral)
	}

	return slices.Concat(assignmentsWithLiteral, assignmentsWithLiteralNegated)
}

func (s *DefaultSolver) propagate(literal Literal, problem Problem) Problem {
	return s.propagator.Propagate(literal, problem)
}

func main() {
	fmt.Println("Example: Trivial clauses")
	fmt.Println("Input: (1 or 2) and (-2 or 3)")
	problem := Problem{{1, 2}, {-2, 3}}
	solver := NewDefaultSolver()
	fmt.Println("Solutions:")
	for _, assignment := range solver.Solve(problem) {
		fmt.Println(assignment)
	}
}
