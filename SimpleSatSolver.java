import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Comparator.comparingInt;

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

    static Clause EMPTY = new Clause(List.of());

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

/**
 * A SAT problem.
 *
 * @param clauses the clauses of this problem
 */
record Problem(List<Clause> clauses) {
    static Problem UNSATISFIABLE = new Problem(List.of(Clause.EMPTY));

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
        final var clausesAfterPropagation = new ArrayList<Clause>();
        for (final Clause clause : problem.clauses()) {
            if (clause.contains(literal)) {
                continue;
            }
            final Clause clauseWithoutLiteralNegated = clause.without(literal.negated());
            if (clauseWithoutLiteralNegated.isEmpty()) {
                // No need to continue propagating this literal
                return Problem.UNSATISFIABLE;
            }
            clausesAfterPropagation.add(clauseWithoutLiteralNegated);
        }
        clausesAfterPropagation.sort(comparingInt(Clause::size));
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

    private Problem propagate(final Literal literal, Problem problem) {
        return Propagate.DEFAULT.apply(literal, problem);
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

    static Clause implication(final Literal left, final Literal right) {
        return Clause.of(left.negated(), right);
    }

    static List<Clause> equivalence(final Literal left, final Literal right) {
        return List.of(implication(left, right), implication(right, left));
    }
}

/**
 * A sudoku problem.
 */
static class Sudoku {

    private final int[][] grid;
    private final Problem problem;

    Sudoku(final int[][] initialGrid) {
        final var clauses = new ArrayList<Clause>();

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

    @Override
    public String toString() {
        return Arrays.deepToString(grid).replace("],", "\n");
    }
}

/**
 * The Eternity II puzzle.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Eternity_II_puzzle">Eternity II</a> problem.
 * @see <a href="https://github.com/super7ramp/eternity2-solver">eternity2-solver</a>, a SAT4j-based Eternity II solver
 */
static final class Eternity2 {

    public record Piece(int id, int northColor, int eastColor, int southColor, int westColor) {

        enum Border {
            NORTH,
            EAST,
            SOUTH,
            WEST;

            private static final List<Border> CACHED_VALUES = List.of(values());

            static Iterable<Border> all() {
                return CACHED_VALUES;
            }

            static int count() {
                return CACHED_VALUES.size();
            }
        }

        enum Rotation {
            PLUS_0,
            PLUS_90,
            PLUS_180,
            PLUS_270;

            private static final List<Rotation> CACHED_VALUES = List.of(values());

            static List<Rotation> all() {
                return CACHED_VALUES;
            }

            static int count() {
                return CACHED_VALUES.size();
            }
        }

        int colorTo(final Border border) {
            return switch (border) {
                case NORTH -> northColor;
                case EAST -> eastColor;
                case SOUTH -> southColor;
                case WEST -> westColor;
            };
        }

        Piece rotate(final Rotation rotation) {
            return switch (rotation) {
                case PLUS_0 -> this;
                case PLUS_90 -> new Piece(id, westColor, northColor, eastColor, southColor);
                case PLUS_180 -> new Piece(id, southColor, westColor, northColor, eastColor);
                case PLUS_270 -> new Piece(id, eastColor, southColor, westColor, northColor);
            };
        }

        Rotation rotationTo(final Piece piece) {
            if (piece.id() != id) {
                throw new IllegalArgumentException("Different piece ids: " + id + " != " + piece.id());
            }
            for (final Rotation rotation : Rotation.all()) {
                if (rotate(rotation).equals(piece)) {
                    return rotation;
                }
            }
            throw new IllegalArgumentException(this + " is not a rotation of this piece: " + piece);
        }
    }

    static final class Game {

        private final Piece[] pieces;
        private final Piece[][] initialBoard;
        private final int rowCount;
        private final int columnCount;
        private final int colorCount;

