package tally.domain;

/**
 * The base of every failure the financial domain raises.
 *
 * <p>Unchecked, deliberately. These represent a caller asking the domain for
 * something it must refuse — adding dollars to yen, posting a negative amount,
 * committing an unbalanced transaction. A well-written caller does not hit
 * them, so forcing every call site to declare or catch them would add ceremony
 * without adding safety. A single {@code catch (DomainException e)} at an
 * application boundary is the intended shape.
 *
 * <p>Subclasses carry the <em>values</em> that caused the failure as typed
 * fields, not just a formatted message. The message exists for stack traces and
 * logs; code that needs to react to a failure matches on the exception type and
 * reads its fields. Domain errors are matchable values, never prose.
 *
 * <h2>Why this is sealed</h2>
 *
 * <p>Sealing turns error handling from a runtime concern into a compile-time
 * one. A {@code switch} over this type is checked for exhaustiveness and needs
 * no {@code default} branch, so when a new failure is added to
 * {@code permits}, every handler that does not account for it stops compiling.
 *
 * <p>The alternative — an open hierarchy — forces handlers to end in a
 * catch-all that silently swallows failures nobody thought about. In a ledger
 * that is the wrong default: a failure mode added in one place should surface
 * as a broken build everywhere it is handled, not as an unhandled case
 * discovered in production.
 *
 * <p>The cost is that adding a failure means editing the {@code permits} list.
 * That is the mechanism working, not friction to route around.
 */
public sealed abstract class DomainException extends RuntimeException
        permits CurrencyMismatchException, NonPositiveAmountException {

    private static final long serialVersionUID = 1L;

    /** @param message a human-readable summary, built from the failure's typed fields */
    protected DomainException(String message) {
        super(message);
    }
}
