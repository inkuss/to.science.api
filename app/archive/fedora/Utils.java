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

import static archive.fedora.FedoraVocabulary.CM_CONTENTMODEL;
import static archive.fedora.FedoraVocabulary.DS_COMPOSITE_MODEL;
import static archive.fedora.FedoraVocabulary.DS_COMPOSITE_MODEL_URI;
import static archive.fedora.FedoraVocabulary.DS_INPUTSPEC;
import static archive.fedora.FedoraVocabulary.DS_INPUTSPEC_URI;
import static archive.fedora.FedoraVocabulary.DS_METHODMAP;
import static archive.fedora.FedoraVocabulary.DS_METHODMAP_URI;
import static archive.fedora.FedoraVocabulary.DS_METHODMAP_WSDL;
import static archive.fedora.FedoraVocabulary.DS_METHODMAP_WSDL_URI;
import static archive.fedora.FedoraVocabulary.DS_WSDL;
import static archive.fedora.FedoraVocabulary.DS_WSDL_URI;
import static archive.fedora.FedoraVocabulary.INFO_NAMESPACE;
import static archive.fedora.FedoraVocabulary.IS_PART_OF;
import static archive.fedora.FedoraVocabulary.REL_HAS_MODEL;
import static archive.fedora.FedoraVocabulary.REL_HAS_SERVICE;
import static archive.fedora.FedoraVocabulary.REL_IS_CONTRACTOR_OF;
import static archive.fedora.FedoraVocabulary.REL_IS_DEPLOYMENT_OF;
import static archive.fedora.FedoraVocabulary.SDEF_CONTENTMODEL;
import static archive.fedora.FedoraVocabulary.SDEP_CONTENTMODEL;
import static archive.fedora.Vocabulary.REL_ACCESS_SCHEME;
import static archive.fedora.Vocabulary.REL_CATALOG_ID;
import static archive.fedora.Vocabulary.REL_CONTENT_TYPE;
import static archive.fedora.Vocabulary.REL_CREATED_BY;
import static archive.fedora.Vocabulary.REL_HAS_DOI;
import static archive.fedora.Vocabulary.REL_HAS_URN;
import static archive.fedora.Vocabulary.REL_IMPORTED_FROM;
import static archive.fedora.Vocabulary.REL_IS_NODE_TYPE;
import static archive.fedora.Vocabulary.REL_LAST_MODIFIED_BY;
import static archive.fedora.Vocabulary.REL_LEGACY_ID;
import static archive.fedora.Vocabulary.REL_NAME;
import static archive.fedora.Vocabulary.REL_PUBLISH_SCHEME;
import static archive.fedora.FedoraVocabulary.DATA_PROVIDER;

import archive.fedora.AddDatastream;
import helper.HttpArchiveException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import models.Link;
import models.Node;
import models.Transformer;
import views.Title;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.request.AddRelationship;
import com.yourmediashelf.fedora.client.request.FindObjects;
import com.yourmediashelf.fedora.client.request.GetDatastreamDissemination;
import com.yourmediashelf.fedora.client.request.Ingest;
import com.yourmediashelf.fedora.client.request.ListDatastreams;
import com.yourmediashelf.fedora.client.request.ModifyDatastream;
import com.yourmediashelf.fedora.client.request.PurgeObject;
import com.yourmediashelf.fedora.client.request.PurgeRelationship;
import com.yourmediashelf.fedora.client.request.Upload;
import com.yourmediashelf.fedora.client.response.FedoraResponse;
import com.yourmediashelf.fedora.client.response.FindObjectsResponse;
import com.yourmediashelf.fedora.client.response.ListDatastreamsResponse;
import com.yourmediashelf.fedora.client.response.UploadResponse;
import com.yourmediashelf.fedora.generated.access.DatastreamType;

/**
 * The utils class provides commonly used "low-level-methods" to the
 * FedoraFacade.
 * 
 * @author Jan Schnasse schnasse@hbz-nrw.de
 * 
 */
public class Utils {

	@SuppressWarnings({ "javadoc", "serial" })
	public class ContentModelException extends RuntimeException {
		public ContentModelException(Throwable e) {
			super(e);
		}
	}

	@SuppressWarnings({ "javadoc", "serial" })
	public class NoPidFoundException extends RuntimeException {

		public NoPidFoundException() {
		}

