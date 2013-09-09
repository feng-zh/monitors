package com.hp.it.perf.monitor.files;

import java.util.concurrent.atomic.AtomicInteger;

public class DefaultFileStatistics implements FileStatistics {

	public static class WriteableCount implements Count {

		private final AtomicInteger value = new AtomicInteger();

		private final String name;

		public WriteableCount(String name) {
			this.name = name;
		}

		@Override
		public int get() {
			return value.get();
		}

		public void increment() {
			value.incrementAndGet();
		}

		public void decrement() {
			value.decrementAndGet();
		}

		@Override
		public String toString() {
			return String.format("%s=%s", name, value);
		}

	}

	private WriteableCount instanceCount = new WriteableCount("file-instance");
	private WriteableCount setCount = new WriteableCount("file-set");
	private WriteableCount clusterCount = new WriteableCount("file-cluster");
	private WriteableCount ioReaderCount = new WriteableCount("io-reader");
	private WriteableCount ioResourceCount = new WriteableCount("io-resource");

	@Override
	public WriteableCount fileInstanceCount() {
		return instanceCount;
	}

	@Override
	public WriteableCount fileSetCount() {
		return setCount;
	}

	@Override
	public WriteableCount fileClusterCount() {
		return clusterCount;
	}

	@Override
	public WriteableCount ioReaderCount() {
		return ioReaderCount;
	}

	@Override
	public WriteableCount ioResourceCount() {
		return ioResourceCount;
	}

}
