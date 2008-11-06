/*******************************************************************************
 * Copyright (c) 2005 Vlad Dumitrescu and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Vlad Dumitrescu
 *******************************************************************************/
package org.erlide.ui.launch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.erlide.core.builder.ErlangBuilder;
import org.erlide.runtime.backend.IErlLaunchAttributes;
import org.erlide.runtime.debug.IErlDebugConstants;
import org.erlide.ui.util.SWTUtil;

public class DebugTab extends AbstractLaunchConfigurationTab {

	private CheckboxTreeViewer checkboxTreeViewer;
	private Button attachOnFirstCallCheck;
	private Button attachOnBreakpointCheck;
	private Button attachOnExitCheck;
	private Button distributedDebugCheck;
	private Set<IFile> interpretedModules;

	public static class TreeLabelProvider extends LabelProvider {
		public TreeLabelProvider() {
			super();
		}

		@Override
		public String getText(final Object element) {
			if (element instanceof DebugTreeItem) {
				return ((DebugTreeItem) element).item.getName();
			}
			return super.getText(element);
		}

		@Override
		public Image getImage(final Object element) {
			return null;
		}
	}

	public static class DebugTreeItem {
		private final IResource item;
		private final DebugTreeItem parent;
		private final List<DebugTreeItem> children = new ArrayList<DebugTreeItem>();

		public DebugTreeItem(final IResource item, final DebugTreeItem parent) {
			this.item = item;
			this.parent = parent;
		}

		public DebugTreeItem getParent() {
			return parent;
		}

		public IResource getItem() {
			return item;
		}

		public List<DebugTreeItem> getChildren() {
			return children;
		}

		public boolean isChecked(final Collection<IFile> interpretedModules) {
			if (item instanceof IFile) {
				return interpretedModules.contains(item);
			} else {
				for (final DebugTreeItem i : children) {
					if (!i.isChecked(interpretedModules)) {
						return false;
					}
				}
			}
			return true;
		}

		public boolean isUnchecked(final Collection<IFile> interpretedModules) {
			if (item instanceof IFile) {
				return !interpretedModules.contains(item);
			} else {
				for (final DebugTreeItem i : children) {
					if (!i.isUnchecked(interpretedModules)) {
						return false;
					}
				}
			}
			return true;
		}

		private boolean recursiveAddAllErlangModules(final IContainer container) {
			boolean result = false;
			try {
				for (final IResource r : container.members()) {
					if (r instanceof IContainer) {
						final IContainer c = (IContainer) r;
						if (c.isAccessible()) {
							final DebugTreeItem dti = new DebugTreeItem(r, this);
							if (dti.recursiveAddAllErlangModules(c)) {
								children.add(dti);
								result = true;
							}
						}
					} else if (r instanceof IFile) {
						final IFile f = (IFile) r;
						if (f.getName().endsWith(".erl")
								&& ErlangBuilder
										.isInCodePath(r, r.getProject())) {
							children.add(new DebugTreeItem(r, this));
							result = true;
						}
					}
				}
			} catch (final CoreException e) {
			}
			return result;
		}

		private void setGrayChecked(
				final CheckboxTreeViewer checkboxTreeViewer,
				final boolean grayed, final boolean checked) {
			checkboxTreeViewer.setParentsGrayed(this, grayed);
			checkboxTreeViewer.setChecked(this, checked);
		}

		private void updateMenuCategoryCheckedState(
				final Collection<IFile> interpretedModules,
				final CheckboxTreeViewer checkboxTreeViewer) {
			if (isChecked(interpretedModules)) {
				setGrayChecked(checkboxTreeViewer, false, true);
			} else if (isUnchecked(interpretedModules)) {
				setGrayChecked(checkboxTreeViewer, false, false);
			} else {
				setGrayChecked(checkboxTreeViewer, true, true);
			}
			if (parent != null) {
				parent.updateMenuCategoryCheckedState(interpretedModules,
						checkboxTreeViewer);
			}
		}

		public void setChecked(final CheckboxTreeViewer checkboxTreeViewer,
				final Collection<IFile> list) {
			if (list.contains(item)) {
				setGrayChecked(checkboxTreeViewer, false, true);
			}
			for (final DebugTreeItem c : children) {
				c.setChecked(checkboxTreeViewer, list);
				c.updateMenuCategoryCheckedState(list, checkboxTreeViewer);
			}
		}
	}

