package actions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.w3c.dom.Element;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

import archive.fedora.RdfUtils;
import archive.fedora.XmlUtils;
import helper.MyEtikettMaker;
import models.Globals;
import models.Node;

/**
 * Add a label for each uri
 * 
 * @author Jan Schnasse
 *
 */
public class Enrich {

	private static final String PREF_LABEL =
			"http://www.w3.org/2004/02/skos/core#prefLabel";

	public static String enrichMetadata2(Node node, boolean doIndex) {
		try {
			play.Logger.info("Enrich 2 " + node.getPid());
			String metadata = node.getMetadata2();
			if (metadata == null || metadata.isEmpty()) {
				play.Logger.info("No metadata2 to enrich " + node.getPid());
				return "No metadata2 to enrich " + node.getPid();
			}
			List<Statement> enrichStatements = new ArrayList<>();
			enrichAll(node, metadata, enrichStatements);
			metadata = RdfUtils.replaceTriples(enrichStatements, metadata);
			new Modify().updateMetadata2(node, metadata, doIndex);
		} catch (Exception e) {
			play.Logger.debug("", e);
			return "Enrichment of " + node.getPid() + " partially failed !\n"
					+ e.getMessage();
		}
		return "Enrichment of " + node.getPid() + " succeeded!";
	}

	public static String enrichMetadata1(Node node) {
		try {
			play.Logger.info("Enrich " + node.getPid());
			String metadata = node.getMetadata1();
			if (metadata == null || metadata.isEmpty()) {
				play.Logger.info("Not metadata to enrich " + node.getPid());
				return "Not metadata to enrich " + node.getPid();
			}
			List<Statement> enrichStatements = new ArrayList<>();
			enrichAll(node, metadata, enrichStatements);
			metadata = RdfUtils.replaceTriples(enrichStatements, metadata);
			new Modify().updateMetadata1(node, metadata);

		} catch (Exception e) {
			play.Logger.warn(e.getMessage());
			play.Logger.debug("", e);
			return "Enrichment of " + node.getPid() + " partially failed !\n"
					+ e.getMessage();
		}
		return "Enrichment of " + node.getPid() + " succeeded!";
	}

	private static void enrichAll(Node node, String metadata,
			List<Statement> enrichStatements) {
		enrichInstitution(node, enrichStatements);
		enrichAllUris(node, metadata, enrichStatements);
	}

	private static void enrichInstitution(Node node,
			List<Statement> enrichStatements) {
		try {
			play.Logger.info("Enrich " + node.getPid() + " with institution.");
			List<Statement> institutions = findInstitution(node);
			enrichStatements.addAll(institutions);
		} catch (Exception e) {
			play.Logger.debug("", e);
		}
	}

	private static List<Statement> findInstitution(Node node) {
		List<Statement> result = new ArrayList<Statement>();
		try {
			String alephid = new Read().getIdOfParallelEdition(node);
			String uri = Globals.lobidHbz01 + alephid + "/about?format=source";
			play.Logger.info("GET " + uri);
			try (InputStream in =
					RdfUtils.urlToInputStream(new URL(uri), "application/xml")) {
				String gndEndpoint = "http://d-nb.info/gnd/";
				List<Element> institutionHack = XmlUtils.getElements(
						"//datafield[@tag='078' and @ind1='r' and @ind2='1']/subfield", in,
						null);

				for (Element el : institutionHack) {
					String marker = el.getTextContent();
					if (!marker.contains("ellinet"))
						continue;
					if (!marker.contains("GND"))
						continue;
					String gndId = gndEndpoint
							+ marker.replaceFirst(".*ellinet.*GND:.*\\([^)]*\\)", "");
					if (gndId.endsWith("16269969-4")) {
						gndId = gndEndpoint + "2006655-7";
					}
					play.Logger.trace("Add data from " + gndId);
					ValueFactory v = RdfUtils.valueFactory;
					Statement link = v.createStatement(v.createIRI(node.getPid()),
							v.createIRI("http://dbpedia.org/ontology/institution"),
							v.createIRI(gndId));
					result.add(link);
					result.add(getLabelStatement(gndId));
				}

			}
		} catch (Exception e) {
			play.Logger.info("No institution found for " + node.getPid());

		}
		play.Logger.info("ADD to collection: " + result);
		return result;
	}

	private static void enrichAllUris(Node node, String metadata,
			List<Statement> enrichStatements) {
		try {
			List<String> allUris = findAllUris(metadata);
			for (String uri : allUris) {
				enrich(uri, enrichStatements);
			}
		} catch (Exception e) {
			play.Logger.debug("", e);
		}
	}

	private static void enrich(String uri, List<Statement> enrichStatements) {
		try {
			Statement label = getLabelStatement(uri);
			play.Logger
					.info("Add label " + label.getObject().stringValue() + " to " + uri);
			enrichStatements.add(label);
		} catch (Exception e) {
			play.Logger.debug("", e);
		}
	}

	private static Statement getLabelStatement(String uri) {
		String prefLabel = MyEtikettMaker.getLabelFromEtikettWs(uri);
		ValueFactory v = RdfUtils.valueFactory;
		Statement newS = v.createStatement(v.createIRI(uri),
				v.createIRI(PREF_LABEL),
				v.createLiteral(Normalizer.normalize(prefLabel, Normalizer.Form.NFKC)));
		return newS;
	}

	private static List<String> findAllUris(String metadata) {
		HashMap<String, String> result = new HashMap<>();
		try (
				RepositoryConnection con = RdfUtils.readRdfInputStreamToRepository(
						new ByteArrayInputStream(metadata.getBytes()), RDFFormat.NTRIPLES);
				RepositoryResult<Statement> statements =
						con.getStatements(null, null, null);) {
			while (statements.hasNext()) {
				Statement st = statements.next();
				Value o = st.getObject();
				if (o instanceof IRI) {
					String objectUri = o.stringValue();
					result.put(objectUri, objectUri);
				}
			}
		}
		return new Vector<>(result.keySet());
	}

}
