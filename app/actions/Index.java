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

import java.util.List;

import models.Globals;
import models.Node;

/**
 * Eine Sammlung von Methoden zur Indexierung von Ressourcen (Metadaten oder
 * Volltexte) in Elasticsearch-Indexen
 * 
 * @author Jan Schnasse
 * @author Ingolf Kuss
 */
public class Index {

	/**
	 * Entfernt eine Ressource aus allen Indexen ihres Namensraumes.
	 * 
	 * @param n the node to remove from all indexes
	 * @return A short message
	 */
	public String remove(Node n) {
		String pid = n.getPid();
		String type = n.getContentType();
		String namespace = n.getNamespace();
		return removeFromAllIndexed(pid, type, namespace);
	}

	private static String removeFromAllIndexed(String pid, String type,
			String namespace) {
		if (type == null)
			return pid + " not deleted from index. Cause: No type available!";
		StringBuffer message = new StringBuffer();
		message.append(removeFromPrivateIndex(pid, type, namespace));
		message.append(removeFromPublicIndex(pid, type, namespace));
		message.append(removeFromFulltextIndex(pid, type, namespace));
		message.append(removeFromPublicFulltextIndex(pid, type, namespace));
		return message.toString();

	}

	/**
	 * Entfernt den Inhalt einer PID aus einem (Such-)Index
	 * 
	 * @param pid PID des Knotens
	 * @param type Inhaltstyp des Knotens
	 * @param indexPrefix Prefix des Index (z.B. "public_"); kann leer (null) sein
	 * @param namespace to.science-Namensraum des Index (z.B. "edoweb")
	 * @param indexSuffix Das Suffix im Indexnamen hinter dem Namensraum, z.B.
	 *          "2"; für Versionsverwaltung der Indexe
	 * @return
	 */
	private static String removeFromIndex(String pid, String type,
			String indexPrefix, String namespace, String indexSuffix) {
		StringBuffer msg = new StringBuffer();
		String indexFullName = namespace;
		if (indexPrefix != null && !indexPrefix.isEmpty()) {
			indexFullName = indexPrefix.concat(namespace);
		}
		if (indexSuffix != null && !indexSuffix.isEmpty()) {
			indexFullName = indexFullName.concat(indexSuffix);
		}

		try {
			Globals.search.delete(pid, indexFullName, type);
			msg.append("Content of type " + type + " for pid " + pid
					+ " removed from " + indexFullName + "\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append("Content of type " + type + " for pid " + pid
					+ " cannot be removed from" + indexFullName + "\n");
		}
		return msg.toString();
	}

	private static String removeFromPrivateIndex(String pid, String type,
			String namespace) {
		return removeFromIndex(pid, type, null, namespace, "2");
	}

	private static String removeFromPublicIndex(String pid, String type,
			String namespace) {
		return removeFromIndex(pid, type, Globals.PUBLIC_INDEX_PREF, namespace,
				"2");
	}

	private static String removeFromFulltextIndex(String pid, String type,
			String namespace) {
		return removeFromIndex(pid, type, Globals.PDFBOX_OCR_INDEX_PREF, namespace,
				null);
	}

	private static String removeFromPublicFulltextIndex(String pid, String type,
			String namespace) {
		return removeFromIndex(pid, type, Globals.PUBLIC_FULLTEXT_INDEX_PREF,
				namespace, null);
	}

	/**
	 * Indexiert eine Ressource in allen verwendeten Indexen.
	 * 
	 * @param n the node to index in all indexes
	 * @return a message
	 */
	public String index(Node n) {
		play.Logger.debug("Ressource mit PID " + n.getPid() + " wird indexiert.");
		String namespace = n.getNamespace();
		String pid = n.getPid();
		String type = n.getContentType();
		StringBuffer msg = new StringBuffer();
		msg.append(indexToPrivateIndex(pid, type, namespace, n));
		msg.append(handlePublicIndex(pid, type, namespace, n));
		msg.append(handleFulltextIndex(pid, type, namespace, n));
		msg.append(handlePublicFulltextIndex(pid, type, namespace, n));
		return msg.toString();
	}

	/**
	 * Diese Methode baut einen Index auf, der sowohl die Metadaten der öffentlich
	 * zugänglichen Objekte enthält als auch die Volltexte.
	 * 
	 * @param pid Die Pid des Knotens, der indexiert werden soll
	 * @param type Der Inhaltstype des Knotens (= n.getContentType() )
	 * @param index Der Namensraum für den Index (z.B. "edoweb")
	 * @param n Der Knoten, der indexiert werden soll
	 * @return eine Textnachricht
	 */
	private static String handlePublicFulltextIndex(String pid, String type,
			String namespace, Node n) {
		StringBuffer msg = new StringBuffer();
		// 1. Indexierung der Metadaten der Ressource
		if ("public".equals(n.getPublishScheme())) {
			if ("monograph".equals(n.getContentType())
					|| "journal".equals(n.getContentType())
					|| "webpage".equals(n.getContentType())
					|| "article".equals(n.getContentType())
					|| "researchData".equals(n.getContentType())) {
				msg.append(indexMetadataToPublicFulltextIndex(pid, type, namespace, n));
				return msg.toString();
			} else
				msg.append("Content of type " + type + " for pid " + pid
						+ " not indexed as metadata in public fulltext index!\n");
		} else {
			msg.append(removeFromPublicFulltextIndex(pid, type, namespace));
		}

		// 2. Indexierung des Volltextes der Ressource
		if ("public".equals(n.getAccessScheme())) {
			if ("file".equals(n.getContentType())
					&& "application/pdf".equals(n.getMimeType()))
				msg.append(indexFulltextToPublicFulltextIndex(pid, type, namespace, n));
			else
				msg.append("Content of type " + type + " for pid " + pid
						+ " not indexed as fulltext in public fulltext index!\n");
		} else {
			msg.append(removeFromPublicFulltextIndex(pid, type, namespace));
		}

		return msg.toString();
	}

