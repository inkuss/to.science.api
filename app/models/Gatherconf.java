/*
 * Copyright 2014 hbz NRW (http://www.hbz-nrw.de/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package models;

import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;

import java.util.List;
import java.util.Map.Entry;

import java.util.Hashtable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

import actions.Modify;
import helper.WebgatherUtils;

/**
 * @author Jan Schnasse
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gatherconf {

	@SuppressWarnings("javadoc")
	public enum Interval {
		annually, halfYearly, quarterly, monthly, weekly, daily, once
	};

	@SuppressWarnings("javadoc")
	public enum RobotsPolicy {
		classic, ignore, obey
	}

	@SuppressWarnings("javadoc")
	public enum CrawlerSelection {
		heritrix, wpull
	}

	@SuppressWarnings("javadoc")
	public enum QuotaUnitSelection {
		KB, MB, GB;
	}

	/**
	 * Enumeration für Browswer-IDs (user agents) The browser ID needs to be
	 * selectable in to.science.drupal/edoweb/php/crawlerForm.php I.e. if you add
	 * or change a browser id, also change that php file.
	 * 
	 * @author Ingolf Kuss, hbz-nrw.de
	 */
	@SuppressWarnings("javadoc")
	public enum AgentIdSelection {
		Undefined, Chrome, Edge, IE, Firefox, Safari, Googlebot, toscience;
	}

	/**
	 * HashTable für Browser-IDs (user agents)
	 * 
	 * @author Ingolf Kuss, hbz-nrw.de
	 */
	@SuppressWarnings("javadoc")
	public static Hashtable<AgentIdSelection, String> agentTable =
			new Hashtable<AgentIdSelection, String>() {
				{
					put(AgentIdSelection.Undefined, "\"InconspiciousWebBrowser/1.0\"");
					put(AgentIdSelection.Chrome,
							"\"Mozilla/5.0%20(Windows%20NT%206.1;%20Win64;%20x64)%20AppleWebKit/537.36%20(KHTML,%20like%20Gecko)%20Chrome/63.0.3239.132%20Safari/537.36\"");
					put(AgentIdSelection.Edge,
							"\"Mozilla/5.0%20(Windows%20NT%2010.0;%20Win64;%20x64)%20AppleWebKit/537.36%20(KHTML,%20like%20Gecko)%20Chrome/58.0.3029.110%20Safari/537.36%20Edge/16.16299\"");
					put(AgentIdSelection.IE,
							"\"Mozilla/5.0%20(compatible;%20MSIE%2010.0;%20Windows%20NT%206.1;%20WOW64;%20Trident/6.0;%20MASBJS)\"");
					put(AgentIdSelection.Firefox,
							"\"Mozilla/5.0%20(Windows%20NT%206.1;%20WOW64;%20rv:40.0)%20Gecko/20100101%20Firefox/40.1\"");
					put(AgentIdSelection.Safari,
							"\"Mozilla/5.0%20(Macintosh;%20Intel%20Mac%20OS%20X%2010_13_3)%20AppleWebKit/604.5.6%20(KHTML,%20like%20Gecko)%20Version/11.0.3%20Safari/604.5.6\"");
					put(AgentIdSelection.Googlebot,
							"\"Mozilla/5.0%20(compatible;%20Googlebot/2.1;%20+http://www.google.com/bot.html)\"");
					put(AgentIdSelection.toscience,
							"\"to.science%20(https://github.com/hbz/to.science.api;mailto:toscience@hbz-nrw.de)\"");
				}
			};

	String name;
	boolean active;
	String url;
	int httpResponseCode;
	boolean invalidUrl;
	String urlNew; // the new URL, yet to be confirmed
	ArrayList<String> domains;
	int deepness;
	RobotsPolicy robotsPolicy;
	Interval interval;
	CrawlerSelection crawlerSelection;
	QuotaUnitSelection quotaUnitSelection;
	AgentIdSelection agentIdSelection;
	ArrayList<String> urlsExcluded;
	Date startDate;
	String localDir;
	String openWaybackLink;
	String id;
	long maxCrawlSize;
	int waitSecBtRequests;
	boolean randomWait;
	int tries;
	int waitRetry;

	/**
	 * Create a new configuration for the webgatherer
	 */
	public Gatherconf() {
		url = null;
		httpResponseCode = 0;
		invalidUrl = false;
		urlNew = null;
		domains = new ArrayList<String>();
		active = true;
		deepness = -1;
		robotsPolicy = RobotsPolicy.ignore;
		interval = null;
		crawlerSelection = CrawlerSelection.wpull;
		quotaUnitSelection = null;
		agentIdSelection = AgentIdSelection.Chrome;
		urlsExcluded = new ArrayList<String>();
		startDate = null;
		localDir = null;
		name = null;
		openWaybackLink = null;
		id = null;
		maxCrawlSize = 0;
		waitSecBtRequests = 0;
		randomWait = true;
		tries = 5;
		waitRetry = 20;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * @return the url of the website
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url a url to harvest data from
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @param domain a domain that shall be included in the crawl
	 */
	public void addDomain(String domain) {
		this.domains.add(domain);
	}

	/**
	 * @return a list of domains to be included in the crawl
	 */
	public ArrayList<String> getDomains() {
		return domains;
	}

	/**
	 * @return number of levels
	 */
	public int getDeepness() {
		return deepness;
	}

	/**
	 * @param deepness the number of levels
	 */
	public void setDeepness(int deepness) {
		this.deepness = deepness;
	}

	/**
	 * @return the maxCrawlSize
	 */
	public long getMaxCrawlSize() {
		return maxCrawlSize;
	}

	/**
	 * @param maxCrawlSize the maxCrawlSize to set
	 */
	public void setMaxCrawlSize(long maxCrawlSize) {
		this.maxCrawlSize = maxCrawlSize;
	}

	/**
	 * @return define how robots.txt will be treated
	 */
	public RobotsPolicy getRobotsPolicy() {
		return robotsPolicy;
	}

	/**
	 * @param robotsPolicy
	 */
	public void setRobotsPolicy(RobotsPolicy robotsPolicy) {
		this.robotsPolicy = robotsPolicy;
	}

	/**
	 * @return a harvest interval
	 */
	public Interval getInterval() {
		return interval;
	}

	/**
	 * @param interval a harvest interval
	 */
	public void setInterval(Interval interval) {
		this.interval = interval;
	}

	/**
	 * @return a crawler selection
	 */
	public CrawlerSelection getCrawlerSelection() {
		return crawlerSelection;
	}

	/**
	 * @param crawlerSelection a crawler selection
	 */
	public void setCrawlerSelection(CrawlerSelection crawlerSelection) {
		this.crawlerSelection = crawlerSelection;
	}

	/**
	 * @return the quotaUnitSelection
	 */
	public QuotaUnitSelection getQuotaUnitSelection() {
		return quotaUnitSelection;
	}

	/**
	 * @param quotaUnitSelection the quotaUnitSelection to set
	 */
	public void setQuotaUnitSelection(QuotaUnitSelection quotaUnitSelection) {
		this.quotaUnitSelection = quotaUnitSelection;
	}

	/**
	 * @return the agentIdSelection
	 */
	public AgentIdSelection getAgentIdSelection() {
		return agentIdSelection;
	}

	/**
	 * @param agentIdSelection the agentIdSelection to set
	 */
	public void setAgentIdSelection(AgentIdSelection agentIdSelection) {
		this.agentIdSelection = agentIdSelection;
	}

	/**
	 * @param urlExcluded an URL or URI that shall be excluded in the crawl
	 */
	public void addUrlExcluded(String urlExcluded) {
		this.urlsExcluded.add(urlExcluded);
	}

	/**
	 * @return a list of URLs/URIs to be excluded in the crawl
	 */
	public ArrayList<String> getUrlsExcluded() {
		return urlsExcluded;
	}

	/**
	 * @return first harvest time
	 */
	public Date getStartDate() {
		return startDate;
	}

	/**
	 * @param startDate the time the url is to be first harvested
	 */
	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	/**
	 * @return ob die URL ungültig (auch: permanent umgezogen) ist
	 */
	public boolean getInvalidUrl() {
		return invalidUrl;
	}

	/**
	 * @param invalidUrl ob die URL ungültig (auch: permanent umgezogen) ist
	 */
	public void setInvalidUrl(boolean invalidUrl) {
		this.invalidUrl = invalidUrl;
	}

	/**
	 * @return the name will be used in heritrix as job name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name he name will be used in heritrix as job name
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return a localDir with information stored by heritrix
	 */
	public String getLocalDir() {
		return localDir;
	}

	/**
	 * @return the waitSecBtRequests
	 */
	public int getWaitSecBtRequests() {
		return waitSecBtRequests;
	}

	/**
	 * @param waitSecBtRequests the waitSecBtRequests to set
	 */
	public void setWaitSecBtRequests(int waitSecBtRequests) {
		this.waitSecBtRequests = waitSecBtRequests;
	}

	/**
	 * @return the randomWait
	 */
	public boolean isRandomWait() {
		return randomWait;
	}

	/**
	 * @param randomWait the randomWait to set
	 */
	public void setRandomWait(boolean randomWait) {
		this.randomWait = randomWait;
	}

	/**
	 * @return the tries
	 */
	public int getTries() {
		return tries;
	}

	/**
	 * @param tries the tries to set
	 */
	public void setTries(int tries) {
		this.tries = tries;
	}

	/**
	 * @return the waitRetry
	 */
	public int getWaitRetry() {
		return waitRetry;
	}

	/**
	 * @param waitRetry the waitRetry to set
	 */
	public void setWaitRetry(int waitRetry) {
		this.waitRetry = waitRetry;
	}

	/**
	 * @param localDir
	 */
	public void setLocalDir(String localDir) {
		this.localDir = localDir;
	}

	@Override
	public String toString() {
		ObjectMapper mapper = JsonUtil.mapper();
		StringWriter w = new StringWriter();
		try {
			mapper.writeValue(w, this);
		} catch (Exception e) {
			return super.toString();
		}
		return w.toString();
	}

	/**
	 * @param json a json representation
	 * @return a new Gatherconf build from json
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws IOException
	 */
	@JsonIgnore
	public static Gatherconf create(String json) throws IOException {
		return (Gatherconf) JsonUtil.mapper().readValue(json, Gatherconf.class);
	}

	/**
	 * Appropriate link to an openwayback instance to display the harvested warc
	 * 
	 * @param openWaybackLink
	 */
	public void setOpenWaybackLink(String openWaybackLink) {
		this.openWaybackLink = openWaybackLink;
	}

	/**
	 * @return allink to an openwayback instance that displays the harvested warc
	 */
	public String getOpenWaybackLink() {
		return openWaybackLink;
	}

	/**
	 * @return an internal id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id an internal id
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @param urlNew the new URL of the website (Location on HTTP Response Code
	 *          301)
	 */
	public void setUrlNew(String urlNew) {
		this.urlNew = urlNew;
	}

	/**
	 * @return the new URL of the website (Location on HTTP Response Code 301)
	 */
	public String getUrlNew() {
		return urlNew;
	}

	/**
	 * @return the HTTP Response Code of the URL of the website
	 */
	public int getHttpResponseCode() {
		return this.httpResponseCode;
	}

	/**
	 * @param httpResponseCode the new httpResponseCode
	 */
	public void setHttpResponseCode(int httpResponseCode) {
		this.httpResponseCode = httpResponseCode;
	}

	/**
	 * Stellt fest, ob die URL umgezogen ist.
	 * 
	 * @param node der Knoten für die Pid (resource, Website), an der die
	 *          Gatherconf hängt
	 * @return ob die URL der Webpage umgezogen ist
	 * @exception MalformedURLException
	 * @exception IOException
	 */
	public boolean hasUrlMoved(Node node)
			throws URISyntaxException, MalformedURLException, IOException {

		play.Logger.debug("Starting hasUrlMoved");
		if (invalidUrl) {
			return true;
		} // keine erneute Prüfung
		HttpURLConnection httpConnection = (HttpURLConnection) new URL(
				WebgatherUtils.convertUnicodeURLToAscii(url)).openConnection();
		httpConnection.setInstanceFollowRedirects(false);
		httpConnection.setRequestMethod("GET");
		httpConnection.connect();
		httpResponseCode = httpConnection.getResponseCode();
		play.Logger.debug("HTTP Response Code: " + httpResponseCode);
		if (httpResponseCode != 301) {
			return false;
		}
		setInvalidUrl(true);
		// ermiitelt die neue URL (falls bekannt)
		urlNew = null;
		for (Entry<String, List<String>> header : httpConnection.getHeaderFields()
				.entrySet()) {
			if (header.getKey() != null && header.getKey().equals("Location")) {
				urlNew = header.getValue().get(0);
			}
		}
		play.Logger.debug("urlNew: " + urlNew);
		httpConnection.disconnect();
		new Modify().updateConf(node, this.toString());
		return true;
	}

}
