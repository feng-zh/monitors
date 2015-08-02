package com.hp.ts.perf.filesearch.util;

import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;

public class DateTimeQuickParser {

	private static Set<String> availableFormat = new LinkedHashSet<String>();

	static {
		addDefaultDateFormat(((SimpleDateFormat) DateFormat
				.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM))
				.toPattern());
		addDefaultDateFormat("MM/dd/yyyy, HH:mm:ss");
		addDefaultDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
		addDefaultDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		addDefaultDateFormat("yyyy-MM-dd HH:mm:ss");
		addDefaultDateFormat("EEE MMM d HH:mm:ss z yyyy");
		addDefaultDateFormat("yyyy-MM-dd HH:mm");
		addDefaultDateFormat("MM/dd/yyyy, HH:mm");
		addDefaultDateFormat("yyyy-MM-dd");
		addDefaultDateFormat("MM/dd/yyyy");
	}

	private SimpleDateFormat definedFormat;
	// default time zone
	private TimeZone timezone = TimeZone.getTimeZone("GMT");
	private SimpleDateFormat currentFormat;

	public static void addDefaultDateFormat(String pattern)
			throws IllegalArgumentException, NullPointerException {
		// test format
		SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
		availableFormat.add(dateFormat.toPattern());
	}

	public void setCustomizedFormat(String customizedFormat) {
		if (customizedFormat != null) {
			definedFormat = newDateFormat(customizedFormat);
			definedFormat.setTimeZone(timezone);
		}
	}

	public Date parse(String content) {
		checkCurrentFormat(content);
		SimpleDateFormat currentFormat = getCurrentFormat();
		if (currentFormat == null) {
			return null;
		}
		Date parsedDate;
		for (int offset = 0; offset < content.length(); offset++) {
			ParsePosition pos = new ParsePosition(offset);
			parsedDate = parseDate(currentFormat, content, pos);
			if (parsedDate != null) {
				return parsedDate;
			}
		}
		return null;
	}

	protected Date parseDate(SimpleDateFormat format, String textFragement,
			ParsePosition pos) {
		Date parsedDate = format.parse(textFragement, pos);
		return parsedDate;
	}

	private void checkCurrentFormat(String content) {
		SimpleDateFormat currentFormat = getCurrentFormat();
		if (currentFormat != null)
			return;
		synchronized (this) {
			String[] formats;
			synchronized (DateTimeQuickParser.class) {
				formats = availableFormat.toArray(new String[availableFormat
						.size()]);
			}
			for (int offset = 0; offset < content.length(); offset++) {
				for (String format : formats) {
					SimpleDateFormat dateFormat = newDateFormat(format);
					ParsePosition pos = new ParsePosition(offset);
					if (parseDate(dateFormat, content, pos) != null) {
						dateFormat.setTimeZone(timezone);
						setCurrentFormat(dateFormat);
						return;
					}
				}
			}
		}
	}

	private SimpleDateFormat getCurrentFormat() {
		return definedFormat != null ? definedFormat : currentFormat;
	}

	private void setCurrentFormat(SimpleDateFormat currentFormat) {
		this.currentFormat = currentFormat;
	}

	private static SimpleDateFormat newDateFormat(String pattern) {
		return new FastSimpleDateFormat(pattern);
	}

}
