package grebe.felix.managed;

import java.lang.ref.Cleaner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A specialized {@code ThreadLocal} implementation for tracking and managing
 * thread-local resources that implement the {@link ManagedClosable} interface.
 * This class ensures proper cleanup of resources when they are no longer needed
 * to prevent resource leaks.
 *
 * <p>
 * Resources set via {@link ManagedThreadLocal#set(ManagedClosable)} are automatically
 * tracked. When a new resource is set or the thread-local value is removed using
 * {@link ManagedThreadLocal#remove()}, previously set resources are closed if
 * they are not already closed. In addition, resources are cleaned up automatically
 * when the associated thread is garbage collected.
 * </p>
 *
 * <p>
 * Internally, this class uses a {@link Cleaner} to register cleanup
 * actions for resources when their associated thread is garbage collected. Manual
 * cleanup of all tracked resources can be performed using the {@link #close()}
 * method.
 * </p>
 *
 * <p>
 * The resource cleanup mechanism involves creating instances of an internal
 * state class and registering them with the {@link Cleaner}. These
 * registered cleanup actions ensure that resources are closed automatically even
 * if the thread-local reference is no longer accessible.
 * </p>
 *
 * @param <T> the type of the thread-local value which must implement the
 *            {@link ManagedClosable} interface
 */
public class ManagedThreadLocal<T extends ManagedClosable> extends ThreadLocal<T> implements AutoCloseable {

	/**
	 * A {@link Cleaner} instance used to manage the automatic cleanup of resources
	 * associated with thread-local values. The {@code cleaner} ensures that when
	 * resources are no longer associated with any active thread or when the thread
	 * is garbage-collected, appropriate cleanup actions are performed to release those resources.
	 */
	private static final Cleaner cleaner = Cleaner.create();

	/**
	 * Stores all {@link Cleaner.Cleanable} instances to prevent them from being garbage
	 * collected prematurely. This ensures that cleanup actions registered with the
	 * {@link Cleaner} are executed as intended.
	 */
	private final Set<Cleaner.Cleanable> cleanables = ConcurrentHashMap.newKeySet();


	/**
	 * Closes and clears all currently registered {@link Cleaner.Cleanable} objects within this
	 * {@code ManagedThreadLocal} instance. This method ensures that resources associated with
	 * the registered {@link Cleaner.Cleanable} objects are freed, preventing potential resource
	 * leaks.
	 */
	@Override
	public void close() {
		cleanables.forEach(Cleaner.Cleanable::clean);
		cleanables.clear();
	}


	/**
	 * Represents a container for associating a value of type {@link ManagedClosable} with the thread
	 * that owns the value. This record is primarily used to facilitate resource management and
	 * cleanup operations in conjunction with thread-local storage.
	 *
	 * @param <T> the type of the managed resource, which must implement {@link ManagedClosable}.
	 * @param value the {@link ManagedClosable} instance being managed. This value is expected
	 *              to ensure proper resource lifecycle management by implementing close
	 *              semantics and indicating whether it has been closed.
	 * @param owningThread the {@link Thread} that owns the associated {@code value}. This is
	 *                     used to associate the managed resource with the thread on which it is
	 *                     currently being used, allowing for context-specific resource handling.
	 */
	private record ValueHolder<T extends ManagedClosable>(T value, Thread owningThread) {
	}

	/**
	 * Represents a cleanup operation that ensures a {@link ManagedClosable} resource
	 * is properly closed when the associated thread-local resource becomes eligible for
	 * garbage collection.
	 *
	 * @param <T>   the type of the resource being managed, which must implement {@link ManagedClosable}.
	 * @param value The managed resource instance that implements {@link ManagedClosable}.
	 *              This resource will be automatically closed when the associated cleanup
	 *              operation is executed to ensure proper resource management.
	 */
	private record CleanupState<T extends ManagedClosable>(T value) implements Runnable {

		/**
		 * Executes the cleanup procedure for the associated {@link ManagedClosable} resource.
		 * This method checks if the resource is not already closed, and if not, it logs the
		 * auto-close operation
		 */
		@Override
		public void run() {
			if (this.value != null && !this.value.isClosed()) {
				try {
					this.value.close();
				} catch (final Exception ignored) {
				}
			}
		}
	}


	/**
	 * Sets the thread-local value for the current thread. If a value is already set
	 * for the current thread, it is closed if it has not already been closed.
	 *
	 * @param value the new thread-local value to set; must implement {@link ManagedClosable}.
	 *              If the value is null, no cleanup action is registered.
	 */
	@Override
	public void set(T value) {
		final T oldValue = get();
		if (oldValue != null && !oldValue.isClosed()) {
			try {
				oldValue.close();
			} catch (final Exception ignored) {
			}
		}

		super.set(value);

		if (value != null) {
			final Thread currentThread = Thread.currentThread();
			final ValueHolder<T> holder = new ValueHolder<>(value, currentThread);
			final CleanupState<T> state = new CleanupState<>(value);
			final Cleaner.Cleanable cleanable = cleaner.register(holder, state);
			this.cleanables.add(cleanable);
		}
	}


	/**
	 * Removes the current thread's value for this thread-local variable. If a value is present,
	 * and it implements the {@link ManagedClosable} interface, this method ensures the value
	 * is properly closed before removal.
	 */
	@Override
	public void remove() {
		final T value = get();

		if (value != null && !value.isClosed()) {
			try {
				value.close();
			} catch (Exception ignored) {
			}
		}
		super.remove();
	}
}