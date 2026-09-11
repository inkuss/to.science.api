package helper;

import java.io.File;
import java.io.InputStream;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.util.ArrayList;
import java.util.List;

import actions.Create;
import models.CrawlerModel;
import models.Gatherconf;
import models.Globals;
import models.Node;
import models.Gatherconf.CrawlSubdomains;
import play.Logger;

/**
 * @author I. Kuss Ein Thread, in dem ein Webcrawl gestartet wird. Der Thread
 *         wartet, bis der Crawl beendet ist. Ist der Crawl mit Fehler beendet,
 *         wird ein neuer Thread aufgerufen, der einen erneuten Crawl-Versuch
 *         macht. Es gibt eine Obergrenze für die Anzahl Crawl-Versuche:
 *         maxNumberAttempts.
 */
public class WpullThread extends Thread {

	private WpullCrawl wpullCrawl = null;
	private Node node = null;
	private Gatherconf conf = null;
	private List<String> title = null;
	private File crawlDir = null;
	private File finishedDir = null;
	private File outDir = null;
	private String warcFilename = null;
	private String host = null; /* = domain */
	private String localpath = null;
	private String executeCommand = null;
	/**
	 * "%20" durch Leerzeichen ersetzt für Ausgabe ins Log
	 */
	private String executeCommandRepl = null;
	private ArrayList<String> domains = null;
	private ProcessBuilder pb = null;
	private File logFile = null;
	private int exitState = 0;
	private String msg = "";
	/**
	 * Der wievielte Versuch ist es, diesen Crawl zu starten ?
	 */
	int attempt = 1;
	// CHG Kuss 1.7.2026: keine weiteren Versuche
	private static int maxNumberAttempts = 1;
	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");

	/**
	 * Der Konstruktor für diese Klasse.
	 * 
	 * @param model a Crawler Model for this wpull crawl
	 * @param attempt Der wievielte Versuch es ist, diesen Webschnitt zu sammeln.
	 */
	public WpullThread(WpullCrawl model, int attempt) {
		this.wpullCrawl = model;
		this.attempt = attempt;
		WebgatherLogger.debug("Instantiating attempt No. " + attempt);
		exitState = 0;
	}

	/**
	 * Die Set-Methode für den Parameter node
	 * 
	 * @param node Der Knoten der Website, für die ein neuer Webschnitt gesammelt
	 *          werden soll.
	 */
	public void setNode(Node node) {
		this.node = node;
	}

	/**
	 * Die Set-Methode für den Parameter conf
	 * 
	 * @param conf Die Gatherconf der Website, die gecrawlt werden soll.
	 */
	public void setConf(Gatherconf conf) {
		this.conf = conf;
	}

	/**
	 * Die Methode, um den Parameter crawlDir zu setzen.
	 * 
	 * @param crawlDir Das Verzeichnis (absoluter Pfad), in das wpull seine
	 *          Ergebnisdateien (z.B. WARC-Datei) schreibt.
	 */
	public void setCrawlDir(File crawlDir) {
		this.crawlDir = crawlDir;
	}

	/**
	 * Die Methode, um den Parameter finishedDir zu setzen.
	 * 
	 * @param finishedDir Das Verzeichnis (absoluter Pfad), in das wpull seine
	 *          fertigen Webarchive (per --warc-move) verschiebt.
	 */
	public void setFinishedDir(File finishedDir) {
		this.finishedDir = finishedDir;
	}

	/**
	 * Die Methode, um den Parameter outDir zu setzen.
	 * 
	 * @param outDir Das Verzeichnis (absoluter Pfad), in dem das Endergebnis,
	 *          also der fertig gecrawlte Webschnitt, liegt. Die Crawl-Datei wird
	 *          ggfs. erst nach Ende eines erfolgrecihen Crawls in dieses
	 *          Verzeichnis herein geschoben (bei wpull-Parameter warc-move).
	 */
	public void setOutDir(File outDir) {
		this.outDir = outDir;
	}

	/**
	 * Die Methode, um den Parameter warcFilename zu setzen.
	 * 
	 * @param warcFilename Der Dateiname (kein Pfad, auch keine Endung !) für die
	 *          WARC-Datei.
	 */
	public void setWarcFilename(String warcFilename) {
		this.warcFilename = warcFilename;
	}

