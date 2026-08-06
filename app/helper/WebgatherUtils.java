/*
 * Copyright 2017 hbz NRW (http://www.hbz-nrw.de/)
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.IDN;
import java.net.URI;
import java.net.URL;
import java.text.CharacterIterator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.StringJoiner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.ArrayUtils;

import com.ibm.icu.text.StringCharacterIterator;

import actions.Create;
import helper.mail.Mail;
import models.Gatherconf;
import models.Gatherconf.CrawlerSelection;
import models.Globals;
import models.Message;
import models.Node;
import play.Logger;

/**
 * Eine Klasse mit nützlichen Methoden im Umfeld des Webgatherings
 * 
 * @author I. Kuss, hbz
 */
public class WebgatherUtils {

	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");
	/** Datumsformat für String-Repräsentation von Datümern */
	public static final DateFormat dateFormat =
			new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz");

	/**
	 * Eine Methode zum Validieren und Umwandeln einer URL. Die URL wird nach
	 * ASCII konvertiert, falls sie noch nicht in dieser Kodierung ist. Wahlweise
	 * wird vorher nach Punycode konvertiert. Das Schema kann wahlweise erhalten
	 * (falls vorhanden) oder entfernt werden. Bei ungültigen URL wird eine
	 * URISyntaxException geschmissen.
	 * 
	 * von hier kopiert:
	 * https://nealvs.wordpress.com/2016/01/18/how-to-convert-unicode-url-to-ascii
	 * -in-java/
	 * 
	 * @param url ein Uniform Resource Locator als Zeichenkette
	 * @return eine URL als Zeichenkette
	 */
	public static String convertUnicodeURLToAscii(String url) {
		try {
			URL u = new URL(url);
			URI uri =
					new URI(u.getProtocol(), u.getUserInfo(), IDN.toASCII(u.getHost()),
							u.getPort(), u.getPath(), u.getQuery(), u.getRef());
			String correctEncodedURL = uri.toASCIIString();
			return correctEncodedURL;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Erzeugt eine Nachricht für den Fall, dass eine URL umgezogen ist.
	 * 
	 * @param conf die Crawler-Settings (Gatherconf)
	 * @return die Nachricht
	 */
	public static Message createInvalidUrlMessage(Gatherconf conf) {
		Message msg = null;
		if (conf.getUrlNew() == null) {
			msg = new Message("Die Website ist unbekannt verzogen.\n"
					+ "Bitte geben Sie auf dem Tab \"Crawler settings\" eine neue, gültige URL ein. Solange wird die Website nicht erneut eingesammelt.");
		} else {
			msg = new Message("Die Website ist umgezogen nach " + conf.getUrlNew()
					+ ".\n"
					+ "Bitte bestätigen Sie den Umzug auf dem Tab \"Crawler settings\" (URL kann dort vorher editiert werden).");
		}
		return msg;
	}

	/**
	 * Schickt eine E-Mail an die Leute in der "mail.properties" - Datei.
	 * 
	 * @param node der Node der Website
	 * @param conf die Gatherconf der umgezogenen Website
	 * @param message der Text der Nachricht, die verschickt werden soll
	 * @param subject die Betreffzeile
	 */
	public static void sendEmail(Node node, Gatherconf conf, String subject,
			String message) {
		WebgatherLogger.info("Schicke E-Mail mit Betreff: " + subject);
		try {
			try {
				Mail.sendMail(message, subject);
			} catch (Exception e) {
				throw new RuntimeException("Email could not be sent successfully!");
			}
		} catch (Exception e) {
			WebgatherLogger.warn(e.toString());
		}
	}

	/**
	 * Schickt E-Mail mit einer Umzugsnotiz und Aufforderung, die neue URL zu
	 * bestätigen.
	 * 
	 * @param node der Knoten der Website
	 * @param conf die Gatherconf der umgezogenen Website
	 */
	public static void sendInvalidUrlEmail(Node node, Gatherconf conf) {
		WebgatherLogger.info("Schicke E-Mail mit Umzugsnotiz.");
		try {
			String siteName =
					conf.getName() == null ? node.getAggregationUri() : conf.getName();
			String mailMsg = "Die Website " + siteName + " ist umgezogen.\n";
			mailMsg +=
					"Bitte geben Sie auf diesem Webformular eine neue, gültige URL ein und bestätigen Sie die neue URL\n.";
			mailMsg += "Solange wird diese Website nicht erneut eingesammelt: ";
			mailMsg += Globals.urnbase + node.getAggregationUri() + "/crawler .";
			sendEmail(node, conf, "Die Website " + siteName + " ist umgezogen ! ",
					mailMsg);
		} catch (Exception e) {
			WebgatherLogger.warn(e.toString());
		}
	}

	/**
	 * Diese Methode stößt einen neuen Webcrawl an.
	 * 
	 * @param node must be of type webpage: Die Webpage
	 */
	@SuppressWarnings("null")
	public void startCrawl(Node node) {
		Gatherconf conf = null;
		File crawlDir = null;
		String localpath = null;
		try {
			if (!"webpage".equals(node.getContentType())) {
				throw new HttpArchiveException(400, node.getContentType()
						+ " is not supported. Operation works only on regalType:\"webpage\"");
			}
			WebgatherLogger.debug("Starte Webcrawl für PID: " + node.getPid());
			WebgatherLogger.debug("Gatherer-Konfiguration: " + node.getConf());
			conf = Gatherconf.create(node.getConf());
			conf.setName(node.getPid());
			if (conf.getCrawlerSelection()
					.equals(Gatherconf.CrawlerSelection.heritrix)) {
				if (Globals.heritrix.isBusy()) {
					WebgatherLogger
							.error("Webgatherer is too busy! Please try again later.");
					throw new HttpArchiveException(403,
							"Webgatherer is too busy! Please try again later.");
				}
				// if (!Globals.heritrix.jobExists(conf.getName())) {
				// nicht nur bei Neuanlage, sondern auch, falls crawlerConf im JobDir
				// erneuert werden muss (refresh)
				Globals.heritrix.createJob(conf);
				// }
				boolean success = Globals.heritrix.teardown(conf.getName());
				WebgatherLogger.debug("Teardown " + conf.getName() + " " + success);

				Globals.heritrix.launch(conf.getName());
				WebgatherLogger.debug("Launched " + conf.getName());
				Thread.currentThread().sleep(10000);

				Globals.heritrix.unpause(conf.getName());
				WebgatherLogger.debug("Unpaused " + conf.getName());
				Thread.currentThread().sleep(10000);

				crawlDir = Globals.heritrix.getCurrentCrawlDir(conf.getName());
				String warcPath = Globals.heritrix.findLatestWarc(crawlDir);
				String uriPath = Globals.heritrix.getUriPath(warcPath);
				String warcFilename = new File(warcPath).getName();
				WebgatherLogger.debug("WARC file name: " + warcFilename);

				localpath = Globals.heritrixData + "/heritrix-data" + "/" + uriPath;
				WebgatherLogger.debug("Path to WARC " + localpath);
				String versionPid = null;
				new Create().createWebpageVersion(node, conf,
						CrawlerSelection.heritrix.toString(), warcFilename, crawlDir,
						localpath, versionPid);
			} else if (conf.getCrawlerSelection()
					.equals(Gatherconf.CrawlerSelection.wpull)) {
				WpullCrawl wpullCrawl = new WpullCrawl(node, conf);
				wpullCrawl.createCrawl();
				/**
				 * Startet Job in neuem Thread, einschließlich CDN-Precrawl
				 */
				wpullCrawl.startCrawl();
				crawlDir = wpullCrawl.getCrawlDir();
				// localpath = wpullCrawl.getLocalpath();
				if (wpullCrawl.getExitState() != 0) {
					throw new RuntimeException("Crawl job returns with exit state "
							+ wpullCrawl.getExitState() + "!");
				}
				WebgatherLogger
						.debug("Path to WARC (crawldir):" + crawlDir.getAbsolutePath());
			} else if (conf.getCrawlerSelection()
					.equals(Gatherconf.CrawlerSelection.btrix)) {
				BtrixWebclient btrixWorkflow = new BtrixWebclient(node, conf);
				btrixWorkflow.createCrawl();
				btrixWorkflow.startCrawl();
			} else {
				throw new RuntimeException(
						"Unknown crawler selection " + conf.getCrawlerSelection() + "!");
			}
		} catch (Exception e) {
			// WebgatherExceptionMail.sendMail(n.getPid(), conf.getUrl());
			WebgatherLogger.warn("Crawl of Webpage " + node.getPid() + ","
					+ conf.getUrl() + " has failed !\n\tReason: " + e.getMessage());
			WebgatherLogger.debug("", e);
			throw new RuntimeException(e);
		}

	} // Ende startCrawl

	/**
	 * 
	 * Diese Routine ermittelt die toscience-ID des im letzten Nachtlauf zuletzt
	 * angestarteten Webpage-Crawls. Zum Wiederaufsetzen an dieser Stelle wichtig.
	 * 
	 * @author Ingolf Kuss
	 * @return die ID der zuletzt gecrawlten Webpage
	 */
	public static String readLastlyCrawledWebpageId() {
		// hier die letzte ID aus einer Datei auslesen
		String fileName = getFileNameLastlyCrawledWebpageId();
		String lastId = Globals.defaultNamespace + ":0";
		try {
			FileReader fr = new FileReader(fileName);
			BufferedReader br = new BufferedReader(fr);
			lastId = br.readLine();
			WebgatherLogger.debug("Found lastly crawled webpage id: " + lastId);
			br.close();
		} catch (IOException e) {
			WebgatherLogger
					.debug("WARN: lastly crawled webpage id could not be read from file ("
							+ fileName + ")!");
			lastId = Globals.defaultNamespace + ":0";
			WebgatherLogger.debug("Using default id: " + lastId);
		}
		return lastId;
	}

	/**
	 * Diese Methode gibt den Namen der Datei zurück, in der sich der Webcrawler
	 * die ID der in Nachtläufen zuletzt gecrawlten Webpage merkt.
	 * 
	 * @author I. Kuss
	 * @return fileName
	 */
	public static String getFileNameLastlyCrawledWebpageId() {
		String fileName = Globals.lastlyCrawledWebpageIdFile;
		if (fileName == null || fileName.isEmpty()) {
			fileName = "/tmp/lastlyCrawledWebpageId";
		}
		return fileName;
	}

	/**
	 * Diese Methode ermittelt aus einer URL die Domain.
	 * 
	 * @author Ingolf Kuss
	 * @date 2025-03-13
	 * @param url eine URL
	 * @return die Domain der URL
	 */
	public static String getDomain(String url) {
		return url.replaceAll("^http://", "").replaceAll("^https://", "")
				.replaceAll("/.*$", "");
	}

	/**
	 * Diese Methode entpackt ein ZIP-Archiv. Sie macht dasselbe wie der
	 * Unix/Linux-Befehl unzip. Quelle:
	 * https://www.geeksforgeeks.org/java/how-to-zip-and-unzip-files-in-java/
	 * 
	 * @author I. Kuss, hbz
	 * @date 2026-04-23
	 * @param zipFile der volle Pfadname eines ZIP-Archivs (Dateiendung .zip)
	 * @param destFolder der volle Pfadname eines Dateiordners, in dem das
	 *          ZIP-Archiv ausgepackt werden soll
	 * @throws IOException eine Ausnahmebehandlung
	 */
	public static void unzip(String zipFile, String destFolder)
			throws IOException {
		try (
				ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
			ZipEntry entry;
			byte[] buffer = new byte[1024];
			while ((entry = zis.getNextEntry()) != null) {
				File newFile = new File(destFolder + File.separator + entry.getName());
				if (entry.isDirectory()) {
					newFile.mkdirs();
				} else {
					new File(newFile.getParent()).mkdirs();
					try (FileOutputStream fos = new FileOutputStream(newFile)) {
						int length;
						while ((length = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, length);
						}
					}
				}
			}
		}
	}

	/**
	 * Diese Methode konvertiert eine Integer-Angabe für Bytes in eine
	 * Zeichenkette der Form %d,%1d GiB (MiB oder KiB). Also auf die führende
	 * Mengenangabe mit einer Stelle hinter dem Komma. Quelle:
	 * https://stackoverflow.com/questions/3758606/how-can-i-convert-byte-size-into-a-human-readable-format-in-java
	 * 
	 * @param bytes die Anzahl Bytes als long integer
	 * @return eine Zeichenkette in menschenlesbarem Format für eine Dateigröße
	 */
	@SuppressWarnings("deprecation")
	public static String humanReadableByteCount(long bytes) {
		long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		if (absB < 1024) {
			return bytes + " B";
		}
		long value = absB;
		CharacterIterator ci = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}
		value *= Long.signum(bytes);
		return String.format("%.1f %ciB", value / 1024.0, ci.current());
	}

	/**
	 * Diese Funktion konvertiert eine Integer-Angabe für Sekunden in eine
	 * Zeichenkette der Form %d h %d m %d s. Quelle:
	 * https://stackoverflow.com/questions/3471397/how-can-i-pretty-print-a-duration-in-java
	 * 
	 * @param duration eine Java-"Duration", z.B. Duration duration = new
	 *          Duration(Zeit in Millisekunden);
	 * @return eine menschenlesbare Zeichenkette für eine Zeitdauer
	 */
	public static String humanReadableDuration(Duration duration) {
		return duration.toString().substring(2).replaceAll("(\\d[HMS])(?!$)", "$1 ")
				.toLowerCase();
	}

	/**
	 * Diese Methode führt ein Shell-Kommando aus und gibt das Ergebnis als
	 * Zeichenkette zurück. Quelle:
	 * https://stackoverflow.com/questions/16714127/how-to-redirect-processbuilders-output-to-a-string
	 * 
	 * @author: Ingolf Kuss
	 * @date 2026-08-03
	 * 
	 * @param execArr ein Array von String = das Shell-Kommando (oder mehrere
	 *          Kommandos als Array)
	 * @param localDir das lokale Verzeichnis, in dem das Shell-Kommando
	 *          ausgeführt werden soll.
	 * @param onlyLastLine (boolean): return only the last line of output
	 * @return the output of a shell command (String value)
	 */
	public static String runShellCommandForOutput(String[] execArr, File localDir,
			boolean onlyLastLine) {
		String[] useBash = { "bash", "-c" };
		ProcessBuilder pb =
				new ProcessBuilder((String[]) ArrayUtils.addAll(useBash, execArr));
		assert localDir.isDirectory();
		pb.directory(localDir);
		pb.redirectErrorStream(true);
		// pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
		Process proc;
		String result = "";
		String nextLine = "";
		try {
			proc = pb.start();
			final BufferedReader reader =
					new BufferedReader(new InputStreamReader(proc.getInputStream()));
			StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
			while (reader.lines().iterator().hasNext()) {
				nextLine = reader.lines().iterator().next();
				sj.add(nextLine);
			}
			result = sj.toString();
			proc.waitFor();
			proc.destroy();
		} catch (Exception e) {
			WebgatherLogger.warn("Cannot execute or evaluate shell command!");
			throw new RuntimeException(e);
		}
		if (onlyLastLine) {
			return nextLine;
		}
		return result;
	}

}
