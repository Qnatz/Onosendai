/*
 * Copyright 2013 Alex Hutter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.vaguehope.onosendai.provider.successwhale;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import com.vaguehope.onosendai.config.Account;
import com.vaguehope.onosendai.model.MetaType;
import com.vaguehope.onosendai.model.Tweet;
import com.vaguehope.onosendai.model.TweetBuilder;
import com.vaguehope.onosendai.model.TweetList;
import com.vaguehope.onosendai.provider.ServiceRef;

public class SuccessWhaleFeedXml implements ContentHandler {

	private final Account account;
	private final List<Tweet> tweets = new ArrayList<Tweet>();

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	public SuccessWhaleFeedXml (final Account account, final InputStream dataIs) throws SAXException {
		this.account = account;
		SAXParserFactory spf = SAXParserFactory.newInstance();
		SAXParser sp;
		try {
			sp = spf.newSAXParser();
			XMLReader xmlReader = sp.getXMLReader();
			xmlReader.setContentHandler(this);
			try {
				xmlReader.parse(new InputSource(dataIs));
			}
			catch (IOException e) {
				throw new SAXException(e);
			}
		}
		catch (ParserConfigurationException e) {
			throw new SAXException(e);
		}
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	public TweetList getTweets () {
		return new TweetList(this.tweets);
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	private final Stack<String> stack = new Stack<String>();
	private StringBuilder currentText;
	private final TweetBuilder currentItem = new TweetBuilder();
	private final TweetBuilder currentComment = new TweetBuilder();
	private boolean addThisItem = true;
	private String stashedFirstLinkTitle;
	private String stashedLinkUrl;
	private String stashedLinkExpandedUrl;
	private String stashedLinkTitle;
	private String stashedFetchedForUserid;
	private String stashedService;
	private String stashedUserName;
	private String stashedHashtagText;

	private final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZ");

	@Override
	public void startElement (final String uri, final String localName, final String qName, final Attributes attributes) throws SAXException {
		this.stack.push(localName);
		if (this.currentText == null || this.currentText.length() > 0) {
			this.currentText = new StringBuilder();
		}
	}

	@Override
	public void endElement (final String uri, final String localName, final String qName) throws SAXException {
		final String elementName = !localName.isEmpty() ? localName : qName;
		if (this.stack.size() == 3 && elementName.equals("item")) { // NOSONAR not a magic number.
			if (this.addThisItem) {
				this.currentItem.bodyIfAbsent(this.stashedFirstLinkTitle);
				this.currentItem.meta(MetaType.ACCOUNT, this.account.getId());
				this.currentItem.meta(MetaType.SERVICE, ServiceRef.createServiceMeta(this.stashedService, this.stashedFetchedForUserid));
				this.tweets.add(this.currentItem.build());
			}
			this.stashedFirstLinkTitle = null;
			this.stashedFetchedForUserid = null;
			this.stashedService = null;
			this.addThisItem = true;
		}
		else if (this.stack.size() == 4) { // NOSONAR not a magic number.
			if ("fetchedforuserid".equals(elementName)) {
				this.stashedFetchedForUserid = this.currentText.toString();
			}
			else if ("service".equals(elementName)) {
				this.stashedService = this.currentText.toString();
			}
		}
		else if (this.stack.size() == 5) { // NOSONAR not a magic number.
			if ("id".equals(elementName)) {
				this.currentItem.id(this.currentText.toString());
			}
			else if ("fromuser".equals(elementName)) {
				this.currentItem.username(this.currentText.toString());
			}
			else if ("fromusername".equals(elementName)) {
				this.currentItem.fullname(this.currentText.toString());
			}
			else if ("text".equals(elementName)) {
				this.currentItem.body(this.currentText.toString());
			}
			else if ("time".equals(elementName)) {
				final long millis = this.dateFormat.parseMillis(this.currentText.toString());
				this.currentItem.unitTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(millis));
			}
			else if ("fromuseravatar".equals(elementName)) {
				this.currentItem.avatarUrl(this.currentText.toString());
			}
			else if ("inreplytostatusid".equals(elementName) && this.currentText.length() > 0) {
				this.currentItem.meta(MetaType.INREPLYTO, this.currentText.toString());
			}
			else if ("retweetedbyuser".equals(elementName)) {
				final String v = this.currentText.toString();
				this.currentItem.meta(MetaType.MENTION, v, String.format("RT by @%s", v));
			}
		}
		else if (this.stack.size() == 6) { // NOSONAR not a magic number.
			if ("link".equals(elementName)) {
				if (this.stashedLinkExpandedUrl != null) {
					this.currentItem.meta(MetaType.URL, this.stashedLinkExpandedUrl, this.stashedLinkTitle);
				}
				else if (this.stashedLinkUrl != null) {
					this.currentItem.meta(MetaType.URL, this.stashedLinkUrl, this.stashedLinkTitle);
				}
				this.stashedLinkUrl = null;
				this.stashedLinkExpandedUrl = null;
				this.stashedLinkTitle = null;
			}
			if ("username".equals(elementName)) {
				this.currentItem.meta(MetaType.MENTION, this.stashedUserName);
				this.stashedUserName = null;
			}
			if ("hashtag".equals(elementName)) {
				this.currentItem.meta(MetaType.HASHTAG, this.stashedHashtagText);
				this.stashedHashtagText = null;
			}
			else if ("comment".equals(elementName)) {
				this.tweets.add(this.currentComment.build());
				this.addThisItem = false;
			}
		}
		else if (this.stack.size() == 7) { // NOSONAR not a magic number.
			if ("url".equals(elementName)) {
				this.stashedLinkUrl = this.currentText.toString();
			}
			else if ("expanded-url".equals(elementName)) {
				this.stashedLinkExpandedUrl = this.currentText.toString();
			}
			else if ("title".equals(elementName)) {
				this.stashedLinkTitle = this.currentText.toString();
				if (this.stashedFirstLinkTitle == null) this.stashedFirstLinkTitle = this.stashedLinkTitle;
			}
			else if ("preview".equals(elementName)) {
				this.currentItem.meta(MetaType.MEDIA, this.currentText.toString());
			}
			else if ("user".equals(elementName)) {
				this.stashedUserName = this.currentText.toString();
			}
			else if ("text".equals(elementName)) {
				this.stashedHashtagText = this.currentText.toString();
			}
			else if ("id".equals(elementName)) {
				this.currentComment.id(this.currentText.toString());
			}
			else if ("message".equals(elementName)) {
				this.currentComment.body(this.currentText.toString());
			}
			else if ("created-time".equals(elementName)) {
				final long millis = this.dateFormat.parseMillis(this.currentText.toString());
				this.currentComment.unitTimeSeconds(TimeUnit.MILLISECONDS.toSeconds(millis));
			}
		}
		else if (this.stack.size() == 8) { // NOSONAR not a magic number.
			if ("name".equals(elementName)) {
				this.currentComment.fullname(this.currentText.toString());
			}
			else if ("fromuseravatar".equals(elementName)) {
				this.currentComment.avatarUrl(this.currentText.toString());
			}
		}

		this.stack.pop();
	}

	@Override
	public void characters (final char[] ch, final int start, final int length) throws SAXException {
		this.currentText.append(ch, start, length);
	}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

	@Override
	public void endDocument () throws SAXException { /* UNUSED */}

	@Override
	public void endPrefixMapping (final String prefix) throws SAXException { /* UNUSED */}

	@Override
	public void ignorableWhitespace (final char[] ch, final int start, final int length) throws SAXException { /* UNUSED */}

	@Override
	public void processingInstruction (final String target, final String data) throws SAXException { /* UNUSED */}

	@Override
	public void setDocumentLocator (final Locator locator) { /* UNUSED */}

	@Override
	public void skippedEntity (final String name) throws SAXException { /* UNUSED */}

	@Override
	public void startDocument () throws SAXException { /* UNUSED */}

	@Override
	public void startPrefixMapping (final String prefix, final String uri) throws SAXException { /* UNUSED */}

//	- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
}
