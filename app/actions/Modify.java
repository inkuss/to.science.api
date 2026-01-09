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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.wordnik.swagger.core.util.JsonUtil;

import archive.fedora.CopyUtils;
import archive.fedora.RdfException;
import archive.fedora.RdfUtils;
import static archive.fedora.Vocabulary.*;
import archive.fedora.XmlUtils;
import controllers.MyController;
import helper.DataciteClient;
import helper.HttpArchiveException;
import helper.JsonMapper;
import helper.MyEtikettMaker;
import helper.RdfHelper;
import helper.TosHelper;
import helper.URN;
import helper.oai.OaiDispatcher;
import models.DublinCoreData;
import models.Globals;
import models.Node;
import models.ToScienceObject;
import play.libs.F;
import play.libs.ws.WSRequest;
import play.libs.ws.WSResponse;
import play.mvc.Http;
import play.mvc.Result;
import views.Helper;
import play.mvc.Http.Request;

/**
 * @author Jan Schnasse
 *
 */
public class Modify extends RegalAction {
	String msg = "";

	/**
	 * @param pid the pid that must be updated
	 * @param content the file content as byte array
	 * @param mimeType the mimetype of the file
	 * @param name the name of the file
	 * @param md5Hash a hash for the content. Can be null.
	 * @return A short message
	 * @throws IOException if data can not be written to a tmp file
	 */
	public String updateData(String pid, InputStream content, String mimeType,
			String name, String md5Hash) throws IOException {
		if (content == null) {
			throw new HttpArchiveException(406,
					pid + " you've tried to upload an empty stream."
							+ " This action is not supported. Use HTTP DELETE instead.");
		}
		File tmp = File.createTempFile(name, "tmp");
		tmp.deleteOnExit();
		CopyUtils.copy(content, tmp);
		Node node = new Read().readNode(pid);
		if (node != null) {
			node.setUploadFile(tmp.getAbsolutePath());
			node.setFileLabel(name);
			node.setMimeType(mimeType);
			Globals.fedora.updateNode(node);
		} else {
			throw new HttpArchiveException(500, "Lost Node!");
		}
		node = updateIndex(pid);
		if (md5Hash != null && !md5Hash.isEmpty()) {

			String fedoraHash = node.getChecksum();
			if (!md5Hash.equals(fedoraHash)) {
				throw new HttpArchiveException(417, pid + " expected a MD5 of "
						+ fedoraHash + " but you provided a MD5 value of " + md5Hash);
			}
		}
		return pid + " data successfully updated!";
	}

	/**
	 * @param pid The pid that must be updated
	 * @param dc A dublin core object
	 * @return a short message
	 */
	public String updateDC(String pid, DublinCoreData dc) {
		Node node = new Read().readNode(pid);
		node.setDublinCoreData(dc);
		Globals.fedora.updateNode(node);
		updateIndex(node.getPid());
		return pid + " dc successfully updated!";
	}

	/**
	 * @param pid the node's pid
	 * @param content a json array to provide ordering information about the
	 *          object's children
	 * @return a message
	 */
	public String updateSeq(String pid, String content) {
		try {
			if (content == null) {
				throw new HttpArchiveException(406,
						pid + " You've tried to upload an empty string."
								+ " This action is not supported."
								+ " Use HTTP DELETE instead.\n");
			}
			play.Logger.info("Write ordering info to fedora \n\t" + content);
			File file = CopyUtils.copyStringToFile(content);
			Node node = new Read().readNode(pid);
			if (node != null) {
				node.setSeqFile(file.getAbsolutePath());
				Globals.fedora.updateNode(node);
			}
			updateIndex(node.getPid());
			return pid + " sequence of child objects updated!";
		} catch (RdfException e) {
			throw new HttpArchiveException(400, e);
		} catch (IOException e) {
			throw new UpdateNodeException(e);
		}
	}

	/**
	 * @param pid The pid that must be updated
	 * @param content The metadata as rdf string
	 * @return a short message
	 */
	public String updateLobidifyAndEnrichMetadata(String pid, String content) {
		try {
			Node node = new Read().readNode(pid);
			return updateLobidifyAndEnrichMetadata(node, content);
		} catch (Exception e) {
			throw new UpdateNodeException(e);
		}
	}

	/**
	 * @param pid The pid that must be updated
	 * @param content The metadata as rdf string
	 * @return a short message
	 */
	public String updateLobidify2AndEnrichMetadata(String pid, String content) {
		try {
			Node node = new Read().readNode(pid);
			return updateLobidify2AndEnrichMetadata(node, content);
		} catch (Exception e) {
			throw new UpdateNodeException(e);
		}
	}

	/**
	 * This method maps DeepGreen data to the Lobid2 format and creates a
	 * datastream "metadata2" of the resource
	 * 
	 * @param pid The pid of the resource that must be updated
	 * @param embargoDuration Die Dauer des Embargos in Monaten
	 * @param format Das RDF-Format, in das die Metadaten konvertiert werden
	 *          sollen (z.B. TURTLE, XDFXML, NTRIPLES)
	 * @param content The metadata in the format DeepGreen-XML
	 * @return a short message
	 */
	public String updateLobidify2AndEnrichDeepGreenData(String pid,
			int embargoDuration, String deepgreenId, RDFFormat format,
			Document content) {
		try {
			Node node = new Read().readNode(pid);
			return updateLobidify2AndEnrichDeepGreenData(node, embargoDuration,
					deepgreenId, format, content);
		} catch (Exception e) {
			throw new UpdateNodeException(e);
		}
	}

