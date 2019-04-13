/*
 * ao-tempfiles-servlet - Temporary file management in a Servlet environment.
 * Copyright (C) 2017, 2019  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-tempfiles-servlet.
 *
 * ao-tempfiles-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-tempfiles-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-tempfiles-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.tempfiles.servlet;

import com.aoindustries.tempfiles.TempFileContext;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * Provides {@link TempFileContext temp file contexts} for {@link ServletContext},
 * {@link ServletRequest}, and {@link HttpSession}.
 */
@WebListener
public class ServletTempFileContext
implements
	ServletContextListener,
	ServletRequestListener,
	HttpSessionListener {

	private static final String ATTRIBUTE_NAME = ServletTempFileContext.class.getName();

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		ServletContext servletContext = sce.getServletContext();
		assert servletContext.getAttribute(ATTRIBUTE_NAME) == null;
		servletContext.setAttribute(
			ATTRIBUTE_NAME,
			new TempFileContext(
				(File)servletContext.getAttribute(ServletContext.TEMPDIR)
			)
		);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		ServletContext servletContext = sce.getServletContext();
		TempFileContext tempFiles = (TempFileContext)servletContext.getAttribute(ATTRIBUTE_NAME);
		if(tempFiles != null) {
			try {
				tempFiles.close();
			} catch(IOException e) {
				servletContext.log("Error deleting temporary files", e);
			}
		}
	}

	/**
	 * Gets the {@link TempFileContext temp file context} for the given {@link ServletContext servlet context}.
	 *
	 * @throws  IllegalStateException  if the temp files have not been added to the servlet context.
	 */
	public static TempFileContext getTempFileContext(ServletContext servletContext) throws IllegalStateException {
		TempFileContext tempFiles = (TempFileContext)servletContext.getAttribute(ATTRIBUTE_NAME);
		if(tempFiles == null) throw new IllegalStateException(ServletTempFileContext.class.getName() + " not added to ServletContext; please use Servlet 3.0+ specification or manually add listener to web.xml.");
		return tempFiles;
	}

	@Override
	public void requestInitialized(ServletRequestEvent sre) {
		ServletRequest request = sre.getServletRequest();
		assert request.getAttribute(ATTRIBUTE_NAME) == null;
		request.setAttribute(
			ATTRIBUTE_NAME,
			new TempFileContext(
				(File)sre.getServletContext().getAttribute(ServletContext.TEMPDIR)
			)
		);
	}

	@Override
	public void requestDestroyed(ServletRequestEvent sre) {
		ServletRequest request = sre.getServletRequest();
		TempFileContext tempFiles = (TempFileContext)request.getAttribute(ATTRIBUTE_NAME);
		if(tempFiles != null) {
			try {
				tempFiles.close();
			} catch(IOException e) {
				sre.getServletContext().log("Error deleting temporary files", e);
			}
		}
	}

	/**
	 * Gets the {@link TempFileContext temp file context} for the given {@link ServletRequest servlet request}.
	 *
	 * @throws  IllegalStateException  if the temp files have not been added to the servlet request.
	 */
	public static TempFileContext getTempFileContext(ServletRequest request) throws IllegalStateException {
		TempFileContext tempFiles = (TempFileContext)request.getAttribute(ATTRIBUTE_NAME);
		if(tempFiles == null) throw new IllegalStateException(ServletTempFileContext.class.getName() + " not added to ServletRequest; please use Servlet 3.0+ specification or manually add listener to web.xml.");
		return tempFiles;
	}

	public static final String SESSION_ATTRIBUTE_NAME = HttpSessionTempFileContext.class.getName();

	private static class HttpSessionTempFileContext implements Serializable, HttpSessionActivationListener {

		private static final long serialVersionUID = 1L;

		private transient TempFileContext tempFiles;

		private HttpSessionTempFileContext(ServletContext servletContext) {
			tempFiles = new TempFileContext(
				(File)servletContext.getAttribute(ServletContext.TEMPDIR)
			);
		}

		@Override
		public void sessionWillPassivate(HttpSessionEvent hse) {
			if(tempFiles != null) {
				try {
					tempFiles.close();
				} catch(IOException e) {
					hse.getSession().getServletContext().log("Error deleting temporary files", e);
				}
				tempFiles = null;
			}
		}

		@Override
		public void sessionDidActivate(HttpSessionEvent hse) {
			if(tempFiles == null) {
				tempFiles = new TempFileContext(
					(File)hse.getSession().getServletContext().getAttribute(ServletContext.TEMPDIR)
				);
			}
		}
	}

	@Override
	public void sessionCreated(HttpSessionEvent hse) {
		HttpSession session = hse.getSession();
		assert session.getAttribute(SESSION_ATTRIBUTE_NAME) == null;
		session.setAttribute(
			SESSION_ATTRIBUTE_NAME,
			new HttpSessionTempFileContext(
				session.getServletContext()
			)
		);
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent hse) {
		HttpSession session = hse.getSession();
		HttpSessionTempFileContext wrapper = (HttpSessionTempFileContext)session.getAttribute(SESSION_ATTRIBUTE_NAME);
		if(wrapper != null) {
			TempFileContext tempFiles = wrapper.tempFiles;
			if(tempFiles != null) {
				wrapper.tempFiles = null;
				try {
					tempFiles.close();
				} catch(IOException e) {
					session.getServletContext().log("Error deleting temporary files", e);
				}
			}
		}
	}

	/**
	 * Gets the {@link TempFileContext temp file context} for the given {@link HttpSession session}.
	 * <p>
	 * At this time, temporary files put into the session are deleted when the session is
	 * {@link HttpSessionActivationListener#sessionWillPassivate(javax.servlet.http.HttpSessionEvent) passivated},
	 * at the {@link HttpSessionListener#sessionDestroyed(javax.servlet.http.HttpSessionEvent) end of the session},
	 * or on JVM shutdown.  The temporary files are not {@link Serializable serialized} with the session.
	 * </p>
	 * <p>
	 * TODO: {@link TempFileContext} is not currently {@link Serializable}.  What would it mean to
	 * serialize temp files?  Would the files themselves be wrapped-up into the serialized form?
	 * Would just the filenames be serialized, assuming the underlying temp files are available
	 * to all servlet containers that might get the session?
	 * </p>
	 *
	 * @throws  IllegalStateException  if the temp files have not been added to the session.
	 */
	public static TempFileContext getTempFileContext(HttpSession session) throws IllegalStateException {
		HttpSessionTempFileContext wrapper = (HttpSessionTempFileContext)session.getAttribute(SESSION_ATTRIBUTE_NAME);
		if(wrapper == null) throw new IllegalStateException(HttpSessionTempFileContext.class.getName() + " not added to HttpSession; please use Servlet 3.0+ specification or manually add listener to web.xml.");
		TempFileContext tempFiles = wrapper.tempFiles;
		if(tempFiles == null) throw new IllegalStateException(HttpSessionTempFileContext.class.getName() + ".tempFiles is null");
		return tempFiles;
	}
}