	private String handleFulltextIndex(String pid, String type, String namespace,
			Node n) {
		if ("public".equals(n.getAccessScheme())) {
			if ("file".equals(n.getContentType())
					&& "application/pdf".equals(n.getMimeType())) {
				return indexToFulltextIndex(pid, type, namespace, n);
			}
		} else {
			return removeFromFulltextIndex(pid, type, namespace);
		}
		return pid + " not indexed in fulltext index!\n";
	}

	private String handlePublicIndex(String pid, String type, String namespace,
			Node n) {
		if ("public".equals(n.getPublishScheme())) {
			if ("monograph".equals(n.getContentType())
					|| "journal".equals(n.getContentType())
					|| "webpage".equals(n.getContentType())
					|| "article".equals(n.getContentType())
					|| "researchData".equals(n.getContentType()))
				return indexToPublicIndex(pid, type, namespace, n);
		} else {
			return removeFromPublicIndex(pid, type, namespace);
		}
		return pid + " not indexed in public index!\n";
	}

	/**
	 * Diese Methode indexiert die Metadaten einer Ressource im Index für
	 * öffentliche Objekte und Volltexte.
	 * 
	 * @param pid Der PID der Ressource
	 * @param type Der Inhaltstype der Ressource
	 * @param index Der Namensraum der Ressource (z.B. "edoweb")
	 * @param data Der Knoten (Klasse Node) der Ressource
	 * @return eine Textnachricht
	 */
	private static String indexMetadataToPublicFulltextIndex(String pid,
			String type, String namespace, Node data) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.index(Globals.PUBLIC_FULLTEXT_INDEX_PREF + namespace, type,
					pid, data.toString2());
			msg.append("Metadata of " + pid + " indexed in "
					+ Globals.PUBLIC_FULLTEXT_INDEX_PREF + namespace + "\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append("Metadata of " + pid + " not indexed in "
					+ Globals.PUBLIC_FULLTEXT_INDEX_PREF + namespace + "\n");
		}
		return msg.toString();
	}

	/**
	 * Diese Methode indexiert den Volltext einer Ressource im Index für
	 * öffentliche Objekte und Volltexte.
	 * 
	 * @param pid Der PID der Ressource
	 * @param type Der Inhaltstype der Ressource
	 * @param index Der Namensraum der Ressource (z.B. "edoweb")
	 * @param data Der Knoten (Klasse Node) der Ressource
	 * @return eine Textnachricht
	 */
	private static String indexFulltextToPublicFulltextIndex(String pid,
			String type, String namespace, Node data) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.index(Globals.PUBLIC_FULLTEXT_INDEX_PREF + namespace, type,
					pid, new Transform().pdfbox(data).toString2());
			msg.append("Fulltext of " + pid + " indexed in "
					+ Globals.PUBLIC_FULLTEXT_INDEX_PREF + namespace + "\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append("Fulltext of " + pid + " not indexed in "
					+ Globals.PUBLIC_FULLTEXT_INDEX_PREF + namespace + "\n");
		}
		return msg.toString();
	}

	private static String indexToFulltextIndex(String pid, String type,
			String namespace, Node data) {
		try {
			Globals.search.index(Globals.PDFBOX_OCR_INDEX_PREF + namespace, type, pid,
					new Transform().pdfbox(data).toString2());
			return pid + " indexed in " + Globals.PDFBOX_OCR_INDEX_PREF + namespace
					+ "\n";
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			return pid + " not indexed in " + Globals.PDFBOX_OCR_INDEX_PREF
					+ namespace + "\n";
		}
	}

	private static String indexToPublicIndex(String pid, String type,
			String namespace, Node data) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.index(Globals.PUBLIC_INDEX_PREF + namespace + "2", type,
					pid, data.toString2());
			msg.append(
					pid + " indexed in " + Globals.PUBLIC_INDEX_PREF + namespace + "2\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " not indexed in " + Globals.PUBLIC_INDEX_PREF
					+ namespace + "2\n");
		}
		return msg.toString();
	}

	private static String indexToPrivateIndex(String pid, String type,
			String namespace, Node data) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.index(namespace + "2", type, pid, data.toString2());
			msg.append(pid + " indexed in " + namespace + "2\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " not indexed in " + namespace + "2\n");
		}
		return msg.toString();
	}

	/**
	 * @param nodes a list of nodes
	 * @param indexNameWithDatestamp a name for a new index
	 * @return the list of indexed objects as string
	 */
	public String indexAll(List<Node> nodes, String indexNameWithDatestamp) {
		return Globals.search.indexAll(nodes, indexNameWithDatestamp).toString();
	}

}
