package com.hp.it.perf.monitor.files;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.nio.MonitorFileFactory;

public class FolderTestCase {

	private static final Logger log = LoggerFactory
			.getLogger(FolderTestCase.class);

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
		// try to wait for monitor threads dead
		Thread.sleep(100L);
		helper.printThreads();
		log.info("[End Test]");
	}

	@Test(timeout = 5000)
	public void testFileRead() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(
				new File("src/test/data/sample_file1.txt"), "sample_file2.txt");
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		String data = "newline";
		helper.echo(data, testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line(data)));
		helper.echo(data, testFile2);
		ContentLine line2 = lineStream.take();
		assertThat(line2, is(notNullValue()));
		assertThat(line2.getLine(), is(helper.line(data)));
		lineStream.close();
	}

	@Test(timeout = 5000)
	public void testFileModifyReadTake() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(
				new File("src/test/data/sample_file1.txt"), "sample_file2.txt");
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		String data = "newline";
		helper.echoSync(data, testFile1, 1, TimeUnit.SECONDS);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line(data)));
		helper.echo(data, testFile2);
		ContentLine line2 = lineStream.take();
		assertThat(line2, is(notNullValue()));
		assertThat(line2.getLine(), is(helper.line(data)));
		lineStream.close();
	}

	protected ContentLineStream createLineStream(FileSet folder)
			throws IOException {
		return ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
	}

	@Test(timeout = 8000)
	public void testFileModifyReadPoll() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		{
			String data = "newline";
			helper.echo(data, testFile1);
			long now = System.currentTimeMillis();
			ContentLine line = lineStream.poll(5, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(line, is(notNullValue()));
			assertThat(line.getLine(), is(helper.line(data)));
			assertThat(duration, is(lessThan(2500L)));
		}
		// let file modified time change
		Thread.sleep(1000);
		{
			String data = "line2";
			helper.echoSync(data, testFile1, 500, TimeUnit.MILLISECONDS);
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
			helper.echoSync(data, testFile1, 3, TimeUnit.SECONDS);
			long now = System.currentTimeMillis();
			ContentLine line = lineStream.poll(2, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(duration, is(greaterThanOrEqualTo(2000L)));
			assertThat(line, is(nullValue()));
		}
		lineStream.close();
	}

	@Test
	public void testReadLines() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(
				new File("src/test/data/sample_file1.txt"), "sample_file2.txt");
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		helper.echo("line2", testFile1);
		// wait for poll time window
		Thread.sleep(2500L);
		Queue<ContentLine> list = new LinkedList<ContentLine>();
		int count = lineStream.drainTo(list, 3);
		assertThat(count, is(equalTo(2)));
		list.clear();
		helper.echo("line3", testFile1);
		helper.echo("line4", testFile2);
		// wait for poll time window
		Thread.sleep(2500L);
		count = lineStream.drainTo(list, 1);
		assertThat(count, is(equalTo(1)));
		count = lineStream.drainTo(list, 2);
		assertThat(count, is(equalTo(1)));
		count = lineStream.drainTo(list, 2);
		assertThat(count, is(equalTo(0)));
		lineStream.close();
	}

	@Test(timeout = 2000L)
	public void testNoFileMonitor() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		// TODO
		helper.delete(testFile1);
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		ContentLine line = lineStream.take();
		assertThat(line, is(nullValue()));
		try {
			line = lineStream.poll(1, TimeUnit.SECONDS);
			Assert.fail("expect EOF");
		} catch (EOFException e) {
			assertThat(e, is(notNullValue(EOFException.class)));
		}
		line = lineStream.poll();
		assertThat(line, is(nullValue()));
		List<ContentLine> list = new ArrayList<ContentLine>();
		int len = lineStream.drainTo(list, 1);
		assertThat(len, is(-1));
		lineStream.close();
	}

	// @Test
	// public void testReadLinesQueueFull() throws Exception {
	// File testFile1 = helper
	// .copy(new File("src/test/data/sample_file1.txt"));
	// File testFile2 = helper.copy(
	// new File("src/test/data/sample_file1.txt"), "sample_file2.txt");
	// FileSet folder = factory.getFileSet(testFile1.getParent());
	// ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
	// .open(new FileOpenOptionBuilder().tailMode().build());
	// helper.registerClosable(lineStream);
	// helper.echo("line1", testFile1);
	// helper.echo("line2", testFile1);
	// helper.echo("line3", testFile1);
	// helper.echo("line4", testFile2);
	// // wait for poll time window
	// Thread.sleep(2500L);
	// Queue<ContentLine> list = new ArrayBlockingQueue<ContentLine>(2);
	// int count = lineStream.drainTo(list, 3);
	// assertThat(count, is(equalTo(FileContentProvider.QUEUE_FULL)));
	// assertThat(list.poll().getLine(), is(helper.line("line1")));
	// assertThat(list.poll().getLine(), is(helper.line("line2")));
	// list.clear();
	// Thread.sleep(2000L);
	// count = lineStream.drainTo(list, 3);
	// assertThat(count, is(equalTo(2)));
	// assertThat(list.poll().getLine(), is(helper.line("line3")));
	// assertThat(list.poll().getLine(), is(helper.line("line4")));
	// lineStream.close();
	// }

	@Test
	public void testGetContent() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		String testFileName1 = testFile1.getPath();
		String data = "newline";
		helper.echo(data, testFile1);
		ContentLine line = lineStream.poll(3, TimeUnit.SECONDS);
		assertThat(line, is(notNullValue()));
		List<? extends FileInstance> contentInfos = folder.listInstances();
		assertThat(contentInfos, is(notNullValue()));
		assertThat(contentInfos.size(), is(equalTo(1)));
		FileMetadata info = contentInfos.get(0).getMetadata(true);
		assertThat(info, is(notNullValue()));
		assertThat(info.getPath(), is(equalTo(testFileName1)));
		assertThat(info.getLastModifiedDate(),
				is(equalTo(testFile1.lastModified())));
		assertThat(info.getLength(),
				is(equalTo(String.valueOf(testFile1.length()))));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testFileRenameSimple() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(
				new File("src/test/data/sample_file1.txt"), "sample_file2.txt");
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		Set<String> expected = new TreeSet<String>();
		expected.add(infos.get(0).getMetadata(false).getPath());
		expected.add(infos.get(1).getMetadata(false).getPath());
		Set<String> current = new TreeSet<String>();
		current.add(testFile1.getPath());
		current.add(testFile2.getPath());
		assertThat(expected, is(equalTo(current)));
		// start rename simple
		File testFile2x = helper.rename(testFile2, "sample_file3a.txt");
		File testFile1x = helper.rename(testFile1, "sample_file2a.txt");
		Thread.sleep(2500L);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		expected = new TreeSet<String>();
		expected.add(infos.get(0).getMetadata(false).getPath());
		expected.add(infos.get(1).getMetadata(false).getPath());
		current = new TreeSet<String>();
		current.add(testFile1x.getPath());
		current.add(testFile2x.getPath());
		assertThat(expected, is(equalTo(current)));
		// another
		helper.echo("line3", testFile2x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		expected = new TreeSet<String>();
		expected.add(infos.get(0).getMetadata(false).getPath());
		expected.add(infos.get(1).getMetadata(false).getPath());
		current = new TreeSet<String>();
		current.add(testFile1x.getPath());
		current.add(testFile2x.getPath());
		assertThat(expected, is(equalTo(current)));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testFileRenameRotate() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(
				new File("src/test/data/sample_file1.txt"), "sample_file2.txt");
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		Set<String> expected = new TreeSet<String>();
		expected.add(infos.get(0).getMetadata(false).getPath());
		expected.add(infos.get(1).getMetadata(false).getPath());
		Set<String> current = new TreeSet<String>();
		current.add(testFile1.getPath());
		current.add(testFile2.getPath());
		assertThat(expected, is(equalTo(current)));
		// start rename rotate
		File testFile2x = helper.rename(testFile2, "sample_file3.txt");
		File testFile1x = helper.rename(testFile1, "sample_file2.txt");
		Thread.sleep(2500L);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		expected = new TreeSet<String>();
		expected.add(infos.get(0).getMetadata(false).getPath());
		expected.add(infos.get(1).getMetadata(false).getPath());
		current = new TreeSet<String>();
		current.add(testFile1x.getPath());
		current.add(testFile2x.getPath());
		assertThat(expected, is(equalTo(current)));
		// another
		helper.echo("line3", testFile2x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		expected = new TreeSet<String>();
		expected.add(infos.get(0).getMetadata(false).getPath());
		expected.add(infos.get(1).getMetadata(false).getPath());
		current = new TreeSet<String>();
		current.add(testFile1x.getPath());
		current.add(testFile2x.getPath());
		assertThat(expected, is(equalTo(current)));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testDelete() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileSet folder = factory.getFileSet(testFile.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		String testFileName = testFile.getPath();
		helper.echo("line1", testFile);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		FileMetadata info = folder.listInstances().get(0).getMetadata(false);
		assertThat(info, is(notNullValue()));
		assertThat(info.getPath(), is(equalTo(testFileName)));
		// try to close it to simulate file rotation delete
		helper.delete(testFile);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(nullValue()));
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.isEmpty(), is(true));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testFileRotate() throws Exception {
		log.debug("Test on " + getClass().getSimpleName() + ".testFileRotate()");
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(
				new File("src/test/data/sample_file1.txt"), "sample_file2.txt");
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = createLineStream(folder);
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getName().equals(testFile1.getName())) {
			testFile1Index = 0;
			testFile2Index = 1;
		} else {
			testFile1Index = 1;
			testFile2Index = 0;
		}
		assertThat(infos.get(testFile1Index).getMetadata(false).getPath(),
				is(equalTo(testFile1.getPath())));
		assertThat(infos.get(testFile2Index).getMetadata(false).getPath(),
				is(equalTo(testFile2.getPath())));
		// start rename rotate
		helper.delete(testFile2);
		File testFile1x = helper.rename(testFile1, "sample_file2.txt");
		helper.echo("newline", testFile1);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		Thread.sleep(1000L);
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		lineStream.close();
	}

	@Test(timeout = 5000)
	public void testPartialLineRead() throws Exception {
		File testFile = helper.copy(new File("src/test/data/sample_file1.txt"));
		FileSet folder = factory.getFileSet(testFile.getParent());
		ContentLineStream lineStream = createLineStream(folder);
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