	/**
	 * @param node The node that must be updated
	 * @param content The metadata as rdf string
	 * @return a short message
	 */
	public String updateLobidifyAndEnrichMetadata(Node node, String content) {

		String pid = node.getPid();
		if (content == null) {
			throw new HttpArchiveException(406,
					pid + " You've tried to upload an empty string."
							+ " This action is not supported."
							+ " Use HTTP DELETE instead.\n");
		}

		if (content.contains(archive.fedora.Vocabulary.REL_MAB_527)) {
			String lobidUri = RdfUtils.findRdfObjects(node.getPid(),
					archive.fedora.Vocabulary.REL_MAB_527, content, RDFFormat.NTRIPLES)
					.get(0);
			String alephid =
					lobidUri.replaceFirst("http://lobid.org/resource[s]*/", "");
			content = getLobid2DataAsNtripleString(node, alephid);
			updateMetadata(metadata2, node, content);

			String enrichMessage2 = Enrich.enrichMetadata2(node);
			return pid + " metadata successfully updated, lobidified and enriched! "
					+ enrichMessage2;
		} else {
			updateMetadata("metadata2", node, content);
			String enrichMessage2 = Enrich.enrichMetadata2(node);
			return pid + " metadata successfully updated, and enriched! "
					+ enrichMessage2;
		}
	}

