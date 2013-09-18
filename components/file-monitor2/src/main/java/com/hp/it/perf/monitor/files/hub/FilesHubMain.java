package com.hp.it.perf.monitor.files.hub;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.util.Hashtable;
import java.util.Map;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.naming.Context;

import com.hp.it.perf.monitor.files.ContentLine;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceFactory;
import com.hp.it.perf.monitor.files.FileOpenOptionBuilder;
import com.hp.it.perf.monitor.files.FileSet;
import com.hp.it.perf.monitor.files.SuperSetContentLineStream;
import com.hp.it.perf.monitor.files.nio.MonitorFileFactory;
import com.hp.it.perf.monitor.hub.GatewayPayload;
import com.hp.it.perf.monitor.hub.HubPublisher;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.internal.InternalMonitorHub;
import com.hp.it.perf.monitor.hub.jmx.MonitorHubService;

public class FilesHubMain {

	private MonitorEndpoint endpoint;
	private FileInstanceFactory fileFactory;
	private SuperSetContentLineStream superSetStream;
	private HubPublisher publisher;
	private JMXConnectorServer connectorServer;

	public FilesHubMain(String domain, String name) {
		this.endpoint = new MonitorEndpoint(domain, name);
		this.fileFactory = new MonitorFileFactory();
		this.superSetStream = new SuperSetContentLineStream(
				new FileOpenOptionBuilder().lazyMode().tailMode().build());
	}

	public void startPublish() throws JMException {
		InternalMonitorHub coreHub = new InternalMonitorHub();
		publisher = coreHub.createPublisher(endpoint, null);
		setupJmxHub(coreHub);
	}

	private void setupJmxHub(InternalMonitorHub coreHub) throws JMException {
		MonitorHubService jmxHub = new MonitorHubService(coreHub);
		ManagementFactory.getPlatformMBeanServer().registerMBean(
				jmxHub,
				ObjectName.getInstance("com.hp.it.perf.monitor.hub", "type",
						"MonitorHubService"));
	}

	public void setupJMXConnectorServer() throws IOException {
		String theHost = InetAddress.getLocalHost().getHostName();
		int port = Integer.getInteger("monitor.rmi.port", 12099);
		LocateRegistry.createRegistry(port);
		String theLocation = System.getProperty("monitor.jmx.location",
				"filemonitor");
		String serviceURL = "service:jmx:rmi:///jndi/rmi://" + theHost + ":"
				+ port + "/" + theLocation;
		Map<String, String> environment = new Hashtable<String, String>();
		environment.put(Context.INITIAL_CONTEXT_FACTORY,
				"com.sun.jndi.rmi.registry.RegistryContextFactory");
		environment.put(RMIConnectorServer.JNDI_REBIND_ATTRIBUTE, "true");
		connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(
				new JMXServiceURL(serviceURL), environment,
				ManagementFactory.getPlatformMBeanServer());
		connectorServer.start();
		System.out.println("==> Target JMX Service URL is " + serviceURL);
	}

	public void addMonitorFolder(String folder) throws FileNotFoundException,
			IOException {
		FileSet fileSet = fileFactory.getFileSet(folder);
		superSetStream.addFileSet(fileSet);
	}

	/**
	 * @param args
	 */
	// TODO support non-monitor mode
	// TODO support configurable idle timeout
	// TODO support configurable lazy open
	public static void main(String[] args) {
		if (args.length < 2) {
			System.err
					.println("Need 'domain', and 'name' as first two arguements, like \"'hpsc' 'production'\".");
		}
		FilesHubMain hubMain = new FilesHubMain(args[0], args[1]);
		try {
			if (args.length == 2) {
				args = new String[] { args[0], args[1], "." };
			}
			hubMain.setupJMXConnectorServer();
			boolean success = false;
			for (int i = 2; i < args.length; i++) {
				// TODO file name filter
				String fileName = args[i];
				try {
					hubMain.addMonitorFolder(fileName);
					success = true;
				} catch (FileNotFoundException e) {
					System.err.println("File not found: " + fileName);
				} catch (IOException e) {
					System.err.println("Add folder error: " + e);
					e.printStackTrace();
				}
			}
			if (!success) {
				System.err.println("No folder added, exit!");
				return;
			}
			hubMain.startPublish();
			FileInstance lastFile = null;
			String fileName = null;
			int lastLineCount = 0;
			ContentLine line = null;
			while (true) {
				try {
					line = hubMain.readLine();
				} catch (IOException e) {
					System.err.println("ERROR: Read Line get " + e);
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
				if (line == null) {
					break;
				}
				FileInstance fileInstance = line.getFileInstance();
				if (!fileInstance.equals(lastFile)) {
					if (lastFile != null) {
						// end last file
						// TODO full file name
						System.out.print(lastFile.getFileSet().getPath());
						System.out.print("/");
						System.out.print(lastFile.getName());
						System.out.print(": ");
						System.out.println(lastLineCount);
					}
					lastLineCount = 0;
					lastFile = fileInstance;
					fileName = fileInstance.getFileSet().getPath() + "/"
							+ lastFile.getName();
				}
				lastLineCount++;
				hubMain.publish(line, fileName);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			hubMain.close();
		}
	}

	public void publish(ContentLine line, String source) {
		GatewayPayload payload = new GatewayPayload();
		payload.setContent(line.getLine());
		payload.setContentSource(source);
		// TODO how to use this content type in hub
		payload.setContentType(1);
		publisher.post(payload);
	}

	public ContentLine readLine() throws IOException, InterruptedException {
		return superSetStream.take();
	}

	public void close() {
		if (connectorServer != null) {
			try {
				connectorServer.stop();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		close(publisher);
		close(superSetStream);
		close(fileFactory);
	}

	private void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
