package com.hp.it.perf.monitor.files;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.FileChangeQueue.ChangeEvent;

public class SuperSetContentLineStream extends
		AbstractContentLineStream<ContentLineStreamProvider> implements
		ContentLineStream {

	private FileOpenOption openOption;

	private static final Logger log = LoggerFactory
			.getLogger(SuperSetContentLineStream.class);

	private Map<ContentLineStreamProvider, ContentLineStream> allInstances = new HashMap<ContentLineStreamProvider, ContentLineStream>();

	public SuperSetContentLineStream(FileOpenOption openOption) {
		this.openOption = openOption;
		this.fileUpdateNotifier = new FileChangeQueue<ContentLineStreamProvider>(
				false);
	}

	public void addFileSet(FileSet fileSet) throws IOException {
		addProvider((ContentLineStreamProvider) fileSet);
	}

	private void addProvider(ContentLineStreamProvider provider)
			throws IOException {
		allInstances.put(provider, null);
		fileUpdateNotifier.addFileContentChangeAware(provider);
		fileUpdateNotifier.addFileInstanceChangeAware(provider);
		if (openOption.openOnTail()) {
			fileUpdateNotifier.preCheckQueue(provider, null);
			getContentStream(provider);
		}
	}

	public void addFileInstance(FileInstance instance) throws IOException {
		addProvider((ContentLineStreamProvider) instance);
	}

	public void removeFileInstance(FileInstance instance) throws IOException {
		removeProvider((ContentLineStreamProvider) instance);
	}

	private void removeProvider(ContentLineStreamProvider provider)
			throws IOException {
		preCloseProvider(provider);
		postCloseProvider(provider);
		// TODO in progress
	}

	public void removeFileSet(FileSet fileSet) throws IOException {
		removeProvider((ContentLineStreamProvider) fileSet);
	}

	protected ContentLineStream getContentStream(
			ChangeEvent<ContentLineStreamProvider> changeEvent)
			throws IOException {
		return getContentStream(changeEvent.getProvider());
	}

	private ContentLineStream getContentStream(
			ContentLineStreamProvider provider) throws IOException {
		ContentLineStream stream = allInstances.get(provider);
		if (stream == null) {
			stream = provider.open(openOption);
			allInstances.put(provider, stream);
		}
		return stream;
	}

	private void closeContentStream(ContentLineStreamProvider provider) {
		ContentLineStream stream = allInstances.get(provider);
		if (stream != null) {
			close(stream);
		}
	}

	private void postCloseProvider(ContentLineStreamProvider provider) {
		closeContentStream(provider);
		allInstances.remove(provider);
	}

	private void preCloseProvider(ContentLineStreamProvider provider) {
		fileUpdateNotifier.removeFileContentChangeAware(provider);
		fileUpdateNotifier.removeFileInstanceChangeAware(provider);
	}

	final protected void onClosed() {
		for (ContentLineStreamProvider provider : new HashSet<ContentLineStreamProvider>(
				allInstances.keySet())) {
			postCloseProvider(provider);
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
	final protected void onClosing() {
		for (ContentLineStreamProvider provider : allInstances.keySet()) {
			preCloseProvider(provider);
		}
	}

}
