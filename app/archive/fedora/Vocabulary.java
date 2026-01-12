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

/**
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 */
public abstract class Vocabulary {

	/**
	 * Self developed vocabularies
	 */
	public final static String HBZ_MODEL_NAMESPACE =
			"info:hbz/hbz-ingest:def/model#";

	/**
	 * A Nodetype describes the role of the node from the perspective of the
	 * general datamodel
	 */
	public final static String REL_IS_NODE_TYPE =
			HBZ_MODEL_NAMESPACE + "isNodeType";
	/**
	 * A contentType is bound to certain characteristics regarding the content of
	 * the node
	 */
	public final static String REL_CONTENT_TYPE =
			HBZ_MODEL_NAMESPACE + "contentType";
	/**
	 * A default type
	 */
	public final static String TYPE_NODE = HBZ_MODEL_NAMESPACE + "HBZ_NODE";

	/**
	 * A default type
	 */
	public final static String TYPE_OBJECT = HBZ_MODEL_NAMESPACE + "HBZ_OBJECT";

	/**
	 * The access scheme rules the access to a node's data
	 */
	public final static String REL_ACCESS_SCHEME =
			HBZ_MODEL_NAMESPACE + "accessScheme";

	/**
	 * The publish scheme rules the access to a node's metadata
	 */
	public final static String REL_PUBLISH_SCHEME =
			HBZ_MODEL_NAMESPACE + "publishScheme";

	/**
	 * Used to point to a legacy system which originally created the resource
	 * 
	 */
	public final static String REL_IMPORTED_FROM =
			HBZ_MODEL_NAMESPACE + "importedFrom";

	/**
	 * Used to identify the creator of the regal resource
	 * 
	 */
	public final static String REL_CREATED_BY = HBZ_MODEL_NAMESPACE + "createdBy";

	/**
	 * Used to identify the last modifier of the regal resource
	 * 
	 */
	public final static String REL_LAST_MODIFIED_BY =
			HBZ_MODEL_NAMESPACE + "lastModifiedBy";

	/**
	 * An id once used to identify the object
	 * 
	 */
	public final static String REL_LEGACY_ID = HBZ_MODEL_NAMESPACE + "legacyId";

	/**
	 * A system internal name for the object
	 */
	public final static String REL_NAME = HBZ_MODEL_NAMESPACE + "name";

	/**
	 * An id for the catalog
	 * 
	 */
	public final static String REL_CATALOG_ID = HBZ_MODEL_NAMESPACE + "catalogId";

	/**
	 * predicate to link to an urn
	 * 
	 */
	public final static String REL_HAS_URN = "http://purl.org/lobid/lv#urn";

	/**
	 * predicate to link to a doi
	 * 
	 */
	public final static String REL_HAS_DOI = HBZ_MODEL_NAMESPACE + "doi";

	/**
	 * All hbzIds from /metadata must be ignored
	 * 
	 */
	public final static String REL_HBZ_ID = "http://purl.org/lobid/lv#hbzID";

	/**
	 * Regal uses this predicate to link to parallel title resources
	 */
	public final static String REL_MAB_527 =
			"http://hbz-nrw.de/regal#parallelEdition";

	/**
	 * This is how lobid stores DOIs
	 */
	public final static String REL_LOBID_DOI =
			"http://purl.org/ontology/bibo/doi";

	/**
	 * This is how lobid stores the title
	 */
	public final static String REL_TITLE = "http://purl.org/dc/terms/title";

	public final static String metadata1 = "metadata";
	public final static String metadata2 = "metadata2";
	public final static String lrmiData = "lrmiData";
	public final static String toscience = "toscience";
	public final static String ktbl = "ktbl";

}
