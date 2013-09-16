package com.hp.it.perf.monitor.files.jmx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.StandardMBean;

import com.hp.it.perf.monitor.files.ContentLine;
import com.hp.it.perf.monitor.files.ContentLineStream;
import com.hp.it.perf.monitor.files.ContentLineStreamProvider;
import com.hp.it.perf.monitor.files.FileOpenOption;

public class ContentLineProvider extends NotificationBroadcasterSupport
		implements ContentLineProviderMXBean, NotificationEmitter {

	// TODO not finalized
	public static final String DOMAIN = "com.hp.it.perf.monitor.filemonitor";

	public static final String LINE_RECORD = DOMAIN + ".lineRecord";

	private static final int BUFFER_SIZE = Integer.getInteger(
			"monitor.content.notification.size", 1000);

	private static final int BUFFER_TIME = Integer.getInteger(
			"monitor.content.notification.time", 2000);

	private ContentLineStream lineStream;
	private int readLineCount;
	private long readByteCount;

	private boolean notificationEnabled;

	private boolean compressMode;

	private ContentLineBuffer lineBuffer;

	private AtomicLong seq = new AtomicLong();

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

	public static interface NotificationInfoSerializerInterface {
		public ContentLine getContentLine();

		public void setContentLine(ContentLine info);
	}

	public static class NotificationInfoSerializer extends StandardMBean
			implements NotificationInfoSerializerInterface {

		private ContentLine lineRecord;

		private static NotificationInfoSerializer me = new NotificationInfoSerializer();

		public NotificationInfoSerializer() {
			super(NotificationInfoSerializerInterface.class, true);
		}

		public ContentLine getContentLine() {
			return lineRecord;
		}

		public void setContentLine(ContentLine info) {
			this.lineRecord = info;
		}

		public static Object serializeContentLine(ContentLine info) {
			try {
				me.setContentLine(info);
				return me.getAttribute("ContentLine");
			} catch (Exception ex) {
				throw new RuntimeException("Unexpected exception", ex);
			}
		}
	}

	public ContentLineProvider(ContentLineStreamProvider stream,
			FileOpenOption option) throws IOException {
		lineStream = stream.open(option);
	}

	@Override
	public String[] getFileNames() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getReadLineCount() {
		return readLineCount;
	}

	@Override
	public long getReadByteCount() {
		return readByteCount;
	}

	@Override
	public void close() throws IOException {
		lineStream.close();
	}

	@Override
	public void setCompressMode(boolean mode) {
		this.compressMode = mode;
	}

	@Override
	public boolean isCompressMode() {
		return compressMode;
	}

	@Override
	public void setNotificationEnabled(boolean enabled) {
		this.notificationEnabled = enabled;
	}

	@Override
	public boolean isNotificationEnabled() {
		return notificationEnabled;
	}

	public ContentLine read() throws IOException, InterruptedException {
		return onLineRead(lineStream.take());
	}

	protected ContentLine onLineRead(ContentLine line) {
		if (notificationEnabled) {
			if (compressMode) {
				if (lineBuffer == null) {
					lineBuffer = new ContentLineBuffer(BUFFER_SIZE,
							BUFFER_TIME, new ContentLineBuffer.BufferHandler() {

								@Override
								public void handleBuffer(
										Queue<ContentLine> buffer) {
									Notification notification = new Notification(
											LINE_RECORD, this, seq
													.incrementAndGet(), System
													.currentTimeMillis());
									notification
											.setSource(ContentLineProvider.this);
									ArrayList<ContentLine> list = new ArrayList<ContentLine>(
											buffer.size());
									ContentLine line;
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

	@Override
	protected void handleNotification(NotificationListener listener,
			Notification notif, Object handback) {
		Notification notification;
		synchronized (this) {
			if (notif.getUserData() instanceof ContentLine) {
				ContentLine line = (ContentLine) notif.getUserData();
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
						.serializeContentLine(line));
				notif.setUserData(notification);
			} else if (notif.getUserData() instanceof List) {
				@SuppressWarnings("unchecked")
				List<ContentLine> lines = (List<ContentLine>) notif
						.getUserData();
				ContentLine line = new ContentLine();
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
						.serializeContentLine(line));
				notif.setUserData(notification);
			} else if (notif.getUserData() instanceof Notification) {
				notification = (Notification) notif.getUserData();
			} else {
				notification = notif;
			}
		}
		super.handleNotification(listener, notification, handback);
	}

	private byte[] compressLines(List<ContentLine> lines) {
		ByteArrayOutputStream baOut = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
				new DeflaterOutputStream(baOut), 512));
		try {
			out.writeInt(lines.size());
			Map<Long, String[]> providers = new HashMap<Long, String[]>();
			for (ContentLine line : lines) {
				out.writeLong(line.getProviderId());
				out.writeInt(0);
				out.writeInt(line.getLine().length);
				out.write(line.getLine());
				if (!providers.containsKey(line.getProviderId())) {
					providers.put(line.getProviderId(), line.getProvider());
				}
			}
			out.writeInt(providers.size());
			for (Entry<Long, String[]> entry : providers.entrySet()) {
				out.writeLong(entry.getKey());
				out.writeInt(entry.getValue().length);
				for (String name : entry.getValue()) {
					out.writeUTF(name);
				}
			}
			// end sign
			out.writeInt(-1);
			out.close();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return baOut.toByteArray();
	}

	public List<ContentLine> decompressLines(byte[] bytes) {
		try {
			DataInputStream input = new DataInputStream(
					new BufferedInputStream(new InflaterInputStream(
							new ByteArrayInputStream(bytes))));
			int size = input.readInt();
			List<ContentLine> lines = new ArrayList<ContentLine>(size);
			for (int i = 0; i < size; i++) {
				ContentLine line = new ContentLine();
				line.setProviderId(input.readLong());
				input.readInt();
				int lineLen = input.readInt();
				byte[] lineBytes = new byte[lineLen];
				input.readFully(lineBytes);
				line.setLine(lineBytes);
				lines.add(line);
			}
			int pSize = input.readInt();
			Map<Long, String[]> providerList = new HashMap<Long, String[]>(
					pSize);
			for (int i = 0; i < pSize; i++) {
				long providerId = input.readLong();
				int providerSize = input.readInt();
				String[] provider = new String[providerSize];
				for (int j = 0; j < providerSize; j++) {
					provider[j] = input.readUTF();
				}
				providerList.put(providerId, provider);
			}
			for (ContentLine line : lines) {
				line.setProvider(providerList.get(line.getProviderId()));
			}
			// read end sign
			input.readInt();
			input.close();
			return lines;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
