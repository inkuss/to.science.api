/*
* Copyright 2025 hbz NRW(http://www.hbz-nrw.de/)
*
* Licensed under the Apache License,Version 2.0(the"License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,software
* distributed under the License is distributed on an"AS IS"BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package helper;

import java.io.File;
import java.util.ArrayList;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static archive.fedora.Vocabulary.*;
import actions.Modify;
import models.CrawlerModel;
import models.Gatherconf;
import models.Globals;
import models.Node;
import play.Play;

/**
 * a class to implement a wpull crawl
 * 
 * @author Ingolf Kuss
 *
 */
public class BtrixWebclient extends CrawlerModel {

	/* Browsertrix spezifische Variablen */
	private CloseableHttpClient httpClient = null;
	private HttpEntityEnclosingRequestBase entityEnclosingRequest = null;
	private HttpRequestBase request = null;
	private CloseableHttpResponse response = null;
	private ObjectMapper objectMapper = new ObjectMapper();
	private String bearerToken = null;
	private String scopeType = null;
	private String btrixWorkflowId = null;
	private String crawlId = null;

	/*
	 * Authorisierung und Organisation (Mandant) für Browsertrix
	 */
	final static String btrix_api_url =
			Play.application().configuration().getString("regal-api.btrix.apiUrl");
	final static String btrix_admin_username = Play.application().configuration()
			.getString("regal-api.btrix.adminUsername");
	final static String btrix_admin_password = Play.application().configuration()
			.getString("regal-api.btrix.adminPassword");
	final static String btrix_org_name =
			Play.application().configuration().getString("regal-api.btrix.orgName");
	final static String btrix_orgid =
			Play.application().configuration().getString("regal-api.btrix.orgId");
	/*
	 * Standardwerte für Browsertrix-Crawls
	 */
	final static int btrix_std_max_depth_in_scope =
			Integer.parseInt(Play.application().configuration()
					.getString("regal-api.btrix.stdMaxDepthInScope"));

