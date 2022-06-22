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

import static archive.fedora.FedoraVocabulary.IS_PART_OF;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiImplicitParam;
import com.wordnik.swagger.annotations.ApiImplicitParams;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.core.util.JsonUtil;

import actions.BulkAction;
import actions.Enrich;
import actions.Read;
import archive.fedora.RdfUtils;
import authenticate.BasicAuth;
import helper.HttpArchiveException;
import helper.WebgatherUtils;
import helper.WebsiteVersionPublisher;
import helper.oai.OaiDispatcher;
import models.DublinCoreData;
import models.Gatherconf;
import models.Gatherconf.RobotsPolicy;
import models.Globals;
import models.Link;
import models.MabRecord;
import models.Message;
import models.Node;
import models.ToScienceObject;
import models.UrlHist;
import play.api.http.MediaRange;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.twirl.api.Html;
import views.html.edit;
import views.html.frlResource;
import views.html.resource;
import views.html.resources;
import views.html.search;
import views.html.status;
import views.html.oai.mab;
import views.html.oai.mets;
import views.html.oai.oaidc;
import views.html.oai.wgl;
import views.html.tags.getTitle;

/**
 * In dieser Klasse werden API-Calls (Endpoint-Calls) auf Ressourcen (sub-path
 * /resource) definiert. Siehe die Definitionen der HTTP-Requests in der
 * "routes"-Datei, to.science.api/conf/routes.
 * 
 * Zur eigentlichen Verarbeitung der Calls wird an andere Klassen übergeben.
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 *         Api is documented using swagger. See:
 *         https://github.com/wordnik/swagger-ui
 * 
 */
@BasicAuth
@Api(value = "/resource", description = "The resource endpoint allows one to manipulate and access complex objects as http resources. ")
@SuppressWarnings("javadoc")
public class Resource extends MyController {

	@ApiOperation(produces = "application/json", nickname = "listUrn", value = "listUrn", notes = "Returns infos about urn", httpMethod = "GET")
	public static Promise<Result> listUrn(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, (Node node) -> {
			response().setHeader("Access-Control-Allow-Origin", "*");
			return getJsonResult(read.getUrnStatus(node));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "listNodes", value = "listNodes", notes = "Returns all nodes for a list of ids", httpMethod = "GET")
	public static Promise<Result> listNodes(@QueryParam("ids") String ids) {
		return new ListAction().call((userId) -> {
			try {
				List<String> is = Arrays.asList(ids.split(","));
				return getJsonResult(read.getNodes(is));
			} catch (HttpArchiveException e) {
				return JsonMessage(new Message(e, e.getCode()));
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});

	}

	@ApiOperation(produces = "application/json,text/html,text/csv", nickname = "listResources", value = "listResources", notes = "Returns a list of ids", httpMethod = "GET")
	public static Promise<Result> listResources(
			@QueryParam("namespace") String namespace,
			@QueryParam("contentType") String contentType,
			@QueryParam("from") int from, @QueryParam("until") int until) {
		try {
			if (request().accepts("text/html")) {
				return htmlList(namespace, contentType, from, until);
			}
			return jsonList(namespace, contentType, from, until);
		} catch (HttpArchiveException e) {
			return Promise.promise(new Function0<Result>() {
				@Override
				public Result apply() {
					return JsonMessage(new Message(e, e.getCode()));
				}
			});
		} catch (Exception e) {
			return Promise.promise(new Function0<Result>() {

				@Override
				public Result apply() {
					return JsonMessage(new Message(e, 500));
				}

			});
		}
	}

	private static Promise<Result> jsonList(String namespace, String contentType,
			int from, int until) {
		return new ListAction().call((userId) -> {
			try {
				List<Node> nodes = read.listRepo(contentType, namespace, from, until);
				return getJsonResult(nodes);
			} catch (HttpArchiveException e) {
				return JsonMessage(new Message(e, e.getCode()));
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});
	}

	private static Promise<Result> htmlList(String namespace, String contentType,
			int from, int until) {
		return new ListAction().call((userId) -> {
			try {
				response().setHeader("Access-Control-Allow-Origin", "*");
				response().setHeader("Content-Type", "text/html; charset=utf-8");
				List<Node> nodes = read.listRepo(contentType, namespace, from, until);
				return ok(resources.render(nodes));
			} catch (HttpArchiveException e) {
				return HtmlMessage(new Message(e, e.getCode()));
			} catch (Exception e) {
				return HtmlMessage(new Message(e, 500));
			}
		});

	}

	@ApiOperation(produces = "application/json,text/html,application/rdf+xml", nickname = "listResource", value = "listResource", notes = "Returns a resource. Redirects in dependends to the accept header ", response = Message.class, httpMethod = "GET")
	public static Promise<Result> listResource(@PathParam("pid") String pid,
			@QueryParam("design") String design) {
		if (request().accepts("text/html"))
			return asHtml(pid, design);
		if (request().accepts("application/rdf+xml"))
			return asRdf(pid);
		if (request().accepts("text/plain"))
			return asRdf(pid);
		return asJson(pid);
	}

	@ApiOperation(produces = "application/rdf+xml,text/plain", nickname = "asRdf", value = "asRdf", notes = "Returns a rdf display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asRdf(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			try {
				String result = "";
				Map<String, Object> rdf = node.getLd2();
				rdf.put("@context", Globals.profile.getContext().get("@context"));
				String jsonString = JsonUtil.mapper().writeValueAsString(rdf);
				/*
				 * play.Logger.debug(
				 * "Response von node.getLd2() als String \n------QA-----\n-----QA----\n"
				 * + jsonString); hier sieht alles gut aus, academicTitle und
				 * affiliation sind Bestandteile von creator
				 */
				RDFFormat rdfFormat = null;
				String mimetype = null;

				// Qa: refactored method in order to simplify code via reducing code
				// duplication
				// Using the power of RDFFormat.Class
				if (request().accepts("application/rdf+xml")) {
					rdfFormat = RDFFormat.RDFXML;
					mimetype = rdfFormat.getDefaultMIMEType();
				} else if (request().accepts("text/plain")) {
					rdfFormat = RDFFormat.NTRIPLES;
					mimetype = "text/plain";
				}
				if (rdfFormat != null) {
					play.Logger.debug("Use RDFFormat: " + rdfFormat.getName());
					play.Logger.debug("aggregationUri= " + node.getAggregationUri());
					result = RdfUtils.readRdfToString(
							new ByteArrayInputStream(jsonString.getBytes("utf-8")),
							RDFFormat.JSONLD, RDFFormat.RDFXML, node.getAggregationUri());
					response().setContentType(mimetype);
					play.Logger.debug("result=" + result);
					return ok(result);
				}
				return JsonMessage(new Message(result));
			} catch (Exception e) {
				throw new HttpArchiveException(500, e);
			}
		});

	}

