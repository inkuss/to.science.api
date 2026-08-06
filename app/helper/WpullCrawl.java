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

import models.CrawlerModel;
import models.CrawlerModel.CrawlControllerState;
import models.Gatherconf;
import models.Gatherconf.AgentIdSelection;
import models.Gatherconf.RobotsPolicy;
import models.Gatherconf.QuotaUnitSelection;
import models.Globals;
import models.Node;
import play.Play;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import java.util.Hashtable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

/**
 * a class to implement a wpull crawl
 * 
 * @author Ingolf Kuss
 *
 */
public class WpullCrawl extends CrawlerModel {

	final static String start_and_disconnect = Play.application().configuration()
			.getString("regal-api.start_and_disconnect");
	final static String crawler =
			Play.application().configuration().getString("regal-api.wpull.crawler");
	final static String tempJobDir = Play.application().configuration()
			.getString("regal-api.wpull.tempJobDir");
	private File tempCrawlDir = null;
	final static String finishedDir = Play.application().configuration()
			.getString("regal-api.wpull.finishedDir");
	private File finishedFile = null;
	private File logAnalysesDir = null;
	private BufferedReader buf;

	/**
	 * Konstruktor zu WpullCrawl
	 * 
	 * @param node der Knoten der Website, zu der ein neuer Crawl gestartet werden
	 *          soll.
	 * @param conf the crawler configuration for the website
	 */
	public WpullCrawl(Node node, Gatherconf conf) {
		super(node, conf);
		try {
			/**
			 * jobDir ist das Arbeitsverzeichnis für die CDN-Crawls. Mit toscience-ID
			 * und Zeitstempel versehen heißt das Verzeichnis crawlDir.
			 */
			this.setJobDir(Play.application().configuration()
					.getString("regal-api.wpull.jobDir"));
			this.setCrawlDir(new File(
					this.getJobDir() + "/" + conf.getName() + "/" + getDatetime()));
			/**
			 * Die Schreibzugriffe von wpull (Downloads) erfolgen in ein lokales
			 * Verzeichnis tempJobDir hinein. Das ist das Arbeitsverzeichnis von
			 * wpull. Mit toscience-ID und Zeitstempel versehen heißt das Verzeichnis
			 * tempCrawlDir.
			 */
			tempCrawlDir =
					new File(tempJobDir + "/" + conf.getName() + "/" + getDatetime());
			/**
			 * Nach erfolgreichem Crawl verschiebt wpulll die Archivdateien in das
			 * Verzeichnis finishedDir. Von hier werden die Dateien in periodischen
			 * Abständen (per cronjob) abgeholt und an ihren endgültigen Speicherort
			 * verschoben. Mit toscience-ID und Zeitstempel versehen heißt das
			 * Verzeichnis finishedFile.
			 */
			finishedFile =
					new File(finishedDir + "/" + conf.getName() + "/" + getDatetime());
			/**
			 * Im Verzeichnis outDir liegen die fertigen Crawls, hier werden sie
			 * endgültig gespeichert. Von hier aus werden die Crawls entweder direkt
			 * von Wayback indexiert. Mit toscience-ID und Zeitstempel versehen heißt
			 * das Verzeichnis resultDir.
			 */
			this.setOutDir(Play.application().configuration()
					.getString("regal-api.wpull.outDir"));
			this.setResultDir(new File(
					this.getOutDir() + "/" + conf.getName() + "/" + getDatetime()));

			this.setCdxFile(new File(this.getOutDir() + "/" + conf.getName() + "/WEB-"
					+ getHost() + ".cdx"));

			this.logAnalysesDir = new File(crawlreportsDir + "/" + "logAnalyses/"
					+ conf.getName() + "/" + getDatetime());
			/*
			 * Die URI localpath wird von Fedora benötigt, um ein Objekt anlegen zu
			 * können. Ohne "localpath" wird im Frontend kein Link zur Wayback
			 * erzeugt.
			 */
			setLocalpath(Globals.heritrixData + "/wpull-data" + "/" + conf.getName()
					+ "/" + getDatetime() + "/" + getWarcFilename() + ".warc.gz");
			setDomains(conf.getDomains());
		} catch (Exception e) {
			WebgatherLogger.error("Ungültige URL :" + conf.getUrl() + " !");
			throw new RuntimeException(e);
		}
	}

