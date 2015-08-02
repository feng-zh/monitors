package com.hp.ts.perf.filesearch.util;

import com.hp.ts.perf.filesearch.ContentBlockMatcher.MatchRelation;

public class ComparableRange<T extends Comparable<T>> {

	private T start;

	private T end;

	public ComparableRange(T start, T end) {
		this.start = start;
		this.end = end;
	}

	public MatchRelation compare(T data) {
		int res;
		if (start != null) {
			res = start.compareTo(data);
		} else {
			res = 1;
		}
		if (res > 0) {
			return MatchRelation.Less;
		} else if (res == 0) {
			return MatchRelation.Infimum;
		} else {
			if (end != null) {
				res = end.compareTo(data);
			}
			if (res > 0) {
				return MatchRelation.Inside;
			} else if (res == 0) {
				return MatchRelation.Supremum;
			} else {
				return MatchRelation.Exceed;
			}
		}
	}

	@Override
	public String toString() {
		if (start == null) {
			if (end == null) {
				return "No Range";
			} else {
				return "Range: <= " + end;
			}
		} else {
			if (start == null) {
				return "Range: >= " + start;
			} else {
				return "Range [" + start + ", " + end + "]";
			}
		}
	}

}
