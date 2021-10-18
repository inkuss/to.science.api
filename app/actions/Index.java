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
 * @author Jan Schnasse
 *
 */
public class Index {

	/**
	 * @param n the node to remove from all indexes
	 * @return A short message
	 */
	public String remove(Node n) {
		String pid = n.getPid();
		String type = n.getContentType();
		String index = n.getNamespace();
		return removeFromAllIndexed(pid, type, index);
	}

	private String removeFromAllIndexed(String pid, String type, String index) {
		if (type == null)
			return pid + " not deleted from index. Cause: No type available!";
		StringBuffer message = new StringBuffer();
		message.append(removeFromPrivateIndex(pid, type, index));
		message.append(removeFromPublicIndex(pid, type, index));
		message.append(removeFromFulltextIndex(pid, type, index));
		return message.toString();

	}

	private String removeFromFulltextIndex(String pid, String type,
			String index) {
		try {
			Globals.search.delete(pid, Globals.PDFBOX_OCR_INDEX_PREF + index, type);
			return pid + " removed from " + Globals.PDFBOX_OCR_INDEX_PREF + index
					+ "\n";
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			return pid + " cannot be removed from" + Globals.PDFBOX_OCR_INDEX_PREF
					+ index + "\n";
		}
	}

	private String removeFromPublicIndex(String pid, String type, String index) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.delete(pid, Globals.PUBLIC_INDEX_PREF + index, type);
			msg.append(
					pid + " removed from " + Globals.PUBLIC_INDEX_PREF + index + "\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " cannot be removed from" + Globals.PUBLIC_INDEX_PREF
					+ index + "\n");
		}
		try {
			Globals.search.delete(pid, Globals.PUBLIC_INDEX_PREF + index + "2", type);
			msg.append(
					pid + " removed from " + Globals.PUBLIC_INDEX_PREF + index + "2\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " cannot be removed from" + Globals.PUBLIC_INDEX_PREF
					+ index + "2\n");
		}
		return msg.toString();
	}

	private String removeFromPrivateIndex(String pid, String type, String index) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.delete(pid, index, type);
			msg.append(pid + " removed from " + index + "\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " cannot be removed from" + index + "\n");
		}
		try {
			Globals.search.delete(pid, index + "2", type);
			msg.append(pid + " removed from " + index + "2\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " cannot be removed from" + index + "2\n");
		}
		return msg.toString();
	}

	/**
	 * @param n the node to index in all indexes
	 * @return a message
	 */
	public String index(Node n) {
		String index = n.getNamespace();
		String pid = n.getPid();
		String type = n.getContentType();
		StringBuffer msg = new StringBuffer();
		msg.append(indexToPrivateIndex(pid, type, index, n));
		msg.append(handlePublicIndex(pid, type, index, n));
		msg.append(handleFulltextIndex(pid, type, index, n));
		return msg.toString();
	}

	private String handleFulltextIndex(String pid, String type, String index,
			Node n) {
		if ("public".equals(n.getAccessScheme())) {
			if ("file".equals(n.getContentType())
					&& "application/pdf".equals(n.getMimeType())) {
				return indexToFulltextIndex(pid, type, index, n);
			}
		} else {
			return removeFromFulltextIndex(pid, type, index);
		}
		return pid + " not indexed in fulltext index!\n";
	}

	private String handlePublicIndex(String pid, String type, String index,
			Node n) {
		if ("public".equals(n.getPublishScheme())) {
			if ("monograph".equals(n.getContentType())
					|| "journal".equals(n.getContentType())
					|| "webpage".equals(n.getContentType())
					|| "article".equals(n.getContentType())
					|| "researchData".equals(n.getContentType())
					|| "orca".contentEquals(n.getContentType()))
				return indexToPublicIndex(pid, type, index, n);
		} else {
			return removeFromPublicIndex(pid, type, index);
		}
		return pid + " not indexed in public index!\n";
	}

	private String indexToPublicIndex(String pid, String type, String index,
			Node data) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.index(Globals.PUBLIC_INDEX_PREF + index, type, pid,
					data.toString());
			msg.append(
					pid + " indexed in " + Globals.PUBLIC_INDEX_PREF + index + "\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(
					pid + " not indexed in " + Globals.PUBLIC_INDEX_PREF + index + "\n");
		}
		try {
			Globals.search.index(Globals.PUBLIC_INDEX_PREF + index + "2", type, pid,
					data.toString2());
			msg.append(
					pid + " indexed in " + Globals.PUBLIC_INDEX_PREF + index + "2\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(
					pid + " not indexed in " + Globals.PUBLIC_INDEX_PREF + index + "2\n");
		}
		return msg.toString();
	}

	private String indexToFulltextIndex(String pid, String type, String index,
			Node data) {
		try {
			Globals.search.index(Globals.PDFBOX_OCR_INDEX_PREF + index, type, pid,
					new Transform().pdfbox(data).toString2());
			return pid + " indexed in " + Globals.PDFBOX_OCR_INDEX_PREF + index
					+ "\n";
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			return pid + " not indexed in " + Globals.PDFBOX_OCR_INDEX_PREF + index
					+ "\n";
		}
	}

	private String indexToPrivateIndex(String pid, String type, String index,
			Node data) {
		StringBuffer msg = new StringBuffer();
		try {
			Globals.search.index(index, type, pid, data.toString());
			msg.append(pid + " indexed in " + index + "\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " not indexed in " + index + "\n");
		}
		try {
			Globals.search.index(index + "2", type, pid, data.toString2());
			msg.append(pid + " indexed in " + index + "2\n");
		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			msg.append(pid + " not indexed in " + index + "2\n");
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
