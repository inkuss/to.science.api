/*
 * Copyright 2012 hbz NRW (http://www.hbz-nrw.de/)
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
package archive.search;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import helper.mail.Mail;
import models.Globals;
import models.Node;

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import play.Play;
import actions.Transform;
import archive.fedora.CopyUtils;

/**
 * @author Jan Schnasse schnasse@hbz-nrw.de
 * 
 */
public class Search {

	Map<String, Object> aggregations;

	@SuppressWarnings("serial")
	class InvalidRangeException extends RuntimeException {
		// It is just there to be thrown
	}

	@SuppressWarnings("serial")
	class SearchException extends RuntimeException {
		public SearchException(Throwable e) {
			super(e);
		}

		public SearchException(String msg, Throwable e) {
			super(msg, e);
		}
	}

	Client client = null;

	Search(Client client) {
		this.client = client;
		initAggregations();
	}

	void init(String[] index, String config) {
		try {
			String indexConfig = CopyUtils
					.copyToString(Play.application().resourceAsStream(config), "utf-8");
			for (String i : index) {
				client.admin().indices().prepareCreate(i).setSource(indexConfig)
						.execute().actionGet();
			}
		} catch (org.elasticsearch.indices.IndexAlreadyExistsException e) {
			play.Logger.warn("", e);
		} catch (Exception e) {
			play.Logger.warn("", e);
		}
	}

	void init(String[] index) {
		for (String i : index) {
			play.Logger.info("Init elasticsearch index " + i);
			init(i);
			play.Logger.info("Init elasticsearch index " + i + "2");
			init(i + "2");
		}
	}

	void init(String index) {
		try {
			play.Logger.info("Configure " + index);
			String indexConfig = CopyUtils.copyToString(
					Play.application().resourceAsStream(Globals.elasticsearchSettings),
					"utf-8");

			client.admin().indices().prepareCreate(index).setSource(indexConfig)
					.execute().actionGet();
		} catch (org.elasticsearch.indices.IndexAlreadyExistsException e) {
			play.Logger.info("Index already exists!");
		} catch (Exception e) {
			play.Logger
					.warn("Problems when creating " + index + " :" + e.getMessage());
		}
	}

	ActionResponse index(String index, String type, String id, String data) {
		try {
			if (!indexExists(index)) {
				play.Logger.info("Create new Index " + index);
				init(index);
			}
			play.Logger.debug("Search.index: update index for index:" + index
					+ ", type:" + type + ", id:" + id);
			ActionResponse response = client.prepareIndex(index, type, id)
					.setSource(data).execute().actionGet();
			// int error = 1 / 0; # Fehler + E-Mail provozieren
			refresh();
			play.Logger.info("Search.index: Index updated successfully!");
			return response;
		} catch (Exception e) {
			play.Logger.warn("E-Mail schicken für EDOZWO-1107");
			helper.mail.Mail.sendMail("Objekt " + Globals.protocol + Globals.server
					+ "/resource/" + id + " vom Typ " + type + " konnte zum Index "
					+ index
					+ " nicht hinzugefügt werden !!!\nBehebungsvorschlag: Bitte speichern Sie das Objekt einmal manuell erneut ab:\nReiter Bearbeiten -- Titel erneut eingeben oder importieren -- Speichern (Save)",
					"Edoweb: FEHLER bei der Indexierung von " + id);
			throw new SearchException(
					"Failed to index " + index + "," + type + "," + id, e);
		}
	}

	private boolean indexExists(String index) {
		refresh();
		return client.admin().indices().exists(new IndicesExistsRequest(index))
				.actionGet().isExists();
	}

	SearchHits listResources(String index, String type, int from, int until) {
		if (from >= until)
			throw new InvalidRangeException();
		SearchRequestBuilder builder = null;
		refresh();
		if (index == null || index.equals(""))
			builder = client.prepareSearch();
		else
			builder = client.prepareSearch(index);
		if (type != null && !type.equals(""))
			builder.setTypes(type);
		builder.setFrom(from).setSize(until - from);
		SearchResponse response = builder.execute().actionGet();
		return response.getHits();
	}

	List<String> list(String index, String type, int from, int until) {
		SearchHits hits = listResources(index, type, from, until);
		Iterator<SearchHit> it = hits.iterator();
		List<String> list = new Vector<String>();
		while (it.hasNext()) {
			SearchHit hit = it.next();
			list.add(hit.getId());
		}
		return list;
	}

	ActionResponse delete(String id, String index, String type) {
		try {
			if (!indexExists(index)) {
				play.Logger.info("Create new Index " + index);
				init(index);
			}
			play.Logger.debug("Search.index: delete from index:" + index + ", type:"
					+ type + ", id:" + id);
			ActionResponse response =
					client.prepareDelete(index, type, id).execute().actionGet();
			int error = 1 / 0;
			refresh();
			play.Logger.info("Search.index: Deleted from index successfully!");
			return response;
		} catch (Exception e) {
			play.Logger.warn("E-Mail schicken für EDOZWO-1107");
			helper.mail.Mail.sendMail("Objekt " + Globals.protocol + Globals.server
					+ "/resource/" + id + " vom Typ " + type + " konnte aus Index "
					+ index
					+ " nicht gelöscht werden !!!\nBehebungsvorschlag: Wenn Sie das Objekt wirklich löschen wollten, versuchen Sie es bitte erneut.\nFalls sie das Objekt nur aktualisieren oder modifizieren wollten, sind keine Aktionen erforderlich.",
					"Edoweb: FEHLER bei der Indexierung von " + id);
			throw new SearchException(
					"Failed to index " + index + "," + type + "," + id, e);
		}
	}

