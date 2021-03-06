package org.erlide.core.builder;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.erlide.model.ErlModelException;
import org.erlide.model.erlang.IErlModule;
import org.erlide.model.erlang.ModuleKind;
import org.erlide.model.root.ErlModelManager;
import org.erlide.model.root.IErlElementLocator;
import org.erlide.model.root.IErlProject;
import org.erlide.model.util.ModelUtils;
import org.erlide.runtime.IRpcSite;
import org.erlide.utils.ErlLogger;
import org.erlide.utils.Util;

import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangRangeException;
import com.ericsson.otp.erlang.OtpErlangTuple;

public class DialyzerMarkerUtils {

    public static final String PATH_ATTRIBUTE = "org.eclipse.ui.views.markers.path";//$NON-NLS-1$
    public static final String DIALYZE_WARNING_MARKER = "org.erlide.dialyzer.core"
            + ".dialyzewarningmarker";
    public static final String PROBLEM_MARKER = "org.erlide.dialyzer.core"
            + ".problemmarker";

    public static void addDialyzerWarningMarkersFromResultList(
            final IRpcSite backend, final OtpErlangList result) {
        if (result == null) {
            return;
        }
        for (final OtpErlangObject i : result) {
            final OtpErlangTuple t = (OtpErlangTuple) i;
            final OtpErlangTuple fileLine = (OtpErlangTuple) t.elementAt(1);
            final String filename = Util.stringValue(fileLine.elementAt(0));
            final OtpErlangLong lineL = (OtpErlangLong) fileLine.elementAt(1);
            int line = 1;
            try {
                line = lineL.intValue();
            } catch (final OtpErlangRangeException e) {
                ErlLogger.error(e);
            }
            String s = ErlideDialyze.formatWarning(backend, t).trim();
            final int j = s.indexOf(": ");
            if (j != -1) {
                s = s.substring(j + 1);
            }
            final IErlElementLocator model = ErlModelManager.getErlangModel();
            addDialyzerWarningMarker(model, filename, line, s);
        }
    }

    public static void addDialyzerWarningMarker(final IErlElementLocator model,
            final String path, final int line, final String message) {
        IResource file = null;
        IProject project = null;
        IErlModule module = null;
        try {
            if (ModuleKind.hasHrlExtension(path)) {
                module = model.findInclude(null, path);
            } else {
                module = model.findModule(null, path);
            }
        } catch (final ErlModelException e) {
        }
        if (module != null) {
            file = module.getResource();
            final IErlProject erlProject = ModelUtils.getProject(module);
            if (erlProject != null) {
                project = erlProject.getWorkspaceProject();
            }
        }
        addMarker(file, project, path, message, line, IMarker.SEVERITY_WARNING,
                DIALYZE_WARNING_MARKER);
    }

    public static IMarker addMarker(final IResource file,
            final IProject project, final String path, final String message,
            int lineNumber, final int severity, final String markerKind) {
        try {
            IResource resource;
            if (file != null) {
                resource = file;
            } else if (project != null) {
                resource = project;
            } else {
                resource = ResourcesPlugin.getWorkspace().getRoot();
            }
            final IMarker marker = resource.createMarker(markerKind);
            marker.setAttribute(IMarker.MESSAGE, message);
            marker.setAttribute(IMarker.SEVERITY, severity);
            if (lineNumber == -1) {
                lineNumber = 1;
            }
            marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
            marker.setAttribute(PATH_ATTRIBUTE, path);
            return marker;
        } catch (final CoreException e) {
        }
        return null;
    }

    public static void removeDialyzerMarkersFor(final IResource resource) {
        removeMarkersFor(resource, DIALYZE_WARNING_MARKER);
    }

    public static boolean haveDialyzerMarkers(final IResource resource) {
        try {
            if (resource.isAccessible()) {
                final IMarker[] markers = resource.findMarkers(
                        DIALYZE_WARNING_MARKER, true, IResource.DEPTH_INFINITE);
                return markers != null && markers.length > 0;
            }
        } catch (final CoreException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static void removeMarkersFor(final IResource resource,
            final String type) {
        try {
            if (resource != null && resource.exists()) {
                resource.deleteMarkers(type, false, IResource.DEPTH_INFINITE);
            }
        } catch (final CoreException e) {
            // assume there were no problems
        }
    }

    public static void addMarker(final IResource file, final String path,
            final IResource compiledFile, final String errorDesc,
            final int lineNumber, final int severity, final String errorVar) {
        addProblemMarker(file, path, compiledFile, errorDesc, lineNumber,
                severity);
    }

    public static void addProblemMarker(final IResource resource,
            final String path, final IResource compiledFile,
            final String message, int lineNumber, final int severity) {
        try {
            final IMarker marker = resource.createMarker(PROBLEM_MARKER);
            marker.setAttribute(IMarker.MESSAGE, message);
            marker.setAttribute(IMarker.SEVERITY, severity);
            if (path != null && !new Path(path).equals(resource.getLocation())) {
                marker.setAttribute(MarkerUtils.PATH_ATTRIBUTE, path);
            }
            if (compiledFile != null) {
                marker.setAttribute(IMarker.SOURCE_ID, compiledFile
                        .getFullPath().toString());
            }
            if (lineNumber == -1) {
                lineNumber = 1;
            }
            marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
        } catch (final CoreException e) {
        }
    }

}
