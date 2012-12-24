package com.hp.it.perf.monitor.filemonitor;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FileTeseBuilder implements Closeable {

	private static final Logger log = LoggerFactory
			.getLogger(FileTeseBuilder.class);

	private static final String NEW_LINE = System.getProperty("line.separator");

	private File targetRootFolder;

	private Timer timer;

	public FileTeseBuilder(String root) {
		targetRootFolder = new File("target/test-data/" + root);
		targetRootFolder.deleteOnExit();
		targetRootFolder.mkdirs();
	}

	public File copy(File sourceFile, String name) throws IOException {
		File targetFile = new File(targetRootFolder, name);
		copyFile(sourceFile, targetFile);
		return targetFile;
	}

	public void copyFile(File sourceFile, File targetFile) throws IOException {
		targetFile.deleteOnExit();
		FileChannel sourceChannel = null;
		FileChannel targetChannel = null;
		try {
			sourceChannel = new FileInputStream(sourceFile).getChannel();
			targetChannel = new FileOutputStream(targetFile).getChannel();
			sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);
		} finally {
			close(sourceChannel);
			close(targetChannel);
		}
		targetFile.setLastModified(sourceFile.lastModified());
	}

	private void close(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ignored) {
			}
		}
	}

	public File copy(File sourceFile) throws IOException {
		return copy(sourceFile, sourceFile.getName());
	}

	@Override
	public void close() throws IOException {
		if (!targetRootFolder.exists()) {
			return;
		}
		deleteFile(targetRootFolder);
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
	}

	private void deleteFile(File dir) {
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				file.delete();
			} else {
				deleteFile(file);
			}
		}
		dir.delete();
	}

	public void echo(String line, File targetFile) throws IOException {
		RandomAccessFile access = null;
		try {
			access = new RandomAccessFile(targetFile, "rw");
			access.seek(access.length());
			byte[] bs = line(line);
			log.trace("echo line size of {} to file {}", bs.length, targetFile);
			access.write(bs);
			log.trace("echo line done.");
		} finally {
			close(access);
		}
	}
	
	public void print(String txt, File targetFile) throws IOException {
		RandomAccessFile access = null;
		try {
			access = new RandomAccessFile(targetFile, "rw");
			access.seek(access.length());
			byte[] bs = txt.getBytes();
			log.trace("print line size of {} to file {}", bs.length, targetFile);
			access.write(bs);
			log.trace("print line done.");
		} finally {
			close(access);
		}
	}

	public byte[] line(String data) {
		return (data + NEW_LINE).getBytes();
	}

	public void echoSync(final String line, final File targetFile, long time,
			TimeUnit unit) {
		Timer timer = getTimer();
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				try {
					echo(line, targetFile);
				} catch (IOException ignored) {
				}
			}
		}, unit.toMillis(time));
	}

	private Timer getTimer() {
		if (timer == null) {
			timer = new Timer("FileTestBuilderTimer", true);
		}
		return timer;
	}

	public void printThreads() {
		Map<Thread, StackTraceElement[]> allStackTraces = Thread
				.getAllStackTraces();
		for (Thread t : allStackTraces.keySet()) {
			if (t.getThreadGroup() != null) {
				if ((t.getThreadGroup().getName().equals("system"))
						|| t == Thread.currentThread()) {
					continue;
				}
				// ignore other junit threads
				if (t.getThreadGroup().getName().equals("main")
						&& t.getName().equals("ReaderThread")) {
					continue;
				}
				// ignore keep alive threads
				if (t.getThreadGroup().getName().equals("main")
						&& t.getName().equals("Reader keepalive thread")) {
					continue;
				}
				// ignore jacoco threads
				if (t.getThreadGroup().getName().equals("main")
						&& t.getName()
								.equals("org.jacoco.agent.rt_kqcpih.controller.TcpClientController")) {
					continue;
				}
			}
			if (t.getName().equals("FileTestBuilderTimer")) {
				continue;
			}
			System.out.println(t + (t.isDaemon() ? " daemon" : ""));
			for (StackTraceElement trace : allStackTraces.get(t)) {
				System.out.println("\tat " + trace.toString());
			}
			System.out.println();
		}
	}

	public String getRelativeFileName(File file) {
		Path rootPath = targetRootFolder.toPath();
		Path filePath = file.toPath();
		return rootPath.relativize(filePath).toString();
	}

	public File rename(File file, String newFileName) {
		File newFile = new File(file.getParentFile(), newFileName);
		if (newFile.exists()) {
			newFile.delete();
		}
		if (file.renameTo(newFile)) {
			log.trace("{} rename to {}", file.getName(), newFile.getName());
		} else {
			log.warn("{} cannot rename to {}", file.getName(),
					newFile.getName());
		}
		newFile.deleteOnExit();
		return newFile;
	}

	public void delete(File file) {
		if (file.delete()) {
			log.trace("Delete {} success", file);
		} else {
			log.warn("Cannot delete {}", file);
		}
	}

}
