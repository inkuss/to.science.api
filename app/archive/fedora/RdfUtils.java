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
package archive.fedora;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.impl.TreeModel;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import helper.HttpArchiveException;
import models.Link;
import models.RdfResource;

/**
 * @author Jan Schnasse schnasse@hbz-nrw.de
 * 
 */
public class RdfUtils {

	final static Logger logger = LoggerFactory.getLogger(RdfUtils.class);
	public final static ValueFactory valueFactory =
			SimpleValueFactory.getInstance();

	/**
	 * @param url the url to read from
	 * @param inf the rdf format
	 * @param outf the rdf output format
	 * @param accept the accept header for the url
	 * @return a rdf string
	 */
	public static String readRdfToString(URL url, RDFFormat inf, RDFFormat outf,
			String accept) {
		try {
			Collection<Statement> myGraph = null;
			myGraph = readRdfToGraph(url, inf, accept);
			return graphToString(myGraph, outf);
		} catch (Exception e) {
			play.Logger.warn("", e);
			return "";
		}
	}

	/**
	 * Transforms a graph to a string.
	 * 
	 * @param myGraph a sesame rdf graph
	 * @param outf the expected output format
	 * @return a rdf string
	 */
	public static String graphToString(Collection<Statement> myGraph,
			RDFFormat outf) {
		StringWriter out = new StringWriter();
		RDFWriter writer = Rio.createWriter(outf, out);
		try {
			writer.startRDF();
			for (Statement st : myGraph) {
				writer.handleStatement(st);
			}
			writer.endRDF();
		} catch (RDFHandlerException e) {
			play.Logger.warn(e.toString());
			throw new RdfException(e);
		}
		play.Logger.trace("Returning " + out.getBuffer().toString());
		return out.getBuffer().toString();
	}

	/**
	 * @param in a rdf input stream
	 * @param inf the rdf format of the input stream
	 * @param outf the output format
	 * @param baseUrl usually the url of the resource
	 * @return a string representation
	 */
	public static String readRdfToString(InputStream in, RDFFormat inf,
			RDFFormat outf, String baseUrl) {
		Collection<Statement> myGraph = null;
		myGraph = readRdfToGraph(in, inf, baseUrl);
		return graphToString(myGraph, outf);
	}

	/**
	 * @param url A url to read from
	 * @param inf the rdf format of the url's data
	 * @param accept the accept header
	 * @return a Graph with the rdf
	 * @throws IOException
	 */
	public static Collection<Statement> readRdfToGraph(URL url, RDFFormat inf,
			String accept) throws IOException {
		try (InputStream in = urlToInputStream(url, accept)) {
			return readRdfToGraph(in, inf, url.toString());
		}
	}

	public static Collection<Statement> readRdfToGraphAndFollowSameAs(URL url,
			RDFFormat inf, String accept) throws IOException {
		Collection<Statement> graph = null;
		try (InputStream in = urlToInputStream(url, accept)) {
			graph = readRdfToGraph(in, inf, url.toString());
			String sameAsTarget = getSameAsTarget(graph);
			play.Logger.info("GET " + sameAsTarget);
			if (sameAsTarget != null && sameAsTarget.contains("lobid")) {
				play.Logger.info("GET " + sameAsTarget);
				graph.addAll(readRdfToGraph(new URL(sameAsTarget), inf, accept));
			}
		}
		return graph;
	}

	private static String getSameAsTarget(Collection<Statement> graph) {
		Iterator<Statement> statements = graph.iterator();
		while (statements.hasNext()) {
			Statement curStatement = statements.next();
			String pred = curStatement.getPredicate().stringValue();
			String obj = curStatement.getObject().stringValue();
			if ("http://schema.org/sameAs".equals(pred)) {
				return obj;
			}
		}
		return null;
	}

