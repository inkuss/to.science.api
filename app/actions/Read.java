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
package actions;

import static archive.fedora.FedoraVocabulary.HAS_PART;
import static archive.fedora.FedoraVocabulary.IS_PART_OF;
import static archive.fedora.Vocabulary.*;

import helper.BtrixWebclient;
import helper.HttpArchiveException;
import helper.JsonMapper;
import helper.WebgatherUtils;
import helper.Webgatherer;
import helper.WpullCrawl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.stream.Collectors;

import models.CrawlerModel.CrawlControllerState;
import models.DublinCoreData;
import models.Gatherconf;
import models.Globals;
import models.Link;
import models.Node;
import models.Pair;
import models.UrlHist;
import models.Urn;
import net.sf.ehcache.pool.sizeof.annotations.IgnoreSizeOf;

import org.apache.commons.codec.binary.Base64;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.json.JSONObject;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.w3c.dom.Element;

import play.Logger;
import play.Play;
import archive.fedora.FedoraVocabulary;
import archive.fedora.RdfUtils;
import archive.fedora.UrlConnectionException;
import archive.fedora.XmlUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.wordnik.swagger.core.util.JsonUtil;

/**
 * 
 * @author Jan Schnasse
 *
 */
@IgnoreSizeOf
public class Read extends RegalAction {

	/**
	 * @param pid the will be read to the node
	 * @return a Node containing the data from the repository
	 */
	public Node readNode(String pid) {
		Node n = internalReadNode(pid);
		addLabelsForParts(n);
		writeNodeToCache(n);
		return n;
	}

	/**
	 * returns the lastModified child of the whole tree
	 * 
	 * @param node
	 * @param contentType if set, only parts of specific type will be analyzed
	 * @return node
	 */
	public Node getLastModifiedChild(Node node, String contentType) {
		if (contentType == null || contentType.isEmpty()) {
			return getLastModifiedChild(node);
		}
		Node oldestNode = null;
		for (Node n : getParts(node)) {
			if (contentType.equals(n.getContentType())) {
				oldestNode = compareDates(n, oldestNode);
			}
		}
		if (oldestNode == null)
			return node;
		return oldestNode;
	}

	/**
	 * Liefert das zuletzt modifizierte Kind vom Type "contentType". Wie
	 * getLastModifiedChild, jedoch wir Null zurück gegeben, falls: - der
	 * Inhaltstyp leer ist, oder - kein Kind von dem gewünschten Inhaltstyp
	 * gefunden wurde. Die Methode getLastModifiedChild liefert dagegen in diesem
	 * Falle ein Kind irgendeinen Types bzw. den Knoten selber.
	 * 
	 * @param node Der Knoten, dessen Kinder gesucht werden.
	 * @param contentType der Inhaltstyp, von dem das Kind sein muss.
	 * @return node
	 */
	public Node getLastModifiedChildOrNull(Node node, String contentType) {
		play.Logger.debug("BEGIN getLastModifiedChildOrNull for pid: "
				+ node.getPid() + "; contentType: " + contentType);
		if (contentType == null || contentType.isEmpty()) {
			return null;
		}
		Node oldestNode = null;
		for (Node n : getParts(node)) {
			play.Logger.debug("found child with pid: " + n.getPid()
					+ "; contentType: " + n.getContentType());
			if (contentType.equals(n.getContentType())) {
				oldestNode = compareDates(n, oldestNode);
				play.Logger.debug("oldest node is now: pid: " + oldestNode.getPid());
			}
		}
		if (oldestNode == null)
			return null;
		play.Logger.debug("returning oldest node with pid: " + oldestNode.getPid());
		return oldestNode;

	}

	private Node compareDates(Node currentNode, Node oldestNode) {
		Date currentNodeDate = currentNode.getObjectTimestamp();
		if (currentNodeDate == null)
			currentNodeDate = currentNode.getLastModified();
		if (oldestNode != null) {
			Date oldestNodeDate = oldestNode.getObjectTimestamp();
			if (oldestNodeDate == null)
				oldestNodeDate = oldestNode.getLastModified();
			if (currentNodeDate.after(oldestNodeDate)) {
				oldestNode = currentNode;
			}
			// Special case: Since input has not to be sorted in any way, we
			// need a condition to prefer child nodes over parent nodes.
			// If both nodes have the same timestamp, the currentNode
			// will win, if it is NOT a parent the oldest.
			if (currentNodeDate.equals(oldestNodeDate)) {
				if (!currentNode.getPid().equals(oldestNode.getParentPid())) {
					oldestNode = currentNode;
				}
			}
		} else {
			return currentNode;
		}
		return oldestNode;

	}

