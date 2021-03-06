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
package helper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;

import models.DataciteRecord;
import models.Globals;
import models.Pair;

/**
 * @author Jan Schnasse
 *
 */
@SuppressWarnings("unchecked")
public class DataciteMapper {

	/**
	 * @param doi the doi identifier
	 * @param ld the object as json-ld linked data in a map
	 * @return an object containing data for datacite
	 */
	public static DataciteRecord getDataciteRecord(String doi,
			Map<String, Object> ld) {
		DataciteRecord rec = new DataciteRecord(doi);
		addSubjects(ld, rec);
		addCreators(ld, rec);
		addTitles(ld, rec);
		addPublisher(ld, rec);
		addPublicationYear(ld, rec);
		addLanguage(ld, rec);
		addDates(ld, rec);
		addAlternativeIdentifiers(ld, rec);
		addSizes(ld, rec);// http://purl.org/dc/terms/bibliographicCitation
		addFormats(ld, rec);// mime
		addResourceType(ld, rec);// bibliographic resource
		return rec;
	}

	private static void addResourceType(Map<String, Object> ld,
			DataciteRecord rec) {
		rec.type = "Text";
	}

	private static void addFormats(Map<String, Object> ld, DataciteRecord rec) {
		try {
			rec.formats.add(new Pair<String, String>("application/pdf", "mime"));
		} catch (NullPointerException e) {
			play.Logger.debug("DataciteMapper: Metadatafield 'Format' not found!");
		}
	}

	private static void addSizes(Map<String, Object> ld, DataciteRecord rec) {
		try {
			String bibDetails =
					(String) ((HashSet<String>) ld.get("bibliographicCitation"))
							.iterator().next();
			if (bibDetails != null) {
				rec.sizes.add(new Pair<String, String>(bibDetails, null));
			}
		} catch (NullPointerException e) {
			play.Logger.debug("DataciteMapper: Metadatafield 'Sizes' not found!");
		}
	}

	private static void addAlternativeIdentifiers(Map<String, Object> ld,
			DataciteRecord rec) {
		try {
			// Lobid
			String lobidUrl =
					(String) ((HashSet<Map<String, Object>>) ld.get("parallelEdition"))
							.iterator().next().get("prefLabel");
			if (lobidUrl != null) {
				// HT
				String ht = lobidUrl.substring(lobidUrl.lastIndexOf("/") + 1);
				// HBZ-HT
				String hbzht = "HBZ" + ht;
				rec.alternateIdentifiers
						.add(new Pair<String, String>(ht, "hbzAleph-IDN"));
				rec.alternateIdentifiers
						.add(new Pair<String, String>(hbzht, "Consortium-IDN"));
				rec.alternateIdentifiers
						.add(new Pair<String, String>(lobidUrl, "LOD-Catalog"));
			}
			// URN
			Collection<String> urns = (Collection<String>) ld.get("urn");
			if (urns != null) {
				for (String urn : urns) {
					rec.alternateIdentifiers.add(new Pair<String, String>(urn, "URN"));
				}
			}
			// URL
			String pid = (String) ld.get("@id");
			String url = Globals.urnbase + pid;
			rec.alternateIdentifiers.add(new Pair<String, String>(url, "URL"));
		} catch (NullPointerException e) {
			play.Logger.debug("", e);
		}
	}

	private static void addDates(Map<String, Object> ld, DataciteRecord rec) {
		try {

			Date creationDate =
					(Date) ((Map<String, Object>) ld.get("isDescribedBy")).get("created");
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			rec.dates.add(new Pair<String, String>(dateFormat.format(creationDate),
					"Submitted"));
		} catch (NullPointerException e) {
			play.Logger.debug("DataciteMapper: Metadatafield 'Submitted' not found!");
		}
	}

	private static void addLanguage(Map<String, Object> ld, DataciteRecord rec) {
		try {
			Collection<Map<String, Object>> languages =
					(Collection<Map<String, Object>>) ld.get("language");
			if (languages != null) {
				for (Map<String, Object> item : languages) {
					String langid = (String) item.get("@id");
					rec.language = langid.substring(langid.length() - 3);
				}
			}
		} catch (NullPointerException e) {
			play.Logger.debug("DataciteMapper: Metadatafield 'Language' not found!");
		}
	}

	private static void addPublicationYear(Map<String, Object> ld,
			DataciteRecord rec) {
		try {
			rec.publicationYear =
					((HashSet<String>) ld.get("issued")).iterator().next();
		} catch (NullPointerException e) {
			play.Logger
					.debug("DataciteMapper: Metadatafield 'PublicationYear' not found!");
		}
		if (rec.publicationYear == null || rec.publicationYear.isEmpty()) {
			rec.publicationYear = getCurrentYear();
		}
	}