	/**
	 * Dise Methode ermittelt eine Alma-Id aus einem RDF-Graphen
	 * 
	 * @author Ingolf Kuss (hbz)
	 * @since 2024-02-01
	 * @param graph der Graph mit den Metadaten der Ressource als RDF-Statements
	 *          (Subjekt, Prädikat, Objekt)
	 * @param alephid die Aleph-ID zu der Ressource
	 * @return die Alma-ID der Ressource
	 */
	public static String getAlmaId(Collection<Statement> graph, String alephid) {

		if ((alephid != null) && (alephid.length() > 2)
				&& !(alephid.startsWith("HT") || alephid.startsWith("TT")
						|| alephid.startsWith("ht") || alephid.startsWith("tt"))) {
			// "alephid" ist schon die alma-ID !
			play.Logger.debug("Found Alma-ID: " + alephid);
			return alephid;
		}
		// Suche nach einer Alma-ID in den Metadaten (es muss ein Subjekt sein):
		Iterator<Statement> statements = graph.iterator();
		while (statements.hasNext()) {
			Statement curStatement = statements.next();
			String subj = curStatement.getSubject().stringValue();
			if (subj.startsWith("http://lobid.org/resource")) {
				String almaid =
						subj.replaceFirst("http[s]*://lobid.org/resource[s]*/", "");
				almaid = almaid.replaceAll("#.*", "");
				if (!(almaid.startsWith("HT") || almaid.startsWith("TT")
						|| almaid.startsWith("ht") || almaid.startsWith("tt"))) {
					play.Logger.debug("Found Alma-ID: " + almaid);
					return almaid;
				}
			}
		}
		play.Logger.warn("Alma-ID not found! Returning alephid: " + alephid);
		return alephid;
	}

