/*******************************************************************************
 * Copyright (c) 2009 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available
 * at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.backend.internal;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.erlide.backend.BackendUtils;
import org.erlide.backend.IBackend;
import org.erlide.backend.IBackendManager;
import org.erlide.backend.ICodeBundle;
import org.erlide.backend.ICodeManager;
import org.erlide.runtime.BeamLoader;
import org.erlide.runtime.ErlUtils;
import org.erlide.runtime.runtimeinfo.RuntimeInfo;
import org.erlide.utils.ErlLogger;
import org.osgi.framework.Bundle;

import com.ericsson.otp.erlang.OtpErlangBinary;

public class CodeManager implements ICodeManager {

    private final IBackend backend;
    private final RuntimeInfo runtimeInfo;

    private final List<PathItem> pathA;
    private final List<PathItem> pathZ;
    private final List<ICodeBundle> registeredBundles;
    private final IBackendManager backendManager;

    // only to be called by Backend
    CodeManager(final IBackend b, final RuntimeInfo runtimeInfo,
            final IBackendManager backendManager) {
        backend = b;
        this.runtimeInfo = runtimeInfo;
        this.backendManager = backendManager;
        pathA = new ArrayList<PathItem>();
        pathZ = new ArrayList<PathItem>();
        registeredBundles = new ArrayList<ICodeBundle>();
    }

    @Override
    public void addPath(final boolean usePathZ, final String path) {
        if (usePathZ) {
            if (addPath(pathZ, path)) {
                ErlangCode.addPathZ(backend.getRpcSite(), path);
            }
        } else {
            if (addPath(pathA, path)) {
                ErlangCode.addPathA(backend.getRpcSite(), path);
            }
        }
    }

    @Override
    public void removePath(final String path) {
        if (removePath(pathA, path)) {
            ErlangCode.removePath(backend.getRpcSite(), path);
        }
    }

    @Override
    public void reRegisterBundles() {
        for (final ICodeBundle p : registeredBundles) {
            registerBundle(p);
        }
    }

    @Override
    public void register(final ICodeBundle b) {
        registeredBundles.add(b);
        registerBundle(b);
    }

    @Override
    public void unregister(final Bundle b) {
        final ICodeBundle p = findBundle(b);
        if (p == null) {
            return;
        }
        registeredBundles.remove(p);
        unloadPluginCode(p);
    }

    /**
     * @param moduleName
     * @param beamPath
     * @return boolean
     */
    private boolean loadBeam(final String moduleName, final URL beamPath) {
        final OtpErlangBinary bin = BeamUtil
                .getBeamBinary(moduleName, beamPath);
        if (bin == null) {
            return false;
        }
        return BeamLoader.loadBeam(backend.getRpcSite(), moduleName, bin);
    }

    private void loadPluginCode(final ICodeBundle p) {

        final Bundle b = p.getBundle();
        ErlLogger.debug("loading plugin " + b.getSymbolicName() + " in "
                + runtimeInfo.getName());

        // TODO Do we have to also check any fragments?
        // see FindSupport.findInFragments

        final IConfigurationElement[] els = BackendUtils
                .getCodepathConfigurationElements();
        for (final IConfigurationElement el : els) {
            final IContributor c = el.getContributor();
            if ("beam_dir".equals(el.getName())
                    && c.getName().equals(b.getSymbolicName())) {
                final String dir_path = el.getAttribute("path");
                @SuppressWarnings("rawtypes")
                Enumeration e = null;
                if (dir_path != null) {
                    e = b.getEntryPaths(dir_path);
                }
                if (e == null) {
                    ErlLogger.debug("* !!! error loading plugin "
                            + b.getSymbolicName());
                    return;
                }
                while (e.hasMoreElements()) {
                    final String s = (String) e.nextElement();
                    final String beamModuleName = BackendUtils
                            .getBeamModuleName(s);
                    if (beamModuleName != null) {
                        // ErlLogger.debug(" " + beamModuleName);
                        try {
                            final boolean ok = loadBeam(beamModuleName,
                                    b.getEntry(s));
                            if (!ok) {
                                ErlLogger.error("Could not load %s",
                                        beamModuleName);
                            }
                            backendManager.moduleLoaded(backend, null,
                                    beamModuleName);
                        } catch (final Exception ex) {
                            ErlLogger.warn(ex);
                        }
                    }
                }
            }
        }

        // loadStubCode(b, reg);
        // ErlLogger.debug("*done! loading plugin " + b.getSymbolicName());
    }

    private boolean addPath(final List<PathItem> l, final String path) {
        if (path == null) {
            return false;
        }

        final PathItem it = findItem(l, path);
        if (it == null) {
            l.add(new PathItem(path));
            return true;
        }
        it.incRef();
        return false;
    }

    private boolean removePath(final List<PathItem> l, final String path) {
        if (path == null) {
            return false;
        }
        final PathItem it = findItem(l, path);
        if (it != null) {
            it.decRef();
            if (it.ref <= 0) {
                l.remove(it);
                return true;
            }
        }
        return false;
    }

    private void registerBundle(final ICodeBundle p) {
        final String externalPath = System.getProperty(p.getBundle()
                .getSymbolicName() + ".ebin");
        if (externalPath != null) {
            final boolean accessible = ErlUtils.isAccessibleDir(
                    backend.getRpcSite(), externalPath);
            if (accessible) {
                ErlLogger.debug("adding external %s to code path for %s:: %s",
                        externalPath, backend, runtimeInfo);
                ErlangCode.addPathA(backend.getRpcSite(), externalPath);
                return;
            } else {
                ErlLogger.info("external code path %s for %s "
                        + "is not accessible, using plugin code", externalPath,
                        backend, runtimeInfo);
            }
        }
        final Collection<String> ebinDirs = p.getEbinDirs();
        if (ebinDirs != null) {
            for (final String ebinDir : ebinDirs) {
                final String localDir = ebinDir.replaceAll("\\\\", "/");
                final boolean accessible = ErlUtils.isAccessibleDir(
                        backend.getRpcSite(), localDir);
                final boolean embedded = ErlangCode.isEmbedded(backend
                        .getRpcSite());
                if (accessible && !embedded) {
                    ErlLogger.debug("adding %s to code path for @%s:: %s",
                            localDir, backend.hashCode(), runtimeInfo);
                    ErlangCode.addPathA(backend.getRpcSite(), localDir);
                } else {
                    ErlLogger.debug("loading %s for %s", p.getBundle()
                            .getSymbolicName(), runtimeInfo);
                    loadPluginCode(p);
                }
            }
        } else {
            ErlLogger.warn("Could not find 'ebin' in bundle %s.", p.getBundle()
                    .getSymbolicName());
            loadPluginCode(p);
        }
    }

    private ICodeBundle findBundle(final Bundle b) {
        for (final ICodeBundle p : registeredBundles) {
            if (p.getBundle() == b) {
                return p;
            }
        }
        return null;
    }

    private PathItem findItem(final List<PathItem> l, final String p) {
        final Iterator<PathItem> i = l.iterator();
        while (i.hasNext()) {
            final PathItem it = i.next();
            if (it.path.equals(p)) {
                return it;
            }
        }
        return null;
    }

    private void unloadPluginCode(final ICodeBundle p) {
        final Bundle b = p.getBundle();
        @SuppressWarnings("rawtypes")
        Enumeration e;
        ErlLogger.debug("*> really unloading plugin " + p.getClass().getName());
        e = b.getEntryPaths("/ebin");
        while (e.hasMoreElements()) {
            final String s = (String) e.nextElement();
            final String beamModuleName = BackendUtils.getBeamModuleName(s);
            if (beamModuleName != null) {
                unloadBeam(beamModuleName);
            }
        }
    }

    private void unloadBeam(final String moduleName) {
        ErlangCode.delete(backend.getRpcSite(), moduleName);
    }

    private static class PathItem {

        public PathItem(final String p) {
            path = p;
            ref = 1;
        }

        public String path;

        public int ref;

        public void incRef() {
            ref++;
        }

        public void decRef() {
            ref--;
        }
    }

}