	/**
	 * Erzeugt einen neuen Wpull-Crawler-Job
	 */
	@Override
	public void createCrawl() {
		super.createCrawl();
		if (!tempCrawlDir.exists()) {
			// create temp crawl directory
			WebgatherLogger.debug("Create temp crawl directory " + tempJobDir + "/"
					+ getConf().getName() + "/" + getDatetime());
			tempCrawlDir.mkdirs();
		}
		if (!finishedFile.exists()) {
			// create directory for finished crawls
			WebgatherLogger.debug("Create directory for finished crawls "
					+ finishedDir + "/" + getConf().getName() + "/" + getDatetime());
			finishedFile.mkdirs();
		}
		/**
		 * Dieser Codeblock wird für das inkrementelle Crawling benötigt. Es wird
		 * geschaut, ob eine CDX-Datei für diese Webpage existiert. Eine CDX-Datei
		 * enthält eine Liste bereits gesammelter URLs für diese Webpage. Falls eine
		 * CDX-Datei existiert, wird sie in das Arbeitsverzeichnis tempJobDir
		 * kopiert und entsprechend so umbenannt, dass der neue Crawl sie weiter
		 * schreiben wird.
		 * 
		 * @author Ingolf Kuss
		 * @date 2026-04-27
		 */
		try {
			if (getCdxFile().exists()) {
				WebgatherLogger
						.debug("CDX-Datei gefunden: " + getCdxFile().getAbsolutePath());
				setCdxFileNew(new File(
						tempCrawlDir.getAbsolutePath() + "/" + getWarcFilename() + ".cdx"));
				FileUtils.copyFile(getCdxFile(), getCdxFileNew());
				WebgatherLogger.debug(
						"Neue CDX-Datei angelegt: " + getCdxFileNew().getAbsolutePath());
			}
		} catch (IOException e) {
			WebgatherLogger.warn("Neue CDX-Datei " + getCdxFileNew().getAbsolutePath()
					+ " kann nicht angelegt werden!", e.toString());
		}
	}

	/**
	 * Ruft den CDN-Gatherer für diese Website auf, anschließend wpull für den
	 * Hauptcrawl
	 */
	public void startCrawl() {

		try {
			// Erzeuge einen Thread für den Hauptcrawl
			WpullThread wpullThread = new WpullThread(this, 1);
			wpullThread.setNode(getNode());
			wpullThread.setConf(getConf());
			wpullThread.setCrawlDir(tempCrawlDir);
			wpullThread.setFinishedDir(finishedFile);
			wpullThread.setOutDir(getResultDir());
			wpullThread.setWarcFilename(getWarcFilename());
			wpullThread.setHost(getHost());
			wpullThread.setLocalPath(getLocalpath());
			wpullThread.setExecuteCommand(buildExecCommand());
			wpullThread.setDomains(getDomains());
			wpullThread.setDaemon(false);

			// Dies führt zunächst den CDN-Precrawl aus, dann den Hauptcrawl.
			boolean wait = true;
			super.startCrawl(wpullThread, wait);

			/*
			 * Da hier nicht gewartet wird, ist das Setzen des Exit-Status hier
			 * eigentlich Blödsinn; Es steht immer "0" drin.
			 */
			setExitState(wpullThread.getExitState());

		} catch (Exception e) {
			WebgatherLogger.error(e.toString());
			throw new RuntimeException("wpull crawl not successfully started!", e);
		}
	}