	public static String getCurrentYear() {
		return java.time.Year.now().toString();
	}

	private static void addPublisher(Map<String, Object> ld, DataciteRecord rec) {
		try {
			play.Logger.debug(ld.get("publisher").getClass() + "");
			play.Logger.debug(ld.get("publisher") + "");
			rec.publisher = ((HashSet<String>) ld.get("publisher")).iterator().next();

		} catch (NullPointerException e) {
			play.Logger.debug("DataciteMapper: Metadatafield 'Publisher' not found!");
		}
		try {
			if (rec.publisher == null || rec.publisher.isEmpty()) {
				rec.publisher = ((HashSet<String>) ld.get("P60489")).iterator().next();
			}
		} catch (NullPointerException e) {

			play.Logger.debug(
					"DataciteMapper: Metadatafield 'thesisInformation' not found!");
		}

		if (rec.publisher == null || rec.publisher.isEmpty()) {
			rec.publisher = "PUBLISSO";
		}

	}

	private static void addTitles(Map<String, Object> ld, DataciteRecord rec) {
		try {
			Collection<String> list = (Collection<String>) ld.get("title");
			if (list != null) {
				for (String item : list) {
					rec.titles.add(new Pair<String, String>(item, ""));
				}
			}
			list = (Collection<String>) ld.get("alternativeTitle");
			if (list != null) {
				for (String item : list) {
					rec.titles.add(new Pair<String, String>(item, "alternativeTitle"));
				}
			}
			list = (Collection<String>) ld.get("otherTitleInformation");
			if (list != null) {
				for (String item : list) {
					rec.titles
							.add(new Pair<String, String>(item, "otherTitleInformation"));
				}
			}
			list = (Collection<String>) ld.get("fullTitle");
			if (list != null) {
				for (String item : list) {
					rec.titles.add(new Pair<String, String>(item, "fullTitle"));
				}
			}
			list = (Collection<String>) ld.get("shortTitle");
			if (list != null) {
				for (String item : list) {
					rec.titles.add(new Pair<String, String>(item, "shortTitle"));
				}
			}
		} catch (NullPointerException e) {
			play.Logger.debug("", e);
		}
	}

	private static void addCreators(Map<String, Object> ld, DataciteRecord rec) {
		try {
			Collection<Map<String, Object>> creators =
					(Collection<Map<String, Object>>) ld.get("creator");
			if (creators != null) {
				for (Map<String, Object> item : creators) {
					String subject = (String) item.get("prefLabel");
					rec.creators.add(new Pair<String, String>(subject, null));
				}
			}
			Collection<String> creatorNames =
					(Collection<String>) ld.get("creatorName");
			if (creatorNames != null) {
				for (String item : creatorNames) {
					rec.creators.add(new Pair<String, String>(item, null));
				}
			}

			Collection<Map<String, Object>> contributors =
					(Collection<Map<String, Object>>) ld.get("contributor");
			if (contributors != null) {
				for (Map<String, Object> item : contributors) {
					String subject = (String) item.get("prefLabel");
					rec.creators.add(new Pair<String, String>(subject, null));
				}
			}
			Collection<String> contributorNames =
					(Collection<String>) ld.get("contributorName");
			if (contributorNames != null) {
				for (String item : contributorNames) {
					rec.creators.add(new Pair<String, String>(item, null));
				}
			}

		} catch (NullPointerException e) {
			play.Logger.debug("", e);
		}
	}

	private static void addSubjects(Map<String, Object> ld, DataciteRecord rec) {
		Collection<Map<String, Object>> subjects =
				(Collection<Map<String, Object>>) ld.get("subject");
		if (subjects != null) {
			for (Map<String, Object> item : subjects) {
				String subject = (String) item.get("prefLabel");
				String id = (String) item.get("@id");
				String type = "GND";
				if (id.startsWith("http://d-nb.info/gnd/")) {
					type = "GND";
				} else if (id.startsWith("http://dewey.info/")) {
					type = "DDC";
				}
				rec.subjects.add(new Pair<String, String>(subject, type));
			}
		}
		// List<String> freeSubjects = (HashSet<String>)ld.get("dc:subject");
		// if (freeSubjects != null) {
		// for (String item : freeSubjects) {
		// rec.subjects.add(new Pair<String, String>(item, "FREE"));
		// }
		// }
	}

}
