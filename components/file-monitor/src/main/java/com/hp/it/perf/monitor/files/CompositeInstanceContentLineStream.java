package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.FileChangeQueue.ChangeEvent;

public class CompositeInstanceContentLineStream extends
		AbstractContentLineStream<Object> implements ContentLineStream {

	private final Object streamTracker = new Object();

	private ContentLineStreamProviderDelegator streamDelegator;

	private FileOpenOption openOption;

	private String name;

	private FileInstanceChangeAware instanceChange;

	private static final Logger log = LoggerFactory
			.getLogger(CompositeInstanceContentLineStream.class);

	public CompositeInstanceContentLineStream(String name,
			FileOpenOption openOption,
			ContentLineStreamProviderDelegator streamDelegator,
			FileInstanceChangeAware instanceChange) {
		this.name = name;
		this.openOption = openOption;
		this.streamDelegator = streamDelegator;
		this.instanceChange = instanceChange;
		fileUpdateNotifier = new FileChangeQueue<Object>(true) {

			protected void onFileInstanceDeleted(Object provider,
					FileInstance instance) {
				removeFileContentChangeAware(instance);
				closeContentStream(instance);
			}

			protected void onFileInstanceCreated(Object provider,
					FileInstance instance) {
				addFileContentChangeAware(instance);
			}

		};
		fileUpdateNotifier.addFileInstanceChangeAware(instanceChange);
	}

	public void addFileInstance(FileInstance instance) throws IOException {
		log.debug("add file instance into streams: {}", instance);
		fileUpdateNotifier.addMonitorInstance(instance);
		if (openOption.openOnTail()) {
			// fileUpdateNotifier.preCheckQueue(instanceChange, instance);
			getContentStream(instance);
		}
		fileUpdateNotifier.addFileContentChangeAware(instance);
	}

	protected ContentLineStream getContentStream(FileInstance file)
			throws IOException {
		ContentLineStream stream = (ContentLineStream) file
				.getClientProperty(streamTracker);
		if (stream == null) {
			stream = streamDelegator.openLineStream(file, openOption);
			file.putClientProperty(streamTracker, stream);
		}
		return stream;
	}

	protected void closeContentStream(FileInstance file) {
		ContentLineStream stream = (ContentLineStream) file
				.getClientProperty(streamTracker);
		if (stream != null) {
			file.putClientProperty(streamTracker, null);
			close(stream);
		}
	}

	final protected void onClosed() {
		for (FileInstance file : fileUpdateNotifier.getMonitoredInstances()) {
			closeContentStream(file);
		}
	}

	private void close(Closeable closeable) {
		try {
			closeable.close();
		} catch (IOException e) {
			// TODO log it
		}
	}

	@Override
	protected ContentLineStream getContentStream(ChangeEvent<Object> event)
			throws IOException {
		return getContentStream(event.getInstance());
	}

	@Override
	final protected void onClosing() {
		fileUpdateNotifier.removeFileInstanceChangeAware(instanceChange);
	}

	public String toString() {
		return name;
	}

}
