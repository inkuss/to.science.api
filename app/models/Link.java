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
package models;

import java.io.StringWriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.core.util.JsonUtil;

/**
 * 
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 */
public class Link implements java.io.Serializable {
	boolean isLiteral = false;
	private String predicateLabel = null;
	private String predicate = null;
	private String object = null;

	private String objectLabel = null;

	/**
	 * Creates a new link
	 */
	public Link() {

	}

	/**
	 * Dieser Konstruktor kopiert ein vorhandenes Objekt. Dabei wid Speicherplatz
	 * physikalisch neu vergeben, es werden nicht nur Referenzen gesetzt. Um
	 * "concurrentModificationException"s zu vermeiden.
	 * 
	 * @author Ingolf Kuss
	 * @date 2025-03-28
	 * @param source the source Link to copy from
	 */
	public Link(Link source) {
		this.isLiteral = source.isLiteral;
		this.predicateLabel = new String(source.predicateLabel);
		this.predicate = new String(source.predicate);
		this.object = new String(source.object);
		this.objectLabel = new String(source.objectLabel);
	}

	/**
	 * @param predicate a rdf predicate
	 * @param object a rdf object
	 * @param isLiteral is the object a literal?
	 */
	public Link(String predicate, String object, boolean isLiteral) {
		this.predicate = predicate;
		this.object = object;
		this.isLiteral = isLiteral;
	}

	/**
	 * @return true if the object is literal. Default is false.
	 */
	public boolean isLiteral() {
		return isLiteral;
	}

	/**
	 * @param isLiteral true if the object is literal. Default is false.
	 */
	public void setLiteral(boolean isLiteral) {
		this.isLiteral = isLiteral;
	}

	/**
	 * @return the rdf predicate as string
	 */
	public String getPredicate() {
		return predicate;
	}

	/**
	 * @param predicate the rdf predicate as string
	 */
	public void setPredicate(String predicate) {
		this.predicate = predicate;
	}

	/**
	 * @return the rdf object as string
	 */
	public String getObject() {
		return object;
	}

	/**
	 * @param object the rdf object as string
	 * @param isLiteral true if the object is a literal
	 */
	public void setObject(String object, boolean isLiteral) {
		this.object = object;
		this.isLiteral = isLiteral;

	}

	/**
	 * @return predicateLabel
	 */
	public String getPredicateLabel() {
		return predicateLabel;
	}

	/**
	 * @param predicateLabel
	 */
	public void setPredicateLabel(String predicateLabel) {
		this.predicateLabel = predicateLabel;
	}

	/**
	 * @return objectLabel
	 */
	public String getObjectLabel() {
		return objectLabel;
	}

	/**
	 * @param objectLabel
	 */
	public void setObjectLabel(String objectLabel) {
		this.objectLabel = objectLabel;
	}

	/**
	 * @param object
	 */
	public void setObject(String object) {
		this.object = object;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Link other = (Link) obj;
		boolean sameA =
				(this.predicate == other.predicate) || (this.predicate != null
						&& this.predicate.equalsIgnoreCase(other.predicate));
		if (!sameA)
			return false;
		boolean sameB = (this.object == other.object)
				|| (this.object != null && this.object.equalsIgnoreCase(other.object));
		if (!sameB)
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 89 * hash + (this.predicate == null ? 0
				: this.predicate.toUpperCase().hashCode());
		hash = 89 * hash
				+ (this.object == null ? 0 : this.object.toUpperCase().hashCode());
		return hash;
	}

	@Override
	public String toString() {
		ObjectMapper mapper = JsonUtil.mapper();
		StringWriter w = new StringWriter();
		try {
			mapper.writeValue(w, this);
		} catch (Exception e) {
			e.printStackTrace();
			return super.toString();
		}
		return w.toString();
	}
}