        Game(final Piece[] pieces, final Piece[][] initialBoard) {
            this.pieces = Objects.requireNonNull(pieces);
            this.initialBoard = Objects.requireNonNull(initialBoard);
            rowCount = initialBoard.length;
            columnCount = rowCount == 0 ? 0 : initialBoard[0].length;
            if (rowCount * columnCount != pieces.length) {
                throw new IllegalArgumentException("Inconsistent number of pieces: " + pieces.length + " != " + rowCount + " * " + columnCount);
            }
            colorCount = (int) Arrays.stream(pieces)
                    .flatMapToInt(piece -> IntStream.of(piece.northColor(), piece.eastColor(), piece.southColor(), piece.westColor()))
                    .distinct()
                    .count();
        }

        Piece piece(final int pieceNumber) {
            return pieces[pieceNumber];
        }

        Optional<Piece> initialBoardPiece(final int rowIndex, final int columnIndex) {
            return Optional.ofNullable(initialBoard[rowIndex][columnIndex]);
        }

        int rowCount() {
            return rowCount;
        }

        int columnCount() {
            return columnCount;
        }

        int piecesCount() {
            return pieces.length;
        }

        int colorCount() {
            return colorCount;
        }
    }

    private final Game game;
    private final Problem problem;

    Eternity2(final Piece[] pieces, final Piece[][] initialBoard) {
        game = new Game(pieces, initialBoard);
        final var clauses = new ArrayList<Clause>();

        // 0. Initial board
        for (int rowIndex = 0; rowIndex < game.rowCount(); rowIndex++) {
            for (int columnIndex = 0; columnIndex < game.columnCount(); columnIndex++) {
                final Optional<Piece> fixedPiece = game.initialBoardPiece(rowIndex, columnIndex);
                if (fixedPiece.isPresent()) {
                    final int pieceIndex = fixedPiece.get().id();
                    final Piece originalPiece = game.piece(pieceIndex);
                    final Piece.Rotation rotation = originalPiece.rotationTo(fixedPiece.get());
                    final Literal pieceLit = new Literal(variableRepresentingPiece(rowIndex, columnIndex, pieceIndex, rotation));
                    clauses.add(Clause.of(pieceLit));
                }
            }
        }

        // 1. There is exactly one piece with exactly one rotation, in each position.
        for (int rowIndex = 0; rowIndex < game.rowCount(); rowIndex++) {
            for (int columnIndex = 0; columnIndex < game.columnCount(); columnIndex++) {
                final var piecesPerPosition = new Literal[game.piecesCount() * Piece.Rotation.count()];
                for (int pieceIndex = 0; pieceIndex < game.piecesCount(); pieceIndex++) {
                    for (final Piece.Rotation rotation : Piece.Rotation.all()) {
                        final int pieceVariable = variableRepresentingPiece(rowIndex, columnIndex, pieceIndex, rotation);
                        piecesPerPosition[pieceIndex * Piece.Rotation.count() + rotation.ordinal()] = new Literal(pieceVariable);
                    }
                }
                clauses.addAll(Clauses.exactlyOne(piecesPerPosition));
            }
        }

        // 2. There is exactly one position per piece
        for (int pieceIndex = 0; pieceIndex < game.piecesCount(); pieceIndex++) {
            final var positionsPerPiece = new Literal[game.rowCount() * game.columnCount() * Piece.Rotation.count()];
            for (int rowIndex = 0; rowIndex < game.rowCount(); rowIndex++) {
                for (int columnIndex = 0; columnIndex < game.columnCount(); columnIndex++) {
                    for (final Piece.Rotation rotation : Piece.Rotation.all()) {
                        final int pieceVariable = variableRepresentingPiece(rowIndex, columnIndex, pieceIndex, rotation);
                        positionsPerPiece[rowIndex * game.columnCount() * Piece.Rotation.count() + columnIndex * Piece.Rotation.count() + rotation.ordinal()] = new Literal(pieceVariable);
                    }
                }
            }
            clauses.addAll(Clauses.exactlyOne(positionsPerPiece));
        }

        // 3. Adjacent borders have the same color
        for (int row = 0; row < game.rowCount(); row++) {
            for (int column = 0; column < game.columnCount() - 1; column++) {
                for (int color = 0; color < game.colorCount(); color++) {
                    final var eastBorder = new Literal(variableRepresentingBorder(row, column, Piece.Border.EAST, color));
                    final var neighborWestBorder = new Literal(variableRepresentingBorder(row, column + 1, Piece.Border.WEST, color));
                    clauses.addAll(Clauses.equivalence(eastBorder, neighborWestBorder));
                }
            }
        }
        for (int rowIndex = 0; rowIndex < game.rowCount() - 1; rowIndex++) {
            for (int columnIndex = 0; columnIndex < game.columnCount(); columnIndex++) {
                for (int colorIndex = 0; colorIndex < game.colorCount(); colorIndex++) {
                    final var southBorder = new Literal(variableRepresentingBorder(rowIndex, columnIndex, Piece.Border.SOUTH, colorIndex));
                    final var neighborNorthBorder = new Literal(variableRepresentingBorder(rowIndex + 1, columnIndex, Piece.Border.NORTH, colorIndex));
                    clauses.addAll(Clauses.equivalence(southBorder, neighborNorthBorder));
                }
            }
        }

        // 4. Colors of the borders match the colors of the pieces
        for (int rowIndex = 0; rowIndex < game.rowCount(); rowIndex++) {
            for (int columnIndex = 0; columnIndex < game.columnCount(); columnIndex++) {
                for (int pieceIndex = 0; pieceIndex < game.piecesCount(); pieceIndex++) {
                    for (final Piece.Rotation rotation : Piece.Rotation.all()) {
                        final var pieceLit = new Literal(variableRepresentingPiece(rowIndex, columnIndex, pieceIndex, rotation));
                        final Piece piece = game.piece(pieceIndex).rotate(rotation);
                        for (final Piece.Border border : Piece.Border.all()) {
                            final int color = piece.colorTo(border);
                            final var pieceBorder = new Literal(variableRepresentingBorder(rowIndex, columnIndex, border, color));
                            clauses.add(Clauses.implication(pieceLit, pieceBorder));
                        }
                    }
                }
            }
        }

        problem = new Problem(clauses);
    }