	public static class TreeContentProvider implements
			IStructuredContentProvider, ITreeContentProvider {
		private DebugTreeItem root;

		public TreeContentProvider() {
			super();
		}

		public void inputChanged(final Viewer viewer, final Object oldInput,
				final Object newInput) {
			try {
				setRoot(new DebugTreeItem(null, null));
				if (newInput instanceof ILaunchConfiguration) {
					final ILaunchConfiguration input = (ILaunchConfiguration) newInput;
					final String projs = input.getAttribute(
							IErlLaunchAttributes.PROJECTS, "").trim();
					final String[] projNames = projs.split(";");
					if (projNames == null) {
						return;
					}
					final IWorkspaceRoot wr = ResourcesPlugin.getWorkspace()
							.getRoot();
					for (final String projName : projNames) {
						final IProject p = wr.getProject(projName);
						final DebugTreeItem dti = new DebugTreeItem(p, getRoot());
						dti.recursiveAddAllErlangModules(p);
						getRoot().children.add(dti);
					}
				}
			} catch (final CoreException e1) {
			}
		}

		public void dispose() {
		}

		public Object[] getElements(final Object inputElement) {
			return getChildren(getRoot());
		}

		public Object[] getChildren(final Object parentElement) {
			final DebugTreeItem dti = (DebugTreeItem) parentElement;
			return dti.children.toArray();
		}

		public Object getParent(final Object element) {
			final DebugTreeItem dti = (DebugTreeItem) element;
			return dti.parent;
		}

		public boolean hasChildren(final Object element) {
			return getChildren(element).length > 0;
		}

		/**
		 * @return the root
		 */
		public DebugTreeItem getRoot() {
			return root;
		}

		public void setRoot(DebugTreeItem root) {
			this.root = root;
		}
	}

