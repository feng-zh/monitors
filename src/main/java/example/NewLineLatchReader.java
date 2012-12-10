package example;

import java.io.IOException;
import java.io.Reader;

public class NewLineLatchReader extends Reader {

	private Reader reader;

	private StringBuffer lineBuf = new StringBuffer();

	private int lineBufOffset = 0;

	private char[] readBuf = new char[1024];

	public NewLineLatchReader(Reader reader) {
		this.reader = reader;
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		// check if new line in it
		int checkOffset = lineBufOffset;
		while (lineBuf.indexOf("\n", checkOffset) < 0) {
			// no new line
			checkOffset = lineBuf.length();
			// check underline reader
			int readLen;
			if ((readLen = reader.read(readBuf)) != -1) {
				lineBuf.append(readBuf, 0, readLen);
			} else {
				// no data read till now
				return -1;
			}
		}
		// get data with new line
		int rLen = Math.min(lineBuf.length() - lineBufOffset, len);
		lineBuf.getChars(lineBufOffset, rLen + lineBufOffset, cbuf, off);
		lineBufOffset += rLen;
		// if no new line in not-loaded, trim loaded
		if (lineBuf.indexOf("\n", lineBufOffset) < 0) {
			lineBuf.delete(0, lineBufOffset);
			lineBufOffset = 0;
		}
		return rLen;
	}

	@Override
	public void close() throws IOException {
		reader.close();
	}

}
