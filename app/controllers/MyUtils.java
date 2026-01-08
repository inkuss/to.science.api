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
package controllers;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import com.avaje.ebean.Ebean;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiImplicitParam;
import com.wordnik.swagger.annotations.ApiImplicitParams;
import com.wordnik.swagger.annotations.ApiOperation;

import actions.Create;
import actions.Index;
import authenticate.BasicAuth;
import authenticate.User;
import authenticate.UserDB;
import helper.GatherconfImporter;
import helper.Webgatherer;
import helper.oai.OaiDispatcher;
import models.Gatherconf;
import models.Globals;
import models.Message;
import models.Node;
import models.ToScienceObject;
import play.libs.F.Promise;
import play.mvc.Result;
import views.Title;

/**
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 *         Api is documented using swagger. See:
 *         https://github.com/wordnik/swagger-ui
 * 
 */
@BasicAuth
@Api(value = "/utils", description = "The utils endpoint provides rpc style methods.")
@SuppressWarnings("javadoc")
public class MyUtils extends MyController {

	@ApiOperation(produces = "application/json,application/html", nickname = "index", value = "index", notes = "Adds resource to private elasticsearch index", response = List.class, httpMethod = "POST")
	public static Promise<Result> index(@PathParam("pid") String pid,
			@QueryParam("index") final String indexName) {
		return new IndexAction().call(pid, node -> {
			String result = index.index(node);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "indexAll", value = "indexAll", notes = "Adds resource to private elasticsearch index", response = List.class, httpMethod = "POST")
	public static Promise<Result> indexAll(
			@QueryParam("index") final String indexName) {
		return new BulkActionAccessor().call((userId) -> {
			String indexNameWithDatestamp = indexName + "-" + getCurrentDate();
			actions.BulkAction bulk = new actions.BulkAction();
			bulk.execute(indexName, userId, nodes -> {
				return index.indexAll(nodes, indexNameWithDatestamp);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "reinitOaisets", value = "reinitOaisets", notes = "Updates the oaisets of all resources", response = List.class, httpMethod = "POST")
	public static Promise<Result> reinitOaisets(
			@QueryParam("namespace") final String namespace) {
		return new BulkActionAccessor().call((userId) -> {
			actions.BulkAction bulk = new actions.BulkAction();
			bulk.execute(namespace, userId, nodes -> {
				return modify.reinitOaiSets(nodes);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "lobidifyAll", value = "lobidifyAll", notes = "Updates the bibliographic metadata of all resources", response = List.class, httpMethod = "POST")
	public static Promise<Result> lobidifyAll(
			@QueryParam("namespace") final String namespace) {
		return new BulkActionAccessor().call((userId) -> {
			actions.BulkAction bulk = new actions.BulkAction();
			bulk.execute(namespace, userId, nodes -> {
				return modify.lobidify(nodes);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "addUrnToAll", value = "addUrnToAll", notes = "Attempts to add urns to all resources", response = List.class, httpMethod = "POST")
	public static Promise<Result> addUrnToAll(
			@QueryParam("namespace") final String namespace,
			@QueryParam("snid") final String snid,
			@QueryParam("fromBefore") final String fromBefore) {
		return new BulkActionAccessor().call((userId) -> {
			Date fromBeforeDate = createDateFromString(fromBefore);
			actions.BulkAction bulk = new actions.BulkAction();
			bulk.execute(namespace, userId, nodes -> {
				String msg = modify.addUrnToAll(nodes, snid, fromBeforeDate);
				play.Logger.info(msg);
				return msg;
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "removeFromIndex", value = "removeFromIndex", notes = "Removes resource to elasticsearch index", httpMethod = "DELETE")
	public static Promise<Result> removeFromIndex(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			String result = index.remove(node);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "removeFromIndex", value = "removeFromIndex", notes = "Removes resource to elasticsearch index", httpMethod = "DELETE")
	public static Promise<Result> removePidFromIndex(
			@QueryParam("pid") String pid, @QueryParam("type") String contentType,
			@QueryParam("namespace") String namespace) {
		return new ModifyAction().call(pid, userId -> {
			String result =
					actions.Index.removeFromAllIndexed(pid, contentType, namespace);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "lobidify", value = "lobidify", notes = "Fetches metadata from lobid.org and PUTs it to /metadata.", httpMethod = "POST")
	public static Promise<Result> lobidify(@PathParam("pid") String pid,
			@QueryParam("alephid") String alephid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			/*
			 * Hier einbauen für TOSDEV-32: 1. den noch vorhandenen alten Titel aus
			 * dem noch unmodifizierten Node lesen 2. das LB-Kennzeichen aus dem alten
			 * Titel extrahieren "und in den Modify einbauen"
			 */
			String title = Title.getTitle(node.getLd2());
			play.Logger.debug("lobidify Titel=" + title);
			if (alephid != null && !alephid.isEmpty()) {
				String result2 = modify.lobidify2(node, alephid);
				return JsonMessage(
						new Message("Load " + alephid + " to " + pid + ".\n" + result2));
			} else {
				String result = modify.lobidify2(node);
				return JsonMessage(new Message(result));
			}
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "lobidify", value = "lobidify", notes = "Fetches metadata from lobid.org and PUTs it to /metadata.", httpMethod = "POST")
	public static Promise<Result> updateMetadata(@PathParam("pid") String pid,
			@QueryParam("date") String date) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			if (date != null && !date.isEmpty()) {
				String result = modify.lobidify2(node,
						LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyyMMdd")));
				return JsonMessage(new Message(result));
			} else {
				LocalDate lastUpdate = getUpdateTimeStamp(node);
				String result = modify.lobidify2(node, lastUpdate);
				return JsonMessage(new Message(result));
			}

		});
	}

	@SuppressWarnings("unchecked")
	private static LocalDate getUpdateTimeStamp(Node node) {
		SimpleDateFormat formatter =
				new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
		// initialise date
		String lastUpdate = formatter.format(new Date());
		// try to set date to created (should work in any case)
		try {
			lastUpdate = ((Map<String, Object>) node.getLd2().get("isDescribedBy"))
					.get("created").toString();
			play.Logger.debug(
					"Got last update date from \"isDescribedBy/created\": " + lastUpdate);
		} catch (Exception e) {
			play.Logger.warn(node.getPid()
					+ " couldn't get created timestamp from \"isDescribedBy/created\" "
					+ e.getMessage());
		}
		// try to set date to modified (overrides created)
		try {
			lastUpdate = ((Map<String, Object>) node.getLd2().get("isDescribedBy"))
					.get("modified").toString();
			play.Logger.debug("Got last update date from \"isDescribedBy/modified\": "
					+ lastUpdate);
		} catch (Exception e) {
			play.Logger.info(node.getPid()
					+ " couldn't get modified timestamp from \"isDescribedBy/modified\" "
					+ e.getMessage());
		}
		return LocalDate.parse(lastUpdate,
				DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "addObjectTimestamp", value = "addObjectTimestamp", notes = "Add a objectTimestamp", httpMethod = "POST")
	public static Promise<Result> addObjectTimestamp(
			@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Date t = node.getObjectTimestamp();
			if (t == null) {
				t = node.getCreationDate();
				modify.setObjectTimestamp(node, t, userId);
				return JsonMessage(new Message(
						pid + " set objectTimestamp to " + Globals.dateFormat.format(t)));
			}
			return JsonMessage(new Message(pid + " already has objectTimestamp "
					+ Globals.dateFormat.format(t)));
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "addUrn", value = "addUrn", notes = "Adds a urn to the /metadata of the resource.", httpMethod = "POST")
	public static Promise<Result> addUrn(@QueryParam("id") final String id,
			@QueryParam("namespace") final String namespace,
			@QueryParam("snid") final String snid) {
		return new BulkActionAccessor().call((userId) -> {
			String result = modify.addUrn(id, namespace, snid, userId);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "replaceUrn", value = "replaceUrn", notes = "Replaces a urn on the /metadata of the resource.", httpMethod = "POST")
	public static Promise<Result> replaceUrn(@QueryParam("id") final String id,
			@QueryParam("namespace") final String namespace,
			@QueryParam("snid") final String snid) {
		return new BulkActionAccessor().call((userId) -> {
			String result =
					modify.replaceUrn(read.readNode(namespace + ":" + id), snid, userId);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "initContentModels", value = "initContentModels", notes = "Initializes default transformers.", httpMethod = "POST")
	public static Promise<Result> initContentModels(
			@DefaultValue("") @QueryParam("namespace") String namespace) {
		return new BulkActionAccessor().call((userId) -> {
			String result = OaiDispatcher.initContentModels(namespace);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json,application/html", nickname = "importGatherconf", value = "importGatherconf", notes = "Import Gatherconf", httpMethod = "POST")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Metadata", required = true, dataType = "string", paramType = "body") })
	public static Promise<Result> importGatherConf(
			@QueryParam("namespace") final String namespace,
			@QueryParam("firstId") final String firstIdStr) {

		return new BulkActionAccessor().call((userId) -> {
			List<Gatherconf> list = new Vector<Gatherconf>();
			play.Logger.debug("request: {}", request().body());
			String csv = request().body().asText();
			play.Logger.debug("userId = {}", userId);
			play.Logger.debug("firstId = {}", firstIdStr);
			play.Logger.debug("csv = {}", csv);

			int firstId = Integer.parseInt(firstIdStr);
			list = GatherconfImporter.read(csv, firstId);
			for (Gatherconf conf : list) {
				ToScienceObject object = new ToScienceObject();
				object.setContentType("webpage");
				object.setAccessScheme("public");
				object.setPublishScheme("public");
				String pid = namespace + ":" + conf.getId();
				play.Logger.info("Create webpage with id " + pid + ".");
				Node webpage = null;
				try {
					Node node = read.readNode(pid);
					webpage = new Create().updateResource(node, object);
				} catch (Exception e) {
					webpage =
							new Create().createResource(conf.getId(), namespace, object);
				}
				new actions.Modify().updateConf(webpage, conf.toString());
				String ht = conf.getName();
				if ("null".equals(ht) || ht == null || ht.isEmpty()) {
					new actions.Modify().updateLobidifyAndEnrichMetadata(webpage,
							"<" + webpage.getPid() + "> <http://purl.org/dc/terms/title> \""
									+ conf.getUrl()
									+ "\"^^<http://www.w3.org/2001/XMLSchema#string> .");
				} else {
					new actions.Modify().lobidify2(webpage, ht);
				}
				play.Logger.info("Import Webpage: " + webpage.getPid());
			}

			return getJsonResult(list);
		});

	}

	@ApiOperation(produces = "application/json,application/html", nickname = "runGatherer", value = "runGatherer", notes = "Runs the webgatherer", httpMethod = "POST")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Metadata", required = true, dataType = "string", paramType = "body") })
	public static Promise<Result> runGatherer() {
		return new BulkActionAccessor().call((userId) -> {
			Webgatherer gatherer = new Webgatherer();
			gatherer.run();
			return ok();
		});
	}

	public static Promise<Result> addUser() {
		return new BulkActionAccessor().call((userId) -> {
			try {
				JsonNode node = request().body().asJson();
				User user = new ObjectMapper().treeToValue(node, User.class);
				if (user.getUsername() == null || user.getUsername().isEmpty()) {
					throw new RuntimeException("Please specify username!");
				}
				if (user.getPassword() == null || user.getPassword().isEmpty()) {
					throw new RuntimeException("Please specify password!");
				}
				UserDB.getInstance().addUser(user, user.getPassword());
				return ok();
			} catch (Exception e) {
				play.Logger.warn("", e);
				return JsonMessage(new Message(e.getMessage(), 400));
			}
		});
	}

}
