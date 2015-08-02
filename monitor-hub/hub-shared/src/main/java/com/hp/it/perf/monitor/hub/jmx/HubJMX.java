package com.hp.it.perf.monitor.hub.jmx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;

import com.hp.it.perf.monitor.hub.GatewayStatus;
import com.hp.it.perf.monitor.hub.HubEvent;
import com.hp.it.perf.monitor.hub.HubEvent.HubStatus;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.MonitorEvent;
import com.hp.it.perf.monitor.hub.MonitorHub;

public class HubJMX {

	public static ObjectName createEndpointObjectName(ObjectName hubObjectName,
			MonitorEndpoint me) {
		Hashtable<String, String> prop = new Hashtable<String, String>();
		prop.put("domain", me.getDomain());
		prop.put("name", me.getName());
		ObjectName endpointName;
		try {
			endpointName = ObjectName.getInstance(hubObjectName.getDomain(),
					prop);
		} catch (MalformedObjectNameException e) {
			throw new IllegalArgumentException("invalid endpoint name: " + me,
					e);
		}
		return endpointName;
	}

	public static Notification compressNotification(Notification notification) {
		Object userData = notification.getUserData();
		if (userData instanceof List) {
			@SuppressWarnings("unchecked")
			List<Notification> list = (List<Notification>) notification
					.getUserData();
			MonitorHubContentData data = new MonitorHubContentData();
			data.setDataType((byte) 0);
			data.setSource(null);
			// lines
			int count = list.size();
			data.setId(count);
			byte[] compressedData = compressData(list);
			data.setContent(compressedData);
			Notification newNotification = new Notification(
					MonitorHubEndpointServiceMXBean.NOTIFICATION_COMPRESSED_EVENT,
					notification.getSource(), notification.getSequenceNumber(),
					notification.getTimeStamp(), "Compressed count - " + count);
			newNotification.setUserData(data);
			return newNotification;
		} else {
			return notification;
		}
	}

