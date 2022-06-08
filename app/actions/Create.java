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

import static archive.fedora.Vocabulary.TYPE_OBJECT;

import java.io.File;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
// import javax.activation.MimetypesFileTypeMap;
import com.fasterxml.jackson.databind.JsonNode;

import controllers.MyController;
import helper.HttpArchiveException;
import helper.WebsiteVersionPublisher;
import helper.oai.OaiDispatcher;
import models.Gatherconf;
import models.Globals;
import models.Node;
import models.ResearchDataResource;
import models.ToScienceObject;
import models.ToScienceObject.Provenience;
import play.Logger;
import play.Play;

/**
 * Legt ein Objekt als to.science-Datenobjekt der Klasse "Node" an. "Node" ist,
 * neben ToScieneObject, das zentrale Datenmodell für komplexe Objekte in
 * to.science. "Node" wird auf Basis von "ToScienceObject" erzeugt. Auf Basis
 * des Node wird ein Fedora-Objekt angelegt (durch Aufruf von
 * FedoraFacace.updateNode).
 * 
 * Zusammenfassend werden Node zusammen mit Fedora-Objekt in to.science als
 * "Ressource" (Resource) bezeichnet.
 * 
 * Diese Klasse macht auch Updates und "Patches" (etwa: Reparaturen) auf
 * bestehende Ressourcen.
 * 
 * @author Jan Schnasse
 *
 */
public class Create extends RegalAction {

	private static final Logger.ALogger ApplicationLogger =
			Logger.of("application");
	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");
	private static final BigInteger bigInt1024 = new BigInteger("1024");

	@SuppressWarnings({ "javadoc", "serial" })
	public class WebgathererTooBusyException extends HttpArchiveException {

		public WebgathererTooBusyException(int status, String msg) {
			super(status, msg);
			WebgatherLogger.error(msg);
		}

	}

	/**
	 * Diese Methode aktualisiert eine Ressource aufgrund eines
	 * ToScience-Objektes. Bestehende Eigenschaften des Nodes werden
	 * überschrieben.
	 * 
	 * @param node Der Knoten (muss schon existieren)
	 * @param object Ein ToScience-Objekt
	 * @return the updated node
	 */
	public Node updateResource(Node node, ToScienceObject object) {
		new Index().remove(node);
		overrideNodeMembers(node, object);
		return updateResource(node);
	}

	public Node updateResource(Node node) {
		play.Logger.debug("Updating Node with Pid " + node.getPid());
		Globals.fedora.updateNode(node);
		updateIndex(node.getPid());
		return node;
	}

	/**
	 * Diese Methode "patcht" (aktualisiert) eine Ressource aufgrund eines
	 * ToScience-Objektes. Fehlende Eigenschaften werden hinzugefügt.
	 * 
	 * @param node Der Knoten (muss schon existieren)
	 * @param object Ein ToScience-Objekt
	 * @return the updated node
	 */
	public Node patchResource(Node node, ToScienceObject object) {
		play.Logger.debug("Patching Node with Pid " + node.getPid());
		new Index().remove(node);
		setNodeMembers(node, object);
		WebsiteVersionPublisher wvp = new WebsiteVersionPublisher();
		node.setLastModifyMessage(wvp.handleWebpagePublishing(node, object));
		return updateResource(node);
	}

	/**
	 * Diese Methode "patcht" ("flickt", aktualisiert) eine Liste von Ressourcen
	 * anhand ein- und dessselben ToScience-Objektes.
	 * 
	 * @param nodes nodes to set new properties for
	 * @param object the RegalObject contains props that will be applied to all
	 *          nodes in the list
	 * @return a message
	 */
	public String patchResources(List<Node> nodes, ToScienceObject object) {
		return apply(nodes, n -> patchResource(n, object).getPid());
	}

	/**
	 * Diese Methode legt eine neue Ressource anhand eines ToScience-Objektes an.
	 * Die PID wird vom System generiert.
	 * 
	 * @param namespace Der Namensraum (z.B. "edoweb" oder "frl")
	 * @param object Das ToScience-Objekt
	 * @return the updated node
	 */
	public Node createResource(String namespace, ToScienceObject object) {
		String pid = pid(namespace);
		return createResource(pid.split(":")[1], namespace, object);
	}

