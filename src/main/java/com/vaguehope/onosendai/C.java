package com.vaguehope.onosendai;


public interface C {

	String TAG = "onosendai";

	String CONFIG_FILE_NAME = "deck.conf";

	int TWEET_FETCH_PAGE_SIZE = 20;
	int TWITTER_TIMELINE_MAX_FETCH = TWEET_FETCH_PAGE_SIZE * 3;
	int TWITTER_ME_MAX_FETCH = TWEET_FETCH_PAGE_SIZE;
	int TWITTER_MENTIONS_MAX_FETCH = TWEET_FETCH_PAGE_SIZE;
	int TWITTER_LIST_MAX_FETCH = TWEET_FETCH_PAGE_SIZE;

	//String DATA_TW_MAX_AGE_DAYS = "-7 days";
	String DATA_TW_MAX_COL_ENTRIES = "500";

	// Arbitrary values.
	int MIN_COLUMS_TO_USE_THREADPOOL = 2;
	int MAX_THREAD_POOL_SIZE = 3;

}