		public NoPidFoundException(String arg0) {
			super(arg0);
		}

		public NoPidFoundException(Throwable arg0) {
			super(arg0);
		}

		public NoPidFoundException(String arg0, Throwable arg1) {
			super(arg0, arg1);
		}

	}

	ContentModelBuilder cmBuilder = new ContentModelBuilder();
	private String user = null;

	/**
	 * @param host The fedora host.
	 * @param user A valid fedora user.
	 */
	public Utils(String host, String user) {
		this.user = user;
	}

	/**
	 * Prefixes a pid with FEDORA_INFO_NAMESPACE
	 * 
	 * @param pid The pid that must be prefixed
	 * @return FEDORA_INFO_NAMESPACE + pid
	 */
	public String addUriPrefix(final String pid) {

		if (pid.contains(INFO_NAMESPACE.toString()))
			return pid;
		String pred = INFO_NAMESPACE.toString() + pid;
		return pred;
	}

	void purgeRelationships(String pid, List<Link> list) {
		for (Link link : list) {
			System.out.println("PURGE: " + addUriPrefix(pid) + " <"
					+ link.getPredicate() + "> " + link.getObject());
			try {
				new PurgeRelationship(pid).subject(addUriPrefix(pid))
						.predicate(link.getPredicate())
						.object(link.getObject(), link.isLiteral()).execute();
			} catch (FedoraClientException e) {
				play.Logger.warn(e.getMessage());
				play.Logger.debug("", e);
			}
		}
	}

	void addRelationships(String pid, List<Link> links) {

		if (links != null)
			for (Link link : links) {

				try {
					new AddRelationship(pid).predicate(link.getPredicate())
							.object(link.getObject(), link.isLiteral()).execute();
				} catch (Exception e) {
					try {
						new AddRelationship(pid).predicate(link.getPredicate())
								.object(link.getObject(), true).execute();
					} catch (Exception e2) {

						play.Logger.warn(e2.getMessage());
						play.Logger.debug("", e2);
					}

				}

			}

	}

	/**
	 * @param pid The pid of the fedora object.
	 * @param datastreamId The datastream ID of the datastream
	 * @return true if the datastream exists, false if not.
	 */
	public boolean dataStreamExists(String pid, String datastreamId) {
		try {

			ListDatastreamsResponse response = new ListDatastreams(pid).execute();

			for (DatastreamType ds : response.getDatastreams()) {
				if (ds.getDsid().compareTo(datastreamId) == 0)
					return true;
			}

		} catch (FedoraClientException e) {
			return false;
		}
		return false;
	}

	String removeUriPrefix(String pred) {
		String pid = pred.replace(INFO_NAMESPACE, "");

		return pid;
	}

	void createRelsExt(Node node) {
		String pid = node.getPid();
		if (!dataStreamExists(pid, "RELS-EXT")) {
			createFedoraXmlForRelsExt(pid);
		}
		List<Link> links = node.getRelsExt();
		createRelsExt(pid, links);
	}

	boolean nodeExists(String pid) {
		try {

			FindObjectsResponse response =
					new FindObjects().terms(pid).pid().execute();
			for (String p : response.getPids()) {
				if (p.compareTo(pid) == 0)
					return true;
			}

		} catch (Exception e) {
			return false;
		}
		return false;
	}

