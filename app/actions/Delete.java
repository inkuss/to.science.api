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

import helper.HttpArchiveError;
import helper.HttpArchiveException;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.List;

import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;

import archive.fedora.RdfUtils;
import static archive.fedora.Vocabulary.*;
import models.Globals;
import models.Node;

/**
 * @author Jan Schnasse
 *
 */
public class Delete extends RegalAction {

	/**
	 * Deletes only this single node. Child objects will remain.
	 * 
	 * @param n a node to delete
	 * @return a message
	 */
	private String purge(Node n) {
		StringBuffer message = new StringBuffer();
		message.append(new Index().remove(n));
		// removeNodeFromCache(n.getPid());
		String parentPid = n.getParentPid();
		if (parentPid != null && !parentPid.isEmpty()) {
			try {
				Globals.fedora.unlinkParent(n);
				updateIndex(parentPid);
			} catch (HttpArchiveException e) {
				message
						.append(e.getCode() + " parent " + parentPid + " already deleted.");
			}
		}
		Globals.fedora.purgeNode(n.getPid());
		return message.toString() + "\n" + n.getPid() + " purged!";
	}

	/**
	 * Deletes only this single node. Child objects will remain.
	 * 
	 * @param n a node to delete
	 * @return a message
	 */
	private String delete(Node n) {
		StringBuffer message = new StringBuffer();
		message.append(new Index().remove(n));
		// removeNodeFromCache(n.getPid());
		Globals.fedora.deleteNode(n.getPid());
		return message.toString() + "\n" + n.getPid() + " deleted!";
	}

	/**
	 * Each node in the list will be deleted. Child objects will remain
	 * 
	 * @param nodes a list of nodes to delete.
	 * @return a message
	 */
	public String delete(List<Node> nodes) {
		StringBuffer message = new StringBuffer();
		for (Node n : nodes) {
			message.append(delete(n) + " \n");
		}
		return message.toString();
	}

	/**
	 * @param nodes a list of nodes to evaluate
	 * @return true if one node in list has a persistent identifier
	 */
	public boolean nodesArePersistent(List<Node> nodes) {
		for (Node n : nodes) {
			if (n.hasPersistentIdentifier())
				return true;
			if (n.hasDoi())
				return true;
		}
		return false;
	}

	/**
	 * Each node in the list will be deleted. Child objects will remain
	 * 
	 * @param nodes a list of nodes to delete.
	 * @return a message
	 */
	public String purge(List<Node> nodes) {
		return apply(nodes, n -> purge(n));
	}

	/**
	 * @param pid the id of a resource.
	 * @return a message
	 */
	public String deleteSeq(String pid) {
		Globals.fedora.deleteDatastream(pid, "seq");
		updateIndex(pid);
		return pid + ": seq - datastream successfully deleted! ";
	}

	/**
	 * @param pid a namespace qualified id
	 * @return a message
	 */
	public String deleteMetadata(String pid) {
		Globals.fedora.deleteDatastream(pid, "metadata");
		updateIndex(pid);
		return pid + ": metadata - datastream successfully deleted! ";
	}

	/**
	 * @param pid a namespace qualified id
	 * @return a message
	 */
	public String deleteMetadata2(String pid) {
		Globals.fedora.deleteDatastream(pid, "metadata2");
		updateIndex(pid);
		return pid + ": metadata2 - datastream successfully deleted! ";
	}

	/**
	 * @param pid the pid og the object
	 * @return a message
	 */
	public String deleteData(String pid) {
		Globals.fedora.deleteDatastream(pid, "data");
		updateIndex(pid);
		return pid + ": data - datastream successfully deleted! ";
	}

	public String deleteMetadataField(String pid, String field) {
		Node node = new Read().readNode(pid);
		String pred = getUriFromJsonName(field);
		RepositoryConnection rdfRepo = RdfUtils.readRdfInputStreamToRepository(
				new ByteArrayInputStream(node.getMetadata(metadata1).getBytes()),
				RDFFormat.NTRIPLES);
		Collection<Statement> myGraph =
				RdfUtils.deletePredicateFromRepo(rdfRepo, pred);
		return new Modify().updateMetadata(metadata1, node,
				RdfUtils.graphToString(myGraph, RDFFormat.NTRIPLES));
	}

	public String deleteMetadata2Field(String pid, String field) {
		Node node = new Read().readNode(pid);
		String pred = getUriFromJsonName(field);
		RepositoryConnection rdfRepo = RdfUtils.readRdfInputStreamToRepository(
				new ByteArrayInputStream(node.getMetadata2().getBytes()),
				RDFFormat.NTRIPLES);
		Collection<Statement> myGraph =
				RdfUtils.deletePredicateFromRepo(rdfRepo, pred);
		return new Modify().updateMetadata(metadata2, node,
				RdfUtils.graphToString(myGraph, RDFFormat.NTRIPLES));
	}
}
