import static java.util.Comparator.comparingInt;

/// Literal represents a propositional logic literal.
///
/// @param value the value of the literal - cannot be 0
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

/// Clause represents a disjunction ("or") of literals.
///
/// @param literals the literals of this clause
record Clause(SequencedCollection<Literal> literals) {
    static Clause of(final Literal... literals) {
        return new Clause(List.of(literals));
    }

    boolean contains(final Literal literal) {
        return literals.contains(literal);
    }

    boolean isEmpty() {
        return literals.isEmpty();
    }

    int size() {
        return literals.size();
    }

    Clause without(final Literal literal) {
        if (!literals.contains(literal)) {
            return this;
        }
        final List<Literal> filteredLiterals = literals.stream()
                .filter(l -> l.value() != literal.value())
                .toList();
        return new Clause(filteredLiterals);
    }

    Literal head() {
        return literals.getFirst();
    }
}

/// Problem represents a conjunction ("and") of clauses.
///
/// @param clauses the clauses of this problem
record Problem(SequencedCollection<Clause> clauses) {
    static Problem of(final Clause... clauses) {
        return new Problem(List.of(clauses));
    }

    boolean isEmpty() {
        return clauses.isEmpty();
    }

    Clause head() {
        return clauses.getFirst();
    }
}

/// Assignment represents a set of literals satisfying a Problem.
///
/// @param literals the literals of this assignment
record Assignment(List<Literal> literals) {
    static final Assignment EMPTY = new Assignment(List.of());

    Assignment prependedWith(final Literal literal) {
        final var newLiterals = new ArrayList<Literal>(literals.size() + 1);
        newLiterals.add(literal);
        newLiterals.addAll(literals);
        return new Assignment(newLiterals);
    }
}

/// A function that propagates a unit clause (a literal) to simplify the problem.
interface Propagate extends BiFunction<Literal, Problem, Problem> {
    Propagate DEFAULT = new Propagate() {};
    Gatherer<Clause, Void, Clause> HALT_WHEN_CLAUSE_EMPTY = haltWhen(Clause::isEmpty);
    Comparator<Clause> COMPARING_BY_CLAUSE_SIZE = comparingInt(Clause::size);

    @Override
    default Problem apply(final Literal literal, final Problem problem) {
        final Literal negatedLiteral = literal.negated();
        final List<Clause> clausesAfterPropagation = problem.clauses().stream()
                .filter(clause -> !clause.contains(literal))
                .map(clause -> clause.without(negatedLiteral))
                .gather(HALT_WHEN_CLAUSE_EMPTY)
                .sorted(COMPARING_BY_CLAUSE_SIZE)
                .toList();
        return new Problem(clausesAfterPropagation);
    }

    private static <T> Gatherer<T, Void, T> haltWhen(final Predicate<T> predicate) {
        return Gatherer.of((_, element, downstream) -> {
            if (predicate.test(element)) {
                downstream.push(element);
                return false;
            }
            return downstream.push(element);
        });
    }
}

/// A function that solves a SAT problem.
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

        final Stream<Assignment> assignmentsWithLiteral = lazily(() -> propagate(literal, problem))
                .flatMap(this)
                .map(assignment -> assignment.prependedWith(literal));

        final Stream<Assignment> assignmentsWithLiteralNegated = lazily(() -> propagate(literal.negated(), problem))
                .flatMap(this)
                .map(assignment -> assignment.prependedWith(literal.negated()));

        return Stream.concat(assignmentsWithLiteral, assignmentsWithLiteralNegated);
    }

    private static <T> Stream<T> lazily(final Supplier<T> supplier) {
        return Stream.of(1).flatMap(i -> Stream.of(supplier.get()));
    }

    default Problem propagate(final Literal literal, Problem problem) {
        return Propagate.DEFAULT.apply(literal, problem);
    }
}

/// Implementation of Solve that uses an explicit stack rather than recursion.
///
/// Prevents stack overflow for large problems.
static class ExplicitStackSolver implements Solve {

    private record Frame(Problem problem, Assignment assignment) {}

    @Override
    public Stream<Assignment> apply(final Problem problem) {
        return Stream.of(problem).gather(solutions());
    }

    private Gatherer<Problem, Deque<Frame>, Assignment> solutions() {
        return Gatherer.ofSequential(ArrayDeque::new, (stack, root, downstream) -> {
            stack.push(new Frame(root, Assignment.EMPTY));

            while (!stack.isEmpty()) {
                final Frame frame = stack.pop();
                final Problem problem = frame.problem();
                final Assignment assignment = frame.assignment();

                if (problem.isEmpty()) {
                    if (downstream.push(assignment)) {
                        continue;
                    } else {
                        break;
                    }
                }

                final Clause headClause = problem.head();
                if (headClause.isEmpty()) {
                    continue;
                }

                final Literal literal = headClause.head();

                final Problem literalPropagated = propagate(literal, problem);
                final Problem negatedLiteralPropagated = propagate(literal.negated(), problem);

                stack.push(new Frame(negatedLiteralPropagated, assignment.prependedWith(literal.negated())));
                stack.push(new Frame(literalPropagated, assignment.prependedWith(literal)));
            }

            return false;
        });
    }
}