	/**
	 * Description: Allows to ingest a local file as managed datastream of the
	 * object
	 * </p>
	 * 
	 * @param pid of the object
	 * @param datastreamID to identify the datastream
	 * @param fileLocation to specify the managed content of the datastream
	 * @param mimeType of the uploaded file
	 */
	void createManagedStream(Node node) {

		try {

			File file = new File(node.getUploadFile());
			UploadResponse response = new Upload(file).execute();

			String location = response.getUploadLocation();
			String label = node.getFileLabel();
			if (label == null || label.isEmpty())
				label = file.getName();
			new AddDatastream(node.getPid(), "data").versionable(true).dsLabel(label)
					.dsState("A").controlGroup("M").mimeType(node.getMimeType())
					.dsLocation(location).execute();

		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	void createUnManagedStream(Node node) {
		try {
			String location = new URL(node.getUploadFile()).toString();
			String label = node.getFileLabel();
			if (label == null || label.isEmpty())
				label = location.substring(location.lastIndexOf('/'));
			((AddDatastream) new AddDatastream(node.getPid(), "data")
					.checksumType("DISABLED").versionable(true).dsLabel(label)
					.dsState("A").controlGroup("E").mimeType(node.getMimeType())
					.dsLocation(location)).dsSize(node.getFileSizeAsString()).execute();
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		} catch (Exception e) {
			throw new HttpArchiveException(400, e);
		}

	}

	@SuppressWarnings("javadoc")
	public void createSeqStream(Node node) {
		try {
			Upload request = new Upload(new File(node.getSeqFile()));
			UploadResponse response = request.execute();
			String location = response.getUploadLocation();
			new AddDatastream(node.getPid(), "seq").versionable(true).dsState("A")
					.dsLabel("json array to define the order of child objects")
					.controlGroup("M").mimeType("application/json").dsLocation(location)
					.execute();
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	@SuppressWarnings("javadoc")
	public void createConfStream(Node node) {
		try {
			Upload request = new Upload(new File(node.getConfFile()));
			UploadResponse response = request.execute();
			String location = response.getUploadLocation();
			new AddDatastream(node.getPid(), "conf").versionable(true).dsState("A")
					.dsLabel("json file to configure webharvests").controlGroup("M")
					.mimeType("application/json").dsLocation(location).execute();
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	@SuppressWarnings("javadoc")
	public void createUrlHistStream(Node node) {
		try {
			Upload request = new Upload(new File(node.getUrlHistFile()));
			UploadResponse response = request.execute();
			String location = response.getUploadLocation();
			new AddDatastream(node.getPid(), "urlHist").versionable(true).dsState("A")
					.dsLabel("json file to keep track of a webpage's URL changes")
					.controlGroup("M").mimeType("application/json").dsLocation(location)
					.execute();
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	/**
	 * Sets a datastream objectTimestamp to the node
	 * 
	 * @param node the node to update
	 */
	public void updateObjectTimestampStream(Node node) {
		try {
			File file = new File(node.getObjectTimestampFile());
			if (dataStreamExists(node.getPid(), "objectTimestamp")) {
				new ModifyDatastream(node.getPid(), "objectTimestamp").versionable(true)
						.dsState("A").dsLabel("a simple timestamp").mimeType("text/plain")
						.controlGroup("M").content(file).execute();
			} else {
				new AddDatastream(node.getPid(), "objectTimestamp").versionable(true)
						.dsState("A").mimeType("text/plain").dsLabel("a simple timestamp")
						.content(file).controlGroup("M").execute();
			}
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	/**
	 * creates a objectTimestamp on node
	 * 
	 * @param node the node
	 */
	public void createObjectTimestampStream(Node node) {
		try {
			Upload request = new Upload(new File(node.getObjectTimestampFile()));
			UploadResponse response = request.execute();
			String location = response.getUploadLocation();
			new AddDatastream(node.getPid(), "objectTimestamp").versionable(true)
					.dsState("A").dsLabel("a simple timestamp").controlGroup("M")
					.mimeType("text/plain").dsLocation(location).execute();
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	void updateManagedStream(Node node) {

		try {
			File file = new File(node.getUploadFile());
			if (dataStreamExists(node.getPid(), "data")) {
				new ModifyDatastream(node.getPid(), "data").versionable(true)
						.dsState("A").dsLabel(node.getFileLabel())
						.mimeType(node.getMimeType()).controlGroup("M").content(file)
						.execute();
			} else {
				new AddDatastream(node.getPid(), "data").versionable(true).dsState("A")
						.mimeType(node.getMimeType()).dsLabel(node.getFileLabel())
						.content(file).controlGroup("M").execute();
			}
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	void updateUnManagedStream(Node node) {
		try {
			play.Logger.debug("Update unmanaged datastream of " + node.getPid());
			String localpath = node.getUploadFile();

			if (dataStreamExists(node.getPid(), "data")) {
				new ModifyDatastream(node.getPid(), "data").checksumType("DISABLED")
						.versionable(true).dsState("A").dsLabel(node.getFileLabel())
						.dsLocation(localpath).mimeType(node.getMimeType())
						.controlGroup("E").execute();
			} else {
				play.Logger.debug("Add datastream " + node.getPid()
						+ "/data with unmanaged content " + localpath);
				play.Logger.debug("MimeType: " + node.getMimeType());
				play.Logger.debug("FileLabel: " + node.getFileLabel());
				((AddDatastream) new AddDatastream(node.getPid(), "data")
						.checksumType("DISABLED").versionable(true).dsState("A")
						.mimeType(node.getMimeType()).dsLabel(node.getFileLabel())
						.dsLocation(localpath)).dsSize(node.getFileSizeAsString())
								.controlGroup("E").execute();
			}
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		} catch (Exception e) {
			throw new HttpArchiveException(400, e);
		}
	}

	@SuppressWarnings("javadoc")
	public void updateSeqStream(Node node) {
		try {
			File file = new File(node.getSeqFile());
			if (dataStreamExists(node.getPid(), "seq")) {
				new ModifyDatastream(node.getPid(), "seq").versionable(true)
						.dsLabel("json array to define the order of child objects")
						.dsState("A").controlGroup("M").mimeType("application/json")
						.content(file).execute();
			} else {
				new AddDatastream(node.getPid(), "seq").versionable(true).dsState("A")
						.dsLabel("json array to define the order of child objects")
						.controlGroup("M").mimeType("application/json").content(file)
						.execute();
			}
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	@SuppressWarnings("javadoc")
	public void updateConfStream(Node node) {
		try {
			File file = new File(node.getConfFile());
			if (dataStreamExists(node.getPid(), "conf")) {
				new ModifyDatastream(node.getPid(), "conf").versionable(true)
						.dsLabel("json file to configure webharvests").dsState("A")
						.controlGroup("M").mimeType("application/json").content(file)
						.execute();
			} else {
				new AddDatastream(node.getPid(), "conf").versionable(true).dsState("A")
						.dsLabel("json file to configure webharvests").controlGroup("M")
						.mimeType("application/json").content(file).execute();
			}
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	@SuppressWarnings("javadoc")
	public void updateUrlHistStream(Node node) {
		try {
			File file = new File(node.getUrlHistFile());
			if (dataStreamExists(node.getPid(), "urlHist")) {
				new ModifyDatastream(node.getPid(), "urlHist").versionable(true)
						.dsLabel("json file to keep track of a webpage's URL history")
						.dsState("A").controlGroup("M").mimeType("application/json")
						.content(file).execute();
			} else {
				new AddDatastream(node.getPid(), "urlHist").versionable(true)
						.dsState("A")
						.dsLabel("json file to keep track of a webpage's URL history")
						.controlGroup("M").mimeType("application/json").content(file)
						.execute();
			}
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	void updateMetadataStream(Node node, String metadataType) {
		updateMetadataStream(metadataType, "text/plain", "n-triple rdf metadata",
				node);
	}

	/**
	 * Update a metadata stream
	 * 
	 * @param metadataType the metadatatype as defined in
	 *          archive.fedora.Vocabulary
	 * @param mimeType the mime tpye of the metadata
	 * @param label the label of the metadata
	 * @param node the node of class models.Node
	 */
	public void updateMetadataStream(String metadataType, String mimeType,
			String label, Node node) {
		try {
			File file = new File(node.getMetadataFile(metadataType));
			if (dataStreamExists(node.getPid(), metadataType)) {
				new ModifyDatastream(node.getPid(), metadataType).versionable(true)
						.dsLabel(label).dsState("A").controlGroup("M").mimeType(mimeType)
						.content(file).execute();
			} else {
				new AddDatastream(node.getPid(), metadataType).versionable(true)
						.dsState("A").dsLabel(label).controlGroup("M").mimeType(mimeType)
						.content(file).execute();
			}
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	void readRelsExt(Node node) throws FedoraClientException {
		FedoraResponse response =
				new GetDatastreamDissemination(node.getPid(), "RELS-EXT").download(true)
						.execute();
		try (InputStream ds = response.getEntityInputStream()) {
			Repository myRepository = new SailRepository(new MemoryStore());
			myRepository.initialize();
			RepositoryConnection con = myRepository.getConnection();
			String baseURI = "";
			try {
				ValueFactory f = myRepository.getValueFactory();
				IRI objectId = f.createIRI("info:fedora/" + node.getPid());
				con.add(ds, baseURI, RDFFormat.RDFXML);
				RepositoryResult<Statement> statements =
						con.getStatements(objectId, null, null, true);
				try {
					while (statements.hasNext()) {
						Statement st = statements.next();
						IRI predUri = st.getPredicate();
						Value objUri = st.getObject();
						Link link = new Link();
						link.setObject(objUri.stringValue(), false);
						link.setPredicate(predUri.stringValue());

						String pred = link.getPredicate();
						if (REL_IS_NODE_TYPE.equals(pred)) {
							node.setType(link.getObject());
							continue;
						} else if (REL_CONTENT_TYPE.equals(pred)) {
							node.setContentType(link.getObject());
							continue;
						} else if (REL_HAS_MODEL.equals(pred)) {
							addContentModel(link, node);
							continue;
						} else if (REL_ACCESS_SCHEME.equals(pred)) {
							node.setAccessScheme(link.getObject());
							continue;
						} else if (REL_IMPORTED_FROM.equals(pred)) {
							node.setImportedFrom(link.getObject());
							continue;
						} else if (REL_CREATED_BY.equals(pred)) {
							node.setCreatedBy(link.getObject());
							continue;
						} else if (REL_PUBLISH_SCHEME.equals(pred)) {
							node.setPublishScheme(link.getObject());
							continue;
						} else if (REL_IMPORTED_FROM.equals(pred)) {
							node.setImportedFrom(link.getObject());
							continue;
						} else if (REL_CREATED_BY.equals(pred)) {
							node.setCreatedBy(link.getObject());
							continue;
						} else if (REL_LAST_MODIFIED_BY.equals(pred)) {
							node.setLastModifiedBy(link.getObject());
							continue;
						} else if (REL_LEGACY_ID.equals(pred)) {
							node.setLegacyId(link.getObject());
							continue;
						} else if (REL_NAME.equals(pred)) {
							node.setName(link.getObject());
							continue;
						} else if (REL_CATALOG_ID.equals(pred)) {
							node.setCatalogId(link.getObject());
							continue;
						} else if (IS_PART_OF.equals(pred)) {
							node.setParentPid(link.getObject());
							continue;
						} else if (REL_HAS_DOI.equals(pred)) {
							node.setDoi(link.getObject());
							continue;
						} else if (REL_HAS_URN.equals(pred)) {
							node.setUrn(link.getObject());
							continue;
						}
						link.setLiteral(objUri instanceof Literal);
						node.addRelation(link);
					}
				} catch (Exception e) {
					throw new HttpArchiveException(500, e);
				} finally {
					statements.close();
				}

			} finally {
				con.close();
			}

		} catch (RepositoryException e) {
			throw new HttpArchiveException(500, e);
		} catch (RemoteException e) {
			throw new HttpArchiveException(500, e);
		} catch (RDFParseException e) {
			throw new HttpArchiveException(500, e);
		} catch (IOException e) {
			throw new HttpArchiveException(500, e);
		}
	}

	/**
	 * Creates new Rels-Ext datastream in object 'pid'
	 * </p>
	 * 
	 * @param pid of the object
	 * 
	 */
	void createFedoraXmlForRelsExt(String pid) {
		try {
			String initialContent =
					"<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:rel=\"info:fedora/fedora-system:def/relations-external#\">"
							+ "    <rdf:Description rdf:about=\"info:fedora/" + pid + "\">"
							+ "    </rdf:Description>" + "</rdf:RDF>";

			new AddDatastream(pid, "RELS-EXT").mimeType("application/rdf+xml")
					.formatURI("info:fedora/fedora-system:FedoraRELSExt-1.0")
					.versionable(true).content(initialContent).execute();
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	void updateFedoraXmlForRelsExt(String pid, List<Link> statements) {
		String initialContent = null;
		try {
			initialContent = RdfUtils.getFedoraRelsExt(pid, statements);
			new ModifyDatastream(pid, "RELS-EXT").mimeType("application/rdf+xml")
					.formatURI("info:fedora/fedora-system:FedoraRELSExt-1.0")
					.versionable(true).content(initialContent).execute();
		} catch (FedoraClientException e) {
			throw new HttpArchiveException(e.getStatus(), e);
		}
	}

	List<String> findPidsSimple(String rdfQuery) {
		FindObjectsResponse response = null;
		List<String> result = null;
		try {
			response = new FindObjects().maxResults(50).resultFormat("xml").pid()
					.terms(rdfQuery).execute();
		} catch (FedoraClientException e) {
			return new Vector<String>();
		}
		try {
			if (!response.hasNext())
				return response.getPids();
			result = response.getPids();
			while (response.hasNext()) {

				response = new FindObjects().pid().sessionToken(response.getToken())
						.maxResults(50).resultFormat("xml").execute();
				result.addAll(response.getPids());

			}
		} catch (FedoraClientException e) {
			throw new NoPidFoundException(rdfQuery, e);
		}
		return result;
	}

	String setOwnerToXMLString(String objXML) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder docBuilder = factory.newDocumentBuilder();
			Document doc = docBuilder.parse(
					new BufferedInputStream(new ByteArrayInputStream(objXML.getBytes())));
			Element root = doc.getDocumentElement();
			root.normalize();
			NodeList properties = root.getElementsByTagName("foxml:property");
			for (int i = 0; i < properties.getLength(); i++) {
				Element n = (Element) properties.item(i);
				String attribute = n.getAttribute("NAME");
				if (attribute
						.compareTo("info:fedora/fedora-system:def/model#ownerId") == 0) {
					n.setAttribute("VALUE", user);
					break;
				}
			}

			try {
				doc.normalize();
				Source source = new DOMSource(doc);
				StringWriter stringWriter = new StringWriter();
				Result result = new StreamResult(stringWriter);
				TransformerFactory fac = TransformerFactory.newInstance();
				javax.xml.transform.Transformer transformer = fac.newTransformer();
				transformer.transform(source, result);
				return stringWriter.toString();
			} catch (TransformerConfigurationException e) {
				e.printStackTrace();
			} catch (TransformerException e) {
				e.printStackTrace();
			}

		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {

			e.printStackTrace();
		} catch (IOException e) {

			e.printStackTrace();
		}
		return null;
	}

	private void createRelsExt(String pid, List<Link> links) {
		if (links != null)
			for (Link curHBZLink : links) {
				if (curHBZLink == null)
					return;
				try {
					if (curHBZLink.isLiteral()) {

						new AddRelationship(pid).predicate(curHBZLink.getPredicate())
								.object(curHBZLink.getObject(), curHBZLink.isLiteral())
								.execute();

					} else {

						new AddRelationship(pid).predicate(curHBZLink.getPredicate())
								.object(addUriPrefix(curHBZLink.getObject()),
										curHBZLink.isLiteral())
								.execute();
					}
				} catch (Exception e) {
					play.Logger.warn(e.getMessage());
					play.Logger.debug("", e);
				}
			}
	}

	void updateRelsExt(Node node) {
		String pid = node.getPid();
		// String type = node.getContentType();
		if (!dataStreamExists(pid, "RELS-EXT")) {
			createFedoraXmlForRelsExt(pid);
		}
		Link link = new Link();
		link.setObject(node.getContentType(), true);
		link.setPredicate(REL_CONTENT_TYPE);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getNodeType(), true);
		link.setPredicate(REL_IS_NODE_TYPE);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getAccessScheme(), true);
		link.setPredicate(REL_ACCESS_SCHEME);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getPublishScheme(), true);
		link.setPredicate(REL_PUBLISH_SCHEME);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getImportedFrom(), true);
		link.setPredicate(REL_IMPORTED_FROM);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getCreatedBy(), true);
		link.setPredicate(REL_CREATED_BY);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getLastModifiedBy(), true);
		link.setPredicate(REL_LAST_MODIFIED_BY);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getLegacyId(), true);
		link.setPredicate(REL_LEGACY_ID);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getName(), true);
		link.setPredicate(REL_NAME);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getCatalogId(), true);
		link.setPredicate(REL_CATALOG_ID);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getParentPid(), true);
		link.setPredicate(IS_PART_OF);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getDoi(), true);
		link.setPredicate(REL_HAS_DOI);
		node.addRelation(link);
		link = new Link();
		link.setObject(node.getUrn(), true);
		link.setPredicate(REL_HAS_URN);
		node.addRelation(link);

		updateFedoraXmlForRelsExt(pid, node.getRelsExt());
	}

	void createContentModels(List<Transformer> models) {
		for (Transformer m : models) {
			try {
				createContentModel(m);
			} catch (FedoraClientException e) {
				throw new ContentModelException(e);
			}
		}
	}

	/**
	 * @param models a list of Transformers/ContentModels
	 */
	public void updateContentModels(List<Transformer> models) {
		for (Transformer m : models) {
			updateContentModel(m);
		}

	}

	private void updateContentModel(Transformer m) {

		try {
			deleteContentModel(m);
		} catch (FedoraClientException e) {
			// throw new ContentModelException(e);
		}
		try {
			createContentModel(m);
		} catch (FedoraClientException e) {
			throw new ContentModelException(e);
		}

	}

	private void deleteContentModel(Transformer m) throws FedoraClientException {
		if (nodeExists(m.getContentModelPID()))
			new PurgeObject(m.getContentModelPID()).execute();
		if (nodeExists(m.getServiceDefinitionPID()))
			new PurgeObject(m.getServiceDefinitionPID()).execute();
		if (nodeExists(m.getServiceDeploymentPID()))
			new PurgeObject(m.getServiceDeploymentPID()).execute();
	}

	/**
	 * Links a list of contentModels to a node
	 * 
	 * @param contentModels the ContentModels
	 * @param node the node
	 */
	public void linkContentModels(List<Transformer> contentModels, Node node) {
		for (Transformer t : contentModels) {
			Link link = new Link();
			link.setPredicate(REL_HAS_MODEL);
			link.setObject(addUriPrefix(t.getContentModelPID()), false);
			node.addRelation(link);
		}
	}

	void createContentModel(Transformer cm) throws FedoraClientException {
		String foCMPid = cm.getContentModelPID();
		String foSDefPid = cm.getServiceDefinitionPID();
		String foSDepPid = cm.getServiceDeploymentPID();

		if (!nodeExists(foCMPid))
			new Ingest(foCMPid).label("Content Model").execute();
		if (!nodeExists(foSDefPid))
			new Ingest(foSDefPid).label("ServiceDefinition").execute();
		if (!nodeExists(foSDepPid))
			new Ingest(foSDepPid).label("ServiceDeployment").execute();

		// Add Relations
		Vector<Link> cmHBZLinks = new Vector<Link>();
		Link cmHBZLink1 = new Link();
		cmHBZLink1.setPredicate(REL_HAS_SERVICE);
		cmHBZLink1.setObject(addUriPrefix(foSDefPid), false);

		cmHBZLinks.add(cmHBZLink1);

		Link cmHBZLink2 = new Link();
		cmHBZLink2.setPredicate(REL_HAS_MODEL);
		cmHBZLink2.setObject(addUriPrefix(CM_CONTENTMODEL), false);
		cmHBZLinks.add(cmHBZLink2);

		Link typeLink = new Link();
		typeLink.setPredicate(REL_CONTENT_TYPE);
		typeLink.setObject("transformer", true);
		cmHBZLinks.add(typeLink);

		addRelationships(foCMPid, cmHBZLinks);

		Vector<Link> sDefHBZLinks = new Vector<Link>();
		Link sDefHBZLink = new Link();
		sDefHBZLink.setPredicate(REL_HAS_MODEL);
		sDefHBZLink.setObject(addUriPrefix(SDEF_CONTENTMODEL), false);
		sDefHBZLinks.add(sDefHBZLink);

		addRelationships(foSDefPid, sDefHBZLinks);

		Vector<Link> sDepHBZLinks = new Vector<Link>();
		Link sDepHBZLink1 = new Link();
		sDepHBZLink1.setPredicate(REL_IS_DEPLOYMENT_OF);
		sDepHBZLink1.setObject(addUriPrefix(foSDefPid), false);
		sDepHBZLinks.add(sDepHBZLink1);

		Link sDepHBZLink2 = new Link();
		sDepHBZLink2.setPredicate(REL_IS_CONTRACTOR_OF);
		sDepHBZLink2.setObject(addUriPrefix(foCMPid), false);
		sDepHBZLinks.add(sDepHBZLink2);

		Link sDepHBZLink3 = new Link();
		sDepHBZLink3.setPredicate(REL_HAS_MODEL);
		sDepHBZLink3.setObject(addUriPrefix(SDEP_CONTENTMODEL), false);
		sDepHBZLinks.add(sDepHBZLink3);

		addRelationships(foSDepPid, sDepHBZLinks);
		if (!dataStreamExists(foCMPid, DS_COMPOSITE_MODEL))
			new AddDatastream(foCMPid, DS_COMPOSITE_MODEL)
					.dsLabel("DS-Composite-Stream").versionable(true)
					.formatURI(DS_COMPOSITE_MODEL_URI).dsState("A").controlGroup("X")
					.mimeType("text/xml").content(cmBuilder.getDsCompositeModel(cm))
					.execute();
		if (!dataStreamExists(foSDefPid, DS_METHODMAP))
			new AddDatastream(foSDefPid, DS_METHODMAP).dsLabel("Methodmap-Stream")
					.versionable(true).formatURI(DS_METHODMAP_URI).dsState("A")
					.controlGroup("X").mimeType("text/xml")
					.content(cmBuilder.getMethodMap(cm)).execute();
		if (!dataStreamExists(foSDepPid, DS_METHODMAP_WSDL))
			new AddDatastream(foSDepPid, DS_METHODMAP_WSDL)
					.dsLabel("Methodmap-Stream").versionable(true)
					.formatURI(DS_METHODMAP_WSDL_URI).dsState("A").controlGroup("X")
					.mimeType("text/xml").content(cmBuilder.getMethodMapToWsdl(cm))
					.execute();
		if (!dataStreamExists(foSDepPid, DS_INPUTSPEC))
			new AddDatastream(foSDepPid, DS_INPUTSPEC).dsLabel("DSINPUTSPEC-Stream")
					.versionable(true).formatURI(DS_INPUTSPEC_URI).dsState("A")
					.controlGroup("X").mimeType("text/xml")
					.content(cmBuilder.getDSInputSpec()).execute();
		if (!dataStreamExists(foSDepPid, DS_WSDL))
			new AddDatastream(foSDepPid, DS_WSDL).dsLabel("WSDL-Stream")
					.versionable(true).formatURI(DS_WSDL_URI).dsState("A")
					.controlGroup("X").mimeType("text/xml").content(cmBuilder.getWsdl(cm))
					.execute();
	}

	private void addContentModel(Link link, Node node) {
		try {
			Transformer t = readTransformer(link.getObject());
			node.addTransformer(t);
		} catch (ContentModelException e) {
			play.Logger.debug("", e);
		}

	}

	private Transformer readTransformer(String prefixedPid) {
		String pid = removeUriPrefix(prefixedPid);
		String id = pid.substring(pid.indexOf(":") + 1);
		Transformer t = new Transformer(id);
		return t;
	}

	void updateMetadataJsonStream(Node node) {
		updateMetadataStream(archive.fedora.Vocabulary.toscience,
				"application/json", "Metadata TOSCIENCE in Format JSON", node);
	}

	void updateMetadataKtblStream(Node node) {
		updateMetadataStream(archive.fedora.Vocabulary.ktbl, "application/json",
				"Metadata KTBL in Format JSON", node);
	}

	/**
	 * Diese Methode ermittelt den "Data-Provider" (z.B. ein
	 * Landesbibliothekskürzel) bei Neuanlage oder Update einer Ressource.
	 * 
	 * @author I. Kuss für TOSDEV-32
	 * @param node der Node so wie er vor dem Update ist, also mit alten Metadaten
	 *          bzw. dem alten vorläufigen Titel.
	 * @return der Data-Provider (Kürzel gem. kontrolliertem Fedora-Vokabular).
	 */
	public static String getDataProvider(Node node) {
		String associated_data_provider = null;
		/**
		 * In der ersten Realisationsphase wird Data-Provider aus dem manuell
		 * erfassten, tenporären Titel ermittelt. Dieses Verfahren soll in einer
		 * zweiten Realisationsphase dahin gehend geändert werden, dass der
		 * Data-Provider aus der Drupal-UserId ermittelt wird.
		 */
		/**
		 * 1. das LB-Kennzeichen aus den alten Titel ermitteln.
		 */
		String title = Title.getTitle(node.getLd2());
		if (title.isEmpty()) {
			play.Logger.debug("Kein Titel am Objekt PID=" + node.getPid() + "!");
			return associated_data_provider;
		}
		play.Logger.debug("alter Titel=" + title);
		for (DATA_PROVIDER dataProvider : DATA_PROVIDER.values()) {
			if (title.startsWith(dataProvider + ": ")) {
				associated_data_provider = dataProvider.toString();
				break;
			}
		}
		if (associated_data_provider == null) {
			play.Logger.debug(
					"Data-Provider konnte aus dem Titel nicht ermittelt werden! PID="
							+ node.getPid());
		} else {
			play.Logger.debug("erkannter Data-Provider=" + associated_data_provider);
		}

		return associated_data_provider;
	}

}