	/**
	 * Builds a shell executable command which starts a wpull crawl (Hauptcrawl)
	 * 
	 * For wpull parameters in use see:
	 * http://wpull.readthedocs.io/en/master/options.html If marked as mandatory,
	 * parameter is needed for running smoothly in edoweb context. So only remove
	 * them if reasonable.
	 * 
	 * @return the ExecCommand for wpull
	 */
	private String buildExecCommand() {
		StringBuilder sb = new StringBuilder();
		sb.append(start_and_disconnect + " " + crawler + " " + getUrlAscii());

		if (getConf().getCookie() != null && !getConf().getCookie().isEmpty()) {
			sb.append(" --header=Cookie:%20"
					+ getConf().getCookie().replaceAll(" ", "%20"));
		}

		sb.append(" --recursive");
		ArrayList<String> urlsExcluded = getConf().getUrlsExcluded();
		for (int i = 0; i < urlsExcluded.size(); i++) {
			sb.append(" --reject-regex=.*" + urlsExcluded.get(i).trim());
		}

		int level = getConf().getDeepness();
		if (level > 0) {
			sb.append(" --level=" + Integer.toString(level)); // number of recursions
		}

		long maxByte = getConf().getMaxCrawlSize();
		if (maxByte > 0) {
			QuotaUnitSelection qFactor = getConf().getQuotaUnitSelection();
			Hashtable<QuotaUnitSelection, Integer> sizeFactor = new Hashtable<>();
			sizeFactor.put(QuotaUnitSelection.KB, 1024);
			sizeFactor.put(QuotaUnitSelection.MB, 1048576);
			sizeFactor.put(QuotaUnitSelection.GB, 1073741824);

			long size = maxByte * sizeFactor.get(qFactor).longValue();
			sb.append(" --quota=" + Long.toString(size));
		}

		int waitSec = getConf().getWaitSecBtRequests();
		if (waitSec != 0) {
			sb.append(" --wait=" + Integer.toString(waitSec)); // number of second
																													// wpull waits between
																													// requests
		} else {
			boolean random = getConf().isRandomWait();
			if (random == true) {
				sb.append(" --random-wait"); // randomize wait times
			}
		}

		int tries = getConf().getTries();
		if (tries != 0) {
			sb.append(" --tries=" + Integer.toString(tries)); // number of requests
																												// wpull performs on
																												// transient errors
		}

		int waitRetry = getConf().getWaitRetry();
		if (waitRetry != 0) {
			sb.append(" --waitretry=" + Integer.toString(waitRetry)); // wait between
																																// re-tries
		}

		// select agent-string for http-request
		AgentIdSelection agentId = getConf().getAgentIdSelection();
		sb.append(" --user-agent=" + Gatherconf.agentTable.get(agentId));

		sb.append(" --link-extractors=javascript,html,css");
		sb.append(" --warc-file=" + getWarcFilename());
		if (getConf().getRobotsPolicy().equals(RobotsPolicy.classic)
				|| getConf().getRobotsPolicy().equals(RobotsPolicy.ignore)) {
			sb.append(" --no-robots");
		}
		/* Benutze Internet-Protokoll Version 4 */
		sb.append(" -4");
		// sb.append(" --http-proxy=externer-web-proxy.hbz-nrw.de:3128");
		// kommt "Misconfigured redirect"
		sb.append(" --escaped-fragment --strip-session-id");
		sb.append(" --no-host-directories --page-requisites");
		sb.append(" --database=" + getWarcFilename() + ".db");
		sb.append(" --no-check-certificate");
		sb.append(" --no-directories"); // mandatory to prevent runtime errors
		sb.append(" --delete-after"); // mandatory for reducing required disc space
		sb.append(" --convert-links"); // mandatory to rewrite relative urls
		/**
		 * ohne diesen Parameter wird www.facebook.com, www.youtoube.com uvm.
		 * eingesammelt (aktiviert 12.05.2020)
		 */
		sb.append(" --no-strong-redirects");
		/**
		 * um CDN-Crawls und Haupt-Crawl im gleichen Archiv zu bündeln
		 */
		sb.append(" --warc-append");
		// auskommentiert 27.08.2020 für EDOZWO-1026
		// sb.append(" --warc-tempdir=" + tempJobDir)
		sb.append(" --warc-move=" + finishedFile);
		sb.append(" --warc-cdx");
		if (getCdxFileNew() != null && getCdxFileNew().exists()) {
			sb.append(" --warc-dedup=" + getWarcFilename() + ".cdx");
		}
		play.Logger.debug("Built Crawl command: " + sb.toString());
		WebgatherLogger.debug("Built Crawl command: " + sb.toString());
		return sb.toString();
	}