	SearchHits query(String index, String fieldName, String fieldValue) {
		refresh();
		QueryBuilder query = QueryBuilders.boolQuery()
				.must(QueryBuilders.matchQuery(fieldName, fieldValue));
		SearchResponse response =
				client.prepareSearch(index).setQuery(query).execute().actionGet();
		return response.getHits();
	}

	SearchResponse query(String[] index, String queryString, int from,
			int until) {
		refresh();
		play.Logger.debug("Search for " + queryString);
		QueryBuilder query = QueryBuilders.queryString(queryString);
		return query(index, query, from, until);
	}

	SearchResponse query(String[] index, QueryBuilder query, int from,
			int until) {
		refresh();
		SearchResponse response = client.prepareSearch(index).setQuery(query)
				.setFrom(from).setSize(until - from).setAggregations(aggregations)
				.execute().actionGet();
		return response;
	}

	Map<String, Object> getSettings(String index, String type) {
		try {
			refresh();
			ClusterState clusterState = client.admin().cluster().prepareState()
					.setIndices(index).execute().actionGet().getState();
			IndexMetaData inMetaData = clusterState.getMetaData().index(index);
			MappingMetaData metad = inMetaData.mapping(type);
			return metad.getSourceAsMap();
		} catch (IOException e) {
			throw new SearchException(e);
		}
	}

	/**
	 * @param pid a pid of a node
	 * @return a map that represents the node
	 */
	public Map<String, Object> get(String pid) {
		refresh();
		GetResponse response =
				client.prepareGet(pid.split(":")[0], "_all", pid).execute().actionGet();
		return response.getSource();

	}

	/**
	 * @param list list of nodes to index
	 * @param index name of a index
	 * @return list of messages
	 */
	public List<String> indexAll(List<Node> list, String index) {
		init(index);
		init(Globals.PUBLIC_INDEX_PREF + index);
		List<String> result = new ArrayList<String>();
		BulkRequestBuilder internalIndexBulk = client.prepareBulk();
		BulkRequestBuilder publicIndexBulk = client.prepareBulk();

		for (Node node : list) {
			try {
				play.Logger.debug("Going to add " + node.getPid() + " to bulk action");
				StringBuffer msg = new StringBuffer("Index " + node.getPid() + " to ");
				String source = node.toString();
				internalIndexBulk.add(
						client.prepareIndex(index, node.getContentType(), node.getPid())
								.setSource(source));

				msg.append(index);
				if ("public".equals(node.getPublishScheme())) {
					if ("monograph".equals(node.getContentType())
							|| "journal".equals(node.getContentType())
							|| "webpage".equals(node.getContentType())) {

						publicIndexBulk
								.add(client
										.prepareIndex(Globals.PUBLIC_INDEX_PREF + index,
												node.getContentType(), node.getPid())
										.setSource(source));
						msg.append(" and " + Globals.PUBLIC_INDEX_PREF + index);
					}

				}
				if ("public".equals(node.getAccessScheme())) {
					if ("file".equals(node.getContentType())) {
						publicIndexBulk.add(client
								.prepareIndex(Globals.PDFBOX_OCR_INDEX_PREF + index,
										node.getContentType(), node.getPid())
								.setSource(new Transform().pdfbox(node).toString()));
						msg.append(" and " + Globals.PDFBOX_OCR_INDEX_PREF + index);
					}
				}
				msg.append("\n");
				result.add(msg.toString());
				play.Logger.info("Add " + node.getPid() + " to bulk action");
			} catch (Exception e) {
				play.Logger.warn("", e);
				result.add("A problem occured: " + e.getMessage());
			}
		}
		try {
			play.Logger.debug("Start building internal Index " + index);
			BulkResponse bulkResponse = internalIndexBulk.execute().actionGet();
			if (bulkResponse.hasFailures()) {
				result.add(bulkResponse.buildFailureMessage());
				play.Logger.warn("FAIL: " + bulkResponse.buildFailureMessage());
			}

			play.Logger.info("Start building public Index " + index);

			bulkResponse = publicIndexBulk.execute().actionGet();
			if (bulkResponse.hasFailures()) {
				result.add(bulkResponse.buildFailureMessage());
				play.Logger.warn("FAIL: " + bulkResponse.buildFailureMessage());
			}
		} catch (Exception e) {
			play.Logger.warn("", e);
		}
		return result;
	}

	private void refresh() {
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	private void initAggregations() {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream in =
				play.Play.application().resourceAsStream("aggregations.conf")) {
			Map map = mapper.readValue(in, Map.class);
			aggregations = (Map<String, Object>) map.get("aggs");
		} catch (Exception e) {
			aggregations = new HashMap<>();
		}
	}

	public SearchResponse query(String[] index, String queryString, int from,
			int until, Map<String, Object> aggregationConf) {
		refresh();
		SearchResponse response = client.prepareSearch(index)
				.setQuery(QueryBuilders.queryString(queryString)).setFrom(from)
				.setSize(until - from).setAggregations(aggregationConf).execute()
				.actionGet();
		return response;
	}
}
