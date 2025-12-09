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
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

// import javax.activation.MimetypesFileTypeMap;
import com.fasterxml.jackson.databind.JsonNode;

import controllers.MyController;
import helper.HttpArchiveException;
import helper.WebgatherUtils;
import helper.WebpageVersionImporter;
import helper.WebsiteVersionPublisher;
import helper.WpullThread;
import helper.oai.OaiDispatcher;
import models.Gatherconf;
import models.Globals;
import models.Node;
import models.ResearchDataResource;
import models.ToScienceObject;
import models.Gatherconf.Interval;
import models.Gatherconf.RobotsPolicy;
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
	private String warcFilename;

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
	 * @param object the ToScienceObject contains props that will be applied to
	 *          all nodes in the list
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
		updateIndex(node.getPid());
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
	 * @param warcFilename der Dateiname der WARC-Datei, ohne die Endung .warc.gz
	 * @param outDir Das Verzeichnis, in dem die endgültige, fertig gecrawlte
	 *          Version des neuen Webschnitts liegt.
	 * @param localpath Die URI, unter der die Webpage-Version lokal gespeichert
	 *          wird
	 * @param versionPid Die PID für die WebpageVersion, oder null. Bei null wird
	 *          PID vom System vergeben.
	 * @return Der Knoten der neuen Webpage-Version
	 */
	public Node createWebpageVersion(Node n, Gatherconf conf, String warcFilename,
			File outDir, String localpath, String versionPid) {
		try {
			/* Das Label, das auf dem Link "Zum Webschnitt" angezeigt werden soll */
			/*
			 * get basename of outDir = the timestamp for when the crawl has started
			 */
			String datetime = outDir.getName();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
			Date startdate = sdf.parse(datetime);
			String label =
					new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(startdate);
			WebgatherLogger.info("Webschnitt Label=" + label);
			/*
			 * Der Zeitstempel, mit dem die Open Wayback (oder Python Wayback)
			 * einsteigen soll. Es ist standardmäßig in UTC anzugeben.
			 */
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
			LocalDateTime startdateLocal = LocalDateTime.parse(datetime, dtf);
			ZonedDateTime startdateUTC = startdateLocal.atZone(ZoneId.systemDefault())
					.withZoneSameInstant(ZoneOffset.UTC);
			String owDatestamp =
					DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(startdateUTC);
			return createWebpageVersion(n, conf, warcFilename, outDir, localpath,
					versionPid, label, owDatestamp);
		} catch (Exception e) {
			WebgatherLogger.warn("Anlage einer Webpage-Version zu PID,URL "
					+ n.getPid() + "," + conf.getUrl()
					+ " ist fehlgeschlagen !\n\tGrund: " + e.getMessage());
			WebgatherLogger.debug("", e);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Diese Methode legt eine neue Webpage-Version (Objekt-Typ "Node") für einen
	 * erfolgreich beendeten Crawl an.
	 * 
	 * @param n must be of type webpage: Die Webpage
	 * @param conf die Gatherconf zu der Webpage
	 * @param warcFilename der Dateiname der WARC-Datei, ohne die Endung .warc.gz
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
	public Node createWebpageVersion(Node n, Gatherconf conf, String warcFilename,
			File outDir, String localpath, String versionPid, String label,
			String owDatestamp) {
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
			WebgatherLogger.debug(msg);

			/**
			 * NEU für inkrementelles Crawlern (= warc-Deduplizierung): Nach
			 * erfolgreicher Anlage einer WebpageVersion wird die neue CDX-Datei in
			 * das übergeordenete Verzeichnis kopiert und umbenannt. Beim nächsten
			 * Crawl wird die CDX-Datei von diesem Ort wieder abgeholt werden.
			 * 
			 * @author Ingolf Kuss
			 * @date 2025-03-13
			 */
			File cdxFileNew =
					new File(outDir.getAbsolutePath() + "/" + warcFilename + ".cdx");
			if (cdxFileNew.exists()) {
				File cdxFileSave = new File(Play.application().configuration()
						.getString("regal-api.wpull.outDir") + "/" + conf.getName()
						+ "/WEB-" + WebgatherUtils.getDomain(conf.getUrl()) + ".cdx");
				FileUtils.copyFile(cdxFileNew, cdxFileSave);
				WebgatherLogger.debug(
						"Aktuelle CDX-Datei abgelegt in: " + cdxFileSave.getAbsolutePath());
			}

			/*
			 * Im Falle von veröffentlichten Websites soll die neue Version sofort
			 * veröffentlicht werden
			 */
			if (n.getAccessScheme().equals("public")) {
				WebsiteVersionPublisher.createSoftlinkInPublicData(webpageVersion,
						conf);
			}

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
			String warcFilename = new File(warcPath).getName();
			ApplicationLogger.debug("WARC file name: " + warcFilename);
			String localpath = Globals.wgetData + "/wget-data" + uriPath;
			ApplicationLogger.debug("URI-Path to WARC " + localpath);

			return createWebpageVersion(n, conf, warcFilename, crawlDir, localpath,
					versionPid, label, crawlDateTimestamp);

		} catch (Exception e) {
			ApplicationLogger.error(
					"Import der WebsiteVersion {} zu Webpage {} ist fehlgeschlagen !",
					versionPid, n.getPid());
			throw new RuntimeException(e);
		}
	}

	/**
	 * Diese Methode legt eine WebpageVersion (Objekt-Typ "Node") für eine
	 * bestehende WARC-Datei an. Es wird angenommen, dass diese WARC-Datei in
	 * einem angemounteten Datenverzeichnis eines anderen toscience-Servers
	 * (Quellserver) liegt. Auf diese Weise können Webschnitte von einem auf den
	 * anderen Server übernommen werden.
	 * 
	 * @param n Node, must be of type webpage. The local Node to which a
	 *          WebpageVersion should be added.
	 * @param versionPid die gewünschte Pid für die Version (7-stellig numerisch)
	 *          oder leer (Pid wird generiert)
	 * @param quellserverWebpagePid The PID of the source webpage on the source
	 *          server
	 * @param quellserverWebschnittPid The PID of the source WebpageVersion
	 *          (Webschnitt) on the source server
	 * @param deleteQuellserverWebschnitt Option, ob Webschnitt auf dem
	 *          Quellserver am Ende gelöscht werden soll und falls ja, mit oder
	 *          ohne Webarchive
	 * @throws RuntimeException eine Ausnahmebehandlung
	 */
	public void importWebpageVersion(Node n, String versionPid,
			String quellserverWebpagePid, String quellserverWebschnittPid,
			String deleteQuellserverWebschnitt) throws RuntimeException {

		Gatherconf conf = null;
		try {

			if (!"webpage".equals(n.getContentType())) {
				throw new HttpArchiveException(400, n.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			ApplicationLogger
					.debug("Import webpageVersion for lokale Webpage " + n.getPid());

			// Hole Gatherconf des Quellwebschnittes vom Quellserver
			conf = new Read().readRemoteConf(quellserverWebschnittPid);

			// Parse localdir der Gatherconf vom Quellserver
			String localdir = conf.getLocalDir();
			ApplicationLogger.debug("remote localdir: " + localdir);
			int indexOfPid = localdir.indexOf(quellserverWebpagePid);
			if (indexOfPid < 0) {
				throw new RuntimeException("localdir " + localdir
						+ " does not contain Webpage-ID " + quellserverWebpagePid);
			}
			String datetime =
					localdir.substring(indexOfPid + quellserverWebpagePid.length() + 1);
			ApplicationLogger.debug("datetime: " + datetime);

			// Überschreibe Felder der remote Conf mit lokalen Werten
			conf.setName(n.getPid());
			conf.setId(versionPid); // kann hier noch null sein
			Date startDate = new SimpleDateFormat("yyyyMMddHHmmss").parse(datetime);
			conf.setStartDate(startDate);
			/*
			 * die Quellserver Webpage PID wird in der lokalen Gatherconf gespeichert,
			 * auch die Quellserver Webschnitt PID.
			 */
			conf.setQuellserverWebpagePid(quellserverWebpagePid);
			conf.setQuellserverWebschnittPid(quellserverWebschnittPid);
			conf.setDeleteOptionQuellserverWebschnitt(deleteQuellserverWebschnitt);

			// Erzeuge lokales Datenverzeichnis localpath und
			// hole angemountetes Datenverzeichnis remotepath
			String localpath = null;
			String outdir = Play.application().configuration()
					.getString("regal-api." + conf.getCrawlerSelection() + ".outDir");
			if (outdir == null || outdir.isEmpty()) {
				throw new RuntimeException(
						"Unknown local path \"outDir\" ! application env var \"regal-api."
								+ conf.getCrawlerSelection() + ".ourDir not found!");
			}
			localpath = outdir + "/" + conf.getName() + "/" + datetime;
			ApplicationLogger.debug("localpath = " + localpath);

			String remotepath = null;
			String importHome = Play.application().configuration()
					.getString("regal-api." + conf.getCrawlerSelection() + ".importHome");
			if (importHome == null || importHome.isEmpty()) {
				throw new RuntimeException(
						"Unknown locally mountet path \"importHome\" ! application env var \"regal-api."
								+ conf.getCrawlerSelection() + ".impotHome not found!");
			}
			remotepath = importHome + "/" + quellserverWebpagePid + "/" + datetime;
			ApplicationLogger.debug("remotepath = " + remotepath);

			File localfile = new File(localpath);
			if (!localfile.exists()) {
				ApplicationLogger.debug("Create local directory " + localpath);
				localfile.mkdirs();
			}

			/**
			 * KS 27.11.2025: Aus Konsistenzgünden wird auch die Conf am Elternobjekt
			 * (Webpage) aktualisiert. Bei neuen Crawls ist das nicht notwendig, da
			 * der neueste Crawl immer mit der aktuellen Conf ausgeführt wird, so dass
			 * die Confs sowieso schon gleich sind. Seit Implementierung des
			 * WebpageVersionImporters können hier jedoch auch Crawls aus anderen
			 * Systemen übernommen werden. Die Conf des Elternobjektes sollte nach
			 * einer solchen Übernahme mit der Conf des importierten Webschnittes
			 * übereinstimmen, danit zukünftige Crawls die richtigen Cawl-Parameter,
			 * nämlich die von dem importierten Crawl, erben.
			 */
			String msg = new Modify().updateConf(n, conf.toString());
			WebgatherLogger.debug(msg);

			/*
			 * Jetzt in einen Thread verzweigen zwecks paralleler Verabeitung (copies
			 * large files)
			 */
			WebpageVersionImporter importThread = new WebpageVersionImporter();
			importThread.setNode(n);
			importThread.setConf(conf);
			importThread.setDatetime(datetime);
			importThread.setLocalpath(localpath);
			importThread.setRenotepath(remotepath);
			importThread.setVersionPid(versionPid);
			importThread.setQuellserverWebschnittPid(quellserverWebschnittPid);
			importThread
					.setDeleteOptionQuellserverWebschnitt(deleteQuellserverWebschnitt);
			importThread.start();
			// fertig

		} catch (Exception e) {
			ApplicationLogger.debug(e.toString());
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
	 * @param crawlerSelection der Name des für diesen Crawl verwendeten
	 *          Webcrawlers gem. Aufzählung in der Klasse Gatherconf
	 * @param timestamp Der Zeitstempel des Crawl. Ist auch Name des
	 *          Unterverzeichnisses für den Crawl. Aus dem Datum wird der
	 *          Bezeichner (Label auf der UI) für den Webschnitt generiert.
	 * @param filename Der Dateiname der Archivdatei (ohne Pfadangaben, aber mit
	 *          Dateiendung) (WARC-Archiv).
	 * @return a new website version pointing to the posted crawl.
	 */
	public Node postWebpageVersion(Node n, String versionPid,
			String crawlerSelection, String timestamp, String filename) {
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
			ApplicationLogger.debug("Crawl Startdate: " + startDate);

			String dataDir = Play.application().configuration()
					.getString("regal-api." + crawlerSelection + ".outDir");
			ApplicationLogger.debug("dataDir: " + dataDir);
			// hier auf ein bestehendes WARC in dataDir verweisen
			File outDir = new File(dataDir + "/" + conf.getName() + "/" + timestamp);
			ApplicationLogger.debug("outDir (localpath): " + outDir);
			String filenameFound = null;
			if (filename == null || filename.isEmpty()) {
				ApplicationLogger.debug(
						"WARC filename will be determined from the contents of directory "
								+ outDir);
				findWarcFilename(outDir.toPath());
				filenameFound = this.warcFilename;
			} else {
				filenameFound = filename;
			}
			ApplicationLogger.debug("filenameFound: " + filenameFound);
			String localDataUrl =
					Globals.heritrixData + "/" + conf.getCrawlerSelection() + "-data/"
							+ conf.getName() + "/" + timestamp + "/" + filenameFound;

			ApplicationLogger.debug("URI-Path to WARC " + localDataUrl);
			String warcFilenameBase = filenameFound.replaceAll(".warc.gz$", "");
			ApplicationLogger.debug("WARC file name base: " + warcFilenameBase);

			return createWebpageVersion(n, conf, warcFilenameBase, outDir,
					localDataUrl, versionPid);

		} catch (Exception e) {
			ApplicationLogger.error(
					"Anlegen der WebsiteVersion {} zu Webpage {} ist fehlgeschlagen !",
					timestamp, n.getPid());
			throw new RuntimeException(e);
		}
	}

	private void findWarcFilename(Path localpath) throws IOException {
		this.warcFilename = null;
		try (Stream<Path> stream = Files.walk(localpath)) {
			stream.forEach(source -> {
				try {
					String filename = source.getFileName().toString();
					ApplicationLogger.info("found filename = " + filename);
					if (filename.endsWith(".warc.gz")) {
						ApplicationLogger.info("Found warc Filename = " + filename);
						this.warcFilename = filename;
					}
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			});
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

	/**
	 * Diese Methode legt einen Node vom contentType "webpage" an. Der Node kann
	 * dabei mit einem vorläufigen Titel (in den Metadaten) sowie mit einer
	 * Konfigurationsdatei für das Webgathering (Gatherconf) mit URL und
	 * Intervallangabe versehen werden.
	 * 
	 * @author Ingolf Kuss 21.01.2025 | Neuanlage für TOS-1178
	 *
	 * @param namespace Der Namensraum, in der die PID angelegt werden soll
	 * @param url Die URL, die gesammelt werdn soll
	 * @param title Der vorläufige Titel der Webpage
	 * @param intervall Das Sammelintervall
	 * @param pid Die PID (persistenter Identifier) für die Webpage. Falls null
	 *          oder leer, wird ein neuer PID angelegt.
	 * @param object ToScienceObject für die neue Webpage
	 * @return Der modifizierte Node vom contentType webpage
	 */
	public Node createWebpage(String namespace, String url, String title,
			String intervall, String pid, ToScienceObject object) {
		try {
			ApplicationLogger.debug("Create Webpage for url: " + url + ", title: "
					+ title + ", intervall: " + intervall + ", pid: " + pid);

			object.setContentType("webpage");
			object.setAccessScheme("restricted");
			Node node = null;
			if (pid == null || pid.length() == 0) {
				node = createResource(namespace, object);
			} else {
				node = createResource(pid, namespace, object);
			}
			ApplicationLogger
					.debug("INFO Webpage mit PID " + node.getPid() + " erzeugt.");

			/* Erzeuge Metadaten mit einem Titel */
			new actions.Modify().updateLobidify2AndEnrichMetadata(node,
					"<" + node.getPid() + "> <http://purl.org/dc/terms/title> \"" + title
							+ "\" .");

			/*
			 * Erzeuge eine Konfigurationsdatei für das Crawling (Gatherconf)
			 */
			Gatherconf conf = new Gatherconf();
			conf.setUrl(WebgatherUtils.convertUnicodeURLToAscii(url));
			conf.setName(node.getPid());
			conf.setDeepness(-1);
			if (intervall == null || intervall.length() == 0) {
				conf.setInterval(Interval.halfYearly);
			} else if (intervall.equals("halbjährlich")) {
				conf.setInterval(Interval.halfYearly);
			} else if (intervall.equals("einmal jährlich")
					|| intervall.equals("einmal Jährlich")) {
				conf.setInterval(Interval.annually);
			} else if (intervall.equals("einmalig")) {
				conf.setInterval(Interval.once);
			} else {
				conf.setInterval(Interval.halfYearly);
			}
			conf.setRobotsPolicy(RobotsPolicy.ignore);
			conf.setStartDate(new Date());
			// node.setConf(conf.toString()); braucht man das ?
			new actions.Modify().updateConf(node, conf.toString());
			// node = updateResource(node); braucht man das ?

			ApplicationLogger.info("Successfully created webpage " + node.getPid()
					+ "  with title " + title + " !");
			return node;

		} catch (Exception e) {
			ApplicationLogger.error(
					"Creation of webpage in namespace {} for URL {} has failed !\n\tReason: {}",
					namespace, url, e.getMessage());
			ApplicationLogger.debug("", e);
			throw new RuntimeException(e);
		}
	}

} /* END of Class Create */
