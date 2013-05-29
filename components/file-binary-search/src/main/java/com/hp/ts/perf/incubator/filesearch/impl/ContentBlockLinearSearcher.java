package com.hp.ts.perf.incubator.filesearch.impl;

import java.io.IOException;

import com.hp.ts.perf.incubator.filesearch.ContentBlock;
import com.hp.ts.perf.incubator.filesearch.ContentBlockMatcher;
import com.hp.ts.perf.incubator.filesearch.ContentBlockMatcher.MatchRelation;
import com.hp.ts.perf.incubator.filesearch.ContentBlockRandomAccess;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSearchMeasurable;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSearchStatistic;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSearchStatistic.Statistic;

class ContentBlockLinearSearcher implements ContentBlockSearchMeasurable {

	private ContentBlock lowerBlock;

	private ContentBlock upperBlock;

	// inside range
	// private boolean innerMatch = true;

	// match exactly
	// private boolean closedMatch = true;

	private ContentBlockRandomAccess loader;

	public enum Direction {
		Lower, Inside, Upper;
	}

	public ContentBlockLinearSearcher(ContentBlock minBlock,
			ContentBlock maxBlock, ContentBlockRandomAccess loader) {
		lowerBlock = minBlock;
		upperBlock = maxBlock;
		this.loader = loader;
	}

	public ContentBlock getLowerBlock() {
		return lowerBlock;
	}

	public ContentBlock getUpperBlock() {
		return upperBlock;
	}

	// ex: check range 0, 10
	public boolean search(Direction direction, ContentBlockMatcher matcher)
			throws IOException {
		Statistic stat = getStatistic().matchStat();
		while (true) {
			long mid = calMiddle(matcher);
			if (mid == -1) {
				// no need to search
				if (direction == Direction.Inside) {
					// inside means not found finally
					return false;
				} else {
					return true;
				}
			}
			ContentBlock testBlock = loader.locateBlock(mid);
			MatchRelation relation;
			stat.start();
			try {
				relation = matcher.match(testBlock);
			} finally {
				stat.incCount();
			}
			if (direction == Direction.Lower) {
				searchLower(matcher, testBlock, relation);
			} else if (direction == Direction.Upper) {
				searchUpper(matcher, testBlock, relation);
			} else {
				if (searchInside(matcher, testBlock, relation)) {
					return true;
				}
			}
		}
	}

	// -2, -1, 0, 3, 5, 10, 90, 100
	// [0, 10]
	private boolean searchInside(ContentBlockMatcher matcher,
			ContentBlock testBlock, MatchRelation relation) throws IOException {
		boolean finished = false;
		switch (relation) {
		case Less:
			lowerBlock = testBlock;
			break;
		case Exceed:
			upperBlock = testBlock;
			break;
		case Infimum:
		case Inside:
		case Supremum:
			// split two parts for search
			ContentBlockLinearSearcher lowerBlockSearch = new ContentBlockLinearSearcher(
					lowerBlock, testBlock, loader);
			lowerBlockSearch.search(Direction.Lower, matcher);
			lowerBlock = lowerBlockSearch.getUpperBlock();
			ContentBlockLinearSearcher upperBlockSearch = new ContentBlockLinearSearcher(
					testBlock, upperBlock, loader);
			upperBlockSearch.search(Direction.Upper, matcher);
			upperBlock = upperBlockSearch.getLowerBlock();
			finished = true;
			break;
		}
		return finished;
	}

	// 3, 5, 10, 90, 100
	// [0, 10]
	private void searchUpper(ContentBlockMatcher matcher,
			ContentBlock testBlock, MatchRelation relation) {
		switch (relation) {
		case Less:
			throw new IllegalStateException("lower search should not exceed");
		case Infimum:
		case Inside:
		case Supremum:// check same more
			lowerBlock = testBlock;
			break;
		case Exceed:
			upperBlock = testBlock;
		}
	}

	// -2, -1, 1, 3, 5
	// [0, 10]
	private void searchLower(ContentBlockMatcher matcher,
			ContentBlock testBlock, MatchRelation relation) {
		switch (relation) {
		case Less:
			lowerBlock = testBlock;
			break;
		case Infimum: // check same more
		case Inside:
		case Supremum:
			upperBlock = testBlock;
			break;
		case Exceed:
			throw new IllegalStateException("lower search should not exceed");
		}
	}

	private long calMiddle(ContentBlockMatcher matcher) {
		long lEnd = lowerBlock.getEnd();
		long uStart = upperBlock.getStart();
		long middle = (lEnd + uStart) >>> 1;
		if (middle < lEnd || middle >= uStart) {
			// no need to found
			return -1;
		} else {
			return middle;
		}
	}

	@Override
	public ContentBlockSearchStatistic getStatistic() {
		return loader.getStatistic();
	}
}
