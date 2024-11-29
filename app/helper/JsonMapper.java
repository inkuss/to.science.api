/*
 * Copyright 2015 hbz NRW (http://www.hbz-nrw.de/)
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
package helper;

import static archive.fedora.FedoraVocabulary.HAS_PART;
import static archive.fedora.FedoraVocabulary.IS_PART_OF;
import static archive.fedora.Vocabulary.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import actions.Read;
import archive.fedora.RdfUtils;
import de.hbz.lobid.helper.EtikettMakerInterface;
import de.hbz.lobid.helper.JsonConverter;
import models.Globals;
import models.Link;
import models.Node;
import play.Play;

/**
 * @author jan schnasse
 * @author Ingolf Kuss, hbz
 *
 */

@SuppressWarnings("unchecked")
public class JsonMapper {

	private static final String PREF_LABEL = Globals.profile.getLabelKey();
	private static final String ID2 = Globals.profile.getIdAlias();
	/**
	 * Here are some short names that must be defined in the context document that
	 * is loaded at Globals.context.
	 */
	final static String primaryTopic = "primaryTopic";
	final static String contentType = "contentType";
	final static String accessScheme = "accessScheme";
	final static String publishScheme = "publishScheme";
	final static String transformer = "transformer";
	final static String catalogId = "catalogId";
	final static String createdBy = "createdBy";
	final static String legacyId = "legacyId";
	final static String importedFrom = "importedFrom";
	final static String name = "name";
	final static String urn = "urn";
	final static String lastModifiedBy = "lastModifiedBy";
	final static String modified = "modified";
	final static String objectTimestamp = "objectTimestamp";
	final static String created = "created";
	final static String describes = "describes";
	final static String isDescribedBy = "isDescribedBy";
	final static String doi = "doi";
	final static String parentPid = "parentPid";
	final static String format = "format";
	final static String size = "size";
	final static String checksumValue = "checksumValue";
	final static String generator = "generator";
	final static String rdftype = "rdftype";
	final static String checksum = "checksum";
	final static String hasData = "hasData";
	final static String fulltext_ocr = "fulltext-ocr";
	final static String title = "title";
	final static String fileLabel = "fileLabel";
	final static String embargoTime = "embargoTime";

	final static String[] typePrios = new String[] {
			"http://purl.org/lobid/lv#ArchivedWebPage",
			"http://purl.org/ontology/bibo/Report",
			"http://purl.org/lobid/lv#Biography", "http://purl.org/library/Game",
			"http://purl.org/lobid/lv#Schoolbook",
			"http://purl.org/ontology/mo/PublishedScore",
			"http://purl.org/lobid/lv#Legislation",
			"http://purl.org/ontology/bibo/ReferenceSource",
			"http://purl.org/lobid/lv#OfficialPublication",
			"http://purl.org/lobid/lv#Bibliography",
			"http://purl.org/lobid/lv#Festschrift",
			"http://purl.org/ontology/bibo/Proceedings",
			"http://hbz-nrw.de/regal#ResearchData",
			"http://purl.org/lobid/lv#EditedVolume",
			"http://purl.org/ontology/bibo/Thesis",
			"http://purl.org/ontology/mo/Record", "http://purl.org/ontology/bibo/Map",
			"http://purl.org/ontology/bibo/AudioVisualDocument",
			"http://purl.org/ontology/bibo/AudioDocument",
			"http://purl.org/ontology/bibo/Image",
			"http://purl.org/ontology/bibo/Article",
			"http://rdvocab.info/termList/RDACarrierType/1018",
			"http://rdvocab.info/termList/RDACarrierType/1010",
			"http://iflastandards.info/ns/isbd/terms/mediatype/T1002",
			"http://purl.org/ontology/bibo/MultiVolumeBook",
			"http://purl.org/ontology/bibo/Journal",
			"http://purl.org/ontology/bibo/Newspaper",
			"http://purl.org/ontology/bibo/Series",
			"http://purl.org/ontology/bibo/Periodical",
			"http://purl.org/ontology/bibo/Collection",
			"http://purl.org/ontology/bibo/Standard",
			"http://purl.org/lobid/lv#Statistics",
			"http://purl.org/ontology/bibo/Book",
			"http://data.archiveshub.ac.uk/def/ArchivalResource",
			"http://purl.org/ontology/bibo/Document",
			"http://purl.org/vocab/frbr/core#Manifestation",
			"http://purl.org/lobid/lv#Miscellaneous",
			"http://purl.org/dc/terms/BibliographicResource",
			"info:regal/zettel/File" };

	Node node = null;
	String toscience_id = null;
	EtikettMakerInterface profile = Globals.profile;
	JsonConverter jsonConverter = null;

	/**
	 * Ein Konstruktor für diese Klasse
	 */
	public JsonMapper() {
		play.Logger.info("Creating new instance of Class JsonMapper");
		jsonConverter = new JsonConverter(profile);
	}