	/**
	 * Diese Methode legt eine neue Ressource anhand eines ToScience-Objektes und
	 * einer gewünschten ID an. Falls die ID schon existiert, wird ein Update
	 * gemacht.
	 * 
	 * @param id Die ID der Ressource
	 * @param namespace Der Namensraum (z.B. "edoweb" oder "frl")
	 * @param object Das ToScience-Objekt
	 * @return the updated node
	 */
	public Node createResource(String id, String namespace,
			ToScienceObject object) {
		Node node = initNode(id, namespace, object);
		updateResource(node, object);
		// updateIndex(node.getPid()); # hier doppelt; wurde schon in updateResource
		// aufgerufen
		return node;
	}

	private Node initNode(String id, String namespace, ToScienceObject object) {
		Node node = new Node();
		node.setNamespace(namespace).setPID(namespace + ":" + id);
		node.setContentType(object.getContentType());
		node.setAccessScheme(object.getAccessScheme());
		node.setPublishScheme(object.getPublishScheme());
		node.setAggregationUri(createAggregationUri(node.getPid()));
		node.setRemUri(node.getAggregationUri() + ".rdf");
		node.setDataUri(node.getAggregationUri() + "/data");
		node.setContextDocumentUri(
				"http://" + Globals.server + "/public/edoweb-resources.json");
		node.setLabel(object.getIsDescribedBy().getName());
		Globals.fedora.createNode(node);
		return node;
	}

	private void setNodeMembers(Node node, ToScienceObject object) {
		if (object.getContentType() != null)
			setNodeType(object.getContentType(), node);
		if (object.getAccessScheme() != null)
			node.setAccessScheme(object.getAccessScheme());
		if (object.getPublishScheme() != null)
			node.setPublishScheme(object.getPublishScheme());
		if (object.getParentPid() != null)
			linkWithParent(object.getParentPid(), node);
		if (object.getIsDescribedBy() != null) {
			if (object.getIsDescribedBy().getCreatedBy() != null)
				node.setCreatedBy(object.getIsDescribedBy().getCreatedBy());
			if (object.getIsDescribedBy().getImportedFrom() != null)
				node.setImportedFrom(object.getIsDescribedBy().getImportedFrom());
			if (object.getIsDescribedBy().getLegacyId() != null)
				node.setLegacyId(object.getIsDescribedBy().getLegacyId());
			if (object.getIsDescribedBy().getName() != null)
				node.setName(object.getIsDescribedBy().getName());
			if (object.getTransformer() != null)
				OaiDispatcher.updateTransformer(object.getTransformer(), node);
			if (object.getIsDescribedBy().getDoi() != null)
				node.setDoi(object.getIsDescribedBy().getDoi());
			if (object.getIsDescribedBy().getUrn() != null)
				node.setUrn(object.getIsDescribedBy().getUrn());
		}
		OaiDispatcher.makeOAISet(node);
	}

	private void overrideNodeMembers(Node node, ToScienceObject object) {
		setNodeType(object.getContentType(), node);
		node.setAccessScheme(object.getAccessScheme());
		node.setPublishScheme(object.getPublishScheme());
		if (object.getParentPid() != null) {
			linkWithParent(object.getParentPid(), node);
		}
		if (object.getIsDescribedBy() != null) {
			node.setCreatedBy(object.getIsDescribedBy().getCreatedBy());
			node.setImportedFrom(object.getIsDescribedBy().getImportedFrom());
			node.setLegacyId(object.getIsDescribedBy().getLegacyId());
			node.setName(object.getIsDescribedBy().getName());
			node.setDoi(object.getIsDescribedBy().getDoi());
			node.setUrn(object.getIsDescribedBy().getUrn());
		}
		OaiDispatcher.makeOAISet(node);
	}

	private static void setNodeType(String type, Node node) {
		node.setType(TYPE_OBJECT);
		node.setContentType(type);
	}

	private void linkWithParent(String parentPid, Node node) {
		try {
			Node parent = new Read().readNode(parentPid);
			unlinkOldParent(node);
			linkToNewParent(parent, node);
			inheritTitle(parent, node);
			inheritRights(parent, node);
			updateIndex(parentPid);
		} catch (Exception e) {
			play.Logger.warn("Fail link " + node.getPid() + " to " + parentPid + "",
					e);
		}
	}

