package com.hp.ts.perf.incubator.filesearch;

public class ContentBlockMatchResult {

	public enum MatchStatus {
		Less, Inside, NotFound, Exceed, NoContent;
	}

	private MatchStatus matchStatus;

	private ContentBlock minBlock;

	private ContentBlock maxBlock;

	private ContentBlockMatchResult() {
	}

	public boolean isMatched() {
		return matchStatus == MatchStatus.Inside;
	}

	public MatchStatus getMatchStatus() {
		return matchStatus;
	}

	public ContentBlock getMinBlock() {
		return minBlock;
	}

	public ContentBlock getMaxBlock() {
		return maxBlock;
	}

	public static ContentBlockMatchResult exceed(ContentBlock block) {
		ContentBlockMatchResult result = new ContentBlockMatchResult();
		result.matchStatus = MatchStatus.Exceed;
		result.minBlock = block;
		return result;
	}

	public static ContentBlockMatchResult less(ContentBlock block) {
		ContentBlockMatchResult result = new ContentBlockMatchResult();
		result.matchStatus = MatchStatus.Less;
		result.maxBlock = block;
		return result;
	}

	public static ContentBlockMatchResult inside(ContentBlock lowerBlock,
			ContentBlock upperBlock) {
		ContentBlockMatchResult result = new ContentBlockMatchResult();
		result.matchStatus = MatchStatus.Inside;
		result.minBlock = lowerBlock;
		result.maxBlock = upperBlock;
		return result;
	}

	public static ContentBlockMatchResult notfound(ContentBlock leftBlock,
			ContentBlock rightBlock) {
		ContentBlockMatchResult result = new ContentBlockMatchResult();
		result.matchStatus = MatchStatus.NotFound;
		result.minBlock = leftBlock;
		result.maxBlock = rightBlock;
		return result;
	}

	@Override
	public String toString() {
		return String
				.format("ContentBlockMatchResult [matchStatus=%s, minBlock=%s, maxBlock=%s]",
						matchStatus, minBlock, maxBlock);
	}

	public static ContentBlockMatchResult noContent() {
		ContentBlockMatchResult result = new ContentBlockMatchResult();
		result.matchStatus = MatchStatus.NoContent;
		return result;
	}

}
