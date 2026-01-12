package helper;

import actions.Enrich;
import actions.Modify;
import actions.Read;
import archive.fedora.RdfUtils;
import controllers.MyController;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import models.Globals;
import models.Node;
import org.eclipse.rdf4j.rio.RDFFormat;
import java.nio.charset.StandardCharsets;
import de.hbz.lobid.helper.JsonConverter;
import de.hbz.lobid.helper.EtikettMakerInterface;

/**
 * 
 * @author adoud
 *
 */
public class RdfHelper {

	/**
	 * This method converts lobid (N-triples) into a map object
	 * 
	 * @param node ParentNode
	 * @param format N-Triples
	 * @param content Lobid
	 * @return Map Object
	 */
	public static Map<String, Object> getRdfAsMap(Node n, RDFFormat format,
			String content) {

		Map<String, Object> rdf = null;
		String rewriteContent = null;
		EtikettMakerInterface profile = Globals.profile;
		JsonConverter jsonConverter = new JsonConverter(profile);

		if (content == null) {
			play.Logger.debug("Lobid (RDF) content is null");
			return null;
		}

		try {

			if (content.contains(archive.fedora.Vocabulary.REL_MAB_527)) {
				/*
				 * parallelEdition - nach der Eingabe einer AlmaMmsId / HTNr in der
				 * Eingabemaske für den Katalogdatenimport wird der content von diesem
				 * Format sein. Die eigentlichen Metadaten von lobid müssen dann noch
				 * gezogen werden (s.u.)
				 */
				String lobidUri = RdfUtils.findRdfObjects(n.getPid(),
						archive.fedora.Vocabulary.REL_MAB_527, content, RDFFormat.NTRIPLES)
						.get(0);
				String alephid =
						lobidUri.replaceFirst("http://lobid.org/resource[s]*/", "");
				alephid = alephid.replaceAll("#.*", "");
				String content_new = Modify.getLobid2DataAsNtripleString(n, alephid);
				// updateMetadata2(node, content);
				rewriteContent = new Modify().rewriteContent(content_new, n.getPid());
				play.Logger.debug("rewriteContent=" + rewriteContent);

			} else {
				// play.Logger.debug("content=" + content);
				rewriteContent = new Modify().rewriteContent(content, n.getPid());
				play.Logger.debug("rewriteContent=" + rewriteContent);
			}

			InputStream stream = new ByteArrayInputStream(
					rewriteContent.getBytes(StandardCharsets.UTF_8));

			rdf = jsonConverter.convert(n.getPid(), stream, format,
					profile.getContext().get("@context"));

			play.Logger.debug("getRdfAsMap(),rdf=" + rdf.toString());

			rdf.remove("@context");

			play.Logger.debug("rdf without key @Context=" + rdf.toString());

		} catch (Exception e) {
			play.Logger.error("Lobid(RDF) Content could not be convert to Map", e);
		}
		return rdf;
	}

}
