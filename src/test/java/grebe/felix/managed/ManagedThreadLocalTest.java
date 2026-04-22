package grebe.felix.managed;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagedThreadLocalTest {

	@Test
	public void testSetStoresValue() {
		ManagedThreadLocal<MockManagedClosable> threadLocal = new ManagedThreadLocal<>();
		MockManagedClosable mockValue = new MockManagedClosable();
		threadLocal.set(mockValue);
		assertEquals(mockValue, threadLocal.get(), "The stored value should match the value set.");
	}


	@Test
	public void testCleanUpOnValueChange() {
		ManagedThreadLocal<MockManagedClosable> threadLocal = new ManagedThreadLocal<>();
		MockManagedClosable mockOldValue = new MockManagedClosable();
		MockManagedClosable mockNewValue = new MockManagedClosable();

		threadLocal.set(mockOldValue);
		threadLocal.set(mockNewValue);
		assertTrue(mockOldValue.isClosed(), "The MockManagedClosable should be closed when cleaned up.");
	}


	@Test
	public void testCleanUpOnThreadExit() throws InterruptedException {
		ManagedThreadLocal<MockManagedClosable> threadLocal = new ManagedThreadLocal<>();
		AtomicBoolean closed = new AtomicBoolean(false);
		Thread.ofPlatform().start(() -> {
			MockManagedClosable mockValue = new MockManagedClosable();
			threadLocal.set(mockValue);
			mockValue.runOnClose = () -> closed.set(true);
		}).join();
		while (!closed.get()) {
			System.gc();
		}
		assertTrue(closed.get(), "The MockManagedClosable should be closed when thread exits.");
	}

	@Test
	public void testCleanUpOnRemove() {
		ManagedThreadLocal<MockManagedClosable> threadLocal = new ManagedThreadLocal<>();
		MockManagedClosable mockValue = new MockManagedClosable();
		threadLocal.set(mockValue);
		threadLocal.remove();
		assertTrue(mockValue.isClosed(), "The MockManagedClosable should be closed when cleaned up.");
	}


	// Mock implementation of ManagedClosable for testing
	private static class MockManagedClosable implements ManagedClosable {
		private boolean closed = false;

		private Runnable runOnClose = null;


		public MockManagedClosable() {
			super();
		}


		public MockManagedClosable(Runnable runnable) {
			this();
			this.runOnClose = runnable;
		}


		@Override
		public void close() {
			closed = true;
			if (runOnClose != null) {
				runOnClose.run();
			}
		}


		@Override
		public boolean isClosed() {
			return closed;
		}
	}


}