	/**
	 * @param node
	 * @param defaultNode
	 * @return returns the recently modified node of list containing all of the
	 *         nodes' children nodes and a defaultNode. The defaultNode might be
	 *         the node itself or null.
	 */
	public Node getLastModifiedChild(Node node, Node defaultNode) {
		Node oldestNode = defaultNode;
		for (Node n : getParts(node, defaultNode)) {
			if (oldestNode == null) {
				oldestNode = n;
			} else {
				oldestNode = compareDates(n, oldestNode);
			}
		}
		return oldestNode;
	}

	/**
	 * @param node
	 * @return returns the recently modified node of list containing the passed
	 *         node and all of it's children nodes.
	 */
	public Node getLastModifiedChild(Node node) {
		return getLastModifiedChild(node, node);
	}

	/**
	 * Liefert das zuletzt erzeugte Kind vom Type "contentType". Wie
	 * getLastlyCreatedChild, jedoch wir Null zurück gegeben, falls: - der
	 * Inhaltstyp leer ist, oder - kein Kind von dem gewünschten Inhaltstyp
	 * gefunden wurde. Die Methode getLastModifiedChild liefert dagegen in diesem
	 * Falle ein Kind irgendeinen Types bzw. den Knoten selber. Für Ermittlung des
	 * neuesten Webschnitts im Webgatherer.
	 * 
	 * @author Ingolf Kuss
	 * @param node Der Knoten, dessen Kinder gesucht werden.
	 * @param contentType der Inhaltstyp, von dem das Kind sein muss.
	 * @return node
	 */
	public Node getLastlyCreatedChildOrNull(Node node, String contentType) {
		/*
		 * play.Logger.debug("BEGIN getLastlyCreatedChildOrNull for pid: " +
		 * node.getPid() + "; contentType: " + contentType);
		 */
		if (contentType == null || contentType.isEmpty()) {
			return null;
		}
		Node newestNode = null;
		for (Node n : getParts(node)) {
			/*
			 * play.Logger.debug("found child with pid: " + n.getPid() +
			 * "; contentType: " + n.getContentType());
			 */
			if (contentType.equals(n.getContentType())) {
				newestNode = compareCreationDates(n, newestNode);
				// play.Logger.debug("newest node is now: pid: " + newestNode.getPid());
			}
		}
		if (newestNode == null)
			return null;
		play.Logger.debug("returning newest node with pid: " + newestNode.getPid());
		return newestNode;
	}

	private static Node compareCreationDates(Node currentNode, Node newestNode) {
		Date currentNodeDate = currentNode.getCreationDate();
		if (currentNodeDate == null)
			currentNodeDate = currentNode.getObjectTimestamp();
		if (currentNodeDate == null)
			currentNodeDate = currentNode.getLastModified();
		currentNode.setCreationDate(currentNodeDate);
		if (newestNode != null) {
			Date newestNodeDate = newestNode.getCreationDate();
			if (newestNodeDate == null)
				newestNodeDate = newestNode.getObjectTimestamp();
			if (newestNodeDate == null)
				newestNodeDate = newestNode.getLastModified();
			newestNode.setCreationDate(newestNodeDate);
			if (currentNodeDate.after(newestNodeDate)) {
				newestNode = currentNode;
			}
			// Special case: Since input has not to be sorted in any way, we
			// need a condition to prefer child nodes over parent nodes.
			// If both nodes have the same timestamp, the currentNode
			// will win, if it is NOT a parent the oldest.
			if (currentNodeDate.equals(newestNodeDate)) {
				if (!currentNode.getPid().equals(newestNode.getParentPid())) {
					newestNode = currentNode;
				}
			}
		} else {
			return currentNode;
		}
		return newestNode;
	}

	/**
	 * @param pid the will be read to the node
	 * @return a Node containing the data from the repository
	 */
	public Node internalReadNode(String pid) {
		Node n = readNodeFromCache(pid);
		if (n != null) {
			return n;
		}
		n = Globals.fedora.readNode(pid);
		n.setAggregationUri(createAggregationUri(n.getPid()));
		n.setRemUri(n.getAggregationUri() + ".rdf");
		n.setDataUri(n.getAggregationUri() + "/data");
		n.setContextDocumentUri("http://" + Globals.server + "/context.json");
		writeNodeToCache(n);
		return n;
	}

