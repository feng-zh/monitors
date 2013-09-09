package com.hp.it.perf.monitor.files.nio;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.hp.it.perf.monitor.files.CompositeInstanceContentLineStream;
import com.hp.it.perf.monitor.files.ContentLineStream;
import com.hp.it.perf.monitor.files.ContentLineStreamProvider;
import com.hp.it.perf.monitor.files.FileCluster;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceChangeAwareProxy;
import com.hp.it.perf.monitor.files.FileInstanceChangeListener;
import com.hp.it.perf.monitor.files.FileOpenOption;

class MonitorFileCluster implements FileCluster, ContentLineStreamProvider,
		FileInstanceChangeListener {

	private final FileInstanceChangeAwareProxy proxy = new FileInstanceChangeAwareProxy();

	private final String name;

	private final List<FileInstance> instanceList = new CopyOnWriteArrayList<FileInstance>();

	private final MonitorFileFolder folder;

	MonitorFileCluster(String clusterName, MonitorFileFolder folder) {
		this.name = clusterName;
		this.folder = folder;
	}

	@Override
	public void addFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		proxy.addFileInstanceChangeListener(listener);
	}

	@Override
	public void removeFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		proxy.removeFileInstanceChangeListener(listener);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public List<FileInstance> listInstances() {
		return instanceList;
	}

	void addFileInstance(MonitorFileInstance fileInstance) {
		instanceList.add(fileInstance);
	}

	void removeFileInstance(MonitorFileInstance fileInstance) {
		instanceList.add(fileInstance);
	}

	@Override
	public ContentLineStream open(FileOpenOption option) throws IOException {
		final CompositeInstanceContentLineStream contentStream = new CompositeInstanceContentLineStream(
				"cluster " + name + "@" + folder.getFolder(), option, folder) {

			@Override
			protected void onClosing() {
				removeFileInstanceChangeListener(this);
				folder.removeFileContentChangeListener(this);
			}

		};
		addFileInstanceChangeListener(contentStream);
		folder.addFileContentChangeListener(contentStream);
		for (FileInstance instance : instanceList) {
			contentStream.addFileInstance(instance);
		}
		return contentStream;
	}

	boolean isEmpty() {
		return instanceList.isEmpty();
	}

	private boolean isInclude(FileInstance instance) {
		return (instance instanceof MonitorFileInstance)
				&& name.equals(((MonitorFileInstance) instance)
						.getClusterName());
	}

	@Override
	public void onFileInstanceCreated(FileInstance instance,
			FileChangeOption changeOption) {
		FileInstance checkInstance = instance;
		if (changeOption.isRenameOption()) {
			// old file instance
			checkInstance = changeOption.getRenameFile();
		}
		if (!isInclude(checkInstance)) {
			return;
		}
		proxy.onFileInstanceCreated(instance, changeOption);
	}

	@Override
	public void onFileInstanceDeleted(FileInstance instance,
			FileChangeOption changeOption) {
		if (!isInclude(instance)) {
			return;
		}
		proxy.onFileInstanceDeleted(instance, changeOption);
	}

}
