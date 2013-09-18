package com.hp.it.perf.monitor.files;

public class FileOpenOptionBuilder {

	private boolean talMode;

	private boolean lazyMode;

	private boolean noMonitor;

	public FileOpenOptionBuilder tailMode() {
		talMode = true;
		return this;
	}

	public FileOpenOptionBuilder lazyMode() {
		lazyMode = true;
		return this;
	}

	public FileOpenOption build() {
		return new FileOpenOption() {

			@Override
			public boolean openOnTail() {
				return talMode;
			}

			@Override
			public boolean lazyOpen() {
				return lazyMode;
			}

			@Override
			public boolean monitor() {
				return !noMonitor;
			}

		};
	}

	public FileOpenOptionBuilder noMonitor() {
		noMonitor = true;
		return this;
	}

}
