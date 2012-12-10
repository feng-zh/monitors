package example;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public interface FileMonitorService extends Closeable {

	public FileMonitorKey register(final File file,
			FileMonitorMode monitorMode, FileMonitorListener... listeners)
			throws IOException;

}
