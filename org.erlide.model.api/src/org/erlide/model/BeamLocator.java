package org.erlide.model;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.erlide.model.root.ErlModelManager;
import org.erlide.model.root.IErlProject;
import org.erlide.utils.SystemConfiguration;

public class BeamLocator implements IBeamLocator {

    @Override
    public IFile findModuleBeam(final IProject project, final String module)
            throws ErlModelException {
        final IErlProject erlProject = ErlModelManager.getErlangModel()
                .getErlangProject(project);
        final IFolder r = project.getFolder(erlProject.getOutputLocation());
        try {
            r.refreshLocal(IResource.DEPTH_ONE, null);
        } catch (final CoreException e) {
        }
        final String beam = SystemConfiguration.withoutExtension(module)
                + ".beam";
        return r.getFile(beam);
    }

}