	/**
	 * Ein Konstruktor für diese Klasse, falls Metadata2-Datenstrom schon
	 * vorhanden ist.
	 * 
	 * @param n the node will be mapped to json ld in accordance to the profile
	 */
	public JsonMapper(final Node n) {
		try {
			jsonConverter = new JsonConverter(profile);
			this.node = n;
			if (node == null)
				throw new NullPointerException(
						"JsonMapper can not work on node with value NULL!");
			if (node.getMetadata2() == null)
				throw new NullPointerException(
						node.getPid() + " metadata2 stream is NULL!");
		} catch (Exception e) {
			play.Logger.trace("", e.getMessage());
			play.Logger.trace("", e);
		}

	}

	/**
	 * @return a map without the context document
	 */
	public Map<String, Object> getLdWithoutContext() {
		Map<String, Object> map = getLd();
		map.remove("@context");
		return map;
	}

	/**
	 * @return a map without the context document
	 */
	public Map<String, Object> getLd2WithoutContext() {
		Map<String, Object> map = getLd2();
		map.remove("@context");
		return map;

	}

	/**
	 * @return a map without the context document
	 */
	public Map<String, Object> getLdWithoutContextShortStyle() {
		Map<String, Object> map = getLdShortStyle();
		map.remove("@context");
		return map;
	}

	/**
	 * Holt Metadaten im Format lobid --- VERALTET ! DEPRECATED !
	 * 
	 * @return a map representing the rdf data on this object
	 */
	public Map<String, Object> getLd() {
		Collection<Link> ls = node.getRelsExt();
		Map<String, Object> m = getDescriptiveMetadata2();
		Map<String, Object> rdf = m == null ? new HashMap<>() : m;

		changeDcIsPartOfToRegalIsPartOf(rdf);
		rdf.remove("describedby");
		rdf.remove("sameAs");

		rdf.put(ID2, node.getPid());
		rdf.put(primaryTopic, node.getPid());
		for (Link l : ls) {
			if (HAS_PART.equals(l.getPredicate()))
				continue;
			if (REL_HBZ_ID.equals(l.getPredicate()))
				continue;
			if (IS_PART_OF.equals(l.getPredicate()))
				continue;
			addLinkToJsonMap(rdf, l);
		}
		addPartsToJsonMap(rdf);
		rdf.remove("isNodeType");

		rdf.put(contentType, node.getContentType());
		rdf.put(accessScheme, node.getAccessScheme());
		rdf.put(publishScheme, node.getPublishScheme());
		rdf.put(transformer, node.getTransformer().stream().map(t -> t.getId())
				.collect(Collectors.toList()));
		rdf.put(catalogId, node.getCatalogId());
		// rdf.put(embargoTime, node.getEmbargoTime());

		if (node.getFulltext() != null)
			rdf.put(fulltext_ocr, node.getFulltext());

		Map<String, Object> aboutMap = new TreeMap<>();
		aboutMap.put(ID2, node.getAggregationUri() + ".rdf");
		if (node.getCreatedBy() != null)
			aboutMap.put(createdBy, node.getCreatedBy());
		if (node.getLegacyId() != null)
			aboutMap.put(legacyId, node.getLegacyId());
		if (node.getImportedFrom() != null)
			aboutMap.put(importedFrom, node.getImportedFrom());
		if (node.getName() != null)
			aboutMap.put(name, node.getName());
		if (node.getLastModifiedBy() != null)
			aboutMap.put(lastModifiedBy, node.getLastModifiedBy());

		aboutMap.put(modified, node.getLastModified());
		if (node.getObjectTimestamp() != null) {
			aboutMap.put(objectTimestamp, node.getObjectTimestamp());
		} else {
			aboutMap.put(objectTimestamp, node.getLastModified());
		}
		aboutMap.put(created, node.getCreationDate());
		aboutMap.put(describes, node.getAggregationUri());

		rdf.put(isDescribedBy, aboutMap);
		if (node.getDoi() != null) {
			rdf.put(doi, node.getDoi());
		}
		if (node.getUrn() != null) {
			rdf.put(urn, node.getUrn());
		}

		if (node.getParentPid() != null)
			rdf.put(parentPid, node.getParentPid());

		if (node.getMimeType() != null && !node.getMimeType().isEmpty()) {
			Map<String, Object> hasDataMap = new TreeMap<>();
			hasDataMap.put(ID2, node.getDataUri());
			hasDataMap.put(format, node.getMimeType());
			hasDataMap.put(size, node.getFileSize());
			if (node.getFileLabel() != null)
				hasDataMap.put(fileLabel, node.getFileLabel());
			if (node.getChecksum() != null) {
				Map<String, Object> checksumMap = new TreeMap<>();
				checksumMap.put(checksumValue, node.getChecksum());
				checksumMap.put(generator, "http://en.wikipedia.org/wiki/MD5");
				checksumMap.put(rdftype,
						"http://downlode.org/Code/RDF/File_Properties/schema#Checksum");
				hasDataMap.put(checksum, checksumMap);
			}
			rdf.put(hasData, hasDataMap);
		}
		rdf.put("@context", Globals.protocol + Globals.server + "/context.json");
		postprocessing(rdf);
		rdf.remove("note");
		return rdf;
	}

