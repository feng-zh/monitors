package com.hp.it.perf.monitor.filemonitor;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

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
		File testFile1 = helper.copy(new File("src/test/data/sample_file1.txt"));
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
		File testFile1 = helper.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(new File("src/test/data/sample_file2.txt"));
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
	
	@Test(timeout = 6000)
	public void testFileSingleRotate() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		helper.registerClosable(folder);
		File testFile1 = helper.copy(new File("src/test/data/sample_file1.txt"));
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
		int testFile1Index =0 ;
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
		File testFile1 = helper.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(new File("src/test/data/sample_file2.txt"));
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
		File testFile1 = helper.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper.copy(new File("src/test/data/sample_file2.txt"));
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
