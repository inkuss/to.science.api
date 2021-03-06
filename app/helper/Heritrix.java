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

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;

import models.Gatherconf;
import play.Logger;
import play.Play;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

/**
 * @author Jan Schnasse
 *
 */
public class Heritrix {
	public static String openwaybackLink = Play.application().configuration()
			.getString("regal-api.heritrix.openwaybackLink");

	final String restUrl =
			Play.application().configuration().getString("regal-api.heritrix.rest");

	final String jobDir =
			Play.application().configuration().getString("regal-api.heritrix.jobDir");

	final static Client client = HeritrixWebclient.createWebclient();
	private static final Logger.ALogger WebgatherLogger =
			Logger.of("webgatherer");

	/**
	 * @param name
	 * @return true if teardown did work
	 */
	public boolean teardown(String name) {
		try {
			teardownJobToHeritrix(name);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private String teardownJobToHeritrix(String name) {
		try {
			WebResource resource = client.resource(restUrl + "/engine/job/" + name);
			String response = resource.accept("application/xml").post(String.class,
					"action=teardown");
			WebgatherLogger.info(response);
			return response;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Create job directory with config. Add Job to heritrix
	 * "https://webarchive.jira.com/wiki/display/Heritrix/Heritrix+3.x+API+Guide#Heritrix3.xAPIGuide-AddJobDirectory"
	 * 
	 * "curl -v -d \"action=add&addpath=/Users/hstern/job\" -k -u admin:admin --anyauth --location -H \"Accept:application/xml\" https://localhost:8443/engine"
	 * 
	 * @param conf
	 */
	public void createJob(Gatherconf conf) {
		WebgatherLogger.debug("Create new job " + conf.getName());
		File dir = createJobDir(conf);
		try {
			teardown(conf.getName());
		} catch (RuntimeException e) {
			WebgatherLogger.debug("", e);
		}
		try {
			WebgatherLogger.debug(" addJobDirToHeritrix(dir) " + dir);
			addJobDirToHeritrix(dir);
		} catch (Exception e) {
			if (dir.exists())
				dir.delete();
			teardown(conf.getName());
		}

	}

	public File createJobDir(Gatherconf conf) {
		// nicht nur bei Neuanlage, sondern auch, falls crawlerConf im JobDir
		// erneuert werden muss (refresh)
		try {
			if (conf.getName() == null) {
				throw new RuntimeException("The configuration has no name !");
			}
			File dir = new File(jobDir + "/" + conf.getName());
			if (!dir.exists()) {
				// Create Job Directory
				WebgatherLogger
						.debug("Create job Directory " + jobDir + "/" + conf.getName());
				dir.mkdirs();
			}
			// Copy Job-Config to JobDirectory
			File crawlerConf = Play.application().getFile("conf/crawler-beans.cxml");

			/*
			 * metadata.operatorContactUrl=${OPERATOR_URL} metadata.jobName=${JOBNAME}
			 * metadata.description=${DESCRIPTION}
			 * metadata.robotsPolicyName=${ROBOTSPOLICY} ${URL}
			 */

			Path path = Paths.get(crawlerConf.getAbsolutePath());
			Charset charset = StandardCharsets.UTF_8;
			String content = new String(Files.readAllBytes(path), charset);
			content = content.replaceAll("\\$\\{OPERATOR_URL\\}",
					"https://www.edoweb-rlp.de");
			content = content.replaceAll("\\$\\{JOBNAME\\}", conf.getName());
			content = content.replaceAll("\\$\\{ROBOTSPOLICY\\}",
					conf.getRobotsPolicy().toString());
			content = content.replaceAll("\\$\\{DESCRIPTION\\}",
					"Edoweb crawl of" + conf.getUrl());
			content = content.replaceAll("\\$\\{URL\\}", conf.getUrl());

			WebgatherLogger.debug("Print-----\n" + content + "\n to \n"
					+ dir.getAbsolutePath() + "/crawler-beans.cxml");

			Files.write(Paths.get(dir.getAbsolutePath() + "/crawler-beans.cxml"),
					content.getBytes(charset));
			return dir;
		} catch (Exception e) {

			throw new RuntimeException(e);
		}
	}

	private void addJobDirToHeritrix(File dir) {
		try {
			WebResource resource = client.resource(restUrl + "/engine");
			String response = resource.accept("application/xml").post(String.class,
					"action=add&addpath=" + dir.getAbsolutePath());
			WebgatherLogger.info(response);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * "curl -v -d \"action=launch\" -k -u admin:admin --anyauth --location -H \"Accept: application/xml\" https://localhost:8443/engine/job/myjob"
	 * 
	 * @param name
	 * @return the url to the warc file
	 */
	public boolean launch(String name) {
		// Launch Job
		try {
			launchJobToHeritrix(name);
			return true;
		} catch (Throwable e) {
			return false;
		}

	}

	private void launchJobToHeritrix(String name) {
		try {
			WebResource resource = client.resource(restUrl + "/engine/job/" + name);
			WebgatherLogger.debug(resource.accept("application/xml")
					.post(String.class, "action=launch"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param name
	 * @return
	 */
	public boolean unpause(String name) {
		try {
			unpauseJobToHeritrix(name);
			return true;

		} catch (RuntimeException e) {
			return false;
		}

	}

	/**
	 * @param name the jobs name e.g. the pid
	 * @return the servers directory where to store the data
	 */
	public File getCurrentCrawlDir(String name) {
		File dir = new File(this.jobDir + "/" + name);
		WebgatherLogger.debug("jobDir/name=" + dir.toString());
		File[] files = dir.listFiles(file -> {
			String now =
					new SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
			// WebgatherLogger.debug("Directory must start with " + now);
			// WebgatherLogger.debug("Found File: "+file.getName());
			return file.isDirectory() && file.getName().startsWith(now);
		});
		WebgatherLogger
				.debug("Found crawl directories: " + java.util.Arrays.toString(files));
		if (files == null || files.length <= 0) {
			throw new RuntimeException("No directory with timestamp created!");
		}
		Arrays.sort(files, (f1, f2) -> {
			return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
		});

		File latest = files[files.length - 1];
		return latest;
	}

	/**
	 * @param latest dir of the latest (most recent) job
	 * @return local path of the latest harvested warc
	 */
	public String findLatestWarc(File latest) {
		WebgatherLogger.debug(latest.getAbsolutePath() + "/warcs");
		File warcDir = new File(latest.getAbsolutePath() + "/warcs");
		return warcDir.listFiles()[0].getAbsolutePath();
	}

	/**
	 * @param str
	 * @return
	 */
	public String getUriPath(String str) {
		return str.replace(jobDir, "").replace(".open", "");
	}

	private String unpauseJobToHeritrix(String name) {
		try {
			WebResource resource = client.resource(restUrl + "/engine/job/" + name);
			String response = resource.accept("application/xml").post(String.class,
					"action=unpause");
			WebgatherLogger.info(response);
			return response;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param name of the job
	 * @return true if a jobdirectory is present
	 */
	public boolean jobExists(String name) {
		WebgatherLogger.debug("Test if jobDir exists " + jobDir + "/" + name);
		return new File(jobDir + "/" + name).exists();
	}

	/**
	 * @param name
	 * @return pass the job status file
	 */
	public String getJobStatus(String name) {
		try {
			WebResource resource = client.resource(restUrl + "/engine/job/" + name);
			String response = resource.accept("application/xml").get(String.class);
			return response;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param name
	 * @param crawlDir
	 * @return
	 */
	public String getJobStatus(String name, String crawlDir) {
		try {
			WebResource resource = client.resource(restUrl + "/engine/job/" + name
					+ "/jobdir/" + crawlDir + "/reports/crawl-report.txt");
			String response = resource.get(String.class);
			return response;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isBusy() {
		try {

			WebgatherLogger.debug("restUrl=" + restUrl);
			WebResource resource = client.resource(restUrl + "/engine/");
			resource.get(String.class);
			return false;
		} catch (Exception e) {
			WebgatherLogger.debug("", e);
			return true;
		}
	}
}