	private static void changeDcIsPartOfToRegalIsPartOf(Map<String, Object> rdf) {
		Object pid = rdf.get("parentPid");
		if (pid != null) {
			rdf.put("externalParent", pid);
			rdf.remove("parentPid");
		}
	}

	/**
	 * Holt Metadaten im Format lobid2 als Java Map
	 * 
	 * @return
	 */
	private Map<String, Object> getDescriptiveMetadata2() {
		try {
			InputStream stream = new ByteArrayInputStream(
					node.getMetadata2().getBytes(StandardCharsets.UTF_8));
			Map<String, Object> rdf = jsonConverter.convert(node.getPid(), stream,
					RDFFormat.NTRIPLES, profile.getContext().get("@context"));
			return rdf;
		} catch (Exception e) {
			play.Logger.trace(node.getPid() + " has no descriptive Metadata2!");
			play.Logger.trace("", e);
		}
		return null;
	}

	/**
	 * @return linked data json optimized for displaying large trees. Most of the
	 *         metadata has been left out.
	 */
	public Map<String, Object> getLdShortStyle() {
		Collection<Link> ls = node.getRelsExt();
		Map<String, Object> m = getDescriptiveMetadata2();
		Map<String, Object> rdf = m == null ? new HashMap<>() : m;
		rdf.put(ID2, node.getPid());
		for (Link l : ls) {
			if (getUriFromJsonName(title).equals(l.getPredicate())) {
				addLinkToJsonMap(rdf, l);
				break;
			}
		}
		addPartsToJsonMap(rdf);
		rdf.remove("isNodeType");
		rdf.put(contentType, node.getContentType());
		if (node.getParentPid() != null)
			rdf.put(parentPid, node.getParentPid());
		if (node.getMimeType() != null && !node.getMimeType().isEmpty()) {
			Map<String, Object> hasDataMap = new HashMap<>();
			hasDataMap.put(ID2, node.getDataUri());
			hasDataMap.put(format, node.getMimeType());
			hasDataMap.put(size, node.getFileSize());
			if (node.getChecksum() != null) {
				Map<String, Object> checksumMap = new HashMap<>();
				checksumMap.put(checksumValue, node.getChecksum());
				checksumMap.put(generator, "http://en.wikipedia.org/wiki/MD5");
				checksumMap.put(rdftype,
						"http://downlode.org/Code/RDF/File_Properties/schema#Checksum");
				hasDataMap.put(checksum, checksumMap);
			}
			rdf.put("hasData", hasDataMap);
		}
		postprocessing(rdf);
		return rdf;
	}

	public void postprocessing(Map<String, Object> rdf) {
		try {
			addCatalogLink(rdf);
			if ("file".equals(rdf.get("contentType"))) {
				rdf.put(rdftype, Arrays.asList(new String[] { "File" }));
			}

			Collection<Map<String, Object>> t =
					getType(new ObjectMapper().valueToTree(rdf));
			if (t != null && t.size() != 0)
				rdf.put(rdftype, t);

			sortCreatorAndContributors(rdf);
			postProcessSubjectName(rdf);
			postProcess(rdf, "subject");
			postProcess(rdf, "agrovoc");
			postProcess(rdf, "contributor");
			postProcess(rdf, "redaktor");
			postProcess(rdf, "actor");
			postProcess(rdf, "producer");
			postProcess(rdf, "interviewer");
			postProcess(rdf, "collaborator");
			postProcess(rdf, "cartographer");
			postProcess(rdf, "director");
			postProcess(rdf, "cinematographer");
			postProcess(rdf, "photographer");
			postProcess(rdf, "engraver");
			postProcess(rdf, "contributor_");
			postProcess(rdf, "dedicatee");
			postProcess(rdf, "honoree");
			postProcess(rdf, "singer");
			postProcess(rdf, "professionalGroup");
			postProcess(rdf, "editor");
			postProcess(rdf, "publisher");
			postProcess(rdf, "recordingLocation");
			postProcess(rdf, "recordingCoords");
			postProcess(rdf, "collectionOne");
			postProcess(rdf, "medium");
			postProcess(rdf, "predecessor");
			postProcess(rdf, "successor");
			postProcess(rdf, "primaryForm");
			postProcess(rdf, "natureOfContent");
			postProcess(rdf, "institution");
			postProcessContribution(rdf);
			postProcess(rdf, "creator");
			postProcessLinkFields("additionalMaterial", rdf);
			postProcessLinkFields("publisherVersion", rdf);
			postProcessLinkFields("fulltextVersion", rdf);
			createJoinedFunding(rdf);
		} catch (Exception e) {
			play.Logger.debug("", e);
		}
	}

