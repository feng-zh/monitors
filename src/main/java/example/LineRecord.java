package example;

import java.io.Serializable;

public class LineRecord implements Serializable {

	private CharSequence line;

	private int lineNum;

	public CharSequence getLine() {
		return line;
	}

	public void setLine(CharSequence line) {
		this.line = line;
	}

	public int getLineNum() {
		return lineNum;
	}

	public void setLineNum(int lineNum) {
		this.lineNum = lineNum;
	}

}
