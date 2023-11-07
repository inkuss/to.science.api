/*
 * Copyright 2023 by hbz NRW (http://www.hbz-nrw.de/)
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

import static archive.fedora.FedoraVocabulary.IS_PART_OF;
import static archive.fedora.Vocabulary.REL_HBZ_ID;

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
import java.util.Locale;
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

import helper.JsonMapper;
import actions.Read;
import archive.fedora.RdfUtils;
import de.hbz.lobid.helper.EtikettMakerInterface;
import de.hbz.lobid.helper.JsonConverter;
import models.Globals;
import models.Link;
import models.Node;
import play.Play;

/**
 * 
 * @author Dr. Ingolf Kuss, hbz
 *
 */

@SuppressWarnings("unchecked")
/**
 * Diese Klasse macht die Abbildung von KTBL nach Metadata2
 */
public class KtblMapper {

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
	public KtblMapper() {
		play.Logger.info("Creating new instance of Class KtblMapper");
		jsonConverter = new JsonConverter(profile);
	}

	/**
	 * Ein Konstruktor für diese Klasse, falls Metadata2-Datenstrom schon
	 * vorhanden sein muss.
	 * 
	 * @param n the node will be mapped to json ld in accordance to the profile
	 */
	public KtblMapper(final Node n) {
		try {
			jsonConverter = new JsonConverter(profile);
			this.node = n;
			if (node == null)
				throw new NullPointerException(
						"JsonMapper can not work on node with value NULL!");
			// if (node.getMetadata1() == null)
			// throw new NullPointerException(
			// node.getPid() + " metadata stream is NULL!");
			if (node.getMetadata2() == null)
				throw new NullPointerException(
						node.getPid() + " metadata2 stream is NULL!");
		} catch (Exception e) {
			play.Logger.warn("", e.getMessage());
			// play.Logger.debug("", e);
		}

	}

