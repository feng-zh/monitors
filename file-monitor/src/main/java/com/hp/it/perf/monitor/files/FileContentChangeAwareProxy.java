package com.hp.it.perf.monitor.files;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FileContentChangeAwareProxy implements FileContentChangeAware,
		FileContentChangeListener {

	protected List<FileContentChangeListener> listenerList = new CopyOnWriteArrayList<FileContentChangeListener>();

	private Map<FileInstance, List<FileContentChangeListener>> singleListenerMap = new ConcurrentHashMap<FileInstance, List<FileContentChangeListener>>();

	private final Object tracker = new Object();

	static public class SingleFileContentChangeAwareProxy extends
			FileContentChangeAwareProxy implements SingleFileIntanceProvider {

		private FileInstance instance;

		public SingleFileContentChangeAwareProxy(FileInstance instance) {
			this.instance = instance;
		}

		@Override
		public FileInstance getFileInstance() {
			return instance;
		}

		public void addFileInstance(FileInstance instance) {
			throw new IllegalStateException(
					"single file content change listener not support change");
		}

		@Override
		public void removeFileInstance(FileInstance instance) {
			throw new IllegalStateException(
					"single file content change listener not support change");
		}

		@Override
		public void onContentChanged(FileInstance instance) {
			if (instance == this.instance) {
				for (FileContentChangeListener listener : listenerList) {
					listener.onContentChanged(instance);
				}
			}
		}

	}

	public void addFileInstance(FileInstance instance) {
		instance.putClientProperty(tracker, tracker);
	}

	public void removeFileInstance(FileInstance instance) {
		if (instance.getClientProperty(tracker) != null) {
			instance.putClientProperty(tracker, null);
		}
	}

	@Override
	public void addFileContentChangeListener(FileContentChangeListener listener) {
		if (listener instanceof SingleFileIntanceProvider) {
			SingleFileIntanceProvider singleListener = (SingleFileIntanceProvider) listener;
			List<FileContentChangeListener> list = listOfSingleListener(
					singleListener.getFileInstance(), true);
			list.add(listener);
		} else {
			listenerList.add(listener);
		}
	}

	@Override
	public void removeFileContentChangeListener(
			FileContentChangeListener listener) {
		if (listener instanceof SingleFileIntanceProvider) {
			SingleFileIntanceProvider singleListener = (SingleFileIntanceProvider) listener;
			List<FileContentChangeListener> list = listOfSingleListener(
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

	private List<FileContentChangeListener> listOfSingleListener(
			FileInstance instance, boolean create) {
		List<FileContentChangeListener> list = singleListenerMap.get(instance);
		if (list == null) {
			if (create) {
				list = new CopyOnWriteArrayList<FileContentChangeListener>();
				singleListenerMap.put(instance, list);
			} else {
				list = Collections.emptyList();
			}
		}
		return list;
	}

	@Override
	public void onContentChanged(FileInstance instance) {
		if (instance.getClientProperty(tracker) != null) {
			for (FileContentChangeListener listener : listOfSingleListener(
					instance, false)) {
				listener.onContentChanged(instance);
			}
			for (FileContentChangeListener listener : listenerList) {
				listener.onContentChanged(instance);
			}
		}
	}

}
