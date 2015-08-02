package com.hp.it.perf.monitor.files;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.EOFException;
import java.io.File;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.nio.MonitorFileFactory;

public class FileInstanceTestCase {

	private static final Logger log = LoggerFactory
			.getLogger(FileInstanceTestCase.class);

	private FileTeseBuilder helper;

	private MonitorFileFactory factory;

	@Before
	public void setUp() throws Exception {
		log.info("[Start Test]");
		factory = new MonitorFileFactory();
		helper = new FileTeseBuilder(getClass().getSimpleName());
	}

	@After
	public void tearDown() throws Exception {
		factory.close();
		helper.close();
		helper.printThreads();
		log.info("[End Test]");
	}

	@Test(timeout = 2000)
	public void testUniqueFileRead() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		assertThat(factory.getStatistics().ioResourceCount().get(),
				is(equalTo(1)));
		helper.registerClosable(lineStream);
		String data = "newline";
		helper.echo(data, testFile);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line(data)));
		assertThat(factory.getStatistics().ioResourceCount().get(),
				is(equalTo(1)));
		lineStream.close();
	}

	@Test(timeout = 2000)
	public void testUniqueFileLazyRead() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().lazyMode().build());
		assertThat(factory.getStatistics().ioResourceCount().get(),
				is(equalTo(0)));
		helper.registerClosable(lineStream);
		String data = "newline";
		helper.echo(data, testFile);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line(data)));
		assertThat(factory.getStatistics().ioResourceCount().get(),
				is(equalTo(1)));
		lineStream.close();
	}

	@Test(timeout = 8000)
	public void testUniqueFileModifyReadTake() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		String data = "newline";
		helper.echo(data, testFile);
		ContentLine line = lineStream.take();
		helper.echoSync(data, testFile, 1, TimeUnit.SECONDS);
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line(data)));
		lineStream.close();
	}

	@Test(timeout = 5000)
	public void testUniqueFileModifyReadPoll() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		{
			String data = "newline";
			helper.echo(data, testFile);
			long now = System.currentTimeMillis();
			ContentLine line = lineStream.poll(5, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(line, is(notNullValue()));
			assertThat(line.getLine(), is(helper.line(data)));
			assertThat(duration, is(lessThan(100L)));
		}
		// let file modified time change
		Thread.sleep(1000);
		{
			String data = "line2";
			helper.echoSync(data, testFile, 500, TimeUnit.MILLISECONDS);
			long now = System.currentTimeMillis();
			ContentLine line = lineStream.poll(3, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat("take " + duration + " get null", line,
					is(notNullValue()));
			assertThat(duration, is(greaterThan(500L)));
			assertThat(duration, is(lessThanOrEqualTo(3000L)));
			assertThat(line.getLine(), is(helper.line(data)));
		}
		{
			String data = "line3";
			helper.echoSync(data, testFile, 3, TimeUnit.SECONDS);
			long now = System.currentTimeMillis();
			ContentLine line = lineStream.poll(2, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(duration, is(greaterThanOrEqualTo(2000L)));
			assertThat(line, is(nullValue()));
		}
		lineStream.close();
	}

	@Test
	public void testUniqueFileReadLines() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile);
		helper.echo("line2", testFile);
		LinkedList<ContentLine> list = new LinkedList<ContentLine>();
		int count = lineStream.drainTo(list, 3);
		assertThat(count, is(equalTo(2)));
		list.clear();
		helper.echo("line3", testFile);
		helper.echo("line4", testFile);
		count = lineStream.drainTo(list, 1);
		assertThat(count, is(equalTo(1)));
		count = lineStream.drainTo(list, 2);
		assertThat(count, is(equalTo(1)));
		count = lineStream.drainTo(list, 2);
		assertThat(count, is(equalTo(0)));
		lineStream.close();
	}

	@Test
	public void testUniqueFileGetContent() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		String data = "newline";
		helper.echo(data, testFile);
		ContentLine line = lineStream.poll(1, TimeUnit.MILLISECONDS);
		assertThat(line, is(notNullValue()));
		FileMetadata info = file.getMetadata(true);
		assertThat(info, is(notNullValue()));
		assertThat(info, is(notNullValue()));
		assertThat(info.getName(), is(equalTo(testFile.getName())));
		assertThat(info.getPath(), is(equalTo(testFile.getPath())));
		assertThat(info.getLength(),
				is(equalTo(String.valueOf(testFile.length()))));
		assertThat(info.getLastModifiedDate(),
				is(equalTo(testFile.lastModified())));
		lineStream.close();
	}

	@Test(timeout = 2000L)
	public void testUniqueFileReadLinesWithNoMonitor() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().noMonitor()
						.build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile);
		helper.echo("line2", testFile);
		LinkedList<ContentLine> list = new LinkedList<ContentLine>();
		int count = lineStream.drainTo(list, 3);
		assertThat(count, is(equalTo(2)));
		list.clear();
		count = lineStream.drainTo(list, 3);
		assertThat(count, is(equalTo(-1)));
		lineStream.close();
	}

	@Test(timeout = 2000L)
	public void testUniqueFileModifyReadTakeWithNoMonitor() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().noMonitor()
						.build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		line = lineStream.take();
		assertThat(line, is(nullValue()));
		lineStream.close();
	}

	@Test
	public void testUniqueFileModifyReadPollWithNoMonitor() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().noMonitor()
						.build());
		helper.registerClosable(lineStream);
		try {
			lineStream.poll(1, TimeUnit.SECONDS);
			Assert.fail("not here");
		} catch (EOFException e) {
			assertThat(e, is(notNullValue()));
		}
		lineStream.close();
	}

	@Test(timeout = 4000)
	public void testUniqueFileRename() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		String testFileName = testFile.toString();
		helper.echo("line1", testFile);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		FileMetadata info = file.getMetadata(false);
		assertThat(info, is(notNullValue()));
		assertThat(info.getPath(), is(equalTo(testFileName)));
		// try to close it to handle windows rename error
		File testFile2 = helper.rename(testFile, "sample_file2.txt");
		helper.echo("line2", testFile2);
		// force wait for file watch
		Thread.sleep(3000L);
		line = lineStream.take();
		assertThat(line, is(nullValue()));
		info = file.getMetadata(false);
		assertThat(info, is(notNullValue()));
		assertThat(info.getPath(), is(equalTo(testFileName)));
		lineStream.close();
	}

	@Test(timeout = 5000)
	public void testUniqueFileDelete() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		String testFileName = testFile.toString();
		// TODO how to set idle timeout
		// file.setIdleTimeout(1);
		helper.echo("line1", testFile);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		FileMetadata info = file.getMetadata(false);
		assertThat(info, is(notNullValue()));
		assertThat(info.getPath(), is(equalTo(testFileName)));
		// try to close it to simulate file rotation delete
		helper.delete(testFile);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(nullValue()));
		info = file.getMetadata(true);
		assertThat(info, is(notNullValue()));
		assertThat(info.getPath(), is(equalTo(testFileName)));
		assertThat(info.getLastModifiedDate(), is(equalTo(0L)));
		assertThat(info.getLength(), is(equalTo(String.valueOf(-1))));
		lineStream.close();
	}

	@Test(timeout = 5000)
	public void testUniqueFilePartialLineRead() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileInstance file = factory.getFileInstance(testFile.getPath());
		ContentLineStream lineStream = ((ContentLineStreamProvider) file)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		String data = "newline";
		helper.print(data, testFile);
		ContentLine line = lineStream.poll(3, TimeUnit.SECONDS);
		assertThat(line, is(nullValue()));
		String data1 = "sameline";
		helper.echo(data1, testFile);
		line = lineStream.take();
		assertThat(line.getLine(), is(helper.line(data + data1)));
		lineStream.close();
	}
}
