package com.hp.it.perf.monitor.filemonitor;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.filemonitor.nio.MultiMonitorService;

public class FolderTestCase {

	private static final Logger log = LoggerFactory
			.getLogger(FolderTestCase.class);

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

	@Test(timeout = 5000)
	public void testFileRead() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		String data = "newline";
		setup.echo(data, testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line(data)));
		assertThat(line.getLineNum(), is(equalTo(1)));
		setup.echo(data, testFile2);
		LineRecord line2 = folder.readLine();
		assertThat(line2, is(notNullValue()));
		assertThat(line2.getLine(), is(setup.line(data)));
		assertThat(line2.getLineNum(), is(equalTo(1)));
		folder.close();
	}

	@Test(timeout = 5000)
	public void testFileModifyReadTake() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		String data = "newline";
		setup.echoSync(data, testFile1, 1, TimeUnit.SECONDS);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line(data)));
		assertThat(line.getLineNum(), is(equalTo(1)));
		setup.echo(data, testFile2);
		LineRecord line2 = folder.readLine();
		assertThat(line2, is(notNullValue()));
		assertThat(line2.getLine(), is(setup.line(data)));
		assertThat(line2.getLineNum(), is(equalTo(1)));
		folder.close();
	}

	@Test(timeout = 8000)
	public void testFileModifyReadPoll() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		{
			String data = "newline";
			setup.echo(data, testFile1);
			long now = System.currentTimeMillis();
			LineRecord line = folder.readLine(5, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(line, is(notNullValue()));
			assertThat(line.getLine(), is(setup.line(data)));
			assertThat(line.getLineNum(), is(equalTo(1)));
			assertThat(duration, is(lessThan(2500L)));
		}
		// let file modified time change
		Thread.sleep(1000);
		{
			String data = "line2";
			setup.echoSync(data, testFile1, 500, TimeUnit.MILLISECONDS);
			long now = System.currentTimeMillis();
			LineRecord line = folder.readLine(3, TimeUnit.SECONDS);
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
			setup.echoSync(data, testFile1, 3, TimeUnit.SECONDS);
			long now = System.currentTimeMillis();
			LineRecord line = folder.readLine(2, TimeUnit.SECONDS);
			long duration = System.currentTimeMillis() - now;
			assertThat(duration, is(greaterThanOrEqualTo(2000L)));
			assertThat(line, is(nullValue()));
		}
		folder.close();
	}

	@Test
	public void testReadLines() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		setup.echo("line1", testFile1);
		setup.echo("line2", testFile1);
		// wait for poll time window
		Thread.sleep(2000L);
		Queue<LineRecord> list = new LinkedList<LineRecord>();
		int count = folder.readLines(list, 3);
		assertThat(count, is(equalTo(2)));
		list.clear();
		setup.echo("line3", testFile1);
		setup.echo("line4", testFile2);
		// wait for poll time window
		Thread.sleep(2000L);
		count = folder.readLines(list, 1);
		assertThat(count, is(equalTo(1)));
		count = folder.readLines(list, 2);
		assertThat(count, is(equalTo(1)));
		count = folder.readLines(list, 2);
		assertThat(count, is(equalTo(0)));
		folder.close();
	}

	@Test
	public void testReadLinesQueueFull() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		setup.echo("line1", testFile1);
		setup.echo("line2", testFile1);
		setup.echo("line3", testFile1);
		setup.echo("line4", testFile2);
		// wait for poll time window
		Thread.sleep(2000L);
		Queue<LineRecord> list = new ArrayBlockingQueue<LineRecord>(2);
		int count = folder.readLines(list, 3);
		assertThat(count, is(equalTo(FileContentProvider.QUEUE_FULL)));
		assertThat(list.poll().getLine(), is(setup.line("line1")));
		assertThat(list.poll().getLine(), is(setup.line("line2")));
		list.clear();
		Thread.sleep(2000L);
		count = folder.readLines(list, 3);
		assertThat(count, is(equalTo(2)));
		assertThat(list.poll().getLine(), is(setup.line("line3")));
		assertThat(list.poll().getLine(), is(setup.line("line4")));
		folder.close();
	}

	@Test
	public void testGetContent() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		String testFileName1 = testFile1.getPath();
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		String data = "newline";
		setup.echo(data, testFile1);
		LineRecord line = folder.readLine(3, TimeUnit.SECONDS);
		assertThat(line, is(notNullValue()));
		List<FileContentInfo> contentInfos = folder.getFileContentInfos(false);
		assertThat(contentInfos, is(notNullValue()));
		assertThat(contentInfos.size(), is(equalTo(1)));
		FileContentInfo info = contentInfos.get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName1)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFileName1)));
		assertThat(info.getProviderId(), is(not(equalTo(0L))));
		assertThat(info.getProviderId(), is(equalTo(line.getProviderId())));
		// realtime info
		contentInfos = folder.getFileContentInfos(true);
		assertThat(contentInfos, is(notNullValue()));
		assertThat(contentInfos.size(), is(equalTo(1)));
		info = contentInfos.get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName1)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFileName1)));
		assertThat(info.getProviderId(), is(equalTo(line.getProviderId())));
		assertThat(info.getLastModified(),
				is(equalTo(testFile1.lastModified())));
		assertThat(info.getLength(), is(equalTo(testFile1.length())));
		assertThat(info.getOffset(), is(equalTo(testFile1.length())));
		folder.close();
	}

	@Test(timeout = 6000)
	public void testFileRenameSimple() throws Exception {
		if (System.getProperty("os.name").indexOf("Windows") != -1) {
			return;
		}
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		setup.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		// prepare file content info
		List<FileContentInfo> infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getFileName().equals(testFile1.getPath())) {
			testFile1Index = 0;
			testFile2Index = 1;
		} else {
			testFile1Index = 1;
			testFile2Index = 0;
		}
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2.getPath())));
		// start rename simple
		File testFile2x = setup.rename(testFile2, "sample_file3a.txt");
		File testFile1x = setup.rename(testFile1, "sample_file2a.txt");
		Thread.sleep(2500L);
		setup.echo("line2", testFile1x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2x.getPath())));
		// another
		setup.echo("line3", testFile2x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line3")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2x.getPath())));
		folder.close();
	}

	@Test(timeout = 6000)
	public void testFileRenameRotate() throws Exception {
		if (System.getProperty("os.name").indexOf("Windows") != -1) {
			return;
		}
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		setup.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		// prepare file content info
		List<FileContentInfo> infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getFileName().equals(testFile1.getPath())) {
			testFile1Index = 0;
			testFile2Index = 1;
		} else {
			testFile1Index = 1;
			testFile2Index = 0;
		}
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2.getPath())));
		// start rename rotate
		File testFile2x = setup.rename(testFile2, "sample_file3.txt");
		File testFile1x = setup.rename(testFile1, "sample_file2.txt");
		Thread.sleep(2500L);
		setup.echo("line2", testFile1x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2x.getPath())));
		// another
		setup.echo("line3", testFile2x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line3")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2x.getPath())));
		folder.close();
	}

	@Test(timeout = 5000)
	public void testDelete() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		String testFileName = testFile.getPath();
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile.getParentFile());
		folder.setTailMode(true);
		folder.init();
		setup.echo("line1", testFile);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		FileContentInfo info = folder.getFileContentInfos(false).get(0);
		assertThat(info, is(notNullValue()));
		assertThat(info.getFileKey(), is(notNullValue()));
		assertThat(info.getFileName(), is(equalTo(testFileName)));
		assertThat(info.getCurrentFileName(), is(equalTo(testFileName)));
		// try to close it to simulate file rotation delete
		setup.delete(testFile);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(nullValue()));
		List<FileContentInfo> infos = folder.getFileContentInfos(true);
		assertThat(infos.isEmpty(), is(true));
		folder.close();
	}

	@Test(timeout = 6000)
	public void testFileRotate() throws Exception {
		if (System.getProperty("os.name").indexOf("Windows") != -1) {
			return;
		}
		FolderContentProvider folder = new FolderContentProvider();
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = setup.copy(new File("src/test/data/sample_file1.txt"),
				"sample_file2.txt");
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		setup.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(setup.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		// prepare file content info
		List<FileContentInfo> infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getFileName().equals(testFile1.getPath())) {
			testFile1Index = 0;
			testFile2Index = 1;
		} else {
			testFile1Index = 1;
			testFile2Index = 0;
		}
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2.getPath())));
		// start rename rotate
		setup.delete(testFile2);
		File testFile1x = setup.rename(testFile1, "sample_file2.txt");
		setup.echo("newline", testFile1);
		setup.echo("line2", testFile1x);
//		Thread.sleep(2500L);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		for(FileContentInfo info:infos) {
			System.out.println(info.getFileName());
			System.out.println(info.getCurrentFileName());
		}
		folder.close();
	}

	@Test(timeout = 5000)
	public void testPartialLineRead() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		File testFile = setup.copy(new File("src/test/data/sample_file1.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile.getParentFile());
		folder.setTailMode(true);
		folder.init();
		String data = "newline";
		setup.print(data, testFile);
		LineRecord line = folder.readLine(3, TimeUnit.SECONDS);
		assertThat(line, is(nullValue()));
		String data1 = "sameline";
		setup.echo(data1, testFile);
		line = folder.readLine();
		assertThat(line.getLine(), is(setup.line(data + data1)));
		assertThat(line.getLineNum(), is(equalTo(1)));
		folder.close();
	}

}
