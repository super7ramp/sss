import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A literal in a SAT problem.
 *
 * @param value the value of the literal - cannot be 0
 */
record Literal(int value) {
    Literal {
        if (value == 0) {
            throw new IllegalArgumentException("Literal value must not be 0");
        }
    }

    Literal negated() {
        return new Literal(-value);
    }
}

/**
 * A clause in a SAT problem.
 *
 * @param literals the literals of this clause
 */
record Clause(List<Literal> literals) {
    static Clause of(final Literal... literals) {
        return new Clause(List.of(literals));
    }

    boolean contains(final Literal literal) {
        return literals.contains(literal);
    }

    boolean isEmpty() {
        return literals.isEmpty();
    }

    Clause without(final Literal literal) {
        final List<Literal> filteredLiterals = literals.stream()
                .filter(l -> l.value() != literal.value())
                .toList();
        return new Clause(filteredLiterals);
    }

    Literal head() {
        return literals.getFirst();
    }

    Clause tail() {
        return new Clause(literals.subList(1, literals.size()));
    }
}

/**
 * A SAT problem.
 *
 * @param clauses the clauses of this problem
 */
record Problem(List<Clause> clauses) {
    static Problem of(final Clause... clauses) {
        return new Problem(List.of(clauses));
    }

    boolean isEmpty() {
        return clauses.isEmpty();
    }

    Clause head() {
        return clauses.getFirst();
    }

    Problem tail() {
        return new Problem(clauses.subList(1, clauses.size()));
    }

    Problem prependedWith(final Clause clause) {
        final var newClauses = new ArrayList<Clause>(clauses.size() + 1);
        newClauses.add(clause);
        newClauses.addAll(clauses);
        return new Problem(newClauses);
    }
}

/**
 * An assignment of literals to a SAT problem.
 *
 * @param literals the literals of this assignment
 */
record Assignment(List<Literal> literals) {
    static final Assignment EMPTY = new Assignment(List.of());

    Assignment prependedWith(final Literal literal) {
        final var newLiterals = new ArrayList<Literal>(literals.size() + 1);
        newLiterals.add(literal);
        newLiterals.addAll(literals);
        return new Assignment(newLiterals);
    }
}

/**
 * A function that propagates a literal in a SAT problem, to simplify the problem.
 */
interface Propagate extends BiFunction<Literal, Problem, Problem> {
    Propagate DEFAULT = new Propagate() {};

    @Override
    default Problem apply(final Literal literal, final Problem problem) {
        //System.out.println("Propagating " + literal + " in " + problem.clauses());
        final List<Clause> clausesAfterPropagation = problem.clauses().stream()
                .filter(clause -> !clause.contains(literal))
                .map(clause -> clause.without(literal.negated()))
                .toList();
        //System.out.println("Result: " + clausesAfterPropagation);
        return new Problem(clausesAfterPropagation);
    }
}

/**
 * A function that solves a SAT problem.
 */
interface Solve extends Function<Problem, Stream<Assignment>> {
    Solve DEFAULT = new Solve() {};

    @Override
    default Stream<Assignment> apply(final Problem problem) {
        if (problem.isEmpty()) {
            return Stream.of(Assignment.EMPTY);
        }

        final Clause headClause = problem.head();
        if (headClause.isEmpty()) {
            return Stream.empty();
        }

        final Literal literal = headClause.head();

        final Stream<Assignment> assignments = lazily(() -> {
            final Problem problemAfterPropagation = Propagate.DEFAULT.apply(literal, problem.tail());
            return apply(problemAfterPropagation);
        }).map(a -> a.prependedWith(literal));

        final Stream<Assignment> assignmentsAfterNegation = lazily(() -> {
            final Problem problemAfterPropagatingNegation = Propagate.DEFAULT.apply(literal.negated(), problem.tail().prependedWith(headClause.tail()));
            return apply(problemAfterPropagatingNegation);
        }).map(a -> a.prependedWith(literal.negated()));

        return Stream.concat(assignments, assignmentsAfterNegation);
    }

    private static <T> Stream<T> lazily(final Supplier<Stream<T>> supplier) {
        return Stream.of(1).flatMap(i -> supplier.get());
    }
}

/**
 * Utility class for creating clauses.
 */
static class Clauses {

    private Clauses() {
        // prevent instantiation
    }

    static List<Clause> atMostOne(final Literal... literals) {
        final var clauses = new ArrayList<Clause>();
        for (int i = 0; i < literals.length; i++) {
            for (int j = i + 1; j < literals.length; j++) {
                clauses.add(Clause.of(literals[i].negated(), literals[j].negated()));
            }
        }
        return clauses;
    }

    static List<Clause> exactlyOne(final Literal... literals) {
        final List<Clause> clauses = atMostOne(literals);
        clauses.add(Clause.of(literals));
        return clauses;
    }
}

/**
 * A sudoku problem.
 */
static class Sudoku {

    private final Problem problem;