	private static byte[] compressData(List<Notification> list) {
		ByteArrayOutputStream baOut = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
				new DeflaterOutputStream(baOut), 512));
		try {
			Map<String, Integer> notificationTypes = new LinkedHashMap<String, Integer>();
			Map<String, Integer> sourceIds = new LinkedHashMap<String, Integer>();
			for (Notification notification : list) {
				if (!notificationTypes.containsKey(notification.getType())) {
					notificationTypes.put(notification.getType(),
							notificationTypes.size());
				}
				MonitorHubContentData data = (MonitorHubContentData) notification
						.getUserData();
				if (!sourceIds.containsKey(data.getSource())) {
					sourceIds.put(data.getSource(), sourceIds.size());
				}
			}
			// section: notification type
			out.writeByte(notificationTypes.size());
			// first is position at 0
			for (String notificationType : notificationTypes.keySet()) {
				out.writeUTF(notificationType);
			}
			// section: source name index list
			out.writeInt(sourceIds.size());
			// first is position at 0
			for (String source : sourceIds.keySet()) {
				out.writeUTF(source);
			}
			// section: data content
			out.writeInt(list.size());
			for (Notification notification : list) {
				MonitorHubContentData data = (MonitorHubContentData) notification
						.getUserData();
				out.writeByte(notificationTypes.get(notification.getType()));
				out.writeByte(data.getDataType());
				out.writeLong(notification.getTimeStamp());
				out.writeLong(data.getId());
				out.writeInt(data.getType());
				out.writeInt(sourceIds.get(data.getSource()));
				out.writeInt(data.getContent().length);
				out.write(data.getContent());
			}
			// secton: end sign
			out.writeInt(-1);
			out.close();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return baOut.toByteArray();
	}

	public static Notification decompressNotification(Notification notification) {
		Object userData = notification.getUserData();
		if (userData instanceof MonitorHubContentData) {
			MonitorHubContentData data = (MonitorHubContentData) userData;
			// compressed flag
			if (MonitorHubEndpointServiceMXBean.NOTIFICATION_COMPRESSED_EVENT
					.equals(notification.getType())) {
				List<Notification> list = decompressData(data.getContent());
				Notification newNotification = new Notification(
						notification.getType(), notification.getSource(),
						notification.getSequenceNumber(),
						notification.getTimeStamp(), "Decompressed count - "
								+ data.getId());
				newNotification.setUserData(list);
				return newNotification;
			}
		}
		return notification;
	}

	private static List<Notification> decompressData(byte[] bytes) {
		try {
			DataInputStream input = new DataInputStream(
					new BufferedInputStream(new InflaterInputStream(
							new ByteArrayInputStream(bytes))));
			// section: notification type
			byte notificationTypeCount = input.readByte();
			String[] notificationTypes = new String[notificationTypeCount];
			for (int i = 0; i < notificationTypeCount; i++) {
				notificationTypes[i] = input.readUTF();
			}
			// section: source name index list
			int sourceCount = input.readInt();
			String[] sourceIds = new String[sourceCount];
			for (int i = 0; i < sourceCount; i++) {
				sourceIds[i] = input.readUTF();
			}
			// section: data content
			int size = input.readInt();
			List<Notification> list = new ArrayList<Notification>(size);
			for (int i = 0; i < size; i++) {
				MonitorHubContentData data = new MonitorHubContentData();
				String notificationType = notificationTypes[input.readByte()];
				data.setDataType(input.readByte());
				Notification notification = new Notification(notificationType,
						"", 0, input.readLong());
				data.setId(input.readLong());
				data.setType(input.readInt());
				data.setSource(sourceIds[input.readInt()]);
				int len = input.readInt();
				byte[] dataBytes = new byte[len];
				input.readFully(dataBytes);
				data.setContent(dataBytes);
				notification.setUserData(data);
				list.add(notification);
			}
			// section: read end sign
			input.readInt();
			input.close();
			return list;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	public static interface NotificationInfoSerializerInterface {
		public MonitorHubContentData getMonitorHubContentData();

		public void setMonitorHubContentData(MonitorHubContentData info);
	}

	public static class NotificationInfoSerializer extends StandardMBean
			implements NotificationInfoSerializerInterface {

		private MonitorHubContentData MonitorHubContentData;

		private static NotificationInfoSerializer me = new NotificationInfoSerializer();

		public NotificationInfoSerializer() {
			super(NotificationInfoSerializerInterface.class, true);
		}

		public MonitorHubContentData getMonitorHubContentData() {
			return MonitorHubContentData;
		}

		public void setMonitorHubContentData(MonitorHubContentData info) {
			this.MonitorHubContentData = info;
		}

		public static Object serializeMonitorHubContentData(
				MonitorHubContentData info) {
			try {
				me.setMonitorHubContentData(info);
				return me.getAttribute("MonitorHubContentData");
			} catch (Exception ex) {
				throw new RuntimeException("Unexpected exception", ex);
			}
		}
	}

	public static void serializeNotification(Notification notification) {
		MonitorHubContentData info = (MonitorHubContentData) notification
				.getUserData();
		notification.setUserData(NotificationInfoSerializer
				.serializeMonitorHubContentData(info));
	}

	public static void deserializeNotification(Notification notification) {
		Object userData = notification.getUserData();
		if (userData instanceof CompositeData) {
			notification
					.setUserData(fromCompositeData((CompositeData) userData));
		}
	}

	private static MonitorHubContentData fromCompositeData(
			CompositeData userData) {
		MonitorHubContentData data = new MonitorHubContentData();
		data.setDataType((Byte) userData.get("dataType"));
		data.setContent((byte[]) userData.get("content"));
		data.setId((Long) userData.get("id"));
		data.setSource((String) userData.get("source"));
		data.setType((Integer) userData.get("type"));
		return data;
	}

	public static ObjectName getHubObjectName() {
		try {
			return ObjectName.getInstance("com.hp.it.perf.monitor.hub", "type",
					"MonitorHubService");
		} catch (MalformedObjectNameException e) {
			throw new RuntimeException(e);
		}
	}

	public static void setMonitorEventContent(MonitorHubContentData data,
			MonitorEvent event) {
		Object content = event.getContent();
		if (content instanceof byte[]) {
			// 0 - byte[]
			data.setDataType((byte) 0);
			data.setContent((byte[]) content);
		} else if (content == null) {
			// 1 - null value
			data.setDataType((byte) 1);
			data.setContent(new byte[0]);
		} else {
			// 2 - java serialized
			data.setDataType((byte) 2);
			data.setContent(toByteArray(content));
		}
		data.setId(event.getContentId());
		data.setSource(event.getContentSource());
		data.setType(event.getContentType());
	}

	private static byte[] toByteArray(Object content) {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		ObjectOutputStream oos = null;
		try {
			oos = new ObjectOutputStream(bao);
			oos.writeObject(content);
			oos.close();
			return bao.toByteArray();
		} catch (ObjectStreamException e) {
			throw new IllegalArgumentException("java serilization error", e);
		} catch (IOException e) {
			throw new RuntimeException("unexpected", e);
		}
	}

	public static void setHubEventContent(MonitorHubContentData data,
			HubEvent event) {
		Object content = event.getData();
		data.setId(0);
		if (content instanceof byte[]) {
			// 0 - byte[]
			data.setDataType((byte) 0);
			data.setContent((byte[]) content);
		} else if (content == null) {
			// 1 - null value
			data.setDataType((byte) 1);
			data.setContent(new byte[0]);
		} else if (content instanceof String) {
			// 3 - string
			data.setDataType((byte) 3);
			data.setContent(toUTF8Bytes((String) content));
		} else if (content instanceof GatewayStatus) {
			// 4 - Gateway Status
			data.setDataType((byte) 4);
			GatewayStatus status = (GatewayStatus) content;
			Object context = status.getContext();
			byte[] contentByteArray;
			int contextDataType;
			if (context == null) {
				contextDataType = 1;
				contentByteArray = new byte[0];
			} else if (context instanceof byte[]) {
				contextDataType = 0;
				contentByteArray = (byte[]) context;
			} else if (context instanceof String) {
				contextDataType = 3;
				contentByteArray = toUTF8Bytes((String) context);
			} else {
				contextDataType = 2;
				contentByteArray = toByteArray(context);
			}
			// high 32 bits: status
			// low 32 bits: data type
			data.setId(((long) status.getStatus() << 32)
					| ((long) contextDataType));
			status.getContext();
			data.setContent(contentByteArray);
		} else {
			// 2 - java serialize
			data.setDataType((byte) 2);
			data.setContent(toByteArray(content));
		}
		data.setSource(event.getEndpoint() == null ? "" : event.getEndpoint()
				.toString());
		data.setType(event.getStatus().ordinal());
	}

	private static byte[] toUTF8Bytes(String data) {
		try {
			return data.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("unexpected", e);
		}
	}

	public static void getMonitorEventContent(MonitorEvent event,
			MonitorHubContentData data) {
		byte dataType = data.getDataType();
		switch (dataType) {
		case 0:
			event.setContent(data.getContent());
			break;
		case 1:
			event.setContent(null);
			break;
		case 2:
			event.setContent(fromByteArray(data.getContent()));
			break;
		default:
			System.out.println("UNKWNON data type: " + (0xFF & dataType));
			event.setContent(data.getContent());
			break;
		}
		event.setContentId(data.getId());
		event.setContentSource(data.getSource());
		event.setContentType(data.getType());
	}

	private static Object fromByteArray(byte[] content) {
		ByteArrayInputStream bai = new ByteArrayInputStream(content);
		ObjectInputStream oos = null;
		try {
			oos = new ObjectInputStream(bai);
			Object data = oos.readObject();
			oos.close();
			return data;
		} catch (ObjectStreamException e) {
			// TODO
			throw new IllegalArgumentException("java deserialization error", e);
		} catch (IOException e) {
			throw new RuntimeException("unexpected", e);
		} catch (ClassNotFoundException e) {
			throw new NoClassDefFoundError(e.getMessage());
		}
	}

	public static HubEvent getHubEventContent(MonitorHub monitorHub,
			MonitorHubContentData data) {
		MonitorEndpoint endpoint;
		if (!"".equals(data.getSource())) {
			endpoint = MonitorEndpoint.valueOf(data.getSource());
		} else {
			endpoint = null;
		}
		byte[] content = data.getContent();
		byte dataType = data.getDataType();
		Object hubData;
		HubStatus hubStatus = HubStatus.values()[data.getType()];
		long dataId = data.getId();
		int contextDataType = (int) (dataId & 0xFFFFFFFF);
		GatewayStatus gatewayStatus = null;
		if (dataType == 4) {
			gatewayStatus = new GatewayStatus();
			gatewayStatus.setStatus((int) (dataId >>> 32));
			dataType = (byte) contextDataType;
		}
		switch (dataType) {
		case 0:
			hubData = content;
			break;
		case 1:
			hubData = null;
			break;
		case 2:
			hubData = fromByteArray(content);
			break;
		case 3:
			hubData = fromUTFBytes(content);
			break;
		default:
			throw new IllegalArgumentException("unkwnown type: " + dataType);
		}
		if (gatewayStatus != null) {
			gatewayStatus.setContext(hubData);
			hubData = gatewayStatus;
		}
		return new HubEvent(monitorHub, hubStatus, endpoint, hubData);
	}

	private static String fromUTFBytes(byte[] content) {
		try {
			return new String(content, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO
			throw new RuntimeException("unexpected", e);
		}
	}

}
