package com.hp.it.perf.monitor.files.hub;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Pattern;

import javax.management.JMException;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.naming.Context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.ContentLine;
import com.hp.it.perf.monitor.files.ContentLineSourceObserver;
import com.hp.it.perf.monitor.files.FileInstance;
import com.hp.it.perf.monitor.files.FileInstanceFactory;
import com.hp.it.perf.monitor.files.FileMetadata;
import com.hp.it.perf.monitor.files.FileOpenOptionBuilder;
import com.hp.it.perf.monitor.files.FileSet;
import com.hp.it.perf.monitor.files.FilenameFilter;
import com.hp.it.perf.monitor.files.SuperSetContentLineStream;
import com.hp.it.perf.monitor.files.nio.MonitorFileFactory;
import com.hp.it.perf.monitor.hub.GatewayPayload;
import com.hp.it.perf.monitor.hub.GatewayStatus;
import com.hp.it.perf.monitor.hub.HubPublisher;
import com.hp.it.perf.monitor.hub.MonitorEndpoint;
import com.hp.it.perf.monitor.hub.internal.InternalMonitorHub;
import com.hp.it.perf.monitor.hub.jmx.HubJMX;
import com.hp.it.perf.monitor.hub.jmx.MonitorHubService;

public class FilesHubMain implements ContentLineSourceObserver {

	private MonitorEndpoint endpoint;
	private FileInstanceFactory fileFactory;
	private SuperSetContentLineStream superSetStream;
	private HubPublisher publisher;
	private JMXConnectorServer connectorServer;
	private InternalMonitorHub coreHub;

	private static Logger log = LoggerFactory.getLogger(FilesHubMain.class);

	public FilesHubMain(String domain, String name, String includePattern) {
		this.endpoint = new MonitorEndpoint(domain, name);
		this.fileFactory = new MonitorFileFactory();
		if (includePattern != null) {
			final Pattern pattern = Pattern.compile(includePattern);
			log.info("Use include pattern: {}", pattern.pattern());
			this.fileFactory.setGlobalFilenameFilter(new FilenameFilter() {

				@Override
				public boolean accept(String name) {
					return pattern.matcher(name).matches();
				}
			});
		}
		this.superSetStream = new SuperSetContentLineStream(
				new FileOpenOptionBuilder().lazyMode().tailMode().build());
		this.superSetStream.setSourceObserver(this);
	}

	public void startPublish() throws JMException {
		coreHub = new InternalMonitorHub();
		publisher = coreHub.createPublisher(endpoint, null);
		setupJmxHub(coreHub);
	}

	private void setupJmxHub(InternalMonitorHub coreHub) throws JMException {
		MonitorHubService jmxHub = new MonitorHubService(coreHub);
		jmxHub.setNotificationCompressDefault(true);
		jmxHub.setNotificationOpenTypeDefault(true);
		ManagementFactory.getPlatformMBeanServer().registerMBean(jmxHub,
				HubJMX.getHubObjectName());
	}

	public void setupJMXConnectorServer() throws IOException {
		String theHost = InetAddress.getLocalHost().getHostName();
		int port = Integer.getInteger("monitor.rmi.port", 12099);
		try {
			LocateRegistry.createRegistry(port);
		} catch (Exception e) {
		}
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
					.println("ERROR: Need 'domain', and 'name' as first two arguements, like \"'hpsc' 'production'\".");
			return;
		}
		FilesHubMain hubMain = new FilesHubMain(args[0], args[1],
				System.getProperty("monitor.file.include"));
		try {
			if (args.length == 2) {
				args = new String[] { args[0], args[1], "." };
			}
			hubMain.setupJMXConnectorServer();
			hubMain.startPublish();
			hubMain.startDone();
			boolean success = false;
			for (int i = 2; i < args.length; i++) {
				String fileName = args[i];
				try {
					hubMain.addMonitorFolder(fileName);
					success = true;
				} catch (FileNotFoundException e) {
					log.error("File not found: " + fileName);
				} catch (IOException e) {
					log.error("Add folder error", e);
				}
			}
			if (!success) {
				log.error("No folder added, exit!");
				return;
			}
			FileInstance lastFile = null;
			String filePath = null;
			int lastLineCount = 0;
			ContentLine line = null;
			while (true) {
				try {
					line = hubMain.readLine();
				} catch (IOException e) {
					log.error("ERROR: Read Line get " + e);
					log.debug("caused by:", e);
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
						log.info("{}/{}: {}", lastFile.getFileSet().getPath(),
								lastFile.getName(), lastLineCount);
					}
					lastLineCount = 0;
					lastFile = fileInstance;
					filePath = getFilePath(fileInstance);
				}
				lastLineCount++;
				hubMain.publish(line, filePath);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			hubMain.close();
		}
	}

	protected static String getFilePath(FileInstance fileInstance) {
		String filePath;
		filePath = (String) fileInstance.getClientProperty("FILE_PATH");
		if (filePath == null) {
			FileMetadata metadata = fileInstance.getMetadata(false);
			filePath = metadata.getRealPath();
			fileInstance.putClientProperty("FILE_PATH", filePath);
		}
		return filePath;
	}

	public void startDone() throws IOException {
		connectorServer.start();
		log.info("==> Target JMX Service URL is {}",
				connectorServer.getAddress());
	}

	public void publish(ContentLine line, String source) {
		GatewayPayload payload = new GatewayPayload();
		payload.setContentId(line.getPosition());
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
				// TODO
				e.printStackTrace();
			}
		}
	}

	@Override
	public void sourceFileCreated(FileInstance file, Object provider) {
		GatewayStatus status = new GatewayStatus();
		status.setStatus(0); // created
		status.setContext(getFilePath(file));
		publisher.update(status);
		log.info("Creating: {}", status.getContext());
	}

	@Override
	public void sourceFileDeleted(FileInstance file, Object provider) {
		GatewayStatus status = new GatewayStatus();
		status.setStatus(1); // deleted
		status.setContext(getFilePath(file));
		publisher.update(status);
		log.info("Deleted: {}", status.getContext());
	}

}
