package com.hp.it.perf.monitor.filemonitor;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.EOFException;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.filemonitor.nio.MultiMonitorService;

public class UniqueFileTestCase {

	private static final Logger log = LoggerFactory
			.getLogger(UniqueFileTestCase.class);

	private FileMonitorService monitorService;
	private FileTeseBuilder setup;

	@Before
	public void setUp() throws Exception {
		log.info("[Start Test]");
		monitorService = new MultiMonitorService();
		setup = new FileTeseBuilder(UniqueFileTestCase.class.getName());
	}

	@After
	public void tearDown() throws Exception {
		monitorService.close();
		setup.close();
		setup.printThreads();
		log.info("[End Test]");
	}

	@Test(timeout = 2000)
	public void testUniqueFileRead() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		String data = "newline";
		setup.echo(data, testFile);
		LineRecord line = file.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line(data)));
		assertThat(line.getLineNum(), is(equalTo(1)));
		file.close();
	}

	@Test(timeout = 2000)
	public void testUniqueFileLazyRead() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.setLazyOpen(true);
		file.init();
		String data = "newline";
		setup.echo(data, testFile);
		LineRecord line = file.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line(data)));
		assertThat(line.getLineNum(), is(equalTo(1)));
		file.close();
	}

	@Test(timeout = 3000)
	public void testUniqueFileModifyReadTake() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		String data = "newline";
		setup.echoSync(data, testFile, 1, TimeUnit.SECONDS);
		LineRecord line = file.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line(data)));
		assertThat(line.getLineNum(), is(equalTo(1)));

		file.close();
	}

	@Test(timeout = 5000)
	public void testUniqueFileModifyReadPoll() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		{
			String data = "newline";
			setup.echo(data, testFile);
			long now = System.currentTimeMillis();
			LineRecord line = file.readLine(5, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(line, is(notNullValue()));
			assertThat(line.getLine(), is(setup.line(data)));
			assertThat(line.getLineNum(), is(equalTo(1)));
			assertThat(duration, is(lessThan(5L)));
		}
		// let file modified time change
		Thread.sleep(1000);
		{
			String data = "line2";
			setup.echoSync(data, testFile, 500, TimeUnit.MILLISECONDS);
			long now = System.currentTimeMillis();
			LineRecord line = file.readLine(3, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat("take " + duration + " get null", line,
					is(notNullValue()));
			assertThat(duration, is(greaterThan(500L)));
			assertThat(duration, is(lessThanOrEqualTo(3000L)));
			assertThat(line.getLine(), is(setup.line(data)));
			assertThat(line.getLineNum(), is(equalTo(2)));
		}
		{
			String data = "line3";
			setup.echoSync(data, testFile, 3, TimeUnit.SECONDS);
			long now = System.currentTimeMillis();
			LineRecord line = file.readLine(2, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(duration, is(greaterThanOrEqualTo(2000L)));
			assertThat(line, is(nullValue()));
		}
		file.close();
	}

	@Test
	public void testUniqueFileReadLines() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		setup.echo("line1", testFile);
		setup.echo("line2", testFile);
		Queue<LineRecord> list = new LinkedList<LineRecord>();
		int count = file.readLines(list, 3);
		assertThat(count, is(equalTo(2)));
		list.clear();
		setup.echo("line3", testFile);
		setup.echo("line4", testFile);
		count = file.readLines(list, 1);
		assertThat(count, is(equalTo(1)));
		count = file.readLines(list, 2);
		assertThat(count, is(equalTo(1)));
		count = file.readLines(list, 2);
		assertThat(count, is(equalTo(0)));
		file.close();
	}

	@Test
	public void testUniqueFileReadLinesQueueFull() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		setup.echo("line1", testFile);
		setup.echo("line2", testFile);
		setup.echo("line3", testFile);
		setup.echo("line4", testFile);
		Queue<LineRecord> list = new ArrayBlockingQueue<LineRecord>(2);
		int count = file.readLines(list, 3);
		assertThat(count, is(equalTo(FileContentProvider.QUEUE_FULL)));
		assertThat(list.poll().getLine(), is(setup.line("line1")));
		assertThat(list.poll().getLine(), is(setup.line("line2")));
		list.clear();
		count = file.readLines(list, 3);
		assertThat(count, is(equalTo(2)));
		assertThat(list.poll().getLine(), is(setup.line("line3")));
		assertThat(list.poll().getLine(), is(setup.line("line4")));
		file.close();
	}

	@Test
	public void testUniqueFileGetContent() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		String testFileName = testFile.toString();
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		String data = "newline";
		setup.echo(data, testFile);
		LineRecord line = file.readLine(1, TimeUnit.MILLISECONDS);
		assertThat(line, is(notNullValue()));
		List<FileContentInfo> contentInfos = file.getFileContentInfos(false);
		assertThat(contentInfos, is(notNullValue()));
		assertThat(contentInfos.size(), is(equalTo(1)));
		FileContentInfo info = contentInfos.get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFileName)));
		assertThat(info.getProviderId(), is(not(equalTo(0L))));
		assertThat(info.getProviderId(), is(equalTo(line.getProviderId())));
		// realtime info
		contentInfos = file.getFileContentInfos(true);
		assertThat(contentInfos, is(notNullValue()));
		assertThat(contentInfos.size(), is(equalTo(1)));
		info = contentInfos.get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFileName)));
		assertThat(info.getProviderId(), is(equalTo(line.getProviderId())));
		assertThat(info.getLastModified(), is(equalTo(testFile.lastModified())));
		assertThat(info.getLength(), is(equalTo(testFile.length())));
		assertThat(info.getOffset(), is(equalTo(testFile.length())));
		file.close();
	}

	@Test
	public void testUniqueFileReadLinesWithNoMonitor() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		setup.echo("line1", testFile);
		setup.echo("line2", testFile);
		Queue<LineRecord> list = new LinkedList<LineRecord>();
		int count = file.readLines(list, 3);
		assertThat(count, is(equalTo(2)));
		list.clear();
		count = file.readLines(list, 1);
		assertThat(count, is(equalTo(FileContentProvider.EOF)));
		file.close();
	}

	@Test
	public void testUniqueFileModifyReadTakeWithNoMonitor() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		setup.echo("line1", testFile);
		LineRecord line = file.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		line = file.readLine();
		assertThat(line, is(nullValue()));
		file.close();
	}

	@Test
	public void testUniqueFileModifyReadPollWithNoMonitor() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		try {
			file.readLine(1, TimeUnit.SECONDS);
			Assert.fail("not here");
		} catch (EOFException e) {
			assertThat(e, is(notNullValue()));
		}
		file.close();
	}

	@Test(timeout = 8000)
	public void testUniqueFileRename() throws Exception {
		if (System.getProperty("os.name").indexOf("Windows") != -1) {
			return;
		}
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		String testFileName = testFile.toString();
		file.setFile(testFile);
		file.setMonitorService(monitorService);
		file.setInitOffset(testFile.length());
		file.setIdleTimeout(1);
		file.init();
		setup.echo("line1", testFile);
		LineRecord line = file.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		FileContentInfo info = file.getFileContentInfos(false).get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFileName)));
		// try to close it to handle windows rename error
		Thread.sleep(1100L);
		File testFile2 = setup.rename(testFile, "sample_file2.txt");
		String testFile2Name = testFile2.toString();
		setup.echo("line2", testFile2);
		// force wait for file watch
		line = file.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		Thread.sleep(2000L);
		info = file.getFileContentInfos(false).get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFile2Name)));
		file.close();
	}

	@Test(timeout = 3000)
	public void testUniqueFileDelete() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		String testFileName = testFile.toString();
		file.setFile(testFile);
		file.setMonitorService(monitorService);
		file.setInitOffset(testFile.length());
		file.setIdleTimeout(1);
		file.init();
		setup.echo("line1", testFile);
		LineRecord line = file.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		FileContentInfo info = file.getFileContentInfos(false).get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFileName)));
		// try to close it to simulate file rotation delete
		setup.delete(testFile);
		// force wait for file watch
		line = file.readLine();
		assertThat(line, is(nullValue()));
		info = file.getFileContentInfos(true).get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName)));
		assertThat(info.getCurrentFileName(), is(nullValue()));
		assertThat(info.getLastModified(), is(equalTo(0L)));
		assertThat(info.getLength(), is(equalTo(-1L)));
		file.close();
	}

	@Test(timeout = 5000)
	public void testUniqueFilePartialLineRead() throws Exception {
		UniqueFile file = new UniqueFile();
		setup.registerClosable(file);
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		file.setMonitorService(monitorService);
		file.setFile(testFile);
		file.setInitOffset(testFile.length());
		file.init();
		String data = "newline";
		setup.print(data, testFile);
		LineRecord line = file.readLine(3, TimeUnit.SECONDS);
		assertThat(line, is(nullValue()));
		String data1 = "sameline";
		setup.echo(data1, testFile);
		line = file.readLine();
		assertThat(line.getLine(), is(setup.line(data + data1)));
		assertThat(line.getLineNum(), is(equalTo(1)));
		file.close();
	}
}