	private void inheritTitle(Node from, Node to) {
		String title = new Read().readMetadata2(to, "title");
		String parentTitle = new Read().readMetadata2(from, "title");
		if (title == null && parentTitle != null) {
			new Modify().addMetadataField(to, getUriFromJsonName("title"),
					parentTitle);
			play.Logger.debug("node.getNamespace=" + to.getNamespace());
		}
	}

	private static void linkToNewParent(Node parent, Node child) {
		Globals.fedora.linkToParent(child, parent.getPid());
		Globals.fedora.linkParentToNode(parent.getPid(), child.getPid());
	}

	private void unlinkOldParent(Node node) {
		String pp = node.getParentPid();
		if (pp != null && !pp.isEmpty()) {
			try {
				Globals.fedora.unlinkParent(node);
				updateIndex(pp);
			} catch (HttpArchiveException e) {
				play.Logger.warn("", e);
			}
		}
	}

	private static void inheritRights(Node from, Node to) {
		to.setAccessScheme(from.getAccessScheme());
		to.setPublishScheme(from.getPublishScheme());
	}

	/**
	 * Diese Methode holt einen neuen persistenten Identifikator (PID) aus dem
	 * Namensraum (Namespace).
	 * 
	 * @param namespace a namespace in fedora , corresponds to an index in
	 *          elasticsearch
	 * @return a new pid in the namespace
	 */
	public String pid(String namespace) {
		return Globals.fedora.getPid(namespace);
	}

	/**
	 * Diese Methode legt eine neue Webpage-Version (Objekt-Typ "Node") für einen
	 * erfolgreich beendeten Crawl an.
	 * 
	 * Kurzform von createWepageVersion mit nur 4 Parametern. Dabei werden Label
	 * und Datestamp aus dem aktuellen Datum erzeugt. Für Neuanlage von
	 * Website-Versionen direkt nach erfolgreich beendetem Crawl. Für nachträglich
	 * angelegte Webschnitte oder zu importierende Altdaten ist dagegen die
	 * vollständige Methode mit 7 Parametern zu benutzen.
	 * 
	 * @param n Der Knoten der Website
	 * @param conf Die Gatherconf der Website
	 * @param outDir Das Verzeichnis, in dem die endgültige, fertig gecrawlte
	 *          Version des neuen Webschnitts liegt.
	 * @param localpath Die URI, unter der die Webpage-Version lokal gespeichert
	 *          wird
	 * @return Der Knoten der neuen Webpage-Version
	 */
	public Node createWebpageVersion(Node n, Gatherconf conf, File outDir,
			String localpath) {
		String label = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String owDatestamp = new SimpleDateFormat("yyyyMMdd").format(new Date());
		String versionPid = null;
		return createWebpageVersion(n, conf, outDir, localpath, versionPid, label,
				owDatestamp);
	}

