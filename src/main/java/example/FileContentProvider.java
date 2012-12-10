package example;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public interface FileContentProvider extends Closeable {

	public LineRecord readLine() throws IOException, InterruptedException;

	public LineRecord readLine(long timeout, TimeUnit unit) throws IOException,
			InterruptedException, EOFException;

	public FileContentInfo getFileInfo() throws IOException;

	public void init() throws IOException;

	public void close() throws IOException;

}
