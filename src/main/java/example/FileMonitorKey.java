package example;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.TimeUnit;

public interface FileMonitorKey extends Closeable {

	public void addMonitorListener(FileMonitorListener listener);

	public void removeMonitorListener(FileMonitorListener listener);

	public File getMonitorFile();

	public FileMonitorEvent await() throws InterruptedException;

	public FileMonitorEvent await(long timeout, TimeUnit unit)
			throws InterruptedException;

	public void close();
}