	/**
	 * @param node The node that must be updated
	 * @param content The metadata as rdf string
	 * @return a short message
	 */
	public String updateLobidify2AndEnrichMetadata(Node node, String content) {

		String pid = node.getPid();
		if (content == null) {
			throw new HttpArchiveException(406,
					pid + " You've tried to upload an empty string."
							+ " This action is not supported."
							+ " Use HTTP DELETE instead.\n");
		}

		/**
		 * 1. toscienceJson
		 */

		try {
			JSONObject toscienceJson = null;
			JSONObject allMetadata = null;
			play.Logger.debug("node.getContentType()= " + node.getContentType());
			if (!node.getContentType().contains("file")
					&& !node.getContentType().contains("part")) {

				RDFFormat format = RDFFormat.NTRIPLES;
				Map<String, Object> rdf = RdfHelper.getRdfAsMap(node, format, content);

				play.Logger.debug("rdf=" + rdf.toString());
				allMetadata = new JSONObject(new JSONObject(rdf).toString());

				String toscienceMetadata =
						TosHelper.getToPersistTosMd(allMetadata.toString(), pid);

				toscienceJson =
						TosHelper.getPrefLabelsResolved(new JSONObject(toscienceMetadata));

				if (Helper.mdStreamExists(pid, "ktbl")) {
					toscienceJson = TosHelper.getPrefLabelsResolved(new JSONObject(
							new Read().readNode(pid).getMetadata("toscience")));
				}

				updateMetadataJson(node, toscienceJson.toString());

			}
		} catch (JSONException e) {
			play.Logger.error("toscience json datastream could not be updated!");
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		/**
		 * 2. METADATA2
		 */
		if (content.contains(archive.fedora.Vocabulary.REL_MAB_527)) {
			String lobidUri = RdfUtils.findRdfObjects(node.getPid(),
					archive.fedora.Vocabulary.REL_MAB_527, content, RDFFormat.NTRIPLES)
					.get(0);
			String alephid =
					lobidUri.replaceFirst("http://lobid.org/resource[s]*/", "");
			alephid = alephid.replaceAll("#.*", "");
			String newContent = getLobid2DataAsNtripleString(node, alephid);
			updateMetadata("metadata2", node, newContent);

			String enrichMessage = Enrich.enrichMetadata2(node);
			return pid + " metadata successfully updated, lobidified and enriched! "
					+ enrichMessage;
		} else {
			updateMetadata("metadata2", node, content);
			String enrichMessage = Enrich.enrichMetadata2(node);
			return pid + " metadata successfully updated, and enriched! "
					+ enrichMessage;
		}
	}

	/**
	 * The method maps DeepGreen metadata to the Lobid2 format and creates a data
	 * stream Metadata2 of the resource
	 * 
	 * @param node The node of the resource that must be updated
	 * @param embargoDuration Die Dauer des Embargos in Monaten
	 * @param format RDF-Format, z.B. NTRIPLES
	 * @param content The metadata as DeepGreen XML
	 * @return a short message
	 */
	public String updateLobidify2AndEnrichDeepGreenData(Node node,
			int embargoDuration, String deepgreenId, RDFFormat format,
			Document content) {

		try {
			play.Logger.debug("Start updateLobidify2AndEnrichDeepGreenData");
			String pid = node.getPid();
			if (content == null) {
				throw new HttpArchiveException(406,
						pid + " You've tried to upload an empty string."
								+ " This action is not supported."
								+ " Use HTTP DELETE instead.\n");
			}

			Map<String, Object> rdf = new XmlUtils().getLd2Lobidify2DeepGreen(
					node.getLd2(), embargoDuration, deepgreenId, content);
			play.Logger.debug("Mapped DeepGrren data to lobid2!");
			updateMetadata(metadata2, node, rdfToString(rdf, format));
			play.Logger.debug("Updated Metadata2 datastream!");

			String enrichMessage = Enrich.enrichMetadata2(node);
			return pid
					+ " DeepGreen-metadata successfully updated, lobidified and enriched! "
					+ enrichMessage;
		} catch (Exception e) {
			play.Logger.error(
					"Datastream metadata2 with mapped DeepGreen data could not be created!",
					e);
			throw new RuntimeException(e);
		}
	}

	public String updateLobidify2AndEnrichMetadataIfRecentlyUpdated(String pid,
			String content, LocalDate date) {
		try {
			Node node = new Read().readNode(pid);
			return updateLobidify2AndEnrichMetadataIfRecentlyUpdated(node, content,
					date);
		} catch (Exception e) {
			throw new UpdateNodeException(e);
		}
	}

	/**
	 * Aktualisiert den Metadata2-Datenstrom einer Ressource auf Basis der
	 * aktuellen lobid-Daten. Nur, falls sich in lobid etwas geändert hat.
	 * 
	 * @author Ingolf Kuss
	 * @param node Der Node der Ressource
	 * @param content Der komplette Inhalt des aktuellen Metadata2-Datenstrom (mit
	 *          der alephid, falls vorhanden)
	 * @param date Das aktuelle Datum (oder ein Vergleichsdatum)
	 * @return eine Message
	 */
	public String updateLobidify2AndEnrichMetadataIfRecentlyUpdated(Node node,
			String content, LocalDate date) {
		String pid = node.getPid();
		if (content == null) {
			throw new HttpArchiveException(406,
					pid + " You've tried to upload an empty string."
							+ " This action is not supported."
							+ " Use HTTP DELETE instead.\n");
		}

		try {
			if (content.contains(archive.fedora.Vocabulary.REL_MAB_527)) {
				String lobidUri = RdfUtils.findRdfObjects(node.getPid(),
						archive.fedora.Vocabulary.REL_MAB_527, content, RDFFormat.NTRIPLES)
						.get(0);
				String alephid =
						lobidUri.replaceFirst("http[s]*://lobid.org/resource[s]*/", "");
				alephid = alephid.replaceAll("#.*", "");
				play.Logger.debug("alephid=" + alephid);
				return updateLobidify2AndEnrichMetadataIfRecentlyUpdatedByAlephid(node,
						alephid, date);
			}
		} catch (Exception e) {
			play.Logger
					.warn("AlmaId zu Pid " + pid + " konnte nicht ermittelt werden!");
			throw new RuntimeException(e);
		}
		return pid + " no updates available. Resource has no Almaid.";
	}

	/**
	 * Returns the current HTTP request.
	 *
	 * @return the request
	 */
	public static Request request() {
		return Http.Context.current().request();
	}

	/**
	 * Aktualisiert den Metadata2-Datenstrom einer Ressource auf Basis der
	 * aktuellen lobid-Daten. Nur, falls sich in lobid etwas geändert hat.
	 * 
	 * @author Ingolf Kuss
	 * @param node Der Node der Ressource
	 * @param alephid die Alephid der Ressource
	 * @param date Das aktuelle Datum (oder ein Vergleichsdatum)
	 * @return eine Message
	 */
	public String updateLobidify2AndEnrichMetadataIfRecentlyUpdatedByAlephid(
			Node node, String alephid, LocalDate date) {
		StringBuffer message = new StringBuffer();
		String pid = node.getPid();
		String content = "";
		try {
			content = getLobid2DataAsNtripleStringIfResourceHasRecentlyChanged(node,
					alephid, date);
			updateMetadata(metadata2, node, content);
			message.append(Enrich.enrichMetadata2(node));
		} catch (NotUpdatedException e) {
			play.Logger.info(pid + " Not updated. " + e.getMessage());
			message.append(pid + " Not updated. " + e.getMessage());
		}
		return pid + " metadata successfully updated, lobidified and enriched! "
				+ message;
	}

	public String rewriteContent(String content, String pid) {
		Collection<Statement> graph = RdfUtils.readRdfToGraph(
				new ByteArrayInputStream(content.getBytes()), RDFFormat.NTRIPLES, "");
		Iterator<Statement> it = graph.iterator();
		String subj = pid;
		while (it.hasNext()) {
			Statement str = it.next();
			if ("http://xmlns.com/foaf/0.1/isPrimaryTopicOf"
					.equals(str.getPredicate().stringValue())) {
				subj = str.getSubject().stringValue();
				break;
			}
		}
		if (pid.equals(subj))
			return content;
		play.Logger.debug("Rewrite " + subj + " to " + pid);
		graph.removeIf(st -> {
			return "http://xmlns.com/foaf/0.1/primaryTopic"
					.equals(st.getPredicate().stringValue());
		});
		graph.removeIf(st -> {
			return "http://xmlns.com/foaf/0.1/isPrimaryTopicOf"
					.equals(st.getPredicate().stringValue());
		});
		graph = RdfUtils.rewriteSubject(subj, pid, graph);
		ValueFactory vf = SimpleValueFactory.getInstance();
		Resource s = vf.createIRI(pid + ".rdf");
		IRI p = vf.createIRI("http://xmlns.com/foaf/0.1/primaryTopic");
		Resource o = vf.createIRI(pid);
		graph.add(vf.createStatement(s, p, o));

		s = vf.createIRI(pid);
		p = vf.createIRI("http://xmlns.com/foaf/0.1/isPrimaryTopicOf");
		o = vf.createIRI(pid + ".rdf");
		graph.add(vf.createStatement(s, p, o));
		return RdfUtils.graphToString(graph, RDFFormat.NTRIPLES);

	}

	private String getLobidDataAsNtripleStringIfResourceHasRecentlyChanged(
			Node node, String alephid, LocalDate date) {
		try {
			LocalDate resourceDate = getLastModifiedFromLobid(alephid);
			play.Logger.info("Lobid resource has been modified on "
					+ resourceDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
			play.Logger.info("I will only update if local date "
					+ date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
					+ " is before remote date "
					+ resourceDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
			if (date.isBefore(resourceDate)) {
				return getLobidDataAsNtripleString(node, alephid);
			}
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}

		throw new NotUpdatedException("No updates since " + date.toString());

	}

	private String getLobid2DataAsNtripleStringIfResourceHasRecentlyChanged(
			Node node, String alephid, LocalDate date) {
		try {
			LocalDate resourceDate = getLastModifiedFromLobid2(alephid);
			play.Logger.info("Lobid resource has been modified on "
					+ resourceDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
			play.Logger.info("I will only update if local date "
					+ date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
					+ " is before remote date "
					+ resourceDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
			if (date.isBefore(resourceDate)) {
				return getLobid2DataAsNtripleString(node, alephid);
			}
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}

		throw new NotUpdatedException("No updates since " + date.toString());

	}

	private LocalDate getLastModifiedFromLobid2(String alephid)
			throws IOException {
		String lobidUri = "http://lobid.org/resources/" + alephid;
		play.Logger.info("GET " + lobidUri + " and analyse date");
		URL lobidUrl = new URL("http://lobid.org/resources/" + alephid);
		RDFFormat inFormat = RDFFormat.TURTLE;
		String accept = "text/turtle";
		Collection<Statement> graph =
				RdfUtils.readRdfToGraph(lobidUrl, inFormat, accept);
		Iterator<Statement> it = graph.iterator();
		while (it.hasNext()) {
			Statement s = it.next();
			String predicate = s.getPredicate().stringValue();
			if (predicate.equals("http://purl.org/dc/terms/modified")) {
				LocalDate date = LocalDate.parse(s.getObject().stringValue(),
						DateTimeFormatter.ofPattern("yyyy-MM-dd"));
				return date;
			}
		}
		return LocalDate.now();
	}

	private LocalDate getLastModifiedFromLobid(String alephid)
			throws IOException {
		String lobidUri = "http://lobid.org/resource/" + alephid;
		play.Logger.info("GET " + lobidUri + " and analyse date");
		URL lobidUrl = new URL("http://lobid.org/resource/" + alephid + "/about");
		RDFFormat inFormat = RDFFormat.TURTLE;
		String accept = "text/turtle";
		Collection<Statement> graph =
				RdfUtils.readRdfToGraph(lobidUrl, inFormat, accept);
		Iterator<Statement> it = graph.iterator();
		while (it.hasNext()) {
			Statement s = it.next();
			String predicate = s.getPredicate().stringValue();
			if (predicate.equals("http://purl.org/dc/terms/modified")) {
				LocalDate date = LocalDate.parse(s.getObject().stringValue(),
						DateTimeFormatter.ofPattern("yyyyMMdd"));
				return date;
			}
		}
		return LocalDate.now();
	}

	private String getLobidDataAsNtripleString(Node node, String alephid) {
		String pid = node.getPid();
		String lobidUri = "http://lobid.org/resource/" + alephid;
		play.Logger.info("GET " + lobidUri);
		try {
			URL lobidUrl = new URL("http://lobid.org/resource/" + alephid + "/about");
			RDFFormat inFormat = RDFFormat.TURTLE;
			String accept = "text/turtle";
			Collection<Statement> graph =
					RdfUtils.readRdfToGraphAndFollowSameAs(lobidUrl, inFormat, accept);
			String almaid = RdfUtils.getAlmaId(graph, alephid);
			String lobidUriAlmaId = "http://lobid.org/resources/" + almaid + "#!";
			ValueFactory f = RdfUtils.valueFactory;
			Statement parallelEditionStatement = f.createStatement(f.createIRI(pid),
					f.createIRI(archive.fedora.Vocabulary.REL_MAB_527),
					f.createIRI(lobidUriAlmaId));
			graph.add(parallelEditionStatement);
			tryToImportOrderingFromLobidData2(node, graph, f);
			tryToGetTypeFromLobidData2(node, graph, f);
			return RdfUtils.graphToString(
					RdfUtils.rewriteSubject(lobidUriAlmaId, pid, graph),
					RDFFormat.NTRIPLES);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}

	}

	public static String getLobid2DataAsNtripleString(Node node, String alephid) {
		String pid = node.getPid();
		String lobidUri = "http://lobid.org/resources/" + alephid + "#!";
		play.Logger.info("GET " + lobidUri);
		try {
			URL lobidUrl = new URL("http://lobid.org/resources/" + alephid);
			RDFFormat inFormat = RDFFormat.TURTLE;
			String accept = "text/turtle";
			Collection<Statement> graph =
					RdfUtils.readRdfToGraphAndFollowSameAs(lobidUrl, inFormat, accept);
			String almaid = RdfUtils.getAlmaId(graph, alephid);
			String lobidUriAlmaId = "http://lobid.org/resources/" + almaid + "#!";
			ValueFactory f = RdfUtils.valueFactory;
			Statement parallelEditionStatement = f.createStatement(f.createIRI(pid),
					f.createIRI(archive.fedora.Vocabulary.REL_MAB_527),
					f.createIRI(lobidUriAlmaId));
			graph.add(parallelEditionStatement);
			return RdfUtils.graphToString(
					RdfUtils.rewriteSubject(lobidUriAlmaId, pid, graph),
					RDFFormat.NTRIPLES);
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}

	}

	private void tryToImportOrderingFromLobidData2(Node node,
			Collection<Statement> graph, ValueFactory f) {
		try {
			String ordering = getAuthorOrdering(node);
			if (ordering != null) {
				Statement contributorOrderStatement =
						f.createStatement(f.createIRI(node.getPid()),
								f.createIRI("http://purl.org/lobid/lv#contributorOrder"),
								f.createLiteral(ordering));
				graph.add(contributorOrderStatement);
			}
		} catch (Exception e) {
			play.Logger.error(node.getPid() + ": Ordering info not available!");
		}
	}

	private void tryToGetTypeFromLobidData2(Node node,
			Collection<Statement> graph, ValueFactory f) {
		try {
			List<String> type = getType(node);
			if (!type.isEmpty()) {
				for (String t : type) {
					Statement typeStatement =
							f.createStatement(f.createIRI(node.getPid()),
									f.createIRI(
											"http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
									f.createIRI(t));
					graph.add(typeStatement);
				}
			}
		} catch (Exception e) {
			play.Logger.error(node.getPid() + ": Ordering info not available!");
		}
	}

	private void reindexNodeAndParent(Node node) {
		node = updateIndex(node.getPid());
		String parentPid = node.getParentPid();
		if (parentPid != null && !parentPid.isEmpty()) {
			updateIndex(parentPid);
		}
	}

	/**
	 * Generates a urn
	 * 
	 * @param id usually the id of an object without namespace
	 * @param namespace usually the namespace
	 * @param snid the urn subnamespace id e.g."hbz:929:02"
	 * @param userId
	 * @return the urn
	 */
	public String addUrn(String id, String namespace, String snid,
			String userId) {
		String pid = namespace + ":" + id;
		return addUrn(pid, snid, userId);
	}

	/**
	 * Generates a urn
	 * 
	 * @param pid usually the id of an object with namespace
	 * @param snid the urn subnamespace id e.g."hbz:929:02"
	 * @return the urn
	 */
	String addUrn(String pid, String snid, String userId) {
		Node node = new Read().readNode(pid);
		node.setLastModifiedBy(userId);
		return addUrn(node, snid);
	}

	/**
	 * Generates a urn
	 * 
	 * @param node the node to add a urn to
	 * @param snid the urn subnamespace id e.g."hbz:929:02"
	 * @return the urn
	 */
	String addUrn(Node node, String snid) {
		String subject = node.getPid();
		if (node.hasUrnInMetadata() || node.hasUrn())
			throw new HttpArchiveException(409,
					subject + " already has a urn. Leave unmodified!");
		String urn = generateUrn(subject, snid);
		node.setUrn(urn);
		return OaiDispatcher.makeOAISet(node);
	}

	/**
	 * Generates a urn
	 * 
	 * @param node the object
	 * @param snid the urn subnamespace id
	 * @param userId
	 * @return the urn
	 */
	public String replaceUrn(Node node, String snid, String userId) {
		String urn = generateUrn(node.getPid(), snid);
		node.setLastModifiedBy(userId);
		node.setUrn(urn);
		return OaiDispatcher.makeOAISet(node);
	}

	/**
	 * @param nodes a list of nodes
	 * @param snid a urn snid e.g."hbz:929:02"
	 * @param fromBefore only objects created before "fromBefore" will get a urn
	 * @return a message
	 */
	public String addUrnToAll(List<Node> nodes, String snid, Date fromBefore) {
		return apply(nodes, n -> addUrn(n, snid, fromBefore));
	}

	private String addUrn(Node n, String snid, Date fromBefore) {
		String contentType = n.getContentType();
		if (n.getCreationDate().before(fromBefore)) {
			if ("journal".equals(contentType)) {
				return addUrn(n, snid);
			} else if ("monograph".equals(contentType)) {
				return addUrn(n, snid);
			} else if ("file".equals(contentType)) {
				return addUrn(n, snid);
			}
		}
		return "Not Updated " + n.getPid() + " " + n.getCreationDate()
				+ " is not before " + fromBefore + " or contentType " + contentType
				+ " is not allowed to carry urn.";
	}

	/**
	 * @param nodes a list of nodes
	 * @param fromBefore only nodes from before the given Date will be modified
	 * @return a message for client
	 */
	public String addDoiToAll(List<Node> nodes, Date fromBefore) {
		return apply(nodes, n -> addDoi(n, fromBefore));
	}

	private String addDoi(Node n, Date fromBefore) {
		try {
			String contentType = n.getContentType();
			if (n.getCreationDate().before(fromBefore)) {
				if ("monograph".equals(contentType)) {
					return MyController.mapper.writeValueAsString(addDoi(n));
				}
			}
			return "Not Updated " + n.getPid() + " " + n.getCreationDate()
					+ " is not before " + fromBefore + " or contentType " + contentType
					+ " is not allowed to carry urn.";
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * Generates a urn
	 * 
	 * @param niss usually the pid of an object
	 * @param snid usually the namespace
	 * @return the urn
	 */
	String generateUrn(String niss, String snid) {
		URN urn = new URN(snid, niss);
		return urn.toString();
	}

	/**
	 * @param node generate metadatafile with lobid data for this node
	 * @return a short message
	 */
	public String lobidify2(Node node) {
		return updateLobidify2AndEnrichMetadata(node, node.getMetadata2());
	}

	public String lobidify2(Node node, LocalDate date) {
		return updateLobidify2AndEnrichMetadataIfRecentlyUpdated(node,
				node.getMetadata2(), date);
	}

	/**
	 * reinits oai sets on every node
	 * 
	 * @param nodes a list of nodes
	 * @return a message
	 */
	public String reinitOaiSets(List<Node> nodes) {
		return apply(nodes, n -> OaiDispatcher.makeOAISet(n));
	}

	/**
	 * Imports lobid metadata for each node in the list
	 * 
	 * @param nodes list of nodes
	 * @return a message
	 */
	public String lobidify(List<Node> nodes) {
		return apply(nodes, n -> lobidify2(n));
	}

	/**
	 * @param node links the node to it's parents parent.
	 * @return the updated node
	 */
	public Node moveUp(Node node) {
		String recentParent = node.getParentPid();
		Node parent = new Read().readNode(recentParent);
		String destinyPid = parent.getParentPid();
		if (destinyPid == null || destinyPid.isEmpty())
			throw new HttpArchiveException(406,
					"Can't find valid destiny for move operation. " + node.getParentPid()
							+ " parent of " + node.getPid() + " has no further parent.");
		ToScienceObject object = new ToScienceObject();
		object.setParentPid(destinyPid);
		node = new Create().patchResource(node, object);
		play.Logger.info("Move " + node.getPid() + " to new parent "
				+ node.getParentPid() + ". Recent Parent was " + recentParent
				+ ". Calculated destiny was " + destinyPid);
		return node;
	}

	/**
	 * @param node the node is the target of the copy operation
	 * @param field defines which metadata field to copy
	 * @param copySource the pid of the source of the copy operation
	 * @return the updated node
	 */
	public Node copyMetadata(Node node, String field, String copySource) {
		if (copySource.isEmpty()) {
			copySource = node.getParentPid();
		}
		Node parent = new Read().readNode(copySource);
		String subject = node.getPid();
		play.Logger.trace("Try to enrich " + node.getPid() + " with "
				+ parent.getPid() + " . Looking for field " + field);
		String pred = getUriFromJsonName(field);
		List<String> value = RdfUtils.findRdfObjects(subject, pred,
				parent.getMetadata2(), RDFFormat.NTRIPLES);
		String metadata = node.getMetadata2();
		if (metadata == null)
			metadata = "";
		if (value != null && !value.isEmpty()) {
			metadata =
					RdfUtils.replaceTriple(subject, pred, value.get(0), true, metadata);
		} else {
			throw new HttpArchiveException(406,
					"Source object " + copySource + " has no field: " + field);
		}
		updateLobidifyAndEnrichMetadata(node, metadata);
		return node;
	}

	/**
	 * @param nodes a list of nodes to hammer on
	 * @return a message
	 */
	public String flattenAll(List<Node> nodes) {
		return apply(nodes, n -> flatten(n).getPid());
	}

	/**
	 * Flatten a node means to take the title of the parent and to move up the
	 * node by one level in the object tree
	 * 
	 * @param n the node to hammer on
	 * @return the updated node
	 */
	public Node flatten(Node n) {
		return moveUp(copyMetadata(n, "title", ""));
	}

	@SuppressWarnings({ "serial" })
	class MetadataNotFoundException extends RuntimeException {
		MetadataNotFoundException(Throwable e) {
			super(e);
		}
	}

	@SuppressWarnings({ "serial" })
	public class UpdateNodeException extends RuntimeException {
		UpdateNodeException(Throwable cause) {
			super(cause);
		}
	}

	@SuppressWarnings({ "serial" })
	private class NotUpdatedException extends RuntimeException {
		NotUpdatedException(String msg) {
			super(msg);
		}
	}

	/**
	 * Creates a new doi identifier and registers to datacite
	 * 
	 * @param node
	 * @return a key value structure as feedback to the client
	 */
	public Map<String, Object> addDoi(Node node) {
		String contentType = node.getContentType();
		if ("file".equals(contentType) || "issue".equals(contentType)
				|| "volume".equals(contentType)) {
			throw new HttpArchiveException(412, node.getPid()
					+ " resource is of type " + contentType
					+ ". It is not allowed to mint Dois for this type. Leave unmodified!");
		}
		Map<String, Object> result = new HashMap<>();
		String doi = node.getDoi();
		play.Logger.debug("doi=" + doi);

		if (doi == null || doi.isEmpty()) {
			doi = createDoiIdentifier(node);
			play.Logger.debug("doi=" + doi);
			result.put("Doi", doi);
			// node.setDoi(doi);
			String objectUrl = Globals.urnbase + node.getPid();
			play.Logger.debug("objectUrl=" + objectUrl);

			String xml = new Transform().datacite(node, doi);
			play.Logger.debug("xml=" + xml);
			MyController.validate(xml,
					"public/schemas/datacite/kernel-4.1/metadata.xsd",
					"https://schema.datacite.org/meta/kernel-4.1/",
					"public/schemas/datacite/kernel-4.1/");
			try {
				DataciteClient client = new DataciteClient();
				play.Logger.debug("xml=" + xml);
				result.put("Metadata", xml);
				String registerMetadataResponse =
						client.registerMetadataAtDatacite(node, xml);
				play.Logger
						.debug("registerMetadataResponse=" + registerMetadataResponse);

				result.put("registerMetadataResponse", registerMetadataResponse);
				if (client.getStatus() != 200)
					throw new RuntimeException("Registering Doi failed!");
				String mintDoiResponse = client.mintDoiAtDatacite(doi, objectUrl);
				result.put("mintDoiResponse", mintDoiResponse);
				if (client.getStatus() != 200)
					throw new RuntimeException("Minting Doi failed!");

			} catch (Exception e) {
				throw new HttpArchiveException(502, node.getPid() + " Add Doi failed!\n"
						+ result + "\n Datacite replies: " + e.getMessage());
			}
			ToScienceObject o = new ToScienceObject();
			o.getIsDescribedBy().setDoi(doi);
			new Create().patchResource(node, o);
			play.Logger.debug("result=" + result.toString());
			return result;
		} else {
			throw new HttpArchiveException(409,
					node.getPid() + " already has a doi. Leave unmodified! ");
		}

	}

	/**
	 * Creates a new doi identifier and registers to datacite
	 * 
	 * @param node
	 * @return a key value structure as feedback to the client
	 */
	public Map<String, Object> replaceDoi(Node node) {
		node.setDoi(null);
		return addDoi(node);
	}

	/**
	 * Updates an existing doi's metadata and url
	 * 
	 * @param node
	 * @return a key value structure as feedback to the client
	 */
	public Map<String, Object> updateDoi(Node node) {
		Map<String, Object> result = new HashMap<String, Object>();
		String doi = node.getDoi();
		result.put("Doi", doi);
		if (doi == null || doi.isEmpty()) {
			throw new HttpArchiveException(412, node.getPid()
					+ " resource is not associated to doi. Please create a doi first (POST /doi).  Leave unmodified!");
		} else {
			String objectUrl = Globals.urnbase + node.getPid();
			String xml = new Transform().datacite(node, doi);
			MyController.validate(xml,
					"public/schemas/datacite/kernel-4.1/metadata.xsd",
					"https://schema.datacite.org/meta/kernel-4.1/",
					"public/schemas/datacite/kernel-4.1/");
			DataciteClient client = new DataciteClient();
			String registerMetadataResponse =
					client.registerMetadataAtDatacite(node, xml);
			String mintDoiResponse = client.mintDoiAtDatacite(doi, objectUrl);
			String makeOaiSetResponse = OaiDispatcher.makeOAISet(node);
			result.put("Metadata", xml);
			result.put("registerMetadataResponse", registerMetadataResponse);
			result.put("mintDoiResponse", mintDoiResponse);
			result.put("makeOaiSetResponse", makeOaiSetResponse);
			return result;
		}
	}

	private String createDoiIdentifier(Node node) {
		String pid = node.getPid();
		String id = pid.replace(node.getNamespace() + ":", "");
		play.Logger.debug("id=" + id);
		String doi = Globals.doiPrefix + "00" + id;
		play.Logger.debug("doi=" + doi);
		return doi;
	}

	/**
	 * @param node the node to add a conf to
	 * @param content json representation of conf
	 * @return a message
	 */
	public String updateConf(Node node, String content) {
		try {
			if (content == null) {
				throw new HttpArchiveException(406,
						node.getPid() + " You've tried to upload an empty string."
								+ " This action is not supported."
								+ " Use HTTP DELETE instead.\n");
			}
			play.Logger.info("Write to conf: " + content);
			File file = CopyUtils.copyStringToFile(content);
			if (node != null) {
				node.setConfFile(file.getAbsolutePath());
				play.Logger.info("Update node" + file.getAbsolutePath());
				Globals.fedora.updateNode(node);
			}
			updateIndex(node.getPid());
			return node.getPid() + " webgatherer conf updated!";
		} catch (RdfException e) {
			throw new HttpArchiveException(400, e);
		} catch (IOException e) {
			throw new UpdateNodeException(e);
		}
	}

	/**
	 * @param node the node to add an urlHist to
	 * @param content json representation of conf
	 * @return a message
	 */
	public String updateUrlHist(Node node, String content) {
		try {
			if (content == null) {
				throw new HttpArchiveException(406,
						node.getPid() + " You've tried to upload an empty string."
								+ " This action is not supported."
								+ " Use HTTP DELETE instead.\n");
			}
			play.Logger.debug("Schreibe nach URL-Historie: " + content);
			File file = CopyUtils.copyStringToFile(content);
			if (node != null) {
				node.setUrlHistFile(file.getAbsolutePath());
				play.Logger.info("Update node" + file.getAbsolutePath());
				Globals.fedora.updateNode(node);
			}
			updateIndex(node.getPid());
			return node.getPid() + " url history updated!";
		} catch (RdfException e) {
			throw new HttpArchiveException(400, e);
		} catch (IOException e) {
			throw new UpdateNodeException(e);
		}
	}

	/**
	 * the node
	 * 
	 * @param pred Rdf-Predicate will be added to /metadata of node
	 * @param obj Rdf-Object will be added to /metadata of node
	 * @return a user message as string
	 */
	public String addMetadataField(Node node, String pred, String obj) {
		String metadata = node.getMetadata2();
		metadata = RdfUtils.addTriple(node.getPid(), pred, obj, true, metadata,
				RDFFormat.NTRIPLES);
		updateLobidify2AndEnrichMetadata(node, metadata);
		node = new Read().readNode(node.getPid());
		OaiDispatcher.makeOAISet(node);
		return "Update " + node.getPid() + "! " + pred + " has been added.";

	}

	/**
	 * @param node what was modified?
	 * @param date when was modified?
	 * @param userId who has modified?
	 * @return a user message in form of a map
	 */
	public Map<String, Object> setObjectTimestamp(Node node, Date date,
			String userId) {
		Map<String, Object> result = new HashMap<String, Object>();
		try {
			String content = Globals.dateFormat.format(date);
			File file = CopyUtils.copyStringToFile(content);
			node.setObjectTimestampFile(file.getAbsolutePath());
			node.setLastModifiedBy(userId);
			result.put("pid", node.getPid());
			result.put("timestamp", content);
			Globals.fedora.updateNode(node);
			String pp = node.getParentPid();
			if (pp != null) {
				Node parent = new Read().readNode(pp);
				result.put("parent", setObjectTimestamp(parent, date, userId));
			}
			updateIndex(node.getPid());
			return result;
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}
	}

	public String lobidify2(Node node, String alephid) {
		updateMetadata(metadata2, node,
				getLobid2DataAsNtripleString(node, alephid));
		String enrichMessage = Enrich.enrichMetadata2(node);
		return enrichMessage;
	}

	private static String getAuthorOrdering(Node node) {
		try (InputStream in =
				new ByteArrayInputStream(node.getMetadata2().getBytes())) {
			Collection<Statement> myGraph =
					RdfUtils.readRdfToGraph(in, RDFFormat.NTRIPLES, "");
			Iterator<Statement> statements = myGraph.iterator();
			while (statements.hasNext()) {
				Statement curStatement = statements.next();
				String pred = curStatement.getPredicate().stringValue();
				String obj = curStatement.getObject().stringValue();
				if ("http://purl.org/lobid/lv#contributorOrder".equals(pred)) {
					return obj;
				}
			}
			return null;
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	private static List<String> getType(Node node) {
		try (InputStream in =
				new ByteArrayInputStream(node.getMetadata2().getBytes())) {
			List<String> result = new ArrayList<>();
			Collection<Statement> myGraph =
					RdfUtils.readRdfToGraph(in, RDFFormat.NTRIPLES, "");
			Iterator<Statement> statements = myGraph.iterator();
			while (statements.hasNext()) {
				Statement curStatement = statements.next();
				String subj = curStatement.getSubject().stringValue();
				String pred = curStatement.getPredicate().stringValue();
				String obj = curStatement.getObject().stringValue();
				if (subj.equals(node.getPid())) {
					if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(pred)) {
						result.add(obj);
					}
				}
			}
			return result;
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	public String rdfToString(Map<String, Object> result, RDFFormat format) {
		try {
			play.Logger.debug("result=" + result.toString());
			String rdf = RdfUtils.readRdfToString(
					new ByteArrayInputStream(json(result).getBytes("utf-8")),
					RDFFormat.JSONLD, format, "");
			return rdf;
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/*
	 * Mappt Objekt nach JSON-String. "Objekt" ist typischerweise eine Java Map.
	 */
	private String json(Object obj) {
		try {
			play.Logger.debug("Start json(obj)");
			StringWriter w = new StringWriter();
			ObjectMapper mapper = JsonUtil.mapper();
			mapper.writeValue(w, obj);
			String result = w.toString();
			play.Logger.debug("Return result " + result);
			return result;
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}
	}

	public String updateMetadataJson(Node node, String content) {
		try {
			return updateMetadata("toscience", node, content);
		} catch (Exception e) {
			play.Logger.error(e.getMessage());
			throw new RuntimeException(e);
		}
	}

	public String updateMetadata(String metadataType, Node node, String content) {
		try {
			String pid = node.getPid();
			play.Logger.debug(
					"Updating metadata of type " + metadataType + " on PID " + pid);
			play.Logger.debug("content: " + content);
			if (content == null) {
				throw new HttpArchiveException(406,
						pid + " You've tried to upload an empty string."
								+ " This action is not supported."
								+ " Use HTTP DELETE instead.\n");
			}

			if (metadataType.equals(metadata2)) {
				content = rewriteContent(content, pid);
			}

			File file = CopyUtils.copyStringToFile(content);
			play.Logger
					.debug("content.file.getAbsolutePath():" + file.getAbsolutePath());
			node.setMetadataFile(metadataType, file.getAbsolutePath());
			node.setMetadata(metadataType, content);
			OaiDispatcher.makeOAISet(node);
			reindexNodeAndParent(node);
			return pid + " metadata of type " + metadataType
					+ " successfully updated!";
		} catch (RdfException e) {
			throw new HttpArchiveException(400, e);
		} catch (IOException e) {
			throw new UpdateNodeException(e);
		}
	}

	public String updateMetadataTosAndKtbl(String metadataType, Node node,
			String contentTos, String contentKtbl) {
		try {
			String pid = node.getPid();

			if (contentTos == null || contentKtbl == null) {
				throw new HttpArchiveException(406,
						pid + " You've tried to upload an empty string."
								+ " This action is not supported."
								+ " Use HTTP DELETE instead.\n");
			}
			if (metadataType.equals(metadata2)) {
				contentTos = rewriteContent(contentTos, pid);
				contentKtbl = rewriteContent(contentKtbl, pid);
			}
			File file = CopyUtils.copyStringToFile(contentTos);
			node.setMetadataFile(metadataType, file.getAbsolutePath());
			node.setMetadata(metadataType, contentTos + contentKtbl);
			OaiDispatcher.makeOAISet(node);
			reindexNodeAndParent(node);
			return pid + " metadata of type " + metadataType
					+ " successfully updated!";
		} catch (RdfException e) {
			throw new HttpArchiveException(400, e);
		} catch (IOException e) {
			throw new UpdateNodeException(e);
		}
	}

}