	/**
	 * Die Methode, um den Parameter host zu setzen.
	 * 
	 * @param host Der Hostname der zu crawlenden URL; ohne Protokollangaben
	 *          (http[s]//:) und ohne Pfadangaben (/.*$)
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * Die Methode, um den Parameter localpath zu setzen.
	 * 
	 * @param localpath Eine URI, unter der die Archivdatei lokal gepeichert ist.
	 *          Fedora benötigt diesesn Parametern, um ein "gemanagtes" Objekt
	 *          anlegen zu können.
	 */
	public void setLocalPath(String localpath) {
		this.localpath = localpath;
	}

	/**
	 * Die Methode, um die Liste "domains" zu setzen. Die Liste "domains" stammt
	 * aus der Gatherconf, kann aber durch den Precrawl angereichert worden sein.
	 * 
	 * @param domains die Liste "domains" mit zusätzlichen Domains, die auch
	 *          gecrwalt werden sollen.
	 */
	public void setDomains(ArrayList<String> domains) {
		this.domains = domains;
	}

	/**
	 * Die Methode, um das Aufrufkommando (wpull) für den Hauptcrawl zu setzen
	 * 
	 * @param executeCommand das Aufrufkommando für den Hauptcrawl
	 */
	public void setExecuteCommand(String executeCommand) {
		this.executeCommand = executeCommand;
	}

	/**
	 * Die Methode, um exit State auszulesen
	 * 
	 * @return exitState ist der Return-Wert von wpull.
	 */
	public int getExitState() {
		return this.exitState;
	}

