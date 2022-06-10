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
package helper.oai;

import static archive.fedora.FedoraVocabulary.IS_MEMBER_OF;
import static archive.fedora.FedoraVocabulary.ITEM_ID;

import java.util.List;
import java.util.Properties;
import java.util.Vector;

import models.DublinCoreData;
import models.Globals;
import models.Link;
import models.Node;
import models.Transformer;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import actions.Modify;
import archive.fedora.RdfUtils;

/**
 * @author jan schnasse
 *
 */
public class OaiDispatcher {

	/**
	 * @param node the node to be published on the oai interface
	 * @return A short message.
	 */
	public static String makeOAISet(Node node, boolean doIndex) {
		try {
			play.Logger.info("Connect transformer to " + node.getPid());
			updateTransformer(null, node);
			play.Logger.info("Create OAI-Sets for " + node.getPid());
			createDDCSets(node);
			createOpenAccessSet(node);
			createUrnSets(node);
			createAlephSet(node);
			createContentTypeSet(node);
			if (doIndex == true)
				new Modify().updateIndex(node.getPid());
			return node.getPid() + " successfully created oai sets!";
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	public static void updateTransformer(List<String> transformers, Node node) {
		node.removeAllContentModels();
		addUnknownTransformer(transformers, node);
		addOaiDcTransformer(node);
		addEpicurTransformer(node);
		addAlephTransformer(node);
		addMetsTransformer(node);
		addRdfTransformer(node);
		addWglTransformer(node);
		addOpenAireTransformer(node);
		addModsTransformer(node);

	}

	public static String initContentModels(String namespace) {
		int port = Globals.getPort();
		play.Logger.info("Reinit fedora content models to listen on port: " + port);
		List<Transformer> transformers = new Vector<>();
		String internalAccessRoute =
				"http://localhost:" + port + "/resource/(pid).";
		transformers.add(new Transformer(namespace + "epicur", "epicur",
				internalAccessRoute + "epicur"));
		transformers.add(new Transformer(namespace + "oaidc", "oaidc",
				internalAccessRoute + "oaidc"));
		transformers.add(new Transformer(namespace + "pdfa", "pdfa",
				internalAccessRoute + "pdfa"));
		transformers.add(new Transformer(namespace + "pdfbox", "pdfbox",
				internalAccessRoute + "pdfbox"));
		transformers.add(new Transformer(namespace + "aleph", "aleph",
				internalAccessRoute + "aleph"));
		transformers.add(new Transformer(namespace + "mets", "mets",
				internalAccessRoute + "mets"));
		transformers.add(new Transformer(namespace + "openaire", "openaire",
				internalAccessRoute + "openaire"));
		transformers.add(new Transformer(namespace + "mods", "mods",
				internalAccessRoute + "mods"));
		transformers.add(
				new Transformer(namespace + "rdf", "rdf", internalAccessRoute + "rdf"));
		transformers.add(
				new Transformer(namespace + "wgl", "wgl", internalAccessRoute + "wgl"));
		OaiDispatcher.contentModelsInit(transformers);
		String result = "Reinit contentModels " + namespace + "epicur, " + namespace
				+ "oaidc, " + namespace + "pdfa, " + namespace + "pdfbox, " + namespace
				+ "aleph, " + namespace + "mets, " + namespace + "rdf, " + namespace
				+ "wgl," + namespace + "openaire" + namespace + "mods";
		play.Logger.info(result);
		return result;
	}

	/**
	 * 
	 * @param cms a List of Transformers
	 * @param userId
	 * @return a message
	 */
	public static String contentModelsInit(List<Transformer> cms) {
		Globals.fedora.updateContentModels(cms);
		return "Success!";
	}

	private static void createDDCSets(Node node) throws RepositoryException {
		OaiSetBuilder oaiSetBuilder = new OaiSetBuilder();
		String metadata2 = node.getMetadata2();
		if (metadata2 == null)
			return;
		RepositoryResult<Statement> statements =
				RdfUtils.getStatements(metadata2, "fedora:info/");
		while (statements.hasNext()) {
			Statement st = statements.next();
			String subject = st.getSubject().stringValue();
			String predicate = st.getPredicate().stringValue();
			String object = st.getObject().stringValue();
			OaiSet set = oaiSetBuilder.getSet(subject, predicate, object);
			if (set == null) {
				continue;
			}
			if (!Globals.fedora.nodeExists(set.getPid())) {
				createOAISet(set.getName(), set.getSpec(), set.getPid());
			}
			linkObjectToOaiSet(node, set.getSpec(), set.getPid());
		}
	}

	private static void createOpenAccessSet(Node node) {
		if ("public".equals(node.getAccessScheme())) {
			addSet(node, "open_access");
		}
	}

	private static void createUrnSets(Node node) {
		if (node.hasUrnInMetadata()) {
			addSet(node, "epicur");
			String urn = node.getUrnFromMetadata();
			if (urn.startsWith("urn:nbn:de:hbz:929:01")) {
				addSet(node, "urn-set-1");
			} else if (urn.startsWith("urn:nbn:de:hbz:929:02")) {
				addSet(node, "urn-set-2");
			}
		}
		if (node.hasUrn()) {
			addSet(node, "epicur");
			String urn = node.getUrn();
			if (urn.startsWith("urn:nbn:de:hbz:929:01")) {
				addSet(node, "urn-set-1");
			} else if (urn.startsWith("urn:nbn:de:hbz:929:02")) {
				addSet(node, "urn-set-2");
			}
		}
	}

	private static void createContentTypeSet(Node node) {
		addSet(node, node.getContentType());
	}

	private static void createAlephSet(Node node) {
		if (node.hasLinkToCatalogId()) {
			play.Logger.info(node.getPid() + " add aleph set!");
			addSet(node, "aleph");
			addSet(node, Globals.alephSetName);
		}
	}

	private static void addSet(Node node, String name) {
		play.Logger.info("Add OAI-Set " + name + " to " + node.getPid());
		String spec = name;
		String namespace = "oai";
		String oaipid = namespace + ":" + name;
		if (!Globals.fedora.nodeExists(oaipid)) {
			createOAISet(name, spec, oaipid);
		}
		linkObjectToOaiSet(node, spec, oaipid);
	}

	private static void linkObjectToOaiSet(Node node, String spec, String pid) {
		node.removeRelations(ITEM_ID);
		node.removeRelation(IS_MEMBER_OF, "info:fedora/" + pid);
		Link link = new Link();
		link.setPredicate(IS_MEMBER_OF);
		link.setObject("info:fedora/" + pid, false);
		node.addRelation(link);
		link = new Link();
		link.setPredicate(ITEM_ID);
		link.setObject("oai:" + Globals.server + ":" + node.getPid(), false);
		node.addRelation(link);
		Globals.fedora.updateNode(node);
	}

	private static void createOAISet(String name, String spec, String pid) {
		String setSpecPred = "http://www.openarchives.org/OAI/2.0/setSpec";
		String setNamePred = "http://www.openarchives.org/OAI/2.0/setName";
		Link setSpecLink = new Link();
		setSpecLink.setPredicate(setSpecPred);
		Link setNameLink = new Link();
		setNameLink.setPredicate(setNamePred);
		String namespace = "oai";
		{
			Node oaiset = new Node();
			oaiset.setNamespace(namespace);
			oaiset.setPID(pid);
			setSpecLink.setObject(spec, true);
			oaiset.addRelation(setSpecLink);
			setNameLink.setObject(name, true);
			oaiset.addRelation(setNameLink);
			DublinCoreData dc = oaiset.getDublinCoreData();
			dc.addTitle(name);
			oaiset.setDublinCoreData(dc);
			Globals.fedora.createNode(oaiset);
		}
	}

	private static void addUnknownTransformer(List<String> transformers,
			Node node) {
		if (transformers != null) {
			for (String t : transformers) {
				if ("oaidc".equals(t))
					continue; // implicitly added - or not allowed to set
				if ("epicur".equals(t))
					continue; // implicitly added - or not allowed to set
				if ("aleph".equals(t))
					continue; // implicitly added - or not allowed to set
				if ("mets".equals(t))
					continue; // implicitly added - or not allowed to set
				if ("rdf".equals(t))
					continue; // implicitly added - or not allowed to set
				if ("wgl".equals(t))
					continue; // implicitly added - or not allowed to set
				if ("openaire".equals(t))
					continue; // implicitly added - or not allowed to set
				if ("mods".equals(t))
					continue; // implicitly added - or not allowed to set
				node.addTransformer(new Transformer(t));
			}
		}
	}

	private static void addMetsTransformer(Node node) {
		String type = node.getContentType();
		if ("public".equals(node.getPublishScheme())) {
			if ("monograph".equals(type) || "journal".equals(type)
					|| "webpage".equals(type) || "researchData".equals(type)
					|| "article".equals(type)) {
				node.addTransformer(new Transformer("mets"));
			}
		}
	}

	private static void addAlephTransformer(Node node) {
		String type = node.getContentType();
		if (node.hasPersistentIdentifier()) {
			if ("monograph".equals(type) || "journal".equals(type)
					|| "webpage".equals(type))
				if (node.hasLinkToCatalogId()) {
					node.addTransformer(new Transformer("aleph"));
				}
		}
	}

	private static void addEpicurTransformer(Node node) {
		if (node.hasUrnInMetadata() || node.hasUrn()) {
			node.addTransformer(new Transformer("epicur"));
		}
	}

	private static void addOaiDcTransformer(Node node) {
		String type = node.getContentType();
		if ("public".equals(node.getPublishScheme())) {
			if ("monograph".equals(type) || "journal".equals(type)
					|| "webpage".equals(type) || "researchData".equals(type)
					|| "article".equals(type)) {
				node.addTransformer(new Transformer("oaidc"));
			}
		}
	}

	private static void addRdfTransformer(Node node) {
		String type = node.getContentType();
		if ("public".equals(node.getPublishScheme())) {
			if ("monograph".equals(type) || "journal".equals(type)
					|| "webpage".equals(type) || "researchData".equals(type)
					|| "article".equals(type)) {
				node.addTransformer(new Transformer("rdf"));
			}
		}
	}

	private static void addWglTransformer(Node node) {
		String type = node.getContentType();
		if ("public".equals(node.getPublishScheme())) {
			if ("article".equals(type)
					&& node.getLd2().containsKey("collectionOne")) {
				node.addTransformer(new Transformer("wgl"));
			}
			// Workaround for identifying Leibniz Institutions while lobid doesn't
			// deliver MAB 078r (see FRL-325)
			if ("monograph".equals(type) && WglMapper.hasWglContributor(node)) {
				node.addTransformer(new Transformer("wgl"));
			}
		}
	}

	private static void addOpenAireTransformer(Node node) {
		String type = node.getContentType();
		if ("public".equals(node.getPublishScheme())) {
			if ("monograph".equals(type) || "journal".equals(type)
					|| "webpage".equals(type) || "researchData".equals(type)
					|| "article".equals(type)) {
				node.addTransformer(new Transformer("openaire"));
			}
		}
	}

	private static void addModsTransformer(Node node) {
		String type = node.getContentType();
		if ("public".equals(node.getPublishScheme())) {
			if ("monograph".equals(type) || "journal".equals(type)
					|| "webpage".equals(type) || "researchData".equals(type)
					|| "article".equals(type)) {
				node.addTransformer(new Transformer("mods"));
			}
		}
	}

}