	private void postProcessLinkFields(String key, Map<String, Object> rdf) {
		Object myObj = rdf.get(key);
		if (myObj instanceof java.util.HashSet) {
			HashSet<Map<String, String>> all =
					(HashSet<Map<String, String>>) rdf.get(key);
			if (all == null)
				return;
			Iterator<Map<String, String>> fit = all.iterator();
			while (fit.hasNext()) {
				Map<String, String> m = fit.next();
				m.put(PREF_LABEL, m.get(ID2));
			}
		} else if (myObj instanceof java.util.List) {
			List<Map<String, String>> all = (List<Map<String, String>>) rdf.get(key);
			if (all == null)
				return;
			for (Map<String, String> m : all) {
				m.put(PREF_LABEL, m.get(ID2));
			}
		}
	}

	private static void postProcessSubjectName(Map<String, Object> rdf) {
		List<Map<String, Object>> newSubjects = new ArrayList<>();
		Set<String> subjects = (Set<String>) rdf.get("subjectName");
		if (subjects == null || subjects.isEmpty()) {
			return;
		}
		subjects.forEach((subject) -> {
			String id = Globals.protocol + Globals.server + "/adhoc/uri/"
					+ helper.MyURLEncoding.encode(subject);
			Map<String, Object> subjectMap = new HashMap<>();
			subjectMap.put(PREF_LABEL, subject);
			subjectMap.put(ID2, id);
			newSubjects.add(subjectMap);
		});
		rdf.remove("subjectName");
		List<Map<String, Object>> oldSubjects =
				(List<Map<String, Object>>) rdf.get("subject");
		if (oldSubjects == null) {
			oldSubjects = new ArrayList<>();
		}
		oldSubjects.addAll(newSubjects);
		rdf.put("subject", oldSubjects);
	}

	private static void createJoinedFunding(Map<String, Object> rdf) {

		Object myObj = rdf.get("fundingId");
		if (myObj instanceof java.util.HashSet) {
			HashSet<Map<String, Object>> fundingId =
					(HashSet<Map<String, Object>>) rdf.get("fundingId");
			if (fundingId == null) {
				fundingId = new HashSet<>();
			}
			List<String> fundings = (List<String>) rdf.get("funding");
			if (fundings != null) {
				for (String funding : fundings) {
					Map<String, Object> fundingJoinedMap = new LinkedHashMap<>();
					fundingJoinedMap.put(ID2, Globals.protocol + Globals.server
							+ "/adhoc/uri/" + helper.MyURLEncoding.encode(funding));
					fundingJoinedMap.put(PREF_LABEL, funding);
					fundingId.add(fundingJoinedMap);
				}
				rdf.remove("funding");
			}
			List<String> fundingProgram = (List<String>) rdf.get("fundingProgram");
			List<String> projectId = (List<String>) rdf.get("projectId");

			List<Map<String, Object>> joinedFundings = new ArrayList<>();
			if (fundingId.isEmpty())
				return;

			Iterator<Map<String, Object>> fit = fundingId.iterator();
			int i = 0;
			while (fit.hasNext()) {
				Map<String, Object> m = fit.next();
				Map<String, Object> f = new LinkedHashMap<>();
				Map<String, Object> fundingJoinedMap = new LinkedHashMap<>();
				fundingJoinedMap.put(ID2, m.get(ID2));
				fundingJoinedMap.put(PREF_LABEL, m.get(PREF_LABEL));
				f.put("fundingJoined", fundingJoinedMap);
				f.put("fundingProgramJoined", fundingProgram.get(i));
				f.put("projectIdJoined", projectId.get(i));
				joinedFundings.add(f);
				i++;
			}

			rdf.put("joinedFunding", joinedFundings);
			rdf.put("fundingId", fundingId);
		} else if (myObj instanceof java.util.List) {
			List<Map<String, Object>> fundingId =
					(List<Map<String, Object>>) rdf.get("fundingId");
			if (fundingId == null) {
				fundingId = new ArrayList<>();
			}
			List<String> fundings = (List<String>) rdf.get("funding");
			if (fundings != null) {
				for (String funding : fundings) {
					Map<String, Object> fundingJoinedMap = new LinkedHashMap<>();
					fundingJoinedMap.put(ID2, Globals.protocol + Globals.server
							+ "/adhoc/uri/" + helper.MyURLEncoding.encode(funding));
					fundingJoinedMap.put(PREF_LABEL, funding);
					fundingId.add(fundingJoinedMap);
				}
				rdf.remove("funding");
			}
			List<String> fundingProgram = (List<String>) rdf.get("fundingProgram");
			/*
			 * in case of casting problems: List<String> fundingProgram = new
			 * ArrayList<String>((java.util.HashSet) rdf.get("fundingProgram"));
			 */
			List<String> projectId = (List<String>) rdf.get("projectId");

			List<Map<String, Object>> joinedFundings = new ArrayList<>();
			if (fundingId.isEmpty())
				return;
			for (int i = 0; i < fundingId.size(); i++) {
				// play.Logger.info(fundingId.get(i));
				Map<String, Object> f = new LinkedHashMap<>();
				Map<String, Object> fundingJoinedMap = new LinkedHashMap<>();
				fundingJoinedMap.put(ID2, fundingId.get(i).get(ID2));
				fundingJoinedMap.put(PREF_LABEL, fundingId.get(i).get(PREF_LABEL));
				f.put("fundingJoined", fundingJoinedMap);
				f.put("fundingProgramJoined", fundingProgram.get(i));
				f.put("projectIdJoined", projectId.get(i));
				joinedFundings.add(f);
			}
			rdf.put("joinedFunding", joinedFundings);
			rdf.put("fundingId", fundingId);
		}

	}

