/*******************************************************************************
 * Copyright (c) 2007 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.basiccore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ErlLogger {

	public enum Level {
		DEBUG, INFO, WARN, ERROR
	};

	private static Level minLevel = Level.DEBUG;

	public static void setLevel(Level level) {
		minLevel = level;
	}

	public static Level levelFromName(String levelName) {
		if ("info".equals(levelName)) {
			return Level.INFO;
		} else if ("debug".equals(levelName)) {
			return Level.DEBUG;
		} else if ("warn".equals(levelName)) {
			return Level.WARN;
		} else if ("error".equals(levelName)) {
			return Level.ERROR;
		} else {
			return minLevel;
		}
	}

	private static StackTraceElement getCaller() {
		StackTraceElement[] st = Thread.currentThread().getStackTrace();
		StackTraceElement el = null;
		int i = 2;
		do {
			el = st[i++];
		} while (el.getClassName().equals(ErlLogger.class.getName()));
		return el;
	}

	private static void log(Level kind, String fmt, Object... o) {
		if (kind.compareTo(minLevel) < 0) {
			return;
		}
		final StackTraceElement el = getCaller();
		final String str = String.format(fmt, o);
		final Date time = Calendar.getInstance().getTime();
		final String stime = new SimpleDateFormat("HH:mm:ss,SSS").format(time);
		System.out.println("[" + kind.toString() + "] [" + stime + "] ("
				+ el.getFileName() + ":" + el.getLineNumber() + ") : " + str);
	}

	public static void erlangLog(String module, int line, Level kind,
			String fmt, Object... o) {
		if (kind.compareTo(minLevel) < 0) {
			return;
		}
		final String str = String.format(fmt, o);
		final Date time = Calendar.getInstance().getTime();
		final String stime = new SimpleDateFormat("HH:mm:ss,SSS").format(time);
		System.out.println("[" + kind.toString() + "] [" + stime + "] ("
				+ module + ":" + line + ") : " + str);
	}

	public static void debug(String fmt, Object... o) {
		log(Level.DEBUG, fmt, o);
	}

	public static void info(String fmt, Object... o) {
		log(Level.INFO, fmt, o);
	}

	public static void warn(String fmt, Object... o) {
		log(Level.WARN, fmt, o);
	}

	public static void error(String fmt, Object... o) {
		log(Level.ERROR, fmt, o);
	}

}