	/**
	 * (Leerer) Konstruktor für den Browsertrix Webclient
	 * 
	 * Dieser Konstruktor wird benötigt, um Aufrufe an Browsertrix zu ermöglichen,
	 * die noch nicht auf ein toscience-Objekt bezogen sind.
	 */
	public BtrixWebclient() {
		super();
		/**
		 * Das Arbeitsverzeichnis von Browsertrix-Crawls für den CDN-Precrawl ist
		 * jobDir. jobDir sollte ein lokales Verzeichnis sein.
		 */
		this.setJobDir(
				Play.application().configuration().getString("regal-api.btrix.jobDir"));
		/**
		 * Im Verzeichnis outDir liegen die fertigen Crawls. Von hier aus werden die
		 * Crawls direkt von Wayback indexiert.
		 */
		this.setOutDir(
				Play.application().configuration().getString("regal-api.btrix.outDir"));
		try {
			getBearerToken();
		} catch (Exception e) {
			WebgatherLogger
					.error("Browsertrix-Webclient kann nicht angelegt werden !");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Konstruktor zu Browsertrix Crawler Workflow
	 * 
	 * @param node der Knoten der Website, zu der ein neuer Crawl gestartet werden
	 *          soll.
	 * @param conf the crawler configuration for the website
	 */
	public BtrixWebclient(Node node, Gatherconf conf) {
		super(node, conf);
		/**
		 * Das Arbeitsverzeichnis von Browsertrix-Crawls für den CDN-Precrawl ist
		 * jobDir. jobDir sollte ein lokales Verzeichnis sein.
		 */
		this.setJobDir(
				Play.application().configuration().getString("regal-api.btrix.jobDir"));
		/**
		 * Im Verzeichnis outDir liegen die fertigen Browsertrox-Crawls. Von hier
		 * aus werden die Crawls direkt von Wayback indexiert.
		 */
		this.setOutDir(
				Play.application().configuration().getString("regal-api.btrix.outDir"));
		this.setCrawlDir(new File(
				this.getJobDir() + "/" + conf.getName() + "/" + getDatetime()));
		this.setResultDir(new File(
				this.getOutDir() + "/" + conf.getName() + "/" + getDatetime()));
		this.setCdxFile(new File(this.getOutDir() + "/" + conf.getName() + "/WEB-"
				+ getHost() + ".cdx"));
		try {
			getBearerToken();
			if (conf.getBtrixWorkflowId() != null) {
				this.btrixWorkflowId = conf.getBtrixWorkflowId();
				WebgatherLogger.debug("btrixWorkflowId: " + btrixWorkflowId);
			}
			/*
			 * Wenn es noch keine Worfkflow ID in der conf gibt, wird jetzt eine
			 * angelegt. Ansonsten wird ein Update ("Patch") gemacht.
			 */
			updateCrawlerConfig();
		} catch (Exception e) {
			WebgatherLogger.error("Browsertrix-Workflow für PID " + node.getPid()
					+ " URL " + conf.getUrl() + " kann nicht angelegt werden !");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Getter für BtrixApiUrl
	 * 
	 * @return BtrixApiUrl
	 */
	public String getBtrixApiUrl() {
		return btrix_api_url;
	}

	/**
	 * Getter für BtrixOrgId
	 * 
	 * @return BtrixOgrId
	 */
	public String getBtrixOrgId() {
		return btrix_orgid;
	}

	/**
	 * Getter für Bearer Token
	 * 
	 * @return Bearer Token
	 */
	public String exportBearerToken() {
		return bearerToken;
	}

	/**
	 * Setter für BtrixWorkflowId
	 * 
	 * @param myWorkflowId Eine Browsertrix-WorkflowId (= cid in der
	 *          Browsertrix-API-Doc https://docs.browsertrix.com/api/)
	 */
	public void setBtrixWorkflowId(String myWorkflowId) {
		this.btrixWorkflowId = myWorkflowId;
	}

	/**
	 * Getter für BtrixWorkflowId
	 * 
	 * @return Btrix WorkflowId
	 */
	public String getBtrixWorkflowId() {
		return btrixWorkflowId;
	}

	/**
	 * Setter für CrawlId
	 * 
	 * @param mycrawlid Eine Browsertrix crawl_id (siehe Browsertrix-API-Doc
	 *          https://docs.browsertrix.com/api/)
	 */
	public void setCrawlId(String mycrawlid) {
		this.crawlId = mycrawlid;
	}

	/**
	 * Getter für CrawlId
	 * 
	 * @return eine Browsertrix crawl_id
	 */
	public String getCrawlId() {
		return crawlId;
	}

	private void getBearerToken() {
		try {
			httpClient = HttpClients.createDefault();
			entityEnclosingRequest = new HttpPost(btrix_api_url + "/auth/jwt/login");
			WebgatherLogger.debug("btrix_api_url " + btrix_api_url);
			WebgatherLogger.debug("btrix_admin_username " + btrix_admin_username);
			// WebgatherLogger.debug("btrix_admin_password " + btrix_admin_password);
			entityEnclosingRequest.addHeader("Content-Type",
					"application/x-www-form-urlencoded");
			entityEnclosingRequest
					.setEntity(new StringEntity("username=" + btrix_admin_username
							+ "&password=" + btrix_admin_password + "&grant_type=password"));
			entityEnclosingRequest.addHeader("Accept", "application/json");
			response = httpClient.execute(entityEnclosingRequest);
			if (response.getStatusLine().getStatusCode() == 200) {
				String tokenResponseJson = EntityUtils.toString(response.getEntity());
				JsonNode tokenJsonNode = objectMapper.readTree(tokenResponseJson);
				this.bearerToken = tokenJsonNode.get("access_token").asText();
				WebgatherLogger.debug("Got bearer Token " + this.bearerToken);
			} else {
				throw new RuntimeException("Status-Code von /auth/jwt/login: "
						+ response.getStatusLine().getStatusCode());
			}
		} catch (Exception e) {
			setMsg("Bearer-Token für Browsertrix-Workflow für PID "
					+ getNode().getPid() + " kann nicht geholt werden!");
			WebgatherLogger.error(getMsg(), e.toString());
			throw new RuntimeException(e);
		} finally {
			try {
				httpClient.close();
				response.close();
			} catch (Exception e) {
				WebgatherLogger.warn("httpClient kann nicht geschlossen werden.",
						e.toString());
			}
		}
	}

	/**
	 * Diese Methode führt einen GET-Request auf den Browsertrix-Endpoint Get
	 * Crawl Configs durch.
	 * 
	 * API-Doc: https://docs.browsertrix.com/api/#tag/crawlconfigs/operation/
	 * get_crawl_configs_api_orgs__oid__crawlconfigs_get
	 * 
	 * @param queryString ein queryString für die Anfrage
	 * @return a JSON Object with the found Crawl Configs
	 */
	public JSONObject getCrawlConfigs(String queryString) {
		try {
			httpClient = HttpClientBuilder.create().build();
			request = new HttpGet(btrix_api_url + "/orgs/" + btrix_orgid
					+ "/crawlconfigs?" + queryString);
			WebgatherLogger.debug("request = " + request.toString());
			request.addHeader("Authorization", "Bearer " + this.bearerToken);
			request.addHeader("Accept", "application/json");
			response = httpClient.execute(request);
			String responseJson = getResponseJson(response);
			WebgatherLogger.debug("received response: " + responseJson);
			// JSON ausparsen
			JSONObject responseJsonObject = new JSONObject(responseJson);
			int total = responseJsonObject.getInt("total");
			WebgatherLogger.debug("Found a number of " + total + " item(s).");
			if (total != 1) {
				throw new RuntimeException(
						"Did not find exactly one Crawl Config for queryString "
								+ queryString + " !");
			}
			return responseJsonObject;

		} catch (Exception e) {
			setMsg("Could not get Crawl Configs for queryString " + queryString);
			WebgatherLogger.error(getMsg(), e.getMessage());
			throw new RuntimeException(e);
		} finally {
			try {
				httpClient.close();
				response.close();
			} catch (Exception e) {
				WebgatherLogger.warn("httpClient kann nicht geschlossen werden.",
						e.toString());
			}
		}
	}

	/**
	 * Diese Methode führt einen GET-Request auf den Browsertrix-Endpoint Get
	 * Crawl Config Out durch. Dabei wird die Crawler-Konfiguration zu einem
	 * bestimmten Workflow ("Webpage") geholt. Die cid des Workflows muss in der
	 * BtrixWorkflowId stehen.
	 * 
	 * API-Doc: https://docs.browsertrix.com/api/#tag/crawlconfigs/operation/
	 * get_crawl_configs_api_orgs__oid__crawlconfigs_get
	 * 
	 * @return a JSON Object with the found Crawl Config
	 */
	public JSONObject getCrawlConfigOut() {
		try {
			httpClient = HttpClientBuilder.create().build();
			request = new HttpGet(btrix_api_url + "/orgs/" + btrix_orgid
					+ "/crawlconfigs/" + this.btrixWorkflowId);
			WebgatherLogger.debug("request = " + request.toString());
			request.addHeader("Authorization", "Bearer " + this.bearerToken);
			request.addHeader("Accept", "application/json");
			response = httpClient.execute(request);
			String responseJson = getResponseJson(response);
			WebgatherLogger.debug("received response: " + responseJson);
			JSONObject responseJsonObject = new JSONObject(responseJson);
			return responseJsonObject;
		} catch (Exception e) {
			setMsg("Could not get Crawl Config for WorkflowId " + btrixWorkflowId);
			WebgatherLogger.error(getMsg(), e.getMessage());
			throw new RuntimeException(e);
		} finally {
			try {
				httpClient.close();
				response.close();
			} catch (Exception e) {
				WebgatherLogger.warn("httpClient kann nicht geschlossen werden.",
						e.toString());
			}
		}
	}

	/**
	 * Diese Methode führt einen GET-Request auf den Browsertrix-Endpoint Get
	 * Crawl Out durch. Dabei wird ein JSON-Objekt geholt, das einen bestimmten
	 * Crawl ("Webschnitt") beschreibt. Die crawl_id muss in der Klassenvariable
	 * crawlId stehen.
	 * 
	 * API-Doc: https://docs.browsertrix.com/api/#tag/crawls/operation/
	 * get_crawl_out_api_orgs__oid__crawls__crawl_id__replay_json_get
	 * 
	 * @return a JSON Object describing a Browsertrix Crawl
	 */
	public JSONObject getCrawlOut() {
		try {
			httpClient = HttpClientBuilder.create().build();
			request = new HttpGet(btrix_api_url + "/orgs/" + btrix_orgid + "/crawls/"
					+ this.crawlId + "/replay.json");
			WebgatherLogger.debug("request = " + request.toString());
			request.addHeader("Authorization", "Bearer " + this.bearerToken);
			request.addHeader("Accept", "application/json");
			response = httpClient.execute(request);
			String responseJson = getResponseJson(response);
			WebgatherLogger.debug("received response: " + responseJson);
			JSONObject responseJsonObject = new JSONObject(responseJson);
			return responseJsonObject;
		} catch (Exception e) {
			setMsg("Could not get Crawl Out for crawlId " + crawlId);
			WebgatherLogger.error(getMsg(), e.getMessage());
			throw new RuntimeException(e);
		} finally {
			try {
				httpClient.close();
				response.close();
			} catch (Exception e) {
				WebgatherLogger.warn("httpClient kann nicht geschlossen werden.",
						e.toString());
			}
		}
	}

	private void updateCrawlerConfig() {
		try {
			httpClient = HttpClientBuilder.create().build();
			if (this.btrixWorkflowId == null) {
				entityEnclosingRequest = new HttpPost(
						btrix_api_url + "/orgs/" + btrix_orgid + "/crawlconfigs/");
			} else {
				entityEnclosingRequest = new HttpPatch(btrix_api_url + "/orgs/"
						+ btrix_orgid + "/crawlconfigs/" + btrixWorkflowId);
			}
			WebgatherLogger.debug("btrix_api_url " + btrix_api_url);
			WebgatherLogger.debug("btrix_orgid " + btrix_orgid);
			WebgatherLogger.debug("request = " + entityEnclosingRequest.toString());
			entityEnclosingRequest.addHeader("Authorization",
					"Bearer " + this.bearerToken);
			entityEnclosingRequest.addHeader("Content-Type", "application/json");
			String jsonBody = createJsonBody();
			WebgatherLogger.debug("jsonBody=" + jsonBody);
			entityEnclosingRequest.setEntity(new StringEntity(jsonBody, "UTF-8"));
			entityEnclosingRequest.addHeader("Accept", "application/json");
			response = httpClient.execute(entityEnclosingRequest);
			String responseJson = getResponseJson(response);
			WebgatherLogger.debug("received response: " + responseJson);
			// JSON ausparsen
			JSONObject responseJsonObject = new JSONObject(responseJson);
			if (this.btrixWorkflowId == null) {
				this.btrixWorkflowId = responseJsonObject.getString("id");
				WebgatherLogger
						.debug("Crawler Workflow angelegt mit btrix_workflow_id: "
								+ btrixWorkflowId);
				/**
				 * hier werden die ersten 12 Stellen der Crawler Workflow ID (cid) als
				 * "description" hinterlegt. Dies geschieht für den späteren Abgleich
				 * mit den crawl_ids. Diese enthalten nur die ersten 12 Ziffern der
				 * Workflow IDs.
				 */
				Thread.sleep(10000);
				entityEnclosingRequest = new HttpPatch(btrix_api_url + "/orgs/"
						+ btrix_orgid + "/crawlconfigs/" + btrixWorkflowId);
				entityEnclosingRequest.addHeader("Authorization",
						"Bearer " + this.bearerToken);
				entityEnclosingRequest.addHeader("Content-Type", "application/json");
				JSONObject data = new JSONObject(jsonBody);
				data.put("description", btrixWorkflowId.substring(0, 12));
				entityEnclosingRequest
						.setEntity(new StringEntity(data.toString(), "UTF-8"));
				entityEnclosingRequest.addHeader("Accept", "application/json");
				response = httpClient.execute(entityEnclosingRequest);
				responseJson = getResponseJson(response);
				WebgatherLogger.debug("received response from update wit description "
						+ btrixWorkflowId.substring(0, 12) + ": " + responseJson);
				/* Übernahme der WorkflowId in die toscience Crawler Conf */
				getConf().setBtrixWorkflowId(btrixWorkflowId);
				setMsg(new Modify().updateConf(getNode(), getConf().toString()));
				WebgatherLogger.info(getMsg());
			} else {
				WebgatherLogger.debug("Crawler Workflow mit btrix_workflow_id "
						+ btrixWorkflowId + " wurde aktualisiert.");
			}
		} catch (Exception e) {
			setMsg("Browsertrix Crawler Config für PID " + getNode().getPid()
					+ " kann nicht gesendet werden!");
			WebgatherLogger.error(getMsg(), e.getMessage());
			throw new RuntimeException(e);
		} finally {
			try {
				httpClient.close();
				response.close();
			} catch (Exception e) {
				WebgatherLogger.warn("httpClient kann nicht geschlossen werden.",
						e.toString());
			}
		}
	}

	private String createJsonBody() {
		JSONObject data = new JSONObject();
		Gatherconf conf = getConf();
		try {
			// Name oder Titel der Site
			data.put("name", conf.getName());
			String md = getNode().getMetadata(toscience);
			if (md != null) {
				JSONObject jo = new JSONObject(md);
				if (jo.has("title")) {
					// Hole Titel aus den toscience-Metadaten
					data.put("name", jo.getJSONArray("title").get(0).toString());
				}
			}
			data.put("inactive", !conf.isActive());
			// data.put("description", conf.getNotices());
			JSONArray tags = new JSONArray();
			tags.put(conf.getName());
			data.put("tags", tags);
			// maximale Crawlgröße in Byte
			data.put("maxCrawlSize", conf.getMaxCrawlSize());
			if (conf.getMaxCrawlSize() > 0) {
				switch (conf.getQuotaUnitSelection()) {
				case KB:
					data.put("maxCrawlSize", conf.getMaxCrawlSize() * 1000);
					break;
				case MB:
					data.put("maxCrawlSize", conf.getMaxCrawlSize() * 1000000);
					break;
				case GB:
					data.put("maxCrawlSize", conf.getMaxCrawlSize() * 1000000000);
					break;
				default:
					// standardmäßig wird Kilobyte angenommen
					data.put("maxCrawlSize", conf.getMaxCrawlSize() * 1000);
					break;
				}
			}
			// Und jetzt eine Config aufbauen:
			JSONObject config = new JSONObject();
			switch (conf.getCrawlSubdomains()) {
			case hostnames:
				this.scopeType = "host";
				break;
			case domains:
				this.scopeType = "domain";
				break;
			default:
				// standardmäßig wird die Domain ohne Subdomains eingesammelt
				this.scopeType = "host";
				break;
			}
			JSONArray seeds = new JSONArray();
			JSONObject seed =
					createSeed(this.getUrlAscii(), this.scopeType, conf.getDeepness());
			seeds.put(seed);
			/*
			 * zu inkludierende (zusätzliche) Domains. Diese erhalten jeweils ein
			 * eigenes "Seed" und zusätzlich einen Eintrag im Array "include".
			 */
			JSONArray include = new JSONArray();
			for (String domain : conf.getDomains()) {
				include.put(domain);
				seeds.put(createSeed(domain, this.scopeType, conf.getDeepness()));
			}
			config.put("seeds", seeds);
			config.put("scopeType", this.scopeType);
			config.put("include", include);
			/*
			 * Exclusions = auszuschließende Bereiche
			 */
			JSONArray exclude = new JSONArray();
			for (String urlExcluded : conf.getUrlsExcluded()) {
				exclude.put(".*" + urlExcluded.trim());
			}

			config.put("exclude", exclude);
			config.put("depth", conf.getDeepness());
			config.put("extraHops", 1);
			config.put("lang", "de");
			config.put("blockAds", true);
			// Limits amount of time to wait for a page to load; in Sekunden
			config.put("pageLoadTimeout", conf.getWaitRetry());
			// Delay Before Next Page; in Sekunden
			// config.put("pageExtraDelay", conf.getWaitSecBtRequests());
			// Anpassung an LAV Settings "delay after page load" KS 07.07.2026 für
			// TOS-1369
			config.put("postLoadedDelay", conf.getWaitSecBtRequests());
			config.put("useSitemap", true);
			// KS Anpassung an LAV:
			config.put("behaviors", "autoscroll,autoclick,autoplay,autofetch");
			config.put("userAgent", Gatherconf.agentTable
					.get(conf.getAgentIdSelection()).replaceAll("%20", " "));
			data.put("config", config);
		} catch (JSONException e) {
			setMsg("Crawlerconf JSON (JsonBody) für PID " + getNode().getPid()
					+ " kann nicht gebaut werden!");
			WebgatherLogger.error(getMsg(), e.getMessage());
		}
		return data.toString();
	} // ENDE createJsonBody()

	private JSONObject createSeed(String url, String seedScopeType, int depth) {
		JSONObject seed = new JSONObject();
		try {
			seed.put("url", url);
			seed.put("scopeType", seedScopeType);
			int actualDepth = depth;
			if (depth <= 0) {
				/*
				 * Vorbelegung "Max Depth in Scope" (maximale Verzeichnistiefe) mit
				 * Standardwert
				 */
				actualDepth = btrix_std_max_depth_in_scope;
			}
			seed.put("depth", actualDepth);
			/* one hop out -- the crawler will visit pages one link away. */
			seed.put("extraHops", 1);
		} catch (JSONException e) {
			setMsg("Seed with url " + url + " could not be created!");
			WebgatherLogger.warn(getMsg(), e.getMessage());
		}
		return seed;
	}

	/**
	 * Erzeugt einen neuen Browsertrix-Crawler-Job
	 */
	@Override
	public void createCrawl() {
		super.createCrawl();
	}

	/**
	 * Ruft den CDN-Gatherer für diese Website auf, außerdem Browsertrix für den
	 * Hauptcrawl
	 */
	public void startCrawl() {
		try {
			BtrixCrawl btrixCrawl = new BtrixCrawl(this);
			// Dies führt den CDN-Precrawl aus, parallel dazu den Hauptcrawl.
			boolean wait = false;
			super.startCrawl(btrixCrawl, wait);
		} catch (Exception e) {
			WebgatherLogger.error(e.toString());
			throw new RuntimeException("Browsertrix crawl not successfully started!",
					e);
		}
	}

	/**
	 * Diese Methode parst eine HTTP-Response aus und gibt sie als JSON-String
	 * zurück.
	 * 
	 * @param myresponse eine Closeable HTTP-Response
	 * @return die Response als JSON-String
	 */
	public String getResponseJson(CloseableHttpResponse myresponse) {
		try {
			int statusCode = myresponse.getStatusLine().getStatusCode();
			if (statusCode == 200) {
				String responseJson = EntityUtils.toString(myresponse.getEntity());
				return responseJson;
			}
			String errorBody = EntityUtils.toString(myresponse.getEntity());
			throw new RuntimeException(
					"Status-Code : " + statusCode + ". Fehler-Body: " + errorBody);
		} catch (Exception e) {
			WebgatherLogger.error(e.getMessage());
			throw new RuntimeException(e);
		}
	}

}