	public static InputStream urlToInputStream(URL url, String accept) {
		HttpURLConnection con = null;
		InputStream inputStream = null;
		try {
			con = (HttpURLConnection) url.openConnection();
			con.setConnectTimeout(15000);
			con.setRequestProperty("User-Agent", "Regal Webservice");
			con.setReadTimeout(15000);
			con.setRequestProperty("Accept", accept);
			con.connect();
			int responseCode = con.getResponseCode();
			play.Logger
					.debug("Request for " + accept + " from " + url.toExternalForm());
			play.Logger
					.debug("Get a " + responseCode + " from " + url.toExternalForm());
			if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
					|| responseCode == HttpURLConnection.HTTP_MOVED_TEMP
					|| responseCode == 307 || responseCode == 303) {
				String redirectUrl = con.getHeaderField("Location");
				play.Logger.debug("redirectUrl:" + redirectUrl);
				try {
					URL newUrl = new URL(redirectUrl);
					play.Logger.debug("Redirect to Location: " + newUrl);
					return urlToInputStream(newUrl, accept);
				} catch (MalformedURLException e) {
					URL newUrl =
							new URL(url.getProtocol() + "://" + url.getHost() + redirectUrl);
					play.Logger.debug("Redirect to Location: " + newUrl);
					return urlToInputStream(newUrl, accept);
				}
			}
			inputStream = con.getInputStream();
			play.Logger.debug("Got input stream");
			return inputStream;
		} catch (SocketTimeoutException e) {
			play.Logger.warn("Timeout on " + url);
			throw new UrlConnectionException(e);
		} catch (IOException e) {
			throw new UrlConnectionException(e);
		}

	}

	/**
	 * @param inputStream an Input stream containing rdf data
	 * @param inf the rdf format
	 * @param baseUrl see sesame docu
	 * @return a Collection<Statement> representing the rdf in the input stream
	 */
	public static Collection<Statement> readRdfToGraph(InputStream inputStream,
			RDFFormat inf, String baseUrl) {
		try {
			RDFParser rdfParser = Rio.createParser(inf);
			Collection<Statement> myGraph = new TreeModel();
			StatementCollector collector = new StatementCollector(myGraph);
			rdfParser.setRDFHandler(collector);
			rdfParser.parse(inputStream, baseUrl);
			return myGraph;
		} catch (Exception e) {
			throw new RdfException(e);
		}
	}

	/**
	 * @param in rdf data in RDFFormat.N3
	 * @return all subjects without info:fedora/ at the beginning
	 */
	public static List<String> getFedoraSubject(InputStream in) {
		Vector<String> pids = new Vector<>();
		String findpid = null;
		try {
			RepositoryConnection con =
					RdfUtils.readRdfInputStreamToRepository(in, RDFFormat.N3);

			RepositoryResult<Statement> statements =
					con.getStatements(null, null, null, true);

			while (statements.hasNext()) {
				Statement st = statements.next();
				findpid = st.getSubject().stringValue().replace("info:fedora/", "");
				pids.add(findpid);
			}
		} catch (Exception e) {
			throw new RdfException(e);
		}
		return pids;
	}

	/**
	 * @param stream the stream contains triples in RDFFormat.N3
	 * @return a List of objects without info:fedora/ at the beginning
	 */
	public static List<String> getFedoraObjects(InputStream stream) {
		Vector<String> findpids = new Vector<String>();
		try {
			RepositoryConnection con =
					RdfUtils.readRdfInputStreamToRepository(stream, RDFFormat.N3);

			RepositoryResult<Statement> statements =
					con.getStatements(null, null, null, true);

			while (statements.hasNext()) {
				Statement st = statements.next();
				findpids.add(st.getObject().stringValue().replace("info:fedora/", ""));

			}
		} catch (Exception e) {
			throw new RdfException(e);
		}
		return findpids;
	}

	/**
	 * @param metadata a Url with NTRIPLES metadata
	 * @param baseUrl a base Url for relative uris
	 * @return all rdf statements
	 */
	public static RepositoryResult<Statement> getStatements(String metadata,
			String baseUrl) {
		try {
			RepositoryConnection con = RdfUtils.readRdfStringToRepository(metadata,
					RDFFormat.NTRIPLES, baseUrl);
			RepositoryResult<Statement> statements =
					con.getStatements(null, null, null, true);
			return statements;
		} catch (Exception e) {
			throw new RdfException(e);
		}

	}

	/**
	 * @param pid a pid
	 * @param links all links
	 * @return a valid relsExt datastream as string
	 */
	public static String getFedoraRelsExt(String pid, List<Link> links) {
		RepositoryConnection con = null;
		SailRepository myRepository = new SailRepository(new MemoryStore());
		try {
			myRepository.initialize();
			con = myRepository.getConnection();
			addStatements(pid, links, con, myRepository);
			return writeStatements(con, RDFFormat.RDFXML);
		} catch (RepositoryException e) {
			throw new RdfException(e);
		}

	}

	private static String writeStatements(RepositoryConnection con,
			RDFFormat outf) {
		StringWriter out = new StringWriter();
		RDFWriter writer = Rio.createWriter(outf, out);
		String result = null;
		try {
			writer.startRDF();
			RepositoryResult<Statement> statements =
					con.getStatements(null, null, null, false);

			while (statements.hasNext()) {
				Statement statement = statements.next();
				writer.handleStatement(statement);
			}
			writer.endRDF();
			result = out.toString();

		} catch (RDFHandlerException e) {
			throw new RdfException(e);
		} catch (RepositoryException e) {
			throw new RdfException(e);
		}
		return result;
	}

	private static void addStatements(String pid, List<Link> links,
			RepositoryConnection con, SailRepository myRepository)
			throws RepositoryException {

		ValueFactory f = myRepository.getValueFactory();
		IRI subject = f.createIRI("info:fedora/" + pid);
		for (Link link : links) {
			IRI predicate = f.createIRI(link.getPredicate());
			if (link.getObject() == null || link.getObject().isEmpty())
				continue;
			if (link.isLiteral()) {
				Literal object = f.createLiteral(link.getObject());
				con.add(subject, predicate, object);
			} else {
				try {
					IRI object = f.createIRI(link.getObject());
					con.add(subject, predicate, object);
				} catch (IllegalArgumentException e) {
					logger.debug("", e);
				}
			}
		}
	}

	/**
	 * @param subject find triples with this subject
	 * @param predicate find triples with this predicate
	 * @param metadata the metadata to search in
	 * @param inf format of the rdf data
	 * @param accept accept header for the url
	 * @return a list of rdf objects
	 */
	public static List<String> findRdfObjects(String subject, String predicate,
			String metadata, RDFFormat inf) {
		RepositoryConnection con = RdfUtils.readRdfInputStreamToRepository(
				new ByteArrayInputStream(metadata.getBytes(StandardCharsets.UTF_8)),
				inf);
		return findRdfObjects(subject, predicate, con);
	}

	private static List<String> findRdfObjects(String subject, String predicate,
			RepositoryConnection con) {
		List<String> list = new Vector<String>();
		TupleQueryResult result = null;
		try {
			String queryString = "SELECT  x, y FROM {x} <" + predicate + "> {y}";
			TupleQuery tupleQuery =
					con.prepareTupleQuery(QueryLanguage.SERQL, queryString);
			result = tupleQuery.evaluate();
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				Value valueOfY = bindingSet.getValue("y");
				list.add(valueOfY.stringValue());
			}
			return list;
		} catch (Exception e) {
			throw new RdfException(e);
		}
	}

	private static RepositoryConnection readRdfUrlToRepository(URL rdfUrl,
			RDFFormat inf) {
		RepositoryConnection con = null;
		try {
			Repository myRepository = new SailRepository(new MemoryStore());
			myRepository.initialize();
			con = myRepository.getConnection();
			String baseURI = rdfUrl.toString();
			con.add(rdfUrl, baseURI, inf);
			return con;
		} catch (Exception e) {
			throw new RdfException(e);
		}
	}

	private static RepositoryConnection readRdfStringToRepository(String str,
			RDFFormat inf, String baseUrl) {
		RepositoryConnection con = null;
		try {
			Repository myRepository = new SailRepository(new MemoryStore());
			myRepository.initialize();
			con = myRepository.getConnection();
			con.add(new ByteArrayInputStream(str.getBytes("utf-8")), baseUrl, inf);
			return con;
		} catch (Exception e) {
			throw new RdfException(e);
		}
	}

	public static RepositoryConnection readRdfInputStreamToRepository(
			InputStream is, RDFFormat inf) {
		RepositoryConnection con = null;
		try {

			Repository myRepository = new SailRepository(new MemoryStore());
			myRepository.initialize();
			con = myRepository.getConnection();
			String baseURI = "";
			con.add(is, baseURI, inf);
			return con;
		} catch (Exception e) {
			throw new RdfException(e);
		}
	}

	/**
	 * Adds the given statement to the stream and removes all statements with same
	 * subject and predicate
	 * 
	 * @param subject rdf subject
	 * @param predicate rdf predicate
	 * @param object rdf object
	 * @param isLiteral true if the object is a literl
	 * @param metadata the metadata as String
	 * @return modified Metadata
	 */
	public static String replaceTriple(String subject, String predicate,
			String object, boolean isLiteral, final String metadata) {
		try {
			InputStream is = new ByteArrayInputStream(metadata.getBytes("UTF-8"));
			RepositoryConnection con =
					readRdfInputStreamToRepository(is, RDFFormat.NTRIPLES);
			ValueFactory f = con.getValueFactory();
			IRI s = f.createIRI(subject);
			IRI p = f.createIRI(predicate);
			Value o = null;
			if (!isLiteral) {
				o = f.createIRI(object);
			} else {
				o = f.createLiteral(object);
			}
			RepositoryResult<Statement> statements =
					con.getStatements(null, null, null, true);
			while (statements.hasNext()) {
				Statement st = statements.next();
				if (st.getSubject().stringValue().equals(subject)
						&& st.getPredicate().stringValue().equals(predicate)) {
					con.remove(st);
				}
			}

			con.add(s, p, o);
			return writeStatements(con, RDFFormat.NTRIPLES);
		} catch (RepositoryException e) {
			throw new RdfException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RdfException(e);
		}

	}

	public static String replaceTriples(List<Statement> graph,
			final String metadata, RDFFormat rdfFormat) {
		try {
			InputStream is = new ByteArrayInputStream(metadata.getBytes("UTF-8"));
			RepositoryConnection con = readRdfInputStreamToRepository(is, rdfFormat);
			for (Statement st : graph) {
				RepositoryResult<Statement> statements =
						con.getStatements(null, null, null, true);
				while (statements.hasNext()) {
					Statement statement = statements.next();
					play.Logger.trace("subject in metadata:" + statement.getSubject());
					play.Logger
							.trace("predicate in metadata:" + statement.getPredicate());
					play.Logger.trace("object in metadata:" + statement.getObject());
					if (statement.getSubject().equals(st.getSubject())
							&& statement.getPredicate().equals(st.getPredicate())) {
						con.remove(statement);
						play.Logger.trace("Statement entfernt.");
					}
				}
				play.Logger.trace("subject in graph:" + st.getSubject());
				play.Logger.trace("predicate in graph:" + st.getPredicate());
				play.Logger.trace("object in graph:" + st.getObject());
				con.add(st);
				play.Logger.trace("Statement hinzugefügt.");
			}
			return writeStatements(con, rdfFormat);
		} catch (RepositoryException e) {
			throw new RdfException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RdfException(e);
		}
	}

	/**
	 * @param subject the triples subject
	 * @param predicate the triples predicate
	 * @param object the triples object
	 * @param metadata ntriple string
	 * @return true if the metadata string contains the triple
	 */
	public static boolean hasTriple(String subject, String predicate,
			String metadata) {
		try {
			RepositoryConnection con = getConnection(metadata);
			RepositoryResult<Statement> statements =
					con.getStatements(null, null, null, true);
			while (statements.hasNext()) {
				Statement st = statements.next();
				if (st.getSubject().stringValue().equals(subject)
						&& st.getPredicate().stringValue().equals(predicate)) {
					return true;
				}
			}
		} catch (Exception e) {
			return false;
		}
		return false;
	}

	private static RepositoryConnection getConnection(String metadata)
			throws UnsupportedEncodingException, IOException {
		try (
				InputStream is = new ByteArrayInputStream(metadata.getBytes("UTF-8"))) {
			RepositoryConnection con =
					readRdfInputStreamToRepository(is, RDFFormat.NTRIPLES);
			return con;
		}
	}

	/**
	 * @param subject the triples subject
	 * @param predicate the triples predicate
	 * @param object the triples object
	 * @param isLiteral true, if object is a literal
	 * @param metadata ntriple rdf-string to add the triple
	 * @param format format of in and out
	 * @return the string together with the new triple
	 */
	public static String addTriple(String subject, String predicate,
			String object, boolean isLiteral, String metadata, RDFFormat format) {
		try {
			RepositoryConnection con = null;
			if (metadata != null) {
				InputStream is = new ByteArrayInputStream(metadata.getBytes("UTF-8"));
				con = readRdfInputStreamToRepository(is, format);
			} else {
				Repository myRepository = new SailRepository(new MemoryStore());
				myRepository.initialize();
				con = myRepository.getConnection();
			}
			ValueFactory f = con.getValueFactory();
			IRI s = f.createIRI(subject);
			IRI p = f.createIRI(predicate);
			Value o = null;
			if (!isLiteral) {
				o = f.createIRI(object);
			} else {
				o = f.createLiteral(object);
			}
			con.add(s, p, o);
			return writeStatements(con, format);
		} catch (RepositoryException e) {
			throw new RdfException(e);
		} catch (UnsupportedEncodingException e) {
			throw new RdfException(e);
		}
	}

	/**
	 * @param metadata n-triple
	 */
	public static void validate(String metadata) {
		try {
			InputStream is = new ByteArrayInputStream(metadata.getBytes("UTF-8"));
			readRdfInputStreamToRepository(is, RDFFormat.NTRIPLES);
		} catch (UnsupportedEncodingException e) {
			throw new RdfException(e);
		}
	}

	/**
	 * @param stream rdf data
	 * @param format format of rdf data
	 * @param uri uri that is described by data
	 * @return a RdfResource
	 */
	public static RdfResource createRdfResource(InputStream stream,
			RDFFormat format, String uri) {
		try {

			RepositoryConnection con = readRdfInputStreamToRepository(stream, format);
			RepositoryResult<Statement> statements =
					con.getStatements(null, null, null, true);
			Map<String, RdfResource> subjects = fetchSubjects(statements);
			RdfResource me = subjects.get(uri);
			for (Link l : me.getLinks()) {
				if (!l.getObject().equals(uri) && !l.isLiteral()
						&& subjects.containsKey(l.getObject())) {
					RdfResource c = subjects.get(l.getObject());
					me.addResolvedLink(c);
				}
			}
			return me;
		} catch (RepositoryException e) {
			throw new RdfException(e);
		} catch (NullPointerException e) {
			return new RdfResource();
		}
	}

	private static Map<String, RdfResource> fetchSubjects(
			RepositoryResult<Statement> statements) throws RepositoryException {

		Map<String, RdfResource> subjs = new HashMap<String, RdfResource>();
		while (statements.hasNext()) {
			Statement st = statements.next();
			Resource subject = st.getSubject();
			if (subjs.containsKey(subject.stringValue())) {

				subjs.get(subject.stringValue())
						.addLink((new Link(st.getPredicate().stringValue(),
								st.getObject().stringValue(),
								(st.getObject() instanceof Literal))));
			} else {
				RdfResource r = new RdfResource();

				r.addLink(new Link(st.getPredicate().stringValue(),
						st.getObject().stringValue(), (st.getObject() instanceof Literal)));
				r.setUri(subject.stringValue());
				subjs.put(subject.stringValue(), r);
			}

		}
		return subjs;
	}

	public static Collection<Statement> rewriteSubject(String lobidUri,
			String pid, Collection<Statement> graph) {
		Collection<Statement> result = new TreeModel();
		Iterator<Statement> statements = graph.iterator();
		while (statements.hasNext()) {
			Statement curStatement = statements.next();
			String subj = curStatement.getSubject().stringValue();
			Resource pred = curStatement.getPredicate();
			Value obj = curStatement.getObject();
			if (subj.equals(lobidUri)) {
				result.add(valueFactory.createStatement(valueFactory.createIRI(pid),
						valueFactory.createIRI(pred.stringValue()), obj));
			} else {
				result.add(curStatement);
			}
		}
		return result;
	}

	public static String urlEncode(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8").replace("+", "%20");
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	public static String urlDecode(String str) {
		try {
			return URLDecoder.decode(str, "UTF-8");
		} catch (Exception e) {
			throw new HttpArchiveException(500, e);
		}
	}

	public static Collection<Statement> deletePredicateFromRepo(
			RepositoryConnection con, String pred) {
		String queryString = "DELETE WHERE{?s <" + pred + "> ?o .} ";
		play.Logger.debug(queryString);
		con.prepareUpdate(QueryLanguage.SPARQL, queryString).execute();
		return Iterations.asList(con.getStatements(null, null, null, true));
	}
}