	private static void addParts(Map<String, Object> rdf) {
		Read read = new Read();
		List<Map<String, Object>> children = new ArrayList<>();
		Object myObj = rdf.get("hasPart");
		if (myObj instanceof java.util.HashSet) {
			HashSet<Map<String, Object>> all =
					(HashSet<Map<String, Object>>) rdf.get("hasPart");
			if (all == null)
				return;
			Iterator<Map<String, Object>> fit = all.iterator();
			while (fit.hasNext()) {
				Map<String, Object> m = fit.next();
				String id = (String) m.get(ID2);
				Node cn = read.internalReadNode(id);
				if (!"D".equals(cn.getState())) {
					children.add(new JsonMapper(cn).getLd2WithoutContext());
				}
			}
		} else if (myObj instanceof java.util.List) {
			List<Map<String, Object>> all =
					(List<Map<String, Object>>) rdf.get("hasPart");
			if (all == null)
				return;
			for (Map<String, Object> part : all) {
				String id = (String) part.get(ID2);
				Node cn = read.internalReadNode(id);
				if (!"D".equals(cn.getState())) {
					children.add(new JsonMapper(cn).getLd2WithoutContext());
				}
			}
		}
		if (!children.isEmpty()) {
			rdf.put("hasPart", children);
		}
	}

	private void postProcessContribution(Map<String, Object> rdf) {
		try {
			List<Map<String, Object>> creator = new ArrayList<>();
			Collection<Map<String, Object>> contributions =
					(Collection<Map<String, Object>>) rdf.get("contribution");
			if (contributions == null)
				return;
			for (Map<String, Object> contribution : contributions) {
				Map<String, Object> agent =
						((Collection<Map<String, Object>>) contribution.get("agent"))
								.iterator().next();
				if (agent != null) {
					String prefLabel = findLabel(agent);
					agent.put(PREF_LABEL, prefLabel);
					String id = null;
					if (agent.containsKey(ID2)) {
						id = agent.get(ID2).toString();
					}
					if (id == null) {
						play.Logger.trace("agent prefLabel=" + prefLabel);
						id = Globals.protocol + Globals.server + "/adhoc/author/"
								+ prefLabel;
					}
					Map<String, Object> cmap = new HashMap<>();
					cmap.put(PREF_LABEL, prefLabel);
					cmap.put(ID2, id);
					creator.add(cmap);
				}
			}
			rdf.put("creator", creator);
		} catch (Exception e) {
			play.Logger.debug("Problem processing key contribution.agent", e);
			// play.Logger.debug("", e);
		}
	}

	private static void postProcess(Map<String, Object> m, String field) {
		try {
			Collection<Map<String, Object>> fields =
					(Collection<Map<String, Object>>) m.get(field);
			if (fields != null) {
				play.Logger.trace("Found roles: " + fields);
				for (Map<String, Object> r : fields) {
					String prefLabel = findLabel(r);
					play.Logger.trace("Found label " + prefLabel + " for role " + r);
					r.put(PREF_LABEL, prefLabel);
				}
			}
		} catch (Exception e) {
			play.Logger.debug("Problem processing key " + field, e);
		}
	}

	private static void addCatalogLink(Map<String, Object> rdf) {
		try {
			String hbzId = ((Collection<String>) rdf.get("hbzId")).iterator().next();
			Collection<Map<String, Object>> catalogLink = new ArrayList<>();
			Map<String, Object> cl = new HashMap<>();
			cl.put(ID2, "https://lobid.org/resources/" + hbzId);
			cl.put(PREF_LABEL, hbzId);
			catalogLink.add(cl);
			rdf.put("catalogLink", catalogLink);
		} catch (Exception e) {
			play.Logger.trace("No catalog link available!");
		}
	}

