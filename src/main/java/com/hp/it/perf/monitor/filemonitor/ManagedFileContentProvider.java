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
import javax.management.ObjectName;
import javax.management.StandardMBean;

abstract class ManagedFileContentProvider extends
		NotificationBroadcasterSupport implements NotificationEmitter,
		MBeanRegistration {

	public static final String DOMAIN = "com.hp.it.perf.monitor.filemonitor";

	public static final String LINE_RECORD = DOMAIN + ".lineRecord";

	private Map<ObjectName, ManagedFileContentProvider> managedFiles = new HashMap<ObjectName, ManagedFileContentProvider>();

	private MBeanServer mbeanServer;

	private ObjectName objectName;

	private AtomicLong seq = new AtomicLong();

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
		return line;
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

	protected String getProviderType() {
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
		prop.put(
				"name",
				objectName.getKeyProperty("name") + "/"
						+ mFile.getProviderName());
		ObjectName fileObjectName = ObjectName.getInstance(DOMAIN, prop);
		managedFiles.put(mbeanServer.registerMBean(mFile, fileObjectName)
				.getObjectName(), mFile);
	}

	@Override
	public void postRegister(Boolean registrationDone) {
		if (!registrationDone.booleanValue()) {
			for (ObjectName subName : new HashSet<ObjectName>(
					managedFiles.keySet())) {
				try {
					mbeanServer.unregisterMBean(subName);
				} catch (Exception ignored) {
				}
				managedFiles.remove(subName);
			}
			postDeregister();
		}
	}

	@Override
	public void preDeregister() throws Exception {
		for (ObjectName subName : new HashSet<ObjectName>(managedFiles.keySet())) {
			mbeanServer.unregisterMBean(subName);
			managedFiles.remove(subName);
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
				} catch (Exception e) {
					// TODO Auto-generated catch block
					// TODO log it
					e.printStackTrace();
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
						try {
							mbeanServer.unregisterMBean(subName);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							// TODO log it
							e.printStackTrace();
						}
						managedFiles.remove(subName);
					}
				}
			}
		}
	}
}