	/**
	 * Suche neuestes Crawler-Logfile. Guckt zuerst in crawlDir
	 * (Arbeitsverzeichnis). Falls dort nichts gefunden, guckt in outDir
	 * (Ergebnisverzeichnis).
	 * 
	 * @param node der Knoten einer Webpage
	 */
	private File findLatestLogFile() {
		File logfile = null;
		File latestCrawlDir =
				Webgatherer.getLatestCrawlDir(Play.application().configuration()
						.getString("regal-api.wpull.tempJobDir"), getNode().getPid());
		File latestOutDir = Webgatherer.getLatestCrawlDir(
				Play.application().configuration().getString("regal-api.wpull.outDir"),
				getNode().getPid());
		if (latestCrawlDir != null) {
			logfile = new File(latestCrawlDir.toString() + "/crawl.log");
		}
		if (logfile == null || !logfile.exists()) {
			if (latestOutDir != null) {
				logfile = new File(latestOutDir.toString() + "/crawl.log");
			}
		}
		return logfile;
	}

	/**
	 * Ermittelt Crawler Exit Status des letzten Crawls. Der Exit-Status ist eine
	 * ganze Zahl. Der Exit-Status ist erst nach Beendigung eines Crawls
	 * verfügbar.
	 * 
	 * @return Crawler Exit Status des letzten wpull-Crawls
	 */
	public int getCrawlExitStatus() {
		File logfile = findLatestLogFile();
		if (logfile == null || !logfile.exists()) {
			WebgatherLogger.warn("Letztes Crawl-Log für PID " + getNode().getPid()
					+ " nicht gefunden.");
			return -2;
		}
		CrawlLog crawlLog = new CrawlLog(logfile);
		crawlLog.parse();
		return crawlLog.getExitStatus();
	}