    private int variableRepresentingPiece(final int rowIndex, final int columnIndex, final int pieceIndex, final Piece.Rotation rotation) {
        if (rowIndex >= game.rowCount()) {
            throw new IllegalArgumentException("Row index out of bounds: " + rowIndex);
        }
        if (columnIndex >= game.columnCount()) {
            throw new IllegalArgumentException("Column index out of bounds: " + columnIndex);
        }
        if (pieceIndex >= game.piecesCount()) {
            throw new IllegalArgumentException("Piece index out of bounds: " + pieceIndex);
        }
        return rowIndex * game.columnCount() * game.piecesCount() * Piece.Rotation.count()
                + columnIndex * game.piecesCount() * Piece.Rotation.count()
                + pieceIndex * Piece.Rotation.count()
                + rotation.ordinal()
                + 1; // variables start at 1
    }

    int variableRepresentingPieceCount() {
        return game.rowCount() * game.columnCount() * game.piecesCount() * Piece.Rotation.count();
    }

    int variableRepresentingBorder(final int rowIndex, final int columnIndex, final Piece.Border border, final int colorIndex) {
        if (rowIndex >= game.rowCount()) {
            throw new IllegalArgumentException("Row index out of bounds: " + rowIndex);
        }
        if (columnIndex >= game.columnCount()) {
            throw new IllegalArgumentException("Column index out of bounds: " + columnIndex);
        }
        if (colorIndex >= game.colorCount()) {
            throw new IllegalArgumentException("Color index out of bounds: " + colorIndex);
        }
        return variableRepresentingPieceCount() + 1
                + rowIndex * game.columnCount() * Piece.Border.count() * game.colorCount()
                + columnIndex * Piece.Border.count() * game.colorCount()
                + border.ordinal() * game.colorCount()
                + colorIndex;
    }

