package com.hp.it.perf.monitor.filemonitor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

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

	private static final int BUFFER_SIZE = Integer.getInteger(
			"monitor.content.notification.size", 1000);

	private static final int BUFFER_TIME = Integer.getInteger(
			"monitor.content.notification.time", 2000);

	private Map<ObjectName, ManagedFileContentProvider> managedFiles = new HashMap<ObjectName, ManagedFileContentProvider>();

	private MBeanServer mbeanServer;

	private ObjectName objectName;

	private AtomicLong seq = new AtomicLong();

	private int readLineCount;

	private long readByteCount;

	private boolean compressMode = true;

	private LineRecordBuffer lineBuffer;
	
	private boolean notificationEnabled = false;

	private static ScheduledExecutorService scheduler = Executors
			.newSingleThreadScheduledExecutor(new ThreadFactory() {

				@Override
				public Thread newThread(Runnable r) {
					Thread thread = Executors.defaultThreadFactory().newThread(
							r);
					thread.setDaemon(true);
					thread.setName("Content Notification Timer");
					return thread;
				}
			});

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
		if (notificationEnabled) {
			if (compressMode) {
				if (lineBuffer == null) {
					lineBuffer = new LineRecordBuffer(BUFFER_SIZE, BUFFER_TIME,
							new LineRecordBuffer.BufferHandler() {
	
								@Override
								public void handleBuffer(Queue<LineRecord> buffer) {
									Notification notification = new Notification(
											LINE_RECORD, this, seq
													.incrementAndGet(), System
													.currentTimeMillis());
									notification
											.setSource(ManagedFileContentProvider.this);
									ArrayList<LineRecord> list = new ArrayList<LineRecord>(
											buffer.size());
									LineRecord line;
									while ((line = buffer.poll()) != null) {
										list.add(line);
									}
									notification.setUserData(list);
									sendNotification(notification);
								}
							}, scheduler);
				}
				lineBuffer.add(line);
			} else {
				if (lineBuffer != null) {
					lineBuffer.flush();
					lineBuffer = null;
				}
				Notification notification = new Notification(LINE_RECORD, this,
						seq.incrementAndGet(), System.currentTimeMillis());
				notification.setUserData(line);
				sendNotification(notification);
			}
		}
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
				notif.setUserData(notification);
			} else if (notif.getUserData() instanceof List) {
				@SuppressWarnings("unchecked")
				List<LineRecord> lines = (List<LineRecord>) notif.getUserData();
				LineRecord line = new LineRecord();
				// compressed flag
				line.setProviderId(-1);
				// lines
				int lineSize = lines.size();
				line.setLineNum(lineSize);
				byte[] compressedLines = compressLines(lines);
				line.setLine(compressedLines);
				notification = new Notification(notif.getType(),
						notif.getSource(), notif.getSequenceNumber(),
						notif.getTimeStamp(), "Compressed lines - " + lineSize);
				notification.setUserData(NotificationInfoSerializer
						.serializeLineRecord(line));
				notif.setUserData(notification);
			} else if (notif.getUserData() instanceof Notification) {
				notification = (Notification) notif.getUserData();
			} else {
				notification = notif;
			}
		}
		super.handleNotification(listener, notification, handback);
	}

	private byte[] compressLines(List<LineRecord> lines) {
		ByteArrayOutputStream baOut = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
				new DeflaterOutputStream(baOut), 512));
		try {
			out.writeInt(lines.size());
			for (LineRecord line : lines) {
				out.writeLong(line.getProviderId());
				out.writeInt(line.getLineNum());
				out.writeInt(line.getLine().length);
				out.write(line.getLine());
			}
			out.close();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return baOut.toByteArray();
	}

	public List<LineRecord> decompressLines(byte[] bytes) {
		try {
			DataInputStream input = new DataInputStream(
					new BufferedInputStream(new InflaterInputStream(
							new ByteArrayInputStream(bytes))));
			int size = input.readInt();
			List<LineRecord> lines = new ArrayList<LineRecord>(size);
			for (int i = 0; i < size; i++) {
				LineRecord line = new LineRecord();
				line.setProviderId(input.readLong());
				line.setLineNum(input.readInt());
				int lineLen = input.readInt();
				byte[] lineBytes = new byte[lineLen];
				input.readFully(lineBytes);
				line.setLine(lineBytes);
				lines.add(line);
			}
			input.close();
			return lines;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
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

	public void setCompressMode(boolean mode) {
		compressMode = mode;
	}

	public boolean isCompressMode() {
		return compressMode;
	}

	public boolean isNotificationEnabled() {
		return notificationEnabled;
	}

	public void setNotificationEnabled(boolean notificationEnabled) {
		this.notificationEnabled = notificationEnabled;
	}
	
}
