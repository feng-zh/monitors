package com.hp.it.perf.monitor.hub.jmx;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

class JmxHubConnectorAgent implements NotificationListener, Runnable,
		InvocationHandler, NotificationEmitter {

	private static class NotificationListenerRecorder {

		private static class ListenerInfo {
			NotificationFilter filter;
			Object handback;
			NotificationListener listener;
			boolean wildcard = false;

			ListenerInfo(NotificationListener listener) {
				this.listener = listener;
				this.wildcard = true;
			}

			ListenerInfo(NotificationListener listener,
					NotificationFilter filter, Object handback) {
				this.listener = listener;
				this.filter = filter;
				this.handback = handback;
			}

			public boolean equals(Object o) {
				if (!(o instanceof ListenerInfo))
					return false;
				ListenerInfo li = (ListenerInfo) o;
				if (li.wildcard)
					return (li.listener == listener);
				else
					return (li.listener == listener && li.filter == filter && li.handback == handback);
			}
		}

		private List<ListenerInfo> listenerList = new CopyOnWriteArrayList<ListenerInfo>();

		public void addNotificationListener(NotificationListener listener,
				NotificationFilter filter, Object handback) {
			listenerList.add(new ListenerInfo(listener, filter, handback));
		}

		void addNotificationListeners(JMXConnector connector, ObjectName name) {
			for (ListenerInfo li : listenerList) {
				try {
					connector.getMBeanServerConnection()
							.addNotificationListener(name, li.listener,
									li.filter, li.handback);
				} catch (InstanceNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		public boolean hasListeneres() {
			return listenerList.isEmpty();
		}

		public void removeNotificationListener(NotificationListener listener)
				throws ListenerNotFoundException {
			ListenerInfo wildcard = new ListenerInfo(listener);
			listenerList.removeAll(Collections.singleton(wildcard));
		}

		public void removeNotificationListener(NotificationListener listener,
				NotificationFilter filter, Object handback)
				throws ListenerNotFoundException {
			ListenerInfo li = new ListenerInfo(listener, filter, handback);
			listenerList.remove(li);
		}
	}

	private NotificationBroadcasterSupport broadcaster = new NotificationBroadcasterSupport();

	private Map<String, ?> enviornment;

	private IOException ioe;

	private JMXConnector jmxConnector;

	private JMXServiceURL jmxServiceURL;

	private MBeanServerConnection mbeanServer;

	private Map<ObjectName, NotificationListenerRecorder> notificationListenerRecorders = new ConcurrentHashMap<ObjectName, NotificationListenerRecorder>();

	private ScheduledFuture<?> scheduledFuture;

	private ScheduledExecutorService scheduler;

	public JmxHubConnectorAgent(JMXServiceURL jmxServiceURL,
			Map<String, ?> environment) {
		this.jmxServiceURL = jmxServiceURL;
		this.enviornment = environment;
	}

	@Override
	public void addNotificationListener(NotificationListener listener,
			NotificationFilter filter, Object handback)
			throws IllegalArgumentException {
		broadcaster.addNotificationListener(listener, filter, handback);
	}

	private JMXConnector connect() throws IOException {
		JMXConnector connector = jmxConnector;
		if (connector == null) {
			connector = JMXConnectorFactory.newJMXConnector(jmxServiceURL,
					enviornment);
			connector.addConnectionNotificationListener(this, null, connector);
			jmxConnector = connector;
		}
		connector.connect();
		return connector;
	}

	public MBeanServerConnection getMBeanServerConnection() {
		try {
			this.mbeanServer = connect().getMBeanServerConnection();
		} catch (IOException e) {
			ioe = e;
		}
		return (MBeanServerConnection) Proxy.newProxyInstance(getClass()
				.getClassLoader(),
				new Class<?>[] { MBeanServerConnection.class }, this);
	}

	@Override
	public MBeanNotificationInfo[] getNotificationInfo() {
		return broadcaster.getNotificationInfo();
	}

	@Override
	public void handleNotification(Notification notification, Object handback) {
		if (JMXConnectionNotification.NOTIFS_LOST
				.equals(notification.getType())) {
			onNotificationLost((JMXConnectionNotification) notification);
		} else if (JMXConnectionNotification.CLOSED.equals(notification
				.getType())) {
			onNotificationClosed((JMXConnectionNotification) notification);
		} else if (JMXConnectionNotification.OPENED.equals(notification
				.getType())) {
			onNotificationOpened((JMXConnectionNotification) notification,
					(JMXConnector) handback);
		} else if (JMXConnectionNotification.FAILED.equals(notification
				.getType())) {
			onNotificationFailed((JMXConnectionNotification) notification);
		}
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {
		if (mbeanServer == null) {
			throw ioe;
		}
		try {
			Object result = method.invoke(mbeanServer, args);
			// handle add/remove notification listener
			recordNotificationListeners(proxy, method, args);
			return result;
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	protected void onNotificationClosed(JMXConnectionNotification notification) {
		// System.out.println("closed");
		broadcaster.sendNotification(notification);
		jmxConnector = null;
		startScheduler();
	}

	protected void onNotificationFailed(JMXConnectionNotification notification) {
		// System.out.println("failed");
		broadcaster.sendNotification(notification);
	}

	protected void onNotificationLost(JMXConnectionNotification notification) {
		// System.out.println("lost");
		broadcaster.sendNotification(notification);
	}

	protected void onNotificationOpened(JMXConnectionNotification notification,
			JMXConnector connector) {
		// System.out.println("open");
		stopScheduler();
		recoverNotificationListeners(connector);
		broadcaster.sendNotification(notification);
	}

	// only support notification listeners (not object name based notification)
	private void recordNotificationListeners(Object proxy, Method method,
			Object[] args) throws Exception {
		if ("addNotificationListener".equals(method.getName())
				&& method.getParameterTypes()[1] == NotificationListener.class) {
			// first argument: objectName
			// second argument: listener
			// third argument: filter
			// forth argument: handback
			NotificationListenerRecorder notificationRecorder = notificationListenerRecorders
					.get(args[0]);
			if (notificationRecorder == null) {
				notificationRecorder = new NotificationListenerRecorder();
				notificationListenerRecorders.put((ObjectName) args[0],
						notificationRecorder);
			}
			notificationRecorder.addNotificationListener(
					(NotificationListener) args[1],
					(NotificationFilter) args[2], args[3]);
		} else if ("removeNotificationListener".equals(method.getName())
				&& method.getParameterTypes()[1] == NotificationListener.class) {
			// first argument: objectName
			// second argument: listener
			// (or) third argument: filter
			// (or) forth argument: handback
			NotificationListenerRecorder notificationRecorder = notificationListenerRecorders
					.get(args[0]);
			if (notificationRecorder == null) {
				return;
			}
			switch ((args == null) ? 0 : args.length) {
			case 2:
				notificationRecorder
						.removeNotificationListener((NotificationListener) args[1]);
			case 4:
				notificationRecorder.removeNotificationListener(
						(NotificationListener) args[1],
						(NotificationFilter) args[2], args[3]);
			}
			if (!notificationRecorder.hasListeneres()) {
				notificationListenerRecorders.remove(args[0]);
			}
		}
	}

	private void recoverNotificationListeners(JMXConnector connector) {
		for (Entry<ObjectName, NotificationListenerRecorder> entry : notificationListenerRecorders
				.entrySet()) {
			entry.getValue()
					.addNotificationListeners(connector, entry.getKey());
		}
	}

	@Override
	public void removeNotificationListener(NotificationListener listener)
			throws ListenerNotFoundException {
		broadcaster.removeNotificationListener(listener);
	}

	@Override
	public void removeNotificationListener(NotificationListener listener,
			NotificationFilter filter, Object handback)
			throws ListenerNotFoundException {
		broadcaster.removeNotificationListener(listener, filter, handback);
	}

	@Override
	public void run() {
		try {
			connect();
		} catch (IOException e) {
			// TODO
			// System.err.println("connect fail: " + e);
		}
	}

	private void startScheduler() {
		scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduledFuture = scheduler.scheduleAtFixedRate(this, 1, 5,
				TimeUnit.SECONDS);
	}

	private void stopScheduler() {
		scheduledFuture.cancel(false);
		scheduler.shutdown();
	}

}