	private void sortCreatorAndContributors(Map<String, Object> rdf) {
		try {
			Collection<Map<String, Object>> cr = getSortedListOfCreators(rdf);
			if (!cr.isEmpty()) {
				rdf.put("creator", cr);
				rdf.remove("creatorName");
				rdf.remove("contributorName");
			}
			Collection<Map<String, Object>> co = getSortedListOfContributors(rdf);
			if (!co.isEmpty()) {
				rdf.put("contributor", co);
			}
		} catch (Exception e) {
			play.Logger.debug("", e);
		}
	}

	private void addLinkToJsonMap(Map<String, Object> rdf, Link l) {
		Map<String, Object> resolvedObject = null;
		String id = l.getObject();
		String value = l.getObjectLabel();
		String jsonName = getJsonName(l.getPredicate());
		if (l.getObjectLabel() != null) {
			resolvedObject = new HashMap<>();
			resolvedObject.put(ID2, id);
			resolvedObject.put(PREF_LABEL, value);
		}
		if (jsonName != null && rdf.containsKey(jsonName)) {
			Collection<Object> list =
					(Collection<Object>) rdf.get(getJsonName(l.getPredicate()));
			if (resolvedObject == null) {
				if (l.isLiteral()) {
					list.add(l.getObject());
				} else {
					resolvedObject = new HashMap<>();
					resolvedObject.put(ID2, id);
					resolvedObject.put(PREF_LABEL, id);
					list.add(resolvedObject);
				}
			} else {
				list.add(resolvedObject);
			}
		} else {
			Collection<Object> list = new ArrayList<>();
			if (resolvedObject == null) {
				if (l.isLiteral()) {
					list.add(l.getObject());
				} else {
					resolvedObject = new HashMap<>();
					resolvedObject.put(ID2, id);
					resolvedObject.put(PREF_LABEL, id);
					list.add(resolvedObject);
				}
			} else {
				list.add(resolvedObject);
			}
			rdf.put(getJsonName(l.getPredicate()), list);
		}
	}

	private void addPartsToJsonMap(Map<String, Object> rdf) {
		for (Link l : node.getPartsSorted()) {
			if (l.getObjectLabel() == null || l.getObjectLabel().isEmpty())
				l.setObjectLabel(l.getObject());
			addLinkToJsonMap(rdf, l);
		}
	}

	Collection<Map<String, Object>> getSortedListOfCreators(
			Map<String, Object> nodeAsMap) {
		Collection<Map<String, Object>> result = new ArrayList<>();
		Collection<String> carray =
				(Collection<String>) nodeAsMap.get("contributorOrder");
		if (carray == null || carray.isEmpty())
			return result;
		for (String cstr : carray) {
			String[] contributorOrdered =
					cstr.contains("|") ? cstr.split("\\|") : new String[] { cstr };
			for (String s : contributorOrdered) {
				Map<String, Object> map = findCreator(nodeAsMap, s.trim());
				if (!map.isEmpty())
					result.add(map);
			}
		}
		return result;
	}

	Collection<Map<String, Object>> getSortedListOfContributors(
			Map<String, Object> nodeAsMap) {
		Collection<Map<String, Object>> result = new ArrayList<>();
		Collection<String> carray =
				(Collection<String>) nodeAsMap.get("contributorOrder");
		if (carray == null || carray.isEmpty())
			return result;
		for (String cstr : carray) {
			String[] contributorOrdered =
					cstr.contains("|") ? cstr.split("\\|") : new String[] { cstr };
			for (String s : contributorOrdered) {
				Map<String, Object> map = findContributor(nodeAsMap, s.trim());
				if (!map.isEmpty())
					result.add(map);
			}
		}
		return result;
	}

	private static Map<String, Object> findCreator(Map<String, Object> m,
			String authorsId) {
		if (!authorsId.startsWith("http")) {
			Map<String, Object> creatorWithoutId = new HashMap<>();
			creatorWithoutId.put(PREF_LABEL, authorsId);
			creatorWithoutId.put(ID2, Globals.protocol + Globals.server + "/authors/"
					+ RdfUtils.urlEncode(authorsId).replace("+", "%20"));
			return creatorWithoutId;
		}

		if (m.get("creator") instanceof List
				&& ((List) m.get("creator")).get(0) instanceof String) {
			Collection<String> creators = (Collection<String>) m.get("creator");
			for (String creator : creators) {
				String currentId = creator;
				play.Logger.trace(creator + " - " + currentId + " - " + authorsId);
				if (authorsId.compareTo(currentId) == 0) {
					Map<String, Object> result = new HashMap<>();
					result.put(ID2, currentId);
					return result;
				}
			}
		} else {
			Collection<Map<String, Object>> creators =
					(Collection<Map<String, Object>>) m.get("creator");
			if (creators != null) {
				for (Map<String, Object> creator : creators) {
					String currentId = (String) creator.get(ID2);
					play.Logger.trace(creator + " " + currentId + " " + authorsId);
					if (authorsId.compareTo(currentId) == 0) {
						return creator;
					}
				}
			}
		}
		return new HashMap<>();
	}