	void addLabelsForParts(Node n) {
		List<Link> rels = n.getRelsExt();
		for (Link l : rels) {
			if (HAS_PART.equals(l.getPredicate())
					|| IS_PART_OF.equals(l.getPredicate())) {
				addLabel(n, l);
			}
		}
	}

	private void addLabel(Node n, Link l) {
		try {
			String label = readMetadata2(l.getObject(), "title");
			l.setObjectLabel(label);
			n.removeRelation(l.getPredicate(), l.getObject());
			n.addRelation(l);
		} catch (Exception e) {

		}
	}

	/**
	 * @param pid the pid of the node
	 * @return a Map that represents the node
	 */
	public Map<String, Object> readNodeFromIndex(String pid) {
		return Globals.search.get(pid);
	}

	/**
	 * @param node
	 * @param defaultNode
	 * @return all parts and their parts recursively and a default node. The
	 *         default node might be the node itself.
	 */
	public List<Node> getParts(Node node, Node defaultNode) {
		List<Node> result = new ArrayList<Node>();
		if (defaultNode != null) {
			result.add(defaultNode);
		}
		List<Node> parts = getNodes(node.getPartsSorted().stream()
				.map((Link l) -> l.getObject()).collect(Collectors.toList()));
		for (Node p : parts) {
			result.addAll(getParts(p));
		}
		return result;
	}

	/**
	 * @param node
	 * @return all parts and their parts recursively and the node itself.
	 */
	public List<Node> getParts(Node node) {
		return getParts(node, node);
	}

	/**
	 * @param node a regal node
	 * @param style if "short".equals(style), a shortened representation will be
	 *          returned
	 * @return a tree of regal objects starting with the passed node as root
	 */
	public Map<String, Object> getPartsAsTree(Node node, String style) {
		if ("D".equals(node.getState())) {
			return null;
		}
		Map<String, Object> nm = node.getLd2();

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> parts =
				(List<Map<String, Object>>) nm.get("hasPart");
		List<Map<String, Object>> children = new ArrayList<Map<String, Object>>();
		if (parts != null) {
			for (Map<String, Object> part : parts) {
				String id = (String) part.get("@id");
				Map<String, Object> child = new HashMap<>();
				Map<String, Object> c = getPartsAsTree(internalReadNode(id), style);
				if (c != null) {
					child.put(id, c);
					children.add(child);
				}
			}
			nm.put("hasPart", children);
		}
		return nm;
	}

	/**
	 * @param list a list of nodes to create a json like map for
	 * @return a map with objects
	 */
	public List<Map<String, Object>> hitlistToMap(List<SearchHit> list) {
		List<Map<String, Object>> map = new ArrayList<Map<String, Object>>();
		for (SearchHit hit : list) {
			Map<String, Object> m = hit.getSource();
			m.put("primaryTopic", hit.getId());
			map.add(m);
		}
		return map;
	}

	/**
	 * @param type The objectTyp
	 * @param namespace list only objects in this namespace
	 * @param from show only hits starting at this index
	 * @param until show only hits ending at this index
	 * @return A list of pids with type {@type}
	 */
	public List<SearchHit> listSearch(String type, String namespace, int from,
			int until) {
		return Arrays
				.asList(Globals.search.list(namespace, type, from, until).getHits());
	}

	/**
	 * @param type The objectTyp
	 * @param namespace list only objects in this namespace
	 * @param from show only hits starting at this index
	 * @param until show only hits ending at this index
	 * @return a list of nodes
	 */
	public List<Node> listRepo(String type, String namespace, int from,
			int until) {
		List<String> list = null;
		if (from < 0 || until <= from) {
			throw new HttpArchiveException(316,
					"until and from not sensible. choose a valid range, please.");
		} else if (type == null
				|| type.isEmpty() && namespace != null && !namespace.isEmpty()) {
			return getNodes(listRepoNamespace(namespace, from, until));
		} else if (namespace == null
				|| namespace.isEmpty() && type != null && !type.isEmpty()) {
			return getNodes(listRepoType(type, from, until));
		} else if ((namespace == null || namespace.isEmpty())
				&& (type == null || type.isEmpty())) {
			list = listRepoAll();
		} else {
			list = listRepo(type, namespace);
		}
		return getNodes(sublist(list, from, until));
	}