    Sudoku(final int[][] initialGrid) {
        final var clauses = new ArrayList<Clause>();

        // 1. No row contains dupe
        /*
        for (int row = 0; row < 9; row++) {
            for (int value = 0; value < 9; value++) {
                final var literals = new Literal[9];
                for (int column = 0; column < 9; column++) {
                    final int variable = variableFrom(row, column, value);
                    literals[column] = new Literal(variable);
                }
                clauses.addAll(Clauses.exactlyOne(literals));
            }
        }*/

        // 2. No column contains dupe
        /*
        for (int column = 0; column < 9; column++) {
            for (int value = 0; value < 9; value++) {
                final var literals = new Literal[9];
                for (int row = 0; row < 9; row++) {
                    final int variable = variableFrom(row, column, value);
                    literals[row] = new Literal(variable);
                }
                clauses.addAll(Clauses.exactlyOne(literals));
            }
        }*/

        // 3. No 3x3 box contains dupe
        /*
        for (int startRow = 0; startRow < 9; startRow += 3) {
            for (int startColumn = 0; startColumn < 9; startColumn += 3) {
                for (int value = 0; value < 9; value++) {
                    final var literals = new Literal[9];
                    for (int rowOffset = 0; rowOffset < 3; rowOffset++) {
                        for (int columnOffset = 0; columnOffset < 3; columnOffset++) {
                            final int variable = variableFrom(startRow + rowOffset, startColumn + columnOffset, value);
                            literals[(rowOffset * 3) + columnOffset] = new Literal(variable);
                        }
                    }
                    clauses.addAll(Clauses.exactlyOne(literals));
                }
            }
        }*/

        // 4. No cell contains dupe
        /*
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                final var literals = new Literal[9];
                for (int value = 0; value < 9; value++) {
                    final int variable = variableFrom(row, column, value);
                    literals[value] = new Literal(variable);
                }
                clauses.addAll(Clauses.exactlyOne(literals));
            }
        }*/

        // 5. Initial values
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                final int value = initialGrid[row][column];
                if (value > 0) {
                    final int variable = variableFrom(row, column, value - 1);
                    clauses.add(Clause.of(new Literal(variable)));
                }
            }
        }
        problem = new Problem(clauses);
    }

    private static int variableFrom(final int row, final int column, final int value) {
        return row * 9 * 9 +
                column * 9 +
                value +
                1; // variables must be strictly positive
    }

    public Stream<int[][]> solutions() {
        return solutionsUsing(Solve.DEFAULT);
    }

    public Stream<int[][]> solutionsUsing(final Solve solve) {
        return solve.apply(problem).map(Sudoku::gridFrom);
    }

    private static int[][] gridFrom(final Assignment assignment) {
        final List<Literal> literals = assignment.literals();
        final int[][] grid = new int[9][9];
        for (final Literal literal : literals) {
            final int literalValue = literal.value();
            if (literalValue < 0) {
                continue;
            }
            final int value = (literalValue - 1) % 9;
            final int column = ((literalValue - 1) / 9) % 9;
            final int row = (literalValue - 1) / (9 * 9);
            grid[row][column] = value + 1;
        }
        return grid;
    }
}

/**
 * Playground for the SAT solver.
 */
void main() {

    System.out.println("Trivial example");
    final var example = Problem.of(
            Clause.of(new Literal(1), new Literal(2)),
            Clause.of(new Literal(-2), new Literal(3))
    );
    Solve.DEFAULT.apply(example).forEach(System.out::println);

    System.out.println("Sudoku");
    final var sudoku = new Sudoku(new int[][]{
            // impossible
            {2, 2, 6, 1, 3, 5, 8, 4, 9}, // 2 is duplicated
            {8, 3, 5, 2, 4, 9, 1, 7, 6},
            {1, 4, 9, 7, 8, 6, 2, 3, 5},
            {5, 6, 3, 4, 1, 2, 7, 9, 8},
            {4, 1, 2, 9, 7, 8, 5, 6, 3},
            {7, 9, 8, 5, 6, 3, 4, 1, 2},
            {6, 5, 4, 3, 2, 1, 9, 8, 7},
            {3, 2, 1, 8, 9, 7, 6, 5, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1},

            // simple, only cell to fill
            //{2, 7, 6, 1, 3, 5, 8, 4, 9},
            //{8, 3, 5, 2, 4, 9, 1, 7, 6},
            //{1, 4, 9, 7, 8, 6, 2, 3, 5},
            //{5, 6, 3, 4, 1, 2, 7, 9, 8},
            //{4, 1, 2, 9, 7, 8, 5, 6, 3},
            //{7, 9, 8, 5, 6, 3, 4, 1, 2},
            //{6, 5, 4, 3, 2, 1, 9, 8, 7},
            //{3, 2, 1, 8, 9, 7, 6, 5, 4},
            //{9, 8, 7, 6, 5, 4, 3, 2, 1}
    });
    sudoku.solutions().parallel().map(Arrays::deepToString).findAny().ifPresent(System.out::println);

}