	private static Map<String, Object> findContributor(Map<String, Object> m,
			String authorsId) {
		if (m.get("contributor") instanceof List) {
			play.Logger.debug("Casting m.get(\"contributor\") to Collection<String>");
			Collection<String> creators = (Collection<String>) m.get("contributor");
			play.Logger.trace("" + creators.getClass());
			for (String creator : creators) {
				String currentId = creator;
				play.Logger.trace(creator + " " + currentId + " " + authorsId);
				if (authorsId.compareTo(currentId) == 0) {
					Map<String, Object> result = new HashMap<>();
					result.put(ID2, currentId);
					return result;
				}
			}
		} else {
			Collection<Map<String, Object>> creators =
					(Collection<Map<String, Object>>) m.get("contributor");
			if (creators != null) {
				for (Map<String, Object> creator : creators) {
					String currentId = (String) creator.get(ID2);
					play.Logger.debug(creator + " " + currentId + " " + authorsId);
					if (authorsId.compareTo(currentId) == 0) {
						return creator;
					}
				}
			}
		}
		return new HashMap<>();
	}

	private static String findLabel(Map<String, Object> map) {

		if (map.containsKey("preferredNameForTheWork"))
			return (String) map.get("preferredNameForTheWork");

		if (map.containsKey("preferredNameForThePerson"))
			return (String) map.get("preferredNameForThePerson");

		if (map.containsKey("preferredNameForTheCorporateBody"))
			return (String) map.get("preferredNameForTheCorporateBody");

		if (map.containsKey("preferredNameForThePlaceOrGeographicName"))
			return (String) map.get("preferredNameForThePlaceOrGeographicName");

		if (map.containsKey("preferredName"))
			return (String) map.get("preferredName");

		if (map.containsKey("preferredNameForTheSubjectHeading"))
			return (String) map.get("preferredNameForTheSubjectHeading");

		if (map.containsKey("alternateName_de")) {
			return (String) map.get("alternateName_de");
		} else if (map.containsKey("alternateName_en")) {
			return (String) map.get("alternateName_en");
		}
		if (map.containsKey("label"))
			return (String) map.get("label");

		if (map.containsKey(PREF_LABEL))
			return (String) map.get(PREF_LABEL);

		return null;
	}

	private String getUriFromJsonName(String n) {
		return profile.getEtikettByName(n).getUri();
	}

	private String getJsonName(String uri) {
		String result = profile.getEtikett(uri).getName();

		if (result == null) {
			play.Logger
					.warn("No json name for " + uri + ". Please fix your labels.json");
			result = uri;
		}

		return result;
	}

	public Map<String, Object> getLd2WithParts() {
		Map<String, Object> rdf = getLd2();
		addParts(rdf);
		return rdf;
	}

