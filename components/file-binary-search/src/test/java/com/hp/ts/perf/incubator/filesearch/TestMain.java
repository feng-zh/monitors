package com.hp.ts.perf.incubator.filesearch;

import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import com.hp.ts.perf.incubator.filesearch.impl.ContentBlockSearchers;
import com.hp.ts.perf.incubator.filesearch.impl.LineBasedFileLoader;

public class TestMain implements ContentBlockMatcher, ContentBlockDetector {
	private String endString;
	private String startString;

	public TestMain(String string, String string2) {
		startString = string;
		endString = string2;
	}

	public static void main(String[] args) throws Exception {
		RandomAccessFile file = new RandomAccessFile(
				"src/test/resources/sample_errortrace.txt", "r");
		TestMain matcher = new TestMain("12/11/2012, 16:10:38",
				"12/11/2012, 16:37:53");
		ContentBlockRandomAccess loader = new LineBasedFileLoader(file, matcher);
		ContentBlockMatchResult result = ContentBlockSearchers.linearSearch(
				loader, matcher);
		System.out.println(result.toString());
		file.close();
	}

	@Override
	public MatchRelation match(ContentBlock block) {
		byte[] bytes = block.toBytes();
		String str;
		if (bytes.length >= endString.length()) {
			str = new String(bytes, 0, endString.length());
		} else {
			str = new String(bytes);
		}
		int startCompare = str.compareTo(startString);
		if (startCompare < 0) {
			return MatchRelation.Less;
		} else if (startCompare == 0) {
			return MatchRelation.Infimum;
		} else {
			int endCompare = str.compareTo(endString);
			if (endCompare < 0) {
				return MatchRelation.Inside;
			} else if (endCompare == 0) {
				return MatchRelation.Supremum;
			} else {
				return MatchRelation.Exceed;
			}
		}
	}

	private SimpleDateFormat format = new SimpleDateFormat(
			"MM/dd/yyyy, hh:mm:ss");

	@Override
	public boolean isStartBlock(ContentBlock block) {
		byte[] bytes = block.toBytes();
		String str;
		if (bytes.length >= endString.length()) {
			str = new String(bytes, 0, endString.length());
		} else {
			str = new String(bytes);
		}
		try {
			return format.parse(str) != null;
		} catch (ParseException e) {
			return false;
		}
	}
}
