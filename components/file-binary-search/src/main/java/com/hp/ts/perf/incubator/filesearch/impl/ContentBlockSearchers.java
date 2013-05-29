package com.hp.ts.perf.incubator.filesearch.impl;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.hp.ts.perf.incubator.filesearch.ContentBlock;
import com.hp.ts.perf.incubator.filesearch.ContentBlockMatchResult;
import com.hp.ts.perf.incubator.filesearch.ContentBlockMatcher;
import com.hp.ts.perf.incubator.filesearch.ContentBlockMatcher.MatchRelation;
import com.hp.ts.perf.incubator.filesearch.ContentBlockRandomAccess;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSearchStatistic.Statistic;
import com.hp.ts.perf.incubator.filesearch.ContentBlockSequentialAccess;

public class ContentBlockSearchers {

	public static ContentBlockMatchResult linearSearch(
			ContentBlockRandomAccess loader, ContentBlockMatcher matcher)
			throws IOException {
		ContentBlock minBlock = loader.locateBlock(0);
		long length = loader.length();
		if (length == 0) {
			return ContentBlockMatchResult.noContent();
		}
		ContentBlock maxBlock = loader.locateBlock(length - 1);
		Statistic stat = loader.getStatistic().matchStat();
		MatchRelation minRelation;
		stat.start();
		try {
			minRelation = matcher.match(minBlock);
		} finally {
			stat.incCount();
		}
		ContentBlockLinearSearcher.Direction direction = null;
		switch (minRelation) {
		case Exceed:
			// out of range (it is max value)
			return ContentBlockMatchResult.exceed(maxBlock);
		case Less:
			direction = ContentBlockLinearSearcher.Direction.Inside;
			break;
		case Infimum:
		case Inside:
		case Supremum:
			direction = ContentBlockLinearSearcher.Direction.Upper;
			break;
		}
		MatchRelation upperRelation;
		stat.start();
		try {
			upperRelation = matcher.match(maxBlock);
		} finally {
			stat.incCount();
		}
		switch (upperRelation) {
		case Less:
			// out of range (it is min value)
			return ContentBlockMatchResult.less(minBlock);
		case Exceed:
			// no need to set direction
			break;
		case Infimum:
		case Inside:
		case Supremum:
			if (direction == ContentBlockLinearSearcher.Direction.Inside) {
				direction = ContentBlockLinearSearcher.Direction.Lower;
				break;
			} else {
				// found it
				return ContentBlockMatchResult.inside(minBlock, maxBlock);
			}
		}
		ContentBlockLinearSearcher searcher = new ContentBlockLinearSearcher(
				minBlock, maxBlock, loader);
		if (!searcher.search(direction, matcher)) {
			// not found
			minBlock = searcher.getLowerBlock();
			maxBlock = searcher.getUpperBlock();
			return ContentBlockMatchResult.notfound(minBlock, maxBlock);
		} else {
			// found
			if (direction == ContentBlockLinearSearcher.Direction.Lower) {
				minBlock = searcher.getUpperBlock();
			} else if (direction == ContentBlockLinearSearcher.Direction.Upper) {
				maxBlock = searcher.getLowerBlock();
			} else {
				minBlock = searcher.getLowerBlock();
				maxBlock = searcher.getUpperBlock();
			}
			return ContentBlockMatchResult.inside(minBlock, maxBlock);
		}
	}

	public static Iterator<ContentBlock> sequentialSearch(
			final ContentBlockSequentialAccess blockAccess,
			final ContentBlockMatcher matcher) throws IOException {
		return new Iterator<ContentBlock>() {

			private ContentBlock block;

			private boolean noNext = false;

			@Override
			public void remove() {
				throw new UnsupportedOperationException(
						"Iterator<ContentBlock>.remove()");
			}

			@Override
			public ContentBlock next() {
				if (hasNext()) {
					ContentBlock cblock = block;
					block = null;
					return cblock;
				} else {
					throw new NoSuchElementException();
				}
			}

			@Override
			public boolean hasNext() {
				if (noNext) {
					return false;
				}
				if (block != null) {
					return true;
				}
				try {
					Statistic stat = blockAccess.getStatistic().matchStat();
					while ((block = blockAccess.nextBlock()) != null) {
						MatchRelation relation;
						stat.start();
						try {
							relation = matcher.match(block);
						} finally {
							stat.incCount();
						}
						switch (relation) {
						case Less:
							// ignore it
							block = null;
							break;
						case Infimum:
						case Inside:
						case Supremum:
							//
							return true;
						case Exceed:
							// ignore others
							noNext = true;
							block = null;
							return false;
						}
					}
				} catch (IOException e) {
					throw (ConcurrentModificationException) (new ConcurrentModificationException()
							.initCause(e));
				}
				noNext = true;
				return false;
			}
		};
	}

}