	/**
	 * Holt Metadaten im Format lobid2 (falls vorhanden) und reichert sie an mit
	 * Informationen aus dem Node
	 * 
	 * @return
	 */
	public Map<String, Object> getLd2() {
		Collection<Link> ls = node.getRelsExt();
		Map<String, Object> m = getDescriptiveMetadata2();
		Map<String, Object> rdf = m == null ? new HashMap<>() : m;

		changeDcIsPartOfToRegalIsPartOf(rdf);
		// rdf.remove("describedby");
		// rdf.remove("sameAs");

		rdf.put(ID2, node.getPid());
		rdf.put(primaryTopic, node.getPid());
		for (Link l : ls) {
			if (HAS_PART.equals(l.getPredicate()))
				continue;
			if (REL_HBZ_ID.equals(l.getPredicate()))
				continue;
			if (IS_PART_OF.equals(l.getPredicate()))
				continue;
			addLinkToJsonMap(rdf, l);
		}
		addPartsToJsonMap(rdf);
		rdf.remove("isNodeType");

		rdf.put(contentType, node.getContentType());
		rdf.put(accessScheme, node.getAccessScheme());
		rdf.put(publishScheme, node.getPublishScheme());
		rdf.put(transformer, node.getTransformer().stream().map(t -> t.getId())
				.collect(Collectors.toList()));
		rdf.put(catalogId, node.getCatalogId());
		// rdf.put(embargoTime, node.getEmbargoTime());

		if (node.getFulltext() != null)
			rdf.put(fulltext_ocr, node.getFulltext());

		Map<String, Object> aboutMap = new TreeMap<>();
		aboutMap.put(ID2, node.getAggregationUri() + ".rdf");
		if (node.getCreatedBy() != null)
			aboutMap.put(createdBy, node.getCreatedBy());
		if (node.getLegacyId() != null)
			aboutMap.put(legacyId, node.getLegacyId());
		if (node.getImportedFrom() != null)
			aboutMap.put(importedFrom, node.getImportedFrom());
		if (node.getName() != null)
			aboutMap.put(name, node.getName());
		if (node.getLastModifiedBy() != null)
			aboutMap.put(lastModifiedBy, node.getLastModifiedBy());

		try {
			aboutMap.put(modified, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
					.format(node.getLastModified()));
		} catch (Exception e) {
			aboutMap.put(modified, node.getLastModified());
		}
		if (node.getObjectTimestamp() != null) {
			aboutMap.put(objectTimestamp, node.getObjectTimestamp());
		} else {
			aboutMap.put(objectTimestamp, node.getLastModified());
		}
		try {
			aboutMap.put(created, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
					.format(node.getCreationDate()));
		} catch (Exception e) {
			aboutMap.put(created, node.getCreationDate());
		}
		aboutMap.put(describes, node.getAggregationUri());

		rdf.put(isDescribedBy, aboutMap);
		if (node.getDoi() != null) {
			rdf.put(doi, node.getDoi());
		}
		if (node.getUrn() != null) {
			rdf.put(urn, node.getUrn());
		}

		if (node.getParentPid() != null)
			rdf.put(parentPid, node.getParentPid());

		if (node.getMimeType() != null && !node.getMimeType().isEmpty()) {
			Map<String, Object> hasDataMap = new TreeMap<>();
			hasDataMap.put(ID2, node.getDataUri());
			hasDataMap.put(format, node.getMimeType());
			hasDataMap.put(size, node.getFileSize());
			if (node.getFileLabel() != null)
				hasDataMap.put(fileLabel, node.getFileLabel());
			if (node.getChecksum() != null) {
				Map<String, Object> checksumMap = new TreeMap<>();
				checksumMap.put(checksumValue, node.getChecksum());
				checksumMap.put(generator, "http://en.wikipedia.org/wiki/MD5");
				checksumMap.put(rdftype,
						"http://downlode.org/Code/RDF/File_Properties/schema#Checksum");
				hasDataMap.put(checksum, checksumMap);
			}
			rdf.put(hasData, hasDataMap);
		}
		ObjectMapper mapper = new ObjectMapper();

		String issued = getPublicationMap(mapper.convertValue(rdf, JsonNode.class));

		if (issued != null) {
			rdf.put("issued", issued);
		}
		rdf.put("@context", Globals.protocol + Globals.server + "/context.json");
		postprocessing(rdf);
		return rdf;
	}

	public static String getPublicationMap(JsonNode jsNode) {

		if (jsNode.has("issued") && !jsNode.get("issued").toString().isEmpty()) {
			String issued = jsNode.get("issued").toString();
			play.Logger.debug("issued =" + issued);
			return issued.substring(issued.toString().indexOf("\"") + 1,
					issued.toString().lastIndexOf("\""));

		} else if (jsNode.has("publicationYear")
				&& !jsNode.get("publicationYear").toString().isEmpty()) {
			String publicationYear =
					jsNode.get("publicationYear").toString().substring(2, 6);
			play.Logger.debug("publicationYear =" + publicationYear);

			if (new JsonMapperHelper().isDateValid(publicationYear)) {
				return publicationYear;
			}

		}
		return null;

	}

	private static Collection<Map<String, Object>> getType(final JsonNode rdf) {
		Collection<Map<String, Object>> result = new ArrayList<>();

		// Special case medium is video - override type
		if (mediumArrayContains(rdf,
				"http://rdaregistry.info/termList/RDAMediaType/1008")
				|| mediumArrayContains(rdf,
						"http://rdvocab.info/termList/RDACarrierType/1050")) {
			String s = "http://rdaregistry.info/termList/RDAMediaType/1008";
			Map<String, Object> tmap = new HashMap<>();
			tmap.put(PREF_LABEL, Globals.profile.getEtikett(s).getLabel());
			tmap.put(ID2, s);
			result.add(tmap);

		}
		JsonNode types = rdf.at("/rdftype");
		types.forEach(t -> {
			String typeId = t.at("/" + ID2).asText();
			if (!"http://purl.org/dc/terms/BibliographicResource".equals(typeId)) {
				Map<String, Object> tmap = new HashMap<>();
				tmap.put(PREF_LABEL, Globals.profile.getEtikett(typeId).getLabel());
				tmap.put(ID2, typeId);
				result.add(tmap);
			}
		});
		return result;
	}

	private static boolean mediumArrayContains(JsonNode rdf, String key) {
		boolean result = false;
		JsonNode mediumArray = rdf.at("/medium");
		for (JsonNode item : mediumArray) {
			if (key.equals(item.at("/" + ID2).asText("no Value found")))
				result = true;
		}
		return result;
	}

}