	/**
	 * @param ids a list of ids to get objects for
	 * @return a list of nodes
	 */
	public List<Node> getNodes(List<String> ids) {
		return ids.stream().map((String id) -> {
			try {
				return internalReadNode(id);
			} catch (Exception e) {
				Logger.error("" + id, e);
				return new Node(id);
			}
		}).filter(n -> n != null).collect(Collectors.toList());
	}

	/**
	 * @param ids a list of ids to get objects for
	 * @return a list of Maps each represents a node
	 */
	public List<Map<String, Object>> getNodesFromIndex(List<String> ids) {
		return ids.stream().map((String id) -> readNodeFromIndex(id))
				.collect(Collectors.toList());
	}

	private List<String> listRepo(String type, String namespace) {
		List<String> result = new ArrayList<String>();
		List<String> typedList = listRepoType(type);
		if (namespace != null && !namespace.isEmpty()) {
			for (String item : typedList) {
				if (item.startsWith(namespace + ":")) {
					result.add(item);
				}
			}
			return result;
		} else {
			return typedList;
		}
	}

	private List<String> listRepoType(String type) {
		List<String> typedList;
		String query = "* <" + REL_CONTENT_TYPE + "> \"" + type + "\"";
		try (InputStream in = Globals.fedora.findTriples(query,
				FedoraVocabulary.SPO, FedoraVocabulary.N3)) {
			typedList = RdfUtils.getFedoraSubject(in);
			return typedList;
		} catch (IOException e) {
			play.Logger.warn("", e);
		}
		return new ArrayList<String>();
	}

	private List<String> listRepoAll() {
		List<String> typedList;
		String query = "* <" + REL_IS_NODE_TYPE + "> <" + TYPE_OBJECT + ">";
		try (InputStream in = Globals.fedora.findTriples(query,
				FedoraVocabulary.SPO, FedoraVocabulary.N3)) {
			typedList = RdfUtils.getFedoraSubject(in);
			return typedList;
		} catch (IOException e) {
			play.Logger.warn("", e);
		}
		return new ArrayList<String>();
	}

	private List<String> listRepoType(String type, int from, int until) {
		List<String> list = listRepoType(type);
		return sublist(list, from, until);
	}

	/**
	 * List all pids within a namespace
	 * 
	 * @param namespace a valid namespace
	 * @return a list of pids
	 */
	public List<String> listRepoNamespace(String namespace) {
		return listByQuery(namespace + ":*");
	}

	/**
	 * @param namespace list only objects in this namespace
	 * @param from show only hits starting at this index
	 * @param until show only hits ending at this index
	 * @return a list of nodes
	 */
	public List<String> listRepoNamespace(String namespace, int from, int until) {
		List<String> list = listRepoNamespace(namespace);
		return sublist(list, from, until);
	}

	private List<String> listByQuery(String query) {
		List<String> objects = null;
		objects = Globals.fedora.findNodes(query);
		return objects;
	}

	private List<String> sublist(List<String> list, int from, int until) {
		if (from >= list.size()) {
			return new Vector<String>();
		}
		if (until < list.size()) {
			return list.subList(from, until);
		} else {
			return list.subList(from, list.size());
		}
	}

	/**
	 * @param pid The pid to read the dublin core stream from.
	 * @return A DCBeanAnnotated java object.
	 */
	public DublinCoreData readDC(String pid) {
		Node node = readNode(pid);
		String uri = getHttpUriOfResource(node);
		if (node != null)
			return node.getDublinCoreData().addIdentifier(uri);
		return null;
	}

