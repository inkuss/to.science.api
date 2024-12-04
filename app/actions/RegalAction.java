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

import java.util.List;

import models.Globals;
import models.Node;
import net.sf.ehcache.pool.sizeof.annotations.IgnoreSizeOf;
import play.cache.Cache;

/**
 * @author Jan Schnasse
 *
 */
@IgnoreSizeOf
public class RegalAction {

	@SuppressWarnings("javadoc")
	public Node updateIndex(String pid) {
		removeNodeFromCache(pid);
		Node node = new Read().readNode(pid);
		String msg = new Index().index(node);
		play.Logger.debug(msg);
		return node;
	}

	Node readNodeFromCache(String pid) {
		return (Node) Cache.get(pid);
	}

	void writeNodeToCache(Node node) {
		Cache.set(node.getPid(), node);
	}

	void removeNodeFromCache(String pid) {
		Cache.remove(pid);
	}

	@SuppressWarnings("javadoc")
	protected String createAggregationUri(String pid) {
		return Globals.useHttpUris
				? Globals.protocol + Globals.server + "/resource/" + pid : pid;
	}

	/**
	 * @param node the node
	 * @return the http address of the resource
	 */
	public String getHttpUriOfResource(Node node) {
		return Globals.useHttpUris ? node.getAggregationUri()
				: Globals.protocol + Globals.server + "/resource/"
						+ node.getAggregationUri();
	}

	/**
	 * @param nodes a list of nodes
	 * @param action a action performed on each node
	 * @return a message
	 */
	public String apply(List<Node> nodes, ProcessNode action) {
		StringBuffer str = new StringBuffer();
		str.append("Process " + nodes.size() + " nodes!\n");
		for (Node n : nodes) {
			try {
				str.append("\n Updated " + action.process(n));
			} catch (Exception e) {
				str.append("\n Not updated " + n.getPid() + " " + e.getMessage());
			}
		}
		str.append("\n");
		return str.toString();
	}

	@SuppressWarnings("javadoc")
	protected String getUriFromJsonName(String name) {
		return Globals.profile.getEtikettByName(name).getUri();
	}

	@SuppressWarnings("javadoc")
	protected String getJsonName(String uri) {
		return Globals.profile.getEtikett(uri).getName();
	}
}
