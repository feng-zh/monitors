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

	private FileTeseBuilder setup;

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
		setup = new FileTeseBuilder(UniqueFileTestCase.class.getName());
	}

	@After
	public void tearDown() throws Exception {
		monitorService.close();
		setup.close();
		setup.printThreads();
		log.info("[End Test]");
	}

	@Test(timeout = 6000)
	public void testFileRename() throws Exception {
		FolderContentProvider folder = new FolderContentProvider();
		setup.registerClosable(folder);
		File testFile1 = setup.copy(new File("src/test/data/sample_file1.txt"));
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
		assertThat(infos.size(), is(equalTo(1)));
		int testFile1Index = 0;
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1.getPath())));
		// start rename simple
		File testFile1x = setup.simulateRename(testFile1, "sample_file1a.txt");
		Thread.sleep(2500L);
		setup.echo("line2", testFile1x);
		// force wait for file watch
		line = folder.readLine();
		infos = folder.getFileContentInfos(false);
		assertThat(infos.size(), is(equalTo(1)));
		assertThat(infos.get(testFile1Index).getCurrentFileName(),
				is(equalTo(testFile1x.getPath())));
		assertThat(line, is(notNullValue()));
		assertThat(line.getLineNum(), is(equalTo(2)));
		assertThat(line.getLine(), is(setup.line("line2")));
		folder.close();
	}
}