    private Piece[][] boardFrom(final Assignment assignment) {
        final var pieces = new Piece[game.rowCount()][game.columnCount()];
        for (final Literal literal : assignment.literals()) {
            final int literalValue = literal.value();
            if (literalValue < 0 || literalValue > variableRepresentingPieceCount()) {
                continue;
            }
            final int rowIndex = (literalValue - 1) / (game.columnCount() * game.piecesCount() * Piece.Rotation.count());
            final int columnIndex = ((literalValue - 1) / (game.piecesCount() * Piece.Rotation.count())) % game.columnCount();
            final int pieceIndex = ((literalValue - 1) / Piece.Rotation.count()) % game.piecesCount();
            final int rotationIndex = (literalValue - 1) % Piece.Rotation.count();
            final Piece piece = game.piece(pieceIndex).rotate(Piece.Rotation.all().get(rotationIndex));
            pieces[rowIndex][columnIndex] = piece;
        }
        return pieces;
    }

    public Stream<Piece[][]> solutions() {
        return solutionsUsing(Solve.DEFAULT);
    }

    public Stream<Piece[][]> solutionsUsing(final Solve solve) {
        return solve.apply(problem).map(this::boardFrom);
    }
}

/**
 * Playground for the SAT solver.
 */
void main() {

    System.out.print("""
            Example: Trivial clauses
            Input: (1 or 2) and (-2 or 3)
            Solutions:
            """);
    final var example = Problem.of(
            Clause.of(new Literal(1), new Literal(2)),
            Clause.of(new Literal(-2), new Literal(3))
    );
    Solve.DEFAULT.apply(example).forEach(System.out::println);

    System.out.println();

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
    System.out.printf("""
            A sudoku problem
            Input:
            %s
            Solutions:
            """, sudoku);
    sudoku.solutions()
            .map(s -> Arrays.deepToString(s).replace("],", "\n"))
            .forEach(System.out::println);

    System.out.println("Example: A (reduced) Eternity 2 puzzle");
    final var pieces = new Eternity2.Piece[]{
            new Eternity2.Piece(0, 1, 0, 2, 1), new Eternity2.Piece(1, 1, 2, 4, 1), new Eternity2.Piece(2, 1, 2, 0, 1), new Eternity2.Piece(3, 1, 2, 0, 1), new Eternity2.Piece(4, 1, 3, 5, 4),
            new Eternity2.Piece(5, 1, 0, 8, 2), new Eternity2.Piece(6, 1, 3, 8, 2), new Eternity2.Piece(7, 1, 4, 5, 0), new Eternity2.Piece(8, 1, 3, 6, 0), new Eternity2.Piece(9, 1, 2, 5, 2),
            new Eternity2.Piece(10, 1, 3, 6, 2), new Eternity2.Piece(11, 1, 0, 7, 3), new Eternity2.Piece(12, 1, 4, 6, 3), new Eternity2.Piece(13, 1, 2, 7, 2), new Eternity2.Piece(14, 1, 0, 6, 3),
            new Eternity2.Piece(15, 1, 2, 5, 3), new Eternity2.Piece(16, 6, 8, 4, 5), new Eternity2.Piece(17, 4, 8, 5, 5), new Eternity2.Piece(18, 6, 8, 7, 6), new Eternity2.Piece(19, 4, 8, 6, 8),
            new Eternity2.Piece(20, 6, 7, 6, 7), new Eternity2.Piece(21, 6, 5, 8, 4), new Eternity2.Piece(22, 5, 8, 5, 8), new Eternity2.Piece(23, 5, 7, 7, 7), new Eternity2.Piece(24, 6, 6, 6, 5),
    };
    final var initialBoard = new Eternity2.Piece[5][5];
    initialBoard[2][2] = pieces[19].rotate(Eternity2.Piece.Rotation.PLUS_90);
    final var eternity2 = new Eternity2(pieces, initialBoard);
    final var count = new AtomicInteger(0);
    eternity2.solutions()
            .map(Arrays::deepToString)
            .peek(s -> System.out.println(count.incrementAndGet()))
            .distinct()
            .forEach(System.out::println);
}