package com.hp.it.perf.monitor.filemonitor;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.filemonitor.nio.ContentBasedFileKeyDetector;
import com.hp.it.perf.monitor.filemonitor.nio.FileKeyDetector;
import com.hp.it.perf.monitor.filemonitor.nio.FileKeyDetectorFactory;
import com.hp.it.perf.monitor.filemonitor.nio.NioFileMonitorService;

public class SshfsTestCase {

	private static final Logger log = LoggerFactory
			.getLogger(SshfsTestCase.class);

	private FileMonitorService monitorService;

	private FileTeseBuilder helper;

	@Before
	public void setUp() throws Exception {
		log.info("[Start Test]");
		monitorService = new NioFileMonitorService();
		((NioFileMonitorService) monitorService)
				.setKeyDetectorFactory(new FileKeyDetectorFactory() {

					@Override
					public FileKeyDetector create(Path basePath) {
						return new ContentBasedFileKeyDetector(basePath);
					}
				});
		helper = new FileTeseBuilder(getClass().getSimpleName());
	}

	@After
	public void tearDown() throws Exception {
		monitorService.close();
		helper.close();
		helper.printThreads();
		log.info("[End Test]");
	}

	@Test(timeout = 6000)
	public void testFileRename() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		helper.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		// prepare file content info
		List<FileContentInfo> infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(1)));
		int testFile1Index = 0;
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1.getPath())));
		// start rename simple
		File testFile1x = helper.simulateRename(testFile1, "sample_file1a.txt");
		Thread.sleep(2500L);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = folder.readLine();
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(1)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(line, is(notNullValue()));
		assertThat(line.getLineNum(), is(equalTo(2)));
		assertThat(line.getLine(), is(helper.line("line2")));
		folder.close();
	}

	@Test(timeout = 6000)
	public void testFileRenameRotate() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		helper.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
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
		File testFile2x = helper.simulateRename(testFile2, "sample_file3.txt");
		File testFile1x = helper.simulateRename(testFile1, "sample_file2.txt");
		Thread.sleep(2500L);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2x.getPath())));
		// another
		helper.echo("line3", testFile2x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2x.getPath())));
		folder.close();
	}

	@Test
	public void testFileMoreRenameRotate() throws Exception {
		StringBuffer sb = new StringBuffer();
		Random random = new Random();
		for(int i=0;i<1000;i++) {
			sb.append(Long.toHexString(random.nextLong()));
		}
		// System.setProperty("monitor.nio.slow", "true");
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper.copy(
				new File("src/test/data/sample_file1.txt"), "business.log");
		helper.setModifiedBefore(testFile1, 1, TimeUnit.MINUTES);
		File testFile2 = helper.copy(
				new File("src/test/data/sample_file2.txt"), "business.log.1");
		helper.setModifiedBefore(testFile2, 15, TimeUnit.MINUTES);
		File testFile3 = helper.copy(
				new File("src/test/data/sample_file3.txt"), "business.log.2");
		helper.setModifiedBefore(testFile3, 45, TimeUnit.MINUTES);
		File testFile4 = helper.create("business.log.3");
		helper.echo(sb.toString(), testFile4);
		helper.setModifiedBefore(testFile4, 1, TimeUnit.HOURS);
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		helper.echo("line1"+sb.substring(0, 1024).toString(), testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(new String(line.getLine()), startsWith("line1"));
		assertThat(line.getLineNum(), is(equalTo(1)));
		// prepare file content info
		List<FileContentInfo> infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(4)));
		Thread.sleep(1000L);
		// start modify rename rotate
		helper.echo("line2"+sb.substring(0, 1024).toString(), testFile1);
		helper.delete(testFile4);
		helper.simulateRename(testFile3, "business.log.3");
		helper.simulateRename(testFile2, "business.log.2");
		helper.simulateRename(testFile1, "business.log.1");
		helper.echo("line3"+sb.substring(0, 1024).toString(), testFile1);
		helper.echo("line4"+sb.substring(0, 1024).toString(), testFile1);
		// force wait for file watch
		for (int i = 0; i < 3; i++) {
			line = folder.readLine();
			assertThat(line, is(notNullValue()));
			assertThat(new String(line.getLine()), startsWith("line"));
		}
		int len = folder.readLines(new LinkedList<LineRecord>(), 10);
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(4)));
		assertThat(len, is(equalTo(0)));
		folder.close();
	}

	@Test(timeout = 6000)
	public void testFileModifyRenameRotate() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		helper.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
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
		// quick modify
		helper.echo("line2", testFile2);
		// start rename rotate
		File testFile2x = helper.simulateRename(testFile2, "sample_file3.txt");
		File testFile1x = helper.simulateRename(testFile1, "sample_file2.txt");
		Thread.sleep(2500L);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getCurrentFileName(),
				is(equalTo(testFile2x.getPath())));
		folder.close();
	}

	@Test
	public void testFileSingleRotate() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		helper.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		// prepare file content info
		List<FileContentInfo> infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(1)));
		int testFile1Index = 0;
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1.getPath())));
		// start rename rotate, and create new one
		helper.echo("line2", testFile1);
		helper.simulateRename(testFile1, "sample_file2.txt");
		helper.echo("line3", testFile1);
		Thread.sleep(2500L);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		// another
		helper.echo("line4", testFile1);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line4")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		folder.close();
	}

	@Test(timeout = 6000)
	public void testFileFixedRotate() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		helper.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
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
		helper.delete(testFile2);
		helper.simulateRename(testFile1, "sample_file2.txt");
		helper.echo("line2", testFile1);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(1)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		// another
		helper.echo("line3", testFile1);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(2)));
		folder.close();
	}

	@Test(timeout = 6000)
	public void testFileMoveOverride() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		folder.setMonitorService(monitorService);
		folder.setFolder(testFile1.getParentFile());
		folder.setTailMode(true);
		folder.init();
		helper.echo("line1", testFile1);
		LineRecord line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
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
		// start move override
		File testFile1x = helper.simulateRename(testFile1, "sample_file2.txt");
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		assertThat(line.getLineNum(), is(equalTo(2)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(1)));
		// another
		helper.echo("line3", testFile1x);
		// force wait for file watch
		line = folder.readLine();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		assertThat(line.getLineNum(), is(equalTo(3)));
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(1)));
		folder.close();
	}
}
