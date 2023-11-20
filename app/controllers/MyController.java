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

import helper.HttpArchiveError;
import helper.HttpArchiveException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import models.Globals;
import models.Message;
import models.Node;
import play.Play;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Result;
import views.Helper;
import views.html.message;
import actions.Activate;
import actions.Create;
import actions.Delete;
import actions.Index;
import actions.Modify;
import actions.Read;
import actions.Transform;
import akka.event.slf4j.Logger;
import archive.fedora.XmlUtils;
import authenticate.Role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

/**
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 */
public class MyController extends Controller {

	/**
	 * private metadata is only visible to admins and editors
	 */
	public final static String METADATA_ACCESSOR_PRIVATE = "private";
	/**
	 * public metadata is visible to everyone
	 */
	public final static String METADATA_ACCESSOR_PUBLIC = "public";

	/**
	 * public data is visible to everyone
	 */
	public final static String DATA_ACCESSOR_PUBLIC = "public";
	/**
	 * private data is visible to admins and editors
	 */
	public final static String DATA_ACCESSOR_PRIVATE = "private";
	/**
	 * restricted data is visible to admins, editors, readers, subscribers and
	 * remotes
	 */
	public final static String DATA_ACCESSOR_RESTRICTED = "restricted";
	/**
	 * single data is visible to admins, editors, and subscribers
	 */
	public final static String DATA_ACCESSOR_SINGLE = "single";
	/**
	 * remote data is visible to admins, editors, readers, subscribers and remotes
	 */
	public final static String DATA_ACCESSOR_REMOTE = "remote";

	/**
	 * a mapper for all
	 */
	public static ObjectMapper mapper = JsonUtil.mapper();

	static Read read = new Read();
	static Create create = new Create();
	static Index index = new Index();
	static Modify modify = new Modify();
	static Delete delete = new Delete();
	static Activate activate = new Activate();
	static Transform transform = new Transform();

	/**
	 * @return Html or Json Output
	 */
	public static Result AccessDenied() {
		ctx().session().put("CURRENT_PAGE", request().uri());
		Message msg = new Message("Access Denied!", 401);
		play.Logger.debug(
				"\nResponse: " + msg.toString() + "\nSession " + ctx().session());
		if (request().accepts("text/html")) {
			flash("message", "You must be logged in to perform this action!");
			return redirect(routes.Forms.getLoginForm());
		} else {
			return JsonMessage(msg);
		}
	}

	protected static void setJsonHeader() {
		response().setHeader("Access-Control-Allow-Origin", "*");
		response().setContentType("application/json");
	}