	/**
	 * Holt Metadaten im Format lobid2 (falls vorhanden) und bildet Felder der
	 * KTBL-Daten darauf ab.
	 * 
	 * @author Ingolf Kuss, hbz
	 * @param n The Node of the resource
	 * @param content Die KTBL-Daten im Format JSON
	 * @date 2023-10-25
	 * 
	 * @return Die Daten im Format lobid2-RDF
	 */
	public Map<String, Object> getLd2LobidifyKtbl(Node n, String content) {
		/* Mapping von LRMI.json nach lobid2.json */
		this.node = n;
		try {
			// Neues JSON-Objekt anlegen; für lobid2-Daten
			Map<String, Object> rdf = node.getLd2();

			// LRMIDaten nach JSONObject wandeln
			JSONObject jcontent = new JSONObject(content);
			play.Logger.debug("Start mapping of KTBL to lobid2");
			JSONArray arr = null;
			JSONObject obj = null;
			Object myObj = null; /* Objekt von zunächst unbekanntem Typ/Klasse */
			String prefLabel = null;

			if (jcontent.has("@context")) {
				arr = jcontent.getJSONArray("@context");
				play.Logger.debug("Found context: " + arr.getString(0));
				/* das geht nicht; @context wird in lobid2 automatisch erstellt */
				/* kann man nicht mappen; soll auch nicht gemappt werden! */
				rdf.put("@context", arr.getString(0));
				obj = arr.getJSONObject(1);
				String language = obj.getString("@language");
				play.Logger.debug("Found language: " + language);
				/*
				 * So etwas anlegen:
				 * "language":[{"@id":"http://id.loc.gov/vocabulary/iso639-2/deu",
				 * "label": "Deutsch","prefLabel":"Deutsch"}]
				 */
				// eine Struktur {} anlegen:
				Map<String, Object> languageMap = new TreeMap<>();
				if (language != null && !language.trim().isEmpty()) {
					// fix the wrong language tag provided by lrmi
					if (language.equals("de")) {
						language = "ger";
					}
					if (language.length() == 2) {
						Locale loc = Locale.forLanguageTag(language);
						languageMap.put("@id", "http://id.loc.gov/vocabulary/iso639-2/"
								+ loc.getISO3Language());
					} else if (language.length() == 3) {
						// vermutlich ISO639-2
						languageMap.put("@id",
								"http://id.loc.gov/vocabulary/iso639-2/" + language);
					} else {
						play.Logger.warn(
								"Unbekanntes Vokabluar für Sprachencode! Code=" + language);
					}
				}
				// languageMap.put("label", "Deutsch");
				// languageMap.put("prefLabel", "Deutsch");
				List<Map<String, Object>> languages = new ArrayList<>();
				languages.add(languageMap);
				rdf.put("language", languages);
			}

			rdf.put(accessScheme, "public");
			rdf.put(publishScheme, "public");

			if (jcontent.has("type")) {
				arr = jcontent.getJSONArray("type");
				rdf.put("contentType", arr.getString(0));
			}

			if (jcontent.has(name)) {
				List<String> names = new ArrayList<>();
				names.add(jcontent.getString(name));
				rdf.put("title", names);
			}

			if (jcontent.has("inLanguage")) {
				List<Map<String, Object>> inLangList = new ArrayList<>();
				String inLang = null;
				arr = jcontent.getJSONArray("inLanguage");
				for (int i = 0; i < arr.length(); i++) {
					Map<String, Object> inLangMap = new TreeMap<>();
					inLang = arr.getString(i);
					Locale loc = Locale.forLanguageTag(inLang);
					inLangMap.put("@id",
							"http://id.loc.gov/vocabulary/iso639-2/" + loc.getISO3Language());
					String langPrefLabel = inLang;
					if (loc.getDisplayLanguage() != null) {
						langPrefLabel = loc.getDisplayLanguage();
					}
					inLangMap.put("prefLabel", langPrefLabel);
					inLangList.add(inLangMap);
				}
				rdf.put("language", inLangList);
			}

			if (jcontent.has("learningResourceType")) {
				List<Map<String, Object>> media = new ArrayList<>();
				arr = jcontent.getJSONArray("learningResourceType");
				for (int i = 0; i < arr.length(); i++) {
					obj = arr.getJSONObject(i);
					Map<String, Object> mediumMap = new TreeMap<>();
					if (obj.has("prefLabel")) {
						JSONObject subObj = obj.getJSONObject("prefLabel");
						prefLabel = subObj.getString("de");
						mediumMap.put("prefLabel", prefLabel);
						play.Logger.debug("learningResourceType: prefLabel: " + prefLabel);
					}
					if (obj.has("id")) {
						mediumMap.put("@id", obj.getString("id"));
					} else {
						// Dieser Fall sollte nicht vorkommen
						play.Logger
								.warn("Achtung! learningResourceType (Medium) hat keine ID !");
					}
					media.add(mediumMap);
				}
				rdf.put("medium", media);
			}

			String creatorName = null;
			String affiliationId = null;
			String affiliationType = null;
			if (jcontent.has("creator")) {
				List<Map<String, Object>> creators = new ArrayList<>();
				arr = jcontent.getJSONArray("creator");
				for (int i = 0; i < arr.length(); i++) {
					obj = arr.getJSONObject(i);
					Map<String, Object> creatorMap = new TreeMap<>();
					creatorName = new String(obj.getString(name));
					creatorMap.put("prefLabel", creatorName);
					if (obj.has("id")) {
						creatorMap.put("@id", obj.getString("id"));
					} else {
						/*
						 * Dieser Fall sollte nicht vorkommen, da die KTBL-Daten vorher
						 * angreichert (enriched) werden, bevor sie auf die Metadata2-Felder
						 * abgebildet werden. Das passiert in Enrich.enrichLrmiData(). Falls
						 * man doch hier hin kommt, gibt es eine Warnung:
						 */
						play.Logger.warn(
								"Achtung! Un-angereicherte LMRI-Daten werden nach metadata2 gemappt!!");
						play.Logger.warn(
								"Dem Creator \"" + creatorName + "\" fehlt eine URI/id !");
					}

					if (obj.has("honoricPrefix")) {
						creatorMap.put("academicTitle", obj.getString("honoricPrefix"));
					}
					if (obj.has("affiliation")) {
						JSONObject obj2 = obj.getJSONObject("affiliation");
						affiliationId = new String(obj2.getString("id"));
						affiliationType = new String(obj2.getString("type"));

						Map<String, Object> affiliationMap = new TreeMap<>();
						affiliationMap.put("@id", affiliationId);
						affiliationMap.put("type", affiliationType);
						creatorMap.put("affiliation", affiliationMap);
					}

					creators.add(creatorMap);
				}
				rdf.put("creator", creators);
			}

			if (jcontent.has("contributor")) {
				List<Map<String, Object>> contributors = new ArrayList<>();
				arr = jcontent.getJSONArray("contributor");
				for (int i = 0; i < arr.length(); i++) {
					obj = arr.getJSONObject(i);
					Map<String, Object> contributorMap = new TreeMap<>();
					contributorMap.put("prefLabel", obj.getString(name));
					if (obj.has("id")) {
						contributorMap.put("@id", obj.getString("id"));
					}
					if (obj.has("honoricPrefix")) {
						contributorMap.put("academicTitle", obj.getString("honoricPrefix"));
					}
					if (obj.has("affiliation")) {
						JSONObject obj2 = obj.getJSONObject("affiliation");
						affiliationId = new String(obj2.getString("id"));
						affiliationType = new String(obj2.getString("type"));

						Map<String, Object> affiliationMap = new TreeMap<>();
						affiliationMap.put("@id", affiliationId);
						affiliationMap.put("type", affiliationType);
						contributorMap.put("affiliation", affiliationMap);
					}

					contributors.add(contributorMap);
				}
				rdf.put("contributor", contributors);
			}

			if (jcontent.has("description")) {
				List<String> descriptions = new ArrayList<>();
				myObj = jcontent.get("description");
				if (myObj instanceof java.lang.String) {
					descriptions.add(jcontent.getString("description"));
				} else if (myObj instanceof org.json.JSONArray) {
					arr = jcontent.getJSONArray("description");
					for (int i = 0; i < arr.length(); i++) {
						descriptions.add(arr.getString(i));
					}
				}
				rdf.put("description", descriptions);
			}

			if (jcontent.has("license")) {
				List<Map<String, Object>> licenses = new ArrayList<>();
				myObj = jcontent.get("license");
				Map<String, Object> licenseMap = null;
				if (myObj instanceof java.lang.String) {
					licenseMap = new TreeMap<>();
					licenseMap.put("@id", jcontent.getString("license"));
					licenses.add(licenseMap);
				} else if (myObj instanceof org.json.JSONObject) {
					obj = jcontent.getJSONObject("license");
					licenseMap = new TreeMap<>();
					licenseMap.put("@id", obj.getString("id"));
					licenses.add(licenseMap);
				} else if (myObj instanceof org.json.JSONArray) {
					arr = jcontent.getJSONArray("license");
					for (int i = 0; i < arr.length(); i++) {
						obj = arr.getJSONObject(i);
						licenseMap = new TreeMap<>();
						licenseMap.put("@id", obj.getString("id"));
						licenses.add(licenseMap);
					}
				}
				rdf.put("license", licenses);
			}

			if (jcontent.has("publisher")) {
				List<Map<String, Object>> institutions = new ArrayList<>();
				arr = jcontent.getJSONArray("publisher");
				for (int i = 0; i < arr.length(); i++) {
					obj = arr.getJSONObject(i);
					Map<String, Object> publisherMap = new TreeMap<>();
					publisherMap.put("prefLabel", obj.getString(name));
					publisherMap.put("@id", obj.getString("id"));
					institutions.add(publisherMap);
				}
				rdf.put("institution", institutions);
			}

			if (jcontent.has("keywords")) {
				String keyword = null;
				List<Map<String, Object>> subject = new ArrayList<>();
				arr = jcontent.getJSONArray("keywords");
				for (int i = 0; i < arr.length(); i++) {
					Map<String, Object> keywordMap = new TreeMap<>();
					keyword = arr.getString(i);
					keywordMap.put("prefLabel", keyword);
					subject.add(keywordMap);
				}
				rdf.put("subject", subject);
			}

			JsonMapper jsonMapper = new JsonMapper();
			jsonMapper.postprocessing(rdf);

			play.Logger.debug("Done mapping KTBL data to lobid2.");
			return rdf;
		} catch (Exception e) {
			play.Logger.error("Content could not be mapped!", e);
			throw new RuntimeException("KTBL.json could not be mapped to lobid2.json",
					e);
		}

	}