	@ApiOperation(produces = "text/plain", nickname = "listMetadata", value = "listMetadata", notes = "Shows Metadata of a resource.", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listMetadata(@PathParam("pid") String pid,
			@QueryParam("field") String field) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setHeader("Access-Control-Allow-Origin", "*");
			String result = read.readMetadata1(node, field);
			RDFFormat format = null;
			if (request().accepts("application/rdf+xml")) {
				format = RDFFormat.RDFXML;
				response().setContentType("application/rdf+xml");
			} else if (request().accepts("text/turtle")) {
				format = RDFFormat.TURTLE;
				response().setContentType("text/turtle");
			} else if (request().accepts("text/plain")) {
				format = RDFFormat.NTRIPLES;
				response().setContentType("text/plain");
			}
			try (
					InputStream in = new ByteArrayInputStream(result.getBytes("utf-8"))) {
				String rdf =
						RdfUtils.readRdfToString(in, RDFFormat.NTRIPLES, format, "");
				return ok(rdf);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@ApiOperation(produces = "text/plain", nickname = "listMetadata", value = "listMetadata", notes = "Shows Metadata of a resource.", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listMetadata2(@PathParam("pid") String pid,
			@QueryParam("field") String field) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setHeader("Access-Control-Allow-Origin", "*");
			String result = read.readMetadata2(node, field);
			RDFFormat format = null;
			if (request().accepts("application/rdf+xml")) {
				format = RDFFormat.RDFXML;
				response().setContentType("application/rdf+xml");
			} else if (request().accepts("text/turtle")) {
				format = RDFFormat.TURTLE;
				response().setContentType("text/turtle");
			} else if (request().accepts("text/plain")) {
				format = RDFFormat.NTRIPLES;
				response().setContentType("text/plain");
			}
			try (
					InputStream in = new ByteArrayInputStream(result.getBytes("utf-8"))) {
				String rdf =
						RdfUtils.readRdfToString(in, RDFFormat.NTRIPLES, format, "");
				return ok(rdf);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
	}

	@ApiOperation(produces = "text/plain", nickname = "listLrmiData", value = "listLrmiData", notes = "Shows LRMI-Metadata of a resource.", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listLrmiData(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setHeader("Access-Control-Allow-Origin", "*");
			String result = read.readLrmiData(node);
			response().setContentType("application/json");
			play.Logger.debug("result=" + result);
			return ok(result);
		});

	}

	@SuppressWarnings("resource")
	@ApiOperation(produces = "application/octet-stream", nickname = "listData", value = "listData", notes = "Shows Data of a resource", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listData(@PathParam("pid") String pid) {
		return new ReadDataAction().call(pid, node -> {
			HttpURLConnection connection = null;
			try {
				response().setHeader("Access-Control-Allow-Origin", "*");
				URL url = new URL(Globals.fedoraIntern + "/objects/" + pid
						+ "/datastreams/data/content");
				connection = (HttpURLConnection) url.openConnection();
				response().setContentType(connection.getContentType());
				response().setHeader("Content-Disposition",
						"inline;filename=\"" + node.getFileLabel() + "\"");
				return ok(connection.getInputStream());
			} catch (FileNotFoundException e) {
				throw new HttpArchiveException(404, e);
			} catch (MalformedURLException e) {
				throw new HttpArchiveException(500, e);
			} catch (IOException e) {
				throw new HttpArchiveException(500, e);
			}
		});
	}

	@ApiOperation(produces = "application/json", nickname = "listDc", value = "listDc", notes = "Shows internal dublin core stream", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listDc(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			DublinCoreData dc = read.readDC(pid);
			return getJsonResult(dc);
		});
	}

	@ApiOperation(produces = "application/json", nickname = "patchResource", value = "patchResource", notes = "Patches a Resource", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "New Object", required = true, dataType = "RegalObject", paramType = "body") })
	public static Promise<Result> patchResource(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			try {
				play.Logger.debug("Patching Pid: " + pid);
				String result = "";
				Node node = readNodeOrNull(pid);
				ToScienceObject object = getRegalObject(request().body().asJson());
				Node newNode = create.patchResource(node, object);
				result = newNode.getLastModifyMessage();
				result = result.concat(" " + newNode.getPid() + " created/updated!");
				return JsonMessage(new Message(result));
			} catch (Exception e) {
				play.Logger.error("", e);
				return JsonMessage(new Message(e, 500));
				// return JsonMessage(new Message( json(e.toString()) ));
			}
		});
	}

	@ApiOperation(produces = "application/json", nickname = "patchResources", value = "patchResources", notes = "Applies the PATCH object to the resource and to all child resources", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "RegalObject wich specifies a values that must be modified in the resource and it's childs", required = true, dataType = "RegalObject", paramType = "body") })
	public static Promise<Result> patchResources(@PathParam("pid") String pid) {
		return new BulkActionAccessor().call((userId) -> {
			ToScienceObject object = getRegalObject(request().body().asJson());
			List<Node> list = Globals.fedora.listComplexObject(pid);
			list.removeIf(n -> "D".equals(n.getState()));
			BulkAction bulk = new BulkAction();
			bulk.executeOnNodes(list, userId, nodes -> {
				return create.patchResources(nodes, object);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json", nickname = "updateResource", value = "updateResource", notes = "Updates or Creates a Resource with the path decoded pid", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "New Object", required = true, dataType = "RegalObject", paramType = "body") })
	public static Promise<Result> updateResource(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			play.Logger.debug("Updating Pid: " + pid);
			String result = "";
			Node node = readNodeOrNull(pid);
			ToScienceObject object = getRegalObject(request().body().asJson());
			Node newNode = null;
			if (node == null) {
				String[] namespacePlusId = pid.split(":");
				newNode = create.createResource(namespacePlusId[1], namespacePlusId[0],
						object);
			} else {
				newNode = create.updateResource(node, object);
			}
			result = result.concat(newNode.getPid() + " created/updated!");
			return JsonMessage(new Message(result));

		});

	}

	@ApiOperation(produces = "application/json", nickname = "createNewResource", value = "createNewResource", notes = "Creates a Resource on a new position", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "New Object", required = true, dataType = "RegalObject", paramType = "body") })
	public static Promise<Result> createResource(
			@PathParam("namespace") String namespace) {
		return new CreateAction().call((userId) -> {
			ToScienceObject object = getRegalObject(request().body().asJson());
			if (object.getContentType().equals("webpage")) {
				object.setAccessScheme("restricted");
			}
			Node newNode = create.createResource(namespace, object);
			String result = newNode.getPid() + " created/updated!";
			response().setHeader("Location", read.getHttpUriOfResource(newNode));
			return JsonMessage(new Message(result, 200));
		});
	}

	private static ToScienceObject getRegalObject(JsonNode json) {
		try {
			ToScienceObject object;
			play.Logger.debug("Json Body: " + json);
			if (json != null) {
				object = MyController.mapper.readValue(json.toString(),
						ToScienceObject.class);
				return object;
			}
			throw new NullPointerException(
					"Please PUT at least a type, e.g. {\"type\":\"monograph\"}");
		} catch (JsonMappingException e) {
			throw new HttpArchiveException(500, e);
		} catch (JsonParseException e) {
			throw new HttpArchiveException(500, e);
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}
	}

	@ApiOperation(produces = "application/json", nickname = "updateSeq", value = "updateSeq", notes = "Updates the ordering of child objects using a n-triple list.", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Metadata", required = true, dataType = "string", paramType = "body") })
	public static Promise<Result> updateSeq(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, node -> {
			String result =
					modify.updateSeq(pid, request().body().asJson().toString());
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "updateMetadata", value = "updateMetadata", notes = "Updates the metadata of the resource using n-triples.", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Metadata", required = true, dataType = "string", paramType = "body") })
	public static Promise<Result> updateMetadata(@PathParam("pid") String pid) {

		return new ModifyAction().call(pid, node -> {
			play.Logger.debug("BEGIN controllers.Resource.updateMetadata(pid)");
			try {
				String result = modify.updateLobidify2AndEnrichMetadata(pid,
						request().body().asText());
				return JsonMessage(new Message(result));
			} catch (Exception e) {
				throw new HttpArchiveException(500, e);
			}
		});

	}

	@ApiOperation(produces = "application/json", nickname = "updateMetadata2", value = "updateMetadata2", notes = "Updates the metadata of the resource using n-triples.", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Metadata", required = true, dataType = "string", paramType = "body") })
	public static Promise<Result> updateMetadata2(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, node -> {
			play.Logger.debug("BEGIN controllers.Resource.updateMetadata2(pid)");
			try {
				play.Logger.debug("Start method updateMetadata2(pid)");
				/**
				 * Wir aktualisieren 2 Datenströme, oder legen sie neu an:
				 * 
				 * 1. lobid2-Metadaten als Datenstrom "Metadata2"
				 */

				String result1 = modify.updateLobidify2AndEnrichMetadata(pid,
						request().body().asText());
				play.Logger.debug(result1);

				/**
				 * 2. nach LRMI gemappte lobid2-Metadaten als Datenstrom "Lrmidata"
				 */
				// Es wird nur NTRIPLES akzeptiert
				RDFFormat format = RDFFormat.NTRIPLES;
				response().setContentType("text/plain");
				// play.Logger.debug("request body=" + request().body().asText());
				Node nodeNode = new Read().readNode(pid);
				String result2 = modify.updateLrmifyAndEnrichMetadata(pid, format,
						nodeNode.getMetadata2());
				play.Logger.debug(result2);

				return JsonMessage(new Message(result1 + "\n" + result2));
			} catch (Exception e) {
				throw new HttpArchiveException(500, e);
			}
		});
	}

	@ApiOperation(produces = "application/json", nickname = "updateLrmiMessageData", value = "updateLrmiData", notes = "Updates the metadata of the resource using Lrmi data.", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Metadata", required = true, dataType = "string", paramType = "body") })
	public static Promise<Result> updateLrmiData(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, node -> {
			play.Logger.debug("Starting updateLrmiData with pid=" + pid);
			play.Logger
					.debug("request().body().asJson()=" + request().body().asJson());
			try {
				/**
				 * Wir legen 2 Datenströme an:
				 * 
				 * 1. ungemappte, aber angereicherte, LRMI-Daten als neuartiger
				 * Datenstrom "Lrmidata"
				 */
				String result1 =
						modify.updateAndEnrichLrmiData(pid, request().body().asJson());
				play.Logger.debug(result1);

				/**
				 * 2. gemappte LRMI-Daten als Metadata2-Datenstrom
				 */
				/* Format nicht nach dem Header richten, es muss NTRIPLES sein: */
				RDFFormat format = RDFFormat.NTRIPLES;
				Node nodeNode = new Read().readNode(pid);
				String result2 = modify.updateLobidify2AndEnrichLrmiData(nodeNode,
						format, nodeNode.getLrmiData());
				play.Logger.debug(result2);

				return JsonMessage(new Message(result1 + "\n" + result2));
			} catch (Exception e) {
				throw new HttpArchiveException(500, e);
			}
		});
	}

	@ApiOperation(produces = "application/json", nickname = "updateData", value = "updateData", notes = "Updates the data of a resource", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(name = "data", value = "data", dataType = "file", required = true, paramType = "body") })
	public static Promise<Result> updateData(@PathParam("pid") String pid,
			@QueryParam("md5") String md5) {
		return new ModifyAction().call(pid, node -> {
			try {
				MultipartFormData body = request().body().asMultipartFormData();
				FilePart d = body.getFile("data");
				if (d == null) {
					return JsonMessage(new Message("Missing File.", 400));
				}
				String mimeType = d.getContentType();
				String name = d.getFilename();
				try (FileInputStream content = new FileInputStream(d.getFile())) {
					modify.updateData(pid, content, mimeType, name, md5);
					return JsonMessage(new Message(
							"File uploaded! Type: " + mimeType + ", Name: " + name));
				}
			} catch (IOException e) {
				throw new HttpArchiveException(500, e);
			}
		});
	}

	@ApiOperation(produces = "application/json", nickname = "updateDc", value = "updateDc", notes = "Updates the dc data of a resource", response = Message.class, httpMethod = "PUT")
	@ApiImplicitParams({
			@ApiImplicitParam(value = "Add Dublin Core", required = true, dataType = "DublinCoreData", paramType = "body") })
	public static Promise<Result> updateDc(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, node -> {
			try {
				Object o = request().body().asJson();
				DublinCoreData dc;
				if (o != null) {
					dc = MyController.mapper.readValue(o.toString(),
							DublinCoreData.class);
				} else {
					dc = new DublinCoreData();
				}
				String result = modify.updateDC(pid, dc);
				return JsonMessage(new Message(result, 200));
			} catch (IOException e) {
				throw new HttpArchiveException(500, e);
			}
		});
	}

	@ApiOperation(produces = "application/json", nickname = "deleteResource", value = "deleteResource", notes = "Deletes a resource", response = Message.class, httpMethod = "DELETE")
	public static Promise<Result> deleteResource(@PathParam("pid") String pid,
			@QueryParam("purge") String purge) {
		return new BulkActionAccessor().call((userId) -> {
			List<Node> list = Globals.fedora.listComplexObject(pid);
			BulkAction bulk = new BulkAction();
			bulk.executeOnNodes(list, userId, nodes -> {
				if ("true".equals(purge)) {
					return delete.purge(nodes);
				}
				return delete.delete(nodes);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json", nickname = "activateResource", value = "activateResource", notes = "Activates a deleted resource", response = Message.class, httpMethod = "POST")
	public static Promise<Result> activateResource(@PathParam("pid") String pid) {
		return new BulkActionAccessor().call((userId) -> {
			List<Node> list = Globals.fedora.listComplexObject(pid);
			BulkAction bulk = new BulkAction();
			bulk.executeOnNodes(list, userId, nodes -> {
				return activate.activate(nodes);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json", nickname = "deleteSeq", value = "deleteSeq", notes = "Deletes a resources ordering definition for it's children objects", response = Message.class, httpMethod = "DELETE")
	public static Promise<Result> deleteSeq(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, node -> {
			String result = delete.deleteSeq(pid);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "deleteMetadata", value = "deleteMetadata", notes = "Deletes a resources metadata", response = Message.class, httpMethod = "DELETE")
	public static Promise<Result> deleteMetadata(@PathParam("pid") String pid,
			@QueryParam("field") String field) {
		return new ModifyAction().call(pid, node -> {
			if (field != null && !field.isEmpty()) {
				String result = delete.deleteMetadataField(pid, field);
				return JsonMessage(new Message(result));
			}
			String result = delete.deleteMetadata(pid);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "deleteMetadata", value = "deleteMetadata", notes = "Deletes a resources metadata", response = Message.class, httpMethod = "DELETE")
	public static Promise<Result> deleteMetadata2(@PathParam("pid") String pid,
			@QueryParam("field") String field) {
		return new ModifyAction().call(pid, node -> {
			if (field != null && !field.isEmpty()) {
				String result = delete.deleteMetadata2Field(pid, field);
				return JsonMessage(new Message(result));
			}
			String result = delete.deleteMetadata2(pid);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "deleteData", value = "deleteData", notes = "Deletes a resources data", response = Message.class, httpMethod = "DELETE")
	public static Promise<Result> deleteData(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, node -> {
			String result = delete.deleteData(pid);
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "deleteDc", value = "deleteDc", notes = "Not implemented", response = Message.class, httpMethod = "DELETE")
	public static Promise<Result> deleteDc(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid,
				node -> JsonMessage(new Message("Not implemented!", 500)));
	}

	@ApiOperation(produces = "application/json", nickname = "deleteResources", value = "deleteResources", notes = "Deletes a set of resources", response = Message.class, httpMethod = "DELETE")
	public static Promise<Result> deleteResources(
			@QueryParam("namespace") String namespace,
			@QueryParam("purge") String purge) {
		return new BulkActionAccessor().call((userId) -> {
			actions.BulkAction bulk = new actions.BulkAction();
			bulk.execute(namespace, userId, nodes -> {
				if ("true".equals(purge)) {
					return delete.purge(nodes);
				}
				return delete.delete(nodes);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json,text/html", nickname = "listParts", value = "listParts", notes = "List resources linked with hasPart", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listParts(@PathParam("pid") String pid,
			@QueryParam("style") String style) {
		return new ReadMetadataAction().call(pid, node -> {
			try {

				List<String> nodeIds = node.getPartsSorted().stream()
						.map((Link l) -> l.getObject()).collect(Collectors.toList());
				if ("short".equals(style)) {
					return getJsonResult(nodeIds);
				}
				List<Node> result = read.getNodes(nodeIds);

				if (request().accepts("text/html")) {
					return ok(resources.render(result));
				}
				return getJsonResult(result);
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}

		});
	}

	@ApiOperation(produces = "application/json,text/html", nickname = "search", value = "search", notes = "Find resources", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> search(@QueryParam("q") String queryString,
			@QueryParam("from") int from, @QueryParam("until") int until,
			@QueryParam("format") String format) {
		return new ReadMetadataAction().call(null, node -> {
			List<Map<String, Object>> hitMap = new ArrayList<>();
			try {
				SearchResponse response = getSearchResult(queryString, from, until);
				SearchHits hits = response.getHits();
				Aggregations aggs = response.getAggregations();
				List<SearchHit> list = Arrays.asList(hits.getHits());
				hitMap = read.hitlistToMap(list);
				if ("csv".equals(format)) {
					return getCsvResults(new ObjectMapper().valueToTree(hitMap));
				}
				if ("json".equals(format)) {
					return getJsonResult(hitMap);
				}
				if (request().accepts("text/html")) {
					return ok(search.render(hitMap, aggs, queryString,
							hits.getTotalHits(), from, until, Globals.defaultNamespace));
				}
				if (request().accepts("text/csv")) {
					return getCsvResults(new ObjectMapper().valueToTree(hitMap));
				}
				return getJsonResult(hitMap);
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});
	}

	private static SearchResponse getSearchResult(String queryString, int from,
			int until) {
		String metadataIndex =
				Globals.PUBLIC_INDEX_PREF + Globals.defaultNamespace + "2";
		if (Globals.users.isLoggedIn(ctx())) {
			metadataIndex = Globals.defaultNamespace + "2";
		}
		return Globals.search.query(
				new String[] { metadataIndex,
						Globals.PDFBOX_OCR_INDEX_PREF + Globals.defaultNamespace },
				queryString, from, until);
	}

	@ApiOperation(produces = "application/json,text/html", nickname = "listAllParts", value = "listAllParts", notes = "List resources linked with hasPart", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listAllParts(@PathParam("pid") String pid,
			@QueryParam("style") String s, @QueryParam("design") String design) {
		return new ReadMetadataAction().call(pid, node -> {
			try {
				String style = "short";
				if (!"short".equals(s)) {
					style = "long";
				}
				if (request().accepts("text/html")) {
					if ("frl".equals(design)) {
						return ok(frlResource.render(node));
					}
					List<Node> result = new ArrayList<>();
					result.add(node);
					return ok(resources.render(result));
				} else if (request().accepts("application/json")) {
					return getJsonResult(read.getPartsAsTree(node, style));
				} else {
					List<Node> result = read.getParts(node);
					return asRdf(result);
				}
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});

	}

	private static Result asRdf(List<Node> result) {
		try {
			RDFFormat format = RDFFormat.TURTLE;
			response().setContentType("text/turtle");
			if (request().accepts("application/rdf+xml")) {
				format = RDFFormat.RDFXML;
				response().setContentType("application/rdf+xml");
			} else if (request().accepts("text/turtle")) {
				format = RDFFormat.TURTLE;
				response().setContentType("text/turtle");
			} else if (request().accepts("text/plain")) {
				format = RDFFormat.NTRIPLES;
				response().setContentType("text/plain");
			}
			String rdf = RdfUtils.readRdfToString(
					new ByteArrayInputStream(json(result).getBytes("utf-8")),
					RDFFormat.JSONLD, format, "");
			return ok(rdf);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	private static Result asJson(Map<String, Object> result) {
		return getJsonResult(result);
	}

	@ApiOperation(produces = "application/json,text/html", nickname = "listAllParts", value = "listAllParts", notes = "List resources linked with hasPart", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listAllPartsAsRdf(
			@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			List<Node> result = read.getParts(node);
			return asRdf(result);
		});
	}

	@ApiOperation(produces = "application/json", nickname = "listAllPartsAsJson", value = "listAllPartsAsJson", notes = "List resources linked with hasPart", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listAllPartsAsJson(@PathParam("pid") String pid,
			@QueryParam("style") String style) {
		return new ReadMetadataAction().call(pid, node -> {
			try {
				Map<String, Object> result = read.getPartsAsTree(node, style);
				return asJson(result);
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});
	}

	@ApiOperation(produces = "applicatio/json", nickname = "listSeq", value = "listSeq", notes = "Shows seq data for ordered print of parts.", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listSeq(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setHeader("Access-Control-Allow-Origin", "*");
			String result = read.readSeq(node);
			return ok(result);
		});
	}

	@ApiOperation(produces = "application/json", nickname = "listParents", value = "listParents", notes = "Shows resources linkes with isPartOf", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> listParents(@PathParam("pid") String pid,
			@QueryParam("style") String style) {
		return new ReadMetadataAction().call(pid, node -> {
			List<String> nodeIds = node.getRelatives(IS_PART_OF).stream()
					.map((Link l) -> l.getObject()).collect(Collectors.toList());
			if ("short".equals(style)) {
				return getJsonResult(nodeIds);
			}
			List<Node> result = read.getNodes(nodeIds);
			return getJsonResult(result);
		});
	}

	@ApiOperation(produces = "application/html", nickname = "asHtml", value = "asHtml", notes = "Returns a html display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asHtml(@PathParam("pid") String pid,
			@QueryParam("design") String design) {
		return new ReadMetadataAction().call(pid, node -> {
			try {
				response().setHeader("Content-Type", "text/html; charset=utf-8");
				if ("frl".equals(design)) {
					return ok(frlResource.render(node));
				}
				return ok(resource.render(node, null));
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});
	}

	@ApiOperation(produces = "application/json", nickname = "asJson", value = "asJson", notes = "Returns a json display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asJson(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			return getJsonResult(node.getLd1());
		});
	}

	@ApiOperation(produces = "application/json", nickname = "asJso2n", value = "asJson2", notes = "Returns a json2 display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asJson2(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			return getJsonResult(node.getLd2());
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asOaiDc", value = "asOaiDc", notes = "Returns a oai dc display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asOaiDc(@PathParam("pid") String pid,
			@QueryParam("validate") boolean validate) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setContentType("application/xml");
			Html result = oaidc.render(transform.wgl(pid), node.getLd2());
			String xml = result.toString();
			if (validate) {
				validate(xml, "public/schemas/oai_dc.xsd", null, "public/schemas");
			}
			return ok(result);
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asWgl", value = "asWgl", notes = "Returns metadata for Open Leibniz", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asWgl(@PathParam("pid") String pid,
			@QueryParam("validate") boolean validate) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setContentType("application/xml");
			Html result = wgl.render(transform.wgl(pid), node.getLd2());
			String xml = result.toString();
			if (validate) {
				validate(xml, "public/schemas/oai_wgl.xsd", null, "public/schemas");
			}
			return ok(result);
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asEpicur", value = "asEpicur", notes = "Returns a epicur display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asEpicur(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			String result = transform.epicur(node);
			response().setContentType("application/xml");
			return ok(result);
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asAleph", value = "asAleph", notes = "Returns a aleph xml display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asAleph(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			MabRecord result = transform.aleph(pid);
			response().setContentType("application/xml");
			return ok(mab.render(result));
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asDatacite", value = "asDatacite", notes = "Returns a Datacite display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asDatacite(@PathParam("pid") String pid,
			@QueryParam("validate") boolean validate) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setContentType("application/xml");
			String result = transform.datacite(node, node.getDoi());
			if (validate) {
				try {
					validate(result, "public/schemas/datacite/kernel-4.1/metadata.xsd",
							"https://schema.datacite.org/meta/kernel-4.1/",
							"public/schemas/datacite/kernel-4.1/");
				} catch (Exception e) {
					return JsonMessage(new Message(e, 400));
				}
			}
			return ok(result);
		});

	}

	@ApiOperation(produces = "application/xml", nickname = "asMets", value = "asMets", notes = "Returns a Mets display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asMets(@PathParam("pid") String pid,
			@QueryParam("validate") boolean validate) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setContentType("application/xml");
			Html result =
					mets.render(read.getPartsAsTree(node, "long"), transform.wgl(pid));
			String xml = result.toString();
			if (validate) {
				validate(xml, "public/schemas/mets.xsd", null, "public/schemas/");
			}
			return ok(result);
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asOpenAire", value = "asOpenAire", notes = "Returns a OpenAire display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asOpenAire(@PathParam("pid") String pid,
			@QueryParam("validate") boolean validate) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setContentType("application/xml");
			String result = transform.openaire(pid);
			if (validate) {
				validate(result, "public/schemas/openaire.xsd", null, "public/schemas");
			}
			return ok(result);
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asMods", value = "asMods", notes = "Returns a OpenAire display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asMods(@PathParam("pid") String pid,
			@QueryParam("validate") boolean validate) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setContentType("application/xml");
			String result = transform.mods(pid);
			if (validate) {
				validate(result, "public/schemas/mods-3-7.xsd", null, "public/schemas");
			}
			return ok(result);
		});
	}

	@ApiOperation(produces = "application/xml", nickname = "asCsv", value = "asCsv", notes = "Returns a Csv display of the resource", response = Message.class, httpMethod = "GET")
	public static Promise<Result> asCsv(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setContentType("application/xml");
			return getCsvResult(new ObjectMapper().valueToTree(node.getLd2()));
		});
	}

	@ApiOperation(produces = "application/pdf", nickname = "asPdfa", value = "asPdfa", notes = "Returns a pdfa conversion of a pdf datastream.", httpMethod = "GET")
	public static Promise<Result> asPdfa(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			try {
				String redirectUrl = transform.getPdfaUrl(pid);
				URL url;
				url = new URL(redirectUrl);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				try (InputStream is = connection.getInputStream()) {
					response().setContentType("application/pdf");
					return ok(is);
				}
			} catch (MalformedURLException e) {
				return JsonMessage(new Message(e, 500));
			} catch (IOException e) {
				return JsonMessage(new Message(e, 500));
			}
		});
	}

	@ApiOperation(produces = "text/plain", nickname = "asPdfboxTxt", value = "asPdfboxTxt", notes = "Returns text display of a pdf datastream.", response = String.class, httpMethod = "GET")
	public static Promise<Result> asPdfboxTxt(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			String result = transform.pdfbox(pid).getFulltext();
			response().setHeader("Access-Control-Allow-Origin", "*");
			response().setHeader("Content-Type", "text/plain; charset=utf-8");
			return ok(result);
		});
	}

	@ApiOperation(produces = "text/plain", nickname = "updateOaiSets", value = "updateOaiSets", notes = "Links resource to oai sets and creates new sets if needed", response = String.class, httpMethod = "POST")
	public static Promise<Result> updateOaiSets(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			String result = OaiDispatcher.makeOAISet(node);
			response().setContentType("text/plain");
			return JsonMessage(new Message(result));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "moveUp", value = "moveUp", notes = "Moves the resource to the parents parent. If parent has no parent, a HTTP 406 will be replied.", response = String.class, httpMethod = "POST")
	public static Promise<Result> moveUp(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Node result = modify.moveUp(node);
			return JsonMessage(new Message(json(result)));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "copyMetadata", value = "copyMetadata", notes = "Copy a certain metadata field from an other resource. If no param given the title field of the parent will be copied. ", response = String.class, httpMethod = "POST")
	public static Promise<Result> copyMetadata(@PathParam("pid") String pid,
			@QueryParam("field") String field,
			@QueryParam("copySource") String copySource) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Node result = modify.copyMetadata(node, field, copySource);
			return JsonMessage(new Message(json(result)));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "enrichMetadata", value = "enrichMetadata", notes = "Includes linked resources into metadata", response = String.class, httpMethod = "POST")
	public static Promise<Result> enrichMetadata(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			String result = Enrich.enrichMetadata1(node);
			return JsonMessage(new Message(json(result)));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "enrichMetadata", value = "enrichMetadata", notes = "Includes linked resources into metadata", response = String.class, httpMethod = "POST")
	public static Promise<Result> enrichMetadata2(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			String result = Enrich.enrichMetadata2(node);
			return JsonMessage(new Message(json(result)));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "flatten", value = "flatten", notes = "Copy the title of your parent and move up one level.", response = String.class, httpMethod = "POST")
	public static Promise<Result> flatten(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Node result = modify.flatten(node);
			return JsonMessage(new Message(json(result)));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "flattenAll", value = "flattenAll", notes = "flatten is applied to all descendents of type contentType or type \"file\"(default).", response = String.class, httpMethod = "POST")
	public static Promise<Result> flattenAll(@PathParam("pid") String pid,
			@QueryParam("contentType") String contentType) {
		return new BulkActionAccessor().call((userId) -> {
			List<Node> list = null;
			if (contentType != null && !contentType.isEmpty()) {
				list = Globals.fedora.listComplexObject(pid).stream()
						.filter(n -> contentType.equals(n.getContentType()))
						.collect(Collectors.toList());
			} else {
				list = Globals.fedora.listComplexObject(pid).stream()
						.filter(n -> "file".equals(n.getContentType()))
						.collect(Collectors.toList());
			}
			BulkAction bulk = new BulkAction();
			bulk.executeOnNodes(list, userId, nodes -> {
				return modify.flattenAll(nodes);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json", nickname = "deleteDescendent", value = "deleteDescendent", notes = "deletes all descendents of a certain contentType", response = String.class, httpMethod = "POST")
	public static Promise<Result> deleteDescendent(@PathParam("pid") String pid,
			@QueryParam("contentType") String contentType) {
		return new BulkActionAccessor().call((userId) -> {
			List<Node> list = null;
			if (contentType != null && !contentType.isEmpty()) {
				list = Globals.fedora.listComplexObject(pid).stream()
						.filter(n -> contentType.equals(n.getContentType()))
						.collect(Collectors.toList());
			} else {
				list = Globals.fedora.listComplexObject(pid);
			}
			BulkAction bulk = new BulkAction();
			bulk.executeOnNodes(list, userId, nodes -> {
				return delete.delete(nodes);
			});
			response().setHeader("Transfer-Encoding", "Chunked");
			return ok(bulk.getChunks());
		});
	}

	@ApiOperation(produces = "application/json,text/html", nickname = "getLastModifiedChild", value = "getLastModifiedChild", notes = "Return the last modified object of tree", response = play.mvc.Result.class, httpMethod = "GET")
	public static Promise<Result> getLastModifiedChild(
			@PathParam("pid") String pid,
			@QueryParam("contentType") String contentType) {
		return new ReadMetadataAction().call(pid, node -> {
			try {
				response().setHeader("Access-Control-Allow-Origin", "*");
				Node result = read.getLastModifiedChild(node, contentType);
				if (request().accepts("text/html")) {
					response().setHeader("Content-Type", "text/html; charset=utf-8");
					return ok(resource.render(result, null));
				}
				return getJsonResult(result.getLd2());
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});
	}

	public static Promise<Result> updateConf(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			try {
				Object o = request().body().asJson();
				Gatherconf conf = null;
				if (o != null) {
					play.Logger.debug("o.toString=" + o.toString());
					conf = MyController.mapper.readValue(o.toString(), Gatherconf.class);
					// hier die neue conf auch im JobDir von Heritrix ablegen
					conf.setName(pid);
					conf.setRobotsPolicy(RobotsPolicy.ignore);
					play.Logger.debug("conf.toString=" + conf.toString());
					String result = modify.updateConf(node, conf.toString());
					// Neue urlHist anlegen, falls es noch keine gibt (nur dann)
					if (node.getUrlHist() == null) {
						UrlHist urlHist = new UrlHist(conf.getUrl());
						String urlHistResult =
								modify.updateUrlHist(node, urlHist.toString());
						play.Logger.debug("URL-Historie neu angelegt: " + urlHistResult);
					}
					Globals.heritrix.createJobDir(conf);
					return JsonMessage(new Message(result, 200));
				}
				throw new HttpArchiveException(409,
						"Please provide JSON config in request body.");
			} catch (Exception e) {
				throw new HttpArchiveException(500, e);
			}
		});
	}

	public static Promise<Result> listConf(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setHeader("Access-Control-Allow-Origin", "*");
			String result = read.readConf(node);
			return ok(result);
		});
	}

	public static Promise<Result> listUrlHist(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			response().setHeader("Access-Control-Allow-Origin", "*");
			String result = read.readUrlHist(node);
			return ok(result);
		});
	}

	public static Promise<Result> createVersion(@PathParam("pid") String pid) {
		try {
			play.Logger.debug("Starting Resource.createVersion()");
			Node node = readNodeOrNull(pid);
			Gatherconf conf = Gatherconf.create(node.getConf());
			if (conf.hasUrlMoved(node)) {
				return Promise.promise(() -> {
					return JsonMessage(WebgatherUtils.createInvalidUrlMessage(conf));
				});
			}
			new WebgatherUtils().startCrawl(node);
			/* KS20200525 war: create.createWebpageVersion(node); */
			return Promise.promise(() -> {
				return JsonMessage(
						new Message("Neuen Webcrawl zur Website " + pid + " angefangen."));
			});
		} catch (Exception e) {
			play.Logger.error(e.toString());
			return Promise.promise(() -> {
				return JsonMessage(new Message(json(e)));
			});
		}
	}

	public static Promise<Result> importVersion(@PathParam("pid") String pid,
			@QueryParam("versionPid") String versionPid,
			@QueryParam("label") String label) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Node result = create.importWebpageVersion(node, versionPid, label);
			return getJsonResult(result);
		});
	}

	public static Promise<Result> postVersion(@PathParam("pid") String pid,
			@QueryParam("versionPid") String versionPid,
			@QueryParam("dataDir") String dataDir,
			@QueryParam("timestamp") String timestamp,
			@QueryParam("filename") String filename) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Node result = create.postWebpageVersion(node, versionPid, dataDir,
					timestamp, filename);
			return getJsonResult(result);
		});
	}

	/**
	 * Dieser Endpunkt fügt eine Forschungsdaten-Ressource (Datei) zu einem
	 * Forschungsdatenobjekt hinzu.
	 */
	public static Promise<Result> createResearchData(@PathParam("pid") String pid,
			@QueryParam("collectionUrl") String collectionUrl,
			@QueryParam("subPath") String subPath,
			@QueryParam("filename") String filename,
			@QueryParam("resourcePid") String resourcePid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Node result = create.createResearchData(node, collectionUrl, subPath,
					filename, resourcePid);
			return getJsonResult(result);
		});
	}

	@ApiOperation(produces = "application/json", nickname = "linkVersion", value = "linkVersion", response = Result.class, httpMethod = "POST")
	public static Promise<Result> linkVersion(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			JsonNode jsn = new ObjectMapper().valueToTree(request().body().asJson());
			Node result = create.linkWebpageVersion(node, jsn);
			return getJsonResult(result);
		});
	}

	/**
	 * Dieser Endpoint realiert ein gewünschtes Zugriffsrecht (AccessScheme) für
	 * eine WebpageVersion (Webschnitt). Aktuell unterstützt werden die
	 * Zugriffsrechte "öffentlich" (public) und "eingeschränkt" (restricted).
	 * "eingeschränkt" bedeutet, dass der Webschnitt nur für bestimmte
	 * IP-Adressen, die i.d.R. im Lesesaal des LBZ liegen, abgerufen (z.B. Replay
	 * in Wayback) werden kann. "öffentlich" bedeutet, dass der Webschnitt
	 * (Inhalt) für jedermann öffentlich im Netz dargestellt werden kann.
	 * 
	 * @param pid Die PID des Webschnitts
	 * @param accessScheme Zugrifssrecht, kodiert als "public", "restricted", ...
	 * @return
	 */
	public static Promise<Result> publishWebpageVersion(
			@PathParam("pid") String pid,
			@QueryParam("accessScheme") String accessScheme) {
		try {
			return new ModifyAction().call(pid, userId -> {
				Node node = readNodeOrNull(pid);
				WebsiteVersionPublisher wvp = new WebsiteVersionPublisher();
				wvp.publishWebpageVersion(node, accessScheme);
				return JsonMessage(new Message("WebpageVersion " + node.getPid()
						+ " wurde auf Zugriffsrecht " + accessScheme + " gesetzt.", 200));
			});
		} catch (Exception e) {
			play.Logger.error(e.toString());
			return Promise.promise(() -> {
				return JsonMessage(new Message(
						"WebpageVersion " + pid + " konnte nicht auf Zugriffsrecht "
								+ accessScheme + " gesetzt werden!",
						200));
			});

		}
	}

	public static Promise<Result> getStatus(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, node -> {
			return getJsonResult(read.getStatus(node));
		});
	}

	@ApiOperation(produces = "application/json,text/html,text/csv", nickname = "listResources", value = "listResources", notes = "Returns a list of ids", httpMethod = "GET")
	public static Promise<Result> listResourcesStatus(
			@QueryParam("namespace") String namespace,
			@QueryParam("contentType") String contentType,
			@QueryParam("from") int from, @QueryParam("until") int until) {
		return new ListAction().call((userId) -> {
			try {
				String ns = namespace;
				if (ns.isEmpty()) {
					ns = Globals.defaultNamespace;
				}
				List<Node> nodes = read.listRepo(contentType, ns, from, until);
				List<Map<String, Object>> stati = read.getStatus(nodes);
				if (request().accepts("text/html")) {
					return htmlStatusList(stati);
				}
				return getJsonResult(stati);
			} catch (HttpArchiveException e) {
				return JsonMessage(new Message(e, e.getCode()));
			} catch (Exception e) {
				return JsonMessage(new Message(e, 500));
			}
		});
	}

	private static Result htmlStatusList(List<Map<String, Object>> stati) {
		try {
			response().setHeader("Access-Control-Allow-Origin", "*");
			response().setHeader("Content-Type", "text/html; charset=utf-8");
			return ok(status.render(json(stati)));
		} catch (HttpArchiveException e) {
			return HtmlMessage(new Message(e, e.getCode()));
		} catch (Exception e) {
			return HtmlMessage(new Message(e, 500));
		}
	}

	@ApiOperation(produces = "application/json", nickname = "addDoi", value = "addDoi", notes = "Adds a Doi and performes a registration at Datacite", response = String.class, httpMethod = "POST")
	public static Promise<Result> addDoi(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Map<String, Object> result = modify.addDoi(node);
			return JsonMessage(new Message(json(result)));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "updateDoi", value = "updateDoi", notes = "Update the Doi's metadata at Datacite", response = String.class, httpMethod = "POST")
	public static Promise<Result> updateDoi(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Map<String, Object> result = modify.updateDoi(node);
			return JsonMessage(new Message(json(result)));
		});
	}

	@ApiOperation(produces = "application/json", nickname = "updateDoi", value = "updateDoi", notes = "Update the Doi's metadata at Datacite", response = String.class, httpMethod = "POST")
	public static Promise<Result> replaceDoi(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			Node node = readNodeOrNull(pid);
			Map<String, Object> result = modify.replaceDoi(node);
			return JsonMessage(new Message(json(result)));
		});
	}

	/**
	 * @param pid the resource identifier
	 * @param format the file format in which metadata wil be provided
	 * @param topicId unclear
	 * @return
	 */
	@ApiOperation(produces = "application/json", nickname = "edit", value = "edit", notes = "get a form to edit the resources metadata", response = String.class, httpMethod = "POST")
	public static Promise<Result> edit(@PathParam("pid") String pid,
			@QueryParam("format") String format,
			@QueryParam("topicId") String topicId) {
		return new ModifyAction().call(pid, userId -> {
			try {
				Node node = readNodeOrNull(pid);
				if ("monograph".equals(node.getContentType())) {
					return redirect(routes.Forms.getCatalogForm(node.getPid()));
				}
				String zettelType = node.getContentType();
				String rdf = RdfUtils.readRdfToString(
						new ByteArrayInputStream(node.toString().getBytes("utf-8")),
						RDFFormat.JSONLD, RDFFormat.RDFXML, node.getAggregationUri());
				// rdf = java.net.URLEncoder.encode(rdf, "utf-8");
				return ok(edit.render(zettelType, "ntriples", pid, pid + ".rdf", rdf));
			} catch (Exception e) {
				return JsonMessage(new Message(json(e)));
			}
		});
	}

	@ApiOperation(produces = "text/html", nickname = "listTitle", value = "listTitle", notes = "get an extended title", response = String.class, httpMethod = "GET")
	public static Promise<Result> listTitle(@PathParam("pid") String pid) {
		return new ReadMetadataAction().call(pid, userId -> {
			try {
				Node node = readNodeOrNull(pid);
				return ok(getTitle.render(node.getLd2()));
			} catch (Exception e) {
				return JsonMessage(new Message(json(e)));
			}
		});
	}

	public static Promise<Result> createObjectWithMetadata() {
		return new CreateAction().call(userId -> {
			try {
				DynamicForm form = Form.form().bindFromRequest();
				String alephId = form.get("alephId");
				String namespace = form.get("namespace");
				String pid = form.get("pid");
				ToScienceObject object = new ToScienceObject();
				object.setContentType("monograph");
				Node node = null;
				if (pid != null && !pid.isEmpty()) {
					node = read.readNode(pid);
				} else {
					node = create.createResource(namespace, object);
				}
				String message = modify.lobidify2(node, alephId);
				flash("message", message);
				return redirect(routes.Resource.listResource(node.getPid(), null));
			} catch (Exception e) {
				return JsonMessage(new Message(json(e)));
			}
		});
	}

	// public static Promise<Result> createFileObjectWithMetadata() {
	// return new CreateAction().call(userId -> {
	// try {
	// MultipartFormData body = request().body().asMultipartFormData();
	// FilePart d = body.getFile("data");
	// Map<String, String[]> form = body.asFormUrlEncoded();
	// String title = form.get("title")[0];
	// String pid = form.get("pid")[0];
	// String namespace = form.get("namespace")[0];
	// String md5 = form.get("md5")[0];
	// RegalObject object = new RegalObject();
	// object.setContentType("file");
	// Node node = null;
	// if (pid != null && !pid.isEmpty()) {
	// node = read.readNode(pid);
	// } else {
	// node = create.createResource(namespace, object);
	// }
	// if (d == null) {
	// return JsonMessage(new Message("Missing File.", 400));
	// }
	// String mimeType = d.getContentType();
	// String name = d.getFilename();
	// try (FileInputStream content = new FileInputStream(d.getFile())) {
	// modify.updateData(pid, content, mimeType, name, md5);
	// flash("message",
	// "File uploaded! Type: " + mimeType + ", Name: " + name);
	// return redirect(routes.Resource.listResource(node.getPid(), null));
	// }
	// } catch (Exception e) {
	// return JsonMessage(new Message(json(e)));
	// }
	// });
	// }

	public static Promise<Result> getUploadForm(String pid) {
		return new CreateAction().call(userId -> {
			try {
				Node node = read.internalReadNode(pid);
				return ok(views.html.upload.render(node));
			} catch (Exception e) {
				return JsonMessage(new Message(json(e)));
			}
		});
	}

	public static Promise<Result> confirmNewUrl(@PathParam("pid") String pid) {
		return new ModifyAction().call(pid, userId -> {
			try {
				play.Logger.debug("Beginne Methode \"confirmNewUrl\"");
				Node node = readNodeOrNull(pid);
				play.Logger.debug("Read node.");
				Gatherconf conf = Gatherconf.create(node.getConf());
				play.Logger.debug("Got Gatherconf.");
				String urlNew = conf.getUrlNew();
				if (urlNew == null) {
					throw new RuntimeException("Keine neue URL bekannt!");
				}
				String urlOld = conf.getUrl();
				conf.setUrl(urlNew);
				conf.setInvalidUrl(false);
				conf.setHttpResponseCode(0);
				conf.setUrlNew((String) null);
				// Die URL-Historie weiterschreiben
				play.Logger.debug("About to continue URL-History.");
				String msg = null;
				UrlHist urlHist = null;
				if (node.getUrlHist() == null) {
					play.Logger.warn(
							"Keine URL-Historie vorhanden ! Lege eine neue URL-Umzugshistorie an !");
					urlHist = new UrlHist(urlOld, new Date());
					urlHist.addUrlHistEntry(urlNew);
					play.Logger.debug(
							"First urlHistEntry has endDate: " + WebgatherUtils.dateFormat
									.format(urlHist.getUrlHistEntry(0).getEndDate()));
					String urlHistResult = modify.updateUrlHist(node, urlHist.toString());
					play.Logger.debug("URL-Historie neu angelegt: " + urlHistResult);
				} else {
					play.Logger.debug("Creating urlHist.");
					play.Logger.debug("Former urlHist: " + node.getUrlHist());
					urlHist = UrlHist.create(node.getUrlHist());
					play.Logger.debug("Former urlHist recreated, urlHist.toString()="
							+ urlHist.toString());
					// Prüfung, ob man auch wirklich den richtigen Eintrag erwischt
					String urlLatest = urlHist
							.getUrlHistEntry(urlHist.getUrlHistEntries().size() - 1).getUrl();
					play.Logger
							.debug("Neueste URL in URL-Historie gefunden: " + urlLatest);
					if (!urlLatest.equals(urlOld)) {
						msg =
								"Neuester Eintrag in URL Historie stimmt nicht mit bisherger URL überein !! URL-Hist: "
										+ urlLatest + " vs. bisherige URL: " + urlOld;
						play.Logger.warn(msg);
					}
					play.Logger.debug("urlHist überprüft.");
					urlHist.updateLatestUrlHistEntry(new Date());
					urlHist.addUrlHistEntry(urlNew);
					String urlHistResult = modify.updateUrlHist(node, urlHist.toString());
					play.Logger.info("URL-Historie aktualsiert: " + urlHistResult);
				}
				// Jetzt Update der Gatherconf
				msg = modify.updateConf(node, conf.toString());
				play.Logger.info(msg);
				// return getJsonResult(conf); für Aufruf von Kommandozeile OK, aber
				// nicht für Aufruf über API - muss code und text haben
				return JsonMessage(new Message(
						"URL wurde umgezogen von " + urlOld + " nach " + conf.getUrl(),
						200));
			} catch (Exception e) {
				play.Logger.error(e.toString());
				return JsonMessage(new Message(json(e)));
			}
		});
	}
}
