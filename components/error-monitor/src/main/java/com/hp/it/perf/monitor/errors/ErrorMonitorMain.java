package com.hp.it.perf.monitor.errors;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.InflaterInputStream;

import javax.management.JMException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class ErrorMonitorMain implements NotificationListener {

	private Map<Long, ContentInfo> contents = new HashMap<Long, ContentInfo>();

	public static interface ContentProvider {

		public String[] getFileNames();

		public List<ContentInfo> getFileContentInfos(boolean realtime,
				boolean active) throws IOException;

		public void setCompressMode(boolean mode);

		public boolean isCompressMode();

		public void setNotificationEnabled(boolean enabled);

		public boolean isNotificationEnabled();

	}

	public static class ContentInfo {

		private String fileName;

		private long providerId;

		private String currentFileName;

		private String realPath;

		private long offset;

		// real-time information
		private long lastModified;

		private long length;

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public long getProviderId() {
			return providerId;
		}

		public void setProviderId(long providerId) {
			this.providerId = providerId;
		}

		public String getCurrentFileName() {
			return currentFileName;
		}

		public void setCurrentFileName(String currentFileName) {
			this.currentFileName = currentFileName;
		}

		public long getOffset() {
			return offset;
		}

		public void setOffset(long offset) {
			this.offset = offset;
		}

		public long getLastModified() {
			return lastModified;
		}

		public void setLastModified(long lastModified) {
			this.lastModified = lastModified;
		}

		public long getLength() {
			return length;
		}

		public void setLength(long length) {
			this.length = length;
		}

		public String getRealPath() {
			return realPath;
		}

		public void setRealPath(String realPath) {
			this.realPath = realPath;
		}

	}

	public static class LineRecord {

		private byte[] line;

		private int lineNum;

		private long providerId;

		public byte[] getLine() {
			return line;
		}

		public void setLine(byte[] line) {
			this.line = line;
		}

		public int getLineNum() {
			return lineNum;
		}

		public void setLineNum(int lineNum) {
			this.lineNum = lineNum;
		}

		public long getProviderId() {
			return providerId;
		}

		public void setProviderId(long providerId) {
			this.providerId = providerId;
		}

		public static LineRecord from(CompositeData userData) {
			LineRecord lineRecord = new LineRecord();
			lineRecord.setLine((byte[]) userData.get("line"));
			lineRecord.setLineNum((Integer) userData.get("lineNum"));
			lineRecord.setProviderId((Long) userData.get("providerId"));
			return lineRecord;
		}

	}

	private void refreshFiles(ContentProvider providers, long provId)
			throws IOException {
		List<ContentInfo> contentInfos;
		ContentInfo contInfo = contents.get(provId);
		if (contInfo == null) {
			contentInfos = providers.getFileContentInfos(false, true);
			for (ContentInfo content : contentInfos) {
				long providerId = content.getProviderId();
				contents.put(providerId, content);
			}
		}
	}

	public void monitor(JMXServiceURL monitorURL) throws IOException,
			InterruptedException, JMException {
		JMXConnector connector = JMXConnectorFactory.connect(monitorURL);
		MBeanServerConnection mbsc = connector.getMBeanServerConnection();
		System.out.println("===> Connected to monitor server: " + monitorURL);
		Set<ObjectName> names = mbsc
				.queryNames(
						ObjectName
								.getInstance("com.hp.it.perf.monitor.filemonitor:type=CompositeContentProvider,*"),
						null);
		if (names.isEmpty()) {
			throw new IOException("no monitor mbean found");
		}
		if (names.size() > 1) {
			throw new IOException("more monitor mbeans found: " + names);
		}
		ObjectName monitorObjName = names.iterator().next();
		ContentProvider contentProvider = JMX.newMXBeanProxy(mbsc,
				monitorObjName, ContentProvider.class, true);
		// contentProvider.setCompressMode(true);
		// contentProvider.setNotificationEnabled(true);
		((NotificationEmitter) contentProvider).addNotificationListener(this,
				null, contentProvider);
		synchronized (contentProvider) {
			contentProvider.wait();
		}
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

	@Override
	public void handleNotification(Notification notification, Object handback) {
		ContentProvider provider = (ContentProvider) handback;
		LineRecord lineRecord = LineRecord.from((CompositeData) notification
				.getUserData());
		List<LineRecord> lines;
		if (lineRecord.getProviderId() == -1) {
			// compress mode
			lines = decompressLines(lineRecord.getLine());
		} else {
			lines = Collections.singletonList(lineRecord);
		}
		for (LineRecord line : lines) {
			long providerId = line.getProviderId();
			ContentInfo contentInfo = contents.get(providerId);
			if (contentInfo == null) {
				try {
					refreshFiles(provider, providerId);
				} catch (IOException e) {
				}
			}
			String fileName;
			if (contentInfo != null) {
				fileName = contentInfo.getCurrentFileName();
			} else {
				fileName = "Unkown";
			}
			String newline;
			try {
				newline = new String(line.getLine(), "UTF-8");
			} catch (UnsupportedEncodingException ignored) {
				continue;
			}
			// trim last '\n'
			if (newline.endsWith("\n")) {
				newline = newline.substring(0, newline.length() - 1);
			}
			if (newline != null && newline.toLowerCase().contains("error")) {
				if (fileName.contains("sp4tsdiag")) {
					continue;
				}
				System.out.println(fileName + "[" + line.getLineNum() + "]: "
						+ newline);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		new ErrorMonitorMain()
				.monitor(new JMXServiceURL(
						"service:jmx:rmi:///jndi/rmi://d6t0009g.atlanta.hp.com:12099/filemonitor"));
	}

}
