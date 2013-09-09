package com.hp.it.perf.monitor.files;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.it.perf.monitor.files.nio.MonitorFileFactory;

public class SshfsTestCase {

	private static final Logger log = LoggerFactory
			.getLogger(SshfsTestCase.class);

	private FileTeseBuilder helper;

	private MonitorFileFactory factory;

	@Before
	public void setUp() throws Exception {
		log.info("[Start Test]");
		factory = new MonitorFileFactory();
		factory.setForcePollMode(true);
		helper = new FileTeseBuilder(getClass().getSimpleName());
	}

	@After
	public void tearDown() throws Exception {
		factory.close();
		helper.close();
		helper.printThreads();
		log.info("[End Test]");
	}

	@Test(timeout = 6000)
	public void testFileRename() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(1)));
		int testFile1Index = 0;
		assertThat(infos.get(testFile1Index).getMetadata(false).getPath(),
				is(equalTo(testFile1.getPath())));
		// start rename simple
		File testFile1x = helper.simulateRename(testFile1, "sample_file1a.txt",
				false);
		Thread.sleep(2500L);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = lineStream.take();
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(1)));
		assertThat(infos.get(testFile1Index).getMetadata(false).getPath(),
				is(equalTo(testFile1x.getPath())));
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testFileRenameRotate() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getMetadata(false).getPath()
				.equals(testFile1.getPath())) {
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
		File testFile2x = helper.simulateRename(testFile2, "sample_file3.txt",
				true);
		File testFile1x = helper.simulateRename(testFile1, "sample_file2.txt",
				false);
		Thread.sleep(2500L);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getMetadata(false).getPath(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getMetadata(false).getPath(),
				is(equalTo(testFile2x.getPath())));
		// another
		helper.echo("line3", testFile2x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getMetadata(false).getPath(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getMetadata(false).getPath(),
				is(equalTo(testFile2x.getPath())));
		lineStream.close();
	}

	@Test
	public void testFileMoreRenameRotate() throws Exception {
		StringBuffer sb = new StringBuffer();
		Random random = new Random();
		for (int i = 0; i < 1000; i++) {
			sb.append(Long.toHexString(random.nextLong()));
		}
		// System.setProperty("monitor.nio.slow", "true");
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
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1" + sb.substring(0, 1024).toString(), testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(new String(line.getLine()), startsWith("line1"));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(4)));
		Thread.sleep(1000L);
		// start modify rename rotate
		helper.echo("line2" + sb.substring(0, 1024).toString(), testFile1);
		helper.delete(testFile4);
		helper.simulateRename(testFile3, "business.log.3", true);
		helper.simulateRename(testFile2, "business.log.2", true);
		helper.simulateRename(testFile1, "business.log.1", true);
		helper.echo("line3" + sb.substring(0, 1024).toString(), testFile1);
		helper.echo("line4" + sb.substring(0, 1024).toString(), testFile1);
		// force wait for file watch
		for (int i = 0; i < 3; i++) {
			line = lineStream.take();
			assertThat(line, is(notNullValue()));
			assertThat(new String(line.getLine()), startsWith("line"));
		}
		int len = lineStream.drainTo(new LinkedList<ContentLine>(), 10);
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(4)));
		assertThat(len, is(equalTo(0)));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testFileModifyRenameRotate() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getMetadata(false).getPath()
				.equals(testFile1.getPath())) {
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
		// quick modify
		helper.echo("line2", testFile2);
		// start rename rotate
		File testFile2x = helper.simulateRename(testFile2, "sample_file3.txt",
				true);
		File testFile1x = helper.simulateRename(testFile1, "sample_file2.txt",
				false);
		Thread.sleep(2500L);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		assertThat(infos.get(testFile1Index).getMetadata(false).getPath(),
				is(equalTo(testFile1x.getPath())));
		assertThat(infos.get(testFile2Index).getMetadata(false).getPath(),
				is(equalTo(testFile2x.getPath())));
		lineStream.close();
	}

	@Test
	public void testFileSingleRotate() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(1)));
		int testFile1Index = 0;
		assertThat(infos.get(testFile1Index).getMetadata(false).getPath(),
				is(equalTo(testFile1.getPath())));
		// start rename rotate, and create new one
		helper.echo("line2", testFile1);
		helper.simulateRename(testFile1, "sample_file2.txt", true);
		helper.echo("line3", testFile1);
		Thread.sleep(2500L);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		// another
		helper.echo("line4", testFile1);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line4")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testFileFixedRotate() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getMetadata(false).getPath()
				.equals(testFile1.getPath())) {
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
		helper.simulateRename(testFile1, "sample_file2.txt", false);
		helper.echo("line2", testFile1);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		// another
		helper.echo("line3", testFile1);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		lineStream.close();
	}

	@Test(timeout = 6000)
	public void testFileMoveOverride() throws Exception {
		File testFile1 = helper
				.copy(new File("src/test/data/sample_file1.txt"));
		File testFile2 = helper
				.copy(new File("src/test/data/sample_file2.txt"));
		FileSet folder = factory.getFileSet(testFile1.getParent());
		ContentLineStream lineStream = ((ContentLineStreamProvider) folder)
				.open(new FileOpenOptionBuilder().tailMode().build());
		helper.registerClosable(lineStream);
		helper.echo("line1", testFile1);
		ContentLine line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line1")));
		// prepare file content info
		List<? extends FileInstance> infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(2)));
		int testFile1Index, testFile2Index;
		if (infos.get(0).getMetadata(false).getPath()
				.equals(testFile1.getPath())) {
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
		// start move override
		File testFile1x = helper.simulateRename(testFile1, "sample_file2.txt",
				false);
		helper.echo("line2", testFile1x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line2")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(1)));
		// another
		helper.echo("line3", testFile1x);
		// force wait for file watch
		line = lineStream.take();
		assertThat(line, is(notNullValue()));
		assertThat(line.getLine(), is(helper.line("line3")));
		infos = folder.listInstances();
		assertThat(infos.size(), is(equalTo(1)));
		lineStream.close();
	}
}