	protected static Node readNodeOrNull(String pid) {
		try {
			return read.readNode(pid);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @param obj an arbitrary object
	 * @return json serialization of obj
	 */
	public static Result getJsonResult(Object obj) {
		setJsonHeader();
		try {
			return ok(json(obj));
		} catch (Exception e) {
			play.Logger.error("", e);
			return internalServerError("Not able to create response!");
		}
	}

	protected static Result getCsvResults(JsonNode hitMap) {
		String[] header = new String[] { "id", "title", "contributions", "issued",
				"contentType", "collectionOne", "created", "doi", "alephId", "url",
				"publisherVersion" };
		try (Writer output = new StringWriter();
				ICsvMapWriter mapWriter = new org.supercsv.io.CsvMapWriter(output,
						CsvPreference.EXCEL_PREFERENCE);) {
			mapWriter.writeHeader(header);
			hitMap.forEach(hit -> {
				try {
					Map<String, Object> result = createCsvLine(hit, header);
					mapWriter.write(result, header);
				} catch (Exception e) {
					play.Logger.warn("", e);
				}
			});
			output.flush();
			mapWriter.flush();
			response().setContentType("text/csv");
			return ok(output.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	protected static Result getCsvResult(JsonNode hit) {
		String[] header = new String[] { "id", "title", "contributions", "issued",
				"contentType", "collectionOne", "created", "doi", "alephId", "url",
				"publisherVersion" };
		try (Writer output = new StringWriter();
				ICsvMapWriter mapWriter = new org.supercsv.io.CsvMapWriter(output,
						CsvPreference.EXCEL_PREFERENCE);) {
			mapWriter.writeHeader(header);
			try {
				Map<String, Object> result = createCsvLine(hit, header);
				mapWriter.write(result, header);
			} catch (Exception e) {
				play.Logger.warn("", e);
			}
			output.flush();
			mapWriter.flush();
			response().setContentType("text/csv");
			return ok(output.toString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private static Map<String, Object> createCsvLine(JsonNode hit,
			String[] header) {
		Map<String, Object> result = new HashMap<>();
		String id;
		String title;
		String contributions;
		String issued;
		String contentType;
		String collectionOne;
		String created;
		String doi;
		String alephId;
		String url;
		String publisherVersion;
		id = hit.at("/@id").asText();
		title = hit.at("/title/0").asText();
		contributions = getContributions(hit) + "";
		issued = hit.at("/issued").asText();
		url = Globals.protocol + Globals.server + "/resource/" + id;
		contentType = hit.at("/contentType").asText();
		collectionOne = getCollectionOne(hit);
		created = hit.at("/isDescribedBy/created").asText();
		doi = hit.at("/doi").asText();
		alephId = hit.at("/hbzId/0").asText();
		publisherVersion = hit.at("/publisherVersion/0/@id").asText();
		result.put(header[0], id);
		result.put(header[1], title);
		result.put(header[2], contributions);
		result.put(header[3], issued);
		result.put(header[4], contentType);
		result.put(header[5], collectionOne);
		result.put(header[6], created);
		result.put(header[7], doi);
		result.put(header[8], alephId);
		result.put(header[9], url);
		result.put(header[10], publisherVersion);
		play.Logger.debug("" + result);
		return result;
	}

	private static String getCollectionOne(JsonNode hit) {
		String label = hit.at("/collectionOne/0/prefLabel").asText();
		String gndid = hit.at("/collectionOne/0/gndIdentifier").asText();
		return label + " (" + gndid + ")";
	}

	private static List<String> getContributions(JsonNode hitMap) {
		List<String> result = new ArrayList<>();
		hitMap.at("/contribution").forEach(n -> {
			String label = n.at("/agent/0/prefLabel").textValue();
			String gndid = n.at("/agent/0/gndIdentifier").textValue();
			result.add(label + " (" + gndid + ")");
		});
		if (result.isEmpty()) {
			hitMap.at("/creator").forEach(c -> {
				String label = c.at("/prefLabel").asText();
				String uri = c.at("/@id").asText();
				String gndid = uri.substring(uri.lastIndexOf('/') + 1);
				result.add(label + " (" + gndid + ")");
			});
			hitMap.at("/contributor").forEach(c -> {
				String label = c.at("/prefLabel").asText();
				String uri = c.at("/@id").asText();
				String gndid = uri.substring(uri.lastIndexOf('/') + 1);
				result.add(label + " (" + gndid + ")");
			});
		}
		return result;
	}

	protected static String json(Object obj) {
		try {
			StringWriter w = new StringWriter();
			mapper.writeValue(w, obj);
			String result = w.toString();
			return result;
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * @param msg the msg will be rendered as html using message view
	 * @return a html rendering of msg
	 */
	public static Result HtmlMessage(Message msg) {
		play.Logger.debug("\nResponse: " + msg.toString());
		response().setContentType("text/html");
		return status(msg.getCode(), message.render(msg.toString()));
	}

	/**
	 * @param msg the msg will be rendered as json
	 * @return a json rendering of msg
	 */
	public static Result JsonMessage(Message msg) {
		response().setHeader("Access-Control-Allow-Methods",
				"POST, GET, PUT, DELETE");
		response().setHeader("Access-Control-Max-Age", "3600");
		response().setHeader("Access-Control-Allow-Headers",
				"Origin, X-Requested-With, Content-Type, Accept, Authorization, X-Auth-Token");
		response().setHeader("Access-Control-Allow-Credentials", "true");
		response().setHeader("Content-Type", "application/json; charset=utf-8");
		return status(msg.getCode(), msg.toString());
	}

	/**
	 * @param accessScheme the accessScheme of the object
	 * @param role the role of the user
	 * @return true if the user is allowed to read the object
	 */
	public static boolean readData_accessIsAllowed(String accessScheme,
			Role role) {
		if (!Role.ADMIN.equals(role)) {
			if (DATA_ACCESSOR_PUBLIC.equals(accessScheme)) {
				return true;
			} else if (DATA_ACCESSOR_RESTRICTED.equals(accessScheme)) {
				if (isWhitelisted(request().remoteAddress())) {
					play.Logger.info("IP " + request().remoteAddress()
							+ " is white listed. Access to restricted data granted.");
					return true;
				}
				if (Role.READER.equals(role) || Role.SUBSCRIBER.equals(role)
						|| Role.REMOTE.equals(role)) {
					if (isWhitelisted(request().getHeader("UserIp"))) {
						play.Logger.info("IP " + request().getHeader("UserIp")
								+ " is white listed. Access to restricted data granted.");
						return true;
					}
				}
				if (Role.EDITOR.equals(role)) {
					return true;
				}
			} else if (DATA_ACCESSOR_PRIVATE.equals(accessScheme)) {
				if (Role.EDITOR.equals(role))
					return true;
			} else if (DATA_ACCESSOR_SINGLE.equals(accessScheme)) {
				if (Role.EDITOR.equals(role)) {// ||
					// SUBSCRIBER_ROLE.equals(role))
					// {
					return true;
				}
			} else if (DATA_ACCESSOR_REMOTE.equals(accessScheme)) {
				if (Role.EDITOR.equals(role)) { // || REMOTE_ROLE.equals(role)
					// || Role.Reader.equals(role)
					// ||
					// SUBSCRIBER_ROLE.equals(role))
					// {
					return true;
				}
			}
		} else {// if enter here you are admin
			return true;
		}
		return false;
	}

	private static boolean isWhitelisted(String remoteAddress) {
		return Globals.ipWhiteList.containsKey(remoteAddress);
	}

	/**
	 * @param publishScheme the publishScheme of the object
	 * @param role the role of the user
	 * @return true if the user is allowed to read the object
	 */
	public static boolean readMetadata_accessIsAllowed(String publishScheme,
			Role role) {
		if (!Role.ADMIN.equals(role)) {
			if (METADATA_ACCESSOR_PUBLIC.equals(publishScheme)) {
				return true;
			} else if (METADATA_ACCESSOR_PRIVATE.equals(publishScheme)) {
				if (Role.EDITOR.equals(role)) {
					return true;
				}
			}
		} else {// if enter here you are admin
			return true;
		}
		return false;
	}

	/**
	 * @param role the role of the user
	 * @return true if the user is allowed to modify the object
	 */
	public static boolean modifyingAccessIsAllowed(Role role) {
		if (Role.ADMIN.equals(role) || Role.EDITOR.equals(role))
			return true;
		return false;
	}

	interface NodeAction {
		Result exec(Node node);
	}

	interface Action {
		Result exec(String userId);
	}

	/**
	 * @author Jan Schnasse Kontrolle der Zugriffsberechtigungen.
	 *         Veröffentlichungsschema und Nutzer-Rolle müssen zusammen passen,
	 *         sonst "Accss Denied"
	 */
	public static class ReadMetadataAction {
		Promise<Result> call(String pid, NodeAction ca) {
			return Promise.promise(() -> {
				try {
					Node node = null;
					if (pid != null) {
						node = read.readNode(pid);
						Role role = Role.valueOf(ctx().session().get("role"));
						String publishScheme = node.getPublishScheme();
						if (!readMetadata_accessIsAllowed(publishScheme, role)) {
							return AccessDenied();
						}
					}
					return ca.exec(node);
				} catch (HttpArchiveException e) {
					if (request().accepts("text/html")) {
						return HtmlMessage(new Message(e, e.getCode()));
					}
					return JsonMessage(new Message(e, e.getCode()));
				} catch (HttpArchiveError e) {
					if (request().accepts("text/html")) {
						return HtmlMessage(new Message(e, e.getCode()));
					}
					return JsonMessage(new Message(e, e.getCode()));
				} catch (Exception e) {
					if (request().accepts("text/html")) {
						return HtmlMessage(new Message(e, 500));
					}
					return JsonMessage(new Message(e, 500));
				}
			});
		}
	}

	/**
	 * @author Jan Schnasse
	 *
	 */
	public static class ReadDataAction {
		Promise<Result> call(String pid, NodeAction ca) {
			return Promise.promise(() -> {
				try {
					Role role = findRole();
					Node node = null;
					if (pid != null) {
						node = read.readNode(pid);

						String accessScheme = node.getAccessScheme();
						if (!readData_accessIsAllowed(accessScheme, role)) {
							return AccessDenied();
						}
					}
					return ca.exec(node);
				} catch (HttpArchiveException e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (HttpArchiveError e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (Exception e) {
					return JsonMessage(new Message(e, 500));
				}
			});
		}
	}

	private static Role findRole() {
		String roleString = session().get("role");
		if (roleString == null || roleString.isEmpty()) {
			roleString = Role.GUEST.toString();
		}
		play.Logger.debug("Access with role " + roleString);
		return Role.valueOf(roleString);
	}

	/**
	 * @author Jan Schnasse
	 *
	 */
	public static class ListAction {
		Promise<Result> call(Action ca) {
			return Promise.promise(() -> {
				try {
					Role role = findRole();
					if (!readMetadata_accessIsAllowed("private", role)) {
						return AccessDenied();
					}
					return ca.exec(request().getHeader("UserId"));
				} catch (HttpArchiveException e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (HttpArchiveError e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (Exception e) {
					return JsonMessage(new Message(e, 500));
				}
			});
		}
	}

	/**
	 * @author Jan Schnasse
	 *
	 */
	public static class ModifyAction {
		Promise<Result> call(String pid, Action ca) {
			return Promise.promise(() -> {
				try {
					Role role = findRole();
					String userId = request().getHeader("UserId");
					play.Logger.debug(
							"Try to access with role: " + role + " and userId " + userId);
					if (!modifyingAccessIsAllowed(role)) {
						play.Logger.debug("Schreibender Zugriff nicht erlaubt");
						return AccessDenied();
					} else {
						play.Logger.debug("Controller Action Exec");
						Result result = ca.exec(userId);
						play.Logger.debug("Controller Action Result:" + result.toString());
						if (userId != null && !userId.equals("0") && !userId.equals("1")
								&& !userId.equals("UrnAllocator")) {
							play.Logger.info(json(modify
									.setObjectTimestamp(read.readNode(pid), new Date(), userId)));
						}
						return result;
					}
				} catch (HttpArchiveException e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (Exception e) {
					return JsonMessage(new Message(e, 500));
				}
			});
		}
	}

	/**
	 * @author Jan Schnasse
	 *
	 */
	public static class IndexAction {
		Promise<Result> call(String pid, NodeAction ca) {
			return Promise.promise(() -> {
				try {
					Role role = findRole();
					play.Logger.debug("Try to access with role: " + role + ".");
					if (!modifyingAccessIsAllowed(role)) {
						return AccessDenied();
					}
					Node node = null;
					try {
						node = read.readNode(pid);
					} catch (Exception e) {
						play.Logger.debug("Try to modify resource that can not be read!",
								e);
					}
					return ca.exec(node);
				} catch (HttpArchiveException e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (Exception e) {
					return JsonMessage(new Message(e, 500));
				}
			});
		}
	}

	/**
	 * @author Jan Schnasse
	 *
	 */
	public static class CreateAction {
		Promise<Result> call(Action ca) {
			return Promise.promise(() -> {
				try {
					Role role = findRole();
					if (!modifyingAccessIsAllowed(role)) {
						return AccessDenied();
					}
					return ca.exec(request().getHeader("UserId"));
				} catch (HttpArchiveException e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (HttpArchiveError e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (Exception e) {
					return JsonMessage(new Message(e, 500));
				}
			});
		}
	}

	/**
	 * @author Jan Schnasse
	 *
	 */
	public static class BulkActionAccessor {
		Promise<Result> call(Action ca) {
			return Promise.promise(() -> {
				try {
					Role role = findRole();
					play.Logger.debug("role={}", role);
					if (!modifyingAccessIsAllowed(role)) {
						return AccessDenied();
					}
					return ca.exec(request().getHeader("UserId"));
				} catch (HttpArchiveException e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (HttpArchiveError e) {
					return JsonMessage(new Message(e, e.getCode()));
				} catch (Exception e) {
					return JsonMessage(new Message(e, 500));
				}
			});
		}
	}

	/**
	 * @param map
	 * @return a pritn of the map
	 */
	public static String mapToString(Map<String, String> map) {
		StringBuilder sb = new StringBuilder();
		Iterator<Entry<String, String>> iter = map.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, String> entry = iter.next();
			sb.append(entry.getKey());
			sb.append('=').append('"').append(entry.getValue()).append('"');
			if (iter.hasNext()) {
				sb.append("\n\t'");
			}
		}
		return sb.toString();

	}

	/**
	 * @param d a string in format "yyyyy-mm-dd"
	 * @return a Date representation of the String passed as param
	 */
	public static Date createDateFromString(String d) {
		try {
			SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");
			Date date = dt.parse(d);
			return date;
		} catch (ParseException e) {
			throw new HttpArchiveException(400, e);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	protected static String getCurrentDate() {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		Date date = new Date();
		return dateFormat.format(date);
	}

	public static void validate(String xml, String schema, String baseUri,
			String localPath) {
		try {
			XmlUtils.validator.validate(
					new ByteArrayInputStream(xml.getBytes("utf-8")),
					Play.application().resourceAsStream(schema), baseUri, localPath);
		} catch (Exception e) {
			throw new HttpArchiveException(406, e.getMessage() + "\n" + xml);
		}
	}
}
