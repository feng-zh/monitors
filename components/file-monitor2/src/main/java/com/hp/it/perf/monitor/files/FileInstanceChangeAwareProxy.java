package com.hp.it.perf.monitor.files;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FileInstanceChangeAwareProxy implements FileInstanceChangeAware,
		FileInstanceChangeListener {

	private List<FileInstanceChangeListener> listenerList = new CopyOnWriteArrayList<FileInstanceChangeListener>();

	private Map<FileInstance, List<FileInstanceChangeListener>> singleListenerMap = new ConcurrentHashMap<FileInstance, List<FileInstanceChangeListener>>();

	private FileInstance filter;

	static public class SingleFileInstanceChangeAwareProxy extends
			FileInstanceChangeAwareProxy implements SingleFileIntanceProvider {

		private FileInstance instance;

		public SingleFileInstanceChangeAwareProxy(FileInstance instance) {
			super(instance);
			this.instance = instance;
		}

		@Override
		public FileInstance getFileInstance() {
			return instance;
		}

	}

	public FileInstanceChangeAwareProxy(FileInstance filter) {
		this.filter = filter;
	}

	public FileInstanceChangeAwareProxy() {
	}

	@Override
	final public void addFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		if (listener instanceof SingleFileIntanceProvider) {
			SingleFileIntanceProvider singleListener = (SingleFileIntanceProvider) listener;
			List<FileInstanceChangeListener> list = listOfSingleListener(
					singleListener.getFileInstance(), true);
			list.add(listener);
		} else {
			listenerList.add(listener);
		}
	}

	@Override
	final public void removeFileInstanceChangeListener(
			FileInstanceChangeListener listener) {
		if (listener instanceof SingleFileIntanceProvider) {
			SingleFileIntanceProvider singleListener = (SingleFileIntanceProvider) listener;
			List<FileInstanceChangeListener> list = listOfSingleListener(
					singleListener.getFileInstance(), false);
			if (!list.isEmpty()) {
				list.remove(listener);
			}
			if (list.isEmpty()) {
				singleListenerMap.remove(singleListener.getFileInstance());
			}
		} else {
			listenerList.remove(listener);
		}
	}

	private List<FileInstanceChangeListener> listOfSingleListener(
			FileInstance instance, boolean create) {
		List<FileInstanceChangeListener> list = singleListenerMap.get(instance);
		if (list == null) {
			if (create) {
				list = new CopyOnWriteArrayList<FileInstanceChangeListener>();
				singleListenerMap.put(instance, list);
			} else {
				list = Collections.emptyList();
			}
		}
		return list;
	}

	public void onFileInstanceCreated(FileInstance instance,
			FileChangeOption changeOption) {
		if (isFiltered(instance)) {
			return;
		}
		for (FileInstanceChangeListener li : listOfSingleListener(instance,
				false)) {
			li.onFileInstanceCreated(instance, changeOption);
		}
		for (FileInstanceChangeListener li : listenerList) {
			li.onFileInstanceCreated(instance, changeOption);
		}
	}

	public void onFileInstanceDeleted(FileInstance instance,
			FileChangeOption changeOption) {
		if (isFiltered(instance)) {
			return;
		}
		for (FileInstanceChangeListener li : listOfSingleListener(instance,
				false)) {
			li.onFileInstanceDeleted(instance, changeOption);
		}
		for (FileInstanceChangeListener li : listenerList) {
			li.onFileInstanceDeleted(instance, changeOption);
		}
	}

	protected boolean isFiltered(FileInstance instance) {
		return filter != null && instance != filter;
	}

	public boolean isEmpty() {
		return listenerList.isEmpty() && singleListenerMap.isEmpty();
	}

}