	/**
	 * @param node
	 * @param field the shortname of metadata field
	 * @return the ntriples or just one field
	 */
	public String readMetadata1(Node node, String field) {
		try {
			String metadata = node.getMetadata(metadata1);
			if (metadata == null)
				return null;
			if (field == null || field.isEmpty()) {
				return metadata;
			} else {
				String pred = getUriFromJsonName(field);
				List<String> value = RdfUtils.findRdfObjects(node.getPid(), pred,
						metadata, RDFFormat.NTRIPLES);
				return value == null || value.isEmpty() ? null : value.get(0);
			}
		} catch (UrlConnectionException e) {
			throw new HttpArchiveException(404, e);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * @param pid the pid of the object
	 * @param field if field is specified, only the value of a certain field will
	 *          be returned
	 * @return n-triple metadata
	 */
	public String readMetadata2(String pid, String field) {
		Node node = internalReadNode(pid);
		String result = readMetadata2(node, field);
		return result == null ? "No " + field : result;
	}

	/**
	 * @param node
	 * @param field the shortname of metadata field
	 * @return the ntriples or just one field
	 */
	public String readMetadata2(Node node, String field) {
		try {
			String metadata = node.getMetadata2();
			if (metadata == null)
				return null;
			if (field == null || field.isEmpty()) {
				return metadata;
			} else {
				String pred = getUriFromJsonName(field);
				List<String> value = RdfUtils.findRdfObjects(node.getPid(), pred,
						metadata, RDFFormat.NTRIPLES);
				return value == null || value.isEmpty() ? null : value.get(0);
			}
		} catch (UrlConnectionException e) {
			throw new HttpArchiveException(404, e);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * Liest Gatherconf von Node
	 * 
	 * @param node ein Node (Webpage oder Webschnitt)
	 * @return a webgather configuration
	 */
	public String readConf(Node node) {
		try {
			String confstring = node.getConf();
			play.Logger.debug("confstring: " + confstring);
			if (confstring == null)
				return "";
			ObjectMapper mapper = JsonUtil.mapper();
			Gatherconf conf = mapper.readValue(confstring, Gatherconf.class);
			if (node.getContentType().equals("version")
					&& (conf.getOpenWaybackLink() == null
							|| conf.getOpenWaybackLink().isEmpty())) {
				play.Logger
						.debug("conf.openWaybackLink is empty for WebpageVersion with pid "
								+ node.getPid());
				play.Logger.debug("Creating an openWaybackLink for the start date.");
				String owDatestamp =
						new SimpleDateFormat("yyyyMMdd").format(conf.getStartDate());
				play.Logger.debug("owDatestamp: " + owDatestamp);
				String collection = conf.fetchCollection();
				if (collection == null || collection.isEmpty()) {
					play.Logger.debug(
							"Collection can not be determined. Creating an openWaybackLink in the standard collection (wpull)");
					conf.setOpenWaybackLink(Play.application().configuration()
							.getString("regal-api.wayback.collection.wpull") + owDatestamp
							+ "/" + conf.getUrl());
				} else {
					play.Logger.debug("collection: " + collection);
					conf.setOpenWaybackLink(Play.application().configuration()
							.getString("regal-api.wayback.collection." + collection)
							+ owDatestamp + "/" + conf.getUrl());
				}
			}
			return conf.toString();
		} catch (UrlConnectionException e) {
			play.Logger.error("Url Connection Exception", e);
			throw new HttpArchiveException(404, e);
		} catch (Exception e) {
			play.Logger.error("Gatherconf for Node with pid " + node.getPid()
					+ " could not be read!", e);
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * Liest eine Gatherconf (Crawler-Konfiguration) von einem anderen
	 * toscience-Server
	 * 
	 * @param quellserverWebschnittPid die PID des Webschnitts auf dem anderen
	 *          Server
	 * 
	 * @return conf Die Gatherconf des anderen Servers
	 */
	public Gatherconf readRemoteConf(String quellserverWebschnittPid) {
		HttpURLConnection con = null;
		BufferedReader br = null;
		String responseMultiLine = null;
		Gatherconf conf = null;
		try {
			URL url = new URL(Globals.protocol + "api." + Globals.importServerName
					+ "/resource/" + quellserverWebschnittPid + "/conf");
			play.Logger.debug("URL for remote Conf: " + url.toString());
			con = (HttpURLConnection) url.openConnection();
			play.Logger.debug("opened connection");
			con.setRequestMethod("GET");
			HttpURLConnection.setFollowRedirects(true);
			String auth = Globals.importServerBackendUser + ":"
					+ Globals.importServerBackendUserPassword;
			byte[] encodedAuth =
					Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
			String authHeaderValue = "Basic " + new String(encodedAuth);
			play.Logger.debug("auth: " + authHeaderValue);
			con.setRequestProperty("Authorization", authHeaderValue);
			con.connect();
			play.Logger
					.debug("response code for GET remote Conf: " + con.getResponseCode());
			InputStream ip = con.getInputStream();
			br = new BufferedReader(new InputStreamReader(ip));
			StringBuilder response = new StringBuilder();
			String responseSingle = null;
			while ((responseSingle = br.readLine()) != null) {
				response.append(responseSingle);
			}
			responseMultiLine = response.toString();
			play.Logger.debug("Conf for quellserverWebschnittPid "
					+ quellserverWebschnittPid + " : " + responseMultiLine);
			conf = Gatherconf.create(responseMultiLine);
		} catch (Exception e) {
			play.Logger.error(e.toString());
			throw new RuntimeException(e);
		} finally {
			con.disconnect();
			try {
				br.close();
			} catch (IOException e) {
				play.Logger.error(e.getMessage());
			}
		}
		return conf;
	}

	/**
	 * read a webpage's url history
	 * 
	 * @param node the node of the webpage
	 * @return an url history
	 */
	public String readUrlHist(Node node) {
		try {
			String urlHistString = node.getUrlHist();
			if (urlHistString == null)
				return "";
			ObjectMapper mapper = JsonUtil.mapper();
			UrlHist urlHist = mapper.readValue(urlHistString, UrlHist.class);
			return urlHist.toString();
		} catch (UrlConnectionException e) {
			throw new HttpArchiveException(404, e);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * @param node the pid of the object
	 * @return ordered json array of parts
	 */
	public String readSeq(Node node) {
		try {
			return node.getSeq();
		} catch (UrlConnectionException e) {
			throw new HttpArchiveException(404, e);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * Diese Methode holt einen Baum von einem Node und gibt ihn aus.
	 * 
	 * @author Ingolf Kuss
	 * @date 2026-06-26
	 * @param node the pid of the object
	 * @return html representation of tree navigation
	 */
	public String readTree(Node node) {
		try {
			play.Logger.debug("Beginn readTree");
			return node.getTreeHtml();
		} catch (UrlConnectionException e) {
			play.Logger.debug("readTree 404 Exception");
			throw new HttpArchiveException(404, e);
		} catch (Exception e) {
			play.Logger.debug(
					"Baum-HTML konnte nicht gelesen werden, pid = " + node.getPid());
			// throw new HttpArchiveException(500, e);
			return null;
		}
	}

	/**
	 * @param pid the pid
	 * @return the last modified date
	 */
	public Date getLastModified(String pid) {
		Node node = readNode(pid);
		return node.getLastModified();
	}

	/**
	 * @param node the node to read a urn from
	 * @return a urn object that describes the status of the urn
	 */
	public Urn getUrnStatus(Node node) {
		return getUrnStatus(
				node.getUrn() == null ? node.getUrnFromMetadata() : node.getUrn(),
				node.getPid());
	}

	public Urn getUrnStatus(String urn, String pid) {
		if (urn == null) {
			play.Logger.debug("urn == null");
			return null;
		}
		Urn result = new Urn(urn);
		result.init(Globals.urnbase + pid);
		return result;
	}

	/**
	 * @param node
	 * @return 200 if object can be found at oaiprovider, 404 if not, 500 on error
	 */
	public int getOaiStatus(Node node) {
		try {
			URL url = new URL(Globals.oaiMabXmlAddress + node.getPid());
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			HttpURLConnection.setFollowRedirects(true);
			con.connect();
			Element root = XmlUtils.getDocument(con.getInputStream());
			List<Element> elements = XmlUtils.getElements("//setSpec", root, null);
			if (elements.isEmpty())
				return 404;
			return con.getResponseCode();
		} catch (Exception e) {
			play.Logger.warn("", e);
			return 500;
		}
	}

	Map<String, String> getLinks(Node node) {
		String oai = Globals.oaiMabXmlAddress + node.getPid();
		String aleph = Globals.alephAddress + node.getLegacyId();
		String lobid = Globals.lobidAddress + node.getLegacyId();
		String api = this.getHttpUriOfResource(node);
		String urn = null;
		if (node.getUrn() != null) {
			urn = Globals.urnResolverAddress + node.getUrn();
		} else {
			urn = Globals.urnResolverAddress + node.getUrnFromMetadata();
		}
		String doi = "https://dx.doi.org/" + node.getDoi();
		String frontend = Globals.urnbase + node.getPid();
		String digitool = Globals.digitoolAddress
				+ node.getPid().substring(node.getNamespace().length() + 1);

		Map<String, String> result = new HashMap<String, String>();
		result.put("oai", oai);
		result.put("aleph", aleph);
		result.put("lobid", lobid);
		result.put("api", api);
		result.put("urn", urn);
		result.put("doi", doi);
		result.put("frontend", frontend);
		result.put("digitool", digitool);

		return result;
	}

	/**
	 * The status contains information about the object with regard to thirdparty
	 * system
	 * 
	 * @param node
	 * @return a Map with status information
	 */
	public Map<String, Object> getStatus(Node node) {
		Map<String, Object> result = new HashMap<String, Object>();
		result.put("urnStatus", urnStatus(node));
		result.put("doiStatus", doiStatus(node));
		result.put("oaiStatus", getOaiStatus(node));
		result.put("links", getLinks(node));
		result.put("title", getTitle(node));
		result.put("metadataAccess", node.getPublishScheme());
		result.put("dataAccess", node.getAccessScheme());
		result.put("type", node.getContentType());
		result.put("pid",
				node.getPid().substring(node.getNamespace().length() + 1));
		result.put("catalogId", node.getLegacyId());
		result.put("webgatherer", getGatherStatus(node));
		play.Logger.debug("node.getUrn()=" + node.getUrn());
		if (node.getUrn() != null) {
			result.put("urn", node.getUrn());
		} else {
			play.Logger.debug("Got URN from Metadata: " + node.getUrnFromMetadata());
			result.put("urn", node.getUrnFromMetadata());
		}
		return result;
	}

	private String getTitle(Node node) {
		try {
			return readMetadata2(node, "title");
		} catch (Exception e) {
			return "No Title";
		}
	}

	private Map<String, Object> getGatherStatus(Node node) {
		Map<String, Object> entries = new HashMap<String, Object>();
		try {
			Gatherconf conf = Gatherconf.create(node.getConf());
			entries.put("lastLaunch", Webgatherer.getLastLaunch(node) == null ? ""
					: Webgatherer.getLastLaunch(node));
			if ("version".equals(node.getContentType())) {
				if (conf.getCrawlerSelection()
						.equals(Gatherconf.CrawlerSelection.btrix)) {
					BtrixWebclient btrixWebclient = new BtrixWebclient();
					btrixWebclient.setBtrixWorkflowId(conf.getBtrixWorkflowId());
					btrixWebclient.setCrawlId(conf.getLastCrawlId());
					JSONObject crawlConfig = btrixWebclient.getCrawlOut();
					entries.put("crawlExitStatus", crawlConfig.getString("state"));
					entries.put("crawlFileSize", WebgatherUtils.humanReadableByteCount(
							Long.parseLong(crawlConfig.getString("fileSize"))));
					entries.put("crawlDuration",
							WebgatherUtils.humanReadableDuration(Duration.ofSeconds(
									Long.parseLong(crawlConfig.getString("crawlExecSeconds")))));
					entries.put("crawlStarted", crawlConfig.getString("started"));
				} else if (conf.getCrawlerSelection()
						.equals(Gatherconf.CrawlerSelection.wpull)) {
					WpullCrawl wpullCrawl = new WpullCrawl(node, conf);
					entries.put("crawlExitStatus", wpullCrawl.getCrawlExitStatus());
					entries.put("crawlFileSize", WebgatherUtils.humanReadableByteCount(
							Long.parseLong(wpullCrawl.getCrawlFileSize())));
				}
			} else if ("webpage".equals(node.getContentType())) {
				if (conf.getCrawlerSelection()
						.equals(Gatherconf.CrawlerSelection.heritrix)) {
					String hertrixXmlResponse =
							Globals.heritrix.getJobStatus(node.getPid());
					XmlMapper xmlMapper = new XmlMapper();
					entries = xmlMapper.readValue(hertrixXmlResponse, Map.class);
				} else if (conf.getCrawlerSelection()
						.equals(Gatherconf.CrawlerSelection.wpull)) {
					WpullCrawl wpullCrawl = new WpullCrawl(node, conf);
					entries.put("crawlControllerState",
							wpullCrawl.getCrawlControllerState());
					entries.put("crawlExitStatus",
							wpullCrawl.getLatestCrawlExitStatus() < 0 ? ""
									: wpullCrawl.getLatestCrawlExitStatus());
				} else if (conf.getCrawlerSelection()
						.equals(Gatherconf.CrawlerSelection.btrix)) {
					if (conf.getBtrixWorkflowId() != null) {
						BtrixWebclient btrixWebclient = new BtrixWebclient();
						btrixWebclient.setBtrixWorkflowId(conf.getBtrixWorkflowId());
						JSONObject crawlConfig = btrixWebclient.getCrawlConfigOut();
						if (crawlConfig.getBoolean("isCrawlRunning")) {
							entries.put("crawlControllerState", CrawlControllerState.RUNNING);
						}
						entries.put("crawlExitStatus",
								crawlConfig.getString("lastCrawlState"));
						entries.put("launchCount", crawlConfig.getString("crawlCount"));
						entries.put("lastCrawlSize", WebgatherUtils.humanReadableByteCount(
								Long.parseLong(crawlConfig.getString("lastCrawlSize"))));
						entries.put("lastLaunch",
								crawlConfig.getString("lastCrawlStartTime"));
					}
				}
				/*
				 * Launch Count als Summe der Launches über alle Crawler ermitteln -
				 * überschreibt launchCount von Heritrix und Browsertrix
				 */
				entries.put("launchCount", Webgatherer.getLaunchCount(node));
				entries.put("nextLaunch", Webgatherer.nextLaunch(node));
			} // end if webpage
		} catch (Exception e) {
			play.Logger.warn(
					"Gather-Status kann nicht oder nur teilweise ermittelt werden.", e);
		}
		return entries == null ? new HashMap<String, Object>() : entries;
	}

	private int urnStatus(Node node) {
		try {
			Urn urn = getUrnStatus(node);
			int urnStatus = urn == null ? 500 : urn.getResolverStatus();
			play.Logger.debug("urnStatus=" + urnStatus);
			return urnStatus;
		} catch (Exception e) {
			play.Logger.warn("", e);
			return 500;
		}
	}

	private int doiStatus(Node node) {
		return doiStatus(node.getDoi());
	}

	private int doiStatus(String doi) {
		try {
			return getFinalResponseCode(Globals.doiResolverAddress + doi);
		} catch (Exception e) {
			play.Logger.warn("", e);
			return 500;
		}
	}

	/**
	 * @param nodes
	 * @return status information for many nodes
	 */
	public List<Map<String, Object>> getStatus(List<Node> nodes) {
		return nodes.stream().map((Node n) -> getStatus(n))
				.collect(Collectors.toList());
	}

	/**
	 * @param namespace
	 * @param from
	 * @param until
	 * @return a list of elasticsearch hits of objects within the given range
	 */
	public List<SearchHit> list(String namespace, Date from, Date until) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
		String f = dateFormat.format(from);
		String u = dateFormat.format(until);
		String query = "isDescribedBy.created:[" + f + " TO " + u + "]";
		play.Logger.info("List all from " + f + " to " + u);
		List<SearchHit> result = new ArrayList<SearchHit>();
		int step = 100;
		int start = 0;
		SearchHits hits = Globals.search
				.query(new String[] { namespace }, query, start, step).getHits();
		long size = hits.getTotalHits();

		result.addAll(Arrays.asList((hits.getHits())));
		for (int i = 0; i < (size - (size % step)); i += step) {
			hits = Globals.search.query(new String[] { namespace }, query, i, step)
					.getHits();
			result.addAll(Arrays.asList((hits.getHits())));
		}
		return result;

	}

	private int getFinalResponseCode(String url) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setInstanceFollowRedirects(false);
		con.setReadTimeout(1000 * 2);
		con.connect();
		con.getInputStream();
		if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
				|| con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP
				|| con.getResponseCode() == 307 || con.getResponseCode() == 303) {
			String redirectUrl = con.getHeaderField("Location");

			return getFinalResponseCode(redirectUrl);
		}
		return con.getResponseCode();
	}

	public String getFinalURL(String url) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
		con.setReadTimeout(1000 * 2);
		con.setInstanceFollowRedirects(false);
		con.connect();
		con.getInputStream();
		if (con.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM
				|| con.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP
				|| con.getResponseCode() == 307 || con.getResponseCode() == 303) {
			String redirectUrl = con.getHeaderField("Location");

			return getFinalURL(redirectUrl);
		}
		return url;
	}

	String findAlephid(Node node) {
		String pid = node.getPid();
		List<Pair<String, String>> identifier =
				node.getDublinCoreData().getIdentifier();
		String alephid = "";
		for (Pair<String, String> id : identifier) {
			if (id.getLeft().startsWith("TT") || id.getLeft().startsWith("HT")) {
				alephid = id.getLeft();
				break;
			}
		}
		if (alephid.isEmpty()) {
			alephid = getIdOfParallelEdition(node);
			if (alephid == null || alephid.isEmpty()) {
				throw new HttpArchiveException(500, pid + " no Catalog-Id found");
			}
		}
		return alephid;
	}

	String getIdOfParallelEdition(Node node) {
		String alephid;
		alephid = new Read().readMetadata2(node, "parallelEdition");
		alephid = alephid.substring(alephid.lastIndexOf('/') + 1, alephid.length());
		return alephid;
	}
}
