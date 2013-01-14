package com.hp.it.perf.monitor.filemonitor;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class ManagedFileContentProvider extends
		NotificationBroadcasterSupport implements NotificationEmitter,
		MBeanRegistration {

	private static final Logger log = LoggerFactory
			.getLogger(ManagedFileContentProvider.class);

	// TODO not finalized
	public static final String DOMAIN = "com.hp.it.perf.monitor.filemonitor";

	public static final String LINE_RECORD = DOMAIN + ".lineRecord";

	private Map<ObjectName, ManagedFileContentProvider> managedFiles = new HashMap<ObjectName, ManagedFileContentProvider>();

	private MBeanServer mbeanServer;

	private ObjectName objectName;

	private AtomicLong seq = new AtomicLong();

	private int readLineCount;

	private long readByteCount;

	protected abstract String getProviderName();

	public abstract List<FileContentInfo> getFileContentInfos(boolean realtime)
			throws IOException;

	public abstract void close() throws IOException;

	protected ManagedFileContentProvider() {
		super(new MBeanNotificationInfo(new String[] { LINE_RECORD },
				LineRecord.class.getName(), "line record notification"));
	}

	public static interface NotificationInfoSerializerInterface {
		public LineRecord getLineRecord();

		public void setLineRecord(LineRecord info);
	}

	public static class NotificationInfoSerializer extends StandardMBean
			implements NotificationInfoSerializerInterface {

		private LineRecord lineRecord;

		private static NotificationInfoSerializer me = new NotificationInfoSerializer();

		public NotificationInfoSerializer() {
			super(NotificationInfoSerializerInterface.class, true);
		}

		public LineRecord getLineRecord() {
			return lineRecord;
		}

		public void setLineRecord(LineRecord info) {
			this.lineRecord = info;
		}

		public static Object serializeLineRecord(LineRecord info) {
			try {
				me.setLineRecord(info);
				return me.getAttribute("LineRecord");
			} catch (Exception ex) {
				throw new RuntimeException("Unexpected exception", ex);
			}
		}
	}

	protected LineRecord onLineRead(LineRecord line) {
		Notification notification = new Notification(LINE_RECORD, this,
				seq.incrementAndGet(), System.currentTimeMillis());
		notification.setUserData(line);
		sendNotification(notification);
		readLineCount++;
		readByteCount += line.getLine().length;
		return line;
	}

	public int getReadLineCount() {
		return readLineCount;
	}

	public long getReadByteCount() {
		return readByteCount;
	}

	@Override
	protected void handleNotification(NotificationListener listener,
			Notification notif, Object handback) {
		Notification notification;
		synchronized (this) {
			if (notif.getUserData() instanceof LineRecord) {
				LineRecord line = (LineRecord) notif.getUserData();
				String message;
				try {
					message = new String(line.getLine(), "UTF-8");
				} catch (UnsupportedEncodingException ignored) {
					message = new String(line.getLine());
				}
				notification = new Notification(notif.getType(),
						notif.getSource(), notif.getSequenceNumber(),
						notif.getTimeStamp(), message);
				notification.setUserData(NotificationInfoSerializer
						.serializeLineRecord(line));
				notif.setSource(notification);
			} else if (notif.getUserData() instanceof Notification) {
				notification = (Notification) notif.getUserData();
			} else {
				notification = notif;
			}
		}
		super.handleNotification(listener, notification, handback);
	}

	public String getProviderType() {
		return getClass().getSimpleName();
	}

	protected Collection<FileContentProvider> providers() {
		return Collections.emptyList();
	}

	@Override
	public ObjectName preRegister(MBeanServer server, ObjectName name)
			throws Exception {
		if (mbeanServer != null) {
			throw new IllegalStateException(
					"mbean is registered on server as object name: "
							+ objectName);
		}
		this.mbeanServer = server;
		this.objectName = name;
		// register sub files
		for (FileContentProvider file : providers()) {
			if (file instanceof ManagedFileContentProvider) {
				registerSubProvider((ManagedFileContentProvider) file);
			}
		}
		return name;
	}

	private void registerSubProvider(ManagedFileContentProvider mFile)
			throws Exception {
		Hashtable<String, String> prop = new Hashtable<String, String>(
				objectName.getKeyPropertyList());
		prop.put("type", mFile.getProviderType());
		prop.put("name", mFile.getProviderName());
		prop.put(getProviderType(), objectName.getKeyProperty("name"));
		ObjectName fileObjectName = ObjectName.getInstance(ObjectName
				.getInstance(DOMAIN, prop).getCanonicalName());
		registerMBean(mFile, fileObjectName);
		log.debug("register sub provider: {}", fileObjectName);
	}

	private synchronized void registerMBean(ManagedFileContentProvider mFile,
			ObjectName fileObjectName) throws Exception {
		try {
			ManagedFileContentProvider oldProvider = managedFiles
					.remove(fileObjectName);
			if (oldProvider != null) {
				unregisterMBean(fileObjectName);
			}
			ObjectInstance objectInstance = mbeanServer.registerMBean(mFile,
					fileObjectName);
			managedFiles.put(objectInstance.getObjectName(), mFile);
		} catch (Exception e) {
			log.warn("fail to register sub provider: " + fileObjectName, e);
			throw e;
		}
	}

	@Override
	public void postRegister(Boolean registrationDone) {
		if (!registrationDone.booleanValue()) {
			for (ObjectName subName : new HashSet<ObjectName>(
					managedFiles.keySet())) {
				unregisterMBean(subName);
			}
			postDeregister();
		}
	}

	@Override
	public void preDeregister() throws Exception {
		for (ObjectName subName : new HashSet<ObjectName>(managedFiles.keySet())) {
			unregisterMBean(subName);
		}
	}

	@Override
	public void postDeregister() {
		mbeanServer = null;
		objectName = null;
	}

	protected void onSubProviderCreated(FileContentProvider subProvider) {
		if (mbeanServer != null) {
			if (subProvider instanceof ManagedFileContentProvider) {
				try {
					registerSubProvider((ManagedFileContentProvider) subProvider);
				} catch (Exception ignored) {
				}
			}
		}
	}

	protected void onSubProviderRemoved(FileContentProvider subProvider) {
		if (mbeanServer != null) {
			if (subProvider instanceof ManagedFileContentProvider) {
				for (ObjectName subName : new HashSet<ObjectName>(
						managedFiles.keySet())) {
					if (subProvider == managedFiles.get(subName)) {
						unregisterMBean(subName);
						break;
					}
				}
			}
		}
	}

	private synchronized void unregisterMBean(ObjectName subName) {
		try {
			mbeanServer.unregisterMBean(subName);
			log.debug("unregister sub provider: {}", subName);
		} catch (Exception e) {
			log.warn("fail to unregister sub provider: " + subName, e);
		}
		managedFiles.remove(subName);
	}
}