	/**
	 * Diese Methode modifiziert den LRMI-Datenstrom. Der LRMI-Datenstrom muss im
	 * Format JSON-String übergeben werden. Es werden IDs darin ersetzt. Der
	 * LRMI-Datenstrom wird als JSON-String zurück gegeben.
	 * 
	 * @author Ingolf Kuss, hbz
	 * @param n The Node of the resource
	 * @param content Die LRMI-Daten im Format JSON
	 * @date 2021-08-20
	 * 
	 * @return Die Daten im Format LRMI JSON
	 */
	public String getLobidifyKtbl(Node n, String content) {
		this.node = n;
		play.Logger.debug("Start getTosciencefyLrmi");
		try {
			// LRMI-Daten nach JSONObject wandeln
			JSONObject jcontent = new JSONObject(content);
			JSONArray arr = null;
			JSONObject obj = null;

			// toscience-ID generieren
			toscience_id = new String(
					Globals.protocol + Globals.server + "/resource/" + node.getPid());
			play.Logger.debug("toscience ID for this resource is: " + toscience_id);

			// bisherige Top-Level ID
			String top_level_id = null;
			if (jcontent.has("id")) {
				top_level_id = jcontent.getString("id");
			}
			if (top_level_id == null) {
				play.Logger.debug("Adding new top level id: " + toscience_id);
				jcontent.put("id", toscience_id);
			} else if (!toscience_id.equals(top_level_id)) {
				play.Logger.info("Found top level id: " + top_level_id
						+ ". Replacing by toscience id: " + toscience_id);
				jcontent.put("id", toscience_id);
			}

			// mainEntityOfPage - ID (mit letztem Änderungsdatum)
			if (jcontent.has("mainEntityOfPage")) {
				// ID in "mainEntityOfPage" wird ersetzt
				arr = jcontent.getJSONArray("mainEntityOfPage");
				for (int i = 0; i < arr.length(); i++) {
					obj = arr.getJSONObject(i);
					if (obj.has("id")) {
						play.Logger
								.debug("Bestehende mainEntityOfPage-ID " + obj.getString("id")
										+ " wird ersetzt durch " + toscience_id + ".");
					}
					obj.put("id", toscience_id);
					obj.put("dateModified", LocalDate.now());
				}
			} else {
				// Element "mainEntityOfPage" wird neu angelegt
				Collection<Map<String, Object>> mainEntityOfPage = new ArrayList<>();
				Map<String, Object> mainEntityMap = new TreeMap<>();
				mainEntityMap.put("id", toscience_id);
				mainEntityMap.put("dateCreated", LocalDate.now());
				mainEntityOfPage.add(mainEntityMap);
				jcontent.put("mainEntityOfPage", mainEntityOfPage);
			}

			// Anlagedatum generieren (falls noch nicht vorhanden)
			if (!jcontent.has("dateCreated")) {
				jcontent.put("dateCreated", LocalDate.now());
			}

			// geändertes JSONObject als Zeichenkette zurück geben
			play.Logger.debug("Modified KTBL Data to: " + jcontent.toString());
			return jcontent.toString();

		} catch (JSONException je) {
			play.Logger.error("Content could not be mapped!", je);
			throw new RuntimeException(
					"LRMI.json could not be modified for toscience !", je);
		}
	}

}