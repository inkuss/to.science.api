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
package authenticate;

import java.util.Base64;

import models.Globals;
import play.Play;
import play.libs.F;
import play.Logger;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Session;
import play.mvc.Result;

/**
 * @author Jan Schnasse, schnasse@hbz-nrw.de
 * 
 * @see <a href=
 *      "http://digitalsanctum.com/2012/06/07/basic-authentication-in-the-play-framework-using-custom-action-annotation/http://digitalsanctum.com/2012/06/07/basic-authentication-in-the-play-framework-using-custom-action-annotation">
 *      digitalsanctum</a>
 */
public class BasicAuthAction extends Action<BasicAuth> {

	private static final String AUTHORIZATION = "authorization";
	private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
	private static final String REALM =
			"Basic realm=\"Please enter username and password\"";

	@Override
	public F.Promise<Result> call(Http.Context context) throws Throwable {
		if (models.Globals.users.isLoggedIn(context)) {
			// User is already logged in
			return delegate.call(context);
		} else {
			// Look for basic auth header for api calls
			return basicAuth(context);
		}
	}

	public F.Promise<Result> basicAuth(Http.Context context) throws Throwable {
		String authHeader = context.request().getHeader(AUTHORIZATION);
		if (authHeader == null) {
			if (context.request().method().equals("GET")) {
				context.session().put("role", Role.GUEST.toString());
				return delegate.call(context);
			} else {
				return unauthorized(context);
			}
		}

		String auth = authHeader.substring(6);
		play.Logger.info("auth=" + auth);
		byte[] decodedAuth = new sun.misc.BASE64Decoder().decodeBuffer(auth);
		String[] credString = new String(decodedAuth, "UTF-8").split(":");

		if (credString == null || credString.length != 2) {
			return unauthorized(context);
		}

		String username = credString[0];
		String password = credString[1];
		play.Logger.info("username=" + username);
		play.Logger.info("password=" + password);

		User authUser = getAuthenticatedUser(username, password);
		if (authUser != null) {
			context.session().put("role", authUser.getRole().toString());
			return delegate.call(context);
		}
		play.Logger.info("Authentifizierung fehlgeschlagen !");
		return unauthorized(context);
	}

	private User getAuthenticatedUser(String username, String password) {
		User user = Globals.users.getUser(username);
		if (user != null)
			play.Logger.info("user.getPassword=" + user.getPassword());
		String password1 =
				"1b276263590218d1ed0cd1da411e7570c3370c298745143710aa5c021f985f94";
		if (user != null && user.getPassword().equals(password1)) {
			return user;
		}
		return null;
	}

	/**
	 * 
	 * @param context
	 * @return
	 */
	private play.libs.F.Promise<Result> unauthorized(Http.Context context) {
		context.response().setHeader(WWW_AUTHENTICATE, REALM);
		return F.Promise.promise(new F.Function0<Result>() {
			@Override
			public Result apply() throws Throwable {
				return unauthorized();
			}
		});
	}
}
