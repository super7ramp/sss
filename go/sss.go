package main

import (
	"fmt"
	"slices"
	"strings"
	"time"
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

// Assignment represents a set of literals satisfying a Problem.
type Assignment []Literal

// Propagator is a function that propagates a unit clause (a literal) to simplify the problem.
type Propagator func(literal Literal, problem Problem) Problem

// DefaultPropagator is the default Propagator implementation.
func DefaultPropagator(literal Literal, problem Problem) Problem {
	negatedLiteral := literal.Negate()
	var clausesAfterPropagation Problem
	for _, clause := range problem {
		if slices.Contains(clause, literal) {
			continue
		}
		updatedClause := clause.Without(negatedLiteral)
		if updatedClause.IsEmpty() {
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
	propagate Propagator
}

func NewDefaultSolver() *DefaultSolver {
	return &DefaultSolver{propagate: DefaultPropagator}
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

func AtMostOne(literals []Literal) []Clause {
	var clauses []Clause
	for i, aLiteral := range literals {
		for _, anotherLiteral := range literals[i+1:] {
			clauses = append(clauses, Clause{aLiteral.Negate(), anotherLiteral.Negate()})
		}
	}
	return clauses
}

func ExactlyOne(literals []Literal) []Clause {
	clauses := AtMostOne(literals)
	clauses = append(clauses, literals)
	return clauses
}

type SudokuGrid [][]int

func (sg SudokuGrid) String() string {
	formattedArray := fmt.Sprint([][]int(sg))
	return strings.ReplaceAll(formattedArray, "] ", "]\n")
}

type Sudoku struct {
	grid    SudokuGrid
	problem Problem
}

func NewSudoku(grid SudokuGrid) *Sudoku {
	var clauses []Clause

	// 1. No row contains dupe
	for row := range 9 {
		for value := 1; value <= 9; value++ {
			literals := make([]Literal, 9)
			for col := range 9 {
				variable := sudokuVarNumber(row, col, value)
				literals[col] = Literal(variable)
			}
			clauses = append(clauses, ExactlyOne(literals)...)
		}
	}

	// 2. No column contains dupe
	for col := range 9 {
		for value := 1; value <= 9; value++ {
			literals := make([]Literal, 9)
			for row := range 9 {
				variable := sudokuVarNumber(row, col, value)
				literals[row] = Literal(variable)
			}
			clauses = append(clauses, ExactlyOne(literals)...)
		}
	}

	// 3. No 3x3 box contains dupe
	for startRow := 0; startRow < 9; startRow += 3 {
		for startCol := 0; startCol < 9; startCol += 3 {
			for value := 1; value <= 9; value++ {
				literals := make([]Literal, 9)
				for rowOffset := range 3 {
					for colOffset := range 3 {
						variable := sudokuVarNumber(startRow+rowOffset, startCol+colOffset, value)
						literals[rowOffset*3+colOffset] = Literal(variable)
					}
				}
				clauses = append(clauses, ExactlyOne(literals)...)
			}
		}
	}

	// 4. No cell contains dupe
	for row := range 9 {
		for col := range 9 {
			literals := make([]Literal, 9)
			for value := 1; value <= 9; value++ {
				variable := sudokuVarNumber(row, col, value)
				literals[value-1] = Literal(variable)
			}
			clauses = append(clauses, ExactlyOne(literals)...)
		}
	}

	// 5. Initial values
	for row := range 9 {
		for col := range 9 {
			value := grid[row][col]
			if value > 0 {
				variable := sudokuVarNumber(row, col, value)
				clauses = append(clauses, Clause{Literal(variable)})
			}
		}
	}

	problem := Problem(clauses)
	return &Sudoku{grid: grid, problem: problem}
}

func sudokuVarNumber(row, col, value int) int {
	return row*9*9 + col*9 + value
}

func (s *Sudoku) Solutions() []SudokuGrid {
	return s.SolutionsUsing(NewDefaultSolver())
}

func (s *Sudoku) SolutionsUsing(solver Solver) []SudokuGrid {
	var solutions []SudokuGrid
	for _, assignment := range solver.Solve(s.problem) {
		solutions = append(solutions, s.gridFrom(assignment))
	}
	return solutions
}

func (s *Sudoku) gridFrom(assignment Assignment) [][]int {
	grid := make([][]int, 9)
	for i := range grid {
		grid[i] = make([]int, 9)
	}
	for _, literal := range assignment {
		if literal < 1 {
			continue
		}
		value := ((literal - 1) % 9) + 1
		col := ((literal - 1) / 9) % 9
		row := (literal - 1) / (9 * 9)
		grid[row][col] = int(value)
	}
	return grid
}

func (s *Sudoku) String() string {
	return fmt.Sprint(s.grid)
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
	fmt.Println()

	fmt.Println("Example: A sudoku problem")
	fmt.Println("Input:")
	sudoku := NewSudoku([][]int{
		{0, 2, 6, 0, 0, 0, 8, 1, 0},
		{3, 0, 0, 7, 0, 8, 0, 0, 6},
		{4, 0, 0, 0, 5, 0, 0, 0, 7},
		{0, 5, 0, 1, 0, 7, 0, 9, 0},
		{0, 0, 3, 9, 0, 5, 1, 0, 0},
		{0, 4, 0, 3, 0, 2, 0, 5, 0},
		{1, 0, 0, 0, 3, 0, 0, 0, 2},
		{5, 0, 0, 2, 0, 4, 0, 0, 9},
		{0, 3, 8, 0, 0, 0, 4, 6, 0},
	})
	fmt.Println(sudoku)
	fmt.Println("Solutions:")
	for _, solution := range sudoku.Solutions() {
		start := time.Now()
		fmt.Println(solution)
		fmt.Println("Time: ", time.Since(start))
	}
}