	/**
	 * This methods starts a webcrawl and waits for completion.
	 */
	@Override
	public void run() {
		WebgatherLogger.debug("Start runnig Wpull Crawl.");
		try {
			boolean noParent = true;
			String zusDomain = null;
			String zusHost = null;
			if ((domains != null && domains.size() > 0)
					|| conf.getCrawlSubdomains().equals(CrawlSubdomains.domains)) {
				executeCommand += " --span-hosts";
				if (conf.getCrawlSubdomains().equals(CrawlSubdomains.domains)) {
					executeCommand += " --domains=" + host.replaceAll("^www.", "");
				} else {
					executeCommand += " --hostnames=" + host;
				}
				if (domains != null) {
					for (int i = 0; i < domains.size(); i++) {
						zusDomain = domains.get(i);
						zusHost = WebgatherUtils.getDomain(zusDomain);
						WebgatherLogger.debug("zusHost=" + zusHost);
						if (zusHost.equalsIgnoreCase(host)) {
							WebgatherLogger.debug("Es soll von der gesamten Domain " + host
									+ " eingesammelt werden, die Option --no-parent wird entfernt.");
							noParent = false;
						} else {
							executeCommand += "," + zusHost;
						}
					}
				}
			}
			if (noParent) {
				executeCommand += " --no-parent";
			}

			// 3. Ausführung des Hauptcrawls (URL)
			String[] execArr = executeCommand.split(" ");
			// unmask spaces in exec command
			for (int i = 0; i < execArr.length; i++) {
				execArr[i] = execArr[i].replaceAll("%20", " ");
			}
			executeCommandRepl = new String(executeCommand);
			executeCommandRepl = executeCommandRepl.replaceAll("%20", " ");
			WebgatherLogger.info("Executing command " + executeCommandRepl);
			// Logdatei für den Hauptcrawl anlegen
			logFile = new File(crawlDir.toString() + "/crawl.log");
			logFile.createNewFile();
			WebgatherLogger.info("Logfile = " + crawlDir.toString() + "/crawl.log");
			pb = new ProcessBuilder(execArr);
			assert crawlDir.isDirectory();
			pb.directory(crawlDir);
			pb.redirectErrorStream(true);
			pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

			Process proc = pb.start();
			assert pb.redirectInput() == ProcessBuilder.Redirect.PIPE;
			assert pb.redirectOutput().file() == logFile;
			InputStream inputStream = proc.getInputStream();
			assert inputStream.read() == -1;
			exitState = proc.waitFor();
			/**
			 * Exit-Status: 0 = Crawl erfolgreich beendet
			 */
			WebgatherLogger.info("Webcrawl for " + conf.getName()
					+ " exited with exitState " + exitState);
			proc.destroy();
			if (exitState == 0 || exitState == 4 || exitState == 7
					|| exitState == 8) {
				/* der Beobachtung zufolge wird bei exitState == 7 das WARC ge-moved */
				/*
				 * daher legen wir ab jetzt auch einen Webschnitt an. IK20250205 für
				 * TOS-1182 und TOS-1224
				 */
				/**
				 * Die Anlage des Webschnitts wird in einen Cronjob ausgelagert.
				 * KS20260511 siehe TOSDEV-46.
				 */
				WebgatherLogger.info("Webpage " + host + " mit PID " + conf.getName()
						+ " wurde erfolgreich eingesammelt. Finished-Dir: "
						+ finishedDir.toString() + ", Dateiname: " + warcFilename);

				/**
				 * Hier eine Mail schicken, falls nichts eingesammelt wurde. Für
				 * TOS-1326
				 */
				if (wpullCrawl.isWpullCrawlEmpty()) {
					WebgatherLogger.info("Crawl was empty. An E-Mail will be send.");
					title = node.getDublinCoreData().getTitle();
					msg =
							"Für die Website " + conf.getName() + ", Titel: " + title + "\n";
					msg +=
							"Es wurde zwar ein Crawl formell erfolgreich beendet und es wurde ein neuer Webschnitt angelegt.\n";
					msg +=
							"Jedoch wurde lt. Logdatei im Hauptcrawl nichts eingesammelt: \"INFO Downloaded: 0 files, 0.0 Byte.\"\n";
					msg += "Bitte überprüfen Sie den neuesten Webschnitt dieser Website: "
							+ Globals.urnbase + node.getAggregationUri();
					WebgatherUtils.sendEmail(node, conf,
							"Keine Inhalte eingesammelt! Für Website " + conf.getName()
									+ ", Titel: " + title,
							msg);
				}
				return;
			}

			// Keep warc file of failed crawl
			// KS 20260701: gescheiterter Crawl: warc-Datei wird nicht umbenannt.
			/**
			 * File warcFile = new File(crawlDir.toString() + "/" + warcFilename +
			 * ".warc.gz"); File warcFileAttempted = new File(crawlDir.toString() +
			 * "/" + warcFilename + ".warc.gz.attempt" + attempt);
			 * warcFile.renameTo(warcFileAttempted); warcFile.delete();
			 */
			// Crawl wird erneut angestoßen
			attempt++;
			if (attempt > maxNumberAttempts) {
				WebgatherLogger.info("Webcrawl for " + conf.getName()
						+ " wurde bereits " + maxNumberAttempts
						+ "-mal angestoßen. Kein weiterer Versuch.");
				WebgatherLogger
						.warn("Webcrawl für " + conf.getName() + "fehlgeschlagen !!");
				/**
				 * ToDo 20210719: Verschicken einer E-Mail !
				 */
				return;
			}
			// KS20260701: Hier kommt er nie mehr hin.
			WebgatherLogger.info("Webcrawl for " + conf.getName()
					+ " wird erneut angestoßen. " + attempt + ". Versuch.");
			pb.directory(crawlDir);
			pb.redirectErrorStream(true);
			WpullThread wpullThread = new WpullThread(wpullCrawl, attempt);
			wpullThread.setNode(node);
			wpullThread.setConf(conf);
			wpullThread.setCrawlDir(crawlDir);
			wpullThread.setFinishedDir(finishedDir);
			wpullThread.setOutDir(outDir);
			wpullThread.setWarcFilename(warcFilename);
			wpullThread.setLocalPath(localpath);
			wpullThread.setExecuteCommand(executeCommand);
			wpullThread.start(); // rekursiver Aufruf
		} catch (Exception e) {
			WebgatherLogger.error(e.toString());
			throw new RuntimeException("wpull crawl not successfully started!", e);
		}
	}

}
