package com.hp.ts.perf.filesearch.util;

import java.text.DateFormatSymbols;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class FastSimpleDateFormat extends SimpleDateFormat {

	private static final long serialVersionUID = 1682451132836658631L;

	private String lastText;
	private long lastTime;
	private TimeFieldsMask timeFieldsMask;
	private int fastCount = 0;

	private static class TimeFieldsMask {

		private static enum TimeField {
			YEAR(Calendar.YEAR, 1900, 2099), MONTH(Calendar.MONTH, 0, 11), DAY(
					Calendar.DAY_OF_MONTH, 1, 28), HOURS(Calendar.HOUR_OF_DAY,
					0, 23), MINUTES(Calendar.MINUTE, 0, 59), SECONDS(
					Calendar.SECOND, 0, 59), MILLISECONDS(Calendar.MILLISECOND,
					0, 999);

			private final int min;
			private final int max;
			private final int calField;
			private int size;

			private TimeField(int calField, int min, int max) {
				this.calField = calField;
				this.min = min;
				this.max = max;
				// set size
				int value = max;
				while (value > 0) {
					size++;
					value = value / 10;
				}
			}

			public void setCalendar(Calendar calendar, int value) {
				calendar.set(calField, value);
			}

			public int getCalendar(Calendar calendar) {
				return calendar.get(calField);
			}

			public int size() {
				return size;
			}

			public static String intToString(int value, int size) {
				char[] ca = new char[size];
				for (int i = ca.length - 1; i >= 0; i--) {
					ca[i] = (char) ('0' + (value % 10));
					value = value / 10;
				}
				return new String(ca);
			}

			public static int charArrayToInt(char[] charArray, int offset,
					int size) {
				int value = 0;
				for (int i = 0; i < size; i++) {
					char c = charArray[offset + i];
					value = value * 10 + (c - '0');
				}
				return value;
			}

			public int extractValue(char[] charArray, int offset) {
				int value = charArrayToInt(charArray, offset, size);
				// pay attention of month (based on 0)
				if (this == MONTH) {
					value--;
				}
				if (value > max || value < min) {
					// exceed limitation (or not safe value)
					return -1;
				} else {
					return value;
				}
			}

			public String toFixedString(int value) {
				// pay attention of month (based on 0)
				if (this == MONTH) {
					value++;
				}
				return intToString(value, size);
			}

		}

		private final TimeField[] slots;

		private static final TimeField[] allFields = TimeField.values();

		private final boolean found;

		private final String pattern;

		public static TimeFieldsMask checkCanUseFast(SimpleDateFormat format) {
			TimeFieldsMask longMask = new TimeFieldsMask(format, new int[] {
					1999, 11, 31, 23, 50, 48, 716 });
			TimeFieldsMask shortMask = new TimeFieldsMask(format, new int[] {
					2000, 0, 2, 3, 4, 5, 6 });
			if (longMask.found && longMask.equals(shortMask)) {
				return longMask;
			} else {
				return null;
			}
		}

		public TimeFieldsMask(SimpleDateFormat format, int[] testValue) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeZone(format.getTimeZone());
			int index = 0;
			for (TimeField timeField : allFields) {
				timeField.setCalendar(calendar, testValue[index++]);
			}
			index = 0;
			String testedText = format.format(calendar.getTime());
			slots = new TimeField[testedText.length()];
			boolean bFound = false;
			for (TimeField timeField : allFields) {
				int position = testedText.indexOf(timeField
						.toFixedString(testValue[index++]));
				// not found
				if (position < 0)
					continue;
				bFound = true;
				for (int i = 0, n = timeField.size(); i < n; i++) {
					slots[position + i] = timeField;
				}
			}
			found = bFound;
			pattern = format.toPattern();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(slots);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TimeFieldsMask other = (TimeFieldsMask) obj;
			if (!Arrays.equals(slots, other.slots))
				return false;
			return true;
		}

		public int update(Calendar calendar, String text, int offset,
				String lastText, long lastValue) {
			if (text.length() - offset < lastText.length()) {
				// not match
				return -1;
			}
			char[] ca = new char[lastText.length()];
			int index = 0;
			int slotIndex = -1;
			// check if text has space before only pattern has no space before
			boolean spaceCheck = !pattern.startsWith(" ");
			int spaceShift = 0;
			// check non-time field are same
			for (int n = ca.length; index < n; index++) {
				char c = text.charAt(index + offset);
				if (spaceCheck && c == ' ' && lastText.charAt(index) == ' ') {
					spaceShift++;
					continue;
				} else {
					spaceCheck = false;
				}
				if (slots[index - spaceShift] != null) {
					if (c < '0' || c > '9') {
						// not digital
						return -1;
					}
					if (slotIndex == -1) {
						slotIndex = index - spaceShift;
					}
					// time field, record it
					ca[index] = c;
				} else if (c != lastText.charAt(index)) {
					// not match
					return -1;
				}
			}
			// extract time field
			int[] preparedValue = new int[allFields.length];
			Arrays.fill(preparedValue, -1);
			while (slotIndex < slots.length) {
				TimeField timeField = slots[slotIndex];
				if (timeField != null) {
					int value = timeField.extractValue(ca, slotIndex
							+ spaceShift);
					if (value == -1) {
						// not safe value
						return -1;
					}
					preparedValue[timeField.ordinal()] = value;
					slotIndex += timeField.size();
				} else {
					slotIndex++;
				}
			}
			// not start set diff value
			if (calendar.getTimeInMillis() != lastValue) {
				calendar.setTimeInMillis(lastValue);
			}
			for (int i = 0, n = preparedValue.length; i < n; i++) {
				int value = preparedValue[i];
				if (value != -1) {
					TimeField timeField = allFields[i];
					int newValue = preparedValue[i];
					if (timeField.getCalendar(calendar) != newValue) {
						// set calendar value because it is changed
						timeField.setCalendar(calendar, newValue);
					}
				}
			}
			return index + offset;
		}

	}

	public FastSimpleDateFormat() {
		super();
		reset();
	}

	public FastSimpleDateFormat(String pattern, DateFormatSymbols formatSymbols) {
		super(pattern, formatSymbols);
		reset();
	}

	public FastSimpleDateFormat(String pattern, Locale locale) {
		super(pattern, locale);
		reset();
	}

	public FastSimpleDateFormat(String pattern) {
		super(pattern);
		reset();
	}

	@Override
	public StringBuffer format(Date date, StringBuffer toAppendTo,
			FieldPosition pos) {
		return super.format(date, toAppendTo, pos);
	}

	@Override
	public Date parse(String text, ParsePosition pos) {
		int startIndex = pos.getIndex();
		Date parsedDate = fastParse(text, pos);
		if (parsedDate == null) {
			parsedDate = super.parse(text, pos);
		}
		if (parsedDate != null) {
			saveParsedResult(text.substring(startIndex, pos.getIndex()),
					parsedDate.getTime());
		}
		return parsedDate;
	}

	private Date fastParse(String text, ParsePosition pos) {
		if (timeFieldsMask == null || lastText == null) {
			return null;
		}
		int result = timeFieldsMask.update(calendar, text, pos.getIndex(),
				lastText, lastTime);
		if (result == -1) {
			// not success
			return null;
		} else {
			fastCount++;
			pos.setIndex(result);
			return calendar.getTime();
		}
	}

	private void saveParsedResult(String dateText, long dateLong) {
		if (timeFieldsMask == null) {
			return;
		}
		lastText = dateText;
		lastTime = dateLong;
	}

	@Override
	public void applyPattern(String pattern) {
		String oldPattern = toPattern();
		super.applyPattern(pattern);
		if (!oldPattern.equals(toPattern())) {
			reset();
		}
	}

	@Override
	public void applyLocalizedPattern(String pattern) {
		String oldPattern = toPattern();
		super.applyLocalizedPattern(pattern);
		if (!oldPattern.equals(toPattern())) {
			reset();
		}
	}

	@Override
	public void setCalendar(Calendar newCalendar) {
		super.setCalendar(newCalendar);
		reset();
	}

	@Override
	public void setNumberFormat(NumberFormat newNumberFormat) {
		super.setNumberFormat(newNumberFormat);
		reset();
	}

	@Override
	public void setTimeZone(TimeZone zone) {
		super.setTimeZone(zone);
		reset();
	}

	@Override
	public void setLenient(boolean lenient) {
		super.setLenient(lenient);
		reset();
	}

	private void reset() {
		lastText = null;
		timeFieldsMask = null; // may used in inner call
		timeFieldsMask = TimeFieldsMask.checkCanUseFast(this);
	}

	protected int getFastCount() {
		return fastCount;
	}

}