/// Utility class for creating clauses.
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

/// A sudoku problem.
static class Sudoku {

    record Solution(int[][] grid) {
        @Override
        public String toString() {
            return Arrays.deepToString(grid).replace("],", "]\n");
        }
    }

    private final int[][] grid;
    private final Problem problem;

    Sudoku(final int[][] initialGrid) {
        final var clauses = new LinkedHashSet<Clause>();

        // 1. No row contains dupe
        for (int row = 0; row < 9; row++) {
            for (int value = 0; value < 9; value++) {
                final var literals = new Literal[9];
                for (int column = 0; column < 9; column++) {
                    final int variable = variableFrom(row, column, value);
                    literals[column] = new Literal(variable);
                }
                clauses.addAll(Clauses.exactlyOne(literals));
            }
        }

        // 2. No column contains dupe
        for (int column = 0; column < 9; column++) {
            for (int value = 0; value < 9; value++) {
                final var literals = new Literal[9];
                for (int row = 0; row < 9; row++) {
                    final int variable = variableFrom(row, column, value);
                    literals[row] = new Literal(variable);
                }
                clauses.addAll(Clauses.exactlyOne(literals));
            }
        }

        // 3. No 3x3 box contains dupe
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
        }

        // 4. No cell contains dupe
        for (int row = 0; row < 9; row++) {
            for (int column = 0; column < 9; column++) {
                final var literals = new Literal[9];
                for (int value = 0; value < 9; value++) {
                    final int variable = variableFrom(row, column, value);
                    literals[value] = new Literal(variable);
                }
                clauses.addAll(Clauses.exactlyOne(literals));
            }
        }

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
        grid = initialGrid;
    }

    private static int variableFrom(final int row, final int column, final int value) {
        return row * 9 * 9 +
                column * 9 +
                value +
                1; // variables must be strictly positive
    }

    public Stream<Solution> solutions() {
        return solutionsUsing(Solve.DEFAULT);
    }

    public Stream<Solution> solutionsUsing(final Solve solve) {
        return solve.apply(problem).map(Sudoku::gridFrom);
    }

    private static Solution gridFrom(final Assignment assignment) {
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
        return new Solution(grid);
    }

    @Override
    public String toString() {
        return Arrays.deepToString(grid).replace("],", "]\n");
    }
}

/// Playground for the SAT solver.
void main() {

    IO.println("Example: Trivial clauses");
    IO.println("Input: (1 or 2) and (-2 or 3)");
    final var example = Problem.of(
            Clause.of(new Literal(1), new Literal(2)),
            Clause.of(new Literal(-2), new Literal(3))
    );
    IO.println("Solutions:");
    Solve.DEFAULT.apply(example).forEach(IO::println);

    IO.println();

    IO.println("Example: A sudoku problem");
    final var sudoku = new Sudoku(new int[][]{
            {0, 2, 6, 0, 0, 0, 8, 1, 0},
            {3, 0, 0, 7, 0, 8, 0, 0, 6},
            {4, 0, 0, 0, 5, 0, 0, 0, 7},
            {0, 5, 0, 1, 0, 7, 0, 9, 0},
            {0, 0, 3, 9, 0, 5, 1, 0, 0},
            {0, 4, 0, 3, 0, 2, 0, 5, 0},
            {1, 0, 0, 0, 3, 0, 0, 0, 2},
            {5, 0, 0, 2, 0, 4, 0, 0, 9},
            {0, 3, 8, 0, 0, 0, 4, 6, 0}});
    IO.println("Input:");
    IO.println(sudoku);

    IO.println("Solutions (using recursive solver):");
    for (int i = 0; i < 3; i++) {
        IO.println("Run #" + (i + 1) + ":");
        final long before = System.currentTimeMillis();
        sudoku.solutions().forEach(IO::println);
        final long after = System.currentTimeMillis();
        IO.println("Time: " + (after - before) + " ms");
    }

    IO.println("Solutions (using non-recursive solver):");
    for (int i = 0; i < 3; i++) {
        IO.println("Run #" + (i + 1) + ":");
        final long before = System.currentTimeMillis();
        sudoku.solutionsUsing(new ExplicitStackSolver()).forEach(IO::println);
        final long after = System.currentTimeMillis();
        IO.println("Time: " + (after - before) + " ms");
    }
}