	/**
	 * Diese Methode legt eine neue Webpage-Version (Objekt-Typ "Node") für einen
	 * erfolgreich beendeten Crawl an.
	 * 
	 * @param n must be of type webpage: Die Webpage
	 * @param conf die Gatherconf zu der Webpage
	 * @param outDir Das Verzeichnis, in dem die endgültige, fertig gecrawlte
	 *          Version des neuen Webschnitts liegt.
	 * @param localpath Die URI, unter der die Webpage-Version lokal gespeichert
	 *          wird
	 * @param versionPid die PID der neuen WbesiteVersion, falls schon bekannt
	 *          (bei Altdatenimport), ansonsten NULL oder "" (bei Import eines
	 *          neuen Webschnittes)
	 * @param label Der Bezeichner des Webschnitts, wie er auf der UI angezeigt
	 *          werden soll (z.B. "2020-07-28")
	 * @param owDatestamp Ein Datumstempel zum Wiederauffinden des Archivs in der
	 *          Wayback, z.B. "20200728"
	 * @return Der Knoten der neuen Webpage-Version
	 */
	public Node createWebpageVersion(Node n, Gatherconf conf, File outDir,
			String localpath, String versionPid, String label, String owDatestamp) {
		try {
			// Erzeuge ein Fedora-Objekt mit ungemanagtem Inhalt,
			// das auf den entsprechenden WARC-Container zeigt.
			ToScienceObject regalObject = new ToScienceObject();
			regalObject.setContentType("version");
			Provenience prov = regalObject.getIsDescribedBy();
			prov.setCreatedBy("webgatherer");
			prov.setName(conf.getName());
			prov.setImportedFrom(conf.getUrl());
			regalObject.setIsDescribedBy(prov);
			regalObject.setParentPid(n.getPid());
			Node webpageVersion = null;
			if ((versionPid != null) && (!versionPid.isEmpty())) {
				webpageVersion =
						createResource(versionPid, n.getNamespace(), regalObject);
			} else {
				webpageVersion = createResource(n.getNamespace(), regalObject);
			}

			new Modify().updateLobidifyAndEnrichMetadata(webpageVersion,
					"<" + webpageVersion.getPid()
							+ "> <http://purl.org/dc/terms/title> \"" + label + "\" .");
			if (localpath != null) {
				webpageVersion.setLocalData(localpath);
			}
			webpageVersion.setMimeType("application/warc");
			webpageVersion.setFileLabel(label);
			webpageVersion.setAccessScheme(n.getAccessScheme());
			webpageVersion.setPublishScheme(n.getPublishScheme());
			webpageVersion = updateResource(webpageVersion);

			conf.setLocalDir(outDir.getAbsolutePath());
			WebgatherLogger.info("localDir=" + outDir.getAbsolutePath());

			String waybackCollectionLink = null;
			if (n.getAccessScheme().equals("public")) {
				waybackCollectionLink = Play.application().configuration()
						.getString("regal-api.wayback.weltweitLink");
			} else {
				waybackCollectionLink = Play.application().configuration()
						.getString("regal-api.wayback.lesesaalLink");
			}
			conf.setOpenWaybackLink(
					waybackCollectionLink + owDatestamp + "/" + conf.getUrl());
			WebgatherLogger
					.info("waybackCollectionLink=" + conf.getOpenWaybackLink());
			conf.setId(webpageVersion.getPid());
			String msg = new Modify().updateConf(webpageVersion, conf.toString());

			/*
			 * Im Falle von veröffentlichten Websites soll die neue Version sofort
			 * veröffentlicht werden
			 */
			if (n.getAccessScheme().equals("public")) {
				WebsiteVersionPublisher.createSoftlinkInPublicData(webpageVersion,
						conf);
			}

			WebgatherLogger.debug(msg);
			WebgatherLogger.info("Version " + webpageVersion.getPid()
					+ " zur Website " + n.getPid() + " erfolgreich angelegt!");

			return webpageVersion;
		} catch (Exception e) {
			WebgatherLogger.warn("Anlage einer Webpage-Version zu PID,URL "
					+ n.getPid() + "," + conf.getUrl()
					+ " ist fehlgeschlagen !\n\tGrund: " + e.getMessage());
			WebgatherLogger.debug("", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Diese Methode legt eine WebpageVersion (Objekt-Typ "Node") für eine
	 * bestehende WARC-Datei an. Es wird angenommen, dass diese WARC-Datei
	 * unterhalb des Verzechnisses wget-data liegt. Diese Methode wurde bei der
	 * Migration der aus EDO2 stammenden Webschnitte, die ausgepackt und mit wget
	 * erneut, lokal gecrawlt wurden, angewandt.
	 * 
	 * @param n must be of type webpage
	 * @param versionPid gewünschte Pid für die Version (7-stellig numerisch) oder
	 *          leer (Pid wird generiert)
	 * @param label Label für die Version im Format YYYY-MM-DD
	 * @return a new version pointing to an imported crawl. The imported crawl is
	 *         in wget-data/ and indexed in openwayback
	 */
	public Node importWebpageVersion(Node n, String versionPid, String label) {
		Gatherconf conf = null;
		try {
			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			ApplicationLogger.debug("Import webpageVersion for PID" + n.getPid());
			conf = Gatherconf.create(n.getConf());
			ApplicationLogger.debug("Import webpageVersion Conf" + conf.toString());
			conf.setName(n.getPid());
			// conf.setId(versionPid);
			Date startDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
					.parse(label + " 12:00:00");
			conf.setStartDate(startDate);

			// hier auf ein bestehendes WARC in dataDir verweisen
			String crawlDateTimestamp = label.substring(0, 4) + label.substring(5, 7)
					+ label.substring(8, 10); // crawl-Datum im Format yyyymmdd
			File crawlDir = new File(Globals.wget.dataDir + "/" + conf.getName() + "/"
					+ crawlDateTimestamp);
			ApplicationLogger.debug("crawlDir=" + crawlDir.toString());
			File warcDir = new File(crawlDir.getAbsolutePath() + "/warcs");
			String warcPath = warcDir.listFiles()[0].getAbsolutePath();
			ApplicationLogger.debug("Path to WARC " + warcPath);
			String uriPath = Globals.wget.getUriPath(warcPath);
			String localpath = Globals.wgetData + "/wget-data" + uriPath;
			ApplicationLogger.debug("URI-Path to WARC " + localpath);

			return createWebpageVersion(n, conf, crawlDir, localpath, versionPid,
					label, crawlDateTimestamp);

		} catch (Exception e) {
			ApplicationLogger.error(
					"Import der WebsiteVersion {} zu Webpage {} ist fehlgeschlagen !",
					versionPid, n.getPid());
			throw new RuntimeException(e);
		}
	}

	/**
	 * Diese Methode legt eine WebpageVersion für eine bestehende WARC-Datei an.
	 * Es wird angenommen, dass diese WARC-Datei unterhalb des Verzechnisses
	 * dataDir liegt.
	 * 
	 * @author Ingolf Kuss | 27.07.2020 | Neuanlage für EDOZWO-1020
	 * 
	 * @param n Der Knoten der Webpage
	 * @param versionPid gewünschte Pid für die Version (7-stellig numerisch) oder
	 *          leer (Pid wird generiert)
	 * @param dataDir Datenhauptverzeichnis, unter dem die WARC-Datei liegt. Z.B.
	 *          /opt/regal/wpull-data
	 * @param timestamp Der Zeitstempel des Crawl. Ist auch Name des
	 *          Unterverzeichnisses für den Crawl. Aus dem Datum wird der
	 *          Bezeichner (Label auf der UI) für den Webschnitt generiert.
	 * @param filename Der Dateiname der Archivdatei (ohne Pfadangaben, aber mit
	 *          Dateiendung) (WARC-Archiv).
	 * @return a new website version pointing to the posted crawl.
	 */
	public Node postWebpageVersion(Node n, String versionPid, String dataDir,
			String timestamp, String filename) {
		Gatherconf conf = null;
		try {
			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			ApplicationLogger.debug("POST webpageVersion for PID " + n.getPid());
			/*
			 * Legt eine Gatherconf für die Version an, zunächst als Kopie von der
			 * Website
			 */
			conf = Gatherconf.create(n.getConf());
			ApplicationLogger.debug("POST webpageVersion Conf" + conf.toString());
			conf.setName(n.getPid());
			Date startDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(timestamp);
			conf.setStartDate(startDate);

			// hier auf ein bestehendes WARC in dataDir verweisen
			File outDir = new File(dataDir + "/" + conf.getName() + "/" + timestamp);
			String localpath = Globals.heritrixData + "/wpull-data" + "/"
					+ conf.getName() + "/" + timestamp + "/" + filename;
			ApplicationLogger.debug("URI-Path to WARC " + localpath);
			String label = timestamp.substring(0, 4) + "-" + timestamp.substring(4, 6)
					+ "-" + timestamp.substring(6, 8);
			String owDatestamp = timestamp.substring(0, 8);
			return createWebpageVersion(n, conf, outDir, localpath, versionPid, label,
					owDatestamp);

		} catch (Exception e) {
			ApplicationLogger.error(
					"Anlegen der WebsiteVersion {} zu Webpage {} ist fehlgeschlagen !",
					timestamp, n.getPid());
			throw new RuntimeException(e);
		}
	}

	/**
	 * Diese Methode erzeugt eine neue WebpageVersion (Webschnitt) und verknüpft
	 * diese mit einem bereits erfolgten Crawl. Es wird angenommen, dass das
	 * Ergebnis dieses Crawl ausgepackt auf der Festplatte liegt, also nicht in
	 * WARC-Form vorhanden ist. Genauer wird angenommen, dass der ausgepackte
	 * Webcrawl unter dem URL-Pfad webharvests/ zu finden ist. Diese Methode wurde
	 * bei der Migration (Import) der aus EDO2 stammenden Webschnitte, die nicht
	 * in die Form WARC überführt werden konnten, verwendet.
	 * 
	 * @param n must be of type web page
	 * @param jsn Json node: Im Json muss die Information über die versionPid,
	 *          Datumsstempel (des Crawls) und ZIP-Size der gecrawlten Website
	 *          vorhanden sein
	 * @return a new version pointing to a linked, unpacked crawl
	 */
	public Node linkWebpageVersion(Node n, JsonNode jsn) {
		Gatherconf conf = null;
		String versionPid = "";
		try {
			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			ApplicationLogger.debug("Link webpageVersion to PID" + n.getPid());
			conf = Gatherconf.create(n.getConf());
			ApplicationLogger.debug("Link webpageVersion Conf" + conf.toString());
			conf.setName(n.getPid());
			versionPid =
					jsn.findValue("versionPid").toString().replaceAll("^\"|\"$", "");
			ApplicationLogger.debug("versionPid: " + versionPid);
			conf.setId(versionPid);
			String dateTimestamp =
					jsn.findValue("dateTimestamp").toString().replaceAll("^\"|\"$", "");
			ApplicationLogger.debug("dateTimestamp: " + dateTimestamp);
			Date startDate = new SimpleDateFormat("yyyyMMdd HH:mm:ss")
					.parse(dateTimestamp + " 12:00:00");
			conf.setStartDate(startDate);
			String label = dateTimestamp.substring(0, 4) + "-"
					+ dateTimestamp.substring(4, 6) + "-" + dateTimestamp.substring(6, 8);
			ApplicationLogger.debug("label: " + label);
			String zipSizeStr =
					jsn.findValue("zipSize").toString().replaceAll("^\"|\"$", "");
			BigInteger zipSize = new BigInteger(zipSizeStr).multiply(bigInt1024);
			ApplicationLogger.debug("zipSize (bytes): " + zipSize.toString());
			String relUri = n.getPid() + "/" + n.getNamespace() + ":" + versionPid;
			File crawlDir = new File(Globals.webharvestsDataDir + "/" + relUri);
			conf.setLocalDir(crawlDir.getAbsolutePath());

			String localpath =
					Globals.webharvestsDataUrl + "/" + relUri + "/webschnitt.xml";
			ApplicationLogger.debug("URI-Path to archive: " + localpath);
			conf.setOpenWaybackLink(localpath);

			// create Regal object
			ToScienceObject regalObject = new ToScienceObject();
			regalObject.setContentType("version");
			Provenience prov = regalObject.getIsDescribedBy();
			prov.setCreatedBy("webgatherer");
			prov.setName(conf.getName());
			prov.setImportedFrom(conf.getUrl());
			regalObject.setIsDescribedBy(prov);
			regalObject.setParentPid(n.getPid());
			Node webpageVersion =
					createResource(versionPid, n.getNamespace(), regalObject);
			new Modify().updateLobidifyAndEnrichMetadata(webpageVersion,
					"<" + webpageVersion.getPid()
							+ "> <http://purl.org/dc/terms/title> \"" + label + "\" .");
			webpageVersion.setLocalData(localpath);
			webpageVersion.setMimeType("application/xml");
			webpageVersion.setFileSize(zipSize);
			webpageVersion.setFileLabel(label);
			webpageVersion.setAccessScheme(n.getAccessScheme());
			webpageVersion.setPublishScheme(n.getPublishScheme());
			webpageVersion = updateResource(webpageVersion);
			String msg = new Modify().updateConf(webpageVersion, conf.toString());
			ApplicationLogger.info(msg);

			return webpageVersion;
		} catch (Exception e) {
			ApplicationLogger.error(
					"Link unpacked website version {} to webpage {} failed !", versionPid,
					n.getPid());
			throw new RuntimeException(e);
		}
	}

	/**
	 * Diese Methode legt einen Node vom contentType "researchDataResource"
	 * (Forschungsdaten-Ressource) an. Eine zu übergebende Datei (URL-Pfad,
	 * Dateiname) wird als ungemanangter Inhalt (Fedora unmanaged content) mit dem
	 * Node verknüpft. Der neue Node wird an einen bestehenden Node des
	 * contentTypes "researchData" (Forschungsdaten) angehängt, oder an eine
	 * Überordnung, die unterhalb diese Knotens hängt.
	 * 
	 * @author Ingolf Kuss 30.11.2020 | Neuanlage für FRL-522
	 * 
	 * @param n Der Knoten (Node) vom Typ Forschungsdaten (researchData)
	 * @param collectionUrl der URL-Pfad unterhalb des Basis-URL, unter der
	 *          Forschungsdatenressourcen abgelegt sind. Standardwert: "data"
	 * @param subPath ein optionaler Unterpfad in dem Verzeichnis, in dem für
	 *          diese PID Forschungsdaten abgelegt sind. Standardwert: "". Falls
	 *          der Unterpfad nicht leer ist, wird für ihn eine Überordnung
	 *          angelegt, sofern diese noch nicht vorhanden ist.
	 * @param filename Der Dateiname der Ressource (ohne Pfadangaben, aber mit
	 *          Dateiendung)
	 * @param resourcePid Die gewünschte Pid für die Resssource (7-stellig
	 *          numerisch) oder leer (Pid wird generiert)
	 * @return a new node of contentType researchDataResource
	 */
	public Node createResearchData(Node n, String collectionUrl, String subPath,
			String filename, String resourcePid) {
		try {
			ApplicationLogger
					.debug("Create research data resource for PID " + n.getPid());

			ResearchDataResource resource = new ResearchDataResource();
			resource.setResearchDataNode(n);
			/* first guess for parent, will be changed if subPath is present */
			resource.setParentNode(n);
			resource.setCollectionUrl(collectionUrl);
			resource.setSubPath(subPath);
			resource.setFilename(filename);
			resource.setResourcePid(resourcePid);

			// Gucke, ob eine Überordnung angelegt werden muss und lege sie ggfs. an
			if (subPath != null && !subPath.isEmpty()) {
				resource.chkCreatePart();
			}

			resource.doConsistencyChecks();

			// Erzeuge ein Fedora-Objekt mit ungemanagtem Inhalt,
			// das auf die Ressource (zugänglich über eine URL) zeigt
			ToScienceObject toScienceObject = new ToScienceObject();
			toScienceObject.setContentType("researchDataResource");
			Provenience prov = toScienceObject.getIsDescribedBy();
			// prov.setCreatedBy(userId);
			prov.setName(filename);
			prov.setImportedFrom(resource.getUrlString());
			toScienceObject.setIsDescribedBy(prov);
			toScienceObject.setParentPid(resource.getParentNode().getPid());
			Node researchDataResource = null;
			if ((resourcePid != null) && (!resourcePid.isEmpty())) {
				researchDataResource =
						createResource(resourcePid, n.getNamespace(), toScienceObject);
			} else {
				researchDataResource =
						createResource(n.getNamespace(), toScienceObject);
			}

			new Modify().updateLobidifyAndEnrichMetadata(researchDataResource,
					"<" + researchDataResource.getPid()
							+ "> <http://purl.org/dc/terms/title> \"" + filename + "\" .");
			researchDataResource.setLocalData(resource.getUrlString());
			researchDataResource.setMimeType(resource.getContentType());
			// Bei Ressourcen vom Typ "File" würde es so gehen (aber wir haben ja
			// URLs):
			/*
			 * researchDataResource.setMimeType( new
			 * MimetypesFileTypeMap().getContentType(<File>));
			 */
			researchDataResource.setFileMimeType(resource.getContentType());
			ApplicationLogger
					.info("FileMimeType = " + researchDataResource.getFileMimeType());
			researchDataResource.setFileLabel(filename);
			researchDataResource.setAccessScheme(n.getAccessScheme());
			researchDataResource.setPublishScheme(n.getPublishScheme());
			/*
			 * hier werden erst mal standardmäßig im ersten Aufschlag alle
			 * Forschungsdatenressourcen auf fiktive 100 KB Größe gesetzt
			 */
			BigInteger sizeInByte = new BigInteger("100000");
			researchDataResource.setFileSize(sizeInByte);
			ApplicationLogger.info("localData = " + resource.getUrlString());
			researchDataResource = updateResource(researchDataResource);

			ApplicationLogger
					.info("Successfully created resource " + researchDataResource.getPid()
							+ "  for research data " + n.getPid() + " !");
			return researchDataResource;

		} catch (Exception e) {
			ApplicationLogger.error(
					"Creation of research data resource {} for research data (Forschungsdaten) of PID {} has failed !\n\tReason: {}",
					filename, n.getPid(), e.getMessage());
			ApplicationLogger.debug("", e);
			throw new RuntimeException(e);
		}
	}

} /* END of Class Create */