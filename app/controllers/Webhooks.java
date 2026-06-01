/*
 * Copyright 2026 hbz NRW (http://www.hbz-nrw.de/)
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
package controllers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiImplicitParam;
import com.wordnik.swagger.annotations.ApiImplicitParams;
import com.wordnik.swagger.annotations.ApiOperation;

import actions.Create;
import actions.Read;
import authenticate.BasicAuth;
import helper.BtrixCrawlMoveArchiveThread;
import helper.BtrixWebclient;
import helper.WebgatherUtils;
import models.Gatherconf.CrawlerSelection;
import models.Message;
import models.Node;
import play.libs.F.Promise;
import play.mvc.Result;

/**
 * In dieser Klasse werden API-Calls (Endpoints) definiert, die von externen
 * Anwendungen aufgerufen werden, sogenannte "Webhooks". Siehe die Definitionen
 * der Endpoints in der "routes"-Datei, to.science.api/conf/routes. API is
 * documented using Swagger. See: https://github.com/wordnik/swagger-ui
 * 
 * Zur eigentlichen Verarbeitung der Calls wird an andere Klassen übergeben.
 * 
 * @author Ingolf Kuss, kuss@hbz-nrw.de
 * @date 2026-04-16
 */
@BasicAuth
@Api(value = "/webhooks", description = "Die Webhooks-Endpoints verarbeiten Anfragen (POSTs) von externen Anwendungen.")
@SuppressWarnings("javadoc")
public class Webhooks extends MyController {

	@ApiOperation(produces = "application/json", nickname = "btrixCrawlFinished", value = "btrixCrawlFinished", notes = "Implementing Browsertrix Webhook \"Crawl Finished\".", response = Message.class, httpMethod = "POST")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Metadata", required = true, dataType = "string", paramType = "body") })
	/**
	 * Dieser Endpoint verarbeitet eine vom Browertrix bereit gestellte neue
	 * Archivdatei (der Endung WACZ)
	 * 
	 * @author I. Kuss
	 * @date 2026-04-22
	 * @return
	 */
	public static Promise<Result> btrixCrawlFinished() {

		return Promise.promise(() -> {
			JsonNode body = request().body().asJson();
			play.Logger.debug("btrix Crawl Finished sent body: " + body);
			String filename =
					body.findValue("filename").toString().replaceAll("^\"|\"$", "");
			play.Logger.debug("filename found: " + filename);
			/**
			 * Hole cid_stub aus dem Dateinamen. cid_stub = die ersten 12 Zeichen der
			 * Crawler Worfklow Id (cid).
			 */
			File waczFile = new File(filename);
			String regExp = "^([0-9]+)-([0-9a-f]{8})-([0-9a-f]{3})-([0-9]+)\\.wacz$";
			Pattern pattern = Pattern.compile(regExp);
			Matcher matcher = pattern.matcher(waczFile.getName());
			if (!matcher.find()) {
				RuntimeException re =
						new RuntimeException("cid_stub can not be infered from filename "
								+ waczFile.getName() + " !");
				play.Logger.error(re.toString());
				throw re;
			}
			String cid_stub = matcher.group(2) + "-" + matcher.group(3);
			play.Logger.debug("Found cid_stub in filename: " + cid_stub);

			/**
			 * Hole Workflow Config über Get Crawl Configs mit Abfrageparameter
			 * description = cid_stub. Dasselbe macht das Shell-Skript
			 * ks.btrix_get_crawl_configs.sh.
			 */
			BtrixWebclient btrixWebclient = new BtrixWebclient();
			JSONObject crawlConfigs =
					btrixWebclient.getCrawlConfigs("description=" + cid_stub);
			JSONObject crawlConfig =
					(JSONObject) crawlConfigs.getJSONArray("items").get(0);
			play.Logger.debug(
					"Found Crawl Config with name: " + crawlConfig.getString("name"));
			play.Logger.debug("Crawl Config cid = " + crawlConfig.getString("id"));
			String lastCrawlId = crawlConfig.getString("lastCrawlId");
			play.Logger.debug("Last Crawl Id = " + lastCrawlId);
			String toscienceId = (String) crawlConfig.getJSONArray("tags").get(0);
			play.Logger.debug("Crawl Config is for toscience ID: " + toscienceId);
			play.Logger.debug(
					"lastCrawlStartTime: " + crawlConfig.getString("lastCrawlStartTime"));
			// Hole Zeitstempel aus Dateinamen
			DateTimeFormatter formatter =
					DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
			LocalDateTime dateTime =
					LocalDateTime.parse(waczFile.getName().substring(0, 14), formatter);
			ZoneId zoneId = ZoneId.of("UTC");
			ZonedDateTime dateTimeUtc = ZonedDateTime.of(dateTime, zoneId);
			ZonedDateTime dateTimeLocal =
					dateTimeUtc.withZoneSameInstant(ZoneId.systemDefault());
			String datetime = dateTimeLocal.format(formatter);
			play.Logger.debug("datetime: " + datetime);

			btrixWebclient.setResultDir(new File(
					btrixWebclient.getOutDir() + "/" + toscienceId + "/" + datetime));
			if (!btrixWebclient.getResultDir().exists()) {
				// create output directory for this Browsertrix Crawl
				play.Logger.debug("Creating Output Directory "
						+ btrixWebclient.getResultDir().toString());
				btrixWebclient.getResultDir().mkdirs();
			}

			/*
			 * Ab hier wird die Verarbeitung an einen Thread übergeben
			 * (Nebenläufigkeit); lang dauernde Dateioperationen möglich! Nach dem
			 * Verschieben der Archivdatei in den Ergebnisbereich (btrix-data) wird
			 * automatisch ein Webschnitt angelegt.
			 */
			BtrixCrawlMoveArchiveThread moveArchive =
					new BtrixCrawlMoveArchiveThread(btrixWebclient);
			moveArchive.setFilename(filename);
			moveArchive.setToscienceId(toscienceId);
			moveArchive.setLastCrawlId(lastCrawlId);
			moveArchive.setDatetime(datetime);
			moveArchive.setDaemon(false);
			moveArchive.start();

			return ok();
		});
	}

}
