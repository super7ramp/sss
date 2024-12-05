import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Deconstruction of a list into head and tail.
 *
 * @param <T> the type of the list elements
 */
sealed interface Deconstruction<T> {
    record HeadTail<T>(T head, List<T> tail) implements Deconstruction<T> {}
    record Empty<T>() implements Deconstruction<T> {}
    static <T> Deconstruction<T> from(final List<T> list) {
        if (list.isEmpty()) {
            return new Empty<>();
        }
        return new HeadTail<>(list.getFirst(), list.subList(1, list.size()));
    }
}

/**
 * A literal in a SAT problem.
 *
 * @param value the value of the literal
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

    Clause tail() {
        return new Clause(literals.subList(1, literals.size()));
    }
}

record Problem(List<Clause> clauses) {
    static Problem of(final Clause... clauses) {
        return new Problem(List.of(clauses));
    }

    boolean isEmpty() {
        return clauses.isEmpty();
    }

    Problem tail() {
        return new Problem(clauses.subList(1, clauses.size()));
    }

    Problem prependedWith(Clause clause) {
        final var newClauses = new ArrayList<Clause>(clauses.size() + 1);
        newClauses.add(clause);
        newClauses.addAll(clauses);
        return new Problem(newClauses);
    }
}

record Assignment(List<Literal> literals) {
    static final Assignment EMPTY = new Assignment(List.of());
    Assignment prependedWith(final Literal literal) {
        final var newLiterals = new ArrayList<Literal>(literals.size() + 1);
        newLiterals.add(literal);
        newLiterals.addAll(literals);
        return new Assignment(newLiterals);
    }
}

interface Propagate extends BiFunction<Literal, Problem, Problem> {
    Propagate DEFAULT = new Propagate() {};

    @Override
    default Problem apply(final Literal literal, final Problem problem) {
        final List<Clause> clausesAfterPropagation = problem.clauses().stream()
                .filter(clause -> !clause.contains(literal))
                .map(clause -> clause.without(literal.negated()))
                .toList();
        return new Problem(clausesAfterPropagation);
    }
}

interface Solve extends Function<Problem, List<Assignment>> {
    Solve DEFAULT = new Solve() {};

    @Override
    default List<Assignment> apply(final Problem problem) {
        if (problem.isEmpty()) {
            return List.of(Assignment.EMPTY);
        }

        final Clause headClause = problem.clauses().getFirst();
        if (headClause.isEmpty()) {
            return List.of();
        }

        final Literal headLiteral = headClause.literals().getFirst();
        final Problem problemAfterPropagation = Propagate.DEFAULT.apply(headLiteral, problem);

        final Stream<Assignment> assignments = apply(problemAfterPropagation).stream()
                .map(a -> a.prependedWith(headLiteral));

        final Problem problemWithoutFirstLiteral = problem.tail().prependedWith(headClause.tail());
        final Problem problemAfterPropagatingNegation = Propagate.DEFAULT.apply(headLiteral.negated(), problemWithoutFirstLiteral);
        final Stream<Assignment> assignmentsAfterNegation = apply(problemAfterPropagatingNegation).stream()
                .map(a -> a.prependedWith(headLiteral.negated()));

        return Stream.concat(assignments, assignmentsAfterNegation).toList();
    }
}

void main() {
    final var example = Problem.of(
            Clause.of(new Literal(1), new Literal(2)),
            Clause.of(new Literal(-2), new Literal(3))
    );
    Solve.DEFAULT.apply(example).forEach(System.out::println);
}