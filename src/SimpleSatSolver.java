import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

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
}

record Problem(List<Clause> clauses) {
    static Problem of(final Clause... clauses) {
        return new Problem(List.of(clauses));
    }
    boolean isEmpty() {
        return clauses.isEmpty();
    }
}

record Assignment(List<Literal> literals) {
    static final Assignment EMPTY = new Assignment(List.of());
}

interface Propagate extends BiFunction<Literal, Problem, Problem> {
    Propagate DEFAULT = new Propagate() {};

    @Override
    default Problem apply(final Literal literal, final Problem problem) {
        final List<Clause> clausesAfterPropagation = problem.clauses().stream()
                .filter(clause -> !clause.contains(literal))
                .map(clause -> new Clause(clause.literals().stream().filter(l -> l.value() != -literal.value()).toList()))
                .toList();
        return new Problem(clausesAfterPropagation);
    }
}

interface Solve extends Function<Problem, Optional<Assignment>> {
    Solve DEFAULT = new Solve() {};

    @Override
    default Optional<Assignment> apply(final Problem problem) {
        if (problem.isEmpty()) {
            return Optional.of(Assignment.EMPTY);
        }

        final Clause headClause = problem.clauses().getFirst();
        if (headClause.isEmpty()) {
            return Optional.empty();
        }

        final Literal headLiteral = headClause.literals().getFirst();
        final Problem problemAfterPropagation = Propagate.DEFAULT.apply(headLiteral, problem);
        final Optional<Assignment> assignment = apply(problemAfterPropagation);
        if (assignment.isPresent()) {
            final var literals = new ArrayList<Literal>();
            literals.add(headLiteral);
            literals.addAll(assignment.get().literals());
            return Optional.of(new Assignment(literals));
        }

        final Clause firstClause = problem.clauses().getFirst();
        final Clause firstClauseWithoutFirstLiteral = new Clause(firstClause.literals().subList(1, firstClause.literals().size()));
        final List<Clause> clausesWithoutFirstClauseFirstLiteral = new ArrayList<>(problem.clauses().subList(1, problem.clauses().size()));
        clausesWithoutFirstClauseFirstLiteral.addFirst(firstClauseWithoutFirstLiteral);
        final Problem problemWithoutFirstLiteral = new Problem(clausesWithoutFirstClauseFirstLiteral);
        final Problem problemAfterNegation = Propagate.DEFAULT.apply(headLiteral.negated(), problemWithoutFirstLiteral);
        final Optional<Assignment> assignmentAfterNegation = apply(problemAfterNegation);
        if (assignmentAfterNegation.isEmpty()) {
            return Optional.empty();
        }
        final var literals = new ArrayList<Literal>();
        literals.add(headLiteral.negated());
        literals.addAll(assignmentAfterNegation.get().literals());
        return Optional.of(new Assignment(literals));
    }
}

void main() {
    final var example = Problem.of(
            Clause.of(new Literal(1), new Literal(2)),
            Clause.of(new Literal(-2), new Literal(3))
    );
    Solve.DEFAULT.apply(example).ifPresentOrElse(
            assignment -> System.out.println("SAT: " + assignment),
            () -> System.out.println("UNSAT")
    );
}