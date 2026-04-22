package grebe.felix.managed;

/**
 * Represents a specialized {@link AutoCloseable} resource with additional support
 * for querying its closed state. The interface is primarily designed for managing
 * resources requiring manual or automated cleanup while tracking their lifecycle.
 *
 * Implementations must ensure that the {@link #isClosed()} method accurately
 * reflects the resource's state after being closed via {@link #close()}.
 */
public interface ManagedClosable extends AutoCloseable {

	/**
	 * Indicates whether the resource has been closed.
	 * @return true if the resource has been closed, false otherwise.
	 */
	boolean isClosed();
}
