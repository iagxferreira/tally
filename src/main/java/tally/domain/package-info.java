/**
 * The pure financial domain: money, accounts, postings, transactions, and the
 * ledger that holds them.
 *
 * <p>Nothing in this package may depend on infrastructure — no HTTP, no JDBC,
 * no serialisation framework, no dependency injection. That independence is
 * upheld by review rather than by the build.
 *
 * <p>Amounts here never use {@code float} or {@code double}.
 *
 * <h2>Folders are not packages</h2>
 *
 * <p>The sources are grouped into directories — {@code money/},
 * {@code account/}, {@code ledger/}, {@code failure/} — but <b>every file
 * declares {@code package tally.domain}</b>. The folders exist to make things
 * findable; they are not subpackages, no imports cross them, and the compiled
 * classes all land flat in {@code tally/domain}.
 *
 * <p>This is deliberate, and it is what lets the domain stay one package while
 * still being navigable. Two invariants depend on that single package and would
 * break if these folders became real subpackages:
 *
 * <ul>
 *   <li>{@code Posting}'s constructor and {@code flip()} are package-private,
 *       so only {@code Account} and {@code Transaction} can mint a posting.
 *       Real subpackages would force them public and delete the guarantee.
 *   <li>{@code DomainException} is {@code sealed}. Outside a named module, a
 *       sealed type's permitted subclasses must share its package —
 *       {@code javac} rejects anything else with <i>"class in unnamed module
 *       cannot extend a sealed class in a different package"</i>.
 * </ul>
 *
 * <p><b>The cost:</b> IDEs expect a file's directory to match its package, and
 * will report these as mismatched — IntelliJ offers to "fix" it by moving files
 * or rewriting the declaration. Do not accept that offer; it would silently
 * make the folders into packages and break both invariants above. Tools that
 * resolve sources through a sourcepath rather than an explicit file list may
 * also fail to find classes here. Gradle passes an explicit list, so the build
 * is unaffected.
 */
package tally.domain;
