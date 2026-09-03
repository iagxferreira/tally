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
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** @param message a human-readable summary, built from the failure's typed fields */
    protected DomainException(String message) {
        super(message);
    }
}