	public void createControl(final Composite parent) {
		interpretedModules = new HashSet<IFile>();

		final Composite comp = new Composite(parent, SWT.NONE);
		setControl(comp);
		final GridLayout topLayout = new GridLayout();
		comp.setLayout(topLayout);

		distributedDebugCheck = createCheckButton(comp,
				"Debug all connected nodes");

		final Group attachGroup = SWTUtil.createGroup(comp, "Auto Attach", 1,
				GridData.FILL_BOTH);
		attachGroup.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false,
				false));
		attachOnFirstCallCheck = createCheckButton(attachGroup, "First &call");
		attachOnBreakpointCheck = createCheckButton(attachGroup, "&Breakpoint");
		attachOnExitCheck = createCheckButton(attachGroup, "E&xit");

		distributedDebugCheck.addSelectionListener(fBasicSelectionListener);
		attachOnFirstCallCheck.addSelectionListener(fBasicSelectionListener);
		attachOnBreakpointCheck.addSelectionListener(fBasicSelectionListener);
		attachOnExitCheck.addSelectionListener(fBasicSelectionListener);

		final Group interpretedModulesGroup = new Group(comp, SWT.NONE);
		interpretedModulesGroup.setText("Interpreted modules");
		final GridData gd_interpretedModulesGroup = new GridData();
		interpretedModulesGroup.setLayoutData(gd_interpretedModulesGroup);
		interpretedModulesGroup.setLayout(new GridLayout());

		final Label anyModuleHavingLabel = new Label(interpretedModulesGroup,
				SWT.WRAP);
		anyModuleHavingLabel.setLayoutData(new GridData(279, SWT.DEFAULT));
		anyModuleHavingLabel
				.setText("Any module having breakpoints enabled will be dynamically added to the list.");

		checkboxTreeViewer = new CheckboxTreeViewer(interpretedModulesGroup,
				SWT.BORDER);
		checkboxTreeViewer.addCheckStateListener(new ICheckStateListener() {
			@SuppressWarnings("synthetic-access")
			public void checkStateChanged(final CheckStateChangedEvent event) {
				final DebugTreeItem item = (DebugTreeItem) event.getElement();
				final boolean checked = event.getChecked();
				updateOnCheck(item, checked);

				checkboxTreeViewer.setSubtreeChecked(item, checked);
				// set gray state of the element's category subtree, all items
				// should not be grayed
				for (final DebugTreeItem i : item.children) {
					checkboxTreeViewer.setGrayed(i, false);
				}
				checkboxTreeViewer.setGrayed(item, false);
				if (item.parent != null) {
					item.parent.updateMenuCategoryCheckedState(
							interpretedModules, checkboxTreeViewer);
				}

				updateLaunchConfigurationDialog();
			}

		});
		checkboxTreeViewer.setLabelProvider(new TreeLabelProvider());
		checkboxTreeViewer.setContentProvider(new TreeContentProvider());
		final Tree tree = checkboxTreeViewer.getTree();
		final GridData gd_tree = new GridData(SWT.FILL, SWT.FILL, true, true);
		gd_tree.minimumWidth = 250;
		gd_tree.minimumHeight = 120;
		gd_tree.widthHint = 256;
		gd_tree.heightHint = 220;
		tree.setLayoutData(gd_tree);
	}

	protected void updateOnCheck(final DebugTreeItem item, final boolean checked) {
		if (item.item instanceof IFile) {
			final IFile file = (IFile) item.item;
			if (checked) {
				interpretedModules.add(file);
			} else {
				interpretedModules.remove(file);
			}
		} else {
			for (final DebugTreeItem i : item.children) {
				updateOnCheck(i, checked);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void setDefaults(final ILaunchConfigurationWorkingCopy config) {
		List<String> interpret;
		try {
			interpret = config.getAttribute(
					IErlLaunchAttributes.DEBUG_INTERPRET_MODULES,
					new ArrayList<String>());
		} catch (final CoreException e1) {
			interpret = new ArrayList<String>();
		}
		interpretedModules = new HashSet<IFile>();
		addModules(interpret, interpretedModules);

		int debugFlags;
		try {
			debugFlags = config.getAttribute(IErlLaunchAttributes.DEBUG_FLAGS,
					IErlDebugConstants.DEFAULT_DEBUG_FLAGS);
		} catch (final CoreException e) {
			debugFlags = IErlDebugConstants.DEFAULT_DEBUG_FLAGS;
		}
		setFlagCheckboxes(debugFlags);

		if (checkboxTreeViewer != null) {
			checkboxTreeViewer.setInput(config);
			checkboxTreeViewer.expandAll();
			final DebugTreeItem root = ((TreeContentProvider) checkboxTreeViewer
					.getContentProvider()).getRoot();
			root.setChecked(checkboxTreeViewer, interpretedModules);
		}
	}

	/**
	 * @param interpret
	 */
	public static void addModules(final Collection<String> interpret,
			final Collection<IFile> interpretedModules) {
		final IWorkspaceRoot wr = ResourcesPlugin.getWorkspace().getRoot();
		for (final String m : interpret) {
			final String[] pm = m.split(":");
			IResource file;
			if (pm.length > 1) {
				final IProject project = wr.getProject(pm[0]);
				file = recuFindMember(project, pm[1]);
			} else {
				file = recuFindMember(wr, m + ".erl");
			}
			if (file instanceof IFile) {
				interpretedModules.add((IFile) file);
			}
		}
	}

	private static IResource recuFindMember(final IResource item,
			final String name) {
		if (item instanceof IFile) {
			final IFile file = (IFile) item;
			if (file.getName().equals(name)) {
				return file;
			}
		} else if (item instanceof IContainer) {
			final IContainer container = (IContainer) item;
			try {
				for (final IResource r : container.members()) {
					final IResource result = recuFindMember(r, name);
					if (result != null) {
						return result;
					}
				}
			} catch (final CoreException e) {
			}
		}
		return null;
	}

	public void initializeFrom(final ILaunchConfiguration config) {
		try {
			setDefaults(config.getWorkingCopy());
		} catch (final CoreException e) {
			e.printStackTrace();
		}
	}

	public void performApply(final ILaunchConfigurationWorkingCopy config) {
		config.setAttribute(IErlLaunchAttributes.DEBUG_FLAGS,
				getFlagChechboxes());
		final List<String> r = new ArrayList<String>();
		for (final IFile file : interpretedModules) {
			r.add(file.getProject().getName() + ":" + file.getName());
		}
		config.setAttribute(IErlLaunchAttributes.DEBUG_INTERPRET_MODULES, r);
	}

	public String getName() {
		return "Debug";
	}

	@Override
	public boolean isValid(final ILaunchConfiguration config) {
		return true;
	}

	private void setFlagCheckboxes(final int debugFlags) {
		if (attachOnFirstCallCheck == null) {
			// I don't know why these are null sometimes...
			return;
		}
		int flag = debugFlags & IErlDebugConstants.ATTACH_ON_FIRST_CALL;
		attachOnFirstCallCheck.setSelection(flag != 0);
		flag = debugFlags & IErlDebugConstants.ATTACH_ON_BREAKPOINT;
		attachOnBreakpointCheck.setSelection(flag != 0);
		flag = debugFlags & IErlDebugConstants.ATTACH_ON_EXIT;
		attachOnExitCheck.setSelection(flag != 0);
		flag = debugFlags & IErlDebugConstants.DISTRIBUTED_DEBUG;
		distributedDebugCheck.setSelection(flag != 0);
	}

	private int getFlagChechboxes() {
		int result = 0;
		if (attachOnFirstCallCheck.getSelection()) {
			result |= IErlDebugConstants.ATTACH_ON_FIRST_CALL;
		}
		if (attachOnBreakpointCheck.getSelection()) {
			result |= IErlDebugConstants.ATTACH_ON_BREAKPOINT;
		}
		if (attachOnExitCheck.getSelection()) {
			result |= IErlDebugConstants.ATTACH_ON_EXIT;
		}
		if (distributedDebugCheck.getSelection()) {
			result |= IErlDebugConstants.DISTRIBUTED_DEBUG;
		}
		return result;
	}

	private final SelectionListener fBasicSelectionListener = new SelectionListener() {
		@SuppressWarnings("synthetic-access")
		public void widgetDefaultSelected(SelectionEvent e) {
			updateLaunchConfigurationDialog();
		}

		@SuppressWarnings("synthetic-access")
		public void widgetSelected(SelectionEvent e) {
			updateLaunchConfigurationDialog();
		}
	};

}
