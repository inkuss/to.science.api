import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Date;

import models.Gatherconf;
import models.Gatherconf.Interval;
import models.Gatherconf.RobotsPolicy;
import models.Node;
import models.ToScienceObject;

import org.junit.Before;

import actions.Create;
import actions.Modify;

public class HeritrixTest {
	Create create;
	Modify modify;

	@Before
	public void setUp() {
		create = new Create();
		modify = new Modify();
	}

	// @Test
	public void testHeritrix() {
		running(testServer(3333), new Runnable() {
			@SuppressWarnings("deprecation")
			public void run() {
				String pid = "test:1234";
				ToScienceObject object = new ToScienceObject();
				object.setContentType("webpage");
				Node webpage = create.createResource("test", object, true);
				play.Logger.debug(webpage.getPid());
				Gatherconf conf = new Gatherconf();
				conf.setUrl("https://schnasse.eu");
				conf.setName("schnasse.eu");
				conf.setDeepness(-1);
				conf.setInterval(Interval.annually);
				conf.setRobotsPolicy(RobotsPolicy.classic);
				conf.setStartDate(new Date());
				webpage.setConf(conf.toString());
				// Node webpageVersion = create.createWebpageVersion(webpage);
			}
		});
	}
}