	/**
	 * Ermittelt den aktuellen Status des zuletzt gestarteten Crawls. Mögliche
	 * Werte sind : NEW - RUNNING - PAUSED (nur Heritrix) - ABORTED (beendet vom
	 * Operator) - CRASHED - FINISHED
	 * 
	 * @return Crawler Status des zuletzt gestarteten wpull-Crawls
	 */
	public CrawlControllerState getCrawlControllerState() {
		// 1. Kein Crawl-Verzeichnis mit crawl.log vorhanden => Status = NEW
		File logfile = findLatestLogFile();
		if (logfile == null || !logfile.exists()) {
			WebgatherLogger.info("Letztes Crawl-Log für PID " + getNode().getPid()
					+ " nicht gefunden.");
			return CrawlControllerState.NEW;
		}
		// 2. Läuft noch => Status = RUNNING
		if (isWpullCrawlRunning()) {
			return CrawlControllerState.RUNNING;
		}
		buf = null;
		String regExp = "^INFO FINISHED.";
		Pattern pattern = Pattern.compile(regExp);
		try {
			buf = new BufferedReader(new FileReader(logfile));
			String line = null;
			while ((line = buf.readLine()) != null) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					return CrawlControllerState.FINISHED;
				}
			}
		} catch (IOException e) {
			WebgatherLogger.warn(
					"Crawl Controller State cannot be defered from crawlLog "
							+ logfile.getAbsolutePath() + "! Assuming CRASHED.",
					e.toString());
		} finally {
			try {
				if (buf != null) {
					buf.close();
				}
			} catch (IOException e) {
				WebgatherLogger.warn("Read Buffer cannot be closed!");
			}
		}
		return CrawlControllerState.CRASHED;
	}

	/**
	 * Ermittelt die Dateigröße einer fertigen Webarchivdatei für wpull Crawls.
	 * Diese ist erst nach Beendigung eines Crawls verfügbar. Falls mehrere
	 * Archivdateien zu diesem Crawl gehören, werden dessen Größen addiert.
	 * 
	 * @return a the Crawl File Size in Bytes.
	 */
	public String getCrawlFileSize() {
		String fileSize = "";
		File outDir = new File(getConf().getLocalDir());
		WebgatherLogger.debug("getCrawlSize: outDir: " + outDir.toString());

		/**
		 * cd nach outDir und dort "du --bytes -c *.warc.gz" absetzen. Davon die
		 * letzte Zeile auswerten; die erste Zahl (Integer) ist das Ergebnis in
		 * Byte.
		 */
		StringBuilder sb = new StringBuilder();
		sb.append("du --bytes -c *.warc.gz");
		WebgatherLogger.debug("Executing shell command: " + sb.toString());
		String[] execArr = { sb.toString() };
		String commandOutput = "";
		try {
			boolean onlyLastLine = true;
			commandOutput = WebgatherUtils.runShellCommandForOutput(execArr, outDir,
					onlyLastLine);
			WebgatherLogger
					.debug("Shell command outputs (only last line): " + commandOutput);
			String regExp = "^([0-9]+)[ \t]+.*$";
			Pattern pattern = Pattern.compile(regExp);
			Matcher matcher = pattern.matcher(commandOutput);
			if (!matcher.find()) {
				throw new RuntimeException("commandOutput " + commandOutput
						+ " can not be parsed as Integer!");
			}
			fileSize = matcher.group(1);
			WebgatherLogger.debug("Found crawlFileSize in outdir " + outDir.toString()
					+ ": " + fileSize);
		} catch (Exception e) {
			WebgatherLogger.error(e.getMessage());
			WebgatherLogger.warn("crawl file size in outDir " + outDir.toString()
					+ " can not be determined!");
		}
		return fileSize;
	}

	/**
	 * Ermittelt, ob ein Crawl nichts eingesammelt hat. Das wird anhand einer
	 * Meldung im Logfile ermittelt.
	 * 
	 * @return wahr (leer bzw. nichts eingesammelt) oder falsch (nicht leer)
	 */
	public boolean isWpullCrawlEmpty() {
		File logfile = findLatestLogFile();
		/**
		 * Kein Crawl-Verzeichnis mit crawl.log vorhanden => wird wie "leer"
		 * behandelt
		 */
		if (logfile == null || !logfile.exists()) {
			WebgatherLogger.warn("Letztes Crawl-Log für PID " + getNode().getPid()
					+ " nicht gefunden.");
			return true;
		}
		buf = null;
		String regExp = "^INFO Downloaded: 0 files, 0.0 B.";
		Pattern pattern = Pattern.compile(regExp);
		boolean isEmpty = false;
		try {
			buf = new BufferedReader(new FileReader(logfile));
			String line = null;
			while ((line = buf.readLine()) != null) {
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					isEmpty = true;
					break;
				}
			}
		} catch (IOException e) {
			WebgatherLogger.warn("Logfile " + logfile.getAbsolutePath()
					+ " can not be parsed or read. Assuming empty.", e.toString());
			isEmpty = true;
		} finally {
			try {
				if (buf != null) {
					buf.close();
				}
			} catch (IOException e) {
				WebgatherLogger.warn("Read Buffer cannot be closed!");
			}
		}
		return isEmpty;
	}

	/**
	 * Prüfung, ob ein Crawl zu einer gegebenen URL aktuell läuft
	 * 
	 * @return boolean Crawl läuft
	 */
	public boolean isWpullCrawlRunning() {
		buf = null;
		String cmd = "ps -eaf";
		String regExp1 =
				Play.application().configuration().getString("regal-api.wpull.crawler");
		Pattern pattern1 = Pattern.compile(regExp1);
		Matcher matcher1 = null;
		try {
			setUrlAscii(WebgatherUtils.convertUnicodeURLToAscii(
					Gatherconf.create(getNode().getConf()).getUrl()));
			String regExp2 = getUrlAscii();
			// Maskiere Sonderzeichen des Regulären Ausdrucks mit Pattern.quote
			Pattern pattern2 = Pattern.compile(Pattern.quote(regExp2));
			Matcher matcher2 = null;
			WebgatherLogger.debug("Setze Systemkommando ab: " + cmd);
			WebgatherLogger.debug("Suche nach wpull-Aufrufen mit url " + regExp2);
			String line;
			Process proc = Runtime.getRuntime().exec(cmd);
			buf = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			while ((line = buf.readLine()) != null) {
				// WebgatherLogger.debug("found line: " + line);
				matcher1 = pattern1.matcher(line);
				if (matcher1.find()) {
					// WebgatherLogger.debug("wpull3 found in line");
					matcher2 = pattern2.matcher(line);
					if (matcher2.find()) {
						WebgatherLogger
								.debug("Found wpull Crawl process for this url=" + line);
						return true;
					}
				}
			}
		} catch (Exception e) {
			WebgatherLogger.warn("Fehler beim Aufruf des Systenkommandos: " + cmd,
					e.toString());
			throw new RuntimeException(
					"Crawl Job Zustand kann nicht bestimmt werden !", e);
		} finally {
			try {
				if (buf != null) {
					buf.close();
				}
			} catch (IOException e) {
				WebgatherLogger.warn("Read Buffer cannot be closed!");
			}
		}
		return false;
	}

	/**
	 * Diese Methode erzeugt symbolische Links für die Log-Analyse via
	 * Browser-Zugriff.
	 * 
	 * @author: I. Kuss (hbz)
	 * @date 2026-03-10
	 * @reference TOS-1273
	 */
	public void createSymLinks() {
		/**
		 * Im resultDir symbolische Links auf die Log- und Textdateien in crawlDir
		 * erzeugen.
		 */
		createSymLink(this.getCrawlDir(), this.getResultDir(), "cdnparse.log");
		createSymLink(this.getCrawlDir(), this.getResultDir(), "cdn.txt");
		createSymLink(this.getCrawlDir(), this.getResultDir(), "hostnames.txt");
		createSymLink(this.getCrawlDir(), this.getResultDir(), "cdncrawl.log");
		createSymLink(this.getCrawlDir(), this.getResultDir(), "crawl.log");
		/**
		 * Symbolische Links in crawlreports/logAnalyses erzuegen, die wiederum auf
		 * diese symbolischen Links im resultDir verweisen. Für TOS-1273.
		 */
		createSymLink(this.getResultDir(), logAnalysesDir, "cdnparse.log");
		createSymLink(this.getResultDir(), logAnalysesDir, "cdn.txt");
		createSymLink(this.getResultDir(), logAnalysesDir, "hostnames.txt");
		createSymLink(this.getResultDir(), logAnalysesDir, "cdncrawl.log");
		createSymLink(this.getResultDir(), logAnalysesDir, "crawl.log");
	}

	/**
	 * Diese Methode erzeugt einen symbolischen Link im Verzeichnis linkDir, der
	 * auf eine Datei namens filename im Verzeichnis fileDir zeigt.
	 * 
	 * @author I. Kuss (hbz)
	 * @date 2026-03-10
	 * 
	 * @param fileDir das Verzeichnis, in dem sich die Datei befindet (Typ File)
	 * @param linkDir das Verzeichnis, in dem die symbolische Verknüpfung angelegt
	 *          werden soll (Typ File)
	 * @param filename der Dateiname (Zeichenkette; ohne Pfadangabe)
	 */
	public void createSymLink(File fileDir, File linkDir, String filename) {
		Path filePath = Paths.get(fileDir.getPath() + "/" + filename);
		Path fileLink = Paths.get(linkDir.getPath() + "/" + filename);
		try {
			Files.createSymbolicLink(fileLink, filePath);
		} catch (IOException e) {
			setMsg("Cannot create symbolic link " + linkDir.getPath() + "/" + filename
					+ " pointing to " + fileDir.getPath() + "/" + filename);
			WebgatherLogger.error(getMsg());
		}
	}

}
