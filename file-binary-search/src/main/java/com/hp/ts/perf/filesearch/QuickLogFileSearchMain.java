package com.hp.ts.perf.filesearch;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.TimeZone;

import com.hp.ts.perf.filesearch.impl.ContentBlockSearchers;
import com.hp.ts.perf.filesearch.impl.LineBasedFileLoader;
import com.hp.ts.perf.filesearch.impl.LineBasedSequentialLoader;
import com.hp.ts.perf.filesearch.util.ComparableRange;
import com.hp.ts.perf.filesearch.util.DateTimeQuickParser;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

public class QuickLogFileSearchMain implements ContentBlockMatcher,
		ContentBlockDetector {

	@Argument(value = "startTime", alias = "b", description = "Start/Begin time")
	private String startTime = System.getProperty("startTime");

	@Argument(value = "endTime", alias = "e", description = "End time")
	private String endTime = System.getProperty("endTime");

	@Argument(value = "format", alias = "f", description = "Date time format")
	private String dateTimeFormat = null;

	@Argument(value = "sequential", alias = "s", description = "Use Sequential search for file or stdin, otherwise use liner search for files (name in arguments or stdin)")
	private boolean sequential;

	@Argument(value = "verbose", alias = "v", description = "Show statistic information")
	private boolean verbose;

	@Argument(value = "ignoreFileTime", alias = "i", description = "Ignore file time check")
	private boolean ignoreFileTime = false;

	@Argument(value = "withFileName", alias = "H", description = "Print the filename for each matched line")
	private boolean withFileName = false;

	@Argument(value = "headFileName", alias = "h", description = "Print the filename before content")
	private boolean headFileName = false;

	@Argument(value = "period", alias = "d", description = "Use period with start/end time, like 00:30 (half an hour), 00:00:05.30 (5 sec, 30 ms)")
	private String period;

	private DateTimeQuickParser timeParser = new DateTimeQuickParser();

	private ComparableRange<Date> dateRange;

	private LinePrefixOutputStream out = new LinePrefixOutputStream(System.out);

	private static class LinePrefixOutputStream extends FilterOutputStream {

		private byte[] prefix;

		private boolean started;

		private byte[] header;

		public LinePrefixOutputStream(OutputStream out) {
			super(out);
		}

		public void setHeader(String header) {
			if (header != null) {
				this.header = header.getBytes();
			}
		}

		public void setPrefix(String prefix) {
			if (prefix == null) {
				this.prefix = new byte[0];
			} else {
				this.prefix = prefix.getBytes();
			}
		}

		@Override
		public void write(int b) throws IOException {
			if (header != null) {
				out.write(header);
				header = null;
			}
			if (!started) {
				if (prefix!= null && prefix.length > 0) {
					out.write(prefix);
				}
				started = true;
			}
			super.write(b);
			if (b == '\n') {
				// new line
				started = false;
			}
		}

		public void reset() {
			started = false;
			setHeader(null);
			setPrefix(null);
		}

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		QuickLogFileSearchMain main = new QuickLogFileSearchMain();
		List<String> files;
		try {
			files = Args.parse(main, args);
		} catch (IllegalArgumentException e) {
			System.err.println("Error: " + e.getMessage());
			Args.usage(main);
			System.err.println("[<File>] ...");
			System.exit(1);
			return;
		}
		try {
			main.prepare();
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			System.exit(2);
			return;
		}
		if (files.isEmpty()) {
			if (main.sequential) {
				// Use system in
				try {
					main.sequentialSearch(System.in, null);
				} catch (IOException e) {
					System.err.println("IO Error: " + e.getMessage());
				}
			} else {
				Scanner fileNameScanner = new Scanner(System.in);
				while (fileNameScanner.hasNextLine()) {
					String file = fileNameScanner.nextLine();
					main.processFile(file);
				}
				fileNameScanner.close();
			}
		} else {
			for (String file : files) {
				main.processFile(file);
			}
		}
	}

	protected void processFile(String file) {
		Closeable closeable = null;
		try {
			File theFile = new File(file);
			out.reset();
			if (theFile.exists() && theFile.isFile()) {
				if (!ignoreFileTime) {
					long lastModified = theFile.lastModified();
					MatchRelation fileMatchRelation = dateRange
							.compare(new Date(lastModified));
					if (fileMatchRelation == MatchRelation.Less) {
						if (verbose) {
							System.out
									.println("Statistic(other): Ignore old file "
											+ file);
						}
						return;
					}
				}
				if (headFileName) {
					out.setHeader("==> " + file + " <==\n");
				} else if (withFileName) {
					out.setPrefix(file + ":");
				}
			}
			if (!sequential) {
				RandomAccessFile accessFile = new RandomAccessFile(theFile, "r");
				closeable = accessFile;
				linearSearch(accessFile, theFile);
			} else {
				FileInputStream input = new FileInputStream(theFile);
				closeable = input;
				sequentialSearch(input, theFile);
			}
		} catch (IOException e) {
			System.err.println("IO Error: " + e.getMessage());
			if (verbose) {
				e.printStackTrace();
			}
		} finally {
			if (closeable != null) {
				try {
					closeable.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	public void prepare() {
		DateTimeQuickParser parser = null;
		parser = new DateTimeQuickParser();
		parser.setCustomizedFormat(this.dateTimeFormat);
		Date startDate = null;
		if (startTime != null) {
			startDate = parser.parse(startTime);
			if (startDate == null) {
				throw new IllegalArgumentException(
						"invalid start time argument: " + startTime);
			}
		}
		parser = new DateTimeQuickParser();
		parser.setCustomizedFormat(this.dateTimeFormat);
		Date endDate = null;
		if (endTime != null) {
			endDate = parser.parse(endTime);
			if (endDate == null) {
				throw new IllegalArgumentException(
						"invalid end time argument: " + endTime);
			}
		}
		long timePeriod = parsePeriod();
		if (timePeriod != -1) {
			if (startDate == null && endDate != null) {
				startDate = new Date(endDate.getTime() - timePeriod);
			} else if (startDate != null && endDate == null) {
				endDate = new Date(startDate.getTime() + timePeriod);
			}
		}
		dateRange = new ComparableRange<Date>(startDate, endDate);
		if (verbose) {
			System.out.println("[Verbose] " + dateRange);
		}
	}

	private static String[] availablePeriodFormat = { "s.S", "m:s.S",
			"H:m:s.S", "H:m:s", "H:m" };

	private long parsePeriod() {
		if (period != null) {
			SimpleDateFormat format = new SimpleDateFormat();
			TimeZone GMT = TimeZone.getTimeZone("GMT");
			Calendar now = Calendar.getInstance();
			for (String pattern : availablePeriodFormat) {
				format.applyPattern(pattern);
				now.setTimeInMillis(0);
				format.setCalendar(now);
				format.setTimeZone(GMT);
				try {
					return format.parse(period).getTime();
				} catch (ParseException e) {
					continue;
				}
			}
			throw new IllegalArgumentException("invalid time period: " + period);
		} else {
			return -1L;
		}
	}

	public void linearSearch(RandomAccessFile file, File searchFile)
			throws IOException {
		timeParser.setCustomizedFormat(this.dateTimeFormat);
		ContentBlockRandomAccess loader = new LineBasedFileLoader(file, this);
		ContentBlockMatchResult result = ContentBlockSearchers.linearSearch(
				loader, this);
		if (result.isMatched()) {
			ContentBlock startBlock = result.getMinBlock();
			ContentBlock endBlock = result.getMaxBlock();
			long startOff = startBlock.getStart();
			file.seek(startOff);
			long len = endBlock.getEnd() - startOff;
			byte[] buffer = new byte[1024 * 8];
			while (len > 0) {
				int size = file.read(buffer, 0,
						(int) Math.min(buffer.length, len));
				if (size < 0) {
					throw new EOFException("" + len);
				}
				if (size == 0) {
					throw new IllegalStateException(String.format(
							"unexpected size == 0. (len = %s, startOff = %s)",
							len, startOff));
				}
				out.write(buffer, 0, size);
				len -= size;
			}
			out.flush();
		}
		if (verbose) {
			System.out.println("Statistic(linear): " + loader.getStatistic()
					+ (searchFile != null ? ("on file " + searchFile) : ""));
		}
	}

	public void sequentialSearch(InputStream input, File searchFile)
			throws IOException {
		timeParser.setCustomizedFormat(this.dateTimeFormat);
		LineBasedSequentialLoader loader = new LineBasedSequentialLoader(input,
				this);
		Iterator<ContentBlock> result = ContentBlockSearchers.sequentialSearch(
				loader, this);
		while (result.hasNext()) {
			ContentBlock block = result.next();
			block.writeTo(out);
		}
		out.flush();
		if (verbose) {
			System.out.println("Statistic(sequential): "
					+ loader.getStatistic()
					+ (searchFile != null ? ("on file " + searchFile) : ""));
		}
	}

	@Override
	public MatchRelation match(ContentBlock block) {
		String firstLine = getFirstLine(block);
		if (firstLine != null) {
			Date date = extractDate(firstLine);
			if (date != null) {
				return dateRange.compare(date);
			}
		}
		// start of file
		return MatchRelation.Less;
	}

	private Date extractDate(String firstLine) {
		Date date = timeParser.parse(firstLine);
		return date;
	}

	@Override
	public boolean isStartBlock(ContentBlock block) {
		// check block is start from time in first line
		String firstLine = getFirstLine(block);
		if (firstLine == null) {
			return false;
		}
		Date date = extractDate(firstLine);
		return date != null;
	}

	private String getFirstLine(ContentBlock block) {
		int eol = block.indexOf((byte) '\n');
		if (eol < 0) {
			return null;
		}
		byte[] line = new byte[eol];
		block.read(line, 0, eol);
		return new String(line);
	}